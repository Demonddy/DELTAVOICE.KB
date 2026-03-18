package com.deltavoice.predict

import android.os.Handler
import android.os.Looper
import com.deltavoice.PredictiveWordList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Singleton prediction provider. Isolates prediction logic from UI.
 * Loads all supported language dictionaries with frequency data on first use.
 * Compute runs on Default; callback posted to Main - never blocks key press path.
 */
object PredictionProvider {

    private val engine = PredictiveTextEngine()
    private val scope = CoroutineScope(Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var loadJob: Job? = null
    private val loadedLangs = mutableSetOf<String>()

    init {
        loadJob = scope.launch {
            PredictiveWordList.getSupportedLanguages().forEach { lang ->
                loadLanguage(lang)
            }
        }
    }

    private fun loadLanguage(lang: String) {
        if (loadedLangs.contains(lang)) return
        val freqMap = PredictiveWordList.getWordsWithFrequency(lang)
        engine.loadDictionaryWithFrequencies(lang, freqMap)
        loadedLangs.add(lang)
    }

    /**
     * Get predictions including auto-correct candidate.
     * Non-blocking; compute on Default, callback posted to Main.
     */
    fun getPredictions(
        prefix: String,
        contextBefore: List<String>,
        lang: String,
        limit: Int = 5,
        includeCorrections: Boolean = true,
        onResult: (PredictionResult) -> Unit
    ) {
        scope.launch {
            loadJob?.join()
            if (!loadedLangs.contains(lang)) loadLanguage(lang)
            val result = engine.getPredictions(
                prefix = prefix,
                contextBefore = contextBefore,
                lang = lang,
                limit = limit,
                includeCorrections = includeCorrections
            )
            mainHandler.post { onResult(result) }
        }
    }

    /**
     * Overload for backward compatibility - returns just suggestions list.
     */
    fun getPredictionsCompat(
        prefix: String,
        contextBefore: List<String>,
        lang: String,
        limit: Int = 5,
        includeCorrections: Boolean = true,
        onResult: (List<String>) -> Unit
    ) {
        getPredictions(prefix, contextBefore, lang, limit, includeCorrections) { result ->
            onResult(result.suggestions)
        }
    }

    /**
     * Get corrections for misspelling.
     */
    suspend fun getCorrections(word: String, lang: String, limit: Int = 5): List<String> {
        loadJob?.join()
        return engine.getCorrections(word, lang, limit)
    }

    /**
     * Check if a word is in the dictionary.
     */
    fun isKnownWord(word: String, lang: String): Boolean {
        return engine.isKnownWord(word, lang)
    }

    /**
     * Get the auto-correct replacement for a word (used when pressing space).
     * Returns null if word is known or no good correction.
     */
    fun getAutoCorrect(word: String, lang: String): String? {
        return engine.getAutoCorrect(word, lang)
    }

    /**
     * Record committed word for n-gram and cache.
     */
    fun recordWord(lang: String, word: String) {
        engine.recordWord(lang, word)
    }

    /**
     * Record word sequence for n-gram model.
     */
    fun recordSequence(lang: String, words: List<String>) {
        engine.recordSequence(lang, words)
    }

    fun switchLanguage(lang: String) {
        scope.launch {
            if (!loadedLangs.contains(lang)) loadLanguage(lang)
        }
        engine.switchLanguage(lang)
    }

    fun setNeuralPredictor(predictor: NeuralPredictor?) {
        engine.setNeuralPredictor(predictor)
    }
}
