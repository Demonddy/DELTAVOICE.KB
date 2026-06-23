package com.deltavoice

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Base64
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.deltavoice.api.CompleteVoiceWorkflowService
import com.deltavoice.auth.FeatureGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class VoiceProcessModeActivity : AppCompatActivity() {
    private lateinit var cardFull: LinearLayout
    private lateinit var cardVoice: LinearLayout
    private lateinit var cardText: LinearLayout
    private lateinit var spinnerLanguage: Spinner
    private lateinit var spinnerVoice: Spinner
    private lateinit var playRecordingButton: ImageButton
    private lateinit var audioDurationText: TextView
    private lateinit var audioSeekBar: android.widget.SeekBar
    private lateinit var buttonSend: Button
    private lateinit var buttonFullProcess: Button

    private var selectedMode: String = VoiceProcessIntent.MODE_FULL
    private var audioFilePath: String? = null
    private var processedAudioFilePath: String? = null
    private var isProcessedAudioReady = false
    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private var isProcessing = false

    private val seekBarHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var seekBarRunnable: Runnable? = null
    
    // Backend service
    private val completeVoiceWorkflowService = CompleteVoiceWorkflowService()
    private val activityScope = CoroutineScope(Dispatchers.Main)

    private lateinit var languages: List<Pair<String, String>>
    private lateinit var voiceStyles: List<Pair<String, String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_process_mode)

        cardFull = findViewById(R.id.card_full_conversion)
        cardVoice = findViewById(R.id.card_voice_only)
        cardText = findViewById(R.id.card_text_only)
        spinnerLanguage = findViewById(R.id.spinner_language)
        spinnerVoice = findViewById(R.id.spinner_voice)
        playRecordingButton = findViewById(R.id.btn_play_recording)
        audioDurationText = findViewById(R.id.audio_duration_text)
        audioSeekBar = findViewById(R.id.audio_playback_seekbar)

        // Get audio file path from intent
        audioFilePath = intent.getStringExtra(VoiceProcessIntent.EXTRA_AUDIO_FILE_PATH)

        languages = KeyboardData.languageOptions(this)
        voiceStyles = KeyboardData.videoVoiceOptions(this)

        setupSpinners()
        setupAudioPlayer()
        updateCardSelection(VoiceProcessIntent.MODE_FULL)

        cardFull.setOnClickListener {
            updateCardSelection(VoiceProcessIntent.MODE_FULL)
        }
        cardVoice.setOnClickListener {
            updateCardSelection(VoiceProcessIntent.MODE_VOICE_ONLY)
        }
        cardText.setOnClickListener {
            updateCardSelection(VoiceProcessIntent.MODE_TEXT_ONLY)
        }

        buttonSend = findViewById(R.id.button_send)
        buttonFullProcess = findViewById(R.id.button_full_process)
        
        // Send button - share the processed audio
        buttonSend.setOnClickListener {
            shareProcessedAudio()
        }
        
        // Full Process button
        buttonFullProcess.setOnClickListener {
            processVoice(selectedMode)
        }
    }

    private fun setupSpinners() {
        val languageAdapter = ArrayAdapter(
            this,
            R.layout.spinner_item_on_surface,
            languages.map { it.first }
        )
        languageAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_on_surface)
        spinnerLanguage.adapter = languageAdapter

        val voiceAdapter = ArrayAdapter(
            this,
            R.layout.spinner_item_on_surface,
            voiceStyles.map { it.first }
        )
        voiceAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_on_surface)
        spinnerVoice.adapter = voiceAdapter
    }

    private fun updateCardSelection(mode: String) {
        selectedMode = mode
        val selected = R.drawable.voice_mode_card_selected
        val unselected = R.drawable.voice_mode_card_unselected

        cardFull.setBackgroundResource(if (mode == VoiceProcessIntent.MODE_FULL) selected else unselected)
        cardVoice.setBackgroundResource(if (mode == VoiceProcessIntent.MODE_VOICE_ONLY) selected else unselected)
        cardText.setBackgroundResource(if (mode == VoiceProcessIntent.MODE_TEXT_ONLY) selected else unselected)
    }

    /**
     * Process the recorded voice based on the selected mode:
     * - MODE_FULL: Translate + change voice
     * - MODE_VOICE_ONLY: Change voice only (keep original language)
     * - MODE_TEXT_ONLY: Transcribe + translate (text output only)
     */
    private fun processVoice(mode: String) {
        val path = audioFilePath
        if (path.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.no_recording_to_process), Toast.LENGTH_SHORT).show()
            return
        }
        
        val audioFile = File(path)
        if (!audioFile.exists()) {
            Toast.makeText(this, getString(R.string.recording_file_not_found), Toast.LENGTH_SHORT).show()
            return
        }
        
        if (isProcessing) {
            Toast.makeText(this, getString(R.string.already_processing), Toast.LENGTH_SHORT).show()
            return
        }
        
        val languageCode = languages.getOrNull(spinnerLanguage.selectedItemPosition)?.second ?: "en"
        val voiceStyle = voiceStyles.getOrNull(spinnerVoice.selectedItemPosition)?.second ?: "aria"

        // Convert mode to workflow type
        val workflowType = when (mode) {
            VoiceProcessIntent.MODE_FULL -> "complete"
            VoiceProcessIntent.MODE_VOICE_ONLY -> "voice-only"
            VoiceProcessIntent.MODE_TEXT_ONLY -> "text-only"
            else -> "text-only"
        }
        
        // Show loading state
        isProcessing = true
        buttonSend.isEnabled = false
        buttonFullProcess.isEnabled = false
        
        val loadingMessage = when (workflowType) {
            "complete" -> getString(R.string.loading_translate_convert_voice)
            "voice-only" -> getString(R.string.loading_convert_voice_to, voiceStyle)
            "text-only" -> getString(R.string.loading_transcribe_translate)
            else -> getString(R.string.processing)
        }
        Toast.makeText(this, loadingMessage, Toast.LENGTH_LONG).show()
        
        activityScope.launch {
            try {
                val result = completeVoiceWorkflowService.runWorkflow(
                    audioFile = audioFile,
                    targetLanguage = languageCode,
                    voiceStyle = voiceStyle,
                    workflowType = workflowType
                )
                
                result.onSuccess { response ->
                    handleWorkflowResponse(workflowType, voiceStyle, response)
                }.onFailure { error ->
                    FeatureGate.showAuthError(this@VoiceProcessModeActivity, error)
                    buttonFullProcess.isEnabled = true
                }
            } catch (e: Exception) {
                android.util.Log.e("VoiceProcessMode", "Workflow failed", e)
                Toast.makeText(this@VoiceProcessModeActivity,
                    getString(R.string.processing_failed_msg, ""), Toast.LENGTH_LONG).show()
                buttonFullProcess.isEnabled = true
            } finally {
                isProcessing = false
            }
        }
    }
    
    private fun handleWorkflowResponse(
        workflowType: String,
        voiceStyle: String,
        response: CompleteVoiceWorkflowService.WorkflowResponse
    ) {
        val audioBase64 = response.convertedAudioBase64
        
        when (workflowType) {
            "complete" -> {
                // Full Conversion: Save processed audio and let user preview
                val translatedText = response.translatedText
                if (!translatedText.isNullOrBlank()) {
                    copyToClipboard(translatedText)
                }
                
                if (!audioBase64.isNullOrBlank()) {
                    saveAndShowProcessedAudio(audioBase64)
                    Toast.makeText(this, getString(R.string.ready_tap_play_send), Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, getString(R.string.text_copied_clipboard), Toast.LENGTH_SHORT).show()
                    buttonFullProcess.isEnabled = true
                }
            }
            
            "voice-only" -> {
                // Voice Only: Save processed audio and let user preview
                if (!audioBase64.isNullOrBlank()) {
                    saveAndShowProcessedAudio(audioBase64)
                    Toast.makeText(this, getString(R.string.voice_converted_tap_play), Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, getString(R.string.voice_conversion_failed_short), Toast.LENGTH_SHORT).show()
                    buttonFullProcess.isEnabled = true
                }
            }
            
            "text-only" -> {
                // Text Only: Show the translated/transcribed text (no audio)
                val translatedText = response.translatedText
                if (!translatedText.isNullOrBlank()) {
                    copyToClipboard(translatedText)
                    Toast.makeText(this, getString(R.string.text_transcribed_copied), Toast.LENGTH_LONG).show()
                } else {
                    val originalText = response.originalText
                    if (!originalText.isNullOrBlank()) {
                        copyToClipboard(originalText)
                        Toast.makeText(this, getString(R.string.text_transcribed_copied), Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, getString(R.string.no_text_detected_short), Toast.LENGTH_SHORT).show()
                    }
                }
                buttonFullProcess.isEnabled = true
            }
        }
    }
    
    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Transcribed Text", text)
        clipboard.setPrimaryClip(clip)
    }
    
    /**
     * Save processed audio and update the player to show it
     */
    private fun saveAndShowProcessedAudio(base64Audio: String) {
        activityScope.launch {
            try {
                val audioBytes = Base64.decode(base64Audio, Base64.DEFAULT)
                val audioFile = withContext(Dispatchers.IO) {
                    val fileName = "processed_voice_${System.currentTimeMillis()}.mp3"
                    val file = File(cacheDir, fileName)
                    file.writeBytes(audioBytes)
                    file
                }
                
                // Store the processed audio path
                processedAudioFilePath = audioFile.absolutePath
                isProcessedAudioReady = true
                
                // Update the audio duration display
                updateAudioDurationFromPath(processedAudioFilePath)
                
                // Enable the Send button
                buttonSend.isEnabled = true
                buttonFullProcess.isEnabled = true
                
                // Auto-play the processed audio
                startPlayback()
                
            } catch (e: Exception) {
                Toast.makeText(this@VoiceProcessModeActivity,
                    getString(R.string.error_saving_audio, e.message ?: ""), Toast.LENGTH_SHORT).show()
                buttonFullProcess.isEnabled = true
            }
        }
    }
    
    private fun updateAudioDurationFromPath(path: String?) {
        if (path.isNullOrBlank()) {
            audioDurationText.text = "0:00"
            audioSeekBar.max = 100
            audioSeekBar.progress = 0
            return
        }
        try {
            val player = MediaPlayer()
            player.setDataSource(path)
            player.prepare()
            val durationMs = player.duration
            player.release()
            val seconds = (durationMs / 1000) % 60
            val minutes = (durationMs / 1000) / 60
            audioDurationText.text = String.format("%d:%02d", minutes, seconds)
            audioSeekBar.max = durationMs.coerceAtLeast(1)
            audioSeekBar.progress = 0
        } catch (e: Exception) {
            audioDurationText.text = "0:00"
        }
    }
    
    /**
     * Share the processed audio file to Messenger, WhatsApp, Telegram, etc.
     */
    private fun shareProcessedAudio() {
        val audioPath = processedAudioFilePath
        if (audioPath.isNullOrBlank() || !isProcessedAudioReady) {
            Toast.makeText(this, getString(R.string.no_processed_audio_send), Toast.LENGTH_SHORT).show()
            return
        }
        val audioFile = File(audioPath)
        if (!audioFile.exists()) {
            Toast.makeText(this, getString(R.string.share_file_not_found), Toast.LENGTH_SHORT).show()
            return
        }
        val uri = try {
            androidx.core.content.FileProvider.getUriForFile(
                this, "${packageName}.fileprovider", audioFile
            )
        } catch (_: Exception) {
            try {
                val cacheCopy = File(cacheDir, "share_${System.currentTimeMillis()}_${audioFile.name}")
                audioFile.copyTo(cacheCopy, overwrite = true)
                androidx.core.content.FileProvider.getUriForFile(
                    this, "${packageName}.fileprovider", cacheCopy
                )
            } catch (e2: Exception) {
                android.util.Log.e("VoiceProcessMode", "getUri failed", e2)
                null
            }
        }
        if (uri == null) {
            Toast.makeText(this, getString(R.string.share_prepare_failed), Toast.LENGTH_LONG).show()
            return
        }
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/mpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                    clipData = ClipData.newUri(contentResolver, "audio", uri)
                }
            }
            val chooserIntent = Intent.createChooser(shareIntent, "Send voice message via").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(chooserIntent)
        } catch (e: Exception) {
            android.util.Log.e("VoiceProcessMode", "share failed", e)
            Toast.makeText(this, getString(R.string.share_failed, e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    private fun setupAudioPlayer() {
        updateAudioDuration()

        playRecordingButton.setOnClickListener {
            togglePlayback()
        }

        audioSeekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) mediaPlayer?.seekTo(progress)
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })
    }

    private fun updateAudioDuration() {
        val path = audioFilePath
        if (path.isNullOrBlank()) {
            audioDurationText.text = "0:00"
            audioSeekBar.max = 100
            audioSeekBar.progress = 0
            return
        }

        try {
            val player = MediaPlayer()
            player.setDataSource(path)
            player.prepare()
            val durationMs = player.duration
            player.release()

            val seconds = (durationMs / 1000) % 60
            val minutes = (durationMs / 1000) / 60
            audioDurationText.text = String.format("%d:%02d", minutes, seconds)
            audioSeekBar.max = durationMs.coerceAtLeast(1)
            audioSeekBar.progress = 0
        } catch (e: Exception) {
            audioDurationText.text = "0:00"
        }
    }

    private fun togglePlayback() {
        if (isPlaying) {
            stopPlayback()
        } else {
            startPlayback()
        }
    }

    private fun startPlayback() {
        // Prefer processed audio if available
        val path = if (isProcessedAudioReady && !processedAudioFilePath.isNullOrBlank()) {
            processedAudioFilePath
        } else {
            audioFilePath
        }
        
        if (path.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.no_audio_to_play), Toast.LENGTH_SHORT).show()
            return
        }

        val audioFile = File(path)
        if (!audioFile.exists()) {
            Toast.makeText(this, getString(R.string.audio_file_not_found), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            mediaPlayer?.release()

            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                prepare()

                audioSeekBar.max = duration.coerceAtLeast(1)
                audioSeekBar.progress = 0

                setOnCompletionListener {
                    this@VoiceProcessModeActivity.isPlaying = false
                    playRecordingButton.setImageResource(R.drawable.ic_play)
                    stopSeekBarUpdater()
                    audioSeekBar.progress = 0
                }

                start()
            }

            isPlaying = true
            playRecordingButton.setImageResource(R.drawable.ic_pause)
            startSeekBarUpdater()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.error_playing_audio, e.message ?: ""), Toast.LENGTH_SHORT).show()
        }
    }

    private fun startSeekBarUpdater() {
        stopSeekBarUpdater()
        val runnable = object : Runnable {
            override fun run() {
                val player = mediaPlayer ?: return
                if (!isPlaying) return
                try { audioSeekBar.progress = player.currentPosition } catch (_: Exception) {}
                seekBarHandler.postDelayed(this, 100L)
            }
        }
        seekBarRunnable = runnable
        seekBarHandler.post(runnable)
    }

    private fun stopSeekBarUpdater() {
        seekBarRunnable?.let { seekBarHandler.removeCallbacks(it) }
        seekBarRunnable = null
    }

    private fun stopPlayback() {
        stopSeekBarUpdater()
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaPlayer = null
        isPlaying = false
        playRecordingButton.setImageResource(R.drawable.ic_play)
        audioSeekBar.progress = 0
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPlayback()
        // Clean up processed audio file
        processedAudioFilePath?.let { path ->
            try {
                File(path).delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

