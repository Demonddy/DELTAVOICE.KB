package com.deltavoice.api

import com.deltavoice.config.SupabaseConfig
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
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
 * Service for full voice workflow: transcribe -> translate -> convert to speech
 */
class CompleteVoiceWorkflowService {
    private val supabase = SupabaseClient.getClient()
    private val json = Json { ignoreUnknownKeys = true }

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
        val workflowType: String? = null
    )

    suspend fun runWorkflow(
        audioFile: File,
        targetLanguage: String,
        voiceStyle: String,
        workflowType: String = "complete"
    ): Result<WorkflowResponse> = withContext(Dispatchers.IO) {
        android.util.Log.d("DeltaVoice", "WorkflowService: Starting workflow - type=$workflowType, lang=$targetLanguage, voice=$voiceStyle")
        
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
        
        val request = WorkflowRequest(
            audioBase64 = audioBase64,
            targetLanguage = targetLanguage,
            voiceStyle = voiceStyle,
            workflowType = workflowType,
            format = audioFile.extension.takeIf { it.isNotBlank() } ?: "m4a"
        )

        // Retry logic with exponential backoff
        val maxRetries = 3
        var lastException: Exception? = null
        
        for (attempt in 1..maxRetries) {
            try {
                android.util.Log.d("DeltaVoice", "WorkflowService: Attempt $attempt/$maxRetries - Calling Supabase function...")
                
                // Call Supabase function
                val response = supabase.functions.invoke(
                    function = SupabaseConfig.FUNCTION_COMPLETE_VOICE_WORKFLOW,
                    body = request
                )

                val responseText: String = response.body()
                
                android.util.Log.d("DeltaVoice", "WorkflowService: Response received, length = ${responseText.length}")
                
                // Check for error response
                if (responseText.contains("\"error\"") && responseText.contains("\"success\":false")) {
                    val errorMatch = Regex("\"error\"\\s*:\\s*\"([^\"]+)\"").find(responseText)
                    val errorMsg = errorMatch?.groupValues?.get(1) ?: "Server error"
                    android.util.Log.e("DeltaVoice", "WorkflowService: Server returned error: $errorMsg")
                    return@withContext Result.failure(Exception(errorMsg))
                }
                
                val result = json.decodeFromString<WorkflowResponse>(responseText)
                
                android.util.Log.d("DeltaVoice", "WorkflowService: Parsed response - success=${result.success}, hasOriginalText=${!result.originalText.isNullOrBlank()}, hasTranslatedText=${!result.translatedText.isNullOrBlank()}, hasAudio=${!result.convertedAudioBase64.isNullOrBlank()}, audioLength=${result.convertedAudioBase64?.length ?: 0}")
                
                // Verify we got useful data
                if (!result.success && result.originalText.isNullOrBlank() && result.translatedText.isNullOrBlank()) {
                    android.util.Log.e("DeltaVoice", "WorkflowService: No useful data in response")
                    return@withContext Result.failure(Exception("No response from server"))
                }
                
                android.util.Log.d("DeltaVoice", "WorkflowService: Workflow completed successfully!")
                return@withContext Result.success(result)
                
            } catch (e: Exception) {
                lastException = e
                android.util.Log.e("DeltaVoice", "WorkflowService: Attempt $attempt failed: ${e.message}")
                
                // Don't retry on non-network errors
                val isNetworkError = e.message?.contains("Unable to resolve host", ignoreCase = true) == true ||
                                     e.message?.contains("timeout", ignoreCase = true) == true ||
                                     e.message?.contains("connection", ignoreCase = true) == true ||
                                     e.message?.contains("network", ignoreCase = true) == true
                
                if (!isNetworkError || attempt == maxRetries) {
                    break
                }
                
                // Wait before retry (exponential backoff: 1s, 2s, 4s)
                val delayMs = (1000L * (1 shl (attempt - 1)))
                android.util.Log.d("DeltaVoice", "WorkflowService: Waiting ${delayMs}ms before retry...")
                kotlinx.coroutines.delay(delayMs)
            }
        }
        
        // All retries with Supabase SDK failed, try direct HTTP as fallback
        android.util.Log.d("DeltaVoice", "WorkflowService: SDK failed, trying direct HTTP...")
        
        try {
            val result = callWithDirectHttp(request)
            if (result != null) {
                return@withContext Result.success(result)
            }
        } catch (e: Exception) {
            android.util.Log.e("DeltaVoice", "WorkflowService: Direct HTTP also failed: ${e.message}")
            lastException = e
        }
        
        // All attempts failed
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
            lastException?.message?.contains("403", ignoreCase = true) == true ||
            lastException?.message?.contains("401", ignoreCase = true) == true ->
                "Voice service is temporarily unavailable. Please try again later."
            lastException?.message?.contains("500", ignoreCase = true) == true ||
            lastException?.message?.contains("502", ignoreCase = true) == true ||
            lastException?.message?.contains("503", ignoreCase = true) == true ->
                "Server temporarily unavailable. Please try again later."
            else -> lastException?.message ?: "Processing failed. Please try again."
        }
        android.util.Log.e("DeltaVoice", "WorkflowService: All attempts failed: $errorMsg")
        Result.failure(Exception(errorMsg))
    }
    
    /**
     * Direct HTTP call as fallback when Supabase SDK fails
     */
    private fun callWithDirectHttp(request: WorkflowRequest): WorkflowResponse? {
        val url = URL("${SupabaseConfig.SUPABASE_URL}/functions/v1/${SupabaseConfig.FUNCTION_COMPLETE_VOICE_WORKFLOW}")
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}")
            connection.setRequestProperty("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
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
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                android.util.Log.d("DeltaVoice", "WorkflowService: Direct HTTP - response length: ${responseText.length}")
                
                val result = json.decodeFromString<WorkflowResponse>(responseText)
                if (result.success || !result.originalText.isNullOrBlank() || !result.translatedText.isNullOrBlank()) {
                    android.util.Log.d("DeltaVoice", "WorkflowService: Direct HTTP successful!")
                    return result
                }
            } else {
                val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                android.util.Log.e("DeltaVoice", "WorkflowService: Direct HTTP error: $responseCode - $errorText")
            }
            
            return null
        } finally {
            connection.disconnect()
        }
    }
}

