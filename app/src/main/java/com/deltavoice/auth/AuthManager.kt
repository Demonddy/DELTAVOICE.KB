package com.deltavoice.auth

import android.content.Context
import com.deltavoice.api.SupabaseClient
import com.deltavoice.config.SupabaseConfig
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Supabase Auth session manager. All cloud AI/voice APIs require a signed-in user JWT.
 */
object AuthManager {

    private const val PREFS_NAME = "deltavoice_prefs"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_IS_PREMIUM = "is_premium"

    class AuthRequiredException(message: String = "Please sign in to use this feature.") :
        Exception(message)

    class PremiumRequiredException(message: String = "Premium subscription required.") :
        Exception(message)

    private lateinit var appContext: Context
    private val json = Json { ignoreUnknownKeys = true }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun prefs() =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun client() = SupabaseClient.getClient()

    suspend fun signIn(email: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            client().auth.signInWith(Email) {
                this.email = email.trim()
                this.password = password
            }
            syncAccountPrefs()
            syncSubscriptionStatus()
            Unit
        }
    }

    suspend fun signUp(email: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            client().auth.signUpWith(Email) {
                this.email = email.trim()
                this.password = password
            }
            syncAccountPrefs()
            syncSubscriptionStatus()
            Unit
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        runCatching { client().auth.signOut() }
        prefs().edit()
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .putBoolean(KEY_IS_PREMIUM, false)
            .putString(KEY_USER_NAME, appContext.getString(com.deltavoice.R.string.guest_user))
            .putString(KEY_USER_EMAIL, "")
            .apply()
    }

    suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        runCatching { client().auth.currentSessionOrNull()?.accessToken }.getOrNull()
    }

    suspend fun requireAccessToken(): String =
        getAccessToken() ?: throw AuthRequiredException()

    fun isLoggedIn(): Boolean =
        prefs().getBoolean(KEY_IS_LOGGED_IN, false)

    fun isPremium(): Boolean =
        prefs().getBoolean(KEY_IS_PREMIUM, false)

    fun userEmail(): String? =
        prefs().getString(KEY_USER_EMAIL, null)?.takeIf { it.isNotBlank() }

    fun userName(): String =
        prefs().getString(KEY_USER_NAME, appContext.getString(com.deltavoice.R.string.guest_user))
            ?: appContext.getString(com.deltavoice.R.string.guest_user)

    suspend fun refreshSessionIfNeeded() = withContext(Dispatchers.IO) {
        runCatching {
            client().auth.currentSessionOrNull() ?: return@runCatching
            syncAccountPrefs()
        }
    }

    suspend fun syncSubscriptionStatus(): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val token = requireAccessToken()
            val url = URL("${SupabaseConfig.SUPABASE_URL}/functions/v1/check-subscription")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.doOutput = true
            connection.outputStream.use { it.write("{}".toByteArray()) }
            val body = if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            connection.disconnect()
            val result = json.decodeFromString<SubscriptionResponse>(body)
            val premium = result.subscribed == true
            prefs().edit().putBoolean(KEY_IS_PREMIUM, premium).apply()
            premium
        }
    }

    private suspend fun syncAccountPrefs() {
        val user = client().auth.currentUserOrNull()
        val email = user?.email?.trim().orEmpty()
        val displayName = email.substringBefore("@").replaceFirstChar { it.uppercase() }
            .ifBlank { appContext.getString(com.deltavoice.R.string.guest_user) }
        prefs().edit()
            .putBoolean(KEY_IS_LOGGED_IN, user != null && email.isNotBlank())
            .putString(KEY_USER_EMAIL, email)
            .putString(KEY_USER_NAME, displayName)
            .apply()
    }

    @Serializable
    private data class SubscriptionResponse(
        val subscribed: Boolean? = null,
        val subscription_tier: String? = null,
    )
}
