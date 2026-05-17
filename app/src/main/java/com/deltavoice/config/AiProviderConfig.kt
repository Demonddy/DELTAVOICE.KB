package com.deltavoice.config

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Supported AI chat providers. BUILT_IN uses the server-side DeepSeek/OpenAI
 * backends (Convex + Supabase) with no user key required.
 * CUSTOM lets users connect to any OpenAI-compatible API (Groq, Mistral,
 * Together AI, Ollama, xAI Grok, Cohere, local servers, etc.).
 */
enum class AiProvider(
    val displayName: String,
    val defaultModel: String,
    val hint: String
) {
    BUILT_IN("Built-in (DeepSeek)", "", "No API key needed"),
    OPENAI("OpenAI", "gpt-4o-mini", "sk-..."),
    CLAUDE("Claude (Anthropic)", "claude-sonnet-4-20250514", "sk-ant-..."),
    GEMINI("Gemini (Google)", "gemini-2.0-flash", "AIza..."),
    DEEPSEEK("DeepSeek", "deepseek-chat", "sk-..."),
    CUSTOM("Custom (any model)", "", "Your API key");

    val requiresKey: Boolean get() = this != BUILT_IN
    val requiresEndpoint: Boolean get() = this == CUSTOM
}

object AiProviderConfig {

    private const val PREFS = "deltavoice_prefs"
    private const val KEY_PROVIDER = "ai_provider"
    private const val KEY_API_KEY = "ai_api_key"
    private const val KEY_MODEL = "ai_model"
    private const val KEY_ENDPOINT = "ai_endpoint"
    // Legacy key kept for migration
    private const val KEY_LEGACY_OPENAI = "openai_api_key"

    fun getProvider(ctx: Context): AiProvider {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_PROVIDER, null)
        if (name != null) {
            return try { AiProvider.valueOf(name) } catch (_: Exception) { AiProvider.BUILT_IN }
        }
        // Migrate: if legacy openai_api_key exists, treat as OPENAI provider
        val legacy = prefs.getString(KEY_LEGACY_OPENAI, "") ?: ""
        if (legacy.isNotBlank()) {
            prefs.edit()
                .putString(KEY_PROVIDER, AiProvider.OPENAI.name)
                .putString(KEY_API_KEY, legacy)
                .putString(KEY_MODEL, AiProvider.OPENAI.defaultModel)
                .remove(KEY_LEGACY_OPENAI)
                .apply()
            return AiProvider.OPENAI
        }
        return AiProvider.BUILT_IN
    }

    fun getApiKey(ctx: Context): String {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_API_KEY, "")
            ?: prefs.getString(KEY_LEGACY_OPENAI, "")
            ?: ""
    }

    fun getModel(ctx: Context): String {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_MODEL, "") ?: ""
        if (saved.isNotBlank()) return saved
        return getProvider(ctx).defaultModel
    }

    fun getEndpoint(ctx: Context): String {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ENDPOINT, "") ?: ""
    }

    fun save(ctx: Context, provider: AiProvider, apiKey: String, model: String, endpoint: String = "") {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_PROVIDER, provider.name)
            .putString(KEY_API_KEY, apiKey)
            .putString(KEY_MODEL, model.ifBlank { provider.defaultModel })
            .putString(KEY_ENDPOINT, endpoint)
            .remove(KEY_LEGACY_OPENAI)
            .apply()
    }

    /** True when the user has a usable direct-API configuration. */
    fun hasDirectKey(ctx: Context): Boolean {
        val p = getProvider(ctx)
        if (!p.requiresKey) return false
        if (getApiKey(ctx).isBlank()) return false
        if (p == AiProvider.CUSTOM && getEndpoint(ctx).isBlank()) return false
        return true
    }

    // ------------------------------------------------------------------
    // Provider-specific HTTP call + response parsing
    // ------------------------------------------------------------------

    /**
     * Call the user-selected AI provider directly.
     * Returns the assistant reply text, or null on failure.
     */
    fun callProvider(
        ctx: Context,
        conversationHistory: List<Map<String, String>>,
        connectTimeoutMs: Int = 30_000,
        readTimeoutMs: Int = 60_000
    ): String? {
        val provider = getProvider(ctx)
        if (!provider.requiresKey) return null
        val apiKey = getApiKey(ctx)
        if (apiKey.isBlank()) return null
        val model = getModel(ctx)

        return when (provider) {
            AiProvider.OPENAI, AiProvider.DEEPSEEK -> callOpenAiCompatible(
                provider, apiKey, model, conversationHistory, connectTimeoutMs, readTimeoutMs
            )
            AiProvider.CLAUDE -> callClaude(
                apiKey, model, conversationHistory, connectTimeoutMs, readTimeoutMs
            )
            AiProvider.GEMINI -> callGemini(
                apiKey, model, conversationHistory, connectTimeoutMs, readTimeoutMs
            )
            AiProvider.CUSTOM -> callCustom(
                ctx, apiKey, model, conversationHistory, connectTimeoutMs, readTimeoutMs
            )
            AiProvider.BUILT_IN -> null
        }
    }

    // ---------- OpenAI-compatible (OpenAI & DeepSeek) ----------

    private fun callOpenAiCompatible(
        provider: AiProvider,
        apiKey: String,
        model: String,
        history: List<Map<String, String>>,
        connectTimeout: Int,
        readTimeout: Int
    ): String? {
        val endpoint = when (provider) {
            AiProvider.DEEPSEEK -> "https://api.deepseek.com/v1/chat/completions"
            else -> "https://api.openai.com/v1/chat/completions"
        }
        return try {
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
                this.connectTimeout = connectTimeout
                this.readTimeout = readTimeout
                doOutput = true
            }
            val body = buildOpenAiBody(model, history)
            conn.outputStream.use { it.write(body.toByteArray()) }

            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                parseOpenAiResponse(text)
            } else {
                conn.disconnect()
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("AiProvider", "${provider.name} call failed: ${e.message}")
            null
        }
    }

    private fun buildOpenAiBody(model: String, history: List<Map<String, String>>): String {
        val msgs = JSONArray()
        msgs.put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
        history.takeLast(8).forEach { m ->
            msgs.put(JSONObject().put("role", m["role"] ?: "user").put("content", m["content"] ?: ""))
        }
        return JSONObject()
            .put("model", model)
            .put("messages", msgs)
            .put("max_tokens", 1000)
            .put("temperature", 0.7)
            .toString()
    }

    private fun parseOpenAiResponse(text: String): String? {
        return try {
            val json = JSONObject(text)
            val choices = json.optJSONArray("choices") ?: return null
            if (choices.length() == 0) return null
            choices.getJSONObject(0).optJSONObject("message")?.optString("content", "")
                ?.takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
    }

    // ---------- Claude (Anthropic Messages API) ----------

    private fun callClaude(
        apiKey: String,
        model: String,
        history: List<Map<String, String>>,
        connectTimeout: Int,
        readTimeout: Int
    ): String? {
        return try {
            val conn = (URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-api-key", apiKey)
                setRequestProperty("anthropic-version", "2023-06-01")
                this.connectTimeout = connectTimeout
                this.readTimeout = readTimeout
                doOutput = true
            }
            val body = buildClaudeBody(model, history)
            conn.outputStream.use { it.write(body.toByteArray()) }

            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                parseClaudeResponse(text)
            } else {
                conn.disconnect()
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("AiProvider", "Claude call failed: ${e.message}")
            null
        }
    }

    private fun buildClaudeBody(model: String, history: List<Map<String, String>>): String {
        val msgs = JSONArray()
        history.takeLast(8).forEach { m ->
            val role = m["role"] ?: "user"
            if (role != "system") {
                msgs.put(JSONObject().put("role", role).put("content", m["content"] ?: ""))
            }
        }
        // Ensure at least one user message exists
        if (msgs.length() == 0) {
            msgs.put(JSONObject().put("role", "user").put("content", "Hello"))
        }
        return JSONObject()
            .put("model", model)
            .put("system", SYSTEM_PROMPT)
            .put("messages", msgs)
            .put("max_tokens", 1000)
            .toString()
    }

    private fun parseClaudeResponse(text: String): String? {
        return try {
            val json = JSONObject(text)
            val content = json.optJSONArray("content") ?: return null
            if (content.length() == 0) return null
            content.getJSONObject(0).optString("text", "").takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
    }

    // ---------- Gemini (Google Generative Language API) ----------

    private fun callGemini(
        apiKey: String,
        model: String,
        history: List<Map<String, String>>,
        connectTimeout: Int,
        readTimeout: Int
    ): String? {
        return try {
            val endpoint =
                "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                this.connectTimeout = connectTimeout
                this.readTimeout = readTimeout
                doOutput = true
            }
            val body = buildGeminiBody(history)
            conn.outputStream.use { it.write(body.toByteArray()) }

            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                parseGeminiResponse(text)
            } else {
                conn.disconnect()
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("AiProvider", "Gemini call failed: ${e.message}")
            null
        }
    }

    private fun buildGeminiBody(history: List<Map<String, String>>): String {
        val contents = JSONArray()

        // System instruction as first "user" turn (Gemini uses systemInstruction field)
        val systemInstruction = JSONObject()
            .put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT)))

        history.takeLast(8).forEach { m ->
            val role = when (m["role"]) {
                "assistant" -> "model"
                "system" -> return@forEach
                else -> "user"
            }
            contents.put(
                JSONObject()
                    .put("role", role)
                    .put("parts", JSONArray().put(JSONObject().put("text", m["content"] ?: "")))
            )
        }
        if (contents.length() == 0) {
            contents.put(
                JSONObject()
                    .put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", "Hello")))
            )
        }

        return JSONObject()
            .put("system_instruction", systemInstruction)
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("maxOutputTokens", 1000).put("temperature", 0.7))
            .toString()
    }

    private fun parseGeminiResponse(text: String): String? {
        return try {
            val json = JSONObject(text)
            val candidates = json.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null
            val parts = candidates.getJSONObject(0)
                .optJSONObject("content")?.optJSONArray("parts") ?: return null
            if (parts.length() == 0) return null
            parts.getJSONObject(0).optString("text", "").takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
    }

    // ---------- Custom (any OpenAI-compatible endpoint) ----------

    private fun callCustom(
        ctx: Context,
        apiKey: String,
        model: String,
        history: List<Map<String, String>>,
        connectTimeout: Int,
        readTimeout: Int
    ): String? {
        val endpoint = getEndpoint(ctx)
        if (endpoint.isBlank()) return null
        return try {
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $apiKey")
                this.connectTimeout = connectTimeout
                this.readTimeout = readTimeout
                doOutput = true
            }
            val body = buildOpenAiBody(model, history)
            conn.outputStream.use { it.write(body.toByteArray()) }

            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                parseOpenAiResponse(text)
            } else {
                conn.disconnect()
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("AiProvider", "Custom endpoint call failed: ${e.message}")
            null
        }
    }

    // ---------- Shared ----------

    private const val SYSTEM_PROMPT =
        "You are a helpful, friendly AI assistant. Be concise but informative. " +
        "Use emojis occasionally. Respond in the same language the user writes in. " +
        "If asked to write something, provide complete, ready-to-use content."
}
