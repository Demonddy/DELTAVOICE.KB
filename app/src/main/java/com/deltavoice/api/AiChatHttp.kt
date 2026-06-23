package com.deltavoice.api

import android.content.Context
import com.deltavoice.auth.AuthManager
import com.deltavoice.auth.ApiAuth
import com.deltavoice.config.ConvexConfig
import com.deltavoice.config.SupabaseConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking

/**
 * Shared HTTP helpers for AI chat (keyboard + AIChatConfigActivity).
 */
object AiChatHttp {

    sealed class ChatResult {
        data class Success(val text: String) : ChatResult()
        data class Error(val userMessage: String, val authRequired: Boolean = false) : ChatResult()
    }

    fun hasCloudSession(): Boolean = runBlocking {
        AuthManager.refreshSessionIfNeeded()
        !AuthManager.getAccessToken().isNullOrBlank()
    }

    fun parseResponse(responseText: String): String? {
        return try {
            val json = JSONObject(responseText)
            if (json.has("success") && !json.optBoolean("success", true)) {
                val errBody = json.optString("content", "")
                if (errBody.isNotBlank()) return errBody
            }
            listOf("content", "response", "message", "text").forEach { key ->
                val value = json.optString(key, "")
                if (value.isNotBlank()) return value
            }
            val choices = json.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val msg = choices.getJSONObject(0).optJSONObject("message")
                msg?.optString("content", "")?.takeIf { it.isNotBlank() }?.let { return it }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun parseError(responseText: String, statusCode: Int, signInMessage: String): ChatResult.Error {
        return try {
            val json = JSONObject(responseText)
            val code = json.optString("code", "")
            when {
                statusCode == 401 || code == "AUTH_REQUIRED" || code == "AUTH_INVALID" ->
                    ChatResult.Error(signInMessage, authRequired = true)
                code == "RATE_LIMIT_EXCEEDED" ->
                    ChatResult.Error("Rate limit reached. Please wait a moment and try again.")
                else -> {
                    val msg = json.optString("error", "").ifBlank {
                        json.optString("content", "AI request failed ($statusCode)")
                    }
                    ChatResult.Error(msg)
                }
            }
        } catch (_: Exception) {
            ChatResult.Error("AI request failed ($statusCode)")
        }
    }

    private fun applyAuthBestEffort(connection: HttpURLConnection) {
        ApiAuth.tryApplyToBlocking(connection)
    }

    private fun applyAuthRequired(connection: HttpURLConnection): Boolean {
        return ApiAuth.tryApplyToBlocking(connection)
    }

    private fun buildMessagesJson(history: List<Pair<String, String>>): String {
        val sb = StringBuilder("[")
        sb.append("""{"role":"system","content":"You are a helpful, friendly AI assistant. Be concise. Use emojis occasionally. Respond in the same language the user writes in."}""")
        history.takeLast(8).forEach { (role, content) ->
            val escaped = content
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")
            sb.append(""",{"role":"$role","content":"$escaped"}""")
        }
        sb.append("]")
        return sb.toString()
    }

    fun callConvex(
        history: List<Pair<String, String>>,
        signInMessage: String,
        connectTimeoutMs: Int = 30_000,
        readTimeoutMs: Int = 60_000,
    ): ChatResult {
        if (!ConvexConfig.USE_CONVEX_FOR_VOICE_WORKFLOW) {
            return ChatResult.Error("Convex disabled")
        }
        if (ConvexConfig.CONVEX_SITE_URL.contains("YOUR_DEPLOYMENT")) {
            return ChatResult.Error("Convex not configured")
        }
        return try {
            val url = URL(ConvexConfig.AI_CHAT_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            applyAuthBestEffort(connection)
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.doOutput = true
            val body = """{"messages":${buildMessagesJson(history)}}"""
            connection.outputStream.use { it.write(body.toByteArray()) }
            val code = connection.responseCode
            val text = if (code in 200..299) {
                connection.inputStream.bufferedReader().readText()
            } else {
                connection.errorStream?.bufferedReader()?.readText().orEmpty()
            }
            connection.disconnect()
            parseResponse(text)?.let { ChatResult.Success(it) }
                ?: if (code in 200..299) ChatResult.Error("Empty AI response")
                else parseError(text, code, signInMessage)
        } catch (e: Exception) {
            ChatResult.Error(e.message ?: "Network error")
        }
    }

    fun callSupabase(
        history: List<Pair<String, String>>,
        signInMessage: String,
        connectTimeoutMs: Int = 30_000,
        readTimeoutMs: Int = 60_000,
    ): ChatResult {
        return try {
            val url = URL("${SupabaseConfig.SUPABASE_URL}/functions/v1/ai-chat")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            if (!applyAuthRequired(connection)) {
                connection.disconnect()
                return ChatResult.Error(signInMessage, authRequired = true)
            }
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.doOutput = true
            val body = """{"messages":${buildMessagesJson(history)}}"""
            connection.outputStream.use { it.write(body.toByteArray()) }
            val code = connection.responseCode
            val text = if (code in 200..299) {
                connection.inputStream.bufferedReader().readText()
            } else {
                connection.errorStream?.bufferedReader()?.readText().orEmpty()
            }
            connection.disconnect()
            parseResponse(text)?.let { ChatResult.Success(it) }
                ?: if (code in 200..299) ChatResult.Error("Empty AI response")
                else parseError(text, code, signInMessage)
        } catch (e: Exception) {
            ChatResult.Error(e.message ?: "Network error")
        }
    }

    /** Convex first, then Supabase. */
    fun callCloud(
        history: List<Pair<String, String>>,
        signInMessage: String,
        connectTimeoutMs: Int = 30_000,
        readTimeoutMs: Int = 60_000,
    ): ChatResult {
        val convex = callConvex(history, signInMessage, connectTimeoutMs, readTimeoutMs)
        if (convex is ChatResult.Success) return convex
        if (!hasCloudSession()) return convex
        val supabase = callSupabase(history, signInMessage, connectTimeoutMs, readTimeoutMs)
        return when {
            supabase is ChatResult.Success -> supabase
            convex is ChatResult.Error && supabase is ChatResult.Error -> supabase
            convex is ChatResult.Error -> convex
            else -> supabase
        }
    }
}
