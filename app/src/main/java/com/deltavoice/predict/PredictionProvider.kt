package com.deltavoice.predict

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.deltavoice.PredictiveWordList
import com.deltavoice.debug.AgentDebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Singleton prediction provider. Isolates prediction logic from UI.
 * Loads each language dictionary on demand (no full-catalog preload — that blocked the UI for
 * a long time or failed with OOM, so suggestions never appeared).
 * Compute runs on Default; callback posted to Main - never blocks key press path.
 */
object PredictionProvider {

    private const val TAG = "PredictionProvider"

    private val engine = PredictiveTextEngine()
    private val scope = CoroutineScope(Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val loadedLangs = mutableSetOf<String>()
    private val loadLock = Any()

    /** Build trie + corrector for [lang] once; thread-safe. */
    private fun ensureLanguageLoaded(lang: String) {
        synchronized(loadLock) {
            if (loadedLangs.contains(lang)) return
            val freqMap = PredictiveWordList.getWordsWithFrequency(lang)
            engine.loadDictionaryWithFrequencies(lang, freqMap)
            loadedLangs.add(lang)
        }
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
            try {
                ensureLanguageLoaded(lang)
                val result = engine.getPredictions(
                    prefix = prefix,
                    contextBefore = contextBefore,
                    lang = lang,
                    limit = limit,
                    includeCorrections = includeCorrections
                )
                // #region agent log
                AgentDebugLog.log(
                    "H3",
                    "PredictionProvider.getPredictions",
                    "engine_result",
                    mapOf(
                        "lang" to lang,
                        "prefixLen" to prefix.length,
                        "suggestionCount" to result.suggestions.size,
                        "latencyMs" to result.latencyMs
                    )
                )
                // #endregion
                mainHandler.post { onResult(result) }
            } catch (e: Exception) {
                Log.e(TAG, "getPredictions failed for lang=$lang", e)
                mainHandler.post {
                    onResult(PredictionResult(suggestions = emptyList(), autoCorrect = null, latencyMs = 0L))
                }
            }
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
        ensureLanguageLoaded(lang)
        return engine.getCorrections(word, lang, limit)
    }

    /**
     * Check if a word is in the dictionary.
     */
    fun isKnownWord(word: String, lang: String): Boolean {
        ensureLanguageLoaded(lang)
        return engine.isKnownWord(word, lang)
    }

    /**
     * Get the auto-correct replacement for a word (used when pressing space).
     * Returns null if word is known or no good correction.
     */
    fun getAutoCorrect(word: String, lang: String): String? {
        ensureLanguageLoaded(lang)
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
            try {
                ensureLanguageLoaded(lang)
            } catch (e: Exception) {
                Log.e(TAG, "switchLanguage preload failed for lang=$lang", e)
            }
        }
        engine.switchLanguage(lang)
    }

    fun setNeuralPredictor(predictor: NeuralPredictor?) {
        engine.setNeuralPredictor(predictor)
    }
}
