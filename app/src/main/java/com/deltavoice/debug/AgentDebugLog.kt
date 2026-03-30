package com.deltavoice.debug

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Session debug NDJSON → ingest (debug mode). Falls back to Logcat if HTTP fails.
 */
object AgentDebugLog {

    private const val SESSION = "e0761c"
    private const val TAG = "AGENT_DEBUG"

    // #region agent log
    fun log(hypothesisId: String, location: String, message: String, data: Map<String, Any?> = emptyMap()) {
        val body = JSONObject().apply {
            put("sessionId", SESSION)
            put("hypothesisId", hypothesisId)
            put("location", location)
            put("message", message)
            put("timestamp", System.currentTimeMillis())
            put("data", JSONObject().apply {
                data.forEach { (k, v) ->
                    when (v) {
                        null -> put(k, JSONObject.NULL)
                        is Int -> put(k, v)
                        is Long -> put(k, v)
                        is Boolean -> put(k, v)
                        is String -> put(k, v)
                        else -> put(k, v.toString())
                    }
                }
            })
        }
        val payload = body.toString()
        Thread {
            try {
                postToIngest(payload)
            } catch (_: Exception) {
                Log.w(TAG, "AGENT_LOG:$payload")
            }
        }.start()
    }

    private fun postToIngest(body: String) {
        val urls = listOf(
            "http://10.0.2.2:7242/ingest/fa21dfb1-8310-431f-93e4-f304708f8263",
            "http://127.0.0.1:7242/ingest/fa21dfb1-8310-431f-93e4-f304708f8263"
        )
        for (url in urls) {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("X-Debug-Session-Id", SESSION)
                conn.doOutput = true
                conn.connectTimeout = 2000
                conn.readTimeout = 2000
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                if (conn.responseCode in 200..299) return
            } catch (_: Exception) {
            }
        }
        Log.w(TAG, "AGENT_LOG:$body")
    }
    // #endregion
}
