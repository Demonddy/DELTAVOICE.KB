package com.deltavoice

import android.Manifest
import android.content.pm.PackageManager
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
        stopButton.visibility = View.GONE

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
     * Check if camera, audio, and storage permissions are granted
     */
    private fun checkPermissions(): Boolean {
        val hasCamera = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        val hasAudio = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val hasStorage = when {
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q -> true
            else -> ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

        return hasCamera && hasAudio && hasStorage
    }

    /**
     * Request camera and audio permissions.
     * Storage only needed pre-API 29 for public dirs.
     */
    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
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

            val outputPath = videoFile?.absolutePath ?: throw IllegalStateException("Video file is null")
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.CAMERA)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setVideoEncodingBitRate(4_000_000)
                setMaxDuration(MAX_DURATION_MS)
                setOutputFile(outputPath)

                setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                        stopRecording()
                    }
                }

                try {
                    setVideoSize(1280, 720)
                    prepare()
                } catch (_: Exception) {
                    reset()
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setVideoSource(MediaRecorder.VideoSource.CAMERA)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setVideoEncodingBitRate(2_000_000)
                    setVideoSize(640, 480)
                    setMaxDuration(MAX_DURATION_MS)
                    setOutputFile(outputPath)
                    setOnInfoListener { _, what, _ ->
                        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) stopRecording()
                    }
                    prepare()
                }
                start()

                isRecording = true
                statusText.text = getString(R.string.recording_video)
                recordButton.isEnabled = false
                recordButton.visibility = View.GONE
                stopButton.isEnabled = true
                stopButton.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            android.util.Log.e("VideoRecording", "Start recording failed", e)
            Toast.makeText(this, "Failed to start recording: ${e.message}", Toast.LENGTH_LONG).show()
            releaseMediaRecorder()
            videoFile = null
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
            recordButton.visibility = View.VISIBLE
            stopButton.isEnabled = false
            stopButton.visibility = View.GONE

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
     * Create a video file with timestamp.
     * Uses app-specific dir (no permission needed on API 29+); falls back to cacheDir if null.
     */
    private fun createVideoFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val videoFileName = "VIDEO_$timeStamp.mp4"

        val storageDir = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        } else {
            @Suppress("DEPRECATION")
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        }
        val dir = storageDir ?: cacheDir
        dir.mkdirs()
        return File(dir, videoFileName)
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

