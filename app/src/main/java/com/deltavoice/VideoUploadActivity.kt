package com.deltavoice

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
 * Activity to pick video/image from gallery for keyboard video processing.
 * Launched by MainKeyboardService; broadcasts the file path when done.
 * Finishes instantly with no animation so user returns directly to keyboard processing panel.
 */
class VideoUploadActivity : AppCompatActivity() {

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

            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PENDING_PATH, path)
                .commit()  // Must be synchronous so keyboard receiver reads path before broadcast

            sendBroadcast(Intent(ACTION_VIDEO_UPLOADED).setPackage(packageName))
            Toast.makeText(this, "File ready for processing", Toast.LENGTH_SHORT).show()
            // Delay finish so keyboard can request show and user returns to keyboard, not app
            Handler(Looper.getMainLooper()).postDelayed({ finishInstantly() }, 350)
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
    }
}
