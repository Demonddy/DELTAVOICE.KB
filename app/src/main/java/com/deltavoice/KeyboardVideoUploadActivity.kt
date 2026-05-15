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
 * Keyboard-only video/image file picker. Always broadcasts the result so the
 * keyboard's [MainKeyboardService] receiver can hand off to the video preview panel.
 * Uses separate broadcast action and prefs keys from [VideoUploadActivity] so a
 * concurrent in-app upload never clobbers the keyboard's pending path.
 */
class KeyboardVideoUploadActivity : AppCompatActivity() {

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
        pickMedia.launch("*/*")
    }

    private fun finishInstantly() {
        overridePendingTransition(0, 0)
        finish()
    }

    private fun handlePickedUri(uri: Uri) {
        try {
            val mimeType = contentResolver.getType(uri) ?: ""
            val isVideo = mimeType.startsWith("video/")
            val isImage = mimeType.startsWith("image/")

            if (!isVideo && !isImage) {
                Toast.makeText(this, getString(R.string.please_select_video_or_image), Toast.LENGTH_SHORT).show()
                finishInstantly()
                return
            }

            if (isImage) {
                Toast.makeText(
                    this,
                    "Select a video file for voice processing. Images can be shared directly from your gallery.",
                    Toast.LENGTH_LONG
                ).show()
                finishInstantly()
                return
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val videoDir = File(filesDir, "videos")
            if (!videoDir.exists()) videoDir.mkdirs()
            val destFile = File(videoDir, "KB_UPLOAD_$timestamp.mp4")

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            }

            val path = destFile.absolutePath
            Toast.makeText(this, getString(R.string.ready_for_processing), Toast.LENGTH_SHORT).show()

            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PENDING_PATH, path)
                .commit()
            sendBroadcast(Intent(ACTION_KEYBOARD_VIDEO_UPLOADED).setPackage(packageName))
            Handler(Looper.getMainLooper()).postDelayed({ finishInstantly() }, 750)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.failed_open_file, e.message ?: ""), Toast.LENGTH_SHORT).show()
            finishInstantly()
        }
    }

    companion object {
        const val ACTION_KEYBOARD_VIDEO_UPLOADED = "com.deltavoice.KEYBOARD_VIDEO_UPLOADED"
        const val PREFS_NAME = "keyboard_video_upload_prefs"
        const val KEY_PENDING_PATH = "pending_keyboard_video_path"
    }
}
