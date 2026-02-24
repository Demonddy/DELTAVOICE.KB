package com.deltavoice.predict

/**
 * Trie-based dictionary for instant prefix search.
 * O(prefix_length) lookup, O(1) per-node traversal.
 * Supports frequency-weighted words for ranking.
 */
internal class TrieDictionary {

    private val root = TrieNode()

    fun insert(word: String, frequency: Int = 1) {
        if (word.isBlank()) return
        var node = root
        for (c in word.lowercase()) {
            node = node.children.getOrPut(c) { TrieNode() }
        }
        node.isWordEnd = true
        node.frequency = (node.frequency + frequency).coerceAtLeast(1)
    }

    /**
     * Get all words with given prefix, sorted by frequency (desc).
     * Limit results for performance.
     */
    fun searchPrefix(prefix: String, limit: Int = 20): List<Pair<String, Int>> {
        if (prefix.isBlank()) return emptyList()
        val normalized = prefix.lowercase()
        var node = root
        for (c in normalized) {
            node = node.children[c] ?: return emptyList()
        }
        val results = mutableListOf<Pair<String, Int>>()
        collectWords(node, normalized, normalized, results, limit)
        return results.sortedByDescending { it.second }.take(limit)
    }

    private fun collectWords(
        node: TrieNode,
        pathFromRoot: String,
        userPrefix: String,
        results: MutableList<Pair<String, Int>>,
        limit: Int
    ) {
        if (results.size >= limit) return
        if (node.isWordEnd && pathFromRoot != userPrefix) {
            results.add(pathFromRoot to node.frequency)
        }
        for ((c, child) in node.children) {
            if (results.size >= limit) break
            collectWords(child, pathFromRoot + c, userPrefix, results, limit)
        }
    }

    fun contains(word: String): Boolean {
        var node = root
        for (c in word.lowercase()) {
            node = node.children[c] ?: return false
        }
        return node.isWordEnd
    }

    /** Get all words (for correction fallback). Limited for performance. */
    fun getAllWords(limit: Int = 1000): List<String> {
        val results = mutableListOf<Pair<String, Int>>()
        collectWords(root, "", "\u0000", results, limit)  // sentinel: never exclude
        return results.map { it.first }
    }

    fun clear() {
        root.children.clear()
    }
}
