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
 * Activity to pick video/image from gallery for keyboard video processing.
 * Launched by MainKeyboardService; broadcasts the file path when done.
 */
class VideoUploadActivity : AppCompatActivity() {

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
        // Transparent, no UI - opens picker for video and image (*/* allows both)
        pickMedia.launch("*/*")
    }

    private fun handlePickedUri(uri: Uri) {
        try {
            val mimeType = contentResolver.getType(uri) ?: ""
            val isVideo = mimeType.startsWith("video/")
            val isImage = mimeType.startsWith("image/")

            if (!isVideo && !isImage) {
                Toast.makeText(this, "Please select a video or image file", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            if (isImage) {
                Toast.makeText(
                    this,
                    "Select a video file for voice processing. Images can be shared directly from your gallery.",
                    Toast.LENGTH_LONG
                ).show()
                finish()
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
                .apply()

            sendBroadcast(Intent(ACTION_VIDEO_UPLOADED))
            Toast.makeText(this, "File ready for processing", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to open file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    companion object {
        const val ACTION_VIDEO_UPLOADED = "com.deltavoice.VIDEO_UPLOADED"
        const val PREFS_NAME = "video_upload_prefs"
        const val KEY_PENDING_PATH = "pending_video_path"
    }
}
