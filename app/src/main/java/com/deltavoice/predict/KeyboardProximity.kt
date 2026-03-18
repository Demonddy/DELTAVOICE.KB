package com.deltavoice.predict

/**
 * Keyboard proximity scoring for autocorrect.
 * Maps each key to its physically adjacent keys on QWERTY and other layouts.
 * Used to boost correction candidates where errors are likely due to fat-finger misses.
 */
object KeyboardProximity {

    private val qwertyAdjacency: Map<Char, Set<Char>> = buildMap {
        put('q', setOf('w', 'a', 's'))
        put('w', setOf('q', 'e', 'a', 's', 'd'))
        put('e', setOf('w', 'r', 's', 'd', 'f'))
        put('r', setOf('e', 't', 'd', 'f', 'g'))
        put('t', setOf('r', 'y', 'f', 'g', 'h'))
        put('y', setOf('t', 'u', 'g', 'h', 'j'))
        put('u', setOf('y', 'i', 'h', 'j', 'k'))
        put('i', setOf('u', 'o', 'j', 'k', 'l'))
        put('o', setOf('i', 'p', 'k', 'l'))
        put('p', setOf('o', 'l'))
        put('a', setOf('q', 'w', 's', 'z'))
        put('s', setOf('q', 'w', 'e', 'a', 'd', 'z', 'x'))
        put('d', setOf('w', 'e', 'r', 's', 'f', 'x', 'c'))
        put('f', setOf('e', 'r', 't', 'd', 'g', 'c', 'v'))
        put('g', setOf('r', 't', 'y', 'f', 'h', 'v', 'b'))
        put('h', setOf('t', 'y', 'u', 'g', 'j', 'b', 'n'))
        put('j', setOf('y', 'u', 'i', 'h', 'k', 'n', 'm'))
        put('k', setOf('u', 'i', 'o', 'j', 'l', 'm'))
        put('l', setOf('i', 'o', 'p', 'k'))
        put('z', setOf('a', 's', 'x'))
        put('x', setOf('s', 'd', 'z', 'c'))
        put('c', setOf('d', 'f', 'x', 'v'))
        put('v', setOf('f', 'g', 'c', 'b'))
        put('b', setOf('g', 'h', 'v', 'n'))
        put('n', setOf('h', 'j', 'b', 'm'))
        put('m', setOf('j', 'k', 'n'))
    }

    private val azertyAdjacency: Map<Char, Set<Char>> = buildMap {
        put('a', setOf('z', 'q', 's'))
        put('z', setOf('a', 'e', 'q', 's', 'd'))
        put('e', setOf('z', 'r', 's', 'd', 'f'))
        put('r', setOf('e', 't', 'd', 'f', 'g'))
        put('t', setOf('r', 'y', 'f', 'g', 'h'))
        put('y', setOf('t', 'u', 'g', 'h', 'j'))
        put('u', setOf('y', 'i', 'h', 'j', 'k'))
        put('i', setOf('u', 'o', 'j', 'k', 'l'))
        put('o', setOf('i', 'p', 'k', 'l'))
        put('p', setOf('o', 'l', 'm'))
        put('q', setOf('a', 'z', 's', 'w'))
        put('s', setOf('a', 'z', 'e', 'q', 'd', 'w', 'x'))
        put('d', setOf('z', 'e', 'r', 's', 'f', 'x', 'c'))
        put('f', setOf('e', 'r', 't', 'd', 'g', 'c', 'v'))
        put('g', setOf('r', 't', 'y', 'f', 'h', 'v', 'b'))
        put('h', setOf('t', 'y', 'u', 'g', 'j', 'b', 'n'))
        put('j', setOf('y', 'u', 'i', 'h', 'k', 'n'))
        put('k', setOf('u', 'i', 'o', 'j', 'l'))
        put('l', setOf('i', 'o', 'p', 'k', 'm'))
        put('m', setOf('p', 'l'))
        put('w', setOf('q', 's', 'x'))
        put('x', setOf('s', 'd', 'w', 'c'))
        put('c', setOf('d', 'f', 'x', 'v'))
        put('v', setOf('f', 'g', 'c', 'b'))
        put('b', setOf('g', 'h', 'v', 'n'))
        put('n', setOf('h', 'j', 'b'))
    }

    private val qwertzAdjacency: Map<Char, Set<Char>> = buildMap {
        put('q', setOf('w', 'a', 's'))
        put('w', setOf('q', 'e', 'a', 's', 'd'))
        put('e', setOf('w', 'r', 's', 'd', 'f'))
        put('r', setOf('e', 't', 'd', 'f', 'g'))
        put('t', setOf('r', 'z', 'f', 'g', 'h'))
        put('z', setOf('t', 'u', 'g', 'h', 'j'))
        put('u', setOf('z', 'i', 'h', 'j', 'k'))
        put('i', setOf('u', 'o', 'j', 'k', 'l'))
        put('o', setOf('i', 'p', 'k', 'l'))
        put('p', setOf('o', 'l'))
        put('a', setOf('q', 'w', 's', 'y'))
        put('s', setOf('q', 'w', 'e', 'a', 'd', 'y', 'x'))
        put('d', setOf('w', 'e', 'r', 's', 'f', 'x', 'c'))
        put('f', setOf('e', 'r', 't', 'd', 'g', 'c', 'v'))
        put('g', setOf('r', 't', 'z', 'f', 'h', 'v', 'b'))
        put('h', setOf('t', 'z', 'u', 'g', 'j', 'b', 'n'))
        put('j', setOf('z', 'u', 'i', 'h', 'k', 'n', 'm'))
        put('k', setOf('u', 'i', 'o', 'j', 'l', 'm'))
        put('l', setOf('i', 'o', 'p', 'k'))
        put('y', setOf('a', 's', 'x'))
        put('x', setOf('s', 'd', 'y', 'c'))
        put('c', setOf('d', 'f', 'x', 'v'))
        put('v', setOf('f', 'g', 'c', 'b'))
        put('b', setOf('g', 'h', 'v', 'n'))
        put('n', setOf('h', 'j', 'b', 'm'))
        put('m', setOf('j', 'k', 'n'))
    }

    private val layoutForLang: Map<String, Map<Char, Set<Char>>> = mapOf(
        "en" to qwertyAdjacency,
        "es" to qwertyAdjacency,
        "it" to qwertyAdjacency,
        "pt" to qwertyAdjacency,
        "nl" to qwertyAdjacency,
        "sv" to qwertyAdjacency,
        "no" to qwertyAdjacency,
        "da" to qwertyAdjacency,
        "fi" to qwertyAdjacency,
        "pl" to qwertyAdjacency,
        "tr" to qwertyAdjacency,
        "id" to qwertyAdjacency,
        "ms" to qwertyAdjacency,
        "tl" to qwertyAdjacency,
        "vi" to qwertyAdjacency,
        "sw" to qwertyAdjacency,
        "ro" to qwertyAdjacency,
        "hu" to qwertyAdjacency,
        "cs" to qwertyAdjacency,
        "sk" to qwertyAdjacency,
        "hr" to qwertyAdjacency,
        "sl" to qwertyAdjacency,
        "et" to qwertyAdjacency,
        "lv" to qwertyAdjacency,
        "lt" to qwertyAdjacency,
        "ca" to qwertyAdjacency,
        "gl" to qwertyAdjacency,
        "eu" to qwertyAdjacency,
        "zh" to qwertyAdjacency,
        "ja" to qwertyAdjacency,
        "fr" to azertyAdjacency,
        "de" to qwertzAdjacency,
    )

    fun getAdjacencyMap(lang: String): Map<Char, Set<Char>> =
        layoutForLang[lang] ?: qwertyAdjacency

    /**
     * Score how likely a substitution is based on keyboard proximity.
     * Returns value between 0.0 (not adjacent) and 1.0 (adjacent keys).
     */
    fun proximityScore(typed: Char, candidate: Char, lang: String = "en"): Float {
        if (typed == candidate) return 1.0f
        val adj = getAdjacencyMap(lang)
        return if (adj[typed.lowercaseChar()]?.contains(candidate.lowercaseChar()) == true) 0.8f else 0.0f
    }

    /**
     * Compute overall proximity score between typed word and candidate word.
     * Higher score = more likely the candidate is what the user intended.
     * Considers character-by-character proximity on the keyboard.
     */
    fun wordProximityScore(typed: String, candidate: String, lang: String = "en"): Float {
        val t = typed.lowercase()
        val c = candidate.lowercase()
        if (t == c) return 1.0f
        if (t.isEmpty() || c.isEmpty()) return 0f

        val minLen = minOf(t.length, c.length)
        val maxLen = maxOf(t.length, c.length)
        if (maxLen - minLen > 2) return 0f

        var score = 0f
        var matches = 0
        val adj = getAdjacencyMap(lang)

        var ti = 0
        var ci = 0
        while (ti < t.length && ci < c.length) {
            when {
                t[ti] == c[ci] -> {
                    score += 1.0f
                    matches++
                    ti++; ci++
                }
                adj[t[ti]]?.contains(c[ci]) == true -> {
                    score += 0.6f
                    matches++
                    ti++; ci++
                }
                t.length > c.length -> {
                    ti++
                }
                c.length > t.length -> {
                    ci++
                }
                else -> {
                    ti++; ci++
                }
            }
        }

        return if (maxLen > 0) score / maxLen else 0f
    }
}
