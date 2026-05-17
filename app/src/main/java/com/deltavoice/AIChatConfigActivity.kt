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
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.deltavoice.ui.ShimmerView
import android.graphics.Color
import com.deltavoice.config.AiProvider
import com.deltavoice.config.AiProviderConfig
import com.deltavoice.config.ConvexConfig
import com.deltavoice.config.SupabaseConfig
import com.deltavoice.util.NetworkUtils
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException

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
        addMessage(getString(R.string.ai_chat_welcome), false)
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

        chatInput.setText("")
        addMessage(message, true)
        conversationHistory.add("user" to message)
        btnSend.isEnabled = false

        if (!NetworkUtils.isConnected(this) && getAiApiKey().isBlank()) {
            showInternetRequiredSnackbar(R.string.internet_required_ai_chat)
            val reply = getFallbackResponse(message)
            addMessage(reply, false)
            conversationHistory.add("assistant" to reply)
            btnSend.isEnabled = true
            return
        }

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

    private fun getAiApiKey(): String = AiProviderConfig.getApiKey(this)

    // #region agent log
    private fun aidLog(hypothesisId: String, location: String, message: String, data: Map<String, Any?>) {
        try {
            val jo = JSONObject()
            jo.put("sessionId", "8355ac")
            jo.put("hypothesisId", hypothesisId)
            jo.put("location", location)
            jo.put("message", message)
            jo.put("timestamp", System.currentTimeMillis())
            val dataObj = JSONObject()
            data.forEach { (k, v) -> dataObj.put(k, v ?: JSONObject.NULL) }
            jo.put("data", dataObj)
            val line = jo.toString() + "\n"
            File(filesDir, "debug-8355ac.log").appendText(line)
            android.util.Log.d("AIDEBUG", line)
        } catch (_: Exception) {}
    }
    // #endregion

    private fun callAiApi(userMessage: String): String? {
        // Build history in the format AiProviderConfig expects
        val historyMaps = conversationHistory.map { (role, content) ->
            mapOf("role" to role, "content" to content)
        }

        // Try user-selected provider first when an API key is configured
        if (AiProviderConfig.hasDirectKey(this)) {
            val directResponse = AiProviderConfig.callProvider(this, historyMaps)
            val provider = AiProviderConfig.getProvider(this)
            aidLog("H3", "callAiApi", "direct_provider", mapOf("provider" to provider.name, "nonNull" to (directResponse != null)))
            if (directResponse != null) return directResponse
        } else {
            aidLog("H3", "callAiApi", "direct_provider_skipped", mapOf("hadKey" to false))
        }

        // Convex first (DeepSeek on server); Supabase second
        return try {
            val c = callOpenAiViaConvex()
            if (c != null) {
                aidLog("H1", "callAiApi", "result", mapOf("source" to "convex", "ok" to true))
                return c
            }
            val s = callOpenAiViaSupabase()
            aidLog("H2", "callAiApi", "result", mapOf("source" to "supabase", "nonNull" to (s != null)))
            s
        } catch (e: Exception) {
            aidLog("H4", "callAiApi", "exception", mapOf("type" to e.javaClass.simpleName, "msg" to (e.message?.take(120) ?: "")))
            if (e.message?.contains("Unable to resolve host", ignoreCase = true) == true) {
                mainHandler.post {
                    val hasKey = AiProviderConfig.hasDirectKey(this)
                    val hint = if (hasKey) "" else "\n\nTip: Tap the settings icon to add your API key for when the server is unreachable."
                    Toast.makeText(this, getString(R.string.cant_reach_server_with_hint, hint), Toast.LENGTH_LONG).show()
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

            val code = connection.responseCode
            val responseText = if (code in 200..299) {
                connection.inputStream.bufferedReader().readText()
            } else {
                connection.errorStream?.bufferedReader()?.readText().orEmpty()
            }
            val parsed = parseAiResponse(responseText)
            aidLog(
                "H2",
                "callOpenAiViaSupabase",
                "http",
                mapOf(
                    "code" to code,
                    "bodyPrefix" to responseText.take(280),
                    "parsedNonNull" to (parsed != null)
                )
            )
            connection.disconnect()
            if (code in 200..299) parsed else null
        } catch (e: Exception) {
            aidLog("H2", "callOpenAiViaSupabase", "exception", mapOf("msg" to (e.message?.take(120) ?: "")))
            if (e is UnknownHostException) {
                android.util.Log.d("AIChatConfig", "Supabase skipped (DNS): ${e.message}")
            } else {
                android.util.Log.w("AIChatConfig", "Supabase AI unreachable: ${e.message}")
            }
            null
        }
    }

    private fun callOpenAiViaConvex(): String? {
        if (!ConvexConfig.USE_CONVEX_FOR_VOICE_WORKFLOW) {
            aidLog("H1", "callOpenAiViaConvex", "skipped", mapOf("reason" to "USE_CONVEX false"))
            return null
        }
        if (ConvexConfig.CONVEX_SITE_URL.contains("YOUR_DEPLOYMENT")) {
            aidLog("H1", "callOpenAiViaConvex", "skipped", mapOf("reason" to "placeholder URL"))
            return null
        }
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

            val code = connection.responseCode
            val responseText = if (code in 200..299) {
                connection.inputStream.bufferedReader().readText()
            } else {
                connection.errorStream?.bufferedReader()?.readText().orEmpty()
            }
            val parsed = parseAiResponse(responseText)
            aidLog(
                "H1",
                "callOpenAiViaConvex",
                "http",
                mapOf(
                    "code" to code,
                    "host" to url.host,
                    "bodyPrefix" to responseText.take(280),
                    "parsedNonNull" to (parsed != null)
                )
            )
            connection.disconnect()
            if (code in 200..299) parsed else null
        } catch (e: Exception) {
            aidLog("H1", "callOpenAiViaConvex", "exception", mapOf("msg" to (e.message?.take(120) ?: "")))
            android.util.Log.e("AIChatConfig", "Convex AI call failed: ${e.message}")
            null
        }
    }

    private fun parseAiResponse(responseText: String): String? {
        return try {
            val json = org.json.JSONObject(responseText)
            if (json.has("success") && !json.optBoolean("success", true)) {
                val errBody = json.optString("content", "")
                if (errBody.isNotBlank()) return errBody
                return null
            }
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
        val currentProvider = AiProviderConfig.getProvider(this)
        val currentKey = AiProviderConfig.getApiKey(this)
        val currentModel = AiProviderConfig.getModel(this)
        val currentEndpoint = AiProviderConfig.getEndpoint(this)

        val providers = AiProvider.entries.toTypedArray()
        val dp = resources.displayMetrics.density

        val scrollWrapper = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * dp).toInt(), (16 * dp).toInt(), (24 * dp).toInt(), (8 * dp).toInt())
        }
        scrollWrapper.addView(container)

        // Provider label
        container.addView(TextView(this).apply {
            text = getString(R.string.ai_chat_provider_label)
            setTextColor(Color.parseColor("#9CA3AF"))
            textSize = 13f
            setPadding(0, 0, 0, (4 * dp).toInt())
        })

        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@AIChatConfigActivity,
                android.R.layout.simple_spinner_dropdown_item,
                providers.map { it.displayName }
            )
            setSelection(providers.indexOf(currentProvider).coerceAtLeast(0))
        }
        container.addView(spinner)

        // Endpoint URL label + input (only for CUSTOM)
        val endpointLabel = TextView(this).apply {
            text = getString(R.string.ai_chat_endpoint_label)
            setTextColor(Color.parseColor("#9CA3AF"))
            textSize = 13f
            setPadding(0, (12 * dp).toInt(), 0, (4 * dp).toInt())
        }
        container.addView(endpointLabel)

        val endpointInput = EditText(this).apply {
            setText(currentEndpoint)
            hint = getString(R.string.ai_chat_endpoint_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setTextColor(Color.parseColor("#E5E7EB"))
            setHintTextColor(Color.parseColor("#6B7280"))
            setBackgroundColor(Color.parseColor("#1F2937"))
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
        }
        container.addView(endpointInput)

        // API key label
        val keyLabel = TextView(this).apply {
            text = getString(R.string.ai_chat_api_key_label)
            setTextColor(Color.parseColor("#9CA3AF"))
            textSize = 13f
            setPadding(0, (12 * dp).toInt(), 0, (4 * dp).toInt())
        }
        container.addView(keyLabel)

        val keyInput = EditText(this).apply {
            setText(currentKey)
            hint = currentProvider.hint
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(Color.parseColor("#E5E7EB"))
            setHintTextColor(Color.parseColor("#6B7280"))
            setBackgroundColor(Color.parseColor("#1F2937"))
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
        }
        container.addView(keyInput)

        // Model label
        val modelLabel = TextView(this).apply {
            text = getString(R.string.ai_chat_model_label)
            setTextColor(Color.parseColor("#9CA3AF"))
            textSize = 13f
            setPadding(0, (12 * dp).toInt(), 0, (4 * dp).toInt())
        }
        container.addView(modelLabel)

        val modelInput = EditText(this).apply {
            setText(currentModel)
            hint = getString(R.string.ai_chat_model_hint)
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(Color.parseColor("#E5E7EB"))
            setHintTextColor(Color.parseColor("#6B7280"))
            setBackgroundColor(Color.parseColor("#1F2937"))
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
        }
        container.addView(modelInput)

        fun updateFieldVisibility(provider: AiProvider) {
            val keyVis = if (provider.requiresKey) View.VISIBLE else View.GONE
            keyLabel.visibility = keyVis
            keyInput.visibility = keyVis
            modelLabel.visibility = keyVis
            modelInput.visibility = keyVis

            val endpointVis = if (provider.requiresEndpoint) View.VISIBLE else View.GONE
            endpointLabel.visibility = endpointVis
            endpointInput.visibility = endpointVis

            if (provider.requiresKey) {
                keyInput.hint = provider.hint
                if (modelInput.text.isNullOrBlank() || providers.any { it.defaultModel == modelInput.text.toString() }) {
                    modelInput.setText(provider.defaultModel)
                }
            }
        }
        updateFieldVisibility(currentProvider)

        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, pos: Int, id: Long) {
                updateFieldVisibility(providers[pos])
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        AlertDialog.Builder(this, R.style.Theme_DeltaVoice_Dialog)
            .setTitle(R.string.ai_chat_api_key_title)
            .setView(scrollWrapper)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val selected = providers[spinner.selectedItemPosition]
                val key = keyInput.text.toString().trim()
                val model = modelInput.text.toString().trim()
                val endpoint = endpointInput.text.toString().trim()
                AiProviderConfig.save(this, selected, key, model, endpoint)
                val msgRes = if (selected == AiProvider.BUILT_IN) R.string.ai_chat_api_key_removed else R.string.ai_chat_api_key_saved
                Toast.makeText(this, msgRes, Toast.LENGTH_SHORT).show()
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
                getString(R.string.ai_chat_fallback_hello)
            lower.contains("thank") -> getString(R.string.ai_chat_fallback_thanks)
            lower.contains("help") -> getString(R.string.ai_chat_fallback_help)
            else -> getString(R.string.ai_chat_fallback_offline)
        }
    }
}
