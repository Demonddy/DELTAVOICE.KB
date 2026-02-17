package com.deltavoice

import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.media.MediaPlayer
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.TextUtils
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputConnection
import android.graphics.Color
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import android.view.TextureView
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.view.Surface
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.Manifest
import androidx.core.content.ContextCompat
import com.deltavoice.api.VoiceToTextService
import com.deltavoice.api.TextToSpeechService
import com.deltavoice.api.TranslationService
import com.deltavoice.api.VoiceCloneService
import com.deltavoice.api.VoiceConversionService
import com.deltavoice.api.CompleteVoiceWorkflowService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.app.AlertDialog
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.Context
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Main IME Service for the Modern Keyboard
 * Handles voice input, text-to-speech, emoji insertion, and video recording
 */
class MainKeyboardService : InputMethodService(), TextToSpeech.OnInitListener {

    // UI Components
    private lateinit var voiceButton: ImageButton
    private lateinit var aiMainButton: ImageButton
    private lateinit var keyboardContainer: LinearLayout
    private lateinit var aiFeaturesContainer: LinearLayout
    private lateinit var voiceRecordingContainer: LinearLayout
    private lateinit var voiceProcessingStep2Container: LinearLayout
    private lateinit var keyboardSpinnerLanguage: Spinner
    private lateinit var keyboardSpinnerVoice: Spinner
    private lateinit var keyboardCardFull: LinearLayout
    private lateinit var keyboardCardVoice: LinearLayout
    private lateinit var keyboardCardText: LinearLayout
    private lateinit var recordingStatusText: android.widget.TextView
    private lateinit var playRecordingButton: ImageButton
    private lateinit var audioDurationText: android.widget.TextView
    private var rootView: View? = null
    
    // Playback
    private var playbackMediaPlayer: MediaPlayer? = null
    private var isPlayingRecording = false
    
    // Predictive text
    private val predictionHandler = Handler(Looper.getMainLooper())
    private var predictionRunnable: Runnable? = null
    private companion object {
        private const val PREDICTION_DELAY_MS = 300L
    }

    private val languages = listOf(
        "English" to "en",
        "Spanish" to "es",
        "French" to "fr",
        "German" to "de",
        "Italian" to "it",
        "Portuguese" to "pt",
        "Russian" to "ru",
        "Japanese" to "ja",
        "Korean" to "ko",
        "Chinese" to "zh",
        "Arabic" to "ar",
        "Hindi" to "hi"
    )
    
    // Keyboard language state
    private var currentKeyboardLanguage = "en"
    private var currentKeyboardLanguageName = "English (UK)"
    private var spaceBarButton: android.widget.Button? = null
    
    // Keyboard layouts for different languages
    private val keyboardLayouts = mapOf(
        "en" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
            row2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
            row3 = listOf("Z", "X", "C", "V", "B", "N", "M"),
            displayName = "English (UK)"
        ),
        "es" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
            row2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L", "Ñ"),
            row3 = listOf("Z", "X", "C", "V", "B", "N", "M"),
            displayName = "Español"
        ),
        "fr" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("A", "Z", "E", "R", "T", "Y", "U", "I", "O", "P"),
            row2 = listOf("Q", "S", "D", "F", "G", "H", "J", "K", "L", "M"),
            row3 = listOf("W", "X", "C", "V", "B", "N"),
            displayName = "Français"
        ),
        "de" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("Q", "W", "E", "R", "T", "Z", "U", "I", "O", "P", "Ü"),
            row2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L", "Ö", "Ä"),
            row3 = listOf("Y", "X", "C", "V", "B", "N", "M"),
            displayName = "Deutsch"
        ),
        "ru" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("Й", "Ц", "У", "К", "Е", "Н", "Г", "Ш", "Щ", "З", "Х"),
            row2 = listOf("Ф", "Ы", "В", "А", "П", "Р", "О", "Л", "Д", "Ж", "Э"),
            row3 = listOf("Я", "Ч", "С", "М", "И", "Т", "Ь", "Б", "Ю"),
            displayName = "Русский"
        ),
        "ar" to KeyboardLayout(
            numbers = listOf("١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩", "٠"),
            row1 = listOf("ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه", "خ", "ح", "ج"),
            row2 = listOf("ش", "س", "ي", "ب", "ل", "ا", "ت", "ن", "م", "ك", "ط"),
            row3 = listOf("ئ", "ء", "ؤ", "ر", "لا", "ى", "ة", "و", "ز", "ظ"),
            displayName = "العربية"
        ),
        "hi" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("ौ", "ै", "ा", "ी", "ू", "ब", "ह", "ग", "द", "ज", "ड"),
            row2 = listOf("ो", "े", "्", "ि", "ु", "प", "र", "क", "त", "च", "ट"),
            row3 = listOf("ं", "म", "न", "व", "ल", "स", "य"),
            displayName = "हिंदी"
        ),
        "ja" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
            row2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
            row3 = listOf("Z", "X", "C", "V", "B", "N", "M"),
            displayName = "日本語"
        ),
        "ko" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("ㅂ", "ㅈ", "ㄷ", "ㄱ", "ㅅ", "ㅛ", "ㅕ", "ㅑ", "ㅐ", "ㅔ"),
            row2 = listOf("ㅁ", "ㄴ", "ㅇ", "ㄹ", "ㅎ", "ㅗ", "ㅓ", "ㅏ", "ㅣ"),
            row3 = listOf("ㅋ", "ㅌ", "ㅊ", "ㅍ", "ㅠ", "ㅜ", "ㅡ"),
            displayName = "한국어"
        ),
        "zh" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
            row2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
            row3 = listOf("Z", "X", "C", "V", "B", "N", "M"),
            displayName = "中文"
        ),
        "it" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
            row2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
            row3 = listOf("Z", "X", "C", "V", "B", "N", "M"),
            displayName = "Italiano"
        ),
        "pt" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
            row2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L", "Ç"),
            row3 = listOf("Z", "X", "C", "V", "B", "N", "M"),
            displayName = "Português"
        )
    )
    
    data class KeyboardLayout(
        val numbers: List<String>,
        val row1: List<String>,
        val row2: List<String>,
        val row3: List<String>,
        val displayName: String
    )

    private val voiceStyles = listOf(
        "Adam" to "adam",
        "Aria" to "aria",
        "Roger" to "roger",
        "Sarah" to "sarah",
        "Laura" to "laura",
        "Charlie" to "charlie",
        "George" to "george",
        "Liam" to "liam"
    )

    // Speech Recognition
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    // Text-to-Speech
    private var textToSpeech: TextToSpeech? = null
    private var ttsInitialized = false

    // Voice Recording
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var recordingFilePath: String? = null
    private var processedAudioFilePath: String? = null
    private var isProcessedAudioReady = false

    enum class RecordingAction {
        TRANSCRIBE,
        CREATE_VOICE_CLONE,
        COMPLETE_WORKFLOW
    }

    private var recordingAction = RecordingAction.TRANSCRIBE
    private var pendingWorkflowType: String = "complete"
    private var pendingVoiceStyle: String = "aria"
    private var pendingTargetLanguage: String = Locale.getDefault().language

    // Keyboard Mode: NORMAL or AI
    enum class KeyboardMode {
        NORMAL,  // Standard keyboard like Gboard
        AI       // AI-powered keyboard with voice features
    }
    
    private var currentKeyboardMode = KeyboardMode.NORMAL
    
    // Mode: true for voice input, false for TTS (only in AI mode)
    private var voiceInputMode = true

    // Keyboard state
    private var isShiftPressed = false
    private var isNumbersMode = false
    private var isSymbolsMode = false
    private var shiftButton: Button? = null
    private var numbersButton: Button? = null
    
    // Language selection for voice recorder
    private var selectedLanguage = Locale.getDefault()
    private lateinit var languageButton: Button
    
    // Translation target language (for translating text)
    private var targetTranslationLanguage = Locale.getDefault()
    private lateinit var translateButton: Button

    // Supabase Services
    private val voiceToTextService = VoiceToTextService()
    private val textToSpeechService = TextToSpeechService()
    private val translationService = TranslationService()
    private val voiceCloneService = VoiceCloneService()
    private val voiceConversionService = VoiceConversionService()
    private val completeVoiceWorkflowService = CompleteVoiceWorkflowService()
    
    // Coroutine scope for async operations
    private val serviceScope = CoroutineScope(Dispatchers.Main)

    // Emoji picker
    private lateinit var emojiPickerContainer: View
    private lateinit var emojiGridFull: GridLayout
    private var currentEmojiCategory = EmojiData.Category.SMILEYS
    private val recentEmojis = mutableListOf<String>()
    private var isEmojiPickerVisible = false
    
    // Category tab buttons
    private val categoryTabs = mutableMapOf<EmojiData.Category, ImageButton>()
    
    // Calculator
    private lateinit var calculatorContainer: View
    private var isCalculatorVisible = false
    private var calcExpression = StringBuilder()
    private var calcResult = "0"
    private var lastWasOperator = false
    private var openBrackets = 0
    
    // Dictionary
    private lateinit var dictionaryContainer: View
    private var isDictionaryVisible = false
    private var dictSearchText = StringBuilder()
    private var dictCurrentLanguage = "en"
    private var dictTargetLanguage = "ar" // For translation
    private var dictMiniKeyboardLanguage = "en" // Current mini keyboard language
    
    // AI Chat
    private lateinit var aiChatContainer: View
    private var isAiChatVisible = false
    private var aiChatInputText = StringBuilder()
    private val aiChatMessages = mutableListOf<Pair<String, Boolean>>() // message, isUser
    private var aiConversationHistory = mutableListOf<Map<String, String>>() // For context
    
    // AI Writing Tools
    private lateinit var aiWritingToolsContainer: View
    private var isAiWritingToolsVisible = false
    
    private val dictLanguages = listOf(
        "en" to "English",
        "ar" to "العربية",
        "es" to "Español",
        "fr" to "Français",
        "de" to "Deutsch",
        "it" to "Italiano",
        "pt" to "Português",
        "ru" to "Русский",
        "zh" to "中文",
        "ja" to "日本語",
        "ko" to "한국어",
        "hi" to "हिंदी",
        "pt" to "Portuguese",
        "ru" to "Russian",
        "ar" to "Arabic",
        "hi" to "Hindi",
        "ja" to "Japanese",
        "ko" to "Korean",
        "zh" to "Chinese"
    )

    // Video Recording
    private var videoRecordingContainer: FrameLayout? = null
    private var videoPreviewContainer: LinearLayout? = null
    private var cameraPreview: TextureView? = null
    private var videoPlayer: VideoView? = null
    private var isVideoRecordingVisible = false
    private var isVideoPreviewVisible = false
    private var isVideoRecording = false
    private var videoFilePath: String? = null
    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var cameraManager: CameraManager? = null
    private var cameraId: String = "0"
    private var useFrontCamera = true
    private var videoMediaRecorder: MediaRecorder? = null
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null
    private var videoRecordingTimer: TextView? = null
    private var videoRecordingSeconds = 0
    private var videoTimerHandler: Handler? = null
    private var videoTimerRunnable: Runnable? = null
    private var videoSpinnerLanguage: Spinner? = null
    private var videoSpinnerVoice: Spinner? = null
    private var processedVideoAudioFilePath: String? = null  // Processed audio (MP3)
    private var isVideoProcessedAudioReady = false

    override fun onCreateInputView(): View {
        // Inflate the keyboard layout
        val view = LayoutInflater.from(this).inflate(R.layout.keyboard_layout, null)
        rootView = view
        
        // In landscape, compact the icon row so keyboard doesn't cover the screen
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            val iconSizePx = (36 * resources.displayMetrics.density).toInt()
            view.findViewById<View>(R.id.ai_features_row)?.let { row ->
                (row as? ViewGroup)?.let { group ->
                    for (i in 0 until group.childCount) {
                        group.getChildAt(i)?.layoutParams?.let { lp ->
                            if (lp is ViewGroup.MarginLayoutParams) {
                                lp.width = iconSizePx
                                lp.height = iconSizePx
                            }
                        }
                    }
                }
            }
            view.setPadding(
                view.paddingLeft,
                (4 * resources.displayMetrics.density).toInt(),
                view.paddingRight,
                view.paddingBottom
            )
        }
        
        // Load saved keyboard language preference
        loadKeyboardLanguagePreference()
        
        // Initialize UI components
        voiceButton = view.findViewById(R.id.btn_voice)
        aiMainButton = view.findViewById(R.id.btn_ai_main)
        keyboardContainer = view.findViewById(R.id.keyboard_container)
        aiFeaturesContainer = view.findViewById(R.id.ai_features_container)
        voiceRecordingContainer = view.findViewById(R.id.voice_recording_container)
        voiceProcessingStep2Container = view.findViewById(R.id.voice_processing_step2_container)
        keyboardSpinnerLanguage = view.findViewById(R.id.keyboard_spinner_language)
        keyboardSpinnerVoice = view.findViewById(R.id.keyboard_spinner_voice)
        keyboardCardFull = view.findViewById(R.id.keyboard_option_full)
        keyboardCardVoice = view.findViewById(R.id.keyboard_option_voice)
        keyboardCardText = view.findViewById(R.id.keyboard_option_text)
        recordingStatusText = view.findViewById(R.id.recording_status_text)
        playRecordingButton = view.findViewById(R.id.btn_play_recording)
        audioDurationText = view.findViewById(R.id.audio_duration_text)

        // Setup button click listeners
        setupButtons()
        
        // Setup recording UI
        setupRecordingUI(view)
        
        // Setup processing UI
        setupProcessingUI(view)
        
        // Setup emoji picker
        setupEmojiPicker(view)
        
        // Setup calculator
        setupCalculator(view)
        
        // Setup dictionary
        setupDictionary(view)
        
        // Setup AI Writing Tools
        setupAiWritingTools(view)
        
        // Setup AI Chat
        setupAiChat(view)
        
        // Setup Video Recording
        setupVideoRecording(view)

        // Setup keyboard
        setupKeyboard(view)
        
        // Setup language selector
        setupLanguageSelector(view)
        
        // Setup translation button
        setupTranslationButton(view)

        // Initialize Speech Recognizer (fallback)
        initializeSpeechRecognizer()

        // Initialize Text-to-Speech (fallback)
        textToSpeech = TextToSpeech(this, this)
        
        // Set initial mode
        updateKeyboardMode()

        return view
    }

    override fun onCreate() {
        super.onCreate()
    }

    /**
     * Setup recording UI (Step 1)
     */
    private fun setupRecordingUI(view: View) {
        // Back button - cancel recording and go back to keyboard
        view.findViewById<ImageButton>(R.id.btn_recording_back).setOnClickListener {
            if (isRecording) {
                cancelVoiceRecording()
            }
            hideRecordingUI()
        }
        
        // Mic button - start/stop recording
        view.findViewById<ImageButton>(R.id.btn_recording_mic).setOnClickListener {
            if (isRecording) {
                // Stop recording and show processing UI
                stopVoiceRecording()
            } else {
                // Start recording
                startVoiceRecording(RecordingAction.COMPLETE_WORKFLOW)
                recordingStatusText.text = "Tap to pause"
            }
        }
    }
    
    /**
     * Setup processing UI (Step 2)
     */
    private fun setupProcessingUI(view: View) {
        // Setup Spinners with dark theme
        val languageAdapter = ArrayAdapter(
            this,
            R.layout.spinner_item_dark,
            languages.map { it.first }
        )
        languageAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark)
        keyboardSpinnerLanguage.adapter = languageAdapter

        val voiceAdapter = ArrayAdapter(
            this,
            R.layout.spinner_item_dark,
            voiceStyles.map { it.first }
        )
        voiceAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark)
        keyboardSpinnerVoice.adapter = voiceAdapter

        // Mode Card Listeners
        keyboardCardFull.setOnClickListener { updateKeyboardModeSelection("complete") }
        keyboardCardVoice.setOnClickListener { updateKeyboardModeSelection("voice-only") }
        keyboardCardText.setOnClickListener { updateKeyboardModeSelection("text-only") }

        // Single Action Button - handles Done/Processing/Send states
        val actionBtn = view.findViewById<Button>(R.id.keyboard_button_action)
        
        // Tint drawable icons to white
        actionBtn.compoundDrawables.forEach { drawable ->
            drawable?.setTint(Color.WHITE)
        }
        
        // Action button - handles both processing and sending
        actionBtn.setOnClickListener {
            val btnText = actionBtn.text.toString().trim()
            when {
                btnText.contains("Send", ignoreCase = true) -> {
                    // Send the processed audio
                    shareProcessedAudio()
                }
                btnText.contains("Processing", ignoreCase = true) -> {
                    // Already processing, ignore clicks
                    Toast.makeText(this, "Please wait, processing...", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    // "Done" state - start processing
                    processRecordedVoice(pendingWorkflowType)
                }
            }
        }
        
        // Close button
        view.findViewById<ImageButton>(R.id.keyboard_button_close_processing).setOnClickListener {
            stopRecordingPlayback()
            hideVoiceProcessingUI()
            clearProcessedAudio()
            recordingFilePath = null
        }
        
        // Play recording button - plays either original or processed audio
        playRecordingButton.setOnClickListener {
            toggleRecordingPlayback()
        }
    }

    private fun updateKeyboardModeSelection(workflowType: String) {
        pendingWorkflowType = workflowType
        val selected = R.drawable.voice_mode_card_selected
        val unselected = R.drawable.voice_mode_card_unselected

        keyboardCardFull.setBackgroundResource(if (workflowType == "complete") selected else unselected)
        keyboardCardVoice.setBackgroundResource(if (workflowType == "voice-only") selected else unselected)
        keyboardCardText.setBackgroundResource(if (workflowType == "text-only") selected else unselected)
        
        // Update spinner states based on mode
        rootView?.let { view ->
            val languageContainer = view.findViewById<LinearLayout>(R.id.language_spinner_container)
            val voiceContainer = view.findViewById<LinearLayout>(R.id.voice_spinner_container)
            val languageIcon = view.findViewById<ImageView>(R.id.language_icon)
            val voiceIcon = view.findViewById<ImageView>(R.id.voice_icon)
            
            when (workflowType) {
                "complete" -> {
                    // Change Language & Voice: Both enabled
                    languageContainer?.apply {
                        isEnabled = true
                        alpha = 1.0f
                        background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.voice_mode_pill_highlighted)
                    }
                    voiceContainer?.apply {
                        isEnabled = true
                        alpha = 1.0f
                        background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.voice_mode_pill_highlighted)
                    }
                    keyboardSpinnerLanguage.isEnabled = true
                    keyboardSpinnerVoice.isEnabled = true
                    languageIcon?.setColorFilter(Color.parseColor("#C084FC"))
                    voiceIcon?.setColorFilter(Color.parseColor("#C084FC"))
                }
                "voice-only" -> {
                    // Translate My Same Voice (Cloning): Language enabled, Voice disabled
                    languageContainer?.apply {
                        isEnabled = true
                        alpha = 1.0f
                        background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.voice_mode_pill_highlighted)
                    }
                    voiceContainer?.apply {
                        isEnabled = false
                        alpha = 0.4f
                        background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.voice_mode_pill_disabled)
                    }
                    keyboardSpinnerLanguage.isEnabled = true
                    keyboardSpinnerVoice.isEnabled = false
                    languageIcon?.setColorFilter(Color.parseColor("#C084FC"))
                    voiceIcon?.setColorFilter(Color.parseColor("#6B7280"))
                }
                "text-only" -> {
                    // Transcript & Translate: Language enabled, Voice disabled
                    languageContainer?.apply {
                        isEnabled = true
                        alpha = 1.0f
                        background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.voice_mode_pill_highlighted)
                    }
                    voiceContainer?.apply {
                        isEnabled = false
                        alpha = 0.4f
                        background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.voice_mode_pill_disabled)
                    }
                    keyboardSpinnerLanguage.isEnabled = true
                    keyboardSpinnerVoice.isEnabled = false
                    languageIcon?.setColorFilter(Color.parseColor("#C084FC"))
                    voiceIcon?.setColorFilter(Color.parseColor("#6B7280"))
                }
                else -> {
                    // Default: treat as "complete" mode
                    languageContainer?.apply {
                        isEnabled = true
                        alpha = 1.0f
                        background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.voice_mode_pill_highlighted)
                    }
                    voiceContainer?.apply {
                        isEnabled = true
                        alpha = 1.0f
                        background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.voice_mode_pill_highlighted)
                    }
                    keyboardSpinnerLanguage.isEnabled = true
                    keyboardSpinnerVoice.isEnabled = true
                    languageIcon?.setColorFilter(Color.parseColor("#C084FC"))
                    voiceIcon?.setColorFilter(Color.parseColor("#C084FC"))
                }
            }
        }
    }

    /**
     * Check if the device has an active internet connection.
     * Uses API 29+ when available; falls back to getActiveNetworkInfo for minSdk 21.
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            (connectivityManager.activeNetworkInfo?.isConnected == true)
        }
    }
    
    /**
     * Test actual internet connectivity by pinging a reliable server
     */
    private suspend fun testInternetConnectivity(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = java.net.URL("https://www.google.com")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.requestMethod = "HEAD"
                val responseCode = connection.responseCode
                connection.disconnect()
                responseCode == 200
            } catch (e: Exception) {
                android.util.Log.e("DeltaVoice", "Internet test failed: ${e.message}")
                false
            }
        }
    }
    
    /**
     * Open device network settings when there's a connection issue
     */
    private fun openNetworkSettings() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback to general settings
            try {
                val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } catch (ex: Exception) {
                android.util.Log.e("DeltaVoice", "Could not open settings: ${ex.message}")
            }
        }
    }
    
    /**
     * Show a network error notification with option to open settings
     */
    private fun showNetworkErrorWithSettings(message: String) {
        android.util.Log.e("DeltaVoice", "Network error: $message")
        
        // Show toast with clear message
        Toast.makeText(this, "📶 $message\nTap globe icon → Settings to fix", Toast.LENGTH_LONG).show()
        
        // Update UI to show network error
        audioDurationText.text = "📶 No Internet"
        
        // Create a clickable notification popup
        rootView?.let { view ->
            // Show a helper banner in the processing UI
            val actionBtn = view.findViewById<Button>(R.id.keyboard_button_action)
            actionBtn?.apply {
                isEnabled = true
                text = "  Open Settings"
                setOnClickListener {
                    openNetworkSettings()
                }
            }
        }
    }
    
    /**
     * Check network and show appropriate error if not available
     * Returns true if network is available, false otherwise
     */
    private fun checkNetworkAndNotify(): Boolean {
        if (!isNetworkAvailable()) {
            showNetworkErrorWithSettings("No internet connection. Please connect to WiFi or mobile data.")
            return false
        }
        return true
    }

    private fun processRecordedVoice(workflowType: String) {
        android.util.Log.d("DeltaVoice", "processRecordedVoice called - workflowType=$workflowType, recordingFilePath=$recordingFilePath")
        
        // Check network first with user-friendly error
        if (!checkNetworkAndNotify()) {
            android.util.Log.e("DeltaVoice", "ERROR: No internet connection!")
            return
        }
        
        val audioPath = recordingFilePath
        
        // Validate recording exists
        if (audioPath.isNullOrBlank()) {
            android.util.Log.e("DeltaVoice", "ERROR: recordingFilePath is null or blank!")
            Toast.makeText(this, "No recording found. Please record first.", Toast.LENGTH_LONG).show()
            return
        }

        val audioFile = File(audioPath)
        android.util.Log.d("DeltaVoice", "Audio file: exists=${audioFile.exists()}, size=${audioFile.length()}")
        
        if (!audioFile.exists() || audioFile.length() == 0L) {
            android.util.Log.e("DeltaVoice", "ERROR: Audio file doesn't exist or is empty!")
            Toast.makeText(this, "Recording is empty. Please try again.", Toast.LENGTH_LONG).show()
            recordingFilePath = null
            return
        }

        // Get selected options with safe defaults
        val targetLang = languages.getOrNull(keyboardSpinnerLanguage.selectedItemPosition)?.second ?: "en"
        val voiceStyle = voiceStyles.getOrNull(keyboardSpinnerVoice.selectedItemPosition)?.second ?: "adam"
        val voiceStyleName = voiceStyles.getOrNull(keyboardSpinnerVoice.selectedItemPosition)?.first ?: "Adam"
        val languageName = languages.getOrNull(keyboardSpinnerLanguage.selectedItemPosition)?.first ?: "English"

        android.util.Log.d("DeltaVoice", "Options: lang=$targetLang ($languageName), voice=$voiceStyle ($voiceStyleName)")

        // Update UI for processing state - show loading
        rootView?.let { view ->
            view.findViewById<Button>(R.id.keyboard_button_action)?.apply {
                isEnabled = false
                text = "  Processing..."
                background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.voice_mode_button_purple)
            }
            // Update duration text to show loading
            audioDurationText.text = "⏳ Loading..."
        }
        
        // Show loading message
        val loadingMsg = when (workflowType) {
            "complete" -> "⏳ Changing language & voice to $languageName..."
            "voice-only" -> "⏳ Cloning your voice to $languageName..."
            "text-only" -> "⏳ Transcribing & translating to $languageName..."
            else -> "⏳ Processing..."
        }
        Toast.makeText(this, loadingMsg, Toast.LENGTH_LONG).show()
        
        android.util.Log.d("DeltaVoice", "Calling runCompleteVoiceWorkflow...")

        runCompleteVoiceWorkflow(
            audioFile = audioFile,
            workflowType = workflowType,
            targetLanguage = targetLang,
            voiceStyle = voiceStyle,
            voiceStyleName = voiceStyleName,
            languageName = languageName
        )
    }
    
    private fun showRecordingUI() {
        // Hide keyboard keys, emoji picker, calculator, dictionary, show recording UI
        keyboardContainer.visibility = View.GONE
        emojiPickerContainer.visibility = View.GONE
        isEmojiPickerVisible = false
        calculatorContainer.visibility = View.GONE
        isCalculatorVisible = false
        dictionaryContainer.visibility = View.GONE
        isDictionaryVisible = false
        aiChatContainer.visibility = View.GONE
        isAiChatVisible = false
        voiceRecordingContainer.visibility = View.VISIBLE
        recordingStatusText.text = "Tap to record"
    }
    
    private fun hideRecordingUI() {
        // Hide recording UI, show keyboard keys
        voiceRecordingContainer.visibility = View.GONE
        emojiPickerContainer.visibility = View.GONE
        isEmojiPickerVisible = false
        calculatorContainer.visibility = View.GONE
        isCalculatorVisible = false
        dictionaryContainer.visibility = View.GONE
        isDictionaryVisible = false
        aiChatContainer.visibility = View.GONE
        isAiChatVisible = false
        keyboardContainer.visibility = View.VISIBLE
    }
    
    private fun showVoiceProcessingUI() {
        android.util.Log.d("DeltaVoice", "showVoiceProcessingUI() - recordingFilePath=$recordingFilePath")
        
        // Hide recording UI, emoji picker, calculator, dictionary, AI chat if visible, show processing UI
        voiceRecordingContainer.visibility = View.GONE
        emojiPickerContainer.visibility = View.GONE
        isEmojiPickerVisible = false
        calculatorContainer.visibility = View.GONE
        isCalculatorVisible = false
        dictionaryContainer.visibility = View.GONE
        isDictionaryVisible = false
        aiChatContainer.visibility = View.GONE
        isAiChatVisible = false
        keyboardContainer.visibility = View.GONE
        voiceProcessingStep2Container.visibility = View.VISIBLE
        
        // Reset processed audio state when showing UI
        clearProcessedAudio()
        
        // Update the audio duration display with original recording
        updateAudioDuration(recordingFilePath)
        
        // Reset button states
        rootView?.let { view ->
            view.findViewById<Button>(R.id.keyboard_button_action)?.apply {
                isEnabled = true
                text = "  Done"
                background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.voice_mode_button_purple)
            }
        }
        
        android.util.Log.d("DeltaVoice", "Processing UI shown successfully")
    }
    
    private fun hideVoiceProcessingUI() {
        // Hide processing UI, show keyboard keys
        stopRecordingPlayback()
        voiceProcessingStep2Container.visibility = View.GONE
        emojiPickerContainer.visibility = View.GONE
        isEmojiPickerVisible = false
        calculatorContainer.visibility = View.GONE
        isCalculatorVisible = false
        dictionaryContainer.visibility = View.GONE
        isDictionaryVisible = false
        aiChatContainer.visibility = View.GONE
        isAiChatVisible = false
        keyboardContainer.visibility = View.VISIBLE
    }
    
    /**
     * Toggle playback of recorded audio
     */
    private fun toggleRecordingPlayback() {
        if (isPlayingRecording) {
            stopRecordingPlayback()
        } else {
            startRecordingPlayback()
        }
    }
    
    /**
     * Start playback of audio - plays processed audio if ready, otherwise original recording
     */
    private fun startRecordingPlayback() {
        // Prefer processed audio if available, otherwise play original recording
        val audioPath = if (isProcessedAudioReady && !processedAudioFilePath.isNullOrBlank()) {
            processedAudioFilePath
        } else {
            recordingFilePath
        }
        
        if (audioPath.isNullOrBlank()) {
            Toast.makeText(this, "No audio to play", Toast.LENGTH_SHORT).show()
            return
        }
        
        val audioFile = File(audioPath)
        if (!audioFile.exists()) {
            Toast.makeText(this, "Audio file not found", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            // Release any existing player
            playbackMediaPlayer?.release()
            
            playbackMediaPlayer = MediaPlayer().apply {
                setDataSource(audioPath)
                prepare()
                
                setOnCompletionListener {
                    isPlayingRecording = false
                    playRecordingButton.setImageResource(R.drawable.ic_play)
                }
                
                start()
            }
            
            isPlayingRecording = true
            playRecordingButton.setImageResource(R.drawable.ic_pause)
            
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error playing audio: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Stop playback of recorded audio
     */
    private fun stopRecordingPlayback() {
        try {
            playbackMediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        playbackMediaPlayer = null
        isPlayingRecording = false
        if (::playRecordingButton.isInitialized) {
            playRecordingButton.setImageResource(R.drawable.ic_play)
        }
    }
    
    /**
     * Update the audio duration text from a file
     */
    private fun updateAudioDuration(audioPath: String?) {
        if (audioPath.isNullOrBlank()) {
            audioDurationText.text = "0:00"
            return
        }
        
        try {
            val mediaPlayer = MediaPlayer()
            mediaPlayer.setDataSource(audioPath)
            mediaPlayer.prepare()
            val durationMs = mediaPlayer.duration
            mediaPlayer.release()
            
            val seconds = (durationMs / 1000) % 60
            val minutes = (durationMs / 1000) / 60
            audioDurationText.text = String.format("%d:%02d", minutes, seconds)
        } catch (e: Exception) {
            audioDurationText.text = "0:00"
        }
    }
    
    /**
     * Save processed audio and update the audio player to show it
     */
    private fun saveAndShowProcessedAudio(base64Audio: String, extension: String) {
        // Validate input
        if (base64Audio.isBlank()) {
            Toast.makeText(this@MainKeyboardService, "No audio data received", Toast.LENGTH_SHORT).show()
            resetButtonState()
            return
        }
        
        serviceScope.launch {
            try {
                // Decode base64 audio
                val audioBytes = try {
                    Base64.decode(base64Audio, Base64.DEFAULT)
                } catch (e: Exception) {
                    Toast.makeText(this@MainKeyboardService, "Invalid audio format", Toast.LENGTH_SHORT).show()
                    resetButtonState()
                    return@launch
                }
                
                if (audioBytes.isEmpty() || audioBytes.size < 100) {
                    Toast.makeText(this@MainKeyboardService, "Audio data is empty or too small", Toast.LENGTH_SHORT).show()
                    resetButtonState()
                    return@launch
                }
                
                // Save to file
                val audioFile = withContext(Dispatchers.IO) {
                    val fileName = "processed_voice_${System.currentTimeMillis()}.$extension"
                    val file = File(cacheDir, fileName)
                    file.writeBytes(audioBytes)
                    file
                }
                
                // Verify file was saved correctly
                if (!audioFile.exists() || audioFile.length() == 0L) {
                    Toast.makeText(this@MainKeyboardService, "Failed to save audio file", Toast.LENGTH_SHORT).show()
                    resetButtonState()
                    return@launch
                }
                
                // Store the processed audio path
                processedAudioFilePath = audioFile.absolutePath
                isProcessedAudioReady = true
                
                // Update UI on main thread
                withContext(Dispatchers.Main) {
                    // Update the audio duration display with the processed audio
                    updateAudioDuration(processedAudioFilePath)
                    
                    // Make audio player container visible
                    rootView?.findViewById<View>(R.id.audio_player_container)?.visibility = View.VISIBLE
                    
                    // Update button states - change to Send mode
                    rootView?.let { view ->
                        view.findViewById<Button>(R.id.keyboard_button_action)?.apply {
                            isEnabled = true
                            text = "  Send"
                            background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.voice_mode_button_green)
                            // Update icon to send icon
                            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_send, 0, 0, 0)
                            compoundDrawables.forEach { drawable ->
                                drawable?.setTint(Color.WHITE)
                            }
                        }
                    }
                    
                    // Update audio duration text to show it's the processed version
                    audioDurationText.text = "✓ " + audioDurationText.text
                    
                    // Auto-play the processed audio
                    startRecordingPlayback()
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainKeyboardService, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    resetButtonState()
                }
            }
        }
    }
    
    /**
     * Share the processed audio file
     */
    private fun shareProcessedAudio() {
        val audioPath = processedAudioFilePath
        if (audioPath.isNullOrBlank() || !isProcessedAudioReady) {
            Toast.makeText(this, "No processed audio to send", Toast.LENGTH_SHORT).show()
            return
        }
        val audioFile = File(audioPath)
        if (!audioFile.exists()) {
            Toast.makeText(this, "Audio file not found", Toast.LENGTH_SHORT).show()
            return
        }
        shareAudioFile(audioFile, "Send voice message via") {
            hideVoiceProcessingUI()
            clearProcessedAudioStateOnly()
            recordingFilePath = null
        }
    }
    
    /**
     * Share an audio file to Messenger, WhatsApp, Telegram, or any app.
     * Uses FileProvider so the file is treated like a normal user-authorized file.
     */
    private fun shareAudioFile(audioFile: File, chooserTitle: String, onComplete: () -> Unit = {}) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                audioFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/mpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    clipData = android.content.ClipData.newUri(contentResolver, "audio", uri)
                }
            }
            val chooserIntent = Intent.createChooser(shareIntent, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(chooserIntent)
            onComplete()
            // Delay file delete so recipient apps (WhatsApp, Telegram, etc.) have time to read it
            Handler(mainLooper).postDelayed({ try { audioFile.delete() } catch (_: Exception) { } }, 45000)
        } catch (e: Exception) {
            Toast.makeText(this, "Error sharing: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareVideoFile(videoFile: File, chooserTitle: String, onComplete: () -> Unit = {}) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                videoFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    clipData = android.content.ClipData.newUri(contentResolver, "video", uri)
                }
            }
            val chooserIntent = Intent.createChooser(shareIntent, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(chooserIntent)
            onComplete()
            Handler(mainLooper).postDelayed({ try { videoFile.delete() } catch (_: Exception) { } }, 45000)
        } catch (e: Exception) {
            Toast.makeText(this, "Error sharing: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * Clear processed audio state (resets UI only; file is deleted by shareAudioFile after delay when sharing)
     */
    private fun clearProcessedAudioStateOnly() {
        processedAudioFilePath = null
        isProcessedAudioReady = false
        rootView?.findViewById<Button>(R.id.keyboard_button_action)?.apply {
            isEnabled = true
            text = "  Done"
            background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.voice_mode_button_purple)
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_ai_mode, 0, 0, 0)
            compoundDrawables.forEach { drawable -> drawable?.setTint(Color.WHITE) }
        }
    }
    
    /**
     * Clear processed audio state and delete file (when user closes without sharing)
     */
    private fun clearProcessedAudio() {
        processedAudioFilePath?.let { path ->
            try { File(path).delete() } catch (e: Exception) { e.printStackTrace() }
        }
        clearProcessedAudioStateOnly()
    }
    
    private fun cancelVoiceRecording() {
        if (!isRecording) return
        
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        mediaRecorder = null
        isRecording = false
        voiceButton.setImageResource(R.drawable.ic_mic)
        
        // Delete the recording file
        recordingFilePath?.let { path ->
            try {
                File(path).delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        recordingFilePath = null
    }

    /**
     * Setup all button click listeners
     */
    private fun setupButtons() {
        // Prevent all buttons from taking focus (which can hide keyboard)
        voiceButton.isFocusable = false
        voiceButton.isFocusableInTouchMode = false
        aiMainButton.isFocusable = false
        aiMainButton.isFocusableInTouchMode = false
        
        // AI Main Button - Toggle AI features container
        aiMainButton.setOnClickListener {
            toggleKeyboardMode()
        }
        
        // Voice Input Button - Click to show recording UI (ready to record)
        voiceButton.setOnClickListener {
            if (isListening) {
                stopVoiceInput()
            }
            // Show recording UI (recording starts when user taps big mic button)
            showRecordingUI()
        }

        // Long press for quick voice recording (transcribe only, no UI)
        voiceButton.setOnLongClickListener {
            if (isRecording) {
                stopVoiceRecording()
            } else {
                if (isListening) {
                    Toast.makeText(this, "Stop voice input first", Toast.LENGTH_SHORT).show()
                } else {
                    startVoiceRecording(RecordingAction.TRANSCRIBE)
                }
            }
            true
        }
        
        // Setup other AI feature buttons
        setupAIFeatureButtons()
        
        // Predictions row: voice and return-to-icons buttons
        rootView?.let { v ->
            v.findViewById<ImageButton>(R.id.predictions_voice_btn)?.setOnClickListener {
                playKeyFeedback(it)
                voiceButton.performClick()
            }
            v.findViewById<ImageButton>(R.id.predictions_return_btn)?.setOnClickListener {
                playKeyFeedback(it)
                val aiRow = v.findViewById<View>(R.id.ai_features_row)
                val predictionsContainer = v.findViewById<View>(R.id.predictions_container)
                if (aiRow != null && predictionsContainer != null) {
                    showIconsHidePredictions(aiRow, predictionsContainer)
                }
            }
        }
    }
    
    /**
     * Setup AI feature button listeners
     */
    private fun setupAIFeatureButtons() {
        val view = rootView ?: return
        // Get AI feature buttons from layout
        val calculatorButton = view.findViewById<ImageButton>(R.id.btn_calculator)
        val cameraButton = view.findViewById<ImageButton>(R.id.btn_camera)
        val listButton = view.findViewById<ImageButton>(R.id.btn_list)
        val textTButton = view.findViewById<ImageButton>(R.id.btn_text_t)
        val kbPlusButton = view.findViewById<ImageButton>(R.id.btn_kb_plus)
        val globeButton = view.findViewById<ImageButton>(R.id.btn_globe)
        val appGridButton = view.findViewById<ImageButton>(R.id.btn_app_grid)
        
        // Prevent focus on all AI buttons
        listOf(calculatorButton, cameraButton, listButton, textTButton, kbPlusButton, globeButton, appGridButton)
            .forEach { button ->
                button?.isFocusable = false
                button?.isFocusableInTouchMode = false
            }
        
        // Globe button - Dictionary
        globeButton?.setOnClickListener {
            toggleDictionary()
        }
        
        // Text T button - AI Chat
        textTButton?.setOnClickListener {
            toggleAiChat()
        }
        
        // KB+ button - AI Writing Tools
        kbPlusButton?.setOnClickListener {
            toggleAiWritingTools()
        }
        
        // Calculator button - Show/hide calculator
        calculatorButton?.setOnClickListener {
            toggleCalculator()
        }
        
        // Camera button - Video recording
        cameraButton?.setOnClickListener {
            toggleVideoRecording()
        }
        
        // List button - Open app homepage
        listButton?.setOnClickListener {
            openAppHomepage()
        }
        appGridButton?.setOnClickListener {
            Toast.makeText(this, "Apps", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Setup full emoji picker with categories
     */
    private fun setupEmojiPicker(view: View) {
        emojiPickerContainer = view.findViewById(R.id.emoji_picker_include)
        emojiGridFull = view.findViewById(R.id.emoji_grid_full)
        
        // Load recent emojis from SharedPreferences
        loadRecentEmojis()
        
        // Setup category tabs
        setupEmojiCategoryTabs(view)
        
        // Setup backspace button
        view.findViewById<ImageButton>(R.id.emoji_backspace_btn)?.setOnClickListener {
            // Send backspace to input connection
            currentInputConnection?.deleteSurroundingText(1, 0)
        }
        
        // Setup back to keyboard button
        view.findViewById<ImageButton>(R.id.emoji_back_to_keyboard)?.setOnClickListener {
            hideEmojiPicker()
        }
        
        // Setup enter button
        view.findViewById<ImageButton>(R.id.emoji_enter_btn)?.setOnClickListener {
            currentInputConnection?.commitText("\n", 1)
        }
        
        // Load default category (smileys)
        loadEmojiCategory(EmojiData.Category.SMILEYS)
    }
    
    /**
     * Setup emoji category tab buttons
     */
    private fun setupEmojiCategoryTabs(view: View) {
        // Map tab IDs to categories
        val tabMappings = mapOf(
            R.id.emoji_tab_recent to EmojiData.Category.RECENT,
            R.id.emoji_tab_smileys to EmojiData.Category.SMILEYS,
            R.id.emoji_tab_people to EmojiData.Category.PEOPLE,
            R.id.emoji_tab_animals to EmojiData.Category.ANIMALS,
            R.id.emoji_tab_food to EmojiData.Category.FOOD,
            R.id.emoji_tab_activities to EmojiData.Category.ACTIVITIES,
            R.id.emoji_tab_travel to EmojiData.Category.TRAVEL,
            R.id.emoji_tab_objects to EmojiData.Category.OBJECTS,
            R.id.emoji_tab_symbols to EmojiData.Category.SYMBOLS,
            R.id.emoji_tab_flags to EmojiData.Category.FLAGS
        )
        
        tabMappings.forEach { (tabId, category) ->
            val tab = view.findViewById<ImageButton>(tabId)
            categoryTabs[category] = tab
            tab?.setOnClickListener {
                loadEmojiCategory(category)
                updateCategoryTabSelection(category)
            }
        }
    }
    
    /**
     * Update the visual selection of category tabs
     */
    private fun updateCategoryTabSelection(selectedCategory: EmojiData.Category) {
        categoryTabs.forEach { (category, tab) ->
            val tintColor = if (category == selectedCategory) {
                Color.parseColor("#4A9EFF") // Selected - blue
            } else {
                Color.parseColor("#888888") // Unselected - gray
            }
            tab.setColorFilter(tintColor)
        }
        currentEmojiCategory = selectedCategory
    }
    
    /**
     * Load emojis for a specific category into the grid
     */
    private fun loadEmojiCategory(category: EmojiData.Category) {
        emojiGridFull.removeAllViews()
        
        val emojis = if (category == EmojiData.Category.RECENT) {
            recentEmojis.toList()
        } else {
            EmojiData.getEmojisForCategory(category)
        }
        
        if (emojis.isEmpty() && category == EmojiData.Category.RECENT) {
            // Show placeholder for empty recent
            val placeholder = android.widget.TextView(this).apply {
                text = "No recent emojis"
                textSize = 14f
                setTextColor(Color.parseColor("#888888"))
                gravity = android.view.Gravity.CENTER
                setPadding(32, 64, 32, 64)
            }
            val params = GridLayout.LayoutParams().apply {
                columnSpec = GridLayout.spec(0, 8)
                width = GridLayout.LayoutParams.MATCH_PARENT
            }
            placeholder.layoutParams = params
            emojiGridFull.addView(placeholder)
            return
        }
        
        // Configure grid
        emojiGridFull.columnCount = 8

        // Create emoji buttons
        emojis.forEach { emoji ->
            val button = android.widget.TextView(this).apply {
                text = emoji
                textSize = 28f
                gravity = android.view.Gravity.CENTER
                setPadding(4, 12, 4, 12)
                setBackgroundResource(android.R.drawable.list_selector_background)
                setOnClickListener {
                    insertEmoji(emoji)
                    addToRecentEmojis(emoji)
                }
            }
            
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            }
            
            button.layoutParams = params
            emojiGridFull.addView(button)
        }
    }
    
    /**
     * Add emoji to recent list
     */
    private fun addToRecentEmojis(emoji: String) {
        // Remove if already exists
        recentEmojis.remove(emoji)
        // Add to beginning
        recentEmojis.add(0, emoji)
        // Keep only last 32
        while (recentEmojis.size > 32) {
            recentEmojis.removeAt(recentEmojis.size - 1)
        }
        // Save to SharedPreferences
        saveRecentEmojis()
    }
    
    /**
     * Load recent emojis from SharedPreferences
     */
    private fun loadRecentEmojis() {
        val prefs = getSharedPreferences("emoji_prefs", MODE_PRIVATE)
        val recentStr = prefs.getString("recent_emojis", "") ?: ""
        recentEmojis.clear()
        if (recentStr.isNotEmpty()) {
            recentEmojis.addAll(recentStr.split(",").filter { it.isNotEmpty() })
        }
    }
    
    /**
     * Save recent emojis to SharedPreferences
     */
    private fun saveRecentEmojis() {
        val prefs = getSharedPreferences("emoji_prefs", MODE_PRIVATE)
        prefs.edit().putString("recent_emojis", recentEmojis.joinToString(",")).apply()
    }
    
    /**
     * Show the emoji picker
     */
    private fun showEmojiPicker() {
        keyboardContainer.visibility = View.GONE
        emojiPickerContainer.visibility = View.VISIBLE
        isEmojiPickerVisible = true
        // Refresh current category
        loadEmojiCategory(currentEmojiCategory)
        updateCategoryTabSelection(currentEmojiCategory)
    }
    
    /**
     * Hide the emoji picker
     */
    private fun hideEmojiPicker() {
        emojiPickerContainer.visibility = View.GONE
        keyboardContainer.visibility = View.VISIBLE
        isEmojiPickerVisible = false
    }
    
    /**
     * Toggle emoji picker visibility
     */
    private fun toggleEmojiPicker() {
        if (isEmojiPickerVisible) {
            hideEmojiPicker()
        } else {
            // Hide other containers first
            aiFeaturesContainer.visibility = View.GONE
            voiceRecordingContainer.visibility = View.GONE
            voiceProcessingStep2Container.visibility = View.GONE
            calculatorContainer.visibility = View.GONE
            isCalculatorVisible = false
            dictionaryContainer.visibility = View.GONE
            isDictionaryVisible = false
            aiChatContainer.visibility = View.GONE
            isAiChatVisible = false
            showEmojiPicker()
        }
    }
    
    // ==================== CALCULATOR ====================
    
    /**
     * Setup calculator
     */
    private fun setupCalculator(view: View) {
        calculatorContainer = view.findViewById(R.id.calculator_include)
        
        // Number buttons
        val numberIds = listOf(
            R.id.calc_0, R.id.calc_1, R.id.calc_2, R.id.calc_3, R.id.calc_4,
            R.id.calc_5, R.id.calc_6, R.id.calc_7, R.id.calc_8, R.id.calc_9
        )
        numberIds.forEachIndexed { index, id ->
            view.findViewById<Button>(id)?.setOnClickListener {
                appendToExpression(index.toString())
            }
        }
        
        // Operator buttons
        view.findViewById<Button>(R.id.calc_plus)?.setOnClickListener { appendOperator("+") }
        view.findViewById<Button>(R.id.calc_minus)?.setOnClickListener { appendOperator("−") }
        view.findViewById<Button>(R.id.calc_multiply)?.setOnClickListener { appendOperator("×") }
        view.findViewById<Button>(R.id.calc_divide)?.setOnClickListener { appendOperator("÷") }
        view.findViewById<Button>(R.id.calc_percent)?.setOnClickListener { appendToExpression("%") }
        view.findViewById<Button>(R.id.calc_decimal)?.setOnClickListener { appendDecimal() }
        
        // Brackets
        view.findViewById<Button>(R.id.calc_brackets)?.setOnClickListener { appendBracket() }
        
        // Clear
        view.findViewById<Button>(R.id.calc_clear)?.setOnClickListener { clearCalculator() }
        
        // Equals
        view.findViewById<Button>(R.id.calc_equals)?.setOnClickListener { calculateResult() }
        
        // Back to keyboard
        view.findViewById<Button>(R.id.calc_keyboard)?.setOnClickListener { hideCalculator() }
        
        // Insert result into text field
        view.findViewById<Button>(R.id.calc_insert)?.setOnClickListener { insertCalculatorResult() }

        // Delete last character
        view.findViewById<Button>(R.id.calc_delete)?.setOnClickListener { deleteLastChar() }
    }
    
    /**
     * Append a character to the calculator expression
     */
    private fun appendToExpression(char: String) {
        calcExpression.append(char)
        lastWasOperator = false
        updateCalculatorDisplay()
        evaluateExpression()
    }
    
    /**
     * Append an operator to the expression
     */
    private fun appendOperator(operator: String) {
        if (calcExpression.isEmpty()) {
            if (operator == "−") {
                calcExpression.append("-")
            }
        } else if (!lastWasOperator) {
            calcExpression.append(operator)
            lastWasOperator = true
        } else {
            // Replace last operator
            calcExpression.deleteCharAt(calcExpression.length - 1)
            calcExpression.append(operator)
        }
        updateCalculatorDisplay()
    }
    
    /**
     * Append a decimal point
     */
    private fun appendDecimal() {
        // Check if current number already has a decimal
        val expr = calcExpression.toString()
        val lastNumber = expr.split(Regex("[+\\-×÷]")).lastOrNull() ?: ""
        if (!lastNumber.contains(".")) {
            if (calcExpression.isEmpty() || lastWasOperator) {
                calcExpression.append("0")
            }
            calcExpression.append(".")
            lastWasOperator = false
            updateCalculatorDisplay()
        }
    }
    
    /**
     * Append opening or closing bracket
     */
    private fun appendBracket() {
        if (openBrackets > 0 && !lastWasOperator && calcExpression.isNotEmpty()) {
            calcExpression.append(")")
            openBrackets--
        } else {
            if (calcExpression.isNotEmpty() && !lastWasOperator) {
                calcExpression.append("×")
            }
            calcExpression.append("(")
            openBrackets++
        }
        lastWasOperator = true
        updateCalculatorDisplay()
    }
    
    /**
     * Clear the calculator
     */
    private fun clearCalculator() {
        calcExpression.clear()
        calcResult = "0"
        lastWasOperator = false
        openBrackets = 0
        updateCalculatorDisplay()
    }

    /**
     * Delete the last character from the calculator expression
     */
    private fun deleteLastChar() {
        if (calcExpression.isEmpty()) return
        val lastChar = calcExpression[calcExpression.length - 1]
        calcExpression.deleteCharAt(calcExpression.length - 1)
        lastWasOperator = when (lastChar) {
            '+', '−', '×', '÷', '%', '(' -> true
            else -> false
        }
        if (lastChar == '(') openBrackets--
        if (lastChar == ')') openBrackets++
        updateCalculatorDisplay()
        evaluateExpression()
    }

    /**
     * Update the calculator display
     */
    private fun updateCalculatorDisplay() {
        rootView?.let { view ->
            view.findViewById<TextView>(R.id.calc_expression)?.text = calcExpression.toString()
            view.findViewById<TextView>(R.id.calc_result)?.text = calcResult
        }
    }
    
    /**
     * Evaluate the current expression
     */
    private fun evaluateExpression() {
        try {
            val expr = calcExpression.toString()
                .replace("×", "*")
                .replace("÷", "/")
                .replace("−", "-")
                .replace("%", "/100")
            
            if (expr.isEmpty()) {
                calcResult = "0"
                return
            }
            
            // Close any open brackets for evaluation
            var evalExpr = expr
            repeat(openBrackets) { evalExpr += ")" }
            
            val result = evaluateMathExpression(evalExpr)
            calcResult = if (result == result.toLong().toDouble()) {
                result.toLong().toString()
            } else {
                String.format("%.8f", result).trimEnd('0').trimEnd('.')
            }
        } catch (e: Exception) {
            // Keep previous result on error
        }
    }
    
    /**
     * Simple math expression evaluator
     */
    private fun evaluateMathExpression(expression: String): Double {
        return object : Any() {
            var pos = -1
            var ch = 0
            
            fun nextChar() {
                ch = if (++pos < expression.length) expression[pos].code else -1
            }
            
            fun eat(charToEat: Int): Boolean {
                while (ch == ' '.code) nextChar()
                if (ch == charToEat) {
                    nextChar()
                    return true
                }
                return false
            }
            
            fun parse(): Double {
                nextChar()
                val x = parseExpression()
                if (pos < expression.length) throw RuntimeException("Unexpected: ${ch.toChar()}")
                return x
            }
            
            fun parseExpression(): Double {
                var x = parseTerm()
                while (true) {
                    when {
                        eat('+'.code) -> x += parseTerm()
                        eat('-'.code) -> x -= parseTerm()
                        else -> return x
                    }
                }
            }
            
            fun parseTerm(): Double {
                var x = parseFactor()
                while (true) {
                    when {
                        eat('*'.code) -> x *= parseFactor()
                        eat('/'.code) -> x /= parseFactor()
                        else -> return x
                    }
                }
            }
            
            fun parseFactor(): Double {
                if (eat('+'.code)) return parseFactor()
                if (eat('-'.code)) return -parseFactor()
                
                var x: Double
                val startPos = pos
                if (eat('('.code)) {
                    x = parseExpression()
                    eat(')'.code)
                } else if (ch >= '0'.code && ch <= '9'.code || ch == '.'.code) {
                    while (ch >= '0'.code && ch <= '9'.code || ch == '.'.code) nextChar()
                    x = expression.substring(startPos, pos).toDouble()
                } else {
                    throw RuntimeException("Unexpected: ${ch.toChar()}")
                }
                
                return x
            }
        }.parse()
    }
    
    /**
     * Calculate and show final result
     */
    private fun calculateResult() {
        evaluateExpression()
        calcExpression.clear()
        calcExpression.append(calcResult)
        lastWasOperator = false
        openBrackets = 0
        updateCalculatorDisplay()
    }
    
    /**
     * Insert calculator result into the text field
     */
    private fun insertCalculatorResult() {
        currentInputConnection?.commitText(calcResult, 1)
        Toast.makeText(this, "Inserted: $calcResult", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Show the calculator
     */
    private fun showCalculator() {
        hideAllOverlays()
        keyboardContainer.visibility = View.GONE
        calculatorContainer.visibility = View.VISIBLE
        isCalculatorVisible = true
        updateCalculatorDisplay()
    }
    
    /**
     * Hide the calculator
     */
    private fun hideCalculator() {
        calculatorContainer.visibility = View.GONE
        keyboardContainer.visibility = View.VISIBLE
        isCalculatorVisible = false
    }
    
    /**
     * Toggle calculator visibility
     */
    private fun toggleCalculator() {
        if (isCalculatorVisible) {
            hideCalculator()
        } else {
            showCalculator()
        }
    }
    
    // ==================== AI WRITING TOOLS ====================
    
    /**
     * Setup AI Writing Tools container and optional button handlers
     */
    private fun setupAiWritingTools(view: View) {
        aiWritingToolsContainer = view.findViewById(R.id.ai_writing_tools_include)
        aiWritingToolsContainer.visibility = View.GONE
        // 1. Grammar – correct grammar of user input
        view.findViewById<Button>(R.id.ai_tool_grammar)?.setOnClickListener {
            runWritingTool("grammar", null, "No text to correct")
        }
        view.findViewById<Button>(R.id.ai_tool_reply)?.setOnClickListener {
            runWritingTool("reply", null, "No text to reply to")
        }
        // 2. Translate – language picker then translate
        view.findViewById<Button>(R.id.ai_tool_translate)?.setOnClickListener {
            showTranslateLanguagePicker()
        }
        // 3. Enhance – improve word choice and clarity
        view.findViewById<Button>(R.id.ai_tool_enhance)?.setOnClickListener {
            runWritingTool("enhance", null, "No text to enhance")
        }
        // 4. Tone – pick tone then rewrite
        view.findViewById<Button>(R.id.ai_tool_tone)?.setOnClickListener {
            showTonePicker()
        }
        // 5. Paraphrase
        view.findViewById<Button>(R.id.ai_tool_paraphrase)?.setOnClickListener {
            runWritingTool("paraphrase", null, "No text to paraphrase")
        }
        view.findViewById<Button>(R.id.ai_tool_continue)?.setOnClickListener {
            runWritingTool("continue", null, "No text to continue")
        }
        // 6. Make longer
        view.findViewById<Button>(R.id.ai_tool_longer)?.setOnClickListener {
            runWritingTool("longer", null, "No text to expand")
        }
        // 7. Summarize
        view.findViewById<Button>(R.id.ai_tool_summarize)?.setOnClickListener {
            runWritingTool("summarize", null, "No text to summarize")
        }
        // 8. Synonymous
        view.findViewById<Button>(R.id.ai_tool_synonymous)?.setOnClickListener {
            runWritingTool("synonyms", null, "No text to reword")
        }
        view.findViewById<Button>(R.id.ai_tool_shorter)?.setOnClickListener {
            runWritingTool("shorter", null, "No text to shorten")
        }
        view.findViewById<Button>(R.id.ai_tool_email)?.setOnClickListener {
            runWritingTool("email", null, "No text to convert to email")
        }
    }
    
    private fun showAiWritingTools() {
        hideAllOverlays()
        keyboardContainer.visibility = View.GONE
        aiWritingToolsContainer.visibility = View.VISIBLE
        isAiWritingToolsVisible = true
    }
    
    private fun hideAiWritingTools() {
        aiWritingToolsContainer.visibility = View.GONE
        keyboardContainer.visibility = View.VISIBLE
        isAiWritingToolsVisible = false
    }
    
    /**
     * Toggle AI Writing Tools visibility (KB+ button)
     */
    private fun toggleAiWritingTools() {
        if (isAiWritingToolsVisible) {
            hideAiWritingTools()
        } else {
            showAiWritingTools()
        }
    }
    
    /** Get current text in the input field (before + after cursor) and lengths for replacement. */
    private fun getCurrentInputText(): Triple<String, Int, Int>? {
        // When AI chat visible, or writing tools open with AI chat text (from prior AI chat session)
        if (isAiChatVisible || (isAiWritingToolsVisible && aiChatInputText.isNotEmpty())) {
            val text = aiChatInputText.toString()
            return Triple(text, 0, text.length)
        }
        val ic = currentInputConnection ?: return null
        val before = ic.getTextBeforeCursor(2000, 0)?.toString() ?: ""
        val after = ic.getTextAfterCursor(2000, 0)?.toString() ?: ""
        val full = "$before$after"
        return Triple(full, before.length, after.length)
    }
    
    /** Replace the current input region with new text. */
    private fun replaceCurrentInputText(beforeLen: Int, afterLen: Int, newText: String) {
        // When source was AI chat input (or writing tools used AI chat text)
        if (isAiChatVisible || (isAiWritingToolsVisible && aiChatInputText.isNotEmpty())) {
            aiChatInputText.clear()
            aiChatInputText.append(newText)
            updateAiChatInputDisplay()
            return
        }
        currentInputConnection?.let { ic ->
            ic.deleteSurroundingText(beforeLen, afterLen)
            ic.commitText(newText, 1)
        }
    }
    
    /**
     * Call writing-tool Supabase Edge Function.
     * Endpoint: https://yvizvsojpwgvaisoahda.supabase.co/functions/v1/writing-tool
     * Request: POST JSON { task, text, option? }
     * Response: { success, content, error? } - content has the result when success=true
     */
    private fun callWritingTool(
        task: String,
        text: String,
        option: String? = null,
        onResult: (String?) -> Unit
    ) {
        serviceScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val endpoint = com.deltavoice.config.SupabaseConfig.WRITING_TOOL_ENDPOINT
                    val anonKey = com.deltavoice.config.SupabaseConfig.WRITING_TOOL_ANON_KEY
                    val conn = java.net.URL(endpoint).openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("Authorization", "Bearer $anonKey")
                    conn.setRequestProperty("apikey", anonKey)
                    conn.setRequestProperty("x-client-info", "deltavoice-keyboard/1.0")
                    conn.connectTimeout = 30000
                    conn.readTimeout = 60000
                    conn.doOutput = true
                    val body = org.json.JSONObject().apply {
                        put("task", task)
                        put("text", text)
                        if (!option.isNullOrBlank()) put("option", option)
                    }.toString()
                    java.io.OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use {
                        it.write(body)
                        it.flush()
                    }
                    val responseBody = if (conn.responseCode in 200..299) {
                        conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    } else {
                        conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                            ?: "HTTP ${conn.responseCode}"
                    }
                    conn.disconnect()
                    parseWritingToolResponse(responseBody)
                } catch (e: Exception) {
                    android.util.Log.e("DeltaVoice", "Writing tool failed: ${e.message}", e)
                    null
                }
            }
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }
    
    /** Parse writing-tool response: { success, content, response?, message?, error? } */
    private fun parseWritingToolResponse(responseBody: String): String? {
        return try {
            val json = org.json.JSONObject(responseBody)
            val success = json.optBoolean("success", true)
            if (!success) {
                android.util.Log.w("DeltaVoice", "Writing tool error: ${json.optString("error", "Unknown")}")
                return null
            }
            json.optString("content").takeIf { it.isNotBlank() }
                ?: json.optString("response").takeIf { it.isNotBlank() }
                ?: json.optString("message").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
    
    /** Run a writing tool and replace input text on success. */
    private fun runWritingTool(task: String, option: String? = null, emptyMessage: String = "No text to process") {
        val input = getCurrentInputText()
        if (input == null || input.first.isBlank()) {
            Toast.makeText(this, emptyMessage, Toast.LENGTH_SHORT).show()
            return
        }
        val (fullText, beforeLen, afterLen) = input
        Toast.makeText(this, "Processing...", Toast.LENGTH_SHORT).show()
        callWritingTool(task, fullText.trim(), option) { result ->
            if (!result.isNullOrBlank()) {
                replaceCurrentInputText(beforeLen, afterLen, result)
                Toast.makeText(this, "Done", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Could not complete. Try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /** Show language picker then run translate with selected language. */
    private fun showTranslateLanguagePicker() {
        val input = getCurrentInputText()
        if (input == null || input.first.isBlank()) {
            Toast.makeText(this, "No text to translate", Toast.LENGTH_SHORT).show()
            return
        }
        val languages = listOf(
            "English", "Spanish", "French", "German", "Italian", "Portuguese",
            "Russian", "Japanese", "Korean", "Chinese", "Arabic", "Hindi", "Dutch", "Turkish"
        )
        AlertDialog.Builder(this)
            .setTitle("Translate to")
            .setItems(languages.toTypedArray()) { _, which ->
                val (fullText, beforeLen, afterLen) = input
                Toast.makeText(this, "Translating...", Toast.LENGTH_SHORT).show()
                callWritingTool("translate", fullText.trim(), languages[which]) { result ->
                    if (!result.isNullOrBlank()) {
                        replaceCurrentInputText(beforeLen, afterLen, result)
                        Toast.makeText(this, "Translation done", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Translation failed. Try again.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /** Show tone picker then run tone change with selected tone. */
    private fun showTonePicker() {
        val input = getCurrentInputText()
        if (input == null || input.first.isBlank()) {
            Toast.makeText(this, "No text to change tone", Toast.LENGTH_SHORT).show()
            return
        }
        val tones = listOf(
            "Professional", "Friendly", "Formal", "Casual", "Encouraging",
            "Assertive", "Empathetic", "Neutral", "Enthusiastic"
        )
        AlertDialog.Builder(this)
            .setTitle("Change tone to")
            .setItems(tones.toTypedArray()) { _, which ->
                val (fullText, beforeLen, afterLen) = input
                Toast.makeText(this, "Changing tone...", Toast.LENGTH_SHORT).show()
                callWritingTool("tone", fullText.trim(), tones[which]) { result ->
                    if (!result.isNullOrBlank()) {
                        replaceCurrentInputText(beforeLen, afterLen, result)
                        Toast.makeText(this, "Done", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Could not change tone. Try again.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    // ==================== DICTIONARY ====================
    
    /**
     * Setup dictionary UI
     */
    private fun setupDictionary(view: View) {
        dictionaryContainer = view.findViewById(R.id.dictionary_include)
        
        // Header: Source language (tap to change)
        view.findViewById<Button>(R.id.dict_source_lang)?.setOnClickListener {
            showDictSourceLanguageSelector()
        }
        
        // Header: Swap languages
        view.findViewById<ImageButton>(R.id.dict_swap_btn)?.setOnClickListener {
            swapDictLanguages()
        }
        
        // Header: Target language (tap to change)
        view.findViewById<Button>(R.id.dict_target_lang)?.setOnClickListener {
            showDictTargetLanguageSelector()
        }
        
        // Close button
        view.findViewById<ImageButton>(R.id.dict_close_btn)?.setOnClickListener {
            hideDictionary()
        }
        
        // Clear button
        view.findViewById<ImageButton>(R.id.dict_clear_btn)?.setOnClickListener {
            dictSearchText.clear()
            updateDictSearchDisplay()
        }
        
        // Search button
        view.findViewById<Button>(R.id.dict_search_btn)?.setOnClickListener {
            searchDictionary()
        }
        
        // Setup mini keyboard
        setupDictMiniKeyboard(view)
        
        // Symbols button - keyboard layout language selector
        view.findViewById<Button>(R.id.dict_key_symbols)?.setOnClickListener {
            showDictMiniKeyboardLanguageSelector()
        }
        
        // Globe button - keyboard layout language selector (same as symbols)
        view.findViewById<Button>(R.id.dict_key_globe)?.setOnClickListener {
            showDictMiniKeyboardLanguageSelector()
        }
        
        // Space bar - insert space into search field
        view.findViewById<Button>(R.id.dict_key_space)?.setOnClickListener {
            dictSearchText.append(" ")
            updateDictSearchDisplay()
        }
        
        // Update header to show language pair
        updateDictLanguageDisplay()
    }
    
    /**
     * Show source language selector for dictionary
     */
    private fun showDictSourceLanguageSelector() {
        showDictLanguageSelector(isSource = true)
    }
    
    /**
     * Show target language selector for dictionary
     */
    private fun showDictTargetLanguageSelector() {
        showDictLanguageSelector(isSource = false)
    }
    
    /**
     * Show language selector popup for dictionary
     */
    private fun showDictLanguageSelector(isSource: Boolean) {
        val title = if (isSource) "🌐 From Language" else "🎯 To Language"
        val currentLang = if (isSource) dictCurrentLanguage else dictTargetLanguage
        
        val popupView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1A1F2E"))
            setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())
        }
        
        // Title
        val titleView = TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 12.dpToPx())
        }
        popupView.addView(titleView)
        
        // Scrollable container
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                250.dpToPx()
            )
        }
        
        val languagesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        
        // Add all languages
        dictLanguages.forEach { (langCode, langName) ->
            val isSelected = langCode == currentLang
            val langButton = TextView(this).apply {
                text = if (isSelected) "✓ $langName" else "   $langName"
                textSize = 15f
                setTextColor(if (isSelected) Color.parseColor("#A78BFA") else Color.WHITE)
                setPadding(12.dpToPx(), 14.dpToPx(), 12.dpToPx(), 14.dpToPx())
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.dict_key_background)
                
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 4.dpToPx()
                }
            }
            languagesContainer.addView(langButton)
        }
        
        scrollView.addView(languagesContainer)
        popupView.addView(scrollView)
        
        // Create popup window
        val popupWindow = android.widget.PopupWindow(
            popupView,
            260.dpToPx(),
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 10f
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            isFocusable = true
        }
        
        // Set click listeners for each language
        for (i in 0 until languagesContainer.childCount) {
            val button = languagesContainer.getChildAt(i)
            val (langCode, langName) = dictLanguages[i]
            button.setOnClickListener {
                if (isSource) {
                    dictCurrentLanguage = langCode
                    dictMiniKeyboardLanguage = langCode
                    rebuildDictMiniKeyboard()
                    Toast.makeText(this, "From: $langName", Toast.LENGTH_SHORT).show()
                } else {
                    dictTargetLanguage = langCode
                    Toast.makeText(this, "To: $langName", Toast.LENGTH_SHORT).show()
                }
                updateDictLanguageDisplay()
                popupWindow.dismiss()
            }
        }
        
        // Show popup
        rootView?.let { view ->
            popupWindow.showAtLocation(view, Gravity.CENTER, 0, 0)
        }
    }
    
    /**
     * Update dictionary language display in header
     */
    private fun updateDictLanguageDisplay() {
        rootView?.let { view ->
            val sourceName = getLanguageName(dictCurrentLanguage)
            val targetName = getLanguageName(dictTargetLanguage)
            view.findViewById<Button>(R.id.dict_source_lang)?.text = sourceName
            view.findViewById<Button>(R.id.dict_target_lang)?.text = targetName
        }
    }
    
    /**
     * Swap source and target languages
     */
    private fun swapDictLanguages() {
        val temp = dictCurrentLanguage
        dictCurrentLanguage = dictTargetLanguage
        dictTargetLanguage = temp
        
        // Also update mini keyboard language
        dictMiniKeyboardLanguage = dictCurrentLanguage
        rebuildDictMiniKeyboard()
        updateDictLanguageDisplay()
        
        Toast.makeText(this, "🔄 ${getLanguageName(dictCurrentLanguage)} → ${getLanguageName(dictTargetLanguage)}", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Mini keyboard layouts for different languages
     */
    private val dictMiniKeyboardLayouts = mapOf(
        "en" to Triple(
            listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
            listOf("z", "x", "c", "v", "b", "n", "m")
        ),
        "ar" to Triple(
            listOf("ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه", "خ", "ح"),
            listOf("ش", "س", "ي", "ب", "ل", "ا", "ت", "ن", "م", "ك"),
            listOf("ئ", "ء", "ؤ", "ر", "ى", "ة", "و", "ز")
        ),
        "ru" to Triple(
            listOf("й", "ц", "у", "к", "е", "н", "г", "ш", "щ", "з"),
            listOf("ф", "ы", "в", "а", "п", "р", "о", "л", "д", "ж"),
            listOf("я", "ч", "с", "м", "и", "т", "ь", "б")
        ),
        "fr" to Triple(
            listOf("a", "z", "e", "r", "t", "y", "u", "i", "o", "p"),
            listOf("q", "s", "d", "f", "g", "h", "j", "k", "l", "m"),
            listOf("w", "x", "c", "v", "b", "n", "é", "è")
        ),
        "de" to Triple(
            listOf("q", "w", "e", "r", "t", "z", "u", "i", "o", "p"),
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "ö"),
            listOf("y", "x", "c", "v", "b", "n", "m", "ü")
        ),
        "es" to Triple(
            listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "ñ"),
            listOf("z", "x", "c", "v", "b", "n", "m")
        ),
        "it" to Triple(
            listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
            listOf("z", "x", "c", "v", "b", "n", "m")
        ),
        "pt" to Triple(
            listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "ç"),
            listOf("z", "x", "c", "v", "b", "n", "m")
        ),
        "hi" to Triple(
            listOf("ौ", "ै", "ा", "ी", "ू", "ब", "ह", "ग", "द", "ज"),
            listOf("ो", "े", "्", "ि", "ु", "प", "र", "क", "त", "च"),
            listOf("ं", "म", "न", "व", "ल", "स", "य")
        ),
        "zh" to Triple(
            listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
            listOf("z", "x", "c", "v", "b", "n", "m")
        ),
        "ja" to Triple(
            listOf("あ", "い", "う", "え", "お", "か", "き", "く", "け", "こ"),
            listOf("さ", "し", "す", "せ", "そ", "た", "ち", "つ", "て", "と"),
            listOf("な", "に", "ぬ", "ね", "の", "は", "ひ")
        ),
        "ko" to Triple(
            listOf("ㅂ", "ㅈ", "ㄷ", "ㄱ", "ㅅ", "ㅛ", "ㅕ", "ㅑ", "ㅐ", "ㅔ"),
            listOf("ㅁ", "ㄴ", "ㅇ", "ㄹ", "ㅎ", "ㅗ", "ㅓ", "ㅏ", "ㅣ"),
            listOf("ㅋ", "ㅌ", "ㅊ", "ㅍ", "ㅠ", "ㅜ", "ㅡ")
        )
    )
    
    /**
     * Setup mini keyboard for dictionary
     */
    private fun setupDictMiniKeyboard(view: View) {
        rebuildDictMiniKeyboard(view)
    }
    
    /**
     * Rebuild dictionary mini keyboard with current language
     */
    private fun rebuildDictMiniKeyboard(view: View? = rootView) {
        view ?: return
        
        val row0 = view.findViewById<LinearLayout>(R.id.dict_row0)
        val row1 = view.findViewById<LinearLayout>(R.id.dict_row1)
        val row2 = view.findViewById<LinearLayout>(R.id.dict_row2)
        val row3 = view.findViewById<LinearLayout>(R.id.dict_row3)
        
        // Clear existing keys
        row0?.removeAllViews()
        row1?.removeAllViews()
        row2?.removeAllViews()
        row3?.removeAllViews()
        
        val numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        val layout = dictMiniKeyboardLayouts[dictMiniKeyboardLanguage] 
            ?: dictMiniKeyboardLayouts["en"]!!
        
        // Number row
        numbers.forEach { key ->
            row0?.addView(createDictKey(key, isNumber = true))
        }
        
        // Row 1
        layout.first.forEach { key ->
            row1?.addView(createDictKey(key))
        }
        
        // Row 2
        layout.second.forEach { key ->
            row2?.addView(createDictKey(key))
        }
        
        // Row 3 with backspace
        layout.third.forEach { key ->
            row3?.addView(createDictKey(key))
        }
        
        // Add backspace to row 3
        row3?.addView(createDictSpecialKey("⌫") {
            if (dictSearchText.isNotEmpty()) {
                dictSearchText.deleteCharAt(dictSearchText.length - 1)
                updateDictSearchDisplay()
            }
        })
    }
    
    /**
     * Show language selector for dictionary mini keyboard
     */
    private fun showDictMiniKeyboardLanguageSelector() {
        val availableLanguages = listOf(
            "en" to "English",
            "ar" to "العربية",
            "ru" to "Русский",
            "fr" to "Français",
            "de" to "Deutsch",
            "es" to "Español",
            "hi" to "हिंदी",
            "zh" to "中文 (Pinyin)",
            "ja" to "日本語",
            "ko" to "한국어"
        )
        
        // Create popup
        val popupView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1A1F2E"))
            setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())
        }
        
        // Title
        val titleView = TextView(this).apply {
            text = "🌐 Keyboard Language"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 12.dpToPx())
        }
        popupView.addView(titleView)
        
        // Scrollable container
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                200.dpToPx()
            )
        }
        
        val languagesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        
        // Add language buttons
        availableLanguages.forEach { (langCode, langName) ->
            val isSelected = langCode == dictMiniKeyboardLanguage
            val langButton = TextView(this).apply {
                text = if (isSelected) "✓ $langName" else "   $langName"
                textSize = 15f
                setTextColor(if (isSelected) Color.parseColor("#A78BFA") else Color.WHITE)
                setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.dict_key_background)
                
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 4.dpToPx()
                }
            }
            languagesContainer.addView(langButton)
        }
        
        scrollView.addView(languagesContainer)
        popupView.addView(scrollView)
        
        // Create popup window
        val popupWindow = android.widget.PopupWindow(
            popupView,
            260.dpToPx(),
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 10f
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            isFocusable = true
        }
        
        // Set click listeners
        for (i in 0 until languagesContainer.childCount) {
            val button = languagesContainer.getChildAt(i)
            val (langCode, langName) = availableLanguages[i]
            button.setOnClickListener {
                dictMiniKeyboardLanguage = langCode
                rebuildDictMiniKeyboard()
                Toast.makeText(this, "Keyboard: $langName", Toast.LENGTH_SHORT).show()
                popupWindow.dismiss()
            }
        }
        
        // Show popup
        rootView?.let { view ->
            popupWindow.showAtLocation(view, Gravity.CENTER, 0, 0)
        }
    }
    
    /**
     * Create a dictionary mini keyboard key
     */
    private fun createDictKey(letter: String, isNumber: Boolean = false): Button {
        return Button(this).apply {
            text = letter
            textSize = if (isNumber) 20f else 22f
            setTextColor(Color.parseColor("#FFFFFF"))
            background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.dict_key_background)
            isAllCaps = false
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
            includeFontPadding = false
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            
            val params = LinearLayout.LayoutParams(0, 48.dpToPx()).apply {
                weight = 1f
                marginStart = 2.dpToPx()
                marginEnd = 2.dpToPx()
                topMargin = 2.dpToPx()
                bottomMargin = 2.dpToPx()
            }
            layoutParams = params
            
            setOnClickListener {
                dictSearchText.append(letter)
                updateDictSearchDisplay()
            }
        }
    }
    
    /**
     * Create a dictionary special key
     */
    private fun createDictSpecialKey(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 20f
            setTextColor(Color.parseColor("#FFFFFF"))
            background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.dict_key_special)
            isAllCaps = false
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
            includeFontPadding = false
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            
            val params = LinearLayout.LayoutParams(0, 48.dpToPx()).apply {
                weight = 1.3f
                marginStart = 2.dpToPx()
                marginEnd = 2.dpToPx()
                topMargin = 2.dpToPx()
                bottomMargin = 2.dpToPx()
            }
            layoutParams = params
            
            setOnClickListener { onClick() }
        }
    }
    
    /**
     * Convert dp to pixels
     */
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
    
    /**
     * Get key height in dp based on user's keyboard height setting.
     * In landscape, uses reduced height so the keyboard doesn't cover the screen.
     */
    private fun getKeyHeightDp(): Int {
        val prefs = getSharedPreferences("deltavoice_prefs", MODE_PRIVATE)
        val baseHeight = when (prefs.getString("keyboard_height", "Normal")) {
            "Extra short" -> 36
            "Short" -> 40
            "Tall" -> 52
            "Custom" -> prefs.getInt("keyboard_height_custom", 44).coerceIn(32, 72)
            else -> 44 // Normal
        }
        return if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            (baseHeight * 0.72).toInt().coerceIn(28, 40)
        } else {
            baseHeight
        }
    }
    
    /**
     * Update dictionary search display
     */
    private fun updateDictSearchDisplay() {
        rootView?.let { view ->
            view.findViewById<TextView>(R.id.dict_search_text)?.text = dictSearchText.toString()
            view.findViewById<ImageButton>(R.id.dict_clear_btn)?.visibility = 
                if (dictSearchText.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }
    
    /**
     * Search dictionary for the word with two-language translation
     */
    private fun searchDictionary() {
        val word = dictSearchText.toString().trim()
        if (word.isEmpty()) {
            Toast.makeText(this, "Please type a word to search", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Show loading state
        showDictLoading()
        
        // Get source and target languages
        val sourceLang = dictCurrentLanguage
        val targetLang = dictTargetLanguage
        
        // Search for definition and translation
        serviceScope.launch {
            try {
                // Get English definition if source is English
                val definition = if (sourceLang == "en") {
                    fetchDictionaryDefinition(word, "en")
                } else null
                
                // Get translation to target language
                val translation = fetchSingleTranslation(word, sourceLang, targetLang)
                
                withContext(Dispatchers.Main) {
                    showDictTwoLanguageResult(word, definition, translation, sourceLang, targetLang)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showDictError("Error: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Dictionary result data class
     */
    data class DictResult(
        val word: String,
        val phonetic: String?,
        val partOfSpeech: String?,
        val definition: String,
        val example: String?
    )
    
    /**
     * Fetch single translation between two languages
     */
    private suspend fun fetchSingleTranslation(word: String, sourceLang: String, targetLang: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val apiKey = com.deltavoice.config.SupabaseConfig.SUPABASE_ANON_KEY
                val supabaseUrl = com.deltavoice.config.SupabaseConfig.SUPABASE_URL
                
                val url = java.net.URL("$supabaseUrl/functions/v1/free-translate-text")
                val connection = url.openConnection() as java.net.HttpURLConnection
                
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
                connection.setRequestProperty("apikey", apiKey)
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.doOutput = true
                
                val escapedWord = word.replace("\"", "\\\"")
                val requestBody = """{"text":"$escapedWord","targetLanguage":"$targetLang","sourceLanguage":"$sourceLang"}"""
                
                android.util.Log.d("DeltaVoice", "Translation request: $sourceLang -> $targetLang: $word")
                
                java.io.OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(requestBody)
                    writer.flush()
                }
                
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    android.util.Log.d("DeltaVoice", "Translation response: $response")
                    
                    val translatedMatch = Regex(""""translatedText"\s*:\s*"([^"]+)"""").find(response)
                    if (translatedMatch != null) {
                        val translated = translatedMatch.groupValues[1]
                        if (translated.isNotBlank()) {
                            return@withContext translated
                        }
                    }
                }
                
                connection.disconnect()
                null
            } catch (e: Exception) {
                android.util.Log.e("DeltaVoice", "Translation error: ${e.message}")
                null
            }
        }
    }
    
    /**
     * Fetch dictionary definition from Free Dictionary API
     */
    private suspend fun fetchDictionaryDefinition(word: String, language: String): DictResult? {
        return withContext(Dispatchers.IO) {
            try {
                val url = java.net.URL("https://api.dictionaryapi.dev/api/v2/entries/en/$word")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    parseDictionaryResponse(response, word)
                } else {
                    null
                }
            } catch (e: Exception) {
                android.util.Log.e("DeltaVoice", "Dictionary error: ${e.message}")
                null
            }
        }
    }
    
    /**
     * Parse dictionary API response
     */
    private fun parseDictionaryResponse(json: String, word: String): DictResult? {
        try {
            val phonetic = Regex("\"phonetic\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)
            val partOfSpeech = Regex("\"partOfSpeech\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)
            val definition = Regex("\"definition\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)
            val example = Regex("\"example\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)
            
            if (definition != null) {
                return DictResult(
                    word = word,
                    phonetic = phonetic,
                    partOfSpeech = partOfSpeech,
                    definition = definition,
                    example = example
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("DeltaVoice", "Parse error: ${e.message}")
        }
        return null
    }
    
    /**
     * Get language name from code
     */
    private fun getLanguageName(code: String): String {
        return when (code) {
            "en" -> "English"
            "ar" -> "العربية"
            "es" -> "Español"
            "fr" -> "Français"
            "de" -> "Deutsch"
            "it" -> "Italiano"
            "pt" -> "Português"
            "ru" -> "Русский"
            "zh" -> "中文"
            "ja" -> "日本語"
            "ko" -> "한국어"
            "hi" -> "हिंदी"
            else -> code.uppercase()
        }
    }
    
    /**
     * Show dictionary result with two-language translation
     */
    private fun showDictTwoLanguageResult(word: String, definition: DictResult?, translation: String?, sourceLang: String, targetLang: String) {
        rootView?.let { view ->
            val resultContainer = view.findViewById<LinearLayout>(R.id.dict_results_container)
            resultContainer?.removeAllViews()
            resultContainer?.visibility = View.VISIBLE
            
            // Hide placeholder
            view.findViewById<TextView>(R.id.dict_placeholder)?.visibility = View.GONE
            
            val sourceName = getLanguageName(sourceLang)
            val targetName = getLanguageName(targetLang)
            
            // Header showing translation direction
            val headerView = TextView(this).apply {
                text = "🔄 $sourceName → $targetName"
                textSize = 14f
                setTextColor(Color.parseColor("#A78BFA"))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, 12.dpToPx())
                gravity = Gravity.CENTER
            }
            resultContainer?.addView(headerView)
            
            // Source word (large)
            val sourceWordView = TextView(this).apply {
                text = "📝 $word"
                textSize = 24f
                setTextColor(Color.WHITE)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, 8.dpToPx())
            }
            resultContainer?.addView(sourceWordView)
            
            // Phonetic (if available)
            definition?.phonetic?.let { phonetic ->
                val phoneticView = TextView(this).apply {
                    text = "🔊 $phonetic"
                    textSize = 14f
                    setTextColor(Color.parseColor("#9CA3AF"))
                    setPadding(0, 0, 0, 8.dpToPx())
                }
                resultContainer?.addView(phoneticView)
            }
            
            // Divider
            val divider = View(this).apply {
                setBackgroundColor(Color.parseColor("#374151"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    2.dpToPx()
                ).apply {
                    topMargin = 8.dpToPx()
                    bottomMargin = 12.dpToPx()
                }
            }
            resultContainer?.addView(divider)
            
            // Translation (large, prominent)
            if (translation != null) {
                val translationLabel = TextView(this).apply {
                    text = "🌍 Translation:"
                    textSize = 14f
                    setTextColor(Color.parseColor("#9CA3AF"))
                    setPadding(0, 0, 0, 4.dpToPx())
                }
                resultContainer?.addView(translationLabel)
                
                val translationView = TextView(this).apply {
                    text = translation
                    textSize = 28f
                    setTextColor(Color.parseColor("#10B981"))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(0, 0, 0, 12.dpToPx())
                    
                    // Make it clickable to copy
                    setOnClickListener {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("translation", translation)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this@MainKeyboardService, "📋 Copied: $translation", Toast.LENGTH_SHORT).show()
                    }
                }
                resultContainer?.addView(translationView)
            } else {
                val noTransView = TextView(this).apply {
                    text = "❌ Translation not available"
                    textSize = 16f
                    setTextColor(Color.parseColor("#EF4444"))
                    setPadding(0, 0, 0, 12.dpToPx())
                }
                resultContainer?.addView(noTransView)
            }
            
            // Definition (if available)
            if (definition != null) {
                val defDivider = View(this).apply {
                    setBackgroundColor(Color.parseColor("#374151"))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1.dpToPx()
                    ).apply {
                        topMargin = 4.dpToPx()
                        bottomMargin = 8.dpToPx()
                    }
                }
                resultContainer?.addView(defDivider)
                
                val defLabel = TextView(this).apply {
                    text = "📖 Definition:"
                    textSize = 14f
                    setTextColor(Color.parseColor("#9CA3AF"))
                    setPadding(0, 0, 0, 4.dpToPx())
                }
                resultContainer?.addView(defLabel)
                
                val defView = TextView(this).apply {
                    text = definition.definition
                    textSize = 15f
                    setTextColor(Color.WHITE)
                    setPadding(0, 0, 0, 8.dpToPx())
                }
                resultContainer?.addView(defView)
                
                // Example if available
                definition.example?.let { example ->
                    val exampleView = TextView(this).apply {
                        text = "💬 \"$example\""
                        textSize = 14f
                        setTextColor(Color.parseColor("#6B7280"))
                        setTypeface(typeface, android.graphics.Typeface.ITALIC)
                        setPadding(0, 0, 0, 8.dpToPx())
                    }
                    resultContainer?.addView(exampleView)
                }
            }
            
            // Action buttons row
            val buttonsRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 12.dpToPx()
                }
            }
            
            // Copy translation button
            if (translation != null) {
                val copyBtn = Button(this).apply {
                    text = "📋 Copy"
                    textSize = 12f
                    setTextColor(Color.WHITE)
                    background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.dict_key_special)
                    layoutParams = LinearLayout.LayoutParams(0, 44.dpToPx(), 1f).apply {
                        marginEnd = 4.dpToPx()
                    }
                    setOnClickListener {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("translation", translation)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this@MainKeyboardService, "📋 Copied!", Toast.LENGTH_SHORT).show()
                    }
                }
                buttonsRow.addView(copyBtn)
            }
            
            // Insert translation button
            if (translation != null) {
                val insertBtn = Button(this).apply {
                    text = "📝 Insert"
                    textSize = 12f
                    setTextColor(Color.WHITE)
                    background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.voice_mode_button_purple)
                    layoutParams = LinearLayout.LayoutParams(0, 44.dpToPx(), 1f).apply {
                        marginStart = 4.dpToPx()
                    }
                    setOnClickListener {
                        insertText(translation)
                        hideDictionary()
                        Toast.makeText(this@MainKeyboardService, "✓ Inserted!", Toast.LENGTH_SHORT).show()
                    }
                }
                buttonsRow.addView(insertBtn)
            }
            
            resultContainer?.addView(buttonsRow)
        }
    }
    
    /**
     * Show dictionary result with translations (legacy)
     */
    private fun showDictResultWithTranslations(definition: DictResult?, translations: Map<String, String>, word: String) {
        rootView?.let { view ->
            val resultContainer = view.findViewById<LinearLayout>(R.id.dict_results_container)
            resultContainer?.removeAllViews()
            resultContainer?.visibility = View.VISIBLE
            
            // Hide placeholder
            view.findViewById<TextView>(R.id.dict_placeholder)?.visibility = View.GONE
            
            // Word title
            val titleView = TextView(this).apply {
                text = "📖 $word"
                textSize = 22f
                setTextColor(Color.WHITE)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, 8.dpToPx())
            }
            resultContainer?.addView(titleView)
            
            // Definition section (if available)
            if (definition != null) {
                // Phonetic
                definition.phonetic?.let { phonetic ->
                    val phoneticView = TextView(this).apply {
                        text = "🔊 $phonetic"
                        textSize = 14f
                        setTextColor(Color.parseColor("#A78BFA"))
                        setPadding(0, 0, 0, 4.dpToPx())
                    }
                    resultContainer?.addView(phoneticView)
                }
                
                // Part of speech
                definition.partOfSpeech?.let { pos ->
                    val posView = TextView(this).apply {
                        text = "[$pos]"
                        textSize = 14f
                        setTextColor(Color.parseColor("#9CA3AF"))
                        setTypeface(typeface, android.graphics.Typeface.ITALIC)
                        setPadding(0, 0, 0, 8.dpToPx())
                    }
                    resultContainer?.addView(posView)
                }
                
                // Definition
                val defView = TextView(this).apply {
                    text = "📝 ${definition.definition}"
                    textSize = 16f
                    setTextColor(Color.WHITE)
                    setPadding(0, 0, 0, 8.dpToPx())
                }
                resultContainer?.addView(defView)
                
                // Example
                definition.example?.let { example ->
                    val exampleView = TextView(this).apply {
                        text = "💬 \"$example\""
                        textSize = 14f
                        setTextColor(Color.parseColor("#9CA3AF"))
                        setTypeface(typeface, android.graphics.Typeface.ITALIC)
                        setPadding(0, 0, 0, 12.dpToPx())
                    }
                    resultContainer?.addView(exampleView)
                }
            }
            
            // Translations section
            if (translations.isNotEmpty()) {
                // Divider
                val divider = View(this).apply {
                    setBackgroundColor(Color.parseColor("#374151"))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1.dpToPx()
                    ).apply {
                        topMargin = 8.dpToPx()
                        bottomMargin = 12.dpToPx()
                    }
                }
                resultContainer?.addView(divider)
                
                // Translations header
                val transHeader = TextView(this).apply {
                    text = "🌍 Translations"
                    textSize = 18f
                    setTextColor(Color.WHITE)
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding(0, 0, 0, 8.dpToPx())
                }
                resultContainer?.addView(transHeader)
                
                // Each translation
                translations.forEach { (language, translation) ->
                    val transRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, 4.dpToPx(), 0, 4.dpToPx())
                        gravity = Gravity.CENTER_VERTICAL
                        
                        // Make row clickable to copy
                        setOnClickListener {
                            // Copy to clipboard
                            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("translation", translation)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(this@MainKeyboardService, "Copied: $translation", Toast.LENGTH_SHORT).show()
                        }
                        
                        background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.dict_key_background)
                    }
                    
                    val langLabel = TextView(this).apply {
                        text = "$language:"
                        textSize = 14f
                        setTextColor(Color.parseColor("#A78BFA"))
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        layoutParams = LinearLayout.LayoutParams(
                            100.dpToPx(),
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
                    }
                    transRow.addView(langLabel)
                    
                    val transText = TextView(this).apply {
                        text = translation
                        textSize = 16f
                        setTextColor(Color.WHITE)
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        )
                        setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
                    }
                    transRow.addView(transText)
                    
                    // Copy icon
                    val copyIcon = TextView(this).apply {
                        text = "📋"
                        textSize = 16f
                        setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
                    }
                    transRow.addView(copyIcon)
                    
                    val rowParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = 4.dpToPx()
                    }
                    transRow.layoutParams = rowParams
                    
                    resultContainer?.addView(transRow)
                }
            }
            
            // Insert button
            val insertButton = Button(this).apply {
                text = "📝 Insert Word"
                textSize = 14f
                setTextColor(Color.WHITE)
                background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.voice_mode_button_purple)
                
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    48.dpToPx()
                ).apply {
                    topMargin = 16.dpToPx()
                }
                
                setOnClickListener {
                    insertText(word)
                    hideDictionary()
                    Toast.makeText(this@MainKeyboardService, "Inserted: $word", Toast.LENGTH_SHORT).show()
                }
            }
            resultContainer?.addView(insertButton)
        }
    }
    
    /**
     * Show dictionary loading state
     */
    private fun showDictLoading() {
        rootView?.let { view ->
            view.findViewById<TextView>(R.id.dict_placeholder)?.apply {
                text = "Searching..."
                visibility = View.VISIBLE
            }
            view.findViewById<TextView>(R.id.dict_word_title)?.visibility = View.GONE
            view.findViewById<TextView>(R.id.dict_phonetic)?.visibility = View.GONE
            view.findViewById<TextView>(R.id.dict_part_of_speech)?.visibility = View.GONE
            view.findViewById<TextView>(R.id.dict_definition)?.visibility = View.GONE
            view.findViewById<TextView>(R.id.dict_example)?.visibility = View.GONE
        }
    }
    
    /**
     * Show dictionary result
     */
    private fun showDictResult(result: DictResult) {
        rootView?.let { view ->
            view.findViewById<TextView>(R.id.dict_placeholder)?.visibility = View.GONE
            
            view.findViewById<TextView>(R.id.dict_word_title)?.apply {
                text = result.word
                visibility = View.VISIBLE
            }
            
            view.findViewById<TextView>(R.id.dict_phonetic)?.apply {
                text = result.phonetic ?: ""
                visibility = if (result.phonetic != null) View.VISIBLE else View.GONE
            }
            
            view.findViewById<TextView>(R.id.dict_part_of_speech)?.apply {
                text = result.partOfSpeech ?: ""
                visibility = if (result.partOfSpeech != null) View.VISIBLE else View.GONE
            }
            
            view.findViewById<TextView>(R.id.dict_definition)?.apply {
                text = result.definition
                visibility = View.VISIBLE
            }
            
            view.findViewById<TextView>(R.id.dict_example)?.apply {
                text = if (result.example != null) "\"${result.example}\"" else ""
                visibility = if (result.example != null) View.VISIBLE else View.GONE
            }
        }
    }
    
    /**
     * Show dictionary error
     */
    private fun showDictError(message: String) {
        rootView?.let { view ->
            view.findViewById<TextView>(R.id.dict_placeholder)?.apply {
                text = message
                visibility = View.VISIBLE
            }
            view.findViewById<TextView>(R.id.dict_word_title)?.visibility = View.GONE
            view.findViewById<TextView>(R.id.dict_phonetic)?.visibility = View.GONE
            view.findViewById<TextView>(R.id.dict_part_of_speech)?.visibility = View.GONE
            view.findViewById<TextView>(R.id.dict_definition)?.visibility = View.GONE
            view.findViewById<TextView>(R.id.dict_example)?.visibility = View.GONE
        }
    }
    
    /**
     * Show the dictionary
     */
    private fun showDictionary() {
        keyboardContainer.visibility = View.GONE
        emojiPickerContainer.visibility = View.GONE
        isEmojiPickerVisible = false
        calculatorContainer.visibility = View.GONE
        isCalculatorVisible = false
        aiWritingToolsContainer.visibility = View.GONE
        isAiWritingToolsVisible = false
        aiFeaturesContainer.visibility = View.GONE
        voiceRecordingContainer.visibility = View.GONE
        voiceProcessingStep2Container.visibility = View.GONE
        dictionaryContainer.visibility = View.VISIBLE
        isDictionaryVisible = true
        
        // Reset search
        dictSearchText.clear()
        updateDictSearchDisplay()
        
        // Reset results
        rootView?.let { view ->
            view.findViewById<TextView>(R.id.dict_placeholder)?.apply {
                text = "Type a word and tap Search to see its definition"
                visibility = View.VISIBLE
            }
            view.findViewById<TextView>(R.id.dict_word_title)?.visibility = View.GONE
            view.findViewById<TextView>(R.id.dict_phonetic)?.visibility = View.GONE
            view.findViewById<TextView>(R.id.dict_part_of_speech)?.visibility = View.GONE
            view.findViewById<TextView>(R.id.dict_definition)?.visibility = View.GONE
            view.findViewById<TextView>(R.id.dict_example)?.visibility = View.GONE
        }
    }
    
    /**
     * Hide the dictionary
     */
    private fun hideDictionary() {
        dictionaryContainer.visibility = View.GONE
        keyboardContainer.visibility = View.VISIBLE
        isDictionaryVisible = false
    }
    
    /**
     * Toggle dictionary visibility
     */
    private fun toggleDictionary() {
        if (isDictionaryVisible) {
            hideDictionary()
        } else {
            showDictionary()
        }
    }
    
    // ==================== AI CHAT FUNCTIONS ====================
    
    /**
     * Setup AI Chat UI
     */
    private fun setupAiChat(view: View) {
        aiChatContainer = view.findViewById(R.id.ai_chat_container)
        
        // Close button
        view.findViewById<ImageButton>(R.id.ai_chat_close_btn)?.setOnClickListener {
            hideAiChat()
        }
        
        // Clear chat button (long press on close to clear)
        view.findViewById<ImageButton>(R.id.ai_chat_close_btn)?.setOnLongClickListener {
            clearAiConversation()
            Toast.makeText(this, "Chat cleared", Toast.LENGTH_SHORT).show()
            true
        }
        
        // Clear input button
        view.findViewById<ImageButton>(R.id.ai_chat_clear_btn)?.setOnClickListener {
            aiChatInputText.clear()
            updateAiChatInputDisplay()
        }
        
        // Send button (in input bar)
        view.findViewById<ImageButton>(R.id.ai_chat_send_btn)?.setOnClickListener {
            sendAiChatMessage()
        }
        
        // Setup mini keyboard for AI chat
        setupAiChatMiniKeyboard(view)
        
        // Emoji button
        view.findViewById<Button>(R.id.ai_chat_key_emoji)?.setOnClickListener {
            hideAiChat()
            showEmojiPicker()
        }
        
        // Space button
        view.findViewById<Button>(R.id.ai_chat_key_space)?.setOnClickListener {
            aiChatInputText.append(" ")
            updateAiChatInputDisplay()
        }
        
        // Send button (keyboard)
        view.findViewById<Button>(R.id.ai_chat_key_send)?.setOnClickListener {
            sendAiChatMessage()
        }
        
        // Globe button for language switch
        view.findViewById<Button>(R.id.ai_chat_key_globe)?.setOnClickListener {
            showAiChatLanguageSelector()
        }
    }
    
    /**
     * AI Chat mini keyboard language
     */
    private var aiChatKeyboardLanguage = "en"
    
    /**
     * Show language selector for AI chat keyboard
     */
    private fun showAiChatLanguageSelector() {
        val availableLanguages = listOf(
            "en" to "English",
            "ar" to "العربية",
            "ru" to "Русский",
            "fr" to "Français",
            "de" to "Deutsch",
            "es" to "Español"
        )
        
        val popupView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1A1F2E"))
            setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())
        }
        
        val titleView = TextView(this).apply {
            text = "🌐 Keyboard Language"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 12.dpToPx())
        }
        popupView.addView(titleView)
        
        val languagesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        
        availableLanguages.forEach { (langCode, langName) ->
            val isSelected = langCode == aiChatKeyboardLanguage
            val langButton = TextView(this).apply {
                text = if (isSelected) "✓ $langName" else "   $langName"
                textSize = 15f
                setTextColor(if (isSelected) Color.parseColor("#A78BFA") else Color.WHITE)
                setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.dict_key_background)
                
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 4.dpToPx()
                }
            }
            languagesContainer.addView(langButton)
        }
        
        popupView.addView(languagesContainer)
        
        val popupWindow = android.widget.PopupWindow(
            popupView,
            220.dpToPx(),
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 10f
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            isFocusable = true
        }
        
        for (i in 0 until languagesContainer.childCount) {
            val button = languagesContainer.getChildAt(i)
            val (langCode, langName) = availableLanguages[i]
            button.setOnClickListener {
                aiChatKeyboardLanguage = langCode
                rebuildAiChatMiniKeyboard()
                Toast.makeText(this, "Keyboard: $langName", Toast.LENGTH_SHORT).show()
                popupWindow.dismiss()
            }
        }
        
        rootView?.let { view ->
            popupWindow.showAtLocation(view, Gravity.CENTER, 0, 0)
        }
    }
    
    /**
     * Rebuild AI chat mini keyboard with current language
     */
    private fun rebuildAiChatMiniKeyboard() {
        rootView?.let { view ->
            val row0 = view.findViewById<LinearLayout>(R.id.ai_chat_row0)
            val row1 = view.findViewById<LinearLayout>(R.id.ai_chat_row1)
            val row2 = view.findViewById<LinearLayout>(R.id.ai_chat_row2)
            val row3 = view.findViewById<LinearLayout>(R.id.ai_chat_row3)
            
            row0?.removeAllViews()
            row1?.removeAllViews()
            row2?.removeAllViews()
            row3?.removeAllViews()
            
            val numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
            val layout = dictMiniKeyboardLayouts[aiChatKeyboardLanguage] 
                ?: dictMiniKeyboardLayouts["en"]!!
            
            numbers.forEach { key ->
                row0?.addView(createAiChatKey(key))
            }
            
            layout.first.forEach { key ->
                row1?.addView(createAiChatKey(key))
            }
            
            layout.second.forEach { key ->
                row2?.addView(createAiChatKey(key))
            }
            
            layout.third.forEach { key ->
                row3?.addView(createAiChatKey(key))
            }
            
            row3?.addView(createAiChatSpecialKey("⌫") {
                if (aiChatInputText.isNotEmpty()) {
                    aiChatInputText.deleteCharAt(aiChatInputText.length - 1)
                    updateAiChatInputDisplay()
                }
            })
        }
    }
    
    /**
     * Setup mini keyboard for AI chat
     */
    private fun setupAiChatMiniKeyboard(view: View) {
        val row0 = view.findViewById<LinearLayout>(R.id.ai_chat_row0)
        val row1 = view.findViewById<LinearLayout>(R.id.ai_chat_row1)
        val row2 = view.findViewById<LinearLayout>(R.id.ai_chat_row2)
        val row3 = view.findViewById<LinearLayout>(R.id.ai_chat_row3)
        
        val numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        val keys1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
        val keys2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
        val keys3 = listOf("z", "x", "c", "v", "b", "n", "m")
        
        // Number row
        numbers.forEach { key ->
            row0?.addView(createAiChatKey(key))
        }
        
        // QWERTY row
        keys1.forEach { key ->
            row1?.addView(createAiChatKey(key))
        }
        
        // ASDF row
        keys2.forEach { key ->
            row2?.addView(createAiChatKey(key))
        }
        
        // ZXCV row + backspace
        keys3.forEach { key ->
            row3?.addView(createAiChatKey(key))
        }
        
        // Add backspace
        row3?.addView(createAiChatSpecialKey("⌫") {
            if (aiChatInputText.isNotEmpty()) {
                aiChatInputText.deleteCharAt(aiChatInputText.length - 1)
                updateAiChatInputDisplay()
            }
        })
    }
    
    /**
     * Create AI chat keyboard key
     */
    private fun createAiChatKey(letter: String): Button {
        return Button(this).apply {
            text = letter
            textSize = 18f
            setTextColor(Color.parseColor("#FFFFFF"))
            background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.dict_key_background)
            isAllCaps = false
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
            includeFontPadding = false
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            
            val params = LinearLayout.LayoutParams(0, 42.dpToPx()).apply {
                weight = 1f
                marginStart = 2.dpToPx()
                marginEnd = 2.dpToPx()
                topMargin = 2.dpToPx()
                bottomMargin = 2.dpToPx()
            }
            layoutParams = params
            
            setOnClickListener {
                aiChatInputText.append(letter)
                updateAiChatInputDisplay()
            }
        }
    }
    
    /**
     * Create AI chat special key
     */
    private fun createAiChatSpecialKey(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 18f
            setTextColor(Color.parseColor("#FFFFFF"))
            background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.dict_key_special)
            isAllCaps = false
            gravity = Gravity.CENTER
            
            val params = LinearLayout.LayoutParams(0, 42.dpToPx()).apply {
                weight = 1.3f
                marginStart = 2.dpToPx()
                marginEnd = 2.dpToPx()
                topMargin = 2.dpToPx()
                bottomMargin = 2.dpToPx()
            }
            layoutParams = params
            
            setOnClickListener { onClick() }
        }
    }
    
    /**
     * Update AI chat input display
     */
    private fun updateAiChatInputDisplay() {
        rootView?.let { view ->
            view.findViewById<TextView>(R.id.ai_chat_input_text)?.text = 
                if (aiChatInputText.isEmpty()) "" else aiChatInputText.toString()
            view.findViewById<ImageButton>(R.id.ai_chat_clear_btn)?.visibility = 
                if (aiChatInputText.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }
    
    /**
     * Send message to AI
     */
    private fun sendAiChatMessage() {
        val message = aiChatInputText.toString().trim()
        if (message.isEmpty()) {
            Toast.makeText(this, "Please type a message", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Add user message to chat
        addChatMessage(message, isUser = true)
        aiChatInputText.clear()
        updateAiChatInputDisplay()
        
        // Show loading
        addChatMessage("Thinking...", isUser = false, isLoading = true)
        
        // Call AI API
        serviceScope.launch {
            try {
                val response = callAiApi(message)
                withContext(Dispatchers.Main) {
                    // Remove loading message and add response
                    removeLoadingMessage()
                    addChatMessage(response, isUser = false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    removeLoadingMessage()
                    addChatMessage("Sorry, I couldn't process that. Please try again.", isUser = false)
                }
            }
        }
    }
    
    /**
     * Add message to chat UI
     */
    private fun addChatMessage(message: String, isUser: Boolean, isLoading: Boolean = false) {
        rootView?.let { view ->
            val messagesContainer = view.findViewById<LinearLayout>(R.id.ai_chat_messages)
            
            // Create message bubble
            val messageLayout = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 12.dpToPx()
                }
                orientation = LinearLayout.HORIZONTAL
                gravity = if (isUser) Gravity.END else Gravity.START
            }
            
            val bubbleLayout = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (isUser) marginStart = 48.dpToPx() else marginEnd = 48.dpToPx()
                }
                background = ContextCompat.getDrawable(
                    this@MainKeyboardService,
                    if (isUser) R.drawable.ai_chat_bubble_user else R.drawable.ai_chat_bubble_ai
                )
                setPadding(12.dpToPx(), 10.dpToPx(), 12.dpToPx(), 10.dpToPx())
                if (isLoading) tag = "loading"
            }
            
            val textView = TextView(this).apply {
                text = message
                setTextColor(Color.parseColor("#E5E7EB"))
                textSize = 14f
                setLineSpacing(0f, 1.3f)
            }
            
            bubbleLayout.addView(textView)
            messageLayout.addView(bubbleLayout)
            messagesContainer?.addView(messageLayout)
            
            // Scroll to bottom
            view.findViewById<ScrollView>(R.id.ai_chat_scroll)?.post {
                view.findViewById<ScrollView>(R.id.ai_chat_scroll)?.fullScroll(View.FOCUS_DOWN)
            }
        }
    }
    
    /**
     * Remove loading message
     */
    private fun removeLoadingMessage() {
        rootView?.let { view ->
            val messagesContainer = view.findViewById<LinearLayout>(R.id.ai_chat_messages)
            for (i in messagesContainer.childCount - 1 downTo 0) {
                val child = messagesContainer.getChildAt(i)
                if (child is LinearLayout) {
                    for (j in 0 until child.childCount) {
                        val bubble = child.getChildAt(j)
                        if (bubble?.tag == "loading") {
                            messagesContainer.removeViewAt(i)
                            return@let
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Call AI API (using OpenAI API for real responses like ChatGPT)
     */
    private suspend fun callAiApi(message: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // Add user message to conversation history
                aiConversationHistory.add(mapOf("role" to "user", "content" to message))
                
                // Keep only last 10 messages to avoid token limits
                if (aiConversationHistory.size > 10) {
                    aiConversationHistory = aiConversationHistory.takeLast(10).toMutableList()
                }
                
                // Try direct OpenAI API call only when API key is set (avoids 401)
                if (getOpenAiApiKey().isNotBlank()) {
                    val response = callOpenAiDirectly(message)
                    if (response != null) {
                        aiConversationHistory.add(mapOf("role" to "assistant", "content" to response))
                        return@withContext response
                    }
                }
                
                // Use Supabase edge function (no app API key needed)
                val edgeResponse = callOpenAiViaSupabase(message)
                if (edgeResponse != null) {
                    aiConversationHistory.add(mapOf("role" to "assistant", "content" to edgeResponse))
                    return@withContext edgeResponse
                }
                
                // Fallback to smart local responses if all API calls fail
                getSmartLocalResponse(message)
                
            } catch (e: Exception) {
                android.util.Log.e("DeltaVoice", "AI API error: ${e.message}")
                getSmartLocalResponse(message)
            }
        }
    }
    
    /**
     * Call OpenAI API directly for ChatGPT-like responses
     */
    private fun callOpenAiDirectly(message: String): String? {
        try {
            val url = java.net.URL("https://api.openai.com/v1/chat/completions")
            val connection = url.openConnection() as java.net.HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            // Note: For production, store API key securely
            connection.setRequestProperty("Authorization", "Bearer ${getOpenAiApiKey()}")
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            connection.doOutput = true
            
            // Build messages array with conversation history
            val messagesJson = StringBuilder("[")
            messagesJson.append("""{"role":"system","content":"You are a helpful, friendly AI assistant. Be concise but informative. Use emojis occasionally to be friendly. Respond in the same language the user writes in. If asked to write something, provide complete and helpful content."}""")
            
            aiConversationHistory.takeLast(8).forEach { msg ->
                val role = msg["role"] ?: "user"
                val content = (msg["content"] ?: "")
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "")
                    .replace("\t", " ")
                messagesJson.append(""",{"role":"$role","content":"$content"}""")
            }
            messagesJson.append("]")
            
            val requestBody = """{"model":"gpt-4o-mini","messages":$messagesJson,"max_tokens":1000,"temperature":0.7}"""
            
            android.util.Log.d("DeltaVoice", "Calling OpenAI API directly...")
            
            java.io.OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestBody)
                writer.flush()
            }
            
            val responseCode = connection.responseCode
            android.util.Log.d("DeltaVoice", "OpenAI response code: $responseCode")
            
            if (responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                android.util.Log.d("DeltaVoice", "OpenAI response received: ${responseText.take(200)}")
                
                // Parse the response to get the content
                val contentMatch = Regex(""""content"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(responseText)
                if (contentMatch != null) {
                    return contentMatch.groupValues[1]
                        .replace("\\n", "\n")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                }
            } else {
                val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                android.util.Log.e("DeltaVoice", "OpenAI API error: $responseCode - $errorText")
            }
            
            connection.disconnect()
            
        } catch (e: Exception) {
            android.util.Log.e("DeltaVoice", "OpenAI direct call failed: ${e.message}")
        }
        
        return null
    }
    
    /**
     * Get OpenAI API key from Supabase config or environment
     */
    private fun getOpenAiApiKey(): String {
        // For now, return empty - the edge function will handle the API key
        // In production, you'd store this securely
        return ""
    }
    
    /**
     * Call OpenAI via Supabase edge function
     */
    private fun callOpenAiViaSupabase(message: String): String? {
        try {
            val apiKey = com.deltavoice.config.SupabaseConfig.SUPABASE_ANON_KEY
            val supabaseUrl = com.deltavoice.config.SupabaseConfig.SUPABASE_URL
            
            val url = java.net.URL("$supabaseUrl/functions/v1/ai-chat")
            val connection = url.openConnection() as java.net.HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("apikey", apiKey)
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            connection.doOutput = true
            
            // Build messages array
            val messagesJson = StringBuilder("[")
            messagesJson.append("""{"role":"system","content":"You are a helpful, friendly AI assistant like ChatGPT. Be concise but informative. Use emojis occasionally. Respond in the same language the user writes in."}""")
            
            aiConversationHistory.takeLast(8).forEach { msg ->
                val role = msg["role"] ?: "user"
                val content = (msg["content"] ?: "")
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                messagesJson.append(""",{"role":"$role","content":"$content"}""")
            }
            messagesJson.append("]")
            
            val requestBody = """{"messages":$messagesJson}"""
            
            android.util.Log.d("DeltaVoice", "Calling Supabase AI chat...")
            
            java.io.OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestBody)
                writer.flush()
            }
            
            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                android.util.Log.d("DeltaVoice", "Supabase response: ${responseText.take(200)}")
                
                // Parse response - try multiple patterns
                val patterns = listOf(
                    Regex(""""content"\s*:\s*"((?:[^"\\]|\\.)*)""""),
                    Regex(""""response"\s*:\s*"((?:[^"\\]|\\.)*)""""),
                    Regex(""""message"\s*:\s*"((?:[^"\\]|\\.)*)""""),
                    Regex(""""text"\s*:\s*"((?:[^"\\]|\\.)*)"""")
                )
                
                for (pattern in patterns) {
                    val match = pattern.find(responseText)
                    if (match != null) {
                        return match.groupValues[1]
                            .replace("\\n", "\n")
                            .replace("\\\"", "\"")
                            .replace("\\\\", "\\")
                    }
                }
            }
            
            connection.disconnect()
            
        } catch (e: Exception) {
            android.util.Log.e("DeltaVoice", "Supabase AI call failed: ${e.message}")
            // User-friendly message when device can't reach the server (DNS/network)
            val msg = e.message ?: ""
            if (msg.contains("Unable to resolve host", ignoreCase = true) || msg.contains("No address", ignoreCase = true)) {
                Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(this@MainKeyboardService, "Can't reach server. Check Wi‑Fi or mobile data.", Toast.LENGTH_LONG).show()
                }
            }
        }
        
        return null
    }
    
    /**
     * Smart local responses when API is unavailable
     */
    private fun getSmartLocalResponse(message: String): String {
        val lowerMessage = message.lowercase()
        
        return when {
            // Greetings
            lowerMessage.matches(Regex(".*(hello|hi|hey|good morning|good evening|السلام|مرحبا|hola|bonjour).*")) ->
                "Hello! 👋 How can I help you today? I can assist with:\n• Writing messages\n• Answering questions\n• Translations\n• And more!"
            
            // Thanks
            lowerMessage.matches(Regex(".*(thank|thanks|شكر|gracias|merci).*")) ->
                "You're welcome! 😊 Is there anything else I can help you with?"
            
            // Help
            lowerMessage.contains("help") || lowerMessage.contains("مساعدة") ->
                "I'm here to help! 🤖\n\n📝 Writing: \"Write an email about...\"\n🌍 Translate: \"Translate [text] to [language]\"\n❓ Questions: Just ask anything!\n💡 Ideas: \"Give me ideas for...\"\n\nWhat would you like?"
            
            // Translation requests
            lowerMessage.matches(Regex(".*(translate|ترجم|traducir|traduire).*")) -> {
                if (lowerMessage.contains(" to ") || lowerMessage.contains(" إلى ")) {
                    "I'll help you translate! Please provide the text you want to translate."
                } else {
                    "I can translate text! Just say:\n\"Translate [your text] to [language]\"\n\nSupported languages: English, Arabic, Spanish, French, German, Chinese, and more!"
                }
            }
            
            // Writing requests
            lowerMessage.matches(Regex(".*(write|compose|draft|اكتب|كتابة|escribir|écrire).*")) ->
                "I'd be happy to help you write! ✍️\n\nPlease tell me:\n1. What type? (email, message, post)\n2. The topic or main points\n3. The tone (formal, casual, friendly)"
            
            // Questions about capabilities
            lowerMessage.matches(Regex(".*(what can you|what do you|ماذا يمكنك|que puedes).*")) ->
                "I'm your AI keyboard assistant! 🎯\n\nI can:\n• Answer questions\n• Help write messages & emails\n• Translate between languages\n• Explain concepts\n• Give suggestions & ideas\n• Have conversations\n\nJust type what you need!"
            
            // Math/calculations
            lowerMessage.matches(Regex(".*\\d+\\s*[+\\-*/]\\s*\\d+.*")) -> {
                try {
                    val result = evaluateSimpleMath(message)
                    "The answer is: $result 🧮"
                } catch (e: Exception) {
                    "I can help with calculations! Please write the math clearly, like \"5 + 3\" or \"10 * 2\""
                }
            }
            
            // Time/date
            lowerMessage.matches(Regex(".*(what time|what date|الوقت|التاريخ|que hora|quelle heure).*")) -> {
                val now = java.text.SimpleDateFormat("EEEE, MMMM d, yyyy 'at' h:mm a", java.util.Locale.getDefault()).format(java.util.Date())
                "📅 Current date & time:\n$now"
            }
            
            // Short messages
            message.length < 3 ->
                "Could you tell me a bit more? I'm here to help! 💬"
            
            // General conversation
            else ->
                "That's interesting! While I work best with an internet connection for complex questions, I can still help with basic tasks.\n\n💡 Try asking me to:\n• Write something\n• Translate text\n• Answer a question\n\nWhat would you like help with?"
        }
    }
    
    /**
     * Evaluate simple math expressions
     */
    private fun evaluateSimpleMath(expression: String): Double {
        val cleanExpr = expression.replace(Regex("[^0-9+\\-*/. ]"), "").trim()
        val parts = cleanExpr.split(Regex("\\s*([+\\-*/])\\s*"))
        val operators = Regex("[+\\-*/]").findAll(cleanExpr).map { it.value }.toList()
        
        if (parts.size != 2 || operators.size != 1) {
            throw IllegalArgumentException("Invalid expression")
        }
        
        val a = parts[0].toDouble()
        val b = parts[1].toDouble()
        
        return when (operators[0]) {
            "+" -> a + b
            "-" -> a - b
            "*" -> a * b
            "/" -> if (b != 0.0) a / b else throw ArithmeticException("Division by zero")
            else -> throw IllegalArgumentException("Unknown operator")
        }
    }
    
    /**
     * Clear AI conversation history
     */
    private fun clearAiConversation() {
        aiConversationHistory.clear()
        aiChatMessages.clear()
        
        // Clear chat UI
        rootView?.let { view ->
            view.findViewById<LinearLayout>(R.id.ai_chat_messages)?.removeAllViews()
        }
        
        // Add welcome message
        addChatMessage("Hello! 👋 I'm your AI assistant. How can I help you today?", isUser = false)
    }
    
    /**
     * Show AI Chat
     */
    private fun showAiChat() {
        // Keep main keyboard visible so user can type with full keyboard + predictions
        keyboardContainer.visibility = View.VISIBLE
        emojiPickerContainer.visibility = View.GONE
        isEmojiPickerVisible = false
        calculatorContainer.visibility = View.GONE
        isCalculatorVisible = false
        dictionaryContainer.visibility = View.GONE
        isDictionaryVisible = false
        aiFeaturesContainer.visibility = View.GONE
        voiceRecordingContainer.visibility = View.GONE
        voiceProcessingStep2Container.visibility = View.GONE
        aiChatContainer.visibility = View.VISIBLE
        isAiChatVisible = true
        
        // Hide AI chat mini keyboard - use main keyboard instead
        rootView?.let { v ->
            v.findViewById<View>(R.id.ai_chat_row0)?.visibility = View.GONE
            v.findViewById<View>(R.id.ai_chat_row1)?.visibility = View.GONE
            v.findViewById<View>(R.id.ai_chat_row2)?.visibility = View.GONE
            v.findViewById<View>(R.id.ai_chat_row3)?.visibility = View.GONE
            v.findViewById<View>(R.id.ai_chat_mini_keyboard_bottom_row)?.visibility = View.GONE
        }
        
        // Reset input
        aiChatInputText.clear()
        updateAiChatInputDisplay()
        
        // Show welcome message only once (persisted)
        val prefs = getSharedPreferences("deltavoice_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("ai_chat_intro_shown", false)) {
            if (aiChatMessages.isEmpty()) {
                addChatMessage("Hello! 👋 I'm your AI assistant.\n\nI can help you with:\n• Writing messages & emails\n• Answering questions\n• Translations\n• And much more!\n\nHow can I help you today?", isUser = false)
            }
            prefs.edit().putBoolean("ai_chat_intro_shown", true).apply()
        }
    }
    
    /**
     * Hide AI Chat
     */
    private fun hideAiChat() {
        aiChatContainer.visibility = View.GONE
        keyboardContainer.visibility = View.VISIBLE
        isAiChatVisible = false
    }
    
    /**
     * Toggle AI Chat visibility
     */
    private fun toggleAiChat() {
        if (isAiChatVisible) {
            hideAiChat()
        } else {
            showAiChat()
        }
    }
    
    // ==================== END AI CHAT FUNCTIONS ====================

    // ==================== VIDEO RECORDING FUNCTIONS ====================
    
    /**
     * Setup video recording UI components
     */
    private fun setupVideoRecording(view: View) {
        videoRecordingContainer = view.findViewById(R.id.video_recording_container)
        videoPreviewContainer = view.findViewById(R.id.video_preview_container)
        cameraPreview = view.findViewById(R.id.camera_preview)
        videoPlayer = view.findViewById(R.id.video_player)
        videoRecordingTimer = view.findViewById(R.id.video_recording_timer)
        videoSpinnerLanguage = view.findViewById(R.id.video_spinner_language)
        videoSpinnerVoice = view.findViewById(R.id.video_spinner_voice)
        
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        
        // Setup camera preview listener
        cameraPreview?.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                openCamera()
            }
            
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
            
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                return true
            }
            
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
        
        // Back button
        view.findViewById<ImageButton>(R.id.btn_video_back)?.setOnClickListener {
            hideVideoRecording()
        }
        
        // Record button
        view.findViewById<ImageButton>(R.id.btn_video_record)?.setOnClickListener {
            if (isVideoRecording) {
                stopVideoRecording()
            } else {
                startVideoRecording()
            }
        }
        
        // Switch camera button
        view.findViewById<ImageButton>(R.id.btn_switch_camera)?.setOnClickListener {
            switchCamera()
        }
        
        // Close preview button
        view.findViewById<ImageButton>(R.id.btn_video_close)?.setOnClickListener {
            hideVideoPreview()
        }
        
        // Play video button
        view.findViewById<ImageButton>(R.id.btn_play_video)?.setOnClickListener {
            playRecordedVideo()
        }
        
        // Process / Send button - handles both processing and sharing
        view.findViewById<Button>(R.id.btn_process_video)?.setOnClickListener {
            val btn = view.findViewById<Button>(R.id.btn_process_video)
            when {
                btn.text.toString().contains("Send", ignoreCase = true) -> shareProcessedVideoAudio()
                else -> processRecordedVideo()
            }
        }
        
        // Setup video spinners
        setupVideoSpinners()
    }
    
    /**
     * Setup language and voice spinners for video processing
     */
    private fun setupVideoSpinners() {
        val languageNames = languages.map { it.first }
        val languageAdapter = ArrayAdapter(this, R.layout.spinner_item_dark, languageNames)
        languageAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark)
        videoSpinnerLanguage?.adapter = languageAdapter
        
        val voiceNames = voiceStyles.map { it.first }
        val voiceAdapter = ArrayAdapter(this, R.layout.spinner_item_dark, voiceNames)
        voiceAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark)
        videoSpinnerVoice?.adapter = voiceAdapter
    }
    
    /**
     * Toggle video recording visibility
     */
    private fun toggleVideoRecording() {
        if (isVideoRecordingVisible) {
            hideVideoRecording()
        } else {
            showVideoRecording()
        }
    }
    
    /**
     * Show video recording UI
     */
    private fun showVideoRecording() {
        // Check camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Hide other components
        hideAllOverlays()
        
        // Show video recording container
        videoRecordingContainer?.visibility = View.VISIBLE
        keyboardContainer.visibility = View.GONE
        isVideoRecordingVisible = true
        
        // Start camera preview
        startBackgroundThread()
        if (cameraPreview?.isAvailable == true) {
            openCamera()
        }
    }
    
    /**
     * Hide video recording UI
     */
    private fun hideVideoRecording() {
        if (isVideoRecording) {
            stopVideoRecording()
        }
        closeCamera()
        stopBackgroundThread()
        
        videoRecordingContainer?.visibility = View.GONE
        keyboardContainer.visibility = View.VISIBLE
        isVideoRecordingVisible = false
        
        // Reset timer
        videoRecordingTimer?.visibility = View.GONE
        videoRecordingSeconds = 0
    }
    
    /**
     * Show video preview UI
     */
    private fun showVideoPreview() {
        hideVideoRecording()
        
        videoPreviewContainer?.visibility = View.VISIBLE
        keyboardContainer.visibility = View.GONE
        isVideoPreviewVisible = true
        
        // Setup video player
        videoFilePath?.let { path ->
            videoPlayer?.setVideoPath(path)
            videoPlayer?.setOnPreparedListener { mp ->
                val duration = mp.duration / 1000
                val minutes = duration / 60
                val seconds = duration % 60
                rootView?.findViewById<TextView>(R.id.video_duration_text)?.text = 
                    String.format("%d:%02d", minutes, seconds)
            }
        }
    }
    
    /**
     * Hide video preview UI
     */
    private fun hideVideoPreview() {
        videoPlayer?.stopPlayback()
        videoPreviewContainer?.visibility = View.GONE
        keyboardContainer.visibility = View.VISIBLE
        isVideoPreviewVisible = false
        processedVideoAudioFilePath?.let { path ->
            try { File(path).delete() } catch (_: Exception) { }
        }
        processedVideoAudioFilePath = null
        isVideoProcessedAudioReady = false
        resetVideoProcessButton()
    }
    
    /**
     * Play recorded video
     */
    private fun playRecordedVideo() {
        val playButton = rootView?.findViewById<ImageButton>(R.id.btn_play_video)
        
        if (videoPlayer?.isPlaying == true) {
            videoPlayer?.pause()
            playButton?.setImageResource(R.drawable.ic_play)
        } else {
            videoPlayer?.start()
            playButton?.setImageResource(R.drawable.ic_pause)
            
            // Reset button when video completes
            videoPlayer?.setOnCompletionListener {
                playButton?.setImageResource(R.drawable.ic_play)
            }
        }
    }
    
    /**
     * Start background thread for camera operations
     */
    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }
    
    /**
     * Stop background thread
     */
    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }
    
    /**
     * Open camera for preview
     */
    private fun openCamera() {
        try {
            // Get camera ID (front or back)
            cameraId = getCameraId(useFrontCamera)
            
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                cameraManager?.openCamera(cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        cameraDevice = camera
                        createCameraPreviewSession()
                    }
                    
                    override fun onDisconnected(camera: CameraDevice) {
                        camera.close()
                        cameraDevice = null
                    }
                    
                    override fun onError(camera: CameraDevice, error: Int) {
                        camera.close()
                        cameraDevice = null
                    }
                }, backgroundHandler)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to open camera", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Get camera ID based on front/back preference
     */
    private fun getCameraId(useFront: Boolean): String {
        val cameraIds = cameraManager?.cameraIdList ?: return "0"
        for (id in cameraIds) {
            val characteristics = cameraManager?.getCameraCharacteristics(id)
            val facing = characteristics?.get(CameraCharacteristics.LENS_FACING)
            if (useFront && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                return id
            } else if (!useFront && facing == CameraCharacteristics.LENS_FACING_BACK) {
                return id
            }
        }
        return cameraIds.firstOrNull() ?: "0"
    }
    
    /**
     * Create camera preview session
     */
    private fun createCameraPreviewSession() {
        try {
            val texture = cameraPreview?.surfaceTexture ?: return
            texture.setDefaultBufferSize(1920, 1080)
            val surface = Surface(texture)
            
            val captureRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder?.addTarget(surface)
            
            cameraDevice?.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    cameraCaptureSession = session
                    captureRequestBuilder?.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                    session.setRepeatingRequest(captureRequestBuilder!!.build(), null, backgroundHandler)
                }
                
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Toast.makeText(this@MainKeyboardService, "Camera configuration failed", Toast.LENGTH_SHORT).show()
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Close camera
     */
    private fun closeCamera() {
        cameraCaptureSession?.close()
        cameraCaptureSession = null
        cameraDevice?.close()
        cameraDevice = null
    }
    
    /**
     * Switch between front and back camera
     */
    private fun switchCamera() {
        closeCamera()
        useFrontCamera = !useFrontCamera
        openCamera()
    }
    
    /**
     * Start video recording
     */
    private fun startVideoRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Audio permission required", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            // Close preview session first
            cameraCaptureSession?.close()
            
            // Create video file
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val videoDir = File(filesDir, "videos")
            if (!videoDir.exists()) videoDir.mkdirs()
            val videoFile = File(videoDir, "VID_$timestamp.mp4")
            videoFilePath = videoFile.absolutePath
            
            // Setup media recorder
            videoMediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(videoFilePath)
                setVideoEncodingBitRate(10000000)
                setVideoFrameRate(30)
                setVideoSize(1920, 1080)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                
                // Handle orientation for front camera
                if (useFrontCamera) {
                    setOrientationHint(270)
                } else {
                    setOrientationHint(90)
                }
                
                prepare()
            }
            
            // Create capture session for recording
            val texture = cameraPreview?.surfaceTexture ?: return
            texture.setDefaultBufferSize(1920, 1080)
            val previewSurface = Surface(texture)
            val recorderSurface = videoMediaRecorder!!.surface
            
            val captureRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            captureRequestBuilder?.addTarget(previewSurface)
            captureRequestBuilder?.addTarget(recorderSurface)
            
            cameraDevice?.createCaptureSession(listOf(previewSurface, recorderSurface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    cameraCaptureSession = session
                    captureRequestBuilder?.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                    session.setRepeatingRequest(captureRequestBuilder!!.build(), null, backgroundHandler)
                    
                    // Start recording
                    videoMediaRecorder?.start()
                    isVideoRecording = true
                    
                    // Update UI
                    CoroutineScope(Dispatchers.Main).launch {
                        updateVideoRecordingUI(true)
                        startVideoTimer()
                    }
                }
                
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Toast.makeText(this@MainKeyboardService, "Recording configuration failed", Toast.LENGTH_SHORT).show()
                }
            }, backgroundHandler)
            
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to start recording: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Stop video recording
     */
    private fun stopVideoRecording() {
        try {
            stopVideoTimer()
            isVideoRecording = false
            
            videoMediaRecorder?.apply {
                stop()
                reset()
                release()
            }
            videoMediaRecorder = null
            
            updateVideoRecordingUI(false)
            
            // Show preview
            showVideoPreview()
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Update video recording UI
     */
    private fun updateVideoRecordingUI(recording: Boolean) {
        val statusText = rootView?.findViewById<TextView>(R.id.video_status_text)
        val recordButton = rootView?.findViewById<ImageButton>(R.id.btn_video_record)
        
        if (recording) {
            statusText?.text = "Recording... Tap to stop"
            statusText?.setTextColor(Color.parseColor("#FF5252"))
            recordButton?.setBackgroundResource(R.drawable.voice_mode_button_green)
            videoRecordingTimer?.visibility = View.VISIBLE
        } else {
            statusText?.text = "Tap to start recording"
            statusText?.setTextColor(Color.parseColor("#AAAAAA"))
            recordButton?.setBackgroundResource(R.drawable.recording_mic_button)
            videoRecordingTimer?.visibility = View.GONE
        }
    }
    
    /**
     * Start video timer
     */
    private fun startVideoTimer() {
        videoRecordingSeconds = 0
        videoTimerHandler = Handler(mainLooper)
        videoTimerRunnable = object : Runnable {
            override fun run() {
                videoRecordingSeconds++
                val minutes = videoRecordingSeconds / 60
                val seconds = videoRecordingSeconds % 60
                videoRecordingTimer?.text = String.format("%02d:%02d", minutes, seconds)
                videoTimerHandler?.postDelayed(this, 1000)
            }
        }
        videoTimerHandler?.post(videoTimerRunnable!!)
    }
    
    /**
     * Stop video timer
     */
    private fun stopVideoTimer() {
        videoTimerRunnable?.let { videoTimerHandler?.removeCallbacks(it) }
        videoTimerHandler = null
        videoTimerRunnable = null
    }
    
    /**
     * Process recorded video with selected language and voice
     */
    private fun processRecordedVideo() {
        // Check network first
        if (!checkNetworkAndNotify()) {
            android.util.Log.e("DeltaVoice", "Video processing: No internet connection!")
            return
        }
        
        val selectedLanguageIndex = videoSpinnerLanguage?.selectedItemPosition ?: 0
        val selectedVoiceIndex = videoSpinnerVoice?.selectedItemPosition ?: 0
        
        val targetLanguage = languages[selectedLanguageIndex].second
        val voiceStyle = voiceStyles[selectedVoiceIndex].second
        val languageName = languages[selectedLanguageIndex].first
        val voiceStyleName = voiceStyles[selectedVoiceIndex].first
        
        // Get the recorded video file path
        val videoPath = videoFilePath
        if (videoPath.isNullOrBlank()) {
            Toast.makeText(this, "❌ No video recorded. Please record first.", Toast.LENGTH_LONG).show()
            return
        }
        
        val videoFile = File(videoPath)
        if (!videoFile.exists() || videoFile.length() == 0L) {
            Toast.makeText(this, "❌ Video file not found. Please record again.", Toast.LENGTH_LONG).show()
            return
        }
        
        android.util.Log.d("DeltaVoice", "Processing video: $videoPath, size: ${videoFile.length()} bytes")
        android.util.Log.d("DeltaVoice", "Target: $languageName ($targetLanguage), Voice: $voiceStyleName ($voiceStyle)")
        
        Toast.makeText(this, "⏳ Processing video to $languageName with $voiceStyleName voice...", Toast.LENGTH_LONG).show()
        
        serviceScope.launch {
            try {
                // Update UI for processing state
                withContext(Dispatchers.Main) {
                    rootView?.findViewById<Button>(R.id.btn_process_video)?.apply {
                        isEnabled = false
                        text = "⏳ Processing..."
                    }
                }
                
                // Step 1: Extract audio from video
                android.util.Log.d("DeltaVoice", "Step 1: Extracting audio from video...")
                val audioFile = extractAudioFromVideo(videoFile)
                
                if (audioFile == null || !audioFile.exists()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainKeyboardService, "❌ Failed to extract audio from video", Toast.LENGTH_LONG).show()
                        resetVideoProcessButton()
                    }
                    return@launch
                }
                
                android.util.Log.d("DeltaVoice", "Audio extracted: ${audioFile.absolutePath}, size: ${audioFile.length()} bytes")
                
                // Step 2: Process audio through the voice workflow
                android.util.Log.d("DeltaVoice", "Step 2: Processing audio through workflow...")
                
                val result = completeVoiceWorkflowService.runWorkflow(
                    audioFile = audioFile,
                    targetLanguage = targetLanguage,
                    voiceStyle = voiceStyle,
                    workflowType = "complete"
                )
                
                if (result.isSuccess) {
                    val response = result.getOrNull()!!
                    android.util.Log.d("DeltaVoice", "Video workflow success!")
                    android.util.Log.d("DeltaVoice", "Original: ${response.originalText?.take(50)}")
                    android.util.Log.d("DeltaVoice", "Translated: ${response.translatedText?.take(50)}")
                    android.util.Log.d("DeltaVoice", "Has audio: ${!response.convertedAudioBase64.isNullOrBlank()}")
                    
                    audioFile.delete()
                    
                    if (!response.convertedAudioBase64.isNullOrBlank()) {
                        val savedPath = withContext(Dispatchers.IO) {
                            val fileName = "processed_video_${languageName.replace(" ", "_")}_${System.currentTimeMillis()}.mp3"
                            val file = File(cacheDir, fileName)
                            file.writeBytes(android.util.Base64.decode(response.convertedAudioBase64, android.util.Base64.NO_WRAP))
                            file.absolutePath
                        }
                        processedVideoAudioFilePath = savedPath
                        isVideoProcessedAudioReady = true
                        withContext(Dispatchers.Main) {
                            response.translatedText?.takeIf { it.isNotBlank() }?.let { insertText(it) }
                            playBase64Audio(response.convertedAudioBase64, "mp3")
                            Toast.makeText(this@MainKeyboardService, "✓ Ready! Tap ▶ to hear, Send to share audio.", Toast.LENGTH_LONG).show()
                            rootView?.findViewById<Button>(R.id.btn_process_video)?.apply {
                                isEnabled = true
                                text = "  Send"
                                setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_send, 0, 0, 0)
                                background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.voice_mode_button_green)
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            response.translatedText?.takeIf { it.isNotBlank() }?.let { insertText(it) }
                            Toast.makeText(this@MainKeyboardService, "✓ Video transcribed & translated!", Toast.LENGTH_SHORT).show()
                            resetVideoProcessButton()
                            hideVideoPreview()
                        }
                    }
                } else {
                    val error = result.exceptionOrNull()
                    android.util.Log.e("DeltaVoice", "Video workflow failed: ${error?.message}")
                    withContext(Dispatchers.Main) {
                        handleVideoProcessingError(error?.message ?: "Unknown error")
                        resetVideoProcessButton()
                    }
                    audioFile.delete()
                }
                
            } catch (e: Exception) {
                android.util.Log.e("DeltaVoice", "Video processing exception: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    handleVideoProcessingError(e.message ?: "Processing failed")
                    resetVideoProcessButton()
                }
            }
        }
    }
    
    /**
     * Extract audio track from video file
     */
    private suspend fun extractAudioFromVideo(videoFile: File): File? = withContext(Dispatchers.IO) {
        try {
            val audioFile = File(cacheDir, "extracted_audio_${System.currentTimeMillis()}.m4a")
            
            // Use MediaExtractor to extract audio
            val extractor = android.media.MediaExtractor()
            extractor.setDataSource(videoFile.absolutePath)
            
            // Find audio track
            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    break
                }
            }
            
            if (audioTrackIndex == -1) {
                android.util.Log.e("DeltaVoice", "No audio track found in video")
                extractor.release()
                return@withContext null
            }
            
            extractor.selectTrack(audioTrackIndex)
            val format = extractor.getTrackFormat(audioTrackIndex)
            
            // Create MediaMuxer to write audio
            val muxer = android.media.MediaMuxer(audioFile.absolutePath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val audioMuxerTrack = muxer.addTrack(format)
            muxer.start()
            
            // Buffer for reading samples
            val bufferSize = 1024 * 1024
            val buffer = java.nio.ByteBuffer.allocate(bufferSize)
            val bufferInfo = android.media.MediaCodec.BufferInfo()
            
            // Copy audio data
            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = extractor.sampleFlags
                
                muxer.writeSampleData(audioMuxerTrack, buffer, bufferInfo)
                extractor.advance()
            }
            
            muxer.stop()
            muxer.release()
            extractor.release()
            
            android.util.Log.d("DeltaVoice", "Audio extracted successfully: ${audioFile.length()} bytes")
            audioFile
            
        } catch (e: Exception) {
            android.util.Log.e("DeltaVoice", "Audio extraction failed: ${e.message}", e)
            null
        }
    }
    
    /**
     * Handle video processing errors
     */
    private fun handleVideoProcessingError(errorMessage: String) {
        val isNetworkError = errorMessage.contains("internet", ignoreCase = true) ||
            errorMessage.contains("network", ignoreCase = true) ||
            errorMessage.contains("connection", ignoreCase = true) ||
            errorMessage.contains("timeout", ignoreCase = true)
        
        if (isNetworkError) {
            showNetworkErrorWithSettings("Video processing failed. Check internet connection.")
        } else {
            Toast.makeText(this, "❌ Video processing failed: ${errorMessage.take(50)}", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * Reset video process button state
     */
    private fun resetVideoProcessButton() {
        rootView?.findViewById<Button>(R.id.btn_process_video)?.apply {
            isEnabled = true
            text = "  Process Video"
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_ai_mode, 0, 0, 0)
            background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.voice_mode_button_purple)
        }
    }
    
    /**
     * Share the processed video audio to Messenger, WhatsApp, Telegram, etc.
     */
    private fun shareProcessedVideoAudio() {
        val audioPath = processedVideoAudioFilePath
        if (audioPath.isNullOrBlank() || !isVideoProcessedAudioReady) {
            Toast.makeText(this, "No processed audio to send", Toast.LENGTH_SHORT).show()
            return
        }
        shareAudioFile(File(audioPath), "Send translated video audio via") {
            processedVideoAudioFilePath = null
            isVideoProcessedAudioReady = false
            resetVideoProcessButton()
            hideVideoPreview()
        }
    }
    
    /**
     * Hide all overlay components
     */
    private fun hideAllOverlays() {
        emojiPickerContainer.visibility = View.GONE
        isEmojiPickerVisible = false
        
        calculatorContainer.visibility = View.GONE
        isCalculatorVisible = false
        
        dictionaryContainer.visibility = View.GONE
        isDictionaryVisible = false
        
        aiChatContainer.visibility = View.GONE
        isAiChatVisible = false
        
        aiWritingToolsContainer.visibility = View.GONE
        
        voiceRecordingContainer.visibility = View.GONE
        voiceProcessingStep2Container.visibility = View.GONE
        
        videoRecordingContainer?.visibility = View.GONE
        isVideoRecordingVisible = false
        
        videoPreviewContainer?.visibility = View.GONE
        isVideoPreviewVisible = false
    }
    
    // ==================== END VIDEO RECORDING FUNCTIONS ====================

    /**
     * Open the app's main homepage
     */
    private fun openAppHomepage() {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to open homepage", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Insert emoji into the current input field
     */
    private fun insertEmoji(emoji: String) {
        val inputConnection: InputConnection? = currentInputConnection
        inputConnection?.commitText(emoji, 1)
    }

    /**
     * Setup keyboard with all keys
     */
    private fun setupKeyboard(view: View) {
        val rowNumbers = view.findViewById<LinearLayout>(R.id.row_numbers)
        val rowQwerty = view.findViewById<LinearLayout>(R.id.row_qwerty)
        val rowAsdf = view.findViewById<LinearLayout>(R.id.row_asdf)
        val rowZxcv = view.findViewById<LinearLayout>(R.id.row_zxcv)
        val rowBottom = view.findViewById<LinearLayout>(R.id.row_bottom)

        // Get current language layout (fallback to English)
        val layout = keyboardLayouts[currentKeyboardLanguage] ?: keyboardLayouts["en"]!!

        // Numbers row
        layout.numbers.forEach { key ->
            rowNumbers.addView(createKeyButton(key, key, weight = 1f))
        }

        // First letter row
        layout.row1.forEach { key ->
            rowQwerty.addView(createKeyButton(key, key, weight = 1f))
        }

        // Second letter row
        layout.row2.forEach { key ->
            rowAsdf.addView(createKeyButton(key, key, weight = 1f))
        }

        // Third letter row: Shift + keys + Backspace
        shiftButton = createSpecialKeyButton("⇧", "SHIFT", weight = 1.5f)
        rowZxcv.addView(shiftButton)

        layout.row3.forEach { key ->
            rowZxcv.addView(createKeyButton(key, key, weight = 1f))
        }

        val backspaceButton = createSpecialKeyButton("⌫", "BACKSPACE", weight = 1.5f)
        rowZxcv.addView(backspaceButton)

        // Bottom row: !#1 Globe Emoji Space Period Search
        numbersButton = createSpecialKeyButton("!#1", "NUMBERS", weight = 1.2f)
        rowBottom.addView(numbersButton)

        val languageButton = createSpecialKeyButton("🌐", "LANGUAGE", weight = 1f)
        rowBottom.addView(languageButton)

        val emojiButton = createSpecialKeyButton("😊", "EMOJI", weight = 1f)
        rowBottom.addView(emojiButton)

        // Space bar with language name
        spaceBarButton = createKeyButton(currentKeyboardLanguageName, " ", weight = 4.2f)
        rowBottom.addView(spaceBarButton)

        val periodButton = createKeyButton(".", ".", weight = 1f)
        rowBottom.addView(periodButton)

        val searchButton = createSpecialKeyButton("🔍", "SEARCH", weight = 1.2f)
        rowBottom.addView(searchButton)
    }

    /**
     * Create a key button with dark theme styling
     */
    private fun createKeyButton(label: String, value: String, weight: Float = 1f): Button {
        val button = Button(this).apply {
            text = if (label.length == 1 && label[0].isLetter()) label.lowercase() else label
            textSize = if (label.length > 1) 12f else 16f
            setTextColor(Color.parseColor("#F2F2F2"))
            gravity = Gravity.CENTER
            isAllCaps = false
            setIncludeFontPadding(true)
            setPadding(10, 12, 10, 12)
            minHeight = getKeyHeightDp().dpToPx()
            background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.key_dark_background)
            
            val layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                this.weight = weight
                marginStart = 3.dpToPx()
                marginEnd = 3.dpToPx()
                topMargin = 3.dpToPx()
                bottomMargin = 3.dpToPx()
            }
            this.layoutParams = layoutParams
            
            // Prevent focus
            isFocusable = false
            isFocusableInTouchMode = false

            setOnClickListener {
                playKeyFeedback(it)
                handleKeyPress(value)
            }
        }
        return button
    }
    
    /**
     * Create a key button with number above letter (for QWERTY row)
     */
    private fun createKeyButtonWithNumber(number: String, letter: String): Button {
        val button = Button(this).apply {
            text = "$number\n${letter.lowercase()}"
            textSize = 12f
            setTextColor(Color.parseColor("#333333"))
            gravity = Gravity.CENTER
            isAllCaps = false
            setIncludeFontPadding(true)
            setLineSpacing(0f, 1.12f)
            setPadding(12, 10, 12, 16)
            minHeight = (getKeyHeightDp() + 4).dpToPx()
            background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.key_dark_background)
            
            val layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                weight = 1f
                marginStart = 3.dpToPx()
                marginEnd = 3.dpToPx()
                topMargin = 3.dpToPx()
                bottomMargin = 3.dpToPx()
            }
            this.layoutParams = layoutParams
            
            // Prevent focus
            isFocusable = false
            isFocusableInTouchMode = false

            setOnClickListener {
                playKeyFeedback(it)
                val key = if (isShiftPressed) letter else letter.lowercase()
                handleKeyPress(key)
            }
        }
        return button
    }
    
    /**
     * Create a special function key button (blue background for special keys)
     */
    private fun createSpecialKeyButton(label: String, value: String, weight: Float = 1f): Button {
        val button = Button(this).apply {
            text = label
            textSize = 14f
            setTextColor(Color.parseColor("#F2F2F2"))
            gravity = Gravity.CENTER
            isAllCaps = false
            setIncludeFontPadding(true)
            setPadding(10, 12, 10, 12)
            minHeight = getKeyHeightDp().dpToPx()
            background = ContextCompat.getDrawable(
                this@MainKeyboardService,
                R.drawable.key_function_background
            )
            
            val layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                this.weight = weight
                marginStart = 3.dpToPx()
                marginEnd = 3.dpToPx()
                topMargin = 3.dpToPx()
                bottomMargin = 3.dpToPx()
            }
            this.layoutParams = layoutParams
            
            // Prevent focus
            isFocusable = false
            isFocusableInTouchMode = false

            setOnClickListener {
                playKeyFeedback(it)
                handleKeyPress(value)
            }
        }
        return button
    }

    /**
     * Play sound and haptic feedback for key press based on user settings
     */
    private fun playKeyFeedback(sourceView: android.view.View) {
        val prefs = getSharedPreferences("deltavoice_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("sound_enabled", false)) {
            sourceView.isSoundEffectsEnabled = true
            sourceView.playSoundEffect(SoundEffectConstants.CLICK)
        }
        if (prefs.getBoolean("vibration_enabled", true)) {
            sourceView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    /**
     * Handle key press
     */
    private fun handleKeyPress(value: String) {
        // When AI chat is visible, route input to AI chat instead of input connection
        if (isAiChatVisible) {
            when (value) {
                "BACKSPACE" -> {
                    if (aiChatInputText.isNotEmpty()) {
                        aiChatInputText.deleteCharAt(aiChatInputText.length - 1)
                        updateAiChatInputDisplay()
                    }
                    schedulePredictionUpdate()
                }
                "SHIFT" -> toggleShift()
                "NUMBERS" -> toggleNumbersSymbols()
                "EMOJI" -> { hideAiChat(); showEmojiPicker() }
                "LANGUAGE" -> showLanguageSelector()
                "TOGGLE_SYMBOLS" -> toggleSymbolsMode()
                "ENTER", "\n" -> sendAiChatMessage()
                "SEARCH" -> {
                    val text = aiChatInputText.toString().trim()
                    if (text.isNotEmpty()) {
                        try {
                            val encodedQuery = java.net.URLEncoder.encode(text, "UTF-8")
                            val searchUrl = "https://www.google.com/search?q=$encodedQuery"
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(searchUrl))
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                        } catch (_: Exception) {}
                    } else {
                        Toast.makeText(this, "Type something to search", Toast.LENGTH_SHORT).show()
                    }
                }
                " " -> {
                    aiChatInputText.append(" ")
                    updateAiChatInputDisplay()
                    schedulePredictionUpdate()
                }
                else -> {
                    val charToInsert = if (value.length == 1 && value[0].isLetter()) {
                        if (isShiftPressed) value.uppercase() else value.lowercase()
                    } else value
                    aiChatInputText.append(charToInsert)
                    updateAiChatInputDisplay()
                    if (isShiftPressed && value.length == 1 && value[0].isLetter()) {
                        isShiftPressed = false
                        updateShiftButton()
                    }
                    if (value.length == 1 && value[0].isLetter()) schedulePredictionUpdate()
                }
            }
            return
        }
        
        val inputConnection: InputConnection? = currentInputConnection
        when (value) {
            "BACKSPACE" -> {
                inputConnection?.deleteSurroundingText(1, 0)
                schedulePredictionUpdate()
            }
            "SHIFT" -> toggleShift()
            "NUMBERS" -> toggleNumbersSymbols()
            "EMOJI" -> toggleEmojiPicker()
            "LANGUAGE" -> showLanguageSelector()
            "TOGGLE_SYMBOLS" -> toggleSymbolsMode()
            "ENTER" -> inputConnection?.commitText("\n", 1)
            "SEARCH" -> performSearch(inputConnection)
            "\n" -> inputConnection?.commitText("\n", 1)
            " " -> {
                inputConnection?.commitText(" ", 1)
                schedulePredictionUpdate()
            }
            else -> {
                val charToInsert = if (value.length == 1 && value[0].isLetter()) {
                    if (isShiftPressed) value.uppercase() else value.lowercase()
                } else value
                inputConnection?.commitText(charToInsert, 1)
                if (isShiftPressed && value.length == 1 && value[0].isLetter()) {
                    isShiftPressed = false
                    updateShiftButton()
                }
                if (value.length == 1 && value[0].isLetter()) schedulePredictionUpdate()
            }
        }
    }
    
    /**
     * Schedule a delayed prediction update (debounced)
     */
    private fun schedulePredictionUpdate() {
        if (!getSharedPreferences("deltavoice_prefs", MODE_PRIVATE).getBoolean("predictive_text", true)) return
        if (isNumbersMode) return
        predictionRunnable?.let { predictionHandler.removeCallbacks(it) }
        predictionRunnable = Runnable { updatePredictions() }
        predictionHandler.postDelayed(predictionRunnable!!, PREDICTION_DELAY_MS)
    }
    
    /**
     * Update predictive text suggestions based on current input
     */
    private fun updatePredictions() {
        rootView ?: return
        val keyboardContainer = rootView!!.findViewById<View>(R.id.keyboard_container)
        // Allow predictions when keyboard is visible OR when AI chat is visible (keyboard shown for AI input)
        if (keyboardContainer?.visibility != View.VISIBLE && !isAiChatVisible) return
        val aiRow = rootView!!.findViewById<View>(R.id.ai_features_row)
        val predictionsContainer = rootView!!.findViewById<View>(R.id.predictions_container)
        val predictionsRow = rootView!!.findViewById<LinearLayout>(R.id.predictions_row)
        if (aiRow == null || predictionsContainer == null || predictionsRow == null) return
        
        val prefs = getSharedPreferences("deltavoice_prefs", MODE_PRIVATE)
        val autoCorrection = prefs.getBoolean("auto_correction", true)
        
        val textBefore = if (isAiChatVisible) {
            aiChatInputText.toString()
        } else {
            currentInputConnection?.getTextBeforeCursor(100, 0)?.toString() ?: run {
                showIconsHidePredictions(aiRow, predictionsContainer)
                return
            }
        }
        val currentWord = textBefore.takeLastWhile { c -> c.isLetter() || c == '\'' }
        
        val suggestions = mutableListOf<String>()
        if (currentWord.isNotEmpty()) {
            val lang = currentKeyboardLanguage
            suggestions.addAll(PredictiveWordList.getPredictions(currentWord, 5, lang))
            if (autoCorrection && suggestions.isEmpty()) {
                suggestions.addAll(PredictiveWordList.getCorrections(currentWord, 5, lang))
            }
        }
        
        if (suggestions.isNotEmpty()) {
            showPredictionsHideIcons(aiRow, predictionsContainer, predictionsRow, suggestions)
        } else {
            showIconsHidePredictions(aiRow, predictionsContainer)
        }
    }
    
    private fun showPredictionsHideIcons(aiRow: View, predictionsContainer: View, predictionsRow: LinearLayout, suggestions: List<String>) {
        aiRow.visibility = View.GONE
        predictionsContainer.visibility = View.VISIBLE
        predictionsRow.removeAllViews()
        
        suggestions.forEach { word ->
            val chip = Button(this).apply {
                text = word
                textSize = 14f
                setTextColor(Color.parseColor("#EDEFF4"))
                setPadding(16.dpToPx(), 8.dpToPx(), 16.dpToPx(), 8.dpToPx())
                background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.key_dark_background)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 6.dpToPx()
                }
                setOnClickListener {
                    playKeyFeedback(it)
                    insertPrediction(word)
                    showIconsHidePredictions(aiRow, predictionsContainer)
                }
            }
            predictionsRow.addView(chip)
        }
    }
    
    private fun showIconsHidePredictions(aiRow: View, predictionsContainer: View) {
        aiRow.visibility = View.VISIBLE
        predictionsContainer.visibility = View.GONE
    }
    
    /**
     * Insert a prediction word, replacing the current partial word
     */
    private fun insertPrediction(word: String) {
        val wordChars: (Char) -> Boolean = { c -> c.isLetter() || c == '\'' }
        if (isAiChatVisible) {
            val textBefore = aiChatInputText.toString()
            val wordLen = textBefore.takeLastWhile(wordChars).length
            if (wordLen > 0) {
                aiChatInputText.delete(aiChatInputText.length - wordLen, aiChatInputText.length)
            }
            aiChatInputText.append("$word ")
            updateAiChatInputDisplay()
            return
        }
        val ic = currentInputConnection ?: return
        val textBefore = ic.getTextBeforeCursor(100, 0)?.toString() ?: ""
        val wordLen = textBefore.takeLastWhile(wordChars).length
        if (wordLen > 0) {
            ic.deleteSurroundingText(wordLen, 0)
        }
        ic.commitText("$word ", 1)
    }

    /**
     * Perform web search with current text
     */
    private fun performSearch(inputConnection: InputConnection?) {
        // Try to get text from input field
        val extractedText = inputConnection?.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
        val searchText = extractedText?.text?.toString()?.trim()
        
        if (searchText.isNullOrBlank()) {
            Toast.makeText(this, "Type something to search", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            // Create Google search URL
            val encodedQuery = java.net.URLEncoder.encode(searchText, "UTF-8")
            val searchUrl = "https://www.google.com/search?q=$encodedQuery"
            
            // Open in browser
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(searchUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            
            Toast.makeText(this, "Searching: $searchText", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open search", Toast.LENGTH_SHORT).show()
            android.util.Log.e("DeltaVoice", "Search error: ${e.message}")
        }
    }
    
    /**
     * Toggle shift state
     */
    private fun toggleShift() {
        isShiftPressed = !isShiftPressed
        updateShiftButton()
    }

    /**
     * Update shift button appearance
     */
    private fun updateShiftButton() {
        shiftButton?.let { button ->
            if (isShiftPressed) {
                button.setBackgroundColor(Color.parseColor("#5A5A5A"))
            } else {
                button.background = ContextCompat.getDrawable(this, R.drawable.key_function_background)
            }
        }
    }

    /**
     * Initialize Speech Recognizer for voice-to-text
     */
    private fun initializeSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {
                    isListening = true
                    voiceButton.setImageResource(R.drawable.ic_mic_active)
                }

                override fun onBeginningOfSpeech() {
                    // Speech started
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // Audio level changed
                }

                override fun onBufferReceived(buffer: ByteArray?) {
                    // Audio buffer received
                }

                override fun onEndOfSpeech() {
                    isListening = false
                    voiceButton.setImageResource(R.drawable.ic_mic)
                }

                override fun onError(error: Int) {
                    isListening = false
                    voiceButton.setImageResource(R.drawable.ic_mic)
                    val errorMessage = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissions error"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                        else -> "Unknown error"
                    }
                    Toast.makeText(this@MainKeyboardService, errorMessage, Toast.LENGTH_SHORT).show()
                }

                override fun onResults(results: android.os.Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (matches != null && matches.isNotEmpty()) {
                        val spokenText = matches[0]
                        insertText(spokenText)
                    }
                    isListening = false
                    voiceButton.setImageResource(R.drawable.ic_mic)
                }

                override fun onPartialResults(partialResults: android.os.Bundle?) {
                    // Partial results received
                }

                override fun onEvent(eventType: Int, params: android.os.Bundle?) {
                    // Event occurred
                }
            })
        }
    }

    /**
     * Toggle voice input on/off
     */
    private fun toggleVoiceInput() {
        // Stop recording if active
        if (isRecording) {
            stopVoiceRecording()
        }
        
        if (!isListening) {
            startVoiceInput()
        } else {
            stopVoiceInput()
        }
    }

    /**
     * Start voice input recognition
     */
    private fun startVoiceInput() {
        // Don't start if recording
        if (isRecording) {
            Toast.makeText(this, "Stop recording first", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (speechRecognizer == null) {
            initializeSpeechRecognizer()
        }
        
        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // Use language tag format for speech recognition
            val languageTag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                selectedLanguage.toLanguageTag()
            } else {
                selectedLanguage.toString()
            }
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
        }
        
        speechRecognizer?.startListening(intent)
    }

    /**
     * Stop voice input recognition
     */
    private fun stopVoiceInput() {
        speechRecognizer?.stopListening()
        isListening = false
        if (!isRecording) {
            voiceButton.setImageResource(R.drawable.ic_mic)
        }
    }

    /**
     * Insert text into the current input field
     */
    private fun insertText(text: String) {
        val inputConnection: InputConnection? = currentInputConnection
        inputConnection?.commitText(text, 1)
    }

    /**
     * Text-to-Speech initialization callback
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(selectedLanguage)
            ttsInitialized = if (result == TextToSpeech.LANG_MISSING_DATA || 
                                result == TextToSpeech.LANG_NOT_SUPPORTED) {
                false
            } else {
                true
            }
        } else {
            ttsInitialized = false
        }
    }

    /**
     * Speak the current text in the input field using Supabase TTS
     */
    private fun speakCurrentText() {
        val inputConnection: InputConnection? = currentInputConnection
        val textBeforeCursor = inputConnection?.getTextBeforeCursor(1000, 0)
        val textAfterCursor = inputConnection?.getTextAfterCursor(1000, 0)
        
        val fullText = "${textBeforeCursor ?: ""}${textAfterCursor ?: ""}"
        
        if (TextUtils.isEmpty(fullText)) {
            Toast.makeText(this, "No text to speak", Toast.LENGTH_SHORT).show()
            return
        }
        
        Toast.makeText(this, "Generating speech...", Toast.LENGTH_SHORT).show()
        
        serviceScope.launch {
            try {
                // Try Supabase TTS first
                val result = textToSpeechService.synthesizeSpeech(
                    text = fullText.trim(),
                    language = selectedLanguage.language
                )
                
                result.onSuccess { audioFile ->
                    // Play audio file using MediaPlayer
                    try {
                        val mediaPlayer = android.media.MediaPlayer()
                        mediaPlayer.setDataSource(audioFile.absolutePath)
                        mediaPlayer.prepare()
                        mediaPlayer.setOnCompletionListener {
                            it.release()
                            // Clean up temp file
                            audioFile.delete()
                        }
                        mediaPlayer.start()
                        Toast.makeText(this@MainKeyboardService, "Playing speech", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this@MainKeyboardService, 
                            "Error playing audio: ${e.message}", Toast.LENGTH_LONG).show()
                        // Fallback to Android TTS
                        fallbackToAndroidTTS(fullText)
                    }
                }.onFailure { error ->
                    // Fallback to Android TTS if Supabase fails
                    Toast.makeText(this@MainKeyboardService, 
                        "Supabase TTS failed, using fallback: ${error.message}", 
                        Toast.LENGTH_SHORT).show()
                    fallbackToAndroidTTS(fullText)
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainKeyboardService, 
                    "Error: ${e.message}", Toast.LENGTH_LONG).show()
                fallbackToAndroidTTS(fullText)
            }
        }
    }
    
    /**
     * Fallback to Android TextToSpeech if Supabase fails
     */
    private fun fallbackToAndroidTTS(text: String) {
        if (!ttsInitialized) {
            Toast.makeText(this, "TTS not initialized", Toast.LENGTH_SHORT).show()
            return
        }
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    /**
     * Toggle between Normal and AI keyboard modes
     */
    private fun toggleKeyboardMode() {
        // Toggle AI features container visibility
        currentKeyboardMode = when (currentKeyboardMode) {
            KeyboardMode.NORMAL -> KeyboardMode.AI
            KeyboardMode.AI -> KeyboardMode.NORMAL
        }
        updateKeyboardMode()
        // Ensure keyboard stays visible - don't call any hide methods
    }
    
    /**
     * Update UI based on current keyboard mode
     */
    private fun updateKeyboardMode() {
        // Ensure keyboard stays open - just update visibility without recreating view
        try {
            when (currentKeyboardMode) {
                KeyboardMode.NORMAL -> {
                    // Hide AI features container (emoji grid, language selector)
                    aiFeaturesContainer.visibility = View.GONE
                    // Always show keyboard
                    keyboardContainer.visibility = View.VISIBLE
                }
                KeyboardMode.AI -> {
                    // Show AI features container (emoji grid, language selector)
                    aiFeaturesContainer.visibility = View.VISIBLE
                    // Always show keyboard
                    keyboardContainer.visibility = View.VISIBLE
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Don't let errors hide the keyboard
        }
    }
    
    /**
     * Toggle between voice input mode and TTS mode (only in AI mode)
     */
    private fun toggleAIMode() {
        if (currentKeyboardMode != KeyboardMode.AI) return
        
        voiceInputMode = !voiceInputMode
        
        if (voiceInputMode) {
            Toast.makeText(this, "Voice Input Mode", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Text-to-Speech Mode", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Setup language selector for voice recorder
     */
    private fun setupLanguageSelector(view: View) {
        val languageContainer = view.findViewById<LinearLayout>(R.id.language_selector_container)
        languageButton = Button(this).apply {
            val languageName = when (selectedLanguage.language) {
                "en" -> "English"
                "es" -> "Spanish"
                "fr" -> "French"
                "de" -> "German"
                "it" -> "Italian"
                "pt" -> "Portuguese"
                "ru" -> "Russian"
                "ja" -> "Japanese"
                "ko" -> "Korean"
                "zh" -> "Chinese"
                "ar" -> "Arabic"
                "hi" -> "Hindi"
                else -> selectedLanguage.displayLanguage
            }
            text = "Language: $languageName"
            textSize = 12f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 8)
            background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.button_background)
            
            val layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 4
                marginEnd = 4
                topMargin = 4
                bottomMargin = 4
            }
            this.layoutParams = layoutParams
            
            setOnClickListener {
                showLanguageSelector()
            }
        }
        languageContainer.addView(languageButton)
    }
    
    /**
     * Show keyboard language selection popup
     */
    private fun showLanguageSelector() {
        // Available keyboard languages with their codes
        val availableLanguages = listOf(
            "en" to "English (UK)",
            "es" to "Español",
            "fr" to "Français",
            "de" to "Deutsch",
            "it" to "Italiano",
            "pt" to "Português",
            "ru" to "Русский",
            "ar" to "العربية",
            "hi" to "हिंदी",
            "ja" to "日本語",
            "ko" to "한국어",
            "zh" to "中文"
        )
        
        // Create popup window with language options
        val popupView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1A1F2E"))
            setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())
        }
        
        // Title
        val titleView = TextView(this).apply {
            text = "🌐 Select Language"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 12.dpToPx())
        }
        popupView.addView(titleView)
        
        // Create scrollable container for languages
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                250.dpToPx()
            )
        }
        
        val languagesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        
        // Add language buttons
        availableLanguages.forEach { (langCode, langName) ->
            val isSelected = langCode == currentKeyboardLanguage
            val langButton = TextView(this).apply {
                text = if (isSelected) "✓ $langName" else "   $langName"
                textSize = 15f
                setTextColor(if (isSelected) Color.parseColor("#A78BFA") else Color.WHITE)
                setPadding(12.dpToPx(), 14.dpToPx(), 12.dpToPx(), 14.dpToPx())
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                
                background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.dict_key_background)
                
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 4.dpToPx()
                }
            }
            languagesContainer.addView(langButton)
        }
        
        scrollView.addView(languagesContainer)
        popupView.addView(scrollView)
        
        // Create and show popup window
        val popupWindow = android.widget.PopupWindow(
            popupView,
            280.dpToPx(),
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 10f
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            isFocusable = true
        }
        
        // Set click listeners for language buttons
        for (i in 0 until languagesContainer.childCount) {
            val button = languagesContainer.getChildAt(i)
            val (langCode, langName) = availableLanguages[i]
            button.setOnClickListener {
                switchKeyboardLanguage(langCode, langName)
                popupWindow.dismiss()
            }
        }
        
        // Show popup anchored to keyboard
        rootView?.let { view ->
            popupWindow.showAtLocation(view, Gravity.CENTER, 0, 0)
        }
    }
    
    /**
     * Switch the keyboard to a different language layout
     */
    private fun switchKeyboardLanguage(languageCode: String, languageName: String) {
        currentKeyboardLanguage = languageCode
        currentKeyboardLanguageName = languageName
        
        // Save preference
        getSharedPreferences("keyboard_prefs", MODE_PRIVATE)
            .edit()
            .putString("keyboard_language", languageCode)
            .putString("keyboard_language_name", languageName)
            .apply()
        
        // Update space bar text
        spaceBarButton?.text = languageName
        
        // Update TTS and recognition language
        val locale = Locale(languageCode)
        selectedLanguage = locale
        textToSpeech?.setLanguage(locale)
        
        // Rebuild keyboard with new layout
        rebuildKeyboardLayout()
        
        Toast.makeText(this, "Keyboard: $languageName", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Rebuild the keyboard with the current language layout
     */
    private fun rebuildKeyboardLayout() {
        rootView?.let { view ->
            val rowNumbers = view.findViewById<LinearLayout>(R.id.row_numbers)
            val rowQwerty = view.findViewById<LinearLayout>(R.id.row_qwerty)
            val rowAsdf = view.findViewById<LinearLayout>(R.id.row_asdf)
            val rowZxcv = view.findViewById<LinearLayout>(R.id.row_zxcv)
            
            // Clear existing keys
            rowNumbers.removeAllViews()
            rowQwerty.removeAllViews()
            rowAsdf.removeAllViews()
            rowZxcv.removeAllViews()
            
            if (isNumbersMode) {
                // Numbers/Symbols layout
                val numbersRow1 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
                val symbolsRow1 = listOf("@", "#", "$", "%", "&", "-", "+", "(", ")")
                val symbolsRow2 = listOf("*", "\"", "'", ":", ";", "!", "?")
                
                if (isSymbolsMode) {
                    // More symbols
                    val moreSymbols1 = listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆")
                    val moreSymbols2 = listOf("£", "€", "¥", "^", "°", "=", "{", "}", "\\")
                    val moreSymbols3 = listOf("©", "®", "™", "✓", "[", "]", "<", ">")
                    
                    moreSymbols1.forEach { key ->
                        rowNumbers.addView(createKeyButton(key, key, weight = 1f))
                    }
                    moreSymbols2.forEach { key ->
                        rowQwerty.addView(createKeyButton(key, key, weight = 1f))
                    }
                    moreSymbols3.forEach { key ->
                        rowAsdf.addView(createKeyButton(key, key, weight = 1f))
                    }
                    
                    // Toggle button for more symbols
                    val moreButton = createSpecialKeyButton("123", "TOGGLE_SYMBOLS", weight = 1.5f)
                    rowZxcv.addView(moreButton)
                    
                    listOf(",", ".", "/", "?", "!", "'").forEach { key ->
                        rowZxcv.addView(createKeyButton(key, key, weight = 1f))
                    }
                } else {
                    // Basic numbers and symbols
                    numbersRow1.forEach { key ->
                        rowNumbers.addView(createKeyButton(key, key, weight = 1f))
                    }
                    symbolsRow1.forEach { key ->
                        rowQwerty.addView(createKeyButton(key, key, weight = 1f))
                    }
                    symbolsRow2.forEach { key ->
                        rowAsdf.addView(createKeyButton(key, key, weight = 1f))
                    }
                    
                    // Toggle button for more symbols
                    val moreButton = createSpecialKeyButton("=\\<", "TOGGLE_SYMBOLS", weight = 1.5f)
                    rowZxcv.addView(moreButton)
                    
                    listOf(",", ".", "/", "_", "~", "`").forEach { key ->
                        rowZxcv.addView(createKeyButton(key, key, weight = 1f))
                    }
                }
                
                val backspaceButton = createSpecialKeyButton("⌫", "BACKSPACE", weight = 1.5f)
                rowZxcv.addView(backspaceButton)
                
                // Update the numbers button to show ABC
                numbersButton?.text = "ABC"
            } else {
                // Get layout for current language (fallback to English)
                val layout = keyboardLayouts[currentKeyboardLanguage] ?: keyboardLayouts["en"]!!
                
                // Rebuild numbers row
                layout.numbers.forEach { key ->
                    rowNumbers.addView(createKeyButton(key, key, weight = 1f))
                }
                
                // Rebuild first letter row
                layout.row1.forEach { key ->
                    rowQwerty.addView(createKeyButton(key, key, weight = 1f))
                }
                
                // Rebuild second letter row
                layout.row2.forEach { key ->
                    rowAsdf.addView(createKeyButton(key, key, weight = 1f))
                }
                
                // Rebuild third letter row with shift and backspace
                shiftButton = createSpecialKeyButton("⇧", "SHIFT", weight = 1.5f)
                rowZxcv.addView(shiftButton)
                
                layout.row3.forEach { key ->
                    rowZxcv.addView(createKeyButton(key, key, weight = 1f))
                }
                
                val backspaceButton = createSpecialKeyButton("⌫", "BACKSPACE", weight = 1.5f)
                rowZxcv.addView(backspaceButton)
                
                // Update the numbers button to show !#1
                numbersButton?.text = "!#1"
            }
        }
    }
    
    /**
     * Toggle between letters and numbers/symbols keyboard
     */
    private fun toggleNumbersSymbols() {
        isNumbersMode = !isNumbersMode
        isSymbolsMode = false // Reset symbols mode when toggling
        predictionRunnable?.let { predictionHandler.removeCallbacks(it) }
        rootView?.let { v ->
            v.findViewById<View>(R.id.ai_features_row)?.visibility = View.VISIBLE
            v.findViewById<View>(R.id.predictions_container)?.visibility = View.GONE
        }
        rebuildKeyboardLayout()
    }
    
    /**
     * Toggle between basic symbols and more symbols
     */
    private fun toggleSymbolsMode() {
        isSymbolsMode = !isSymbolsMode
        rebuildKeyboardLayout()
    }
    
    /**
     * Load saved keyboard language preference
     */
    private fun loadKeyboardLanguagePreference() {
        val prefs = getSharedPreferences("keyboard_prefs", MODE_PRIVATE)
        currentKeyboardLanguage = prefs.getString("keyboard_language", "en") ?: "en"
        currentKeyboardLanguageName = prefs.getString("keyboard_language_name", "English (UK)") ?: "English (UK)"
    }
    
    /**
     * Setup translation button
     */
    private fun setupTranslationButton(view: View) {
        val languageContainer = view.findViewById<LinearLayout>(R.id.language_selector_container)
        translateButton = Button(this).apply {
            val languageName = when (targetTranslationLanguage.language) {
                "en" -> "English"
                "es" -> "Spanish"
                "fr" -> "French"
                "de" -> "German"
                "it" -> "Italian"
                "pt" -> "Portuguese"
                "ru" -> "Russian"
                "ja" -> "Japanese"
                "ko" -> "Korean"
                "zh" -> "Chinese"
                "ar" -> "Arabic"
                "hi" -> "Hindi"
                else -> targetTranslationLanguage.displayLanguage
            }
            text = "Translate to: $languageName"
            textSize = 12f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 8)
            background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.button_background)
            
            val layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 4
                marginEnd = 4
                topMargin = 4
                bottomMargin = 4
            }
            this.layoutParams = layoutParams
            
            setOnClickListener {
                translateCurrentText()
            }
            
            setOnLongClickListener {
                showTranslationLanguageSelector()
                true
            }
        }
        languageContainer.addView(translateButton)
    }
    
    /**
     * Show translation language selection dialog
     */
    private fun showTranslationLanguageSelector() {
        val languages = listOf(
            Locale.ENGLISH to "English",
            Locale("es") to "Spanish",
            Locale("fr") to "French",
            Locale("de") to "German",
            Locale("it") to "Italian",
            Locale("pt") to "Portuguese",
            Locale("ru") to "Russian",
            Locale("ja") to "Japanese",
            Locale("ko") to "Korean",
            Locale("zh") to "Chinese",
            Locale("ar") to "Arabic",
            Locale("hi") to "Hindi"
        )
        
        val languageNames = languages.map { it.second }.toTypedArray()
        
        android.app.AlertDialog.Builder(this)
            .setTitle("Select Translation Language")
            .setItems(languageNames) { _, which ->
                targetTranslationLanguage = languages[which].first
                translateButton.text = "Translate to: ${languages[which].second}"
                Toast.makeText(this, "Translation target set to ${languages[which].second}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    /**
     * Translate current text using Supabase
     */
    private fun translateCurrentText() {
        val inputConnection: InputConnection? = currentInputConnection
        val textBeforeCursor = inputConnection?.getTextBeforeCursor(1000, 0)
        val textAfterCursor = inputConnection?.getTextAfterCursor(1000, 0)
        
        val fullText = "${textBeforeCursor ?: ""}${textAfterCursor ?: ""}"
        
        if (TextUtils.isEmpty(fullText)) {
            Toast.makeText(this, "No text to translate", Toast.LENGTH_SHORT).show()
            return
        }
        
        Toast.makeText(this, "Translating...", Toast.LENGTH_SHORT).show()
        
        serviceScope.launch {
            try {
                val result = translationService.translateText(
                    text = fullText.trim(),
                    targetLanguage = targetTranslationLanguage.language
                )
                
                result.onSuccess { translatedText ->
                    // Replace current text with translated text
                    inputConnection?.let { ic ->
                        // Delete existing text
                        val textLength = fullText.length
                        ic.deleteSurroundingText(textLength, 0)
                        // Insert translated text
                        ic.commitText(translatedText, 1)
                    }
                    Toast.makeText(this@MainKeyboardService, "Translation complete", Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    Toast.makeText(this@MainKeyboardService, 
                        "Translation failed: ${error.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainKeyboardService, 
                    "Translation error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Convert current text to voice using Supabase voice-conversion
     */
    private fun convertCurrentTextToVoice() {
        val inputConnection: InputConnection? = currentInputConnection
        val textBeforeCursor = inputConnection?.getTextBeforeCursor(1000, 0)
        val textAfterCursor = inputConnection?.getTextAfterCursor(1000, 0)
        val fullText = "${textBeforeCursor ?: ""}${textAfterCursor ?: ""}".trim()

        if (TextUtils.isEmpty(fullText)) {
            Toast.makeText(this, "No text to convert", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Converting text to voice...", Toast.LENGTH_SHORT).show()
        val voiceStyle = "aria"

        serviceScope.launch {
            try {
                val result = voiceConversionService.convertText(
                    text = fullText,
                    voiceStyle = voiceStyle,
                    targetLanguage = targetTranslationLanguage.language
                )

                result.onSuccess { response ->
                    val audioBase64 = response.audioBase64
                    if (!audioBase64.isNullOrBlank()) {
                        playBase64Audio(audioBase64, "mp3")
                        Toast.makeText(this@MainKeyboardService, "Playing converted voice", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainKeyboardService, "No audio returned", Toast.LENGTH_SHORT).show()
                    }
                }.onFailure { error ->
                    Toast.makeText(this@MainKeyboardService,
                        "Voice conversion failed: ${error.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainKeyboardService,
                    "Voice conversion error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Create a voice clone from a recorded audio file
     */
    private fun createVoiceCloneFromRecording(audioFile: File) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val cloneName = "Voice Clone $timestamp"

        Toast.makeText(this, "Creating voice clone...", Toast.LENGTH_SHORT).show()
        serviceScope.launch {
            try {
                val result = voiceCloneService.createVoiceClone(
                    audioFile = audioFile,
                    name = cloneName,
                    description = "Recorded from keyboard"
                )

                result.onSuccess { response ->
                    if (response.success && !response.voiceId.isNullOrBlank()) {
                        Toast.makeText(this@MainKeyboardService,
                            "Voice clone created: ${response.name ?: cloneName}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainKeyboardService,
                            "Voice clone failed", Toast.LENGTH_LONG).show()
                    }
                }.onFailure { error ->
                    Toast.makeText(this@MainKeyboardService,
                        "Voice clone error: ${error.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainKeyboardService,
                    "Voice clone error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Run the voice workflow based on workflow type
     */
    private fun runCompleteVoiceWorkflow(
        audioFile: File,
        workflowType: String,
        targetLanguage: String,
        voiceStyle: String,
        voiceStyleName: String = "Adam",
        languageName: String = "English"
    ) {
        android.util.Log.d("DeltaVoice", "=== STARTING WORKFLOW ===")
        android.util.Log.d("DeltaVoice", "Type: $workflowType, Lang: $targetLanguage, Voice: $voiceStyle")
        android.util.Log.d("DeltaVoice", "Audio file: ${audioFile.absolutePath}")
        android.util.Log.d("DeltaVoice", "Audio file exists: ${audioFile.exists()}, size: ${audioFile.length()} bytes")
        
        // Show immediate feedback that processing started
        audioDurationText.text = "⏳ Connecting..."

        serviceScope.launch {
            try {
                android.util.Log.d("DeltaVoice", "Coroutine launched, calling Supabase backend...")
                audioDurationText.text = "⏳ Processing..."
                
                val result = completeVoiceWorkflowService.runWorkflow(
                    audioFile = audioFile,
                    targetLanguage = targetLanguage,
                    voiceStyle = voiceStyle,
                    workflowType = workflowType
                )

                android.util.Log.d("DeltaVoice", "Backend call returned")

                result.onSuccess { response ->
                    android.util.Log.d("DeltaVoice", "=== SUCCESS from backend ===")
                    android.util.Log.d("DeltaVoice", "originalText: ${response.originalText?.take(100) ?: "null"}")
                    android.util.Log.d("DeltaVoice", "translatedText: ${response.translatedText?.take(100) ?: "null"}")
                    android.util.Log.d("DeltaVoice", "hasAudio: ${!response.convertedAudioBase64.isNullOrBlank()}")
                    android.util.Log.d("DeltaVoice", "audioLength: ${response.convertedAudioBase64?.length ?: 0}")
                    handleWorkflowSuccess(response, workflowType, voiceStyleName, languageName)
                }.onFailure { error ->
                    android.util.Log.e("DeltaVoice", "=== FAILURE from backend ===")
                    android.util.Log.e("DeltaVoice", "Error: ${error.message}", error)
                    handleWorkflowError(error.message ?: "Unknown error")
                }
            } catch (e: Exception) {
                android.util.Log.e("DeltaVoice", "=== EXCEPTION in workflow ===")
                android.util.Log.e("DeltaVoice", "Exception: ${e.javaClass.simpleName}: ${e.message}", e)
                handleWorkflowError(e.message ?: "Connection error")
            }
        }
        
        android.util.Log.d("DeltaVoice", "Coroutine dispatched (running in background)")
    }
    
    private fun handleWorkflowSuccess(
        response: com.deltavoice.api.CompleteVoiceWorkflowService.WorkflowResponse,
        workflowType: String,
        voiceStyleName: String,
        languageName: String
    ) {
                    val audioBase64 = response.convertedAudioBase64
        val hasAudio = !audioBase64.isNullOrBlank() && audioBase64.length > 100
        
        android.util.Log.d("DeltaVoice", "=== WORKFLOW SUCCESS ===")
        android.util.Log.d("DeltaVoice", "Type: $workflowType")
        android.util.Log.d("DeltaVoice", "Has audio: $hasAudio (length: ${audioBase64?.length ?: 0})")
        android.util.Log.d("DeltaVoice", "Original text: ${response.originalText?.take(50) ?: "null"}")
        android.util.Log.d("DeltaVoice", "Translated text: ${response.translatedText?.take(50) ?: "null"}")
        
        when (workflowType) {
            "complete" -> {
                // Full Conversion: Insert text and show audio
                response.translatedText?.takeIf { it.isNotBlank() }?.let { 
                    insertText(it)
                    android.util.Log.d("DeltaVoice", "Inserted translated text")
                }
                
                if (hasAudio) {
                    android.util.Log.d("DeltaVoice", "Saving and playing audio...")
                    saveAndShowProcessedAudio(audioBase64!!, "mp3")
                    Toast.makeText(this, "✓ Ready! Tap ▶ to hear, Send to share", Toast.LENGTH_LONG).show()
                } else {
                    android.util.Log.w("DeltaVoice", "No audio returned - showing text only result")
                    Toast.makeText(this, "✓ Text translated (no audio returned)", Toast.LENGTH_LONG).show()
                    // Don't hide UI - let user see the result and retry if needed
                    audioDurationText.text = "No audio"
                    resetButtonState()
                }
            }
            
            "voice-only" -> {
                // Translate My Same Voice (Voice Cloning): Your voice in different language
                if (hasAudio) {
                    android.util.Log.d("DeltaVoice", "Saving cloned voice audio...")
                    saveAndShowProcessedAudio(audioBase64!!, "mp3")
                    Toast.makeText(this, "✓ Your voice cloned! Tap ▶ to hear", Toast.LENGTH_LONG).show()
                } else {
                    android.util.Log.w("DeltaVoice", "No audio returned for voice cloning")
                    Toast.makeText(this, "❌ Voice cloning failed - try again", Toast.LENGTH_LONG).show()
                    // Don't hide UI - let user retry
                    audioDurationText.text = "❌ Failed"
                    resetButtonState()
                }
            }
            
            "text-only" -> {
                // Transcript & Translate: Insert translated text into message bar
                val text = response.translatedText?.takeIf { it.isNotBlank() } 
                    ?: response.originalText?.takeIf { it.isNotBlank() }
                
                if (text != null) {
                    insertText(text)
                    android.util.Log.d("DeltaVoice", "Inserted transcribed & translated text")
                    Toast.makeText(this, "✓ Translated text inserted!", Toast.LENGTH_SHORT).show()
                    // For text-only, hide the UI since there's no audio to play
                    hideVoiceProcessingUI()
                    recordingFilePath = null
                } else {
                    android.util.Log.w("DeltaVoice", "No text detected")
                    Toast.makeText(this, "❌ No speech detected - try again", Toast.LENGTH_LONG).show()
                    // Don't hide UI - let user retry
                    audioDurationText.text = "❌ No speech"
                    resetButtonState()
                }
            }
            
            else -> {
                response.translatedText?.takeIf { it.isNotBlank() }?.let { insertText(it) }
                if (hasAudio) {
                    saveAndShowProcessedAudio(audioBase64!!, "mp3")
                }
                Toast.makeText(this, "✓ Done!", Toast.LENGTH_SHORT).show()
                resetButtonState()
            }
        }
    }
    
    private fun handleWorkflowError(errorMessage: String) {
        android.util.Log.e("DeltaVoice", "Workflow error: $errorMessage")
        
        // Check if it's a network-related error
        val isNetworkError = errorMessage.contains("internet", ignoreCase = true) ||
            errorMessage.contains("network", ignoreCase = true) ||
            errorMessage.contains("Unable to resolve host", ignoreCase = true) ||
            errorMessage.contains("connection", ignoreCase = true) ||
            errorMessage.contains("timeout", ignoreCase = true) ||
            errorMessage.contains("socket", ignoreCase = true)
        
        if (isNetworkError) {
            // Show network error with settings option
            showNetworkErrorWithSettings("Connection failed. Check your internet and try again.")
            return
        }
        
        // Determine user-friendly error message for non-network errors
        val userMessage = when {
            errorMessage.contains("API key", ignoreCase = true) ||
            errorMessage.contains("unauthorized", ignoreCase = true) ||
            errorMessage.contains("401", ignoreCase = true) ->
                "🔑 API configuration error. Please contact support."
            
            errorMessage.contains("server", ignoreCase = true) ||
            errorMessage.contains("500", ignoreCase = true) ||
            errorMessage.contains("503", ignoreCase = true) ->
                "🔧 Server is busy. Please try again in a moment."
            
            errorMessage.contains("audio", ignoreCase = true) ||
            errorMessage.contains("empty", ignoreCase = true) ->
                "🎤 No audio detected. Please record again."
            
            errorMessage.contains("speech", ignoreCase = true) ||
            errorMessage.contains("transcri", ignoreCase = true) ->
                "🗣️ No speech detected. Speak clearly and try again."
            
            else -> "❌ Error: ${errorMessage.take(50)}"
        }
        
        Toast.makeText(this, userMessage, Toast.LENGTH_LONG).show()
        
        // DON'T hide the UI - just reset button state so user can retry
        resetButtonState()
        
        // Update duration text based on error type
        val statusText = when {
            errorMessage.contains("API", ignoreCase = true) -> "🔑 Config Error"
            errorMessage.contains("server", ignoreCase = true) -> "🔧 Server Error"
            errorMessage.contains("audio", ignoreCase = true) -> "🎤 No Audio"
            errorMessage.contains("speech", ignoreCase = true) -> "🗣️ No Speech"
            else -> "❌ Tap to Retry"
        }
        audioDurationText.text = statusText
    }
    
    private fun resetProcessingUI() {
        hideVoiceProcessingUI()
        resetButtonState()
    }
    
    private fun resetButtonState() {
        rootView?.let { view ->
            view.findViewById<Button>(R.id.keyboard_button_action)?.apply {
                isEnabled = true
                text = "  Done"
                background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.voice_mode_button_purple)
                setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_ai_mode, 0, 0, 0)
                compoundDrawables.forEach { drawable ->
                    drawable?.setTint(Color.WHITE)
                }
            }
        }
    }
    

    /**
     * Decode base64 audio to a temp file and play it
     */
    private fun playBase64Audio(base64Audio: String, extension: String) {
        serviceScope.launch {
            try {
                val audioBytes = Base64.decode(base64Audio, Base64.DEFAULT)
                val audioFile = withContext(Dispatchers.IO) {
                    val fileName = "voice_output_${System.currentTimeMillis()}.$extension"
                    val file = File(cacheDir, fileName)
                    file.writeBytes(audioBytes)
                    file
                }

                val mediaPlayer = MediaPlayer()
                mediaPlayer.setDataSource(audioFile.absolutePath)
                mediaPlayer.prepare()
                mediaPlayer.setOnCompletionListener {
                    it.release()
                    audioFile.delete()
                }
                mediaPlayer.start()
            } catch (e: Exception) {
                Toast.makeText(this@MainKeyboardService,
                    "Audio playback failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Start voice recording
     */
    private fun startVoiceRecording(action: RecordingAction = RecordingAction.TRANSCRIBE) {
        // Don't start if already recording
        if (isRecording) {
            Toast.makeText(this, "Already recording", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Stop voice-to-text if active
        if (isListening) {
            stopVoiceInput()
        }
        
        // Check permission
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.microphone_permission_required, Toast.LENGTH_LONG).show()
            // Try to request permission
            try {
                val intent = android.content.Intent(this, PermissionsActivity::class.java)
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return
        }

        try {
            recordingAction = action

            // Release any existing recorder first
            releaseMediaRecorder()
            
            // Create file path for recording - use cache directory for better compatibility
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "voice_recording_$timestamp.m4a"
            
            // Use cache directory which doesn't require storage permissions on newer Android
            val recordingsDir = File(cacheDir, "Recordings")
            if (!recordingsDir.exists()) {
                val created = recordingsDir.mkdirs()
                if (!created) {
                    Toast.makeText(this, "Failed to create recordings directory", Toast.LENGTH_SHORT).show()
                    return
                }
            }
            
            val recordingFile = File(recordingsDir, fileName)
            recordingFilePath = recordingFile.absolutePath
            
            // Verify we can create the file
            val parentDir = recordingFile.parentFile
            if (parentDir == null || !parentDir.exists()) {
                Toast.makeText(this, "Cannot access recordings directory", Toast.LENGTH_SHORT).show()
                return
            }

            // Initialize MediaRecorder
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
                try {
                    // Set up MediaRecorder in correct order
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setOutputFile(recordingFilePath)
                    
                    // Prepare and start
                    prepare()
                    start()
                    
                    isRecording = true
                    
                    // Update UI
                    voiceButton.setImageResource(R.drawable.ic_mic_active)
                    
                    // Only show Toast for actions that don't have UI
                    if (recordingAction != RecordingAction.COMPLETE_WORKFLOW) {
                        val message = when (recordingAction) {
                            RecordingAction.TRANSCRIBE -> "Recording started..."
                            RecordingAction.CREATE_VOICE_CLONE -> "Recording for voice clone..."
                            else -> "Recording..."
                        }
                        Toast.makeText(this@MainKeyboardService, message, Toast.LENGTH_SHORT).show()
                    }
                        
                } catch (e: IllegalStateException) {
                    e.printStackTrace()
                    Toast.makeText(this@MainKeyboardService, 
                        "Recording failed: Illegal state - ${e.message}", Toast.LENGTH_LONG).show()
                    releaseMediaRecorder()
                } catch (e: IOException) {
                    e.printStackTrace()
                    Toast.makeText(this@MainKeyboardService, 
                        "Recording failed: IO error - ${e.message}", Toast.LENGTH_LONG).show()
                    releaseMediaRecorder()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this@MainKeyboardService, 
                        "Recording failed: ${e.message}", Toast.LENGTH_LONG).show()
                    releaseMediaRecorder()
                }
            } ?: run {
                Toast.makeText(this, "Failed to initialize MediaRecorder", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error starting recording: ${e.message}", Toast.LENGTH_LONG).show()
            releaseMediaRecorder()
        }
    }

    /**
     * Stop voice recording and convert to text using Supabase
     */
    private fun stopVoiceRecording() {
        if (!isRecording) {
            return
        }
        
        if (mediaRecorder == null) {
            isRecording = false
            voiceButton.setImageResource(R.drawable.ic_mic)
            return
        }
        
        try {
            val filePath = recordingFilePath
            val wasRecording = isRecording
            
            // Stop and release MediaRecorder
            try {
                mediaRecorder?.apply {
                    if (wasRecording) {
                        stop()
                    }
                    release()
                }
            } catch (e: IllegalStateException) {
                // Already stopped or not started properly
                e.printStackTrace()
            } catch (e: RuntimeException) {
                // Sometimes happens if recorder wasn't properly initialized
                e.printStackTrace()
            }
            
            mediaRecorder = null
            isRecording = false
            
            // Update UI
            voiceButton.setImageResource(R.drawable.ic_mic)
            
            val action = recordingAction
            recordingAction = RecordingAction.TRANSCRIBE
            
            // Only show Toast for actions that don't have UI transition
            if (action != RecordingAction.COMPLETE_WORKFLOW) {
                Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show()
            }

            // Process recording based on action
            filePath?.let { path ->
                val audioFile = File(path)
                if (audioFile.exists() && audioFile.length() > 0) {
                    when (action) {
                        RecordingAction.TRANSCRIBE -> {
                            Toast.makeText(this, "Converting speech to text...", Toast.LENGTH_SHORT).show()
                            serviceScope.launch {
                                try {
                                    val result = voiceToTextService.transcribeAudio(
                                        audioFile = audioFile,
                                        language = selectedLanguage.language
                                    )
                                    
                                    result.onSuccess { transcribedText ->
                                        if (transcribedText.isNotBlank()) {
                                            insertText(transcribedText)
                                            Toast.makeText(this@MainKeyboardService,
                                                "Speech converted to text", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(this@MainKeyboardService,
                                                "No speech detected", Toast.LENGTH_SHORT).show()
                                        }
                                    }.onFailure { error ->
                                        Toast.makeText(this@MainKeyboardService,
                                            "Transcription failed: ${error.message}",
                                            Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(this@MainKeyboardService,
                                        "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                            recordingFilePath = null // Clear for transcribe
                        }
                        RecordingAction.CREATE_VOICE_CLONE -> {
                            createVoiceCloneFromRecording(audioFile)
                            recordingFilePath = null // Clear for clone
                        }
                        RecordingAction.COMPLETE_WORKFLOW -> {
                            // Show the Step 2 UI - keep recordingFilePath for Full Process button
                            showVoiceProcessingUI()
                            updateKeyboardModeSelection("complete")
                        }
                    }
                } else {
                    Toast.makeText(this, "Recording file not found or empty", Toast.LENGTH_SHORT).show()
                    recordingFilePath = null
                }
            }
            // removed recordingFilePath = null here because COMPLETE_WORKFLOW needs it later
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error stopping recording: ${e.message}", Toast.LENGTH_LONG).show()
            releaseMediaRecorder()
        }
    }

    /**
     * Release MediaRecorder resources
     */
    private fun releaseMediaRecorder() {
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaRecorder = null
        isRecording = false
        voiceButton.setImageResource(R.drawable.ic_mic)
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        releaseMediaRecorder()
        stopRecordingPlayback()
    }
}

