package com.deltavoice

import android.content.Intent
import android.os.Bundle
import android.util.Base64
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
import androidx.core.content.FileProvider
import com.deltavoice.api.CompleteVoiceWorkflowService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
    private lateinit var btnDownload: Button
    private lateinit var btnShare: Button

    private var videoFilePath: String? = null
    private var processedAudioPath: String? = null  // Processed audio (MP3)
    private var isProcessedReady = false
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_config)

        spinnerLanguage = findViewById(R.id.spinner_language)
        spinnerVoice = findViewById(R.id.spinner_voice)
        btnRecord = findViewById(R.id.btn_record_video)
        processingSection = findViewById(R.id.video_processing_section)
        videoStatus = findViewById(R.id.video_status)
        btnProcess = findViewById(R.id.btn_process_video)
        btnDownload = findViewById(R.id.btn_download_video)
        btnShare = findViewById(R.id.btn_share_video)

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        val langAdapter = ArrayAdapter(this, R.layout.spinner_item_dark, languages.map { it.first })
        langAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark)
        spinnerLanguage.adapter = langAdapter

        val voiceAdapter = ArrayAdapter(this, R.layout.spinner_item_dark, voiceStyles.map { it.first })
        voiceAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark)
        spinnerVoice.adapter = voiceAdapter

        btnRecord.setOnClickListener {
            recordVideoLauncher.launch(Intent(this, VideoRecordingActivity::class.java))
        }

        btnProcess.setOnClickListener { processVideo() }
        btnDownload.setOnClickListener { downloadProcessedVideo() }
        btnShare.setOnClickListener { shareProcessedVideo() }
    }

    private fun processVideo() {
        val path = videoFilePath
        if (path.isNullOrBlank()) {
            Toast.makeText(this, "Record a video first", Toast.LENGTH_SHORT).show()
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
                        val outFile = File(cacheDir, "processed_video_${langName.replace(" ", "_")}_${System.currentTimeMillis()}.mp3")
                        withContext(Dispatchers.IO) { outFile.writeBytes(audioBytes) }
                        processedAudioPath = outFile.absolutePath
                        isProcessedReady = true
                        withContext(Dispatchers.Main) {
                            btnDownload.visibility = View.VISIBLE
                            btnShare.visibility = View.VISIBLE
                            videoStatus.text = "Ready! Download or share the translated audio."
                            Toast.makeText(this@VideoConfigActivity, "Done! Download or share the audio.", Toast.LENGTH_LONG).show()
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
                    if (!isProcessedReady) videoStatus.text = "Video recorded. Tap Process to translate."
                }
            }
        }
    }

    private fun downloadProcessedVideo() {
        val path = processedAudioPath ?: return
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
            return
        }
        activityScope.launch {
            val saved = withContext(Dispatchers.IO) {
                val name = "DeltaVoice_video_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.mp3"
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

    private fun shareProcessedVideo() {
        val path = processedAudioPath ?: return
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/mpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share audio"))
        } catch (e: Exception) {
            Toast.makeText(this, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
