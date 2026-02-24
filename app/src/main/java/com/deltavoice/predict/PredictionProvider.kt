package com.deltavoice.predict

import com.deltavoice.PredictiveWordList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Singleton prediction provider. Isolates prediction logic from UI.
 * Ensures <15ms via background dispatch. Loads dictionaries on first use.
 */
object PredictionProvider {

    private val engine = PredictiveTextEngine()
    private val scope = CoroutineScope(Dispatchers.Main)
    private var loadJob: Job? = null

    private val supportedLangs = listOf(
        "en", "es", "fr", "de", "it", "pt", "ru", "ar", "hi", "ja", "ko", "zh"
    )

    init {
        loadJob = scope.launch {
            withContext(Dispatchers.Default) {
                supportedLangs.forEach { lang ->
                    engine.loadDictionary(lang, PredictiveWordList.getWordsForLanguage(lang))
                }
            }
        }
    }

    /**
     * Get predictions. Non-blocking; callback on Main.
     */
    fun getPredictions(
        prefix: String,
        contextBefore: List<String>,
        lang: String,
        limit: Int = 5,
        includeCorrections: Boolean = true,
        onResult: (List<String>) -> Unit
    ) {
        scope.launch {
            loadJob?.join()
            val result = engine.getPredictions(
                prefix = prefix,
                contextBefore = contextBefore,
                lang = lang,
                limit = limit,
                includeCorrections = includeCorrections
            )
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
        engine.switchLanguage(lang)
    }

    /** For future TFLite integration. */
    fun setNeuralPredictor(predictor: NeuralPredictor?) {
        engine.setNeuralPredictor(predictor)
    }
}
