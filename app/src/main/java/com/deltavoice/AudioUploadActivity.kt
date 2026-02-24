package com.deltavoice

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Activity to pick audio from device for keyboard voice processing.
 * Launched by MainKeyboardService; broadcasts the file path when done.
 */
class AudioUploadActivity : AppCompatActivity() {

    private val pickMedia = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) {
            finish()
            return@registerForActivityResult
        }
        handlePickedUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pickMedia.launch("audio/*")
    }

    private fun handlePickedUri(uri: Uri) {
        try {
            val mimeType = contentResolver.getType(uri) ?: ""
            val isAudio = mimeType.startsWith("audio/")

            if (!isAudio) {
                Toast.makeText(this, "Please select an audio file", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            val ext = when {
                mimeType.contains("mpeg") || mimeType.contains("mp3") -> ".mp3"
                mimeType.contains("m4a") || mimeType.contains("aac") -> ".m4a"
                mimeType.contains("ogg") -> ".ogg"
                mimeType.contains("wav") -> ".wav"
                else -> ".mp3"
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val audioDir = File(filesDir, "recordings")
            if (!audioDir.exists()) audioDir.mkdirs()
            val destFile = File(audioDir, "UPLOAD_$timestamp$ext")

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            val path = destFile.absolutePath

            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PENDING_PATH, path)
                .apply()

            sendBroadcast(Intent(ACTION_AUDIO_UPLOADED))
            Toast.makeText(this, "Audio ready for processing", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to open file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    companion object {
        const val ACTION_AUDIO_UPLOADED = "com.deltavoice.AUDIO_UPLOADED"
        const val PREFS_NAME = "audio_upload_prefs"
        const val KEY_PENDING_PATH = "pending_audio_path"
    }
}
