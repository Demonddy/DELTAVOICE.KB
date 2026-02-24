package com.deltavoice.predict

import java.util.LinkedHashMap

/**
 * LRU cache for recent user-typed words.
 * Boosts suggestions from words the user has typed before.
 * Bounded size for memory efficiency on low-end devices.
 */
internal class UserWordCache(private val maxSize: Int = 200) {

    private val cache = object : LinkedHashMap<String, Int>(maxSize + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>?) = size > maxSize
    }

    @Synchronized
    fun addWord(word: String) {
        if (word.isBlank() || word.length < 2) return
        val w = word.lowercase()
        cache[w] = (cache[w] ?: 0) + 1
    }

    @Synchronized
    fun getRecencyScore(word: String): Int = cache[word.lowercase()] ?: 0

    @Synchronized
    fun getRecentWords(limit: Int = 10): List<String> =
        cache.keys.toList().takeLast(limit).reversed()

    @Synchronized
    fun clear() = cache.clear()
}
