package com.deltavoice

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
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
    private lateinit var processedViewSelector: LinearLayout
    private lateinit var btnViewList: Button
    private lateinit var btnViewGrid: Button
    private lateinit var videoPreview: VideoView
    private lateinit var videoPreviewContainer: LinearLayout

    private var videoFilePath: String? = null
    private var processedAudioPath: String? = null
    private val processedVideos = mutableListOf<ProcessedVideoItem>()

    private data class ProcessedVideoItem(var filePath: String, var label: String, val isVideo: Boolean)
    private var isProcessing = false
    private var isGridViewMode = false

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
                onVideoSelected(path, getString(R.string.video_recorded_tap_process))
            }
        }
    }

    private val uploadVideoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val path = result.data?.getStringExtra(VideoUploadActivity.EXTRA_RESULT_PATH)
            if (!path.isNullOrBlank()) {
                onVideoSelected(path, getString(R.string.video_uploaded_tap_process))
                Toast.makeText(this, getString(R.string.ready_for_processing), Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_UPLOAD = "open_upload"
        const val EXTRA_VIDEO_PATH = "extra_video_path"
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
        processedViewSelector = findViewById(R.id.processed_view_selector)
        btnViewList = findViewById(R.id.btn_view_list)
        btnViewGrid = findViewById(R.id.btn_view_grid)
        videoPreview = findViewById(R.id.video_preview)
        videoPreviewContainer = findViewById(R.id.video_preview_container)

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
        btnViewList.setOnClickListener {
            isGridViewMode = false
            updateProcessedViewModeUI()
            updateProcessedVideosList()
        }
        btnViewGrid.setOnClickListener {
            isGridViewMode = true
            updateProcessedViewModeUI()
            updateProcessedVideosList()
        }

        applyVideoPathFromIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyVideoPathFromIntent(intent)
    }

    private fun applyVideoPathFromIntent(intent: Intent?) {
        val path = intent?.getStringExtra(EXTRA_VIDEO_PATH)
        if (!path.isNullOrBlank() && File(path).exists()) {
            onVideoSelected(path, getString(R.string.video_recorded_tap_process))
            intent?.removeExtra(EXTRA_VIDEO_PATH)
        }
    }

    private fun onVideoSelected(path: String, statusText: String) {
        videoFilePath = path
        processingSection.visibility = View.VISIBLE
        videoStatus.text = statusText
        bindVideoPreview(path)
    }

    private fun bindVideoPreview(path: String?) {
        if (path.isNullOrBlank()) {
            videoPreviewContainer.visibility = View.GONE
            stopVideoPreview()
            return
        }
        val file = File(path)
        if (!file.exists()) {
            videoPreviewContainer.visibility = View.GONE
            stopVideoPreview()
            return
        }
        videoPreviewContainer.visibility = View.VISIBLE
        stopVideoPreview()
        videoPreview.post {
            val mc = MediaController(this)
            mc.setAnchorView(videoPreview)
            videoPreview.setMediaController(mc)
            videoPreview.setVideoURI(Uri.fromFile(file))
            videoPreview.setOnPreparedListener { it.isLooping = false }
        }
    }

    private fun stopVideoPreview() {
        try {
            videoPreview.stopPlayback()
        } catch (_: Exception) {
        }
        videoPreview.setOnPreparedListener(null)
        videoPreview.setMediaController(null)
    }

    override fun onPause() {
        super.onPause()
        if (::videoPreview.isInitialized) {
            try {
                videoPreview.pause()
            } catch (_: Exception) {
            }
        }
    }

    override fun onDestroy() {
        stopVideoPreview()
        super.onDestroy()
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
            Toast.makeText(this, getString(R.string.record_upload_first), Toast.LENGTH_SHORT).show()
            return
        }
        val videoFile = File(path)
        if (!videoFile.exists()) {
            Toast.makeText(this, getString(R.string.video_file_not_found), Toast.LENGTH_SHORT).show()
            return
        }
        if (isProcessing) return

        val langCode = languages.getOrNull(spinnerLanguage.selectedItemPosition)?.second ?: "en"
        val voiceStyle = voiceStyles.getOrNull(spinnerVoice.selectedItemPosition)?.second ?: "aria"

        if (::videoPreview.isInitialized) {
            try {
                videoPreview.pause()
            } catch (_: Exception) {
            }
        }

        isProcessing = true
        btnProcess.isEnabled = false
        videoStatus.text = getString(R.string.processing_video)
        Toast.makeText(this, getString(R.string.processing_video_toast), Toast.LENGTH_LONG).show()

        activityScope.launch {
            try {
                val audioFile = VideoProcessingHelper.extractAudioFromVideo(videoFile, cacheDir)
                if (audioFile == null || !audioFile.exists()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@VideoConfigActivity, getString(R.string.no_audio_in_video), Toast.LENGTH_LONG).show()
                        btnProcess.isEnabled = true
                        videoStatus.text = getString(R.string.video_recorded_tap_process)
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
                            videoStatus.text = getString(R.string.video_status_muxing)
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
                                videoStatus.text = getString(R.string.video_status_ready_tap_download)
                                Toast.makeText(this@VideoConfigActivity, getString(R.string.done_tap_download), Toast.LENGTH_LONG).show()
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
                                videoStatus.text = getString(R.string.video_status_mux_failed_tap_audio)
                                Toast.makeText(this@VideoConfigActivity, getString(R.string.video_mux_failed), Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@VideoConfigActivity, getString(R.string.processing_no_audio), Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@VideoConfigActivity, getString(R.string.failed_error, result.exceptionOrNull()?.message ?: ""), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@VideoConfigActivity, getString(R.string.error_message, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            } finally {
                isProcessing = false
                withContext(Dispatchers.Main) {
                    btnProcess.isEnabled = true
                    if (processedVideos.isEmpty()) {
                        videoStatus.text = getString(R.string.video_recorded_tap_process)
                    }
                }
            }
        }
    }

    private fun updateProcessedVideosList() {
        processedVideosList.removeAllViews()
        if (processedVideos.isEmpty()) {
            processedVideosLabel.visibility = View.GONE
            processedViewSelector.visibility = View.GONE
            processedVideosList.visibility = View.GONE
            return
        }
        processedVideosLabel.visibility = View.VISIBLE
        processedViewSelector.visibility = View.VISIBLE
        processedVideosList.visibility = View.VISIBLE
        updateProcessedViewModeUI()

        if (!isGridViewMode) {
            for (item in processedVideos) {
                val row = LayoutInflater.from(this).inflate(R.layout.item_processed_video, processedVideosList, false)
                val labelView = row.findViewById<TextView>(R.id.video_label)
                labelView.text = item.label
                row.findViewById<ImageButton>(R.id.btn_download_video).setOnClickListener { downloadProcessedVideo(item.filePath) }
                row.findViewById<ImageButton>(R.id.btn_preview_video).setOnClickListener { previewProcessedMedia(item) }
                row.findViewById<ImageButton>(R.id.btn_rename_video).setOnClickListener { renameProcessedItem(item, labelView) }
                row.findViewById<ImageButton>(R.id.btn_send_video).setOnClickListener { sendProcessedMedia(item) }
                processedVideosList.addView(row)
            }
            return
        }

        var index = 0
        while (index < processedVideos.size) {
            val rowContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val firstItem = processedVideos[index]
            rowContainer.addView(createGridItemView(firstItem))
            index++

            if (index < processedVideos.size) {
                val secondItem = processedVideos[index]
                rowContainer.addView(createGridItemView(secondItem))
                index++
            } else {
                // Keep columns balanced on odd item count.
                rowContainer.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
                })
            }

            processedVideosList.addView(rowContainer)
        }
    }

    private fun createGridItemView(item: ProcessedVideoItem): View {
        val card = LayoutInflater.from(this).inflate(R.layout.item_processed_video_grid, processedVideosList, false)
        val labelView = card.findViewById<TextView>(R.id.grid_video_label)
        labelView.text = item.label
        val icon = card.findViewById<ImageView>(R.id.grid_media_icon)
        icon.setImageResource(if (item.isVideo) R.drawable.ic_video else R.drawable.ic_speaker)

        card.findViewById<ImageButton>(R.id.btn_download_video).setOnClickListener { downloadProcessedVideo(item.filePath) }
        card.findViewById<ImageButton>(R.id.btn_preview_video).setOnClickListener { previewProcessedMedia(item) }
        card.findViewById<ImageButton>(R.id.btn_rename_video).setOnClickListener { renameProcessedItem(item, labelView) }
        card.findViewById<ImageButton>(R.id.btn_send_video).setOnClickListener { sendProcessedMedia(item) }
        return card
    }

    private fun updateProcessedViewModeUI() {
        if (!::btnViewList.isInitialized || !::btnViewGrid.isInitialized) return
        if (isGridViewMode) {
            btnViewGrid.background = ContextCompat.getDrawable(this, R.drawable.voice_mode_button_purple)
            btnViewGrid.setTextColor(ContextCompat.getColor(this, R.color.on_primary))
            btnViewList.background = ContextCompat.getDrawable(this, R.drawable.bg_card_dark)
            btnViewList.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        } else {
            btnViewList.background = ContextCompat.getDrawable(this, R.drawable.voice_mode_button_purple)
            btnViewList.setTextColor(ContextCompat.getColor(this, R.color.on_primary))
            btnViewGrid.background = ContextCompat.getDrawable(this, R.drawable.bg_card_dark)
            btnViewGrid.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    private fun previewProcessedMedia(item: ProcessedVideoItem) {
        val file = File(item.filePath)
        if (!file.exists()) {
            Toast.makeText(this, getString(R.string.file_not_found), Toast.LENGTH_SHORT).show()
            return
        }
        val mimeType = if (item.isVideo) "video/mp4" else "audio/mpeg"
        val uri: Uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.preview_media)))
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.no_app_to_preview), Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendProcessedMedia(item: ProcessedVideoItem) {
        val file = File(item.filePath)
        if (!file.exists()) {
            Toast.makeText(this, getString(R.string.file_not_found), Toast.LENGTH_SHORT).show()
            return
        }
        val mimeType = if (item.isVideo) "video/mp4" else "audio/mpeg"
        val uri: Uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(sendIntent, getString(R.string.send_via)))
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.no_app_to_send_file), Toast.LENGTH_SHORT).show()
        }
    }

    private fun renameProcessedItem(item: ProcessedVideoItem, labelView: TextView) {
        val originalFile = File(item.filePath)
        if (!originalFile.exists()) {
            Toast.makeText(this, getString(R.string.file_not_found), Toast.LENGTH_SHORT).show()
            return
        }

        val input = EditText(this).apply {
            setText(item.label)
            setSelection(text.length)
            setSingleLine(true)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.rename_dialog_title))
            .setMessage(getString(R.string.rename_dialog_message))
            .setView(input)
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val newLabel = input.text?.toString()?.trim().orEmpty()
                if (newLabel.isBlank()) {
                    Toast.makeText(this, getString(R.string.name_cannot_be_empty), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val extension = originalFile.extension
                val safeBaseName = newLabel
                    .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    .trim()
                    .ifBlank { "Processed_${System.currentTimeMillis()}" }
                val targetName = if (extension.isNotBlank() &&
                    !safeBaseName.lowercase(Locale.getDefault()).endsWith(".${extension.lowercase(Locale.getDefault())}")
                ) {
                    "$safeBaseName.$extension"
                } else {
                    safeBaseName
                }
                val renamedFile = File(originalFile.parentFile, targetName)

                if (renamedFile.exists() && renamedFile.absolutePath != originalFile.absolutePath) {
                    Toast.makeText(this, getString(R.string.file_name_already_exists), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val renamed = if (renamedFile.absolutePath == originalFile.absolutePath) {
                    true
                } else {
                    originalFile.renameTo(renamedFile)
                }

                if (renamed) {
                    item.filePath = renamedFile.absolutePath
                    item.label = safeBaseName
                    labelView.text = item.label
                    Toast.makeText(this, getString(R.string.renamed), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, getString(R.string.rename_failed), Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun downloadProcessedVideo(path: String) {
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(this, getString(R.string.file_not_found), Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this@VideoConfigActivity, getString(R.string.saved_to, saved), Toast.LENGTH_LONG).show()
            }
        }
    }
}
