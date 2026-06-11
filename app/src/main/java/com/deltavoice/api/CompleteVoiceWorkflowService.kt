package com.deltavoice.api

import com.deltavoice.config.ConvexConfig
import com.deltavoice.config.SupabaseConfig
import com.deltavoice.auth.ApiAuth
import com.deltavoice.auth.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Service for full voice workflow: transcribe -> translate -> optional TTS.
 * When [ConvexConfig.USE_CONVEX_FOR_VOICE_WORKFLOW] is true, Convex is tried first for all modes:
 * complete, voice-only, and text-only. Supabase edge function is fallback only.
 */
class CompleteVoiceWorkflowService {
    private val json = Json { ignoreUnknownKeys = true }

    class HttpStatusException(val statusCode: Int, message: String) : Exception(message)

    private fun throwForHttpStatus(code: Int, body: String) {
        when (code) {
            401 -> throw HttpStatusException(401, "Session expired. Please sign in again.")
            403 -> throw HttpStatusException(403, "Premium subscription required for this feature.")
            429 -> throw HttpStatusException(429, "Rate limit reached. Please wait a moment and try again.")
            413 -> throw HttpStatusException(413, "File too large. Please use a shorter recording.")
        }
    }

    @Serializable
    data class WorkflowRequest(
        val audioBase64: String,
        val targetLanguage: String,
        val voiceStyle: String,
        val workflowType: String = "complete",
        val format: String? = null
    )

    @Serializable
    data class WorkflowResponse(
        val success: Boolean = false,
        val originalText: String? = null,
        val translatedText: String? = null,
        val convertedAudioBase64: String? = null,
        val targetLanguage: String? = null,
        val voiceStyle: String? = null,
        val workflowType: String? = null,
        val ttsFallback: Boolean? = null
    )

    suspend fun runWorkflow(
        audioFile: File,
        targetLanguage: String,
        voiceStyle: String,
        workflowType: String = "complete"
    ): Result<WorkflowResponse> = withContext(Dispatchers.IO) {
        android.util.Log.d("DeltaVoice", "WorkflowService: Starting workflow - type=$workflowType, lang=$targetLanguage, voice=$voiceStyle")

        try {
            AuthManager.requireAccessToken()
        } catch (e: AuthManager.AuthRequiredException) {
            return@withContext Result.failure(e)
        }
        
        // Validate file
        if (!audioFile.exists() || audioFile.length() == 0L) {
            android.util.Log.e("DeltaVoice", "WorkflowService: Audio file is empty or missing")
            return@withContext Result.failure(Exception("Audio file is empty or missing"))
        }
        
        android.util.Log.d("DeltaVoice", "WorkflowService: Audio file size = ${audioFile.length()} bytes")
        
        // Read and encode audio
        val audioBytes = audioFile.readBytes()
        val audioBase64 = android.util.Base64.encodeToString(audioBytes, android.util.Base64.NO_WRAP)
        
        android.util.Log.d("DeltaVoice", "WorkflowService: Encoded audio base64 length = ${audioBase64.length}")
        
        // Ensure backend receives valid voice and language - never send empty strings
        val sanitizedLang = targetLanguage.takeIf { it.isNotBlank() } ?: "en"
        val sanitizedVoice = voiceStyle.takeIf { it.isNotBlank() } ?: "aria"
        android.util.Log.d("DeltaVoice", "WorkflowService: Sending lang=$sanitizedLang, voice=$sanitizedVoice")
        
        val request = WorkflowRequest(
            audioBase64 = audioBase64,
            targetLanguage = sanitizedLang,
            voiceStyle = sanitizedVoice,
            workflowType = workflowType,
            format = audioFile.extension.takeIf { it.isNotBlank() } ?: "m4a"
        )

        // Convex primary for all workflow modes; Supabase only after retries fail
        val useConvex = ConvexConfig.USE_CONVEX_FOR_VOICE_WORKFLOW &&
            (workflowType == "complete" ||
                workflowType == "voice-only" ||
                workflowType == "text-only")

        if (useConvex && !ConvexConfig.CONVEX_SITE_URL.contains("YOUR_DEPLOYMENT")) {
            val convexMaxRetries = 3
            for (attempt in 1..convexMaxRetries) {
                android.util.Log.d("DeltaVoice", "WorkflowService: Convex attempt $attempt/$convexMaxRetries (real-time delivery)...")
                val convexResult = callConvex(request)
                if (convexResult != null) {
                    android.util.Log.d("DeltaVoice", "WorkflowService: Convex real-time delivery succeeded!")
                    return@withContext Result.success(convexResult)
                }
                if (attempt < convexMaxRetries) {
                    val delayMs = 1000L * attempt
                    android.util.Log.d("DeltaVoice", "WorkflowService: Convex retry in ${delayMs}ms...")
                    kotlinx.coroutines.delay(delayMs)
                }
            }
            android.util.Log.w("DeltaVoice", "WorkflowService: Convex failed after $convexMaxRetries attempts, falling back to Supabase")
        }

        // Supabase fallback (direct HTTP with 120s read timeout)
        val maxRetries = 3
        var lastException: Exception? = null
        
        for (attempt in 1..maxRetries) {
            try {
                android.util.Log.d("DeltaVoice", "WorkflowService: Attempt $attempt/$maxRetries - Calling Supabase (direct HTTP)...")
                
                val result = callWithDirectHttp(request)
                if (result != null) {
                    android.util.Log.d("DeltaVoice", "WorkflowService: Workflow completed successfully!")
                    return@withContext Result.success(result)
                }
                lastException = Exception("Empty or invalid response")
                
            } catch (e: Exception) {
                lastException = e
                android.util.Log.e("DeltaVoice", "WorkflowService: Attempt $attempt failed: ${e.message}")
                
                val isNetworkError = e.message?.contains("Unable to resolve host", ignoreCase = true) == true ||
                                     e.message?.contains("timeout", ignoreCase = true) == true ||
                                     e.message?.contains("connection", ignoreCase = true) == true ||
                                     e.message?.contains("network", ignoreCase = true) == true
                
                if (!isNetworkError || attempt == maxRetries) {
                    break
                }
                
                val delayMs = (1000L * (1 shl (attempt - 1)))
                android.util.Log.d("DeltaVoice", "WorkflowService: Waiting ${delayMs}ms before retry...")
                kotlinx.coroutines.delay(delayMs)
            }
        }
        
        // All attempts failed - propagate HTTP status exceptions directly
        if (lastException is HttpStatusException) {
            return@withContext Result.failure(lastException!!)
        }
        if (lastException is AuthManager.AuthRequiredException) {
            return@withContext Result.failure(lastException!!)
        }
        if (lastException is AuthManager.PremiumRequiredException) {
            return@withContext Result.failure(lastException!!)
        }

        val errorMsg = when {
            lastException?.message?.contains("timeout", ignoreCase = true) == true -> 
                "Connection timeout. Please check your internet and try again."
            lastException?.message?.contains("Unable to resolve host", ignoreCase = true) == true -> 
                "No internet connection. Please connect to WiFi or mobile data."
            lastException?.message?.contains("network", ignoreCase = true) == true ||
            lastException?.message?.contains("connection", ignoreCase = true) == true -> 
                "Network error. Please check your internet connection."
            lastException?.message?.contains("socket", ignoreCase = true) == true ->
                "Connection lost. Please try again."
            else -> lastException?.message ?: "Processing failed. Please try again."
        }
        android.util.Log.e("DeltaVoice", "WorkflowService: All attempts failed: $errorMsg")
        Result.failure(Exception(errorMsg))
    }
    
    private suspend fun callConvex(request: WorkflowRequest): WorkflowResponse? {
        return try {
            val url = URL(ConvexConfig.VOICE_WORKFLOW_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            ApiAuth.applyTo(connection)
            connection.connectTimeout = 60000
            connection.readTimeout = 120000
            connection.doOutput = true

            val requestJson = json.encodeToString(request)
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestJson)
                writer.flush()
            }

            val responseCode = connection.responseCode
            val responseText = if (responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }
            connection.disconnect()

            throwForHttpStatus(responseCode, responseText)

            val result = json.decodeFromString<WorkflowResponse>(responseText)
            if (result.success || !result.originalText.isNullOrBlank() || !result.translatedText.isNullOrBlank()) {
                result
            } else null
        } catch (e: HttpStatusException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("DeltaVoice", "WorkflowService: Convex call failed: ${e.message}")
            null
        }
    }

    /**
     * Direct HTTP call as fallback when Supabase SDK fails
     */
    private suspend fun callWithDirectHttp(request: WorkflowRequest): WorkflowResponse? {
        val url = URL("${SupabaseConfig.SUPABASE_URL}/functions/v1/${SupabaseConfig.FUNCTION_COMPLETE_VOICE_WORKFLOW}")
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            ApiAuth.applyTo(connection)
            connection.connectTimeout = 60000 // 60 seconds
            connection.readTimeout = 120000 // 120 seconds for processing
            connection.doOutput = true
            
            // Send request
            val requestJson = json.encodeToString(request)
            android.util.Log.d("DeltaVoice", "WorkflowService: Direct HTTP - sending ${requestJson.length} bytes")
            
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestJson)
                writer.flush()
            }
            
            // Read response
            val responseCode = connection.responseCode
            android.util.Log.d("DeltaVoice", "WorkflowService: Direct HTTP - response code: $responseCode")
            
            val responseText = if (responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }
            android.util.Log.d("DeltaVoice", "WorkflowService: Direct HTTP - response code: $responseCode, length: ${responseText.length}")

            throwForHttpStatus(responseCode, responseText)

            if (responseCode == HttpURLConnection.HTTP_OK && responseText.isNotBlank()) {
                val result = json.decodeFromString<WorkflowResponse>(responseText)
                if (result.success || !result.originalText.isNullOrBlank() || !result.translatedText.isNullOrBlank()) {
                    return result
                }
            }
            
            return null
        } finally {
            connection.disconnect()
        }
    }
}

