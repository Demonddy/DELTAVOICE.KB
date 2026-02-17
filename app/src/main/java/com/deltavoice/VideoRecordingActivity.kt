package com.deltavoice

import android.Manifest
import android.content.pm.PackageManager
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Activity for recording short videos (max 15 seconds)
 * Records video and optionally returns URI
 */
class VideoRecordingActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var recordButton: Button
    private lateinit var stopButton: Button

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var videoFile: File? = null

    companion object {
        const val EXTRA_VIDEO_PATH = "extra_video_path"
        private const val MAX_DURATION_MS = 15000 // 15 seconds
        private const val PERMISSION_REQUEST_CODE = 200
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_recording)

        statusText = findViewById(R.id.status_text)
        recordButton = findViewById(R.id.record_button)
        stopButton = findViewById(R.id.stop_button)

        stopButton.isEnabled = false

        recordButton.setOnClickListener {
            if (checkPermissions()) {
                startRecording()
            } else {
                requestPermissions()
            }
        }

        stopButton.setOnClickListener {
            stopRecording()
        }
    }

    /**
     * Check if camera and storage permissions are granted
     */
    private fun checkPermissions(): Boolean {
        val hasCamera = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        val hasStorage = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            true // No storage permission needed for API 33+
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

        return hasCamera && hasStorage
    }

    /**
     * Request camera and storage permissions
     */
    private fun requestPermissions() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        ActivityCompat.requestPermissions(
            this,
            permissions.toTypedArray(),
            PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startRecording()
            } else {
                Toast.makeText(
                    this,
                    R.string.camera_permission_required,
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    /**
     * Start video recording
     */
    private fun startRecording() {
        if (isRecording) return

        try {
            // Create video file
            videoFile = createVideoFile()

            // Initialize MediaRecorder
            // MediaRecorder constructor with Context is available from API 31+
            mediaRecorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
                setVideoSource(MediaRecorder.VideoSource.CAMERA)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoEncodingBitRate(10000000)
                setVideoSize(1280, 720)
                setMaxDuration(MAX_DURATION_MS)
                setOutputFile(videoFile?.absolutePath)

                setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                        stopRecording()
                    }
                }

                prepare()
                start()

                isRecording = true
                statusText.text = getString(R.string.recording_video)
                recordButton.isEnabled = false
                stopButton.isEnabled = true
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to start recording: ${e.message}", Toast.LENGTH_SHORT).show()
            releaseMediaRecorder()
        }
    }

    /**
     * Stop video recording
     */
    private fun stopRecording() {
        if (!isRecording) return

        try {
            mediaRecorder?.apply {
                stop()
                reset()
            }
            releaseMediaRecorder()

            isRecording = false
            statusText.text = getString(R.string.video_recorded)
            recordButton.isEnabled = true
            stopButton.isEnabled = false

            videoFile?.let { file ->
                setResult(RESULT_OK, android.content.Intent().putExtra(EXTRA_VIDEO_PATH, file.absolutePath))
                Toast.makeText(this, "Video saved: ${file.name}", Toast.LENGTH_LONG).show()
                finish()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error stopping recording: ${e.message}", Toast.LENGTH_SHORT).show()
            releaseMediaRecorder()
        }
    }

    /**
     * Create a video file with timestamp
     */
    private fun createVideoFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val videoFileName = "VIDEO_$timeStamp.mp4"
        
        val storageDir = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Use app-specific directory for Android 10+
            getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        } else {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        }

        return File(storageDir, videoFileName)
    }

    /**
     * Release MediaRecorder resources
     */
    private fun releaseMediaRecorder() {
        mediaRecorder?.release()
        mediaRecorder = null
    }

    override fun onStop() {
        super.onStop()
        if (isRecording) {
            stopRecording()
        }
        releaseMediaRecorder()
    }
}

