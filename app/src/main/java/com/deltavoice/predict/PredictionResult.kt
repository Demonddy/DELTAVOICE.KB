package com.deltavoice.predict

/**
 * Result of a prediction request.
 * @param suggestions Ordered list of word suggestions (best first)
 * @param latencyMs Time taken to compute (for profiling)
 */
data class PredictionResult(
    val suggestions: List<String>,
    val latencyMs: Long = 0L
)
