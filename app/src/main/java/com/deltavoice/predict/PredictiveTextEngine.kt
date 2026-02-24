package com.deltavoice.predict

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * High-performance predictive text engine for IME.
 * - Trie-based prefix search
 * - Bigram/trigram next-word prediction
 * - Frequency + recency scoring
 * - User word cache
 * - Runs on background thread (<15ms target)
 * - Modular for TFLite integration
 */
class PredictiveTextEngine {

    private val trieByLang = mutableMapOf<String, TrieDictionary>()
    private val ngramByLang = mutableMapOf<String, NgramModel>()
    private val cacheByLang = mutableMapOf<String, UserWordCache>()
    private var neuralPredictor: NeuralPredictor? = null

    @Volatile
    private var lastLang: String? = null

    fun setNeuralPredictor(predictor: NeuralPredictor?) {
        neuralPredictor = predictor
    }

    private fun getTrie(lang: String): TrieDictionary =
        trieByLang.getOrPut(lang) { TrieDictionary() }

    private fun getNgram(lang: String): NgramModel =
        ngramByLang.getOrPut(lang) { NgramModel() }

    private fun getCache(lang: String): UserWordCache =
        cacheByLang.getOrPut(lang) { UserWordCache(200) }

    /**
     * Load dictionary words into Trie. Call on init or language switch.
     */
    fun loadDictionary(lang: String, words: Collection<String>, frequencies: Map<String, Int>? = null) {
        val trie = getTrie(lang)
        trie.clear()
        words.forEach { w ->
            val freq = frequencies?.get(w.lowercase()) ?: 1
            trie.insert(w, freq)
        }
    }

    /**
     * Record typed word for n-gram and cache. Call when user commits a word.
     */
    fun recordWord(lang: String, word: String) {
        if (word.isBlank()) return
        getCache(lang).addWord(word)
    }

    /**
     * Record word sequence for n-gram model.
     */
    fun recordSequence(lang: String, words: List<String>) {
        getNgram(lang).recordSequence(words.filter { it.isNotBlank() })
    }

    /**
     * Get predictions. Runs on Dispatchers.Default. Target <15ms.
     */
    suspend fun getPredictions(
        prefix: String,
        contextBefore: List<String>,
        lang: String,
        limit: Int = 5,
        includeCorrections: Boolean = true
    ): PredictionResult = withContext(Dispatchers.Default) {
        val start = System.currentTimeMillis()
        val suggestions = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        val normalizedPrefix = prefix.lowercase(Locale.ROOT)

        // 1. Next-word prediction (when prefix is empty, use n-gram)
        if (prefix.isBlank()) {
            val prev = contextBefore.takeLast(2)
            when (prev.size) {
                2 -> getNgram(lang).getNextWordsTrigram(prev[0], prev[1], limit).forEach { w ->
                    if (seen.add(w)) suggestions.add(w)
                }
                1 -> getNgram(lang).getNextWordsBigram(prev[0], limit).forEach { w ->
                    if (seen.add(w)) suggestions.add(w)
                }
                else -> getCache(lang).getRecentWords(limit).forEach { w ->
                    if (seen.add(w)) suggestions.add(w)
                }
            }
        } else {
            // 2. Trie prefix search
            val trieResults = getTrie(lang).searchPrefix(prefix, limit * 2)
            val cache = getCache(lang)
            val scored = trieResults.map { (word, freq) ->
                val recency = cache.getRecencyScore(word)
                word to (freq * 10 + recency * 50)  // Recency boost
            }.sortedByDescending { it.second }
            scored.take(limit).forEach { (w, _) ->
                if (seen.add(w)) suggestions.add(w)
            }
            // 3. Autocorrect fallback when no prefix matches
            if (includeCorrections && suggestions.isEmpty() && prefix.length >= 2) {
                getCorrections(prefix, lang, limit).forEach { w ->
                    if (seen.add(w)) suggestions.add(w)
                }
            }
        }

        // 3. Optional: neural suggestions (future)
        neuralPredictor?.takeIf { it.isAvailable() }?.let { np ->
            val neural = np.getNeuralSuggestions(contextBefore + listOf(prefix), limit)
            neural.forEach { w -> if (seen.add(w)) suggestions.add(w) }
        }

        val latency = System.currentTimeMillis() - start
        PredictionResult(suggestions.take(limit), latency)
    }

    /**
     * Get autocorrect suggestions for misspelled word.
     */
    suspend fun getCorrections(
        word: String,
        lang: String,
        limit: Int = 5
    ): List<String> = withContext(Dispatchers.Default) {
        if (word.length < 2) return@withContext emptyList()
        val trie = getTrie(lang)
        if (trie.contains(word.lowercase())) return@withContext emptyList()
        val normalized = word.lowercase(Locale.ROOT)
        val allWords = trie.searchPrefix("", 500).map { it.first }
        allWords
            .filter { levenshtein(normalized, it.lowercase()) <= 2 }
            .sortedBy { levenshtein(normalized, it.lowercase()) }
            .take(limit)
    }

    private fun levenshtein(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        val dp = Array(len1 + 1) { IntArray(len2 + 1) }
        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j
        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
            }
        }
        return dp[len1][len2]
    }

    fun switchLanguage(lang: String) {
        lastLang = lang
    }

    fun clearCache(lang: String) {
        cacheByLang[lang]?.clear()
    }
}
