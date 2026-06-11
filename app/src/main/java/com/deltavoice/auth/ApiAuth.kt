package com.deltavoice.auth

import com.deltavoice.config.SupabaseConfig
import java.net.HttpURLConnection

/**
 * Attach authenticated Supabase headers to manual HTTP calls.
 */
object ApiAuth {

    suspend fun authorizationHeaders(): Map<String, String> {
        val token = AuthManager.requireAccessToken()
        return mapOf(
            "Authorization" to "Bearer $token",
            "apikey" to SupabaseConfig.SUPABASE_ANON_KEY,
        )
    }

    suspend fun applyTo(connection: HttpURLConnection) {
        authorizationHeaders().forEach { (key, value) ->
            connection.setRequestProperty(key, value)
        }
    }

    /** For legacy synchronous HTTP helpers running on a background thread. */
    fun applyToBlocking(connection: HttpURLConnection) {
        kotlinx.coroutines.runBlocking { applyTo(connection) }
    }
}
