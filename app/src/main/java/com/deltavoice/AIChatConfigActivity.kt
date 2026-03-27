package com.deltavoice

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.deltavoice.ui.ShimmerView
import android.graphics.Color
import com.deltavoice.config.ConvexConfig
import com.deltavoice.config.SupabaseConfig
import com.deltavoice.util.NetworkUtils
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * AI Chat page - Chat with AI directly on this page.
 * No keyboard navigation required.
 *
 * Always uses dark UI regardless of app light/dark setting (local night mode).
 */
class AIChatConfigActivity : AppCompatActivity() {

    private lateinit var chatMessages: LinearLayout
    private lateinit var chatScroll: ScrollView
    private lateinit var chatInput: EditText
    private lateinit var btnSend: ImageButton

    private val conversationHistory = mutableListOf<Pair<String, String>>()
    private val activityScope = CoroutineScope(Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_chat_config)

        chatMessages = findViewById(R.id.chat_messages)
        chatScroll = findViewById(R.id.chat_scroll)
        chatInput = findViewById(R.id.chat_input)
        btnSend = findViewById(R.id.btn_send)

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btn_api_key).setOnClickListener { showApiKeyDialog() }

        addWelcomeMessage()

        chatInput.post {
            if (!NetworkUtils.isConnected(this)) {
                showInternetRequiredSnackbar(R.string.internet_required_ai_chat)
            }
        }

        chatInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                btnSend.isEnabled = !s.isNullOrBlank()
            }
        })

        btnSend.setOnClickListener { sendMessage() }
    }

    private fun addWelcomeMessage() {
        addMessage("Hi! I'm your AI assistant. Ask me anything - writing, questions, translations, and more!", false)
    }

    /** Scroll chat to bottom without triggering focus changes (prevents keyboard flicker). */
    private fun scrollToBottom(keepInputFocus: Boolean = false) {
        chatScroll.post {
            val scrollRange = (chatMessages.height - chatScroll.height).coerceAtLeast(0)
            chatScroll.smoothScrollTo(0, scrollRange)
            if (keepInputFocus) chatInput.requestFocus()
        }
    }

    private fun addMessage(text: String, isUser: Boolean) {
        val bubble = TextView(this).apply {
            this.text = text
            setPadding(48, 24, 48, 24)
            textSize = 15f
            setLineSpacing(4f, 1.2f)
            isFocusable = false
            isFocusableInTouchMode = false
        }

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 12
            bottomMargin = 12
        }

        if (isUser) {
            bubble.setBackgroundResource(R.drawable.ai_chat_bubble_user)
            bubble.setTextColor(0xFFE5E7EB.toInt())
            params.gravity = Gravity.END
            params.marginEnd = 24
            params.marginStart = 64
        } else {
            bubble.setBackgroundResource(R.drawable.ai_chat_bubble_ai)
            bubble.setTextColor(0xFFE5E7EB.toInt())
            params.gravity = Gravity.START
            params.marginStart = 24
            params.marginEnd = 64
        }

        bubble.layoutParams = params
        chatMessages.addView(bubble)

        scrollToBottom(keepInputFocus = true)
    }

    private var loadingBubbleView: android.view.View? = null

    private fun sendMessage() {
        val message = chatInput.text.toString().trim()
        if (message.isBlank()) return

        if (!NetworkUtils.isConnected(this)) {
            showInternetRequiredSnackbar(R.string.internet_required_ai_chat)
        }

        chatInput.setText("")
        addMessage(message, true)
        conversationHistory.add("user" to message)
        btnSend.isEnabled = false

        addLoadingMessage()

        activityScope.launch {
            val response = withContext(Dispatchers.IO) {
                callAiApi(message)
            }
            mainHandler.post {
                removeLoadingMessage()
                val reply = response ?: getFallbackResponse(message)
                addMessage(reply, false)
                conversationHistory.add("assistant" to reply)
                btnSend.isEnabled = true
            }
        }
    }

    private fun addLoadingMessage() {
        val bubble = android.widget.LinearLayout(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 12
                bottomMargin = 12
                gravity = Gravity.START
                marginStart = 24
                marginEnd = 64
            }
            setBackgroundResource(R.drawable.ai_chat_bubble_ai)
            setPadding(48, 24, 48, 24)
            tag = "loading"
            isFocusable = false
            isFocusableInTouchMode = false
        }
        val shimmerView = ShimmerView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                (80 * resources.displayMetrics.density).toInt(),
                (24 * resources.displayMetrics.density).toInt()
            )
            baseColor = Color.parseColor("#2A2F3E")
            cornerRadius = 4f * resources.displayMetrics.density
            isFocusable = false
            isFocusableInTouchMode = false
        }
        bubble.addView(shimmerView)
        shimmerView.startShimmer()
        chatMessages.addView(bubble)
        loadingBubbleView = bubble
        scrollToBottom(keepInputFocus = true)
    }

    private fun removeLoadingMessage() {
        loadingBubbleView?.let { view ->
            if (view is android.view.ViewGroup) {
                for (i in 0 until view.childCount) {
                    (view.getChildAt(i) as? ShimmerView)?.stopShimmer()
                }
            }
            chatMessages.removeView(view)
            loadingBubbleView = null
        }
    }

    private fun getOpenAiApiKey(): String {
        return getSharedPreferences("deltavoice_prefs", Context.MODE_PRIVATE)
            .getString("openai_api_key", "") ?: ""
    }

    private fun callAiApi(userMessage: String): String? {
        // Try direct OpenAI first when user has API key
        val openAiKey = getOpenAiApiKey()
        if (openAiKey.isNotBlank()) {
            val directResponse = callOpenAiDirectly()
            if (directResponse != null) return directResponse
        }

        // Convex first (primary), then Supabase fallback
        return try {
            callOpenAiViaConvex() ?: callOpenAiViaSupabase()
        } catch (e: Exception) {
            if (e.message?.contains("Unable to resolve host", ignoreCase = true) == true) {
                mainHandler.post {
                    val hint = if (openAiKey.isNotBlank()) "" else "\n\nTip: Tap the ⚙ icon to add your OpenAI API key for when the server is unreachable."
                    Toast.makeText(this, "Can't reach server. Check Wi‑Fi or mobile data.$hint", Toast.LENGTH_LONG).show()
                }
            }
            null
        }
    }

    private fun callOpenAiViaSupabase(): String? {
        return try {
            val apiKey = SupabaseConfig.SUPABASE_ANON_KEY
            val supabaseUrl = SupabaseConfig.SUPABASE_URL
            val url = URL("$supabaseUrl/functions/v1/ai-chat")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("apikey", apiKey)
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            connection.doOutput = true

            val messagesJson = StringBuilder("[")
            messagesJson.append("""{"role":"system","content":"You are a helpful, friendly AI assistant. Be concise. Use emojis occasionally. Respond in the same language the user writes in."}""")
            conversationHistory.takeLast(8).forEach { (role, content) ->
                val escaped = content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                messagesJson.append(""",{"role":"$role","content":"$escaped"}""")
            }
            messagesJson.append("]")
            val requestBody = """{"messages":$messagesJson}"""

            connection.outputStream.use { it.write(requestBody.toByteArray()) }

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().readText()
                parseAiResponse(responseText)?.let { return it }
            }
            connection.disconnect()
            null
        } catch (e: Exception) {
            android.util.Log.e("AIChatConfig", "Supabase AI call failed: ${e.message}")
            null
        }
    }

    private fun callOpenAiViaConvex(): String? {
        if (!ConvexConfig.USE_CONVEX_FOR_VOICE_WORKFLOW) return null
        if (ConvexConfig.CONVEX_SITE_URL.contains("YOUR_DEPLOYMENT")) return null
        return try {
            val url = URL(ConvexConfig.AI_CHAT_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            connection.doOutput = true

            val messagesJson = StringBuilder("[")
            messagesJson.append("""{"role":"system","content":"You are a helpful, friendly AI assistant. Be concise. Use emojis occasionally. Respond in the same language the user writes in."}""")
            conversationHistory.takeLast(8).forEach { (role, content) ->
                val escaped = content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                messagesJson.append(""",{"role":"$role","content":"$escaped"}""")
            }
            messagesJson.append("]")
            val requestBody = """{"messages":$messagesJson}"""
            connection.outputStream.use { it.write(requestBody.toByteArray()) }

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().readText()
                parseAiResponse(responseText)?.let { return it }
            }
            connection.disconnect()
            null
        } catch (e: Exception) {
            android.util.Log.e("AIChatConfig", "Convex AI call failed: ${e.message}")
            null
        }
    }

    private fun callOpenAiDirectly(): String? {
        val apiKey = getOpenAiApiKey()
        if (apiKey.isBlank()) return null
        return try {
            val url = URL("https://api.openai.com/v1/chat/completions")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            connection.doOutput = true

            val messagesJson = StringBuilder("[")
            messagesJson.append("""{"role":"system","content":"You are a helpful, friendly AI assistant. Be concise. Use emojis occasionally. Respond in the same language the user writes in."}""")
            conversationHistory.takeLast(8).forEach { (role, content) ->
                val escaped = content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                messagesJson.append(""",{"role":"$role","content":"$escaped"}""")
            }
            messagesJson.append("]")
            val requestBody = """{"model":"gpt-4o-mini","messages":$messagesJson,"max_tokens":1000,"temperature":0.7}"""

            connection.outputStream.use { it.write(requestBody.toByteArray()) }

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().readText()
                parseAiResponse(responseText)?.let { return it }
            }
            connection.disconnect()
            null
        } catch (e: Exception) {
            android.util.Log.e("AIChatConfig", "OpenAI direct call failed: ${e.message}")
            null
        }
    }

    private fun parseAiResponse(responseText: String): String? {
        return try {
            val json = org.json.JSONObject(responseText)
            listOf("content", "response", "message", "text").forEach { key ->
                val value = json.optString(key, "")
                if (value.isNotBlank()) return value
            }
            val choices = json.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val message = choices.getJSONObject(0).optJSONObject("message")
                message?.optString("content", "")?.takeIf { it.isNotBlank() }?.let { return it }
            }
            null
        } catch (_: Exception) {
            val patterns = listOf(
                Regex(""""content"\s*:\s*"((?:[^"\\]|\\.)*)""""),
                Regex(""""response"\s*:\s*"((?:[^"\\]|\\.)*)""""),
                Regex(""""message"\s*:\s*"((?:[^"\\]|\\.)*)""""),
                Regex(""""text"\s*:\s*"((?:[^"\\]|\\.)*)"""")
            )
            for (pattern in patterns) {
                pattern.find(responseText)?.groupValues?.getOrNull(1)?.let { match ->
                    return match.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")
                }
            }
            null
        }
    }

    private fun showApiKeyDialog() {
        val prefs = getSharedPreferences("deltavoice_prefs", Context.MODE_PRIVATE)
        val currentKey = prefs.getString("openai_api_key", "") ?: ""

        val input = EditText(this).apply {
            setText(currentKey)
            hint = getString(R.string.ai_chat_api_key_hint)
            setPadding(48, 32, 48, 32)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(Color.parseColor("#E5E7EB"))
            setHintTextColor(Color.parseColor("#6B7280"))
        }

        AlertDialog.Builder(this, R.style.Theme_DeltaVoice_Dialog)
            .setTitle(R.string.ai_chat_api_key_title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val key = input.text.toString().trim()
                prefs.edit().putString("openai_api_key", key).apply()
                Toast.makeText(this, if (key.isNotBlank()) R.string.ai_chat_api_key_saved else R.string.ai_chat_api_key_removed, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showInternetRequiredSnackbar(messageResId: Int) {
        val snackbar = Snackbar.make(
            findViewById(android.R.id.content),
            getString(messageResId),
            Snackbar.LENGTH_LONG
        ).apply {
            setAction(R.string.internet_required_action_settings) {
                try {
                    startActivity(android.content.Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                    startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            setActionTextColor(Color.parseColor("#A78BFA"))
        }
        snackbar.view.setBackgroundColor(Color.parseColor("#1F2937"))
        snackbar.show()
    }

    private fun getFallbackResponse(message: String): String {
        val lower = message.lowercase()
        return when {
            lower.contains("hello") || lower.contains("hi") || lower.contains("hey") ->
                "Hello! 👋 How can I help you today?"
            lower.contains("thank") -> "You're welcome! 😊 Anything else?"
            lower.contains("help") -> "I can help with writing, translations, questions, and ideas. What do you need?"
            else -> "I'm having trouble connecting. Please check your internet and try again. In the meantime, try: \"Write an email about...\" or \"Translate X to Spanish\""
        }
    }
}
