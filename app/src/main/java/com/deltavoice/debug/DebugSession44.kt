package com.deltavoice.debug

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * NDJSON debug logging for session 44ebda (theme preview vs IME).
 */
object DebugSession44 {

    private const val SESSION = "44ebda"

    fun log(
        context: Context,
        hypothesisId: String,
        location: String,
        message: String,
        data: Map<String, String> = emptyMap()
    ) {
        try {
            val json = JSONObject()
            json.put("sessionId", SESSION)
            json.put("hypothesisId", hypothesisId)
            json.put("location", location)
            json.put("message", message)
            json.put("timestamp", System.currentTimeMillis())
            for ((k, v) in data) json.put(k, v)
            val line = json.toString() + "\n"
            Log.d("DV44ebda", line.trim())
            try {
                File(context.filesDir, "debug-44ebda.log").appendText(line)
            } catch (_: Exception) {
            }
            Thread {
                try {
                    val url = URL("http://10.0.2.2:7242/ingest/fa21dfb1-8310-431f-93e4-f304708f8263")
                    val c = url.openConnection() as HttpURLConnection
                    c.requestMethod = "POST"
                    c.setRequestProperty("Content-Type", "application/json")
                    c.setRequestProperty("X-Debug-Session-Id", SESSION)
                    c.doOutput = true
                    c.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
                    c.inputStream.use { }
                } catch (_: Exception) {
                }
            }.start()
        } catch (_: Exception) {
        }
    }
}
