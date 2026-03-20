package com.deltavoice

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Manages feature overlay windows for the floating bubble.
 * Each feature uses the same layout/dimensions as the keyboard.
 * Output goes to clipboard (no InputConnection in overlay context).
 */
class OverlayFeatureController(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val overlayViews = mutableListOf<View>()

    private val typeOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
        @Suppress("DEPRECATION")
        WindowManager.LayoutParams.TYPE_PHONE
    }

    fun dismissAll() {
        overlayViews.toList().forEach { removeOverlay(it) }
        overlayViews.clear()
    }

    private fun addOverlay(view: View, params: WindowManager.LayoutParams): View {
        windowManager.addView(view, params)
        overlayViews.add(view)
        return view
    }

    private fun removeOverlay(view: View) {
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {}
        overlayViews.remove(view)
    }

    private fun createOverlayParams(width: Int, height: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            width,
            height,
            typeOverlay,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
    }

    private fun dp(pixels: Int): Int = (pixels * context.resources.displayMetrics.density).toInt()

    fun showMoreOptions(onDismiss: () -> Unit) {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.overlay_more_options, null)
        val container = FrameLayout(context).apply {
            setBackgroundColor(0xE6000000.toInt())
            setOnClickListener { }
        }
        container.addView(view)

        val params = createOverlayParams(
            (context.resources.displayMetrics.widthPixels * 0.9).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        addOverlay(container, params)

        view.findViewById<ImageButton>(R.id.btn_more_calculator)?.setOnClickListener {
            removeOverlay(container)
            showCalculator()
        }
        view.findViewById<ImageButton>(R.id.btn_more_dictionary)?.setOnClickListener {
            removeOverlay(container)
            showDictionary()
        }
        view.findViewById<Button>(R.id.btn_more_back)?.setOnClickListener {
            removeOverlay(container)
            onDismiss()
        }
        container.setOnClickListener {
            removeOverlay(container)
            onDismiss()
        }
        view.setOnClickListener { } // Prevent click from propagating to container
    }

    fun showCalculator() {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.calculator_layout, null)
        val container = FrameLayout(context).apply {
            setBackgroundColor(0xE6000000.toInt())
        }
        container.addView(view)
        container.setOnClickListener { removeOverlay(container) }
        view.setOnClickListener { } // Prevent taps on calculator from closing

        val params = createOverlayParams(
            (context.resources.displayMetrics.widthPixels * 0.95).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        addOverlay(container, params)

        val calcState = CalculatorState()
        setupCalculatorLogic(view, calcState) {
            removeOverlay(container)
        }
    }

    private data class CalculatorState(
        var expression: StringBuilder = StringBuilder(),
        var result: String = "0",
        var lastWasOperator: Boolean = false,
        var openBrackets: Int = 0
    )

    private fun setupCalculatorLogic(view: View, state: CalculatorState, onClose: () -> Unit) {
        val exprView = view.findViewById<TextView>(R.id.calc_expression)
        val resultView = view.findViewById<TextView>(R.id.calc_result)

        fun updateDisplay() {
            exprView?.text = state.expression.toString()
            resultView?.text = state.result
        }

        fun evaluate() {
            try {
                val expr = state.expression.toString()
                    .replace("×", "*")
                    .replace("÷", "/")
                    .replace("−", "-")
                    .replace("%", "/100")
                if (expr.isEmpty()) {
                    state.result = "0"
                    return
                }
                var evalExpr = expr
                repeat(state.openBrackets) { evalExpr += ")" }
                val res = evaluateMathExpression(evalExpr)
                state.result = if (res == res.toLong().toDouble()) {
                    res.toLong().toString()
                } else {
                    String.format("%.8f", res).trimEnd('0').trimEnd('.')
                }
            } catch (_: Exception) {}
        }

        val numberIds = listOf(
            R.id.calc_0, R.id.calc_1, R.id.calc_2, R.id.calc_3, R.id.calc_4,
            R.id.calc_5, R.id.calc_6, R.id.calc_7, R.id.calc_8, R.id.calc_9
        )
        numberIds.forEachIndexed { index, id ->
            view.findViewById<Button>(id)?.setOnClickListener {
                state.expression.append(index.toString())
                state.lastWasOperator = false
                updateDisplay()
                evaluate()
            }
        }

        view.findViewById<Button>(R.id.calc_plus)?.setOnClickListener {
            if (state.expression.isNotEmpty() && !state.lastWasOperator) {
                state.expression.append("+")
                state.lastWasOperator = true
            } else if (state.lastWasOperator && state.expression.isNotEmpty()) {
                state.expression.setCharAt(state.expression.length - 1, '+')
            }
            updateDisplay()
            evaluate()
        }
        view.findViewById<Button>(R.id.calc_minus)?.setOnClickListener {
            if (state.expression.isEmpty()) {
                state.expression.append("-")
            } else if (!state.lastWasOperator) {
                state.expression.append("−")
                state.lastWasOperator = true
            } else if (state.expression.isNotEmpty()) {
                state.expression.setCharAt(state.expression.length - 1, '−')
            }
            updateDisplay()
            evaluate()
        }
        view.findViewById<Button>(R.id.calc_multiply)?.setOnClickListener {
            if (state.expression.isNotEmpty() && !state.lastWasOperator) {
                state.expression.append("×")
                state.lastWasOperator = true
            } else if (state.lastWasOperator && state.expression.isNotEmpty()) {
                state.expression.setCharAt(state.expression.length - 1, '×')
            }
            updateDisplay()
            evaluate()
        }
        view.findViewById<Button>(R.id.calc_divide)?.setOnClickListener {
            if (state.expression.isNotEmpty() && !state.lastWasOperator) {
                state.expression.append("÷")
                state.lastWasOperator = true
            } else if (state.lastWasOperator && state.expression.isNotEmpty()) {
                state.expression.setCharAt(state.expression.length - 1, '÷')
            }
            updateDisplay()
            evaluate()
        }
        view.findViewById<Button>(R.id.calc_percent)?.setOnClickListener {
            state.expression.append("%")
            state.lastWasOperator = false
            updateDisplay()
            evaluate()
        }
        view.findViewById<Button>(R.id.calc_decimal)?.setOnClickListener {
            val expr = state.expression.toString()
            val lastNumber = expr.split(Regex("[+\\-×÷]")).lastOrNull() ?: ""
            if (!lastNumber.contains(".")) {
                if (state.expression.isEmpty() || state.lastWasOperator) {
                    state.expression.append("0")
                }
                state.expression.append(".")
                state.lastWasOperator = false
            }
            updateDisplay()
            evaluate()
        }
        view.findViewById<Button>(R.id.calc_brackets)?.setOnClickListener {
            if (state.openBrackets > 0 && !state.lastWasOperator && state.expression.isNotEmpty()) {
                state.expression.append(")")
                state.openBrackets--
            } else {
                if (state.expression.isNotEmpty() && !state.lastWasOperator) {
                    state.expression.append("×")
                }
                state.expression.append("(")
                state.openBrackets++
            }
            state.lastWasOperator = true
            updateDisplay()
            evaluate()
        }
        view.findViewById<Button>(R.id.calc_clear)?.setOnClickListener {
            state.expression.clear()
            state.result = "0"
            state.lastWasOperator = false
            state.openBrackets = 0
            updateDisplay()
        }
        view.findViewById<Button>(R.id.calc_equals)?.setOnClickListener {
            try {
                evaluate()
                state.expression.clear()
                state.expression.append(state.result)
                state.lastWasOperator = false
                state.openBrackets = 0
                updateDisplay()
            } catch (_: Exception) {}
        }
        view.findViewById<Button>(R.id.calc_keyboard)?.setOnClickListener { onClose() }
        view.findViewById<Button>(R.id.calc_insert)?.setOnClickListener {
            val text = state.result
            clipboardManager.setPrimaryClip(ClipData.newPlainText("calculator", text))
            Toast.makeText(context, "Copied: $text", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<Button>(R.id.calc_delete)?.setOnClickListener {
            if (state.expression.isNotEmpty()) {
                val lastChar = state.expression[state.expression.length - 1]
                state.expression.deleteCharAt(state.expression.length - 1)
                state.lastWasOperator = when (lastChar) {
                    '+', '−', '×', '÷', '%', '(' -> true
                    else -> false
                }
                if (lastChar == '(') state.openBrackets--
                if (lastChar == ')') state.openBrackets++
                updateDisplay()
                evaluate()
            }
        }
    }

    private fun evaluateMathExpression(expression: String): Double {
        return object {
            var pos = -1
            var ch = 0
            fun nextChar() {
                ch = if (++pos < expression.length) expression[pos].code else -1
            }
            fun eat(charToEat: Int): Boolean {
                while (ch == ' '.code) nextChar()
                if (ch == charToEat) {
                    nextChar()
                    return true
                }
                return false
            }
            fun parse(): Double {
                nextChar()
                val x = parseExpression()
                if (pos < expression.length) throw RuntimeException("Unexpected: ${ch.toChar()}")
                return x
            }
            fun parseExpression(): Double {
                var x = parseTerm()
                while (true) {
                    when {
                        eat('+'.code) -> x += parseTerm()
                        eat('-'.code) -> x -= parseTerm()
                        else -> return x
                    }
                }
            }
            fun parseTerm(): Double {
                var x = parseFactor()
                while (true) {
                    when {
                        eat('*'.code) -> x *= parseFactor()
                        eat('/'.code) -> x /= parseFactor()
                        else -> return x
                    }
                }
            }
            fun parseFactor(): Double {
                if (eat('+'.code)) return parseFactor()
                if (eat('-'.code)) return -parseFactor()
                var x: Double
                val startPos = pos
                if (eat('('.code)) {
                    x = parseExpression()
                    eat(')'.code)
                } else if (ch in '0'.code..'9'.code || ch == '.'.code) {
                    while (ch in '0'.code..'9'.code || ch == '.'.code) nextChar()
                    x = expression.substring(startPos, pos).toDouble()
                } else {
                    throw RuntimeException("Unexpected: ${ch.toChar()}")
                }
                return x
            }
        }.parse()
    }

    fun showDictionary() {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.overlay_dictionary, null)
        val container = FrameLayout(context).apply {
            setBackgroundColor(0xE6000000.toInt())
        }
        container.addView(view)
        container.setOnClickListener { removeOverlay(container) }
        view.setOnClickListener { } // Prevent taps on dictionary from closing

        val params = createOverlayParams(
            (context.resources.displayMetrics.widthPixels * 0.95).toInt(),
            (context.resources.displayMetrics.heightPixels * 0.7).toInt()
        )
        addOverlay(container, params)

        val searchEdit = view.findViewById<EditText>(R.id.dict_search_edit)
        val placeholder = view.findViewById<TextView>(R.id.dict_placeholder)
        val wordTitle = view.findViewById<TextView>(R.id.dict_word_title)
        val phonetic = view.findViewById<TextView>(R.id.dict_phonetic)
        val partOfSpeech = view.findViewById<TextView>(R.id.dict_part_of_speech)
        val definition = view.findViewById<TextView>(R.id.dict_definition)
        val example = view.findViewById<TextView>(R.id.dict_example)

        view.findViewById<ImageButton>(R.id.dict_close_btn)?.setOnClickListener {
            removeOverlay(container)
        }
        view.findViewById<Button>(R.id.dict_search_btn)?.setOnClickListener {
            val word = searchEdit.text.toString().trim()
            if (word.isEmpty()) {
                Toast.makeText(context, "Please type a word to search", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            scope.launch {
                val result = fetchDictionaryDefinition(word)
                if (result != null) {
                    placeholder?.visibility = View.GONE
                    wordTitle?.visibility = View.VISIBLE
                    wordTitle?.text = result.word
                    phonetic?.visibility = if (result.phonetic != null) View.VISIBLE else View.GONE
                    phonetic?.text = result.phonetic ?: ""
                    partOfSpeech?.visibility = if (result.partOfSpeech != null) View.VISIBLE else View.GONE
                    partOfSpeech?.text = result.partOfSpeech ?: ""
                    definition?.visibility = View.VISIBLE
                    definition?.text = result.definition ?: ""
                    example?.visibility = if (result.example != null) View.VISIBLE else View.GONE
                    example?.text = result.example ?: ""
                } else {
                    placeholder?.visibility = View.VISIBLE
                    placeholder?.text = "No definition found for \"$word\""
                    wordTitle?.visibility = View.GONE
                    phonetic?.visibility = View.GONE
                    partOfSpeech?.visibility = View.GONE
                    definition?.visibility = View.GONE
                    example?.visibility = View.GONE
                }
            }
        }
        view.findViewById<Button>(R.id.dict_copy_btn)?.setOnClickListener {
            val def = definition?.text?.toString() ?: ""
            val word = wordTitle?.text?.toString() ?: ""
            if (def.isNotEmpty() || word.isNotEmpty()) {
                val text = if (def.isNotEmpty()) "$word: $def" else word
                clipboardManager.setPrimaryClip(ClipData.newPlainText("dictionary", text))
                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private data class DictResult(
        val word: String,
        val phonetic: String?,
        val partOfSpeech: String?,
        val definition: String?,
        val example: String?
    )

    private suspend fun fetchDictionaryDefinition(word: String): DictResult? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.dictionaryapi.dev/api/v2/entries/en/$word")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                parseDictionaryResponse(response, word)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun parseDictionaryResponse(json: String, word: String): DictResult? {
        return try {
            val phonetic = Regex("\"phonetic\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)
            val partOfSpeech = Regex("\"partOfSpeech\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)
            val definition = Regex("\"definition\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)
            val example = Regex("\"example\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)
            if (definition != null) {
                DictResult(word, phonetic, partOfSpeech, definition, example)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun showClipboard() {
        val prefs = context.getSharedPreferences("clipboard_prefs", Context.MODE_PRIVATE)
        val stored = prefs.getString("clipboard_history", null) ?: ""
        val items = stored.split("\u001E").filter { it.isNotBlank() }

        val container = FrameLayout(context).apply {
            setBackgroundColor(0xE6000000.toInt())
        }
        val scroll = ScrollView(context)
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val title = TextView(context).apply {
            text = "Clipboard"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, dp(12))
        }
        layout.addView(title)
        if (items.isEmpty()) {
            val empty = TextView(context).apply {
                text = "No copied text yet"
                setTextColor(0xFF888888.toInt())
            }
            layout.addView(empty)
        } else {
            items.forEach { text ->
                val chip = Button(context).apply {
                    this.text = if (text.length > 50) text.take(50) + "…" else text
                    setTextColor(0xFFEDEFF4.toInt())
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    setBackgroundResource(R.drawable.glass_key_background)
                    setOnClickListener {
                        clipboardManager.setPrimaryClip(ClipData.newPlainText("clipboard", text))
                        Toast.makeText(context, "Copied! Paste with long-press", Toast.LENGTH_SHORT).show()
                        removeOverlay(container)
                    }
                }
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
                layout.addView(chip, lp)
            }
        }
        val closeBtn = Button(context).apply {
            text = "Close"
            setOnClickListener { removeOverlay(container) }
        }
        layout.addView(closeBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(16) })
        scroll.addView(layout)
        container.addView(scroll)
        val params = createOverlayParams(
            (context.resources.displayMetrics.widthPixels * 0.9).toInt(),
            (context.resources.displayMetrics.heightPixels * 0.6).toInt()
        )
        addOverlay(container, params)
    }

    fun showVideoRecording() {
        val intent = Intent(context, VideoRecordingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun showAiChat() {
        val intent = Intent(context, AIChatConfigActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun showAiWritingTools() {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        Toast.makeText(context, "Open keyboard for AI Writing Tools", Toast.LENGTH_SHORT).show()
    }

    fun showVoiceRecording() {
        val intent = Intent(context, VoiceProcessModeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
