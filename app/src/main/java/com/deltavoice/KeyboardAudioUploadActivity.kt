package com.deltavoice

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keyboard-only audio file picker. Always broadcasts the result so the
 * keyboard's [MainKeyboardService] receiver can hand off to the voice Step-2 panel.
 * Uses separate broadcast action and prefs keys from [AudioUploadActivity] so a
 * concurrent in-app upload never clobbers the keyboard's pending path.
 */
class KeyboardAudioUploadActivity : AppCompatActivity() {

    private val pickMedia = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) {
            finishInstantly()
            return@registerForActivityResult
        }
        handlePickedUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pickMedia.launch("audio/*")
    }

    private fun finishInstantly() {
        overridePendingTransition(0, 0)
        finish()
    }

    private fun handlePickedUri(uri: Uri) {
        try {
            val mimeType = contentResolver.getType(uri) ?: ""
            val isAudio = mimeType.startsWith("audio/")

            if (!isAudio) {
                Toast.makeText(this, getString(R.string.please_select_audio), Toast.LENGTH_SHORT).show()
                finishInstantly()
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
            val destFile = File(audioDir, "KB_UPLOAD_$timestamp$ext")

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            }

            val path = destFile.absolutePath
            Toast.makeText(this, getString(R.string.ready_for_processing), Toast.LENGTH_SHORT).show()

            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PENDING_PATH, path)
                .commit()
            sendBroadcast(Intent(ACTION_KEYBOARD_AUDIO_UPLOADED).setPackage(packageName))
            Handler(Looper.getMainLooper()).postDelayed({ finishInstantly() }, 750)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.failed_open_file, e.message ?: ""), Toast.LENGTH_SHORT).show()
            finishInstantly()
        }
    }

    companion object {
        const val ACTION_KEYBOARD_AUDIO_UPLOADED = "com.deltavoice.KEYBOARD_AUDIO_UPLOADED"
        const val PREFS_NAME = "keyboard_audio_upload_prefs"
        const val KEY_PENDING_PATH = "pending_keyboard_audio_path"
    }
}
