package com.deltavoice.api

import com.deltavoice.config.SupabaseConfig
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Service for creating voice clones using Supabase Edge Functions
 */
class VoiceCloneService {
    private val supabase = SupabaseClient.getClient()
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class VoiceCloneRequest(
        val name: String,
        val audioBase64: String,
        val description: String? = null,
        val format: String? = null
    )

    @Serializable
    data class VoiceCloneResponse(
        val success: Boolean = false,
        val voiceId: String? = null,
        val name: String? = null,
        val message: String? = null
    )

    suspend fun createVoiceClone(
        audioFile: File,
        name: String,
        description: String? = null
    ): Result<VoiceCloneResponse> = withContext(Dispatchers.IO) {
        try {
            val audioBase64 = android.util.Base64.encodeToString(audioFile.readBytes(), android.util.Base64.NO_WRAP)
            val request = VoiceCloneRequest(
                name = name,
                audioBase64 = audioBase64,
                description = description,
                format = audioFile.extension.takeIf { it.isNotBlank() }
            )

            val response = supabase.functions.invoke(
                function = SupabaseConfig.FUNCTION_CREATE_VOICE_CLONE,
                body = request
            )

            val responseText: String = response.body()
            val result = json.decodeFromString<VoiceCloneResponse>(responseText)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

