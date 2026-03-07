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
 * Activity to pick video from gallery. Differentiates by launch source:
 * - Launched from keyboard: broadcasts path, delays finish, user returns to keyboard processing panel.
 * - Launched from app: returns path via setResult, finishes immediately, user stays in app.
 */
class VideoUploadActivity : AppCompatActivity() {

    private val launchedFrom: String
        get() = intent?.getStringExtra(EXTRA_LAUNCHED_FROM) ?: LAUNCHED_FROM_KEYBOARD

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
        // Transparent, no UI - opens picker for video and image (*/* allows both)
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
                Toast.makeText(this, "Please select a video or image file", Toast.LENGTH_SHORT).show()
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

            val ext = when {
                isVideo -> ".mp4"
                isImage -> ".jpg"
                else -> ".mp4"
            }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val videoDir = File(filesDir, "videos")
            if (!videoDir.exists()) videoDir.mkdirs()
            val destFile = File(videoDir, "UPLOAD_$timestamp$ext")

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            val path = destFile.absolutePath
            Toast.makeText(this, "File ready for processing", Toast.LENGTH_SHORT).show()

            if (launchedFrom == LAUNCHED_FROM_APP) {
                // From app: return path, finish immediately, user stays in app
                setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT_PATH, path))
                finishInstantly()
            } else {
                // From keyboard: broadcast, delay finish, user returns to keyboard
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_PENDING_PATH, path)
                    .commit()
                sendBroadcast(Intent(ACTION_VIDEO_UPLOADED).setPackage(packageName))
                Handler(Looper.getMainLooper()).postDelayed({ finishInstantly() }, 500)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to open file: ${e.message}", Toast.LENGTH_SHORT).show()
            finishInstantly()
        }
    }

    companion object {
        const val ACTION_VIDEO_UPLOADED = "com.deltavoice.VIDEO_UPLOADED"
        const val PREFS_NAME = "video_upload_prefs"
        const val KEY_PENDING_PATH = "pending_video_path"
        const val EXTRA_LAUNCHED_FROM = "launched_from"
        const val LAUNCHED_FROM_KEYBOARD = "keyboard"
        const val LAUNCHED_FROM_APP = "app"
        const val EXTRA_RESULT_PATH = "result_path"
    }
}
