package com.deltavoice

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
 * Voice page - Record and process voice directly on this page.
 * No keyboard navigation required.
 */
class VoiceConfigActivity : AppCompatActivity() {

    private lateinit var cardFull: LinearLayout
    private lateinit var cardVoiceOnly: LinearLayout
    private lateinit var cardTextOnly: LinearLayout
    private lateinit var spinnerLanguage: Spinner
    private lateinit var spinnerVoice: Spinner
    private lateinit var btnRecord: Button
    private lateinit var recordingSection: LinearLayout
    private lateinit var recordingStatus: TextView
    private lateinit var audioDuration: TextView
    private lateinit var btnPlay: ImageButton
    private lateinit var btnProcess: Button
    private lateinit var processedVoicesList: LinearLayout
    private lateinit var processedVoicesLabel: TextView
    private lateinit var processedVoiceViewSelector: LinearLayout
    private lateinit var btnVoiceViewList: Button
    private lateinit var btnVoiceViewGrid: Button

    private var selectedMode = VoiceProcessIntent.MODE_FULL
    private var audioFilePath: String? = null
    private val processedVoices = mutableListOf<ProcessedVoiceItem>()

    private data class ProcessedVoiceItem(var filePath: String, var label: String, var duration: String)
    private var mediaRecorder: android.media.MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isRecording = false
    private var isPlaying = false
    private var isProcessing = false
    private var currentPlayingPlayButton: ImageButton? = null
    private var isVoiceGridViewMode = false

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

    companion object {
        private const val PERMISSION_REQUEST_RECORD = 100
        const val EXTRA_OPEN_UPLOAD = "open_upload"
    }

    private val uploadAudioLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val path = result.data?.getStringExtra(AudioUploadActivity.EXTRA_RESULT_PATH)
            if (!path.isNullOrBlank()) {
                audioFilePath = path
                recordingSection.visibility = View.VISIBLE
                recordingStatus.text = "Audio uploaded"
                audioDuration.text = getAudioDuration(path)
                Toast.makeText(this, getString(R.string.ready_for_processing), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_config)

        cardFull = findViewById(R.id.card_full)
        cardVoiceOnly = findViewById(R.id.card_voice_only)
        cardTextOnly = findViewById(R.id.card_text_only)
        spinnerLanguage = findViewById(R.id.spinner_language)
        spinnerVoice = findViewById(R.id.spinner_voice)
        btnRecord = findViewById(R.id.btn_record_voice)
        recordingSection = findViewById(R.id.voice_recording_section)
        recordingStatus = findViewById(R.id.recording_status)
        audioDuration = findViewById(R.id.audio_duration)
        btnPlay = findViewById(R.id.btn_play_recording)
        btnProcess = findViewById(R.id.btn_process_voice)
        processedVoicesList = findViewById(R.id.processed_voices_list)
        processedVoicesLabel = findViewById(R.id.processed_voices_label)
        processedVoiceViewSelector = findViewById(R.id.processed_voice_view_selector)
        btnVoiceViewList = findViewById(R.id.btn_voice_view_list)
        btnVoiceViewGrid = findViewById(R.id.btn_voice_view_grid)

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }

        val langAdapter = ArrayAdapter(this, R.layout.spinner_item_dark, languages.map { it.first })
        langAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark)
        spinnerLanguage.adapter = langAdapter

        val voiceAdapter = ArrayAdapter(this, R.layout.spinner_item_dark, voiceStyles.map { it.first })
        voiceAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark)
        spinnerVoice.adapter = voiceAdapter

        updateCardSelection(cardFull)
        cardFull.setOnClickListener { updateCardSelection(cardFull) }
        cardVoiceOnly.setOnClickListener { updateCardSelection(cardVoiceOnly) }
        cardTextOnly.setOnClickListener { updateCardSelection(cardTextOnly) }

        val btnUpload = findViewById<Button>(R.id.btn_upload_voice)
        btnUpload.setOnClickListener {
            val intent = Intent(this, AudioUploadActivity::class.java).apply {
                putExtra(AudioUploadActivity.EXTRA_LAUNCHED_FROM, AudioUploadActivity.LAUNCHED_FROM_APP)
            }
            uploadAudioLauncher.launch(intent)
        }
        btnRecord.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                if (checkRecordPermission()) startRecording()
                else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_REQUEST_RECORD)
            }
        }

        btnPlay.setOnClickListener { togglePlayback() }
        btnProcess.setOnClickListener { processVoice() }
        btnVoiceViewList.setOnClickListener {
            isVoiceGridViewMode = false
            updateProcessedVoiceViewModeUI()
            updateProcessedVoicesList()
        }
        btnVoiceViewGrid.setOnClickListener {
            isVoiceGridViewMode = true
            updateProcessedVoiceViewModeUI()
            updateProcessedVoicesList()
        }
    }

    override fun onResume() {
        super.onResume()
        if (intent.getBooleanExtra(EXTRA_OPEN_UPLOAD, false)) {
            intent.removeExtra(EXTRA_OPEN_UPLOAD)
            val launchIntent = Intent(this, AudioUploadActivity::class.java).apply {
                putExtra(AudioUploadActivity.EXTRA_LAUNCHED_FROM, AudioUploadActivity.LAUNCHED_FROM_APP)
            }
            uploadAudioLauncher.launch(launchIntent)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_RECORD && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            Toast.makeText(this, "Microphone permission required to record", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkRecordPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun updateCardSelection(selected: LinearLayout) {
        val selectedBg = R.drawable.voice_mode_card_selected
        val unselectedBg = R.drawable.voice_mode_card_unselected
        cardFull.setBackgroundResource(if (selected == cardFull) selectedBg else unselectedBg)
        cardVoiceOnly.setBackgroundResource(if (selected == cardVoiceOnly) selectedBg else unselectedBg)
        cardTextOnly.setBackgroundResource(if (selected == cardTextOnly) selectedBg else unselectedBg)
        selectedMode = when (selected) {
            cardFull -> VoiceProcessIntent.MODE_FULL
            cardVoiceOnly -> VoiceProcessIntent.MODE_VOICE_ONLY
            else -> VoiceProcessIntent.MODE_TEXT_ONLY
        }
    }

    private fun startRecording() {
        if (isRecording) return
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "voice_$timestamp.m4a"
            val recordingsDir = File(cacheDir, "recordings").apply { mkdirs() }
            val recordingFile = File(recordingsDir, fileName)
            audioFilePath = recordingFile.absolutePath

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                android.media.MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                android.media.MediaRecorder()
            }.apply {
                setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
                setOutputFile(recordingFile.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            btnRecord.text = "  Stop recording"
            recordingStatus.text = "Recording..."
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to start: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        if (!isRecording) return
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            btnRecord.text = "  Record"
            recordingSection.visibility = View.VISIBLE
            recordingStatus.text = "Recording ready"
            updateAudioDuration(audioFilePath)
            btnProcess.visibility = View.VISIBLE
        } catch (e: Exception) {
            Toast.makeText(this, "Error stopping: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateAudioDuration(path: String?) {
        if (path.isNullOrBlank()) {
            audioDuration.text = "0:00"
            return
        }
        try {
            MediaPlayer().apply {
                setDataSource(path)
                prepare()
                val sec = (duration / 1000) % 60
                val min = (duration / 1000) / 60
                audioDuration.text = String.format("%d:%02d", min, sec)
                release()
            }
        } catch (e: Exception) {
            audioDuration.text = "0:00"
        }
    }

    private fun togglePlayback() {
        val path = audioFilePath
        if (path.isNullOrBlank()) {
            Toast.makeText(this, "No audio to play", Toast.LENGTH_SHORT).show()
            return
        }
        if (isPlaying) {
            mediaPlayer?.apply { stop(); release() }
            mediaPlayer = null
            isPlaying = false
            currentPlayingPlayButton?.setImageResource(R.drawable.ic_play)
            currentPlayingPlayButton = null
            btnPlay.setImageResource(R.drawable.ic_play)
        } else {
            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(path)
                    prepare()
                    setOnCompletionListener {
                        this@VoiceConfigActivity.isPlaying = false
                        btnPlay.setImageResource(R.drawable.ic_play)
                    }
                    start()
                }
                isPlaying = true
                btnPlay.setImageResource(R.drawable.ic_pause)
            } catch (e: Exception) {
                Toast.makeText(this, "Playback error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun processVoice() {
        val path = audioFilePath
        if (path.isNullOrBlank()) {
            Toast.makeText(this, "Record or upload first", Toast.LENGTH_SHORT).show()
            return
        }
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(this, "Recording not found", Toast.LENGTH_SHORT).show()
            return
        }
        if (isProcessing) return

        val langCode = languages.getOrNull(spinnerLanguage.selectedItemPosition)?.second ?: "en"
        val voiceStyle = voiceStyles.getOrNull(spinnerVoice.selectedItemPosition)?.second ?: "aria"
        val workflowType = when (selectedMode) {
            VoiceProcessIntent.MODE_FULL -> "complete"
            VoiceProcessIntent.MODE_VOICE_ONLY -> "voice-only"
            VoiceProcessIntent.MODE_TEXT_ONLY -> "text-only"
            else -> "complete"
        }

        isProcessing = true
        btnProcess.isEnabled = false
        btnProcess.text = "  Processing..."
        val originalBtnText = "  Process"

        activityScope.launch {
            try {
                val result = completeVoiceWorkflowService.runWorkflow(
                    audioFile = file,
                    targetLanguage = langCode,
                    voiceStyle = voiceStyle,
                    workflowType = workflowType
                )
                if (result.isSuccess) {
                    val response = result.getOrNull()!!
                    when (workflowType) {
                        "complete", "voice-only" -> {
                            response.convertedAudioBase64?.takeIf { it.isNotBlank() }?.let { base64 ->
                                val bytes = Base64.decode(base64, Base64.DEFAULT)
                                val outFile = File(cacheDir, "processed_voice_${System.currentTimeMillis()}.mp3")
                                withContext(Dispatchers.IO) { outFile.writeBytes(bytes) }
                                val duration = withContext(Dispatchers.IO) { getAudioDuration(outFile.absolutePath) }
                                val voiceName = voiceStyles.getOrNull(spinnerVoice.selectedItemPosition)?.first ?: "Voice"
                                val item = ProcessedVoiceItem(
                                    filePath = outFile.absolutePath,
                                    label = "$voiceName #${processedVoices.size + 1}",
                                    duration = duration
                                )
                                processedVoices.add(item)
                                updateProcessedVoicesList()
                                Toast.makeText(this@VoiceConfigActivity, "Ready! Tap download on any voice", Toast.LENGTH_SHORT).show()
                            } ?: run {
                                Toast.makeText(this@VoiceConfigActivity, "No audio produced. Try text-only mode.", Toast.LENGTH_SHORT).show()
                            }
                        }
                        "text-only" -> {
                            (response.translatedText ?: response.originalText)?.takeIf { it.isNotBlank() }?.let { text ->
                                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Text", text))
                                Toast.makeText(this@VoiceConfigActivity, "Text copied to clipboard!", Toast.LENGTH_SHORT).show()
                            } ?: run {
                                Toast.makeText(this@VoiceConfigActivity, "No text detected. Try recording again.", Toast.LENGTH_SHORT).show()
                            }
                        }
                        else -> {}
                    }
                } else {
                    val msg = result.exceptionOrNull()?.message ?: "Processing failed"
                    Toast.makeText(this@VoiceConfigActivity, msg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@VoiceConfigActivity, "Please try again", Toast.LENGTH_SHORT).show()
            } finally {
                isProcessing = false
                btnProcess.isEnabled = true
                btnProcess.text = originalBtnText
            }
        }
    }

    private fun getAudioDuration(path: String): String {
        if (path.isBlank()) return "0:00"
        return try {
            val mp = MediaPlayer()
            mp.setDataSource(path)
            mp.prepare()
            val sec = (mp.duration / 1000) % 60
            val min = (mp.duration / 1000) / 60
            mp.release()
            String.format("%d:%02d", min, sec)
        } catch (e: Exception) {
            "0:00"
        }
    }

    private fun updateProcessedVoicesList() {
        processedVoicesList.removeAllViews()
        if (processedVoices.isEmpty()) {
            processedVoicesLabel.visibility = View.GONE
            processedVoiceViewSelector.visibility = View.GONE
            processedVoicesList.visibility = View.GONE
            return
        }
        processedVoicesLabel.visibility = View.VISIBLE
        processedVoiceViewSelector.visibility = View.VISIBLE
        processedVoicesList.visibility = View.VISIBLE
        updateProcessedVoiceViewModeUI()

        if (!isVoiceGridViewMode) {
            for (item in processedVoices) {
                val row = LayoutInflater.from(this).inflate(R.layout.item_processed_voice, processedVoicesList, false)
                val labelView = row.findViewById<TextView>(R.id.voice_label)
                val durationView = row.findViewById<TextView>(R.id.voice_duration)
                val previewBtn = row.findViewById<ImageButton>(R.id.btn_preview_voice)
                labelView.text = item.label
                durationView.text = item.duration
                row.findViewById<ImageButton>(R.id.btn_download_voice).setOnClickListener { downloadProcessedAudio(item.filePath) }
                previewBtn.setOnClickListener { playProcessedAudio(item.filePath, previewBtn) }
                row.findViewById<ImageButton>(R.id.btn_rename_voice).setOnClickListener { renameProcessedVoice(item, labelView, durationView) }
                row.findViewById<ImageButton>(R.id.btn_send_voice).setOnClickListener { sendProcessedAudio(item.filePath) }
                processedVoicesList.addView(row)
            }
            return
        }

        var index = 0
        while (index < processedVoices.size) {
            val rowContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val firstItem = processedVoices[index]
            rowContainer.addView(createVoiceGridItemView(firstItem))
            index++

            if (index < processedVoices.size) {
                val secondItem = processedVoices[index]
                rowContainer.addView(createVoiceGridItemView(secondItem))
                index++
            } else {
                rowContainer.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
                })
            }

            processedVoicesList.addView(rowContainer)
        }
    }

    private fun createVoiceGridItemView(item: ProcessedVoiceItem): View {
        val card = LayoutInflater.from(this).inflate(R.layout.item_processed_voice_grid, processedVoicesList, false)
        val labelView = card.findViewById<TextView>(R.id.grid_voice_label)
        val durationView = card.findViewById<TextView>(R.id.grid_voice_duration)
        val previewBtn = card.findViewById<ImageButton>(R.id.btn_preview_voice)
        labelView.text = item.label
        durationView.text = item.duration
        card.findViewById<ImageButton>(R.id.btn_download_voice).setOnClickListener { downloadProcessedAudio(item.filePath) }
        previewBtn.setOnClickListener { playProcessedAudio(item.filePath, previewBtn) }
        card.findViewById<ImageButton>(R.id.btn_rename_voice).setOnClickListener { renameProcessedVoice(item, labelView, durationView) }
        card.findViewById<ImageButton>(R.id.btn_send_voice).setOnClickListener { sendProcessedAudio(item.filePath) }
        return card
    }

    private fun updateProcessedVoiceViewModeUI() {
        if (!::btnVoiceViewList.isInitialized || !::btnVoiceViewGrid.isInitialized) return
        if (isVoiceGridViewMode) {
            btnVoiceViewGrid.background = ContextCompat.getDrawable(this, R.drawable.voice_mode_button_purple)
            btnVoiceViewGrid.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            btnVoiceViewList.background = ContextCompat.getDrawable(this, R.drawable.bg_card_dark)
            btnVoiceViewList.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        } else {
            btnVoiceViewList.background = ContextCompat.getDrawable(this, R.drawable.voice_mode_button_purple)
            btnVoiceViewList.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            btnVoiceViewGrid.background = ContextCompat.getDrawable(this, R.drawable.bg_card_dark)
            btnVoiceViewGrid.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    private fun downloadProcessedAudio(path: String) {
        if (path.isBlank()) {
            Toast.makeText(this, "No processed audio to download", Toast.LENGTH_SHORT).show()
            return
        }
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            activityScope.launch {
                val saved = withContext(Dispatchers.IO) {
                    val name = "DeltaVoice_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.mp3"
                    val destDir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS) ?: cacheDir
                    val dest = File(destDir, name)
                    file.copyTo(dest, overwrite = true)
                    dest.absolutePath
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@VoiceConfigActivity, "Saved to: $saved", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playProcessedAudio(path: String, playButton: ImageButton) {
        if (path.isBlank()) return
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
            return
        }
        if (isPlaying) {
            mediaPlayer?.apply { stop(); release() }
            mediaPlayer = null
            isPlaying = false
            currentPlayingPlayButton?.setImageResource(R.drawable.ic_play)
            btnPlay.setImageResource(R.drawable.ic_play)
        }
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                setOnCompletionListener {
                    this@VoiceConfigActivity.isPlaying = false
                    currentPlayingPlayButton?.setImageResource(R.drawable.ic_play)
                    currentPlayingPlayButton = null
                    btnPlay.setImageResource(R.drawable.ic_play)
                }
                start()
            }
            isPlaying = true
            currentPlayingPlayButton = playButton
            playButton.setImageResource(R.drawable.ic_pause)
            btnPlay.setImageResource(R.drawable.ic_pause)
        } catch (e: Exception) {
            Toast.makeText(this, "Playback error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendProcessedAudio(path: String) {
        if (path.isBlank()) {
            Toast.makeText(this, "No processed audio to send", Toast.LENGTH_SHORT).show()
            return
        }
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/mpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(intent, "Send voice via"))
        } catch (e: Exception) {
            Toast.makeText(this, "No app available to send audio", Toast.LENGTH_SHORT).show()
        }
    }

    private fun renameProcessedVoice(item: ProcessedVoiceItem, labelView: TextView, durationView: TextView) {
        val originalFile = File(item.filePath)
        if (!originalFile.exists()) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
            return
        }

        val input = EditText(this).apply {
            setText(item.label)
            setSelection(text.length)
            setSingleLine(true)
        }

        AlertDialog.Builder(this)
            .setTitle("Rename voice")
            .setMessage("Choose a clear name for this processed voice")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val newLabel = input.text?.toString()?.trim().orEmpty()
                if (newLabel.isBlank()) {
                    Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val extension = originalFile.extension
                val safeBaseName = newLabel
                    .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    .trim()
                    .ifBlank { "Processed_voice_${System.currentTimeMillis()}" }
                val targetName = if (extension.isNotBlank() &&
                    !safeBaseName.lowercase(Locale.getDefault()).endsWith(".${extension.lowercase(Locale.getDefault())}")
                ) {
                    "$safeBaseName.$extension"
                } else {
                    safeBaseName
                }

                val renamedFile = File(originalFile.parentFile, targetName)
                if (renamedFile.exists() && renamedFile.absolutePath != originalFile.absolutePath) {
                    Toast.makeText(this, "A file with this name already exists", Toast.LENGTH_SHORT).show()
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
                    item.duration = getAudioDuration(item.filePath)
                    labelView.text = item.label
                    durationView.text = item.duration
                    Toast.makeText(this, "Renamed", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Rename failed", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaRecorder?.release()
    }
}
