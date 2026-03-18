package com.deltavoice.predict

import java.util.concurrent.ConcurrentHashMap

/**
 * Symmetric Delete spelling correction algorithm.
 * Pre-computes delete variants at index time for O(1) lookup at query time.
 * ~100x faster than naive Levenshtein scan for large dictionaries.
 *
 * Based on SymSpell algorithm:
 * - At index time: for each word, generate all deletes within edit distance 2
 * - At query time: generate all deletes of the input, look up in the pre-computed map
 * - Candidate words that share a delete variant are within edit distance 2
 */
internal class SymmetricDeleteCorrector(private val maxEditDistance: Int = 2) {

    private val dictionary = ConcurrentHashMap<String, Int>()
    private val deleteMap = ConcurrentHashMap<String, MutableSet<String>>()
    private val maxWordLength = 50

    fun addWord(word: String, frequency: Int = 1) {
        val w = word.lowercase()
        if (w.length > maxWordLength) return
        dictionary[w] = (dictionary[w] ?: 0) + frequency

        val deletes = generateDeletes(w, maxEditDistance)
        for (del in deletes) {
            deleteMap.getOrPut(del) { ConcurrentHashMap.newKeySet() }.add(w)
        }
    }

    fun contains(word: String): Boolean = dictionary.containsKey(word.lowercase())

    fun getFrequency(word: String): Int = dictionary[word.lowercase()] ?: 0

    /**
     * Find corrections for an input word, ranked by edit distance then frequency.
     * Returns list of (word, editDistance, frequency) triples.
     */
    fun lookup(
        input: String,
        maxResults: Int = 5,
        proximityScorer: ((String, String) -> Float)? = null
    ): List<CorrectionCandidate> {
        val inputLower = input.lowercase()
        if (inputLower.length > maxWordLength) return emptyList()

        if (dictionary.containsKey(inputLower)) return emptyList()

        val candidates = mutableMapOf<String, CorrectionCandidate>()

        val inputDeletes = generateDeletes(inputLower, maxEditDistance)
        val allCandidateKeys = inputDeletes + inputLower

        for (variant in allCandidateKeys) {
            val matches = deleteMap[variant] ?: continue
            for (word in matches) {
                if (candidates.containsKey(word)) continue
                val dist = damerauLevenshtein(inputLower, word)
                if (dist <= maxEditDistance) {
                    val freq = dictionary[word] ?: 1
                    val proxScore = proximityScorer?.invoke(inputLower, word) ?: 0f
                    candidates[word] = CorrectionCandidate(word, dist, freq, proxScore)
                }
            }
        }

        return candidates.values
            .sortedWith(compareBy<CorrectionCandidate> { it.editDistance }
                .thenByDescending { it.proximityScore }
                .thenByDescending { it.frequency })
            .take(maxResults)
    }

    /**
     * Get the best auto-correction candidate (if any).
     * Returns null if the word is correctly spelled or no good correction exists.
     */
    fun getBestCorrection(
        input: String,
        proximityScorer: ((String, String) -> Float)? = null,
        minFrequencyForAutoCorrect: Int = 5
    ): String? {
        val results = lookup(input, 3, proximityScorer)
        if (results.isEmpty()) return null

        val best = results[0]
        if (best.editDistance == 1 && best.frequency >= minFrequencyForAutoCorrect) {
            return best.word
        }
        if (best.editDistance == 2 && best.frequency >= minFrequencyForAutoCorrect * 5 && best.proximityScore > 0.5f) {
            return best.word
        }
        return null
    }

    private fun generateDeletes(word: String, distance: Int): Set<String> {
        val results = mutableSetOf<String>()
        if (distance < 1 || word.isEmpty()) return results

        for (i in word.indices) {
            val delete = word.removeRange(i, i + 1)
            results.add(delete)
            if (distance > 1) {
                results.addAll(generateDeletes(delete, distance - 1))
            }
        }
        return results
    }

    /**
     * Damerau-Levenshtein distance (handles transpositions).
     */
    private fun damerauLevenshtein(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        val dp = Array(len1 + 1) { IntArray(len2 + 1) }

        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j

        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
                if (i > 1 && j > 1 && s1[i - 1] == s2[j - 2] && s1[i - 2] == s2[j - 1]) {
                    dp[i][j] = minOf(dp[i][j], dp[i - 2][j - 2] + cost)
                }
            }
        }
        return dp[len1][len2]
    }

    fun clear() {
        dictionary.clear()
        deleteMap.clear()
    }

    fun wordCount(): Int = dictionary.size
}

data class CorrectionCandidate(
    val word: String,
    val editDistance: Int,
    val frequency: Int,
    val proximityScore: Float = 0f
)
