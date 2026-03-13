package com.deltavoice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Environment
import android.util.Log
import android.view.Surface
import android.view.TextureView
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
 * Activity for recording short videos (max 15 seconds).
 * Uses Camera2 API + MediaRecorder SURFACE (same config as keyboard) for reliable recording.
 */
class VideoRecordingActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var recordButton: Button
    private lateinit var stopButton: Button
    private lateinit var cameraPreview: TextureView
    private lateinit var recordingTimer: TextView

    private var cameraManager: CameraManager? = null
    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var mediaRecorder: MediaRecorder? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var isRecording = false
    private var videoFilePath: String? = null
    private var cameraId: String = "0"
    private var useFrontCamera = false

    private var timerHandler: Handler? = null
    private var timerRunnable: Runnable? = null
    private var recordingSeconds = 0

    companion object {
        const val EXTRA_VIDEO_PATH = "extra_video_path"
        private const val MAX_DURATION_MS = 15000
        private const val PERMISSION_REQUEST_CODE = 200
        private const val TAG = "VideoRecording"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_recording)

        statusText = findViewById(R.id.status_text)
        recordButton = findViewById(R.id.record_button)
        stopButton = findViewById(R.id.stop_button)
        cameraPreview = findViewById(R.id.camera_preview)
        recordingTimer = findViewById(R.id.recording_timer)

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        stopButton.isEnabled = false
        stopButton.visibility = View.GONE

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        // Switch between front and back cameras
        findViewById<View>(R.id.btn_switch_camera).setOnClickListener {
            if (isRecording) {
                Toast.makeText(this, "Stop recording to switch camera", Toast.LENGTH_SHORT).show()
            } else {
                useFrontCamera = !useFrontCamera
                closeCamera()
                if (checkPermissions() && cameraPreview.isAvailable) {
                    openCamera()
                }
            }
        }

        recordButton.setOnClickListener {
            if (checkPermissions()) {
                if (isRecording) {
                    stopRecording()
                } else {
                    startRecording()
                }
            } else {
                requestPermissions()
            }
        }

        stopButton.setOnClickListener { stopRecording() }

        cameraPreview.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                if (checkPermissions()) {
                    startBackgroundThread()
                    openCamera()
                }
            }
            override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture) = true
            override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}
        }
    }

    override fun onResume() {
        super.onResume()
        if (checkPermissions() && cameraPreview.isAvailable) {
            startBackgroundThread()
            openCamera()
        }
    }

    override fun onPause() {
        super.onPause()
        if (isRecording) stopRecording()
        closeCamera()
        stopBackgroundThread()
    }

    private fun checkPermissions(): Boolean {
        val hasCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val hasAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasStorage = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            true
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        return hasCamera && hasAudio && hasStorage
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startBackgroundThread()
                if (cameraPreview.isAvailable) openCamera()
            } else {
                Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun startBackgroundThread() {
        if (backgroundThread?.isAlive == true) return
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join(500)
        } catch (_: InterruptedException) {}
        backgroundThread = null
        backgroundHandler = null
    }

    private fun getCameraId(useFront: Boolean): String {
        val ids = cameraManager?.cameraIdList ?: return "0"
        for (id in ids) {
            val chars = cameraManager?.getCameraCharacteristics(id)
            val facing = chars?.get(CameraCharacteristics.LENS_FACING)
            if (useFront && facing == CameraCharacteristics.LENS_FACING_FRONT) return id
            if (!useFront && facing == CameraCharacteristics.LENS_FACING_BACK) return id
        }
        return ids.firstOrNull() ?: "0"
    }

    private fun openCamera() {
        try {
            cameraId = getCameraId(useFrontCamera)
            cameraManager?.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createPreviewSession()
                }
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    runOnUiThread { Toast.makeText(this@VideoRecordingActivity, "Camera error", Toast.LENGTH_SHORT).show() }
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Open camera failed", e)
            Toast.makeText(this, "Failed to open camera", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createPreviewSession() {
        try {
            val texture = cameraPreview.surfaceTexture ?: return
            texture.setDefaultBufferSize(1920, 1080)
            val surface = Surface(texture)
            val requestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            requestBuilder?.addTarget(surface)
            cameraDevice?.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    cameraCaptureSession = session
                    requestBuilder?.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                    try {
                        session.setRepeatingRequest(requestBuilder!!.build(), null, backgroundHandler)
                    } catch (_: Exception) {}
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    runOnUiThread { Toast.makeText(this@VideoRecordingActivity, "Camera config failed", Toast.LENGTH_SHORT).show() }
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Create preview failed", e)
        }
    }

    private fun closeCamera() {
        try { cameraCaptureSession?.close() } catch (_: Exception) {}
        cameraCaptureSession = null
        try { cameraDevice?.close() } catch (_: Exception) {}
        cameraDevice = null
    }

    private fun createVideoFile(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val dir = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        } else {
            @Suppress("DEPRECATION")
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        } ?: cacheDir
        dir.mkdirs()
        return File(dir, "VIDEO_$timestamp.mp4")
    }

    /**
     * Start recording using Camera2 + MediaRecorder SURFACE (same config as keyboard).
     */
    private fun startRecording() {
        if (isRecording) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Audio permission required", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            cameraCaptureSession?.close()
            cameraCaptureSession = null

            val videoFile = createVideoFile()
            videoFilePath = videoFile.absolutePath

            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(videoFilePath)
                setVideoEncodingBitRate(10_000_000)
                setVideoFrameRate(30)
                setVideoSize(1920, 1080)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setMaxDuration(MAX_DURATION_MS)
                if (useFrontCamera) setOrientationHint(270) else setOrientationHint(90)
                setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                        runOnUiThread { stopRecording() }
                    }
                }
                prepare()
            }

            val texture = cameraPreview.surfaceTexture ?: return
            texture.setDefaultBufferSize(1920, 1080)
            val previewSurface = Surface(texture)
            val recorderSurface = mediaRecorder!!.surface

            val requestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            requestBuilder?.addTarget(previewSurface)
            requestBuilder?.addTarget(recorderSurface)

            cameraDevice?.createCaptureSession(listOf(previewSurface, recorderSurface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    cameraCaptureSession = session
                    requestBuilder?.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                    try {
                        session.setRepeatingRequest(requestBuilder!!.build(), null, backgroundHandler)
                        mediaRecorder?.start()
                        isRecording = true
                        runOnUiThread {
                            updateRecordingUI(true)
                            startTimer()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Start recording failed", e)
                        runOnUiThread {
                            Toast.makeText(this@VideoRecordingActivity, "Failed to start recording: ${e.message}", Toast.LENGTH_LONG).show()
                            releaseMediaRecorder()
                            createPreviewSession()
                        }
                    }
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    runOnUiThread {
                        Toast.makeText(this@VideoRecordingActivity, "Recording config failed", Toast.LENGTH_SHORT).show()
                        releaseMediaRecorder()
                        createPreviewSession()
                    }
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Start recording failed", e)
            Toast.makeText(this, "Failed to start recording: ${e.message}", Toast.LENGTH_LONG).show()
            releaseMediaRecorder()
            createPreviewSession()
        }
    }

    private fun stopRecording() {
        if (!isRecording) return
        try {
            stopTimer()
            isRecording = false
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
            mediaRecorder = null
            updateRecordingUI(false)
            createPreviewSession()

            videoFilePath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    val intent = Intent(this, VideoConfigActivity::class.java).apply {
                        putExtra(VideoConfigActivity.EXTRA_VIDEO_PATH, path)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                    finish()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Stop recording failed", e)
            Toast.makeText(this, "Error stopping: ${e.message}", Toast.LENGTH_SHORT).show()
            releaseMediaRecorder()
            createPreviewSession()
        }
    }

    private fun releaseMediaRecorder() {
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {}
        mediaRecorder = null
    }

    private fun updateRecordingUI(recording: Boolean) {
        if (recording) {
            statusText.text = getString(R.string.recording_video)
            statusText.setTextColor(0xFFFF5252.toInt())
            recordButton.visibility = View.GONE
            stopButton.visibility = View.VISIBLE
            stopButton.isEnabled = true
            recordingTimer.visibility = View.VISIBLE
        } else {
            statusText.text = "Ready to record video with audio"
            statusText.setTextColor(0xFFAAAAAA.toInt())
            recordButton.visibility = View.VISIBLE
            stopButton.visibility = View.GONE
            stopButton.isEnabled = false
            recordingTimer.visibility = View.GONE
        }
    }

    private fun startTimer() {
        recordingSeconds = 0
        timerHandler = Handler(Looper.getMainLooper())
        timerRunnable = object : Runnable {
            override fun run() {
                if (!isRecording) return
                recordingSeconds++
                val m = recordingSeconds / 60
                val s = recordingSeconds % 60
                recordingTimer.text = String.format("%02d:%02d", m, s)
                timerHandler?.postDelayed(this, 1000)
            }
        }
        timerRunnable?.run()
    }

    private fun stopTimer() {
        timerRunnable?.let { timerHandler?.removeCallbacks(it) }
        timerHandler = null
        timerRunnable = null
    }
}
