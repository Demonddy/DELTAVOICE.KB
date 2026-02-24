package com.deltavoice.predict

import java.util.concurrent.ConcurrentHashMap

/**
 * Bigram and trigram next-word prediction.
 * Maps (prevWord) -> [(nextWord, count)] and (w1, w2) -> [(nextWord, count)].
 * Memory-efficient: only stores observed n-grams.
 */
internal class NgramModel {

    private companion object {
        private const val MAX_NEXT_WORDS = 50  // Cap per context for memory efficiency
    }

    // bigram: prev -> list of (next, count) sorted by count desc
    private val bigrams = ConcurrentHashMap<String, MutableList<Pair<String, Int>>>()
    // trigram: (w1, w2) -> list of (next, count)
    private val trigrams = ConcurrentHashMap<String, MutableList<Pair<String, Int>>>()

    private fun trigramKey(w1: String, w2: String) = "$w1|$w2"

    fun addBigram(prev: String, next: String) {
        if (prev.isBlank() || next.isBlank()) return
        val p = prev.lowercase()
        val n = next.lowercase()
        bigrams.getOrPut(p) { mutableListOf() }.let { list ->
            val idx = list.indexOfFirst { it.first == n }
            if (idx >= 0) {
                val (word, count) = list[idx]
                list[idx] = word to (count + 1)
            } else {
                list.add(n to 1)
            }
            list.sortByDescending { it.second }
            if (list.size > MAX_NEXT_WORDS) list.subList(MAX_NEXT_WORDS, list.size).clear()
        }
    }

    fun addTrigram(w1: String, w2: String, next: String) {
        if (w1.isBlank() || w2.isBlank() || next.isBlank()) return
        val key = trigramKey(w1.lowercase(), w2.lowercase())
        val n = next.lowercase()
        trigrams.getOrPut(key) { mutableListOf() }.let { list ->
            val idx = list.indexOfFirst { it.first == n }
            if (idx >= 0) {
                val (word, count) = list[idx]
                list[idx] = word to (count + 1)
            } else {
                list.add(n to 1)
            }
            list.sortByDescending { it.second }
            if (list.size > MAX_NEXT_WORDS) list.subList(MAX_NEXT_WORDS, list.size).clear()
        }
    }

    fun getNextWordsBigram(prev: String, limit: Int = 5): List<String> {
        val p = prev.lowercase()
        return bigrams[p]?.take(limit)?.map { it.first } ?: emptyList()
    }

    fun getNextWordsTrigram(w1: String, w2: String, limit: Int = 5): List<String> {
        val key = trigramKey(w1.lowercase(), w2.lowercase())
        return trigrams[key]?.take(limit)?.map { it.first } ?: emptyList()
    }

    fun recordSequence(words: List<String>) {
        for (i in 0 until words.size - 1) {
            addBigram(words[i], words[i + 1])
            if (i >= 1) {
                addTrigram(words[i - 1], words[i], words[i + 1])
            }
        }
    }
}
