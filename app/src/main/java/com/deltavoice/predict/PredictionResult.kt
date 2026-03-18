package com.deltavoice.predict

/**
 * Result of a prediction request.
 * @param suggestions Ordered list of word suggestions (best first)
 * @param autoCorrect The auto-correction candidate if the word is misspelled, null if word is correct
 * @param latencyMs Time taken to compute (for profiling)
 */
data class PredictionResult(
    val suggestions: List<String>,
    val autoCorrect: String? = null,
    val latencyMs: Long = 0L
)
