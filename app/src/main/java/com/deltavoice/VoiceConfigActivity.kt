package com.deltavoice

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
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
    private lateinit var btnDownload: Button
    private lateinit var btnShare: Button

    private var selectedMode = VoiceProcessIntent.MODE_FULL
    private var audioFilePath: String? = null
    private var processedAudioFilePath: String? = null
    private var isProcessedReady = false
    private var mediaRecorder: android.media.MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isRecording = false
    private var isPlaying = false
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

    companion object {
        private const val PERMISSION_REQUEST_RECORD = 100
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
        btnDownload = findViewById(R.id.btn_download_voice)
        btnShare = findViewById(R.id.btn_share_voice)

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
        btnDownload.setOnClickListener { downloadProcessedAudio() }
        btnShare.setOnClickListener { shareProcessedAudio() }
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
            btnRecord.text = "  Record voice"
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
        val path = if (isProcessedReady && processedAudioFilePath != null) processedAudioFilePath else audioFilePath
        if (path.isNullOrBlank()) {
            Toast.makeText(this, "No audio to play", Toast.LENGTH_SHORT).show()
            return
        }
        if (isPlaying) {
            mediaPlayer?.apply { stop(); release() }
            mediaPlayer = null
            isPlaying = false
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
            Toast.makeText(this, "Record first", Toast.LENGTH_SHORT).show()
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
                                processedAudioFilePath = outFile.absolutePath
                                isProcessedReady = true
                                btnDownload.visibility = View.VISIBLE
                                btnShare.visibility = View.VISIBLE
                                updateAudioDuration(processedAudioFilePath)
                                Toast.makeText(this@VoiceConfigActivity, "Ready! Tap Download or Share", Toast.LENGTH_SHORT).show()
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

    private fun downloadProcessedAudio() {
        val path = processedAudioFilePath
        if (path.isNullOrBlank()) {
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

    private fun shareProcessedAudio() {
        val path = processedAudioFilePath
        if (path.isNullOrBlank()) {
            Toast.makeText(this, "No processed audio to share", Toast.LENGTH_SHORT).show()
            return
        }
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
            startActivity(Intent.createChooser(shareIntent, "Share via"))
        } catch (e: Exception) {
            Toast.makeText(this, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaRecorder?.release()
    }
}
