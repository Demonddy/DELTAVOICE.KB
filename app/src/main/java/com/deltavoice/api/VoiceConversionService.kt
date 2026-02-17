package com.deltavoice.api

import com.deltavoice.config.SupabaseConfig
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Service for converting text to speech with voice styles using Supabase Edge Functions
 */
class VoiceConversionService {
    private val supabase = SupabaseClient.getClient()
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class VoiceConversionRequest(
        val text: String,
        val voiceStyle: String,
        val targetLanguage: String? = null
    )

    @Serializable
    data class VoiceConversionResponse(
        val success: Boolean = false,
        val audioBase64: String? = null,
        val translatedText: String? = null,
        val originalText: String? = null,
        val voiceStyle: String? = null,
        val targetLanguage: String? = null
    )

    suspend fun convertText(
        text: String,
        voiceStyle: String,
        targetLanguage: String? = null
    ): Result<VoiceConversionResponse> = withContext(Dispatchers.IO) {
        try {
            val request = VoiceConversionRequest(
                text = text,
                voiceStyle = voiceStyle,
                targetLanguage = targetLanguage
            )

            val response = supabase.functions.invoke(
                function = SupabaseConfig.FUNCTION_VOICE_CONVERSION,
                body = request
            )

            val responseText: String = response.body()
            val result = json.decodeFromString<VoiceConversionResponse>(responseText)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

