package com.deltavoice.predict

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Gboard-class predictive text engine.
 * - Trie prefix search with frequency ranking
 * - SymmetricDelete O(1) autocorrect (vs O(n) Levenshtein scan)
 * - Keyboard proximity scoring for fat-finger correction
 * - Bigram/trigram context-aware next-word prediction
 * - User word learning (recency + frequency boost)
 * - Auto-correct candidate selection for space-bar commit
 * - Target: <10ms per prediction on mid-range device
 */
class PredictiveTextEngine {

    private val trieByLang = mutableMapOf<String, TrieDictionary>()
    private val correctorByLang = mutableMapOf<String, SymmetricDeleteCorrector>()
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

    private fun getCorrector(lang: String): SymmetricDeleteCorrector =
        correctorByLang.getOrPut(lang) { SymmetricDeleteCorrector(maxEditDistance = 2) }

    private fun getNgram(lang: String): NgramModel =
        ngramByLang.getOrPut(lang) { NgramModel() }

    private fun getCache(lang: String): UserWordCache =
        cacheByLang.getOrPut(lang) { UserWordCache(500) }

    /**
     * Load dictionary words with frequency data. Call on init or language switch.
     * Populates both the Trie (for prefix search) and the SymmetricDelete index (for correction).
     */
    fun loadDictionary(lang: String, words: Collection<String>, frequencies: Map<String, Int>? = null) {
        val trie = getTrie(lang)
        val corrector = getCorrector(lang)
        trie.clear()
        corrector.clear()
        words.forEach { w ->
            val freq = frequencies?.get(w.lowercase(Locale.ROOT)) ?: frequencies?.get(w) ?: 1
            trie.insert(w, freq)
            corrector.addWord(w, freq)
        }
    }

    /**
     * Load dictionary from frequency map (preferred path). Each entry is word -> frequency.
     */
    fun loadDictionaryWithFrequencies(lang: String, wordFrequencies: Map<String, Int>) {
        val trie = getTrie(lang)
        val corrector = getCorrector(lang)
        trie.clear()
        corrector.clear()
        wordFrequencies.forEach { (word, freq) ->
            trie.insert(word, freq)
            corrector.addWord(word, freq)
        }
    }

    fun recordWord(lang: String, word: String) {
        if (word.isBlank()) return
        val w = word.lowercase(Locale.ROOT)
        getCache(lang).addWord(w)
        getTrie(lang).insert(w, 50)
        getCorrector(lang).addWord(w, 50)
    }

    fun recordSequence(lang: String, words: List<String>) {
        getNgram(lang).recordSequence(words.filter { it.isNotBlank() })
    }

    /**
     * Main prediction entry point. Returns PredictionResult with:
     * - suggestions: ordered list of completions/corrections
     * - autoCorrect: the best correction if user likely misspelled (null if correct)
     * - latencyMs: computation time
     */
    suspend fun getPredictions(
        prefix: String,
        contextBefore: List<String>,
        lang: String,
        limit: Int = 5,
        includeCorrections: Boolean = true
    ): PredictionResult = withContext(Dispatchers.Default) {
        val start = System.nanoTime()
        val suggestions = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        var autoCorrect: String? = null

        if (prefix.isBlank()) {
            autoCorrect = null
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
            val normalizedPrefix = prefix.lowercase(Locale.ROOT)
            val trie = getTrie(lang)
            val cache = getCache(lang)
            val corrector = getCorrector(lang)

            val isKnownWord = trie.contains(normalizedPrefix)

            // 1. Trie prefix completions (ranked by frequency + recency)
            val trieResults = trie.searchPrefixIncludingSelf(normalizedPrefix, limit * 3)
            val scored = trieResults.map { (word, freq) ->
                val recency = cache.getRecencyScore(word)
                val ngramBoost = getNgramBoostForWord(word, contextBefore, lang)
                val totalScore = freq * 10 + recency * 80 + ngramBoost * 30
                word to totalScore
            }.sortedByDescending { it.second }

            scored.take(limit).forEach { (w, _) ->
                if (seen.add(w)) suggestions.add(w)
            }

            // 2. Auto-correction (if word is likely misspelled)
            if (includeCorrections && !isKnownWord && normalizedPrefix.length >= 2) {
                val proximityScorer = { typed: String, candidate: String ->
                    KeyboardProximity.wordProximityScore(typed, candidate, lang)
                }
                val bestCorrection = corrector.getBestCorrection(
                    normalizedPrefix,
                    proximityScorer,
                    minFrequencyForAutoCorrect = 3
                )
                if (bestCorrection != null) {
                    autoCorrect = bestCorrection
                    if (seen.add(bestCorrection)) {
                        suggestions.add(0, bestCorrection)
                        if (suggestions.size > limit) suggestions.removeAt(suggestions.size - 1)
                    }
                }

                // 3. Additional correction candidates
                if (suggestions.size < limit) {
                    val corrections = corrector.lookup(normalizedPrefix, limit, proximityScorer)
                    corrections.forEach { candidate ->
                        if (seen.add(candidate.word)) {
                            suggestions.add(candidate.word)
                        }
                    }
                }
            }

            // 4. If we still have few results, try fuzzy trie search with nearby prefixes
            if (suggestions.size < 2 && normalizedPrefix.length >= 3) {
                tryNearbyPrefixes(normalizedPrefix, lang, limit, seen, suggestions)
            }
        }

        neuralPredictor?.takeIf { it.isAvailable() }?.let { np ->
            val neural = np.getNeuralSuggestions(contextBefore + listOf(prefix), limit)
            neural.forEach { w -> if (seen.add(w)) suggestions.add(w) }
        }

        val latency = (System.nanoTime() - start) / 1_000_000
        PredictionResult(
            suggestions = suggestions.take(limit),
            autoCorrect = autoCorrect,
            latencyMs = latency
        )
    }

    /**
     * Get autocorrect suggestions for misspelled word. Fast path via SymmetricDelete.
     */
    suspend fun getCorrections(
        word: String,
        lang: String,
        limit: Int = 5
    ): List<String> = withContext(Dispatchers.Default) {
        if (word.length < 2) return@withContext emptyList()
        val corrector = getCorrector(lang)
        if (corrector.contains(word.lowercase(Locale.ROOT))) return@withContext emptyList()

        val proximityScorer = { typed: String, candidate: String ->
            KeyboardProximity.wordProximityScore(typed, candidate, lang)
        }
        corrector.lookup(word.lowercase(Locale.ROOT), limit, proximityScorer).map { it.word }
    }

    /**
     * Check if a word is in the dictionary.
     */
    fun isKnownWord(word: String, lang: String): Boolean {
        return getTrie(lang).contains(word.lowercase(Locale.ROOT)) ||
               getCorrector(lang).contains(word.lowercase(Locale.ROOT))
    }

    /**
     * Get the best auto-correct for a word (for space-bar auto-correction).
     * Returns null if word is known or no good correction exists.
     */
    fun getAutoCorrect(word: String, lang: String): String? {
        val lower = word.lowercase(Locale.ROOT)
        if (lower.length < 2) return null
        if (getTrie(lang).contains(lower)) return null

        val corrector = getCorrector(lang)
        val proximityScorer = { typed: String, candidate: String ->
            KeyboardProximity.wordProximityScore(typed, candidate, lang)
        }
        return corrector.getBestCorrection(lower, proximityScorer, minFrequencyForAutoCorrect = 3)
    }

    private fun getNgramBoostForWord(word: String, contextBefore: List<String>, lang: String): Int {
        if (contextBefore.isEmpty()) return 0
        val ngram = getNgram(lang)
        val prev = contextBefore.takeLast(2)
        val bigramWords = if (prev.isNotEmpty()) ngram.getNextWordsBigram(prev.last(), 20) else emptyList()
        val trigramWords = if (prev.size >= 2) ngram.getNextWordsTrigram(prev[0], prev[1], 20) else emptyList()

        var boost = 0
        if (word in bigramWords) boost += 5
        if (word in trigramWords) boost += 10
        return boost
    }

    /**
     * Try searching with nearby prefixes (handle typo in first 1-2 chars).
     */
    private fun tryNearbyPrefixes(prefix: String, lang: String, limit: Int, seen: MutableSet<String>, results: MutableList<String>) {
        val adjacency = KeyboardProximity.getAdjacencyMap(lang)
        val trie = getTrie(lang)
        val firstChar = prefix[0]
        val nearby = adjacency[firstChar] ?: return
        for (alt in nearby) {
            val altPrefix = alt + prefix.substring(1)
            val altResults = trie.searchPrefix(altPrefix, limit)
            altResults.forEach { (word, _) ->
                if (seen.add(word) && results.size < limit) {
                    results.add(word)
                }
            }
            if (results.size >= limit) break
        }
    }

    fun switchLanguage(lang: String) {
        lastLang = lang
    }

    fun clearCache(lang: String) {
        cacheByLang[lang]?.clear()
    }
}
