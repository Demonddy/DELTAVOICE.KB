package com.deltavoice.predict

import java.util.concurrent.ConcurrentHashMap

/**
 * Trie node for O(prefix_length) prefix search.
 * Uses ConcurrentHashMap for thread-safe reads during prediction.
 * Memory-efficient: only stores branches that exist.
 */
internal class TrieNode {
    val children = ConcurrentHashMap<Char, TrieNode>()
    var isWordEnd = false
    var frequency: Int = 0  // Higher = more common word
}
