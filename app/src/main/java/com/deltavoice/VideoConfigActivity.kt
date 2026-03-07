package com.deltavoice

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.deltavoice.api.CompleteVoiceWorkflowService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Video page - Record and process video directly on this page.
 * No keyboard navigation required.
 */
class VideoConfigActivity : AppCompatActivity() {

    private lateinit var spinnerLanguage: Spinner
    private lateinit var spinnerVoice: Spinner
    private lateinit var btnRecord: Button
    private lateinit var processingSection: LinearLayout
    private lateinit var videoStatus: TextView
    private lateinit var btnProcess: Button
    private lateinit var processedVideosList: LinearLayout
    private lateinit var processedVideosLabel: TextView

    private var videoFilePath: String? = null
    private var processedAudioPath: String? = null
    private val processedVideos = mutableListOf<ProcessedVideoItem>()

    private data class ProcessedVideoItem(val filePath: String, val label: String, val isVideo: Boolean)
    private var isProcessing = false

    private val completeVoiceWorkflowService = CompleteVoiceWorkflowService()
    private val activityScope = CoroutineScope(Dispatchers.Main)

    private val languages = listOf(
        "English" to "en", "Spanish" to "es", "French" to "fr", "German" to "de",
        "Italian" to "it", "Portuguese" to "pt", "Russian" to "ru", "Japanese" to "ja",
        "Korean" to "ko", "Chinese" to "zh", "Arabic" to "ar", "Hindi" to "hi"
    )

    private val voiceStyles = listOf(
        "Adam" to "adam", "Aria" to "aria", "Sarah" to "sarah", "Liam" to "liam",
        "Charlotte" to "charlotte", "Alice" to "alice", "Roger" to "roger", "Laura" to "laura"
    )

    private val recordVideoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val path = result.data?.getStringExtra(VideoRecordingActivity.EXTRA_VIDEO_PATH)
            if (!path.isNullOrBlank()) {
                videoFilePath = path
                processingSection.visibility = View.VISIBLE
                videoStatus.text = "Video recorded. Tap Process to translate."
            }
        }
    }

    private val uploadVideoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val path = result.data?.getStringExtra(VideoUploadActivity.EXTRA_RESULT_PATH)
            if (!path.isNullOrBlank()) {
                videoFilePath = path
                processingSection.visibility = View.VISIBLE
                videoStatus.text = "Video uploaded. Tap Process to translate."
                Toast.makeText(this, "Video ready for processing", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_UPLOAD = "open_upload"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_config)

        spinnerLanguage = findViewById(R.id.spinner_language)
        spinnerVoice = findViewById(R.id.spinner_voice)
        btnRecord = findViewById(R.id.btn_record_video)
        processingSection = findViewById(R.id.video_processing_section)
        videoStatus = findViewById(R.id.video_status)
        btnProcess = findViewById(R.id.btn_process_video)
        processedVideosList = findViewById(R.id.processed_videos_list)
        processedVideosLabel = findViewById(R.id.processed_videos_label)

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        val langAdapter = ArrayAdapter(this, R.layout.spinner_item_dark, languages.map { it.first })
        langAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark)
        spinnerLanguage.adapter = langAdapter

        val voiceAdapter = ArrayAdapter(this, R.layout.spinner_item_dark, voiceStyles.map { it.first })
        voiceAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark)
        spinnerVoice.adapter = voiceAdapter

        val btnUpload = findViewById<Button>(R.id.btn_upload_video)
        btnUpload.setOnClickListener {
            val intent = Intent(this, VideoUploadActivity::class.java).apply {
                putExtra(VideoUploadActivity.EXTRA_LAUNCHED_FROM, VideoUploadActivity.LAUNCHED_FROM_APP)
            }
            uploadVideoLauncher.launch(intent)
        }
        btnRecord.setOnClickListener {
            recordVideoLauncher.launch(Intent(this, VideoRecordingActivity::class.java))
        }
        btnProcess.setOnClickListener { processVideo() }
    }

    override fun onResume() {
        super.onResume()
        if (intent.getBooleanExtra(EXTRA_OPEN_UPLOAD, false)) {
            intent.removeExtra(EXTRA_OPEN_UPLOAD)
            val launchIntent = Intent(this, VideoUploadActivity::class.java).apply {
                putExtra(VideoUploadActivity.EXTRA_LAUNCHED_FROM, VideoUploadActivity.LAUNCHED_FROM_APP)
            }
            uploadVideoLauncher.launch(launchIntent)
        }
    }

    private fun processVideo() {
        val path = videoFilePath
        if (path.isNullOrBlank()) {
            Toast.makeText(this, "Record or upload a video first", Toast.LENGTH_SHORT).show()
            return
        }
        val videoFile = File(path)
        if (!videoFile.exists()) {
            Toast.makeText(this, "Video file not found", Toast.LENGTH_SHORT).show()
            return
        }
        if (isProcessing) return

        val langCode = languages.getOrNull(spinnerLanguage.selectedItemPosition)?.second ?: "en"
        val voiceStyle = voiceStyles.getOrNull(spinnerVoice.selectedItemPosition)?.second ?: "aria"
        val langName = languages.getOrNull(spinnerLanguage.selectedItemPosition)?.first ?: "English"

        isProcessing = true
        btnProcess.isEnabled = false
        videoStatus.text = "Processing..."
        Toast.makeText(this, "Processing video...", Toast.LENGTH_LONG).show()

        activityScope.launch {
            try {
                val audioFile = VideoProcessingHelper.extractAudioFromVideo(videoFile, cacheDir)
                if (audioFile == null || !audioFile.exists()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@VideoConfigActivity, "No audio in video", Toast.LENGTH_LONG).show()
                        btnProcess.isEnabled = true
                        videoStatus.text = "Video recorded. Tap Process to translate."
                    }
                    return@launch
                }

                val result = completeVoiceWorkflowService.runWorkflow(
                    audioFile = audioFile,
                    targetLanguage = langCode,
                    voiceStyle = voiceStyle,
                    workflowType = "complete"
                )

                audioFile.delete()

                if (result.isSuccess) {
                    val response = result.getOrNull()!!
                    val base64 = response.convertedAudioBase64?.takeIf { it.isNotBlank() }
                    if (base64 != null) {
                        val audioBytes = Base64.decode(base64, Base64.DEFAULT)
                        val mp3File = File(cacheDir, "processed_audio_${System.currentTimeMillis()}.mp3")
                        withContext(Dispatchers.IO) { mp3File.writeBytes(audioBytes) }
                        processedAudioPath = mp3File.absolutePath

                        withContext(Dispatchers.Main) {
                            videoStatus.text = "Muxing video..."
                        }

                        val aacFile = VideoProcessingHelper.convertMp3ToAac(mp3File, cacheDir)
                        val muxedVideo = VideoProcessingHelper.muxVideoWithProcessedAudio(videoFile, aacFile, cacheDir)

                        if (aacFile != mp3File) {
                            try { aacFile.delete() } catch (_: Exception) {}
                        }

                        if (muxedVideo != null && muxedVideo.exists()) {
                            val voiceName = voiceStyles.getOrNull(spinnerVoice.selectedItemPosition)?.first ?: "Video"
                            val item = ProcessedVideoItem(
                                filePath = muxedVideo.absolutePath,
                                label = "$voiceName #${processedVideos.size + 1}",
                                isVideo = true
                            )
                            processedVideos.add(item)
                            withContext(Dispatchers.Main) {
                                updateProcessedVideosList()
                                videoStatus.text = "Ready! Tap download on any video."
                                Toast.makeText(this@VideoConfigActivity, "Done! Tap download on any video.", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            val voiceName = voiceStyles.getOrNull(spinnerVoice.selectedItemPosition)?.first ?: "Audio"
                            val item = ProcessedVideoItem(
                                filePath = mp3File.absolutePath,
                                label = "$voiceName #${processedVideos.size + 1} (audio)",
                                isVideo = false
                            )
                            processedVideos.add(item)
                            withContext(Dispatchers.Main) {
                                updateProcessedVideosList()
                                videoStatus.text = "Video mux failed. Tap download on any audio."
                                Toast.makeText(this@VideoConfigActivity, "Video mux failed. Download or share audio.", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@VideoConfigActivity, "Processing produced no audio", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@VideoConfigActivity, "Failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@VideoConfigActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                isProcessing = false
                withContext(Dispatchers.Main) {
                    btnProcess.isEnabled = true
                    if (processedVideos.isEmpty()) videoStatus.text = "Video recorded. Tap Process to translate."
                }
            }
        }
    }

    private fun updateProcessedVideosList() {
        processedVideosList.removeAllViews()
        if (processedVideos.isEmpty()) {
            processedVideosLabel.visibility = View.GONE
            processedVideosList.visibility = View.GONE
            return
        }
        processedVideosLabel.visibility = View.VISIBLE
        processedVideosList.visibility = View.VISIBLE
        for (item in processedVideos) {
            val row = LayoutInflater.from(this).inflate(R.layout.item_processed_video, processedVideosList, false)
            row.findViewById<TextView>(R.id.video_label).text = item.label
            row.findViewById<ImageButton>(R.id.btn_download_video).setOnClickListener { downloadProcessedVideo(item.filePath) }
            processedVideosList.addView(row)
        }
    }

    private fun downloadProcessedVideo(path: String) {
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
            return
        }
        activityScope.launch {
            val saved = withContext(Dispatchers.IO) {
                val ext = if (path.endsWith(".mp4")) "mp4" else "mp3"
                val name = "DeltaVoice_video_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.$ext"
                val destDir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS) ?: cacheDir
                val dest = File(destDir, name)
                file.copyTo(dest, overwrite = true)
                dest.absolutePath
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(this@VideoConfigActivity, "Saved to: $saved", Toast.LENGTH_LONG).show()
            }
        }
    }
}
