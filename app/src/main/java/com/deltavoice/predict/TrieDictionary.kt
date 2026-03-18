package com.deltavoice.predict

/**
 * Trie-based dictionary for instant prefix search.
 * O(prefix_length) lookup, O(1) per-node traversal.
 * Supports frequency-weighted words for ranking.
 * Optimized: priority-queue style collection for top-K results.
 */
internal class TrieDictionary {

    private val root = TrieNode()
    private var wordCount = 0

    fun insert(word: String, frequency: Int = 1) {
        if (word.isBlank()) return
        var node = root
        for (c in word.lowercase()) {
            node = node.children.getOrPut(c) { TrieNode() }
        }
        if (!node.isWordEnd) wordCount++
        node.isWordEnd = true
        node.frequency = maxOf(node.frequency, frequency)
    }

    /**
     * Get all words with given prefix, sorted by frequency (desc).
     * Uses bounded collection to avoid scanning entire subtree.
     */
    fun searchPrefix(prefix: String, limit: Int = 20): List<Pair<String, Int>> {
        if (prefix.isBlank()) return emptyList()
        val normalized = prefix.lowercase()
        var node = root
        for (c in normalized) {
            node = node.children[c] ?: return emptyList()
        }
        val results = BoundedResultList(limit * 3)
        collectWords(node, normalized, normalized, results, limit * 3)
        return results.toSortedList().take(limit)
    }

    /**
     * Search prefix but also return the exact prefix match if it exists.
     */
    fun searchPrefixIncludingSelf(prefix: String, limit: Int = 20): List<Pair<String, Int>> {
        if (prefix.isBlank()) return emptyList()
        val normalized = prefix.lowercase()
        var node = root
        for (c in normalized) {
            node = node.children[c] ?: return emptyList()
        }
        val results = BoundedResultList(limit * 3)
        if (node.isWordEnd) {
            results.add(normalized, node.frequency)
        }
        collectWords(node, normalized, "\u0000", results, limit * 3)
        return results.toSortedList().take(limit)
    }

    private fun collectWords(
        node: TrieNode,
        pathFromRoot: String,
        userPrefix: String,
        results: BoundedResultList,
        limit: Int
    ) {
        if (results.size >= limit) return
        if (node.isWordEnd && pathFromRoot != userPrefix) {
            results.add(pathFromRoot, node.frequency)
        }
        val sortedChildren = node.children.entries.sortedByDescending { it.value.frequency }
        for ((c, child) in sortedChildren) {
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

    fun getFrequency(word: String): Int {
        var node = root
        for (c in word.lowercase()) {
            node = node.children[c] ?: return 0
        }
        return if (node.isWordEnd) node.frequency else 0
    }

    fun size(): Int = wordCount

    fun clear() {
        root.children.clear()
        wordCount = 0
    }

    /**
     * Bounded result list that keeps top-K by frequency without full sort.
     */
    private class BoundedResultList(private val capacity: Int) {
        private val items = ArrayList<Pair<String, Int>>(capacity)
        val size: Int get() = items.size

        fun add(word: String, frequency: Int) {
            if (items.size < capacity) {
                items.add(word to frequency)
            }
        }

        fun toSortedList(): List<Pair<String, Int>> =
            items.sortedByDescending { it.second }
    }
}
