package com.deltavoice

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.deltavoice.config.SupabaseConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * AI Chat page - Chat with AI directly on this page.
 * No keyboard navigation required.
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
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_chat_config)

        chatMessages = findViewById(R.id.chat_messages)
        chatScroll = findViewById(R.id.chat_scroll)
        chatInput = findViewById(R.id.chat_input)
        btnSend = findViewById(R.id.btn_send)

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        addWelcomeMessage()

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

    private fun addMessage(text: String, isUser: Boolean) {
        val bubble = TextView(this).apply {
            this.text = text
            setPadding(48, 24, 48, 24)
            textSize = 15f
            setLineSpacing(4f, 1.2f)
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

        chatScroll.post { chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun sendMessage() {
        val message = chatInput.text.toString().trim()
        if (message.isBlank()) return

        chatInput.setText("")
        addMessage(message, true)
        conversationHistory.add("user" to message)
        btnSend.isEnabled = false

        activityScope.launch {
            val response = withContext(Dispatchers.IO) {
                callAiApi(message)
            }
            mainHandler.post {
                val reply = response ?: getFallbackResponse(message)
                addMessage(reply, false)
                conversationHistory.add("assistant" to reply)
                btnSend.isEnabled = true
            }
        }
    }

    private fun callAiApi(userMessage: String): String? {
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
                val patterns = listOf(
                    Regex(""""content"\s*:\s*"((?:[^"\\]|\\.)*)""""),
                    Regex(""""response"\s*:\s*"((?:[^"\\]|\\.)*)""""),
                    Regex(""""message"\s*:\s*"((?:[^"\\]|\\.)*)""""),
                    Regex(""""text"\s*:\s*"((?:[^"\\]|\\.)*)"""")
                )
                for (pattern in patterns) {
                    pattern.find(responseText)?.groupValues?.get(1)?.let { match ->
                        return match.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")
                    }
                }
            }
            connection.disconnect()
            null
        } catch (e: Exception) {
            if (e.message?.contains("Unable to resolve host", ignoreCase = true) == true) {
                mainHandler.post {
                    Toast.makeText(this, "Can't reach server. Check Wi‑Fi or mobile data.", Toast.LENGTH_LONG).show()
                }
            }
            null
        }
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
