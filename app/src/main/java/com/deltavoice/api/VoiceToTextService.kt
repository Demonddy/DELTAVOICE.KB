package com.deltavoice.api

import com.deltavoice.config.SupabaseConfig
import io.github.jan.supabase.functions.functions
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.utils.io.core.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Service for converting voice/audio to text using Supabase or Whisper Backend
 */
class VoiceToTextService {
    private val supabase = SupabaseClient.getClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = HttpClient(Android) {
        engine {
            connectTimeout = 60_000
            socketTimeout = 60_000
        }
    }
    
    @Serializable
    data class SupabaseVoiceToTextRequest(
        val audio: String,  // base64 audio data
        val language: String? = null,  // Optional, auto-detect if not provided
        val format: String? = null  // Optional file extension (e.g., "m4a", "mp3")
    )
    
    @Serializable
    data class VoiceToTextResponse(
        val text: String,
        val language: String? = null,
        val confidence: Double? = null
    )
    
    @Serializable
    data class WhisperBackendResponse(
        val text: String
    )
    
    /**
     * Convert audio file to text using Supabase Edge Function or Whisper Backend
     * 
     * @param audioFile The audio file to transcribe
     * @param language The language code (e.g., "en", "es", "fr") or null for auto-detect
     * @return The transcribed text
     */
    suspend fun transcribeAudio(
        audioFile: File,
        language: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Read audio file and convert to base64
            val audioBytes = audioFile.readBytes()
            
            // Try direct Whisper backend first if configured
            if (SupabaseConfig.USE_DIRECT_WHISPER_BACKEND && SupabaseConfig.WHISPER_BACKEND_URL.isNotEmpty()) {
                return@withContext transcribeWithWhisperBackend(audioBytes, audioFile.extension)
            }
            
            // Otherwise use Supabase Edge Function
            val audioBase64 = android.util.Base64.encodeToString(audioBytes, android.util.Base64.NO_WRAP)
            val request = SupabaseVoiceToTextRequest(
                audio = audioBase64,
                language = language,
                format = audioFile.extension.takeIf { it.isNotBlank() }
            )
            
            // Call Supabase Edge Function
            val response = supabase.functions.invoke(function = SupabaseConfig.FUNCTION_VOICE_TO_TEXT, body = request)
            
            // Parse response using Ktor's body() extension
            val responseText: String = response.body()
            val result = json.decodeFromString<VoiceToTextResponse>(responseText)
            Result.success(result.text)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Transcribe audio using direct Whisper Backend API
     */
    private suspend fun transcribeWithWhisperBackend(
        audioBytes: ByteArray,
        format: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val whisperUrl = SupabaseConfig.WHISPER_BACKEND_URL
            if (whisperUrl.isEmpty()) {
                throw IllegalStateException("Whisper backend URL not configured")
            }
            
            // Create multipart form data
            val response = httpClient.post("$whisperUrl/transcribe") {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("file", audioBytes, Headers.build {
                                append(HttpHeaders.ContentType, "audio/$format")
                                append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"audio.$format\"")
                            })
                        }
                    )
                )
            }
            
            val responseText = response.body<String>()
            val result = json.decodeFromString<WhisperBackendResponse>(responseText)
            Result.success(result.text)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Convert audio bytes to text
     */
    suspend fun transcribeAudioBytes(
        audioBytes: ByteArray,
        language: String? = null,
        format: String = "m4a"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Try direct Whisper backend first if configured
            if (SupabaseConfig.USE_DIRECT_WHISPER_BACKEND && SupabaseConfig.WHISPER_BACKEND_URL.isNotEmpty()) {
                return@withContext transcribeWithWhisperBackend(audioBytes, format)
            }
            
            // Otherwise use Supabase Edge Function
            val audioBase64 = android.util.Base64.encodeToString(audioBytes, android.util.Base64.NO_WRAP)
            val request = SupabaseVoiceToTextRequest(
                audio = audioBase64,
                language = language,
                format = format.takeIf { it.isNotBlank() }
            )
            
            val response = supabase.functions.invoke(function = SupabaseConfig.FUNCTION_VOICE_TO_TEXT, body = request)
            
            val responseText: String = response.body()
            val result = json.decodeFromString<VoiceToTextResponse>(responseText)
            Result.success(result.text)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Cleanup resources
     */
    fun close() {
        httpClient.close()
    }
}

