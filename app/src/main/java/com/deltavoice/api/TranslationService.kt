package com.deltavoice.api

import com.deltavoice.config.SupabaseConfig
import io.github.jan.supabase.functions.functions
import io.ktor.client.call.body
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.withContext

/**
 * Service for translating text using Supabase
 */
class TranslationService {
    private val supabase = SupabaseClient.getClient()
    private val json = Json { ignoreUnknownKeys = true }
    
    @Serializable
    data class TranslationRequest(
        val text: String,
        val targetLanguage: String,
        val model: String = "gpt-4o-mini"
    )
    
    @Serializable
    data class TranslationResponse(
        val translatedText: String
    )
    
    /**
     * Translate text to target language using Supabase Edge Function
     * 
     * @param text The text to translate
     * @param targetLanguage Target language code (e.g., "fr", "de", "es")
     * @return The translated text
     */
    suspend fun translateText(
        text: String,
        targetLanguage: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = TranslationRequest(
                text = text,
                targetLanguage = targetLanguage
            )
            
            // Call Supabase Edge Function (translate-text)
            val response = supabase.functions.invoke(function = SupabaseConfig.FUNCTION_TRANSLATE_TEXT, body = request)
            
            // Parse response using Ktor's body() extension
            val responseText: String = response.body()
            val result = json.decodeFromString<TranslationResponse>(responseText)
            Result.success(result.translatedText)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Detect language of text
     */
    suspend fun detectLanguage(text: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // This would require a separate Edge Function for language detection
            // For now, return a default or use a simple heuristic
            Result.success("en") // Placeholder
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

