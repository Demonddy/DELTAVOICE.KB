package com.deltavoice.predict

/**
 * Placeholder interface for TensorFlow Lite neural prediction.
 * Future: Implement with TFLite model for next-word prediction.
 *
 * Scalability path:
 * 1. Load .tflite model from assets
 * 2. Tokenize input context (last N words)
 * 3. Run inference on background thread
 * 4. Decode output to word IDs, map to vocabulary
 * 5. Merge with Trie/n-gram results via weighted combination
 * 6. Consider on-device training for personalization
 */
interface NeuralPredictor {
    fun getNeuralSuggestions(context: List<String>, limit: Int): List<String>
    fun isAvailable(): Boolean
}
