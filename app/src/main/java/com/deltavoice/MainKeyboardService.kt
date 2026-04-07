package com.deltavoice

import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
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
import android.speech.tts.UtteranceProgressListener
import android.text.TextUtils
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
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
import android.widget.PopupMenu
import android.view.TextureView
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.view.Surface
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.Manifest
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.deltavoice.api.VoiceToTextService
import com.deltavoice.api.TextToSpeechService
import com.deltavoice.api.TranslationService
import com.deltavoice.api.VoiceCloneService
import com.deltavoice.api.VoiceConversionService
import com.deltavoice.api.CompleteVoiceWorkflowService
import com.deltavoice.privacy.OutboundHttpPolicy
import com.deltavoice.privacy.OutboundMediaSanitizer
import com.deltavoice.predict.PredictionProvider
import com.deltavoice.predict.PredictionResult
import com.deltavoice.debug.AgentDebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import android.app.AlertDialog
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.res.Resources
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import java.io.File
import java.io.IOException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import org.json.JSONObject

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
    private lateinit var moreOptionsContainer: View
    private lateinit var voiceRecordingContainer: LinearLayout
    private lateinit var voiceProcessingStep2Container: LinearLayout
    private lateinit var keyboardSpinnerLanguage: Spinner
    private lateinit var keyboardSpinnerVoice: Spinner
    private lateinit var keyboardCardFull: LinearLayout
    private lateinit var keyboardCardVoice: LinearLayout
    private lateinit var keyboardCardText: LinearLayout
    private lateinit var recordingStatusText: android.widget.TextView
    private lateinit var recordingMicButton: ImageButton
    private lateinit var recordingTimerText: android.widget.TextView
    private lateinit var playRecordingButton: ImageButton
    private lateinit var sendRawRecordingButton: ImageButton
    private lateinit var audioDurationText: android.widget.TextView
    private lateinit var audioPlaybackSeekBar: android.widget.SeekBar
    private var rootView: View? = null

    /** When video UI is shown from the bubble overlay, non-root views for recording/preview live here. */
    private var videoUiHost: View? = null

    private fun videoUiContent(): View? = videoUiHost ?: rootView

    private fun voiceStep2ActionButton(): Button? =
        if (::voiceProcessingStep2Container.isInitialized) {
            voiceProcessingStep2Container.findViewById(R.id.keyboard_button_action)
        } else null

    /** Bubble overlay: dictionary UI in a separate WindowManager layer. */
    private var overlayDictionaryRoot: View? = null
    private var overlayDictionaryClose: (() -> Unit)? = null

    /**
     * True while a bubble overlay panel owns voice/video/AI/dictionary views.
     * IME key row and top icon bar on [rootView] must not be hidden — the panel is a separate window.
     */
    private var overlayBubbleKeyboardIsolated = false

    /**
     * Hide/show QWERTY key area on the IME. When a bubble overlay is active we **skip hiding** the IME
     * (the overlay is a separate window) but we still **allow show** so a previously hidden IME can recover.
     */
    private fun applyImeKeyboardContainerVisible(visible: Boolean) {
        if (overlayBubbleKeyboardIsolated && !visible) return
        keyboardContainer.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /** Restore top icon row + key row on the IME while a bubble panel is open (independent window). */
    private fun snapImeTypingUiToVisibleForBubbleOverlay() {
        if (!overlayBubbleKeyboardIsolated) return
        val root = rootView ?: return
        try {
            root.findViewById<View>(R.id.top_bar_container)?.visibility = View.VISIBLE
            root.findViewById<View>(R.id.ai_features_row)?.visibility = View.VISIBLE
            root.findViewById<View>(R.id.predictions_container)?.visibility = View.GONE
            if (::keyboardContainer.isInitialized) {
                keyboardContainer.visibility = View.VISIBLE
            }
        } catch (_: Exception) {
        }
        // #region agent log
        try {
            val jo = JSONObject()
            jo.put("sessionId", "a65c8d")
            jo.put("hypothesisId", "H3")
            jo.put("location", "snapImeTypingUiToVisibleForBubbleOverlay")
            jo.put("message", "ime_chrome_restored")
            jo.put(
                "data",
                JSONObject().apply {
                    put("kbVis", if (::keyboardContainer.isInitialized) keyboardContainer.visibility else -1)
                }
            )
            jo.put("timestamp", System.currentTimeMillis())
            File(filesDir, "debug-a65c8d.log").appendText(jo.toString() + "\n")
        } catch (_: Exception) {
        }
        // #endregion
    }

    private fun dictContentRoot(): View? = overlayDictionaryRoot ?: rootView

    // SeekBar progress updater
    private val seekBarHandler = Handler(Looper.getMainLooper())
    private var seekBarRunnable: Runnable? = null
    private var audioTotalDurationMs: Int = 0

    // Voice recording timer
    private val recordingUiHandler = Handler(Looper.getMainLooper())
    private var recordingTimerRunnable: Runnable? = null
    private var recordingStartTimeMs: Long = 0L

    // Network callback for "internet restored" when Process button was blocked
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var networkRequest: NetworkRequest? = null
    
    // Playback
    private var playbackMediaPlayer: MediaPlayer? = null
    private var isPlayingRecording = false
    
    // Predictive text
    private val predictionHandler = Handler(Looper.getMainLooper())
    private var predictionRunnable: Runnable? = null
    private var predictionComputeJob: Job? = null
    private var lastPredictionQuery: String = ""
    private var lastRenderedSuggestions: List<String> = emptyList()
    private var keyFeedbackSoundEnabled: Boolean = false
    private var keyFeedbackVibrationEnabled: Boolean = true
    private var keyFeedbackPrefsLastReadMs: Long = 0L
    private var predictiveTextEnabled: Boolean = true
    private var autoCorrectionEnabled: Boolean = true
    private var predictionPrefsLastReadMs: Long = 0L
    private var lastTypingEventMs: Long = 0L
    private var lastTypingIntervalMs: Long = Long.MAX_VALUE
    // Auto-correct state (Gboard-style)
    private var pendingAutoCorrect: String? = null
    private var lastAutoCorrectOriginal: String? = null
    private var lastAutoCorrectReplacement: String? = null
    private var autoCorrectUndoable: Boolean = false
    private val backspaceRepeatHandler = Handler(Looper.getMainLooper())
    private var backspaceRepeatRunnable: Runnable? = null
    private var isBackspaceRepeating: Boolean = false
    companion object {
        /** Current service instance for overlay bubble attach (null when keyboard not active). */
        var serviceInstance: MainKeyboardService? = null

        /** Overlay-only host when the system has not bound the IME yet. */
        private var standaloneOverlayInstance: MainKeyboardService? = null

        /**
         * Returns the running IME if any, otherwise creates a standalone [MainKeyboardService] host
         * so bubble overlays use the same UI and logic as the keyboard without the IME window.
         */
        fun acquireForOverlay(context: Context): MainKeyboardService? {
            serviceInstance?.let { return it }
            standaloneOverlayInstance?.let { return it }
            return try {
                val h = MainKeyboardService()
                h.attachBaseContext(AppLocaleHelper.wrap(context.applicationContext))
                h.isStandaloneOverlayHost = true
                h.onCreate()
                h
            } catch (e: Exception) {
                Log.e("DeltaVoice", "acquireForOverlay", e)
                null
            }
        }

        /** Tear down standalone overlay host when the bubble service stops (IME continues unaffected). */
        fun releaseStandaloneOverlayHostForBubble() {
            val s = standaloneOverlayInstance ?: return
            s.cleanupBeforeImeTakeover()
            if (serviceInstance === s) serviceInstance = null
            standaloneOverlayInstance = null
        }

        // Performance targets (confirmed): backspace 30-45 chars/sec when held, typing 12-15 chars/sec
        private const val PREDICTION_DELAY_NORMAL_MS = 80L
        private const val PREDICTION_DELAY_FAST_MS = 200L
        private const val FAST_TYPING_INTERVAL_MS = 65L
        private const val KEY_FEEDBACK_PREFS_CACHE_MS = 1000L
        private const val PREDICTION_PREFS_CACHE_MS = 1000L
        // Backspace repeat: 22ms interval = 1000/22 ≈ 45 chars/sec (target 30-45)
        private const val BACKSPACE_REPEAT_START_DELAY_MS = 120L
        private const val BACKSPACE_REPEAT_INTERVAL_MS = 22L
        private const val KEY_PRESS_ANIM_DURATION_MS = 35L
        private const val CLIPBOARD_PREFS = "clipboard_prefs"
        private const val CLIPBOARD_HISTORY_KEY = "clipboard_history"
        private const val CLIPBOARD_MAX_ITEMS = 20
        private const val CLIPBOARD_DELIMITER = "\u001E"
        private const val LONG_PRESS_DELAY_MS = 300L

        /** Gboard-style: base key -> list of variants (lowercase + uppercase for letters). */
        private val ACCENT_MAP: Map<String, List<String>> = mapOf(
            "a" to listOf("à", "á", "â", "ã", "ä", "å", "æ", "ā", "ă", "ą", "ª", "À", "Á", "Â", "Ã", "Ä", "Å", "Æ", "Ā", "Ă", "Ą"),
            "c" to listOf("ç", "ć", "č", "ĉ", "Ç", "Ć", "Č", "Ĉ"),
            "d" to listOf("ð", "ď", "đ", "Ð", "Ď", "Đ"),
            "e" to listOf("è", "é", "ê", "ë", "ē", "ė", "ę", "ě", "È", "É", "Ê", "Ë", "Ē", "Ė", "Ę", "Ě"),
            "g" to listOf("ğ", "ĝ", "ġ", "Ğ", "Ĝ", "Ġ"),
            "h" to listOf("ĥ", "ħ", "Ĥ", "Ħ"),
            "i" to listOf("ì", "í", "î", "ï", "ī", "ĩ", "į", "ı", "¡", "Ì", "Í", "Î", "Ï", "Ī", "Ĩ", "Į", "İ"),
            "j" to listOf("ĵ", "Ĵ"),
            "l" to listOf("ł", "ĺ", "ľ", "ļ", "Ł", "Ĺ", "Ľ", "Ļ"),
            "n" to listOf("ñ", "ń", "ň", "ņ", "Ñ", "Ń", "Ň", "Ņ"),
            "o" to listOf("ò", "ó", "ô", "õ", "ö", "ø", "ō", "ő", "œ", "ọ", "Ò", "Ó", "Ô", "Õ", "Ö", "Ø", "Ō", "Ő", "Œ", "Ọ"),
            "r" to listOf("ŕ", "ř", "Ŕ", "Ř"),
            "s" to listOf("ß", "ś", "š", "ş", "ŝ", "Ś", "Š", "Ş", "Ŝ"),
            "t" to listOf("ţ", "ť", "þ", "Ţ", "Ť", "Þ"),
            "u" to listOf("ù", "ú", "û", "ü", "ū", "ů", "ű", "ų", "Ù", "Ú", "Û", "Ü", "Ū", "Ů", "Ű", "Ų"),
            "w" to listOf("ŵ", "Ŵ"),
            "y" to listOf("ý", "ŷ", "ÿ", "Ý", "Ŷ", "Ÿ"),
            "z" to listOf("ž", "ź", "ż", "Ž", "Ź", "Ż"),
            "0" to listOf("°", "⁰"),
            "1" to listOf("¹", "½", "⅓", "¼", "⅛"),
            "2" to listOf("²", "⅔"),
            "3" to listOf("³", "¾", "⅜"),
            "4" to listOf("⁴"),
            "5" to listOf("⁵", "⅝"),
            "7" to listOf("⁷", "⅞"),
            "8" to listOf("⁸"),
            "9" to listOf("⁹"),
            "!" to listOf("¡"),
            "?" to listOf("¿"),
            "-" to listOf("–", "—", "·"),
            "." to listOf("…", "•"),
            "'" to listOf("'", "'", "‚", "‛"),
            "\"" to listOf(""", """, "„", "‟"),
            "/" to listOf("\\"),
            "$" to listOf("€", "£", "¥", "₩", "₹", "₽"),
            "&" to listOf("§"),
            "%" to listOf("‰")
        )
    }

    /** True when this instance exists only to host overlay features (system IME not bound). */
    private var isStandaloneOverlayHost: Boolean = false

    private var localeResourcesCacheTag: String? = null
    private var localeWrappedResources: Resources? = null

    /**
     * Keeps IME UI strings in sync with [AppCompatDelegate] app language (same as Settings).
     */
    override fun getResources(): Resources {
        val locales = AppCompatDelegate.getApplicationLocales()
        val tag = if (locales.isEmpty) "" else locales[0]?.toLanguageTag() ?: ""
        if (tag == localeResourcesCacheTag && localeWrappedResources != null) {
            return localeWrappedResources!!
        }
        localeResourcesCacheTag = tag
        localeWrappedResources = AppLocaleHelper.wrap(applicationContext).resources
        return localeWrappedResources!!
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        localeResourcesCacheTag = null
        localeWrappedResources = null
        if (rootView != null) {
            rootView = null
            try {
                setInputView(onCreateInputView())
            } catch (_: Exception) {
            }
        }
    }

    // Long-press accent popup state
    private var accentPopup: android.widget.PopupWindow? = null
    private var accentPopupSelectedIndex = -1
    private var accentPopupChars: List<String> = emptyList()
    private var accentPopupViews: List<TextView> = emptyList()
    private var accentPopupAnchor: View? = null
    private var longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var isLongPressActive = false

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
    
    // All 40+ keyboard layouts from shared KeyboardLayouts
    private val keyboardLayouts = KeyboardLayouts.layouts

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
    private var isCapsLocked = false
    private var lastShiftTapTimeMs: Long = 0L
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

    // Emoji picker (nullable for defensive null checks if layout missing)
    private var emojiPickerContainer: View? = null
    private var emojiGridFull: GridLayout? = null
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
    private var dictSearchTextView: TextView? = null
    private var dictClearBtnRef: ImageButton? = null
    private var dictSearchCachedRoot: View? = null
    private var dictSuggestionsScroll: View? = null
    private var dictSuggestionsRow: LinearLayout? = null
    private val dictPredictionHandler = Handler(Looper.getMainLooper())
    private var dictPredictionRunnable: Runnable? = null
    private val dictPredictionDelayMs = 80L
    private var dictCurrentLanguage = "en"
    private var dictTargetLanguage = "ar" // For translation
    private var dictMiniKeyboardLanguage = "en" // Current mini keyboard language
    
    // AI Chat
    private lateinit var aiChatContainer: View
    private var isAiChatVisible = false
    private var aiChatInputText = StringBuilder()
    private var aiChatInputTextView: TextView? = null
    private var aiChatClearBtnRef: ImageButton? = null
    private var aiChatInputCachedRoot: View? = null
    private var aiChatSuggestionsScroll: View? = null
    private var aiChatSuggestionsRow: LinearLayout? = null
    private val aiChatPredictionHandler = Handler(Looper.getMainLooper())
    private var aiChatPredictionRunnable: Runnable? = null
    private val aiChatPredictionDelayMs = 80L
    private val aiChatConnectTimeoutMs = 8000
    private val aiChatReadTimeoutMs = 12000
    private val aiChatEdgeTotalTimeoutMs = 15000L
    private val aiChatMessages = mutableListOf<Pair<String, Boolean>>() // message, isUser
    private var aiConversationHistory = mutableListOf<Map<String, String>>() // For context
    
    // AI Writing Tools
    private lateinit var aiWritingToolsContainer: View
    private var isAiWritingToolsVisible = false

    // Clipboard (Gboard-style)
    private val clipboardHistory = mutableListOf<String>()
    private var clipboardManager: ClipboardManager? = null
    private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private var isClipboardVisible = false
    
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
    private var videoPreviewContainer: View? = null
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
    private var processedVideoFilePath: String? = null       // Muxed video (original visuals + processed audio)
    private var isVideoProcessedAudioReady = false

    override fun onCreateInputView(): View {
        if (rootView != null) {
            // #region agent log
            run {
                val pal = com.deltavoice.theme.KeyboardThemeStore.loadPalette(this)
                com.deltavoice.debug.DebugSession44.log(
                    this, "H4", "MainKeyboardService.onCreateInputView",
                    "returning_cached_rootView",
                    mapOf(
                        "cached" to "true",
                        "currentPrefsBgArgb" to pal.background.toString()
                    )
                )
            }
            // #endregion
            applyOrientationOptimizations(rootView!!)
            return rootView!!
        }
        // #region agent log
        com.deltavoice.debug.DebugSession44.log(
            this, "H4", "MainKeyboardService.onCreateInputView",
            "inflate_and_bind_fresh",
            mapOf("cached" to "false")
        )
        // #endregion
        val view = LayoutInflater.from(this).inflate(R.layout.keyboard_layout, null)
        rootView = view
        bindKeyboardLayout(view)
        return view
    }

    /**
     * Inflate and bind the full keyboard layout without the IME window (for floating-bubble features).
     * Safe to call before the user has opened the keyboard.
     */
    fun ensureKeyboardLayoutInflated(): Boolean {
        if (rootView != null) return true
        return try {
            val view = LayoutInflater.from(this).inflate(R.layout.keyboard_layout, null)
            rootView = view
            bindKeyboardLayout(view)
            true
        } catch (e: Exception) {
            android.util.Log.e("DeltaVoice", "ensureKeyboardLayoutInflated", e)
            false
        }
    }

    private fun bindKeyboardLayout(view: View) {
        // Re-apply orientation-specific UI sizing each time the view is created.
        applyOrientationOptimizations(view)

        // #region agent log
        run {
            val pal = com.deltavoice.theme.KeyboardThemeStore.loadPalette(this)
            com.deltavoice.debug.DebugSession44.log(
                this, "H3", "MainKeyboardService.bindKeyboardLayout",
                "prefs_readable_palette_at_bind",
                mapOf(
                    "bgArgb" to pal.background.toString(),
                    "accentArgb" to pal.accent.toString(),
                    "iconTintArgb" to pal.iconTint.toString(),
                    "theme_apply_in_service" to "unknown"
                )
            )
        }
        // #endregion

        // Load saved keyboard language preference
        loadKeyboardLanguagePreference()

        // Initialize UI components
        voiceButton = view.findViewById(R.id.btn_voice)
        aiMainButton = view.findViewById(R.id.btn_more)  // btn_ai_main not in layout; btn_more toggles AI mode
        keyboardContainer = view.findViewById(R.id.keyboard_container)
        aiFeaturesContainer = view.findViewById(R.id.ai_features_container)
        moreOptionsContainer = view.findViewById(R.id.more_options_container)
        voiceRecordingContainer = view.findViewById(R.id.voice_recording_container)
        voiceProcessingStep2Container = view.findViewById(R.id.voice_processing_step2_container)
        keyboardSpinnerLanguage = view.findViewById(R.id.keyboard_spinner_language)
        keyboardSpinnerVoice = view.findViewById(R.id.keyboard_spinner_voice)
        keyboardCardFull = view.findViewById(R.id.keyboard_option_full)
        keyboardCardVoice = view.findViewById(R.id.keyboard_option_voice)
        keyboardCardText = view.findViewById(R.id.keyboard_option_text)
        recordingStatusText = view.findViewById(R.id.recording_status_text)
        recordingMicButton = view.findViewById(R.id.btn_recording_mic)
        recordingTimerText = view.findViewById(R.id.recording_timer_text)
        playRecordingButton = view.findViewById(R.id.btn_play_recording)
        sendRawRecordingButton = view.findViewById(R.id.btn_send_raw_recording)
        audioDurationText = view.findViewById(R.id.audio_duration_text)
        audioPlaybackSeekBar = view.findViewById(R.id.audio_playback_seekbar)

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

        // Preload key feedback settings to avoid SharedPreferences read on first tap (latency)
        refreshKeyFeedbackSettingsIfNeeded()

        // Initialize Speech Recognizer (fallback)
        initializeSpeechRecognizer()

        // Initialize Text-to-Speech (fallback)
        textToSpeech = TextToSpeech(this, this)

        // Set initial mode
        updateKeyboardMode()
    }

    /**
     * Never enter fullscreen mode. By default InputMethodService goes fullscreen in landscape,
     * replacing the keyboard with an extracted-text view. Returning false keeps our custom
     * keyboard layout visible at all orientations.
     */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        rootView?.let { applyOrientationOptimizations(it) }
        // Apply pending upload state so uploaded media is processed in Step 2 (voice) or Preview & Process (video)
        if (pendingShowVoiceFromUpload && !recordingFilePath.isNullOrBlank()) {
            pendingShowVoiceFromUpload = false
            showVoiceProcessingUI()
            Toast.makeText(this, getString(R.string.ready_for_processing), Toast.LENGTH_SHORT).show()
        }
        if (pendingShowVideoFromUpload && !videoFilePath.isNullOrBlank()) {
            pendingShowVideoFromUpload = false
            showVideoPreview()
            Toast.makeText(this, getString(R.string.ready_for_processing), Toast.LENGTH_SHORT).show()
        }
        // Reload saved language and rebuild layout when keyboard is shown
        loadKeyboardLanguagePreference()
        spaceBarButton?.text = currentKeyboardLanguageName
        rebuildKeyboardLayout()
        // #region agent log
        run {
            val pal = com.deltavoice.theme.KeyboardThemeStore.loadPalette(this)
            com.deltavoice.debug.DebugSession44.log(
                this, "H5", "MainKeyboardService.onStartInputView",
                "after_rebuild_loadPalette",
                mapOf(
                    "bgArgb" to pal.background.toString(),
                    "accentArgb" to pal.accent.toString(),
                    "restarting" to restarting.toString()
                )
            )
        }
        // #endregion
        // Enable background blur and transparency (API 31+)
        applyBlurAndTransparency()
    }

    /**
     * Apply window blur and transparency for Liquid Glass effect.
     * Blur requires API 31+; transparency works on all versions.
     * Note: InputMethodService.window returns a Dialog (SoftInputWindow); use .window to get the actual Window.
     */
    private fun applyBlurAndTransparency() {
        val w = (window as? android.app.Dialog)?.window ?: return
        w.setBackgroundDrawableResource(R.drawable.transparent_background)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            applyBlurRadius(w)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun applyBlurRadius(window: android.view.Window) {
        window.setBackgroundBlurRadius(80)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        // Release camera immediately when keyboard is hidden so status bar icon disappears
        if (isVideoRecordingVisible) {
            hideVideoRecording()
        }
    }

    private var videoUploadReceiver: BroadcastReceiver? = null
    private var audioUploadReceiver: BroadcastReceiver? = null
    private var pendingShowVoiceFromUpload = false
    private var pendingShowVideoFromUpload = false

    override fun onCreate() {
        if (!isStandaloneOverlayHost) {
            standaloneOverlayInstance?.cleanupBeforeImeTakeover()
            standaloneOverlayInstance = null
        }
        super.onCreate()
        serviceInstance = this
        if (isStandaloneOverlayHost) {
            standaloneOverlayInstance = this
        }
        videoUploadReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != VideoUploadActivity.ACTION_VIDEO_UPLOADED) return
                val path = getSharedPreferences(VideoUploadActivity.PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(VideoUploadActivity.KEY_PENDING_PATH, null)
                getSharedPreferences(VideoUploadActivity.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .remove(VideoUploadActivity.KEY_PENDING_PATH)
                    .apply()
                path?.let { p ->
                    videoFilePath = p
                    if (rootView?.isAttachedToWindow == true) {
                        pendingShowVideoFromUpload = false
                        showVideoPreview()
                    } else {
                        pendingShowVideoFromUpload = true
                    }
                    scheduleKeyboardShowAfterUpload()
                }
            }
        }
        audioUploadReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != AudioUploadActivity.ACTION_AUDIO_UPLOADED) return
                val path = getSharedPreferences(AudioUploadActivity.PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(AudioUploadActivity.KEY_PENDING_PATH, null)
                getSharedPreferences(AudioUploadActivity.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .remove(AudioUploadActivity.KEY_PENDING_PATH)
                    .apply()
                path?.let { p ->
                    recordingFilePath = p
                    if (rootView?.isAttachedToWindow == true) {
                        pendingShowVoiceFromUpload = false
                        showVoiceProcessingUI()
                    } else {
                        pendingShowVoiceFromUpload = true
                    }
                    scheduleKeyboardShowAfterUpload()
                }
            }
        }
        val videoFilter = IntentFilter(VideoUploadActivity.ACTION_VIDEO_UPLOADED)
        val audioFilter = IntentFilter(AudioUploadActivity.ACTION_AUDIO_UPLOADED)
        ContextCompat.registerReceiver(this, videoUploadReceiver, videoFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, audioUploadReceiver, audioFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        // Clipboard history: capture copies from any app
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        loadClipboardHistory()
        clipboardListener = ClipboardManager.OnPrimaryClipChangedListener { syncCurrentClipboardToHistory() }
        clipboardManager?.addPrimaryClipChangedListener(clipboardListener!!)
    }

    /**
     * Schedule multiple requestShowSelf calls so keyboard reliably appears after upload activity
     * finishes. Activity finishes at 750ms; we call before and after to catch the transition.
     */
    private fun scheduleKeyboardShowAfterUpload() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val handler = Handler(Looper.getMainLooper())
        val delays = longArrayOf(150, 400, 700, 1000, 1300)
        for (delay in delays) {
            handler.postDelayed({
                try {
                    requestShowSelf(InputMethodManager.SHOW_FORCED)
                } catch (_: Exception) { }
            }, delay)
        }
    }

    /**
     * Apply orientation-specific sizing so reused IME views do not keep stale values.
     */
    private fun applyOrientationOptimizations(view: View) {
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            applyLandscapeOptimizations(view)
        } else {
            applyPortraitOptimizations(view)
        }
    }

    /**
     * Apply landscape-specific layout optimizations.
     * The dedicated layout-land/keyboard_layout.xml handles most sizing;
     * these adjustments fine-tune anything that must be set programmatically.
     */
    private fun applyLandscapeOptimizations(view: View) {
        // layout-land XML already sets correct sizes; no extra programmatic overrides needed.
        // Request the window to not push up when soft input is shown (keyboard IS the input).
    }

    /**
     * Restore portrait defaults when orientation returns to portrait.
     */
    private fun applyPortraitOptimizations(view: View) {
        // layout/keyboard_layout.xml (portrait) already sets correct sizes.
    }

    private fun hideTopBarsForOverlay() {
        if (overlayBubbleKeyboardIsolated) return
        rootView?.findViewById<View>(R.id.top_bar_container)?.visibility = View.GONE
        rootView?.findViewById<View>(R.id.ai_features_row)?.visibility = View.GONE
        rootView?.findViewById<View>(R.id.predictions_container)?.visibility = View.GONE
    }

    private fun showTopBarsAfterOverlay() {
        if (overlayBubbleKeyboardIsolated) return
        rootView?.findViewById<View>(R.id.top_bar_container)?.visibility = View.VISIBLE
        rootView?.findViewById<View>(R.id.ai_features_row)?.visibility = View.VISIBLE
        rootView?.findViewById<View>(R.id.predictions_container)?.visibility = View.GONE
    }

    private fun startRecordingTimer() {
        recordingTimerRunnable?.let { recordingUiHandler.removeCallbacks(it) }
        recordingStartTimeMs = SystemClock.elapsedRealtime()
        updateRecordingTimerText(0L)
        val runnable = object : Runnable {
            override fun run() {
                if (!isRecording) return
                val elapsedMs = SystemClock.elapsedRealtime() - recordingStartTimeMs
                updateRecordingTimerText(elapsedMs)
                recordingUiHandler.postDelayed(this, 500L)
            }
        }
        recordingTimerRunnable = runnable
        recordingUiHandler.post(runnable)
    }

    private fun stopRecordingTimer(reset: Boolean) {
        recordingTimerRunnable?.let { recordingUiHandler.removeCallbacks(it) }
        recordingTimerRunnable = null
        if (reset) {
            updateRecordingTimerText(0L)
        }
    }

    private fun updateRecordingTimerText(elapsedMs: Long) {
        if (!::recordingTimerText.isInitialized) return
        val totalSeconds = elapsedMs / 1000
        val minutes = (totalSeconds / 60).toInt()
        val seconds = (totalSeconds % 60).toInt()
        recordingTimerText.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun updateRecordingMicColor(recording: Boolean) {
        if (!::recordingMicButton.isInitialized) return
        val drawable = recordingMicButton.drawable ?: return
        val color = if (recording) {
            Color.parseColor("#4CAF50") // green
        } else {
            Color.WHITE
        }
        drawable.mutate().setTint(color)
        recordingMicButton.setImageDrawable(drawable)
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
                recordingStatusText.text = getString(R.string.recording_tap_to_pause)
            }
        }

        // Upload button - pick audio from device
        view.findViewById<ImageButton>(R.id.btn_voice_upload)?.setOnClickListener {
            launchAudioUploadPicker()
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
                    Toast.makeText(this, getString(R.string.please_wait_processing), Toast.LENGTH_SHORT).show()
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

        // Seekbar - allow user to scrub through audio
        audioPlaybackSeekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    playbackMediaPlayer?.seekTo(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        // Send original recording immediately (without any processing)
        sendRawRecordingButton.setOnClickListener {
            shareOriginalRecordedAudio()
        }
    }

    private fun updateKeyboardModeSelection(workflowType: String) {
        pendingWorkflowType = workflowType
        val selected = R.drawable.voice_mode_card_selected
        val unselected = R.drawable.voice_mode_card_unselected

        keyboardCardFull.setBackgroundResource(if (workflowType == "complete") selected else unselected)
        keyboardCardVoice.setBackgroundResource(if (workflowType == "voice-only") selected else unselected)
        keyboardCardText.setBackgroundResource(if (workflowType == "text-only") selected else unselected)
        
        // Scope pills/icons to Step 2 (keyboard + overlay each have their own included layout)
        if (!::voiceProcessingStep2Container.isInitialized) return
        val scope = voiceProcessingStep2Container
        run {
            val languageContainer = scope.findViewById<LinearLayout>(R.id.language_spinner_container)
            val voiceContainer = scope.findViewById<LinearLayout>(R.id.voice_spinner_container)
            val languageIcon = scope.findViewById<ImageView>(R.id.language_icon)
            val voiceIcon = scope.findViewById<ImageView>(R.id.voice_icon)

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
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
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
                OutboundHttpPolicy.applyTo(connection)
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
     * Show a network error notification. Keeps Process button as "Done" so user can
     * tap again when internet is restored. Registers NetworkCallback to notify when
     * connectivity returns.
     */
    private fun showNetworkErrorWithSettings(message: String) {
        android.util.Log.e("DeltaVoice", "Network error: $message")
        
        Toast.makeText(
            this,
            "📶 $message\nRetry when internet is restored (Done or Process Video).",
            Toast.LENGTH_LONG
        ).show()
        
        // Voice Step 2 (keyboard or bubble overlay): bound to [voiceProcessingStep2Container], not [rootView].
        if (isVoiceUiActive()) {
            audioDurationText.text = getString(R.string.status_no_internet)
            voiceStep2ActionButton()?.apply {
                isEnabled = true
                text = "  Done"
                background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.voice_mode_button_purple)
                setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_ai_mode, 0, 0, 0)
                compoundDrawables.forEach { drawable -> drawable?.setTint(Color.WHITE) }
            }
        }
        // Video preview (keyboard or overlay): use active [videoUiContent], not root-only ids.
        if (isVideoPreviewVisible) {
            resetVideoProcessButton()
        }

        registerNetworkRestoredCallback()
    }

    /**
     * Register callback to notify when internet is restored (API 24+)
     */
    private fun registerNetworkRestoredCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        unregisterNetworkRestoredCallback()
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                    Handler(Looper.getMainLooper()).post {
                        when {
                            isVoiceUiActive() -> {
                                Toast.makeText(this@MainKeyboardService, getString(R.string.internet_restored_tap_done), Toast.LENGTH_SHORT).show()
                                audioDurationText.text = getString(R.string.ready_tap_done_process)
                            }
                            isVideoPreviewVisible -> {
                                Toast.makeText(this@MainKeyboardService, getString(R.string.internet_restored_tap_process_video), Toast.LENGTH_SHORT).show()
                            }
                            else -> return@post
                        }
                        unregisterNetworkRestoredCallback()
                    }
                }
            }
        }
        try {
            cm.registerNetworkCallback(networkRequest!!, networkCallback!!)
        } catch (e: Exception) {
            android.util.Log.w("DeltaVoice", "Could not register network callback", e)
        }
    }

    private fun unregisterNetworkRestoredCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        networkCallback?.let { callback ->
            try {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                networkRequest?.let { cm.unregisterNetworkCallback(callback) }
            } catch (e: Exception) { /* ignore */ }
        }
        networkCallback = null
        networkRequest = null
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
            Toast.makeText(this, getString(R.string.no_recording_found), Toast.LENGTH_LONG).show()
            return
        }

        val audioFile = File(audioPath)
        android.util.Log.d("DeltaVoice", "Audio file: exists=${audioFile.exists()}, size=${audioFile.length()}")
        
        if (!audioFile.exists() || audioFile.length() == 0L) {
            android.util.Log.e("DeltaVoice", "ERROR: Audio file doesn't exist or is empty!")
            Toast.makeText(this, getString(R.string.recording_empty), Toast.LENGTH_LONG).show()
            recordingFilePath = null
            return
        }

        // Voice cloning quality depends heavily on sample length.
        // Gate voice-only mode to reduce low-fidelity clone outputs.
        if (workflowType == "voice-only") {
            val durationSeconds = getAudioDurationSeconds(audioPath)
            if (durationSeconds in 0..7) {
                Toast.makeText(
                    this,
                    "For better voice cloning, record at least 8 seconds of clear speech.",
                    Toast.LENGTH_LONG
                ).show()
                audioDurationText.text = getString(R.string.status_need_8_seconds)
                voiceStep2ActionButton()?.apply {
                    isEnabled = true
                    text = "  Done"
                }
                return
            }
        }

        // Mode-specific options — each workflow uses its own path (no shared state):
        // • complete: preset voice (Adam, Aria, etc.) from spinner
        // • voice-only: always "myvoiceclone" — backend clones from this recording
        // • text-only: voice unused (text output only)
        val targetLang = languages.getOrNull(keyboardSpinnerLanguage.selectedItemPosition)?.second ?: "en"
        val selectedVoiceStyle = voiceStyles.getOrNull(keyboardSpinnerVoice.selectedItemPosition)?.second ?: "adam"
        val selectedVoiceStyleName = voiceStyles.getOrNull(keyboardSpinnerVoice.selectedItemPosition)?.first ?: "Adam"
        val voiceStyle = when (workflowType) {
            "voice-only" -> "myvoiceclone"  // Translate My Same Voice: clone from recording
            else -> selectedVoiceStyle       // Change Language & Voice: use selected preset
        }
        val voiceStyleName = when (workflowType) {
            "voice-only" -> "My Voice Clone"
            else -> selectedVoiceStyleName
        }
        val languageName = languages.getOrNull(keyboardSpinnerLanguage.selectedItemPosition)?.first ?: "English"

        android.util.Log.d("DeltaVoice", "Options: workflow=$workflowType, lang=$targetLang ($languageName), voice=$voiceStyle ($voiceStyleName)")

        // Update UI for processing state - show loading
        voiceStep2ActionButton()?.apply {
            isEnabled = false
            text = "  Processing..."
            background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.voice_mode_button_purple)
        }
        audioDurationText.text = getString(R.string.status_loading)
        
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
        // Ensure voice UI is exclusive and no other panel can overlap it.
        hideAllOverlays()
        hideTopBarsForOverlay()
        applyImeKeyboardContainerVisible(false)
        voiceRecordingContainer.visibility = View.VISIBLE
        stopRecordingTimer(reset = true)
        updateRecordingMicColor(false)
        recordingStatusText.text = getString(R.string.tap_to_record)
    }
    
    private fun hideRecordingUI() {
        // Hide recording UI, show keyboard keys
        voiceRecordingContainer.visibility = View.GONE
        emojiPickerContainer?.visibility = View.GONE
        isEmojiPickerVisible = false
        calculatorContainer.visibility = View.GONE
        isCalculatorVisible = false
        dictionaryContainer.visibility = View.GONE
        isDictionaryVisible = false
        aiChatContainer.visibility = View.GONE
        isAiChatVisible = false
        aiWritingToolsContainer.visibility = View.GONE
        isAiWritingToolsVisible = false
        applyImeKeyboardContainerVisible(true)
        showTopBarsAfterOverlay()
        updateRecordingMicColor(false)
        stopRecordingTimer(reset = true)
    }
    
    /**
     * Launch the audio upload picker (opens AudioUploadActivity).
     */
    private fun launchAudioUploadPicker() {
        val intent = Intent(this, AudioUploadActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
            putExtra(AudioUploadActivity.EXTRA_LAUNCHED_FROM, AudioUploadActivity.LAUNCHED_FROM_KEYBOARD)
        }
        startActivity(intent)
    }

    /**
     * Show voice processing UI with an uploaded audio path (called when AudioUploadActivity finishes).
     */
    private fun showVoiceProcessingWithPath(path: String) {
        recordingFilePath = path
        showVoiceProcessingUI()
    }

    private fun showVoiceProcessingUI() {
        android.util.Log.d("DeltaVoice", "showVoiceProcessingUI() - recordingFilePath=$recordingFilePath")
        
        // Ensure processing UI is exclusive and no other panel can overlap it.
        hideAllOverlays()
        hideTopBarsForOverlay()
        applyImeKeyboardContainerVisible(false)
        voiceProcessingStep2Container.visibility = View.VISIBLE
        stopRecordingTimer(reset = false)
        updateRecordingMicColor(false)
        
        // Default to "Change Language & Voice" (complete) when showing Step 2
        updateKeyboardModeSelection("complete")
        
        // Reset processed audio state when showing UI
        clearProcessedAudio()
        
        // Update the audio duration display with original recording
        updateAudioDuration(recordingFilePath)
        
        // Reset button states
        voiceStep2ActionButton()?.apply {
            isEnabled = true
            text = "  Done"
            background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.voice_mode_button_purple)
        }
        
        android.util.Log.d("DeltaVoice", "Processing UI shown successfully")
    }
    
    private fun hideVoiceProcessingUI() {
        // Hide processing UI, show keyboard keys
        unregisterNetworkRestoredCallback()
        stopRecordingPlayback()
        voiceProcessingStep2Container.visibility = View.GONE
        updateRecordingMicColor(false)
        emojiPickerContainer?.visibility = View.GONE
        isEmojiPickerVisible = false
        calculatorContainer.visibility = View.GONE
        isCalculatorVisible = false
        dictionaryContainer.visibility = View.GONE
        isDictionaryVisible = false
        aiChatContainer.visibility = View.GONE
        isAiChatVisible = false
        aiWritingToolsContainer.visibility = View.GONE
        isAiWritingToolsVisible = false
        applyImeKeyboardContainerVisible(true)
        showTopBarsAfterOverlay()
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
            Toast.makeText(this, getString(R.string.no_audio_to_play_short), Toast.LENGTH_SHORT).show()
            return
        }
        
        val audioFile = File(audioPath)
        if (!audioFile.exists()) {
            Toast.makeText(this, getString(R.string.audio_file_not_found), Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            // Release any existing player
            playbackMediaPlayer?.release()
            
            playbackMediaPlayer = MediaPlayer().apply {
                setDataSource(audioPath)
                prepare()
                
                if (::audioPlaybackSeekBar.isInitialized) {
                    audioPlaybackSeekBar.max = duration.coerceAtLeast(1)
                    audioPlaybackSeekBar.progress = 0
                }
                
                setOnCompletionListener {
                    isPlayingRecording = false
                    playRecordingButton.setImageResource(R.drawable.ic_play)
                    stopSeekBarUpdater()
                    if (::audioPlaybackSeekBar.isInitialized) {
                        audioPlaybackSeekBar.progress = 0
                    }
                }
                
                start()
            }
            
            isPlayingRecording = true
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
                val player = playbackMediaPlayer ?: return
                if (!isPlayingRecording) return
                try {
                    if (::audioPlaybackSeekBar.isInitialized) {
                        audioPlaybackSeekBar.progress = player.currentPosition
                    }
                } catch (_: Exception) {}
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
    
    /**
     * Stop playback of recorded audio
     */
    private fun stopRecordingPlayback() {
        stopSeekBarUpdater()
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
        if (::audioPlaybackSeekBar.isInitialized) {
            audioPlaybackSeekBar.progress = 0
        }
    }
    
    /**
     * Update the audio duration text from a file.
     * Runs MediaPlayer.prepare() on IO thread to avoid blocking the main thread.
     */
    private fun updateAudioDuration(audioPath: String?) {
        if (audioPath.isNullOrBlank()) {
            audioDurationText.text = "0:00"
            audioTotalDurationMs = 0
            if (::audioPlaybackSeekBar.isInitialized) {
                audioPlaybackSeekBar.max = 100
                audioPlaybackSeekBar.progress = 0
            }
            return
        }
        serviceScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val mp = MediaPlayer()
                    mp.setDataSource(audioPath)
                    mp.prepare()
                    val durationMs = mp.duration
                    mp.release()
                    val seconds = (durationMs / 1000) % 60
                    val minutes = (durationMs / 1000) / 60
                    Pair(String.format("%d:%02d", minutes, seconds), durationMs)
                } catch (e: Exception) {
                    Pair("0:00", 0)
                }
            }
            audioDurationText.text = result.first
            audioTotalDurationMs = result.second
            if (::audioPlaybackSeekBar.isInitialized) {
                audioPlaybackSeekBar.max = if (result.second > 0) result.second else 100
                audioPlaybackSeekBar.progress = 0
            }
        }
    }

    /**
     * Safely read audio duration in seconds. Returns 0 on failure.
     */
    private fun getAudioDurationSeconds(audioPath: String?): Int {
        if (audioPath.isNullOrBlank()) return 0
        return try {
            val mediaPlayer = MediaPlayer()
            mediaPlayer.setDataSource(audioPath)
            mediaPlayer.prepare()
            val durationSeconds = mediaPlayer.duration / 1000
            mediaPlayer.release()
            durationSeconds
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * Save processed audio and update the audio player to show it
     */
    private fun saveAndShowProcessedAudio(base64Audio: String, extension: String) {
        // Validate input
        if (base64Audio.isBlank()) {
            Toast.makeText(this@MainKeyboardService, getString(R.string.no_audio_data_received), Toast.LENGTH_SHORT).show()
            resetButtonState()
            return
        }
        
        serviceScope.launch {
            try {
                // Decode base64 audio
                val audioBytes = try {
                    Base64.decode(base64Audio, Base64.DEFAULT)
                } catch (e: Exception) {
                    Toast.makeText(this@MainKeyboardService, getString(R.string.invalid_audio_format), Toast.LENGTH_SHORT).show()
                    resetButtonState()
                    return@launch
                }
                
                if (audioBytes.isEmpty() || audioBytes.size < 100) {
                    Toast.makeText(this@MainKeyboardService, getString(R.string.audio_empty_too_small), Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this@MainKeyboardService, getString(R.string.failed_save_audio), Toast.LENGTH_SHORT).show()
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
                    if (::voiceProcessingStep2Container.isInitialized) {
                        voiceProcessingStep2Container.findViewById<View>(R.id.audio_player_container)?.visibility =
                            View.VISIBLE
                    }

                    // Update button states - change to Send mode
                    voiceStep2ActionButton()?.apply {
                        isEnabled = true
                        text = "  Send"
                        background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.voice_mode_button_green)
                        setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_send, 0, 0, 0)
                        compoundDrawables.forEach { drawable ->
                            drawable?.setTint(Color.WHITE)
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
                    Toast.makeText(this@MainKeyboardService, getString(R.string.error_message, e.message ?: ""), Toast.LENGTH_LONG).show()
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
            Toast.makeText(this, getString(R.string.no_processed_audio_send), Toast.LENGTH_SHORT).show()
            return
        }
        val audioFile = File(audioPath)
        if (!audioFile.exists()) {
            Toast.makeText(this, getString(R.string.audio_file_not_found), Toast.LENGTH_SHORT).show()
            return
        }
        shareAudioFile(audioFile, "Send voice message via") {
            hideVoiceProcessingUI()
            clearProcessedAudioStateOnly()
            recordingFilePath = null
        }
    }

    private fun shareOriginalRecordedAudio() {
        val rawPath = recordingFilePath
        if (rawPath.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.no_recording_found_short), Toast.LENGTH_SHORT).show()
            return
        }

        val rawAudioFile = File(rawPath)
        if (!rawAudioFile.exists() || rawAudioFile.length() == 0L) {
            Toast.makeText(this, getString(R.string.recording_file_not_found), Toast.LENGTH_SHORT).show()
            return
        }

        stopRecordingPlayback()
        shareAudioFile(rawAudioFile, "Send original voice via") {
            hideVoiceProcessingUI()
            clearProcessedAudio()
            recordingFilePath = null
        }
    }
    
    /**
     * Try to send a file directly into the current chat via commitContent (API 25+).
     * This inserts the media into the active input field without a share chooser.
     * Returns true if the content was committed successfully.
     */
    private fun trySendContentDirectly(file: File, mimeType: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return false
        try {
            val ic = currentInputConnection ?: return false
            val editorInfo = currentInputEditorInfo ?: return false

            val supportedMimes = androidx.core.view.inputmethod.EditorInfoCompat.getContentMimeTypes(editorInfo)
            val supported = supportedMimes.any { supported ->
                android.content.ClipDescription.compareMimeTypes(mimeType, supported)
            }
            if (!supported) return false

            val uri = androidx.core.content.FileProvider.getUriForFile(
                this, "${packageName}.fileprovider", file
            )
            grantUriPermission(editorInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

            val contentInfo = androidx.core.view.inputmethod.InputContentInfoCompat(
                uri,
                android.content.ClipDescription("", arrayOf(mimeType)),
                null
            )
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1)
                androidx.core.view.inputmethod.InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION else 0

            val committed = androidx.core.view.inputmethod.InputConnectionCompat.commitContent(
                ic, editorInfo, contentInfo, flags, null
            )
            if (committed) {
                android.util.Log.d("DeltaVoice", "Content committed directly to chat: $mimeType")
            }
            return committed
        } catch (e: Exception) {
            android.util.Log.w("DeltaVoice", "commitContent failed: ${e.message}")
            return false
        }
    }

    private fun mimeForSharedAudio(file: File): String {
        return when (file.extension.lowercase(Locale.US)) {
            "m4a", "aac", "mp4" -> "audio/mp4"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            else -> "audio/mpeg"
        }
    }

    /**
     * Share an audio file directly to the current chat, or fall back to share chooser.
     */
    private fun shareAudioFile(audioFile: File, chooserTitle: String, onComplete: () -> Unit = {}) {
        if (!audioFile.exists()) {
            Toast.makeText(this, getString(R.string.share_failed_message), Toast.LENGTH_LONG).show()
            return
        }
        serviceScope.launch {
            val toShare = withContext(Dispatchers.IO) {
                OutboundMediaSanitizer.sanitizeAudioForOutbound(audioFile)
            }
            if (!toShare.exists()) {
                Toast.makeText(this@MainKeyboardService, getString(R.string.share_failed_message), Toast.LENGTH_LONG).show()
                return@launch
            }
            val mime = mimeForSharedAudio(toShare)
            if (trySendContentDirectly(toShare, mime)) {
                Toast.makeText(this@MainKeyboardService, getString(R.string.sent_to_chat), Toast.LENGTH_SHORT).show()
                onComplete()
                Handler(mainLooper).postDelayed({ try { toShare.delete() } catch (_: Exception) { } }, 45000)
                return@launch
            }
            try {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    this@MainKeyboardService, "${packageName}.fileprovider", toShare
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    clipData = android.content.ClipData.newUri(contentResolver, "audio", uri)
                }
                val chooserIntent = Intent.createChooser(shareIntent, chooserTitle).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(chooserIntent)
                onComplete()
                Handler(mainLooper).postDelayed({ try { toShare.delete() } catch (_: Exception) { } }, 45000)
            } catch (e: Exception) {
                android.util.Log.e("DeltaVoice", "Share audio failed", e)
                Toast.makeText(this@MainKeyboardService, getString(R.string.share_failed_message), Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Save video to MediaStore so Messenger, WhatsApp, Telegram etc. can read it.
     * MediaStore URIs are more reliably accepted by messaging apps than FileProvider cache URIs.
     * Returns the content URI, or null if MediaStore save fails.
     */
    private fun saveVideoToMediaStore(videoFile: File): android.net.Uri? {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "v_${UUID.randomUUID()}.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/DeltaVoice")
                }
            }
            val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { output ->
                    videoFile.inputStream().use { input -> input.copyTo(output) }
                }
                uri
            } else null
        } catch (e: Exception) {
            android.util.Log.w("DeltaVoice", "MediaStore save failed, will use FileProvider: ${e.message}")
            null
        }
    }

    /**
     * Share a video file directly to the current chat, or fall back to share chooser.
     * Prefers MediaStore URI (best compatibility with Messenger, WhatsApp, Telegram).
     * Falls back to FileProvider if MediaStore fails.
     */
    private fun shareVideoFile(videoFile: File, chooserTitle: String, onComplete: () -> Unit = {}) {
        if (!videoFile.exists()) {
            Toast.makeText(this, getString(R.string.share_failed_message), Toast.LENGTH_LONG).show()
            return
        }
        serviceScope.launch {
            val toShare = withContext(Dispatchers.IO) {
                OutboundMediaSanitizer.sanitizeVideoForOutbound(videoFile) ?: videoFile
            }
            if (!toShare.exists()) {
                Toast.makeText(this@MainKeyboardService, getString(R.string.share_failed_message), Toast.LENGTH_LONG).show()
                return@launch
            }
            if (trySendContentDirectly(toShare, "video/mp4")) {
                Toast.makeText(this@MainKeyboardService, getString(R.string.sent_to_chat), Toast.LENGTH_SHORT).show()
                onComplete()
                Handler(mainLooper).postDelayed({ try { toShare.delete() } catch (_: Exception) { } }, 300000)
                return@launch
            }
            try {
                // Prefer MediaStore: messaging apps accept content://media/... URIs more reliably
                var uri: android.net.Uri? = saveVideoToMediaStore(toShare)
                var fileToCleanup: File? = null
                if (uri == null) {
                    val shareCopy = File(cacheDir, "share_v_${UUID.randomUUID()}.mp4")
                    toShare.copyTo(shareCopy, overwrite = true)
                    uri = androidx.core.content.FileProvider.getUriForFile(
                        this@MainKeyboardService, "${packageName}.fileprovider", shareCopy
                    )
                    fileToCleanup = shareCopy
                }
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    clipData = android.content.ClipData.newUri(contentResolver, "video", uri)
                }
                val chooserIntent = Intent.createChooser(shareIntent, chooserTitle).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(chooserIntent)
                onComplete()
                fileToCleanup?.let { f ->
                    Handler(mainLooper).postDelayed({ try { f.delete() } catch (_: Exception) { } }, 300000)
                }
            } catch (e: Exception) {
                android.util.Log.e("DeltaVoice", "Share video failed", e)
                Toast.makeText(this@MainKeyboardService, getString(R.string.share_failed_message), Toast.LENGTH_LONG).show()
            }
        }
    }
    
    /**
     * Clear processed audio state (resets UI only; file is deleted by shareAudioFile after delay when sharing)
     */
    private fun clearProcessedAudioStateOnly() {
        processedAudioFilePath = null
        isProcessedAudioReady = false
        voiceStep2ActionButton()?.apply {
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
        stopRecordingTimer(reset = true)
        updateRecordingMicColor(false)
        recordingStatusText.text = getString(R.string.tap_to_record)
        
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
        
        // AI Main Button (three dots) - Show Calculator & Dictionary
        aiMainButton.setOnClickListener {
            playKeyFeedback(it)
            showMoreOptions()
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
                    Toast.makeText(this, getString(R.string.stop_voice_input_first), Toast.LENGTH_SHORT).show()
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
        // Get AI feature buttons from layout (calculator/dictionary are in more_options_container)
        val calculatorButton = view.findViewById<ImageButton>(R.id.btn_more_calculator)
        val cameraButton = view.findViewById<ImageButton>(R.id.btn_camera)
        val listButton = view.findViewById<ImageButton>(R.id.btn_list)
        val textTButton = view.findViewById<ImageButton>(R.id.btn_text_t)
        val kbPlusButton = view.findViewById<ImageButton>(R.id.btn_kb_plus)
        val dictionaryButton = view.findViewById<ImageButton>(R.id.btn_more_dictionary)
        val appGridButton = view.findViewById<ImageButton>(R.id.btn_app_grid)
        val moreVoiceButton = view.findViewById<ImageButton>(R.id.btn_more_voice)
        val moreVideoButton = view.findViewById<ImageButton>(R.id.btn_more_video)
        val moreAiChatButton = view.findViewById<ImageButton>(R.id.btn_more_ai_chat)
        val moreClipboardButton = view.findViewById<ImageButton>(R.id.btn_more_clipboard)
        val moreKbPlusButton = view.findViewById<ImageButton>(R.id.btn_more_kb_plus)
        val moreThreeDotButton = view.findViewById<ImageButton>(R.id.btn_more_three_dot)

        // Prevent focus on all AI buttons
        listOf(
            calculatorButton, cameraButton, listButton, textTButton, kbPlusButton, dictionaryButton, appGridButton,
            moreVoiceButton, moreVideoButton, moreAiChatButton, moreClipboardButton, moreKbPlusButton, moreThreeDotButton
        )
            .forEach { button ->
                button?.isFocusable = false
                button?.isFocusableInTouchMode = false
            }

        moreVoiceButton?.setOnClickListener {
            playKeyFeedback(it)
            if (isListening) stopVoiceInput()
            hideMoreOptions()
            showRecordingUI()
        }

        moreVideoButton?.setOnClickListener {
            playKeyFeedback(it)
            hideMoreOptions()
            toggleVideoRecording()
        }

        moreAiChatButton?.setOnClickListener {
            playKeyFeedback(it)
            hideMoreOptions()
            toggleAiChat()
        }

        moreClipboardButton?.setOnClickListener {
            playKeyFeedback(it)
            hideMoreOptions()
            toggleClipboardPopup()
        }

        moreKbPlusButton?.setOnClickListener {
            playKeyFeedback(it)
            hideMoreOptions()
            toggleAiWritingTools()
        }

        moreThreeDotButton?.setOnClickListener {
            playKeyFeedback(it)
            hideMoreOptions()
            openKeyboardSettings()
        }

        // Dictionary button (in more options)
        dictionaryButton?.setOnClickListener {
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
            playKeyFeedback(it)
            if (moreOptionsContainer.visibility == View.VISIBLE) {
                hideMoreOptions()
            }
            toggleClipboardPopup()
        }

        // Back to keyboard (from more options)
        view.findViewById<Button>(R.id.btn_more_back)?.setOnClickListener {
            playKeyFeedback(it)
            hideMoreOptions()
        }
    }

    /**
     * Setup full emoji picker with categories
     */
    private fun setupEmojiPicker(view: View) {
        emojiPickerContainer = view.findViewById(R.id.emoji_picker_include)
        emojiGridFull = view.findViewById(R.id.emoji_grid_full)
        if (emojiPickerContainer == null || emojiGridFull == null) return  // Defensive: skip if layout missing
        
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
        val grid = emojiGridFull ?: return
        grid.removeAllViews()
        
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
            grid.addView(placeholder)
            return
        }
        
        // Configure grid
        grid.columnCount = 8

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
            grid.addView(button)
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
     * Show the emoji picker - sized to ~half screen like messaging apps
     */
    private fun showEmojiPicker() {
        applyImeKeyboardContainerVisible(false)
        emojiPickerContainer?.let { container ->
            // Size to half screen (like WhatsApp/Telegram emoji picker)
            val halfScreenPx = resources.displayMetrics.heightPixels / 2
            val lp = container.layoutParams ?: LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                halfScreenPx
            )
            lp.height = halfScreenPx
            container.layoutParams = lp
            container.visibility = View.VISIBLE
        }
        isEmojiPickerVisible = true
        // Refresh current category
        loadEmojiCategory(currentEmojiCategory)
        updateCategoryTabSelection(currentEmojiCategory)
    }
    
    /**
     * Hide the emoji picker
     */
    private fun hideEmojiPicker() {
        emojiPickerContainer?.visibility = View.GONE
        applyImeKeyboardContainerVisible(true)
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
        Toast.makeText(this, getString(R.string.inserted, calcResult), Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Show the calculator
     */
    private fun showCalculator() {
        hideAllOverlays()
        hideTopBarsForOverlay()
        applyImeKeyboardContainerVisible(false)
        calculatorContainer.visibility = View.VISIBLE
        isCalculatorVisible = true
        updateCalculatorDisplay()
    }
    
    /**
     * Hide the calculator
     */
    private fun hideCalculator() {
        calculatorContainer.visibility = View.GONE
        applyImeKeyboardContainerVisible(true)
        showTopBarsAfterOverlay()
        isCalculatorVisible = false
    }
    
    /**
     * Toggle calculator visibility
     */
    private fun toggleCalculator() {
        if (isVoiceUiActive()) {
            Toast.makeText(this, getString(R.string.finish_voice_input_first), Toast.LENGTH_SHORT).show()
            return
        }
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

        view.findViewById<ImageButton>(R.id.ai_tools_close_btn)?.setOnClickListener {
            hideAiWritingTools()
        }
        
        view.findViewById<Button>(R.id.ai_tool_grammar)?.setOnClickListener {
            runToolInChat("grammar", null, "Fix grammar", "No text to correct")
        }
        view.findViewById<Button>(R.id.ai_tool_reply)?.setOnClickListener {
            runToolInChat("reply", null, "Write a reply", "No text to reply to")
        }
        view.findViewById<Button>(R.id.ai_tool_translate)?.setOnClickListener {
            showTranslateLanguagePicker(it)
        }
        view.findViewById<Button>(R.id.ai_tool_enhance)?.setOnClickListener {
            runToolInChat("enhance", null, "Enhance words", "No text to enhance")
        }
        view.findViewById<Button>(R.id.ai_tool_tone)?.setOnClickListener {
            showTonePicker(it)
        }
        view.findViewById<Button>(R.id.ai_tool_paraphrase)?.setOnClickListener {
            runToolInChat("paraphrase", null, "Paraphrase", "No text to paraphrase")
        }
        view.findViewById<Button>(R.id.ai_tool_continue)?.setOnClickListener {
            runToolInChat("continue", null, "Continue writing", "No text to continue")
        }
        view.findViewById<Button>(R.id.ai_tool_longer)?.setOnClickListener {
            runToolInChat("longer", null, "Make longer", "No text to expand")
        }
        view.findViewById<Button>(R.id.ai_tool_summarize)?.setOnClickListener {
            runToolInChat("summarize", null, "Summarize", "No text to summarize")
        }
        view.findViewById<Button>(R.id.ai_tool_synonymous)?.setOnClickListener {
            runToolInChat("synonyms", null, "Find synonyms", "No text to reword")
        }
        view.findViewById<Button>(R.id.ai_tool_shorter)?.setOnClickListener {
            runToolInChat("shorter", null, "Make shorter", "No text to shorten")
        }
        view.findViewById<Button>(R.id.ai_tool_email)?.setOnClickListener {
            runToolInChat("email", null, "Write as email", "No text to convert to email")
        }
    }
    
    /**
     * Grab current input text, send it as a command to AI chat, and show the result there.
     */
    private fun runToolInChat(task: String, option: String?, label: String, emptyMessage: String) {
        val input = getCurrentInputText()
        if (input == null || input.first.isBlank()) {
            Toast.makeText(this, emptyMessage, Toast.LENGTH_SHORT).show()
            return
        }
        val (fullText, beforeLen, afterLen) = input
        runToolCommandInAiChat(task, fullText.trim(), option ?: "", label, beforeLen, afterLen)
    }
    
    /**
     * Build an explicit command prompt so tool taps behave like direct AI instructions.
     */
    private fun buildAiToolCommandPrompt(task: String, text: String, option: String): String {
        val sanitizedText = text.trim()
        return when (task) {
            "grammar" ->
                "Fix grammar, spelling, and punctuation in this text. Keep the original meaning and tone. Return only the corrected text:\n\n$sanitizedText"
            "reply" ->
                "Write a natural and context-aware reply to this message. Return only the reply text:\n\n$sanitizedText"
            "translate" -> {
                val target = option.ifBlank { "English" }
                "Translate the following text to $target. Return only the translated text:\n\n$sanitizedText"
            }
            "enhance" ->
                "Enhance this writing with clearer wording and better flow while keeping the same meaning. Return only the improved text:\n\n$sanitizedText"
            "tone" -> {
                val targetTone = option.ifBlank { "professional" }
                "Rewrite this text in a $targetTone tone while keeping the same meaning. Return only the rewritten text:\n\n$sanitizedText"
            }
            "paraphrase" ->
                "Paraphrase this text in different words while preserving the meaning. Return only the paraphrased text:\n\n$sanitizedText"
            "continue" ->
                "Continue writing this text in the same style and tone. Include the original text and continue from it. Return only the final text:\n\n$sanitizedText"
            "longer" ->
                "Make this text longer by adding useful detail while keeping the same core message. Return only the expanded text:\n\n$sanitizedText"
            "summarize" ->
                "Summarize this text clearly and concisely. Return only the summary:\n\n$sanitizedText"
            "synonyms" ->
                "Rewrite this text using better synonym choices where useful while preserving meaning. Return only the rewritten text:\n\n$sanitizedText"
            "shorter" ->
                "Make this text shorter and concise while preserving key points. Return only the shortened text:\n\n$sanitizedText"
            "email" ->
                "Rewrite this text as a polished professional email with greeting and sign-off when appropriate. Return only the email text:\n\n$sanitizedText"
            else ->
                "Process this text according to the request \"$task\". Return only the result:\n\n$sanitizedText"
        }
    }
    
    /**
     * Route writing-tool taps through the main AI assistant command flow.
     */
    private fun runToolCommandInAiChat(
        task: String,
        text: String,
        option: String,
        userCommandLabel: String,
        targetBeforeLen: Int? = null,
        targetAfterLen: Int? = null
    ) {
        if (!isNetworkAvailable()) {
            Toast.makeText(this, getString(R.string.internet_required_ai_chat), Toast.LENGTH_LONG).show()
            return
        }
        hideAiWritingTools()
        showAiChat()

        val userMessage = when (task) {
            "translate" -> "Translate to $option"
            "tone" -> "Change to $option tone"
            else -> userCommandLabel
        }
        val commandPrompt = buildAiToolCommandPrompt(task, text, option)
        addChatMessage("$userMessage:\n\n$text", isUser = true)
        addChatMessage("Processing...", isUser = false, isLoading = true)

        serviceScope.launch {
            try {
                val response = callAiApi(commandPrompt).trim()
                withContext(Dispatchers.Main) {
                    removeLoadingMessage()
                    if (response.isNotBlank()) {
                        addChatMessage(response, isUser = false)
                        // Put result back into the active app text field (e.g., Messenger chat bar).
                        if (targetBeforeLen != null && targetAfterLen != null) {
                            replaceTargetInputTextDirectly(targetBeforeLen, targetAfterLen, response)
                        }
                        aiChatInputText.clear()
                        aiChatInputText.append(response)
                        updateAiChatInputDisplay()
                        Toast.makeText(this@MainKeyboardService, getString(R.string.done), Toast.LENGTH_SHORT).show()
                    } else {
                        addChatMessage("Could not complete. Please try again.", isUser = false)
                        Toast.makeText(this@MainKeyboardService, getString(R.string.failed_try_again), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    removeLoadingMessage()
                    addChatMessage("Could not complete. Please try again.", isUser = false)
                    Toast.makeText(this@MainKeyboardService, getString(R.string.failed_try_again), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Replace text in the currently focused app field, even while AI chat overlay is visible.
     */
    private fun replaceTargetInputTextDirectly(beforeLen: Int, afterLen: Int, newText: String) {
        val ic = currentInputConnection
        if (ic != null) {
            ic.deleteSurroundingText(beforeLen, afterLen)
            ic.commitText(newText, 1)
        } else {
            // Fallback to AI chat input if host input connection is temporarily unavailable.
            aiChatInputText.clear()
            aiChatInputText.append(newText)
            updateAiChatInputDisplay()
        }
    }
    
    private fun showAiWritingTools() {
        hideAllOverlays()
        hideTopBarsForOverlay()
        applyImeKeyboardContainerVisible(false)
        aiWritingToolsContainer.visibility = View.VISIBLE
        isAiWritingToolsVisible = true
    }
    
    private fun hideAiWritingTools() {
        aiWritingToolsContainer.visibility = View.GONE
        applyImeKeyboardContainerVisible(true)
        showTopBarsAfterOverlay()
        isAiWritingToolsVisible = false
    }
    
    /**
     * Toggle AI Writing Tools visibility (KB+ button)
     */
    private fun toggleAiWritingTools() {
        if (isVoiceUiActive()) {
            Toast.makeText(this, getString(R.string.finish_voice_input_first), Toast.LENGTH_SHORT).show()
            return
        }
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
                    OutboundHttpPolicy.applyTo(conn)
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
        Toast.makeText(this, getString(R.string.processing), Toast.LENGTH_SHORT).show()
        callWritingTool(task, fullText.trim(), option) { result ->
            if (!result.isNullOrBlank()) {
                replaceCurrentInputText(beforeLen, afterLen, result)
                Toast.makeText(this, getString(R.string.done), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.could_not_complete), Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /** Show language options menu then run translate and show result in AI chat. */
    private fun showTranslateLanguagePicker(anchorView: View? = null) {
        val input = getCurrentInputText()
        if (input == null || input.first.isBlank()) {
            Toast.makeText(this, getString(R.string.no_text_to_translate), Toast.LENGTH_SHORT).show()
            return
        }
        val languages = listOf(
            "English", "Spanish", "French", "German", "Italian", "Portuguese",
            "Russian", "Japanese", "Korean", "Chinese", "Arabic", "Hindi",
            "Dutch", "Turkish", "Polish", "Vietnamese", "Thai", "Indonesian"
        )
        showSelectionMenu(
            title = "Translate to",
            anchorView = anchorView,
            options = languages
        ) { lang ->
            val (fullText, _, _) = input
            runWritingToolAndShowInChat("translate", fullText.trim(), lang, "Translate to $lang")
        }
    }
    
    /** Show tone options menu then run tone change and show result in AI chat. */
    private fun showTonePicker(anchorView: View? = null) {
        val input = getCurrentInputText()
        if (input == null || input.first.isBlank()) {
            Toast.makeText(this, getString(R.string.no_text_to_change_tone), Toast.LENGTH_SHORT).show()
            return
        }
        val tones = listOf(
            "Professional", "Friendly", "Formal", "Casual", "Encouraging",
            "Assertive", "Empathetic", "Neutral", "Enthusiastic", "Confident",
            "Diplomatic", "Sincere", "Humorous", "Urgent", "Calm", "Positive",
            "Supportive", "Apologetic", "Persuasive"
        )
        showSelectionMenu(
            title = "Change tone to",
            anchorView = anchorView,
            options = tones
        ) { tone ->
            val (fullText, _, _) = input
            runWritingToolAndShowInChat("tone", fullText.trim(), tone, "Change to $tone tone")
        }
    }

    /**
     * Show an anchored popup selection menu with AlertDialog fallback.
     */
    private fun showSelectionMenu(
        title: String,
        anchorView: View?,
        options: List<String>,
        onSelected: (String) -> Unit
    ) {
        if (options.isEmpty()) return
        if (anchorView != null) {
            try {
                val popup = PopupMenu(this, anchorView)
                options.forEachIndexed { index, option ->
                    popup.menu.add(0, index, index, option)
                }
                popup.setOnMenuItemClickListener { item ->
                    val selected = options.getOrNull(item.itemId)
                    if (selected != null) {
                        onSelected(selected)
                        true
                    } else false
                }
                popup.show()
                return
            } catch (_: Exception) {
                // Fall through to AlertDialog fallback.
            }
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(options.toTypedArray()) { _, which ->
                options.getOrNull(which)?.let { onSelected(it) }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Run writing tool and show result in AI chat bar.
     * Puts input text in chat input bar, switches to AI chat, adds user command,
     * calls tool, displays output in chat messages.
     */
    private fun runWritingToolAndShowInChat(task: String, text: String, option: String, userCommandLabel: String) {
        runToolCommandInAiChat(task, text, option, userCommandLabel, null, null)
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
        // Use isFocusable = false so the popup does not steal focus and hide the keyboard
        val popupWindow = android.widget.PopupWindow(
            popupView,
            260.dpToPx(),
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 10f
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            isFocusable = false  // Prevents keyboard from hiding when language selector opens
            inputMethodMode = android.widget.PopupWindow.INPUT_METHOD_NOT_NEEDED
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
                    Toast.makeText(this, getString(R.string.from_lang, langName), Toast.LENGTH_SHORT).show()
                } else {
                    dictTargetLanguage = langCode
                    Toast.makeText(this, getString(R.string.to_lang, langName), Toast.LENGTH_SHORT).show()
                }
                updateDictLanguageDisplay()
                popupWindow.dismiss()
            }
        }
        
        // Show popup
        dictContentRoot()?.let { view ->
            popupWindow.showAtLocation(view, Gravity.CENTER, 0, 0)
        }
    }
    
    /**
     * Update dictionary language display in header
     */
    private fun updateDictLanguageDisplay() {
        dictContentRoot()?.let { view ->
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
        
        Toast.makeText(
            this,
            getString(
                R.string.lang_switch,
                getLanguageName(dictCurrentLanguage),
                getLanguageName(dictTargetLanguage)
            ),
            Toast.LENGTH_SHORT
        ).show()
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
            listOf("ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه", "خ", "ح", "ج"),
            listOf("ش", "س", "ي", "ب", "ل", "أ", "ت", "ن", "م", "ك", "ط"),
            listOf("ذ", "ء", "ؤ", "ر", "ئ", "ة", "و", "ز", "ظ", "د")
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
    private fun rebuildDictMiniKeyboard(view: View? = dictContentRoot()) {
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
        
        val numbers = if (dictMiniKeyboardLanguage == "ar") {
            listOf("١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩", ".")
        } else {
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        }
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
        // Use isFocusable = false so the popup does not steal focus and hide the keyboard
        val popupWindow = android.widget.PopupWindow(
            popupView,
            260.dpToPx(),
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 10f
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            isFocusable = false  // Prevents keyboard from hiding when language selector opens
            inputMethodMode = android.widget.PopupWindow.INPUT_METHOD_NOT_NEEDED
        }

        // Set click listeners
        for (i in 0 until languagesContainer.childCount) {
            val button = languagesContainer.getChildAt(i)
            val (langCode, langName) = availableLanguages[i]
            button.setOnClickListener {
                dictMiniKeyboardLanguage = langCode
                rebuildDictMiniKeyboard()
                Toast.makeText(this, getString(R.string.keyboard_lang, langName), Toast.LENGTH_SHORT).show()
                popupWindow.dismiss()
            }
        }
        
        // Show popup
        dictContentRoot()?.let { view ->
            popupWindow.showAtLocation(view, Gravity.CENTER, 0, 0)
        }
    }
    
    /**
     * Create a dictionary mini keyboard key
     */
    private fun createDictKey(letter: String, isNumber: Boolean = false): Button {
        val keyHeightDp = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 42 else 52
        return Button(this).apply {
            tag = letter
            val isArabicKey = letter.any { isArabicCodePoint(it.code) }
            text = letter
            textSize = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                if (isNumber) 17f else 19f
            } else {
                if (isNumber) 20f else 22f
            }
            if (isArabicKey) {
                typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
                textLocale = Locale("ar")
                textDirection = View.TEXT_DIRECTION_RTL
            }
            setTextColor(Color.parseColor("#FFFFFF"))
            background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.dict_key_background)
            isAllCaps = false
            ellipsize = null
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
            includeFontPadding = false
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            
            val params = LinearLayout.LayoutParams(0, keyHeightDp.dpToPx()).apply {
                weight = 1f
                marginStart = 2.dpToPx()
                marginEnd = 2.dpToPx()
                topMargin = 2.dpToPx()
                bottomMargin = 2.dpToPx()
            }
            layoutParams = params
            
            setKeyPressWithAnimation(this) {
                dictSearchText.append(letter)
                updateDictSearchDisplay()
            }
        }
    }
    
    /**
     * Create a dictionary special key
     */
    private fun createDictSpecialKey(label: String, onClick: () -> Unit): Button {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val keyHeightDp = if (isLandscape) 40 else 48
        return Button(this).apply {
            text = label
            textSize = if (isLandscape) 17f else 20f
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
            
            val params = LinearLayout.LayoutParams(0, keyHeightDp.dpToPx()).apply {
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
            "Extra short" -> 40
            "Short" -> 44
            "Tall" -> 56
            "Custom" -> prefs.getInt("keyboard_height_custom", 48).coerceIn(36, 76)
            else -> 48 // Normal - Gboard-style larger touch targets
        }
        return if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            (baseHeight * 0.72).toInt().coerceIn(28, 40)
        } else {
            baseHeight
        }
    }
    
    /**
     * Update dictionary search display. Uses cached refs to avoid findViewById on every key press.
     */
    private fun updateDictSearchDisplay() {
        val view = dictContentRoot() ?: return
        if (dictSearchCachedRoot != view) {
            dictSearchCachedRoot = view
            dictSearchTextView = view.findViewById(R.id.dict_search_text)
            dictClearBtnRef = view.findViewById(R.id.dict_clear_btn)
            dictSuggestionsScroll = view.findViewById(R.id.dict_suggestions_scroll)
            dictSuggestionsRow = view.findViewById(R.id.dict_suggestions_row)
        }
        dictSearchTextView?.text = dictSearchText.toString()
        dictClearBtnRef?.visibility = if (dictSearchText.isNotEmpty()) View.VISIBLE else View.GONE
        scheduleDictPredictions()
    }

    private fun scheduleDictPredictions() {
        dictPredictionRunnable?.let { dictPredictionHandler.removeCallbacks(it) }
        dictPredictionRunnable = Runnable {
            val prefix = dictSearchText.toString().trim()
            PredictionProvider.getPredictions(
                prefix = prefix,
                contextBefore = emptyList(),
                lang = dictMiniKeyboardLanguage,
                limit = 5,
                includeCorrections = true
            ) { result ->
                if (isDictionaryVisible && prefix.isNotEmpty() && result.suggestions.isNotEmpty()) {
                    showDictSuggestions(result.suggestions)
                } else {
                    hideDictSuggestions()
                }
            }
        }
        dictPredictionHandler.postDelayed(dictPredictionRunnable!!, dictPredictionDelayMs)
    }

    private fun showDictSuggestions(suggestions: List<String>) {
        dictSuggestionsScroll?.visibility = View.VISIBLE
        dictSuggestionsRow?.removeAllViews()
        suggestions.forEach { word ->
            val chip = Button(this).apply {
                text = word
                textSize = 13f
                setTextColor(Color.parseColor("#EDEFF4"))
                setPadding(12.dpToPx(), 6.dpToPx(), 12.dpToPx(), 6.dpToPx())
                background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.dict_key_background)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = 4.dpToPx() }
                setOnClickListener {
                    playKeyFeedback(it)
                    insertDictPrediction(word)
                }
            }
            dictSuggestionsRow?.addView(chip)
        }
    }

    private fun hideDictSuggestions() {
        dictSuggestionsScroll?.visibility = View.GONE
        dictSuggestionsRow?.removeAllViews()
    }

    private fun insertDictPrediction(word: String) {
        val text = dictSearchText.toString()
        val wordLen = text.takeLastWhile { c -> c.isLetterOrDigit() || c == '\'' || c == '-' }.length
        if (wordLen > 0) {
            dictSearchText.delete(dictSearchText.length - wordLen, dictSearchText.length)
        }
        dictSearchText.append(word)
        updateDictSearchDisplay()
        PredictionProvider.recordWord(dictMiniKeyboardLanguage, word)
    }
    
    /**
     * Search dictionary for the word with two-language translation
     */
    private fun searchDictionary() {
        val word = dictSearchText.toString().trim()
        if (word.isEmpty()) {
            Toast.makeText(this, getString(R.string.please_type_word_search), Toast.LENGTH_SHORT).show()
            return
        }
        if (!isNetworkAvailable()) {
            Toast.makeText(this, getString(R.string.internet_required_dictionary), Toast.LENGTH_LONG).show()
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
                OutboundHttpPolicy.applyTo(connection)
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
     * Get language name from code, displayed in the user's device language.
     * Uses Locale.getDisplayName() so e.g. "English" shows as "Inglés" when the app is in Spanish.
     */
    private fun getLanguageName(code: String): String {
        return try {
            val locale = Locale.forLanguageTag(code.replace("_", "-"))
            locale.getDisplayName(Locale.getDefault())
        } catch (_: Exception) {
            code.uppercase(Locale.ROOT)
        }
    }
    
    /**
     * Show dictionary result with two-language translation
     */
    private fun showDictTwoLanguageResult(word: String, definition: DictResult?, translation: String?, sourceLang: String, targetLang: String) {
        dictContentRoot()?.let { view ->
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
                        Toast.makeText(this@MainKeyboardService, getString(R.string.copied, translation), Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(this@MainKeyboardService, getString(R.string.copied_short), Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(this@MainKeyboardService, getString(R.string.inserted_short), Toast.LENGTH_SHORT).show()
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
        dictContentRoot()?.let { view ->
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
                            Toast.makeText(this@MainKeyboardService, getString(R.string.copied_translation, translation), Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this@MainKeyboardService, getString(R.string.inserted, word), Toast.LENGTH_SHORT).show()
                }
            }
            resultContainer?.addView(insertButton)
        }
    }
    
    /**
     * Show dictionary loading state
     */
    private fun showDictLoading() {
        dictContentRoot()?.let { view ->
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
        dictContentRoot()?.let { view ->
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
        dictContentRoot()?.let { view ->
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
        if (overlayDictionaryRoot != null) {
            dictSearchText.clear()
            updateDictSearchDisplay()
            dictContentRoot()?.let { view ->
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
            dictionaryContainer.visibility = View.VISIBLE
            isDictionaryVisible = true
            if (!isNetworkAvailable()) {
                Toast.makeText(this, getString(R.string.internet_required_dictionary), Toast.LENGTH_LONG).show()
            }
            return
        }
        hideTopBarsForOverlay()
        applyImeKeyboardContainerVisible(false)
        emojiPickerContainer?.visibility = View.GONE
        isEmojiPickerVisible = false
        calculatorContainer.visibility = View.GONE
        isCalculatorVisible = false
        aiWritingToolsContainer.visibility = View.GONE
        isAiWritingToolsVisible = false
        aiFeaturesContainer.visibility = View.GONE
        moreOptionsContainer.visibility = View.GONE
        voiceRecordingContainer.visibility = View.GONE
        voiceProcessingStep2Container.visibility = View.GONE
        dictionaryContainer.visibility = View.VISIBLE
        isDictionaryVisible = true
        
        // Reset search
        dictSearchText.clear()
        updateDictSearchDisplay()
        
        // Reset results
        dictContentRoot()?.let { view ->
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
        // Proactive internet notice when offline
        if (!isNetworkAvailable()) {
            Toast.makeText(this, getString(R.string.internet_required_dictionary), Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * Hide the dictionary
     */
    private fun hideDictionary() {
        if (overlayDictionaryRoot != null) {
            val cb = overlayDictionaryClose
            overlayDictionaryRoot = null
            overlayDictionaryClose = null
            dictSearchCachedRoot = null
            overlayBubbleKeyboardIsolated = false
            rootView?.let {
                dictionaryContainer = it.findViewById(R.id.dictionary_include)
                dictionaryContainer.visibility = View.GONE
            }
            isDictionaryVisible = false
            cb?.invoke()
            return
        }
        dictionaryContainer.visibility = View.GONE
        applyImeKeyboardContainerVisible(true)
        showTopBarsAfterOverlay()
        isDictionaryVisible = false
    }
    
    /**
     * Toggle dictionary visibility
     */
    private fun toggleDictionary() {
        if (isVoiceUiActive()) {
            Toast.makeText(this, getString(R.string.finish_voice_input_first), Toast.LENGTH_SHORT).show()
            return
        }
        if (isDictionaryVisible) {
            hideDictionary()
        } else {
            showDictionary()
        }
    }

    // ==================== CLIPBOARD (Gboard-style) ====================

    private fun toggleClipboardPopup() {
        if (isVoiceUiActive()) {
            Toast.makeText(this, getString(R.string.finish_voice_input_first), Toast.LENGTH_SHORT).show()
            return
        }
        rootView ?: return
        val aiRow = rootView!!.findViewById<View>(R.id.ai_features_row)
        val predictionsContainer = rootView!!.findViewById<View>(R.id.predictions_container)
        if (aiRow == null || predictionsContainer == null) return
        if (isClipboardVisible) {
            showIconsHidePredictions(aiRow, predictionsContainer)
            isClipboardVisible = false
            return
        }
        showClipboardOnKeys()
    }

    private fun loadClipboardHistory() {
        clipboardHistory.clear()
        val prefs = getSharedPreferences(CLIPBOARD_PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(CLIPBOARD_HISTORY_KEY, null) ?: return
        val items = stored.split(CLIPBOARD_DELIMITER).filter { it.isNotBlank() }
        clipboardHistory.addAll(items)
    }

    private fun saveClipboardHistory() {
        getSharedPreferences(CLIPBOARD_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(CLIPBOARD_HISTORY_KEY, clipboardHistory.joinToString(CLIPBOARD_DELIMITER))
            .apply()
    }

    private fun addToClipboardHistory(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        clipboardHistory.removeAll { it == trimmed }
        clipboardHistory.add(0, trimmed)
        while (clipboardHistory.size > CLIPBOARD_MAX_ITEMS) {
            clipboardHistory.removeAt(clipboardHistory.size - 1)
        }
        saveClipboardHistory()
    }

    private fun syncCurrentClipboardToHistory() {
        val clip = clipboardManager?.primaryClip ?: return
        if (clip.itemCount == 0) return
        val item = clip.getItemAt(0)
        val text = item?.coerceToText(this)?.toString() ?: return
        if (text.isNotBlank()) addToClipboardHistory(text)
    }

    private fun showClipboardOnKeys() {
        val root = rootView ?: return
        val aiRow = root.findViewById<View>(R.id.ai_features_row)
        val predictionsContainer = root.findViewById<View>(R.id.predictions_container)
        val predictionsRow = root.findViewById<LinearLayout>(R.id.predictions_row)
        if (aiRow == null || predictionsContainer == null || predictionsRow == null) return

        syncCurrentClipboardToHistory()
        val items = clipboardHistory.toList()

        isClipboardVisible = true
        lastRenderedSuggestions = emptyList()
        val topBar = (aiRow.parent as? View)
        // Keep top_bar_container visible: predictions_container is a child of it; GONE would hide the strip.
        topBar?.visibility = View.VISIBLE
        aiRow.visibility = View.GONE
        predictionsContainer.visibility = View.VISIBLE
        predictionsRow.removeAllViews()

        if (items.isEmpty()) {
            val emptyChip = Button(this).apply {
                text = "📋 No copied text yet"
                textSize = 13f
                setTextColor(Color.parseColor("#888888"))
                setPadding(16.dpToPx(), 8.dpToPx(), 16.dpToPx(), 8.dpToPx())
                background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.glass_key_background)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                isEnabled = false
            }
            predictionsRow.addView(emptyChip)
        } else {
            items.forEach { text ->
                val displayText = if (text.length > 40) text.take(40) + "…" else text
                val chip = Button(this).apply {
                    this.text = displayText
                    textSize = 13f
                    setTextColor(Color.parseColor("#EDEFF4"))
                    setPadding(12.dpToPx(), 8.dpToPx(), 12.dpToPx(), 8.dpToPx())
                    background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.glass_key_background)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = 6.dpToPx() }
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setOnClickListener {
                        playKeyFeedback(it)
                        currentInputConnection?.commitText(text, 1)
                        showIconsHidePredictions(aiRow, predictionsContainer)
                        isClipboardVisible = false
                    }
                }
                predictionsRow.addView(chip)
            }
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
            Toast.makeText(this, getString(R.string.chat_cleared), Toast.LENGTH_SHORT).show()
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
        
        // Emoji button - action first, then feedback; lightweight scale animation
        view.findViewById<Button>(R.id.ai_chat_key_emoji)?.let { setKeyPressWithAnimation(it) { hideAiChat(); showEmojiPicker() } }
        
        // Space button
        view.findViewById<Button>(R.id.ai_chat_key_space)?.let { setKeyPressWithAnimation(it) { aiChatInputText.append(" "); updateAiChatInputDisplay() } }
        
        // Send button (keyboard)
        view.findViewById<Button>(R.id.ai_chat_key_send)?.let { setKeyPressWithAnimation(it) { sendAiChatMessage() } }
        
        // Globe button for language switch
        view.findViewById<Button>(R.id.ai_chat_key_globe)?.let { setKeyPressWithAnimation(it) { showAiChatLanguageSelector() } }
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
        
        // Use isFocusable = false so the popup does not steal focus and hide the keyboard
        val popupWindow = android.widget.PopupWindow(
            popupView,
            220.dpToPx(),
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 10f
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            isFocusable = false  // Prevents keyboard from hiding when language selector opens
            inputMethodMode = android.widget.PopupWindow.INPUT_METHOD_NOT_NEEDED
        }
        
        for (i in 0 until languagesContainer.childCount) {
            val button = languagesContainer.getChildAt(i)
            val (langCode, langName) = availableLanguages[i]
            button.setOnClickListener {
                aiChatKeyboardLanguage = langCode
                rebuildAiChatMiniKeyboard()
                Toast.makeText(this, getString(R.string.keyboard_lang, langName), Toast.LENGTH_SHORT).show()
                popupWindow.dismiss()
            }
        }
        
        popupWindow.showAtLocation(aiChatContainer, Gravity.CENTER, 0, 0)
    }
    
    /**
     * Rebuild AI chat mini keyboard with current language
     */
    private fun rebuildAiChatMiniKeyboard() {
        val view = aiChatContainer
        val row0 = view.findViewById<LinearLayout>(R.id.ai_chat_row0)
        val row1 = view.findViewById<LinearLayout>(R.id.ai_chat_row1)
        val row2 = view.findViewById<LinearLayout>(R.id.ai_chat_row2)
        val row3 = view.findViewById<LinearLayout>(R.id.ai_chat_row3)

        row0?.removeAllViews()
        row1?.removeAllViews()
        row2?.removeAllViews()
        row3?.removeAllViews()

        val numbers = if (aiChatKeyboardLanguage == "ar") {
            listOf("١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩", ".")
        } else {
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        }
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
    
    /**
     * Setup mini keyboard for AI chat
     */
    private fun setupAiChatMiniKeyboard(view: View) {
        val row0 = view.findViewById<LinearLayout>(R.id.ai_chat_row0)
        val row1 = view.findViewById<LinearLayout>(R.id.ai_chat_row1)
        val row2 = view.findViewById<LinearLayout>(R.id.ai_chat_row2)
        val row3 = view.findViewById<LinearLayout>(R.id.ai_chat_row3)
        // setupAiChat runs on first inflate, on overlay attach, and when rebinding root after overlay —
        // always clear programmatic keys so rows are not duplicated.
        row0?.removeAllViews()
        row1?.removeAllViews()
        row2?.removeAllViews()
        row3?.removeAllViews()
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        val numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        val keys1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
        val keys2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
        val keys3 = listOf("z", "x", "c", "v", "b", "n", "m")

        // Number row
        numbers.forEach { key ->
            row0?.addView(createAiChatKey(key, isLandscape))
        }

        // QWERTY row
        keys1.forEach { key ->
            row1?.addView(createAiChatKey(key, isLandscape))
        }

        // ASDF row
        keys2.forEach { key ->
            row2?.addView(createAiChatKey(key, isLandscape))
        }

        // ZXCV row + backspace
        keys3.forEach { key ->
            row3?.addView(createAiChatKey(key, isLandscape))
        }

        // Add backspace
        row3?.addView(createAiChatSpecialKey("⌫", isLandscape) {
            if (aiChatInputText.isNotEmpty()) {
                aiChatInputText.deleteCharAt(aiChatInputText.length - 1)
                updateAiChatInputDisplay()
            }
        })
    }

    /**
     * Create AI chat keyboard key - uses dimens for orientation-aware sizing
     */
    private fun createAiChatKey(letter: String, isLandscape: Boolean = false): Button {
        val keyHeightPx = resources.getDimensionPixelSize(R.dimen.ai_chat_key_height)
        val isArabicKey = letter.any { isArabicCodePoint(it.code) }
        val textSize = if (isLandscape) 13f else 15f
        val margin = if (isLandscape) 1 else 2
        return Button(this).apply {
            tag = letter
            text = letter
            this.textSize = textSize
            if (isArabicKey) {
                typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
                textLocale = Locale("ar")
                textDirection = View.TEXT_DIRECTION_RTL
            }
            setTextColor(Color.parseColor("#FFFFFF"))
            background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.dict_key_background)
            isAllCaps = false
            ellipsize = null
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
            includeFontPadding = false
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0

            val params = LinearLayout.LayoutParams(0, keyHeightPx).apply {
                weight = 1f
                marginStart = margin.dpToPx()
                marginEnd = margin.dpToPx()
                topMargin = margin.dpToPx()
                bottomMargin = margin.dpToPx()
            }
            layoutParams = params

            setKeyPressWithAnimation(this) {
                aiChatInputText.append(letter)
                updateAiChatInputDisplay()
            }
        }
    }

    /**
     * Create AI chat special key (e.g. backspace). Uses icon for delete key for visibility.
     */
    private fun createAiChatSpecialKey(label: String, isLandscape: Boolean = false, onClick: () -> Unit): Button {
        val keyHeightPx = resources.getDimensionPixelSize(R.dimen.ai_chat_key_height)
        val margin = if (isLandscape) 1 else 2
        val isBackspace = label == "⌫"
        return Button(this).apply {
            if (isBackspace) {
                val icon = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.ic_backspace)?.mutate()
                icon?.let { DrawableCompat.setTint(it, Color.WHITE) }
                setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null)
                compoundDrawablePadding = 0
                text = ""
            } else {
                text = label
                textSize = if (isLandscape) 13f else 15f
            }
            setTextColor(Color.parseColor("#FFFFFF"))
            background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.dict_key_special)
            isAllCaps = false
            gravity = Gravity.CENTER

            val params = LinearLayout.LayoutParams(0, keyHeightPx).apply {
                weight = 1.3f
                marginStart = margin.dpToPx()
                marginEnd = margin.dpToPx()
                topMargin = margin.dpToPx()
                bottomMargin = margin.dpToPx()
            }
            layoutParams = params
            
            setKeyPressWithAnimation(this) {
                onClick()
            }
        }
    }
    
    /**
     * Update AI chat input display. Uses cached refs to avoid findViewById on every key press.
     */
    private fun updateAiChatInputDisplay() {
        val view = aiChatContainer
        if (aiChatInputCachedRoot != view) {
            aiChatInputCachedRoot = view
            aiChatInputTextView = view.findViewById(R.id.ai_chat_input_text)
            aiChatClearBtnRef = view.findViewById(R.id.ai_chat_clear_btn)
            aiChatSuggestionsScroll = view.findViewById(R.id.ai_chat_suggestions_scroll)
            aiChatSuggestionsRow = view.findViewById(R.id.ai_chat_suggestions_row)
        }
        aiChatInputTextView?.text = if (aiChatInputText.isEmpty()) "" else aiChatInputText.toString()
        aiChatClearBtnRef?.visibility = if (aiChatInputText.isNotEmpty()) View.VISIBLE else View.GONE
        scheduleAiChatPredictions()
    }

    private fun scheduleAiChatPredictions() {
        aiChatPredictionRunnable?.let { aiChatPredictionHandler.removeCallbacks(it) }
        aiChatPredictionRunnable = Runnable {
            val text = aiChatInputText.toString()
            val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            val currentWord = words.lastOrNull()?.takeWhile { c -> c.isLetter() || c == '\'' } ?: ""
            val contextBefore = words.dropLast(1)
            PredictionProvider.getPredictions(
                prefix = currentWord,
                contextBefore = contextBefore,
                lang = aiChatKeyboardLanguage,
                limit = 5,
                includeCorrections = autoCorrectionEnabled
            ) { result ->
                if (isAiChatVisible && result.suggestions.isNotEmpty()) {
                    showAiChatSuggestions(result.suggestions)
                } else {
                    hideAiChatSuggestions()
                }
            }
        }
        aiChatPredictionHandler.postDelayed(aiChatPredictionRunnable!!, aiChatPredictionDelayMs)
    }

    private fun showAiChatSuggestions(suggestions: List<String>) {
        aiChatSuggestionsScroll?.visibility = View.VISIBLE
        aiChatSuggestionsRow?.removeAllViews()
        suggestions.forEach { word ->
            val chip = Button(this).apply {
                text = word
                textSize = 13f
                setTextColor(Color.parseColor("#EDEFF4"))
                setPadding(12.dpToPx(), 6.dpToPx(), 12.dpToPx(), 6.dpToPx())
                background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.dict_key_background)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = 4.dpToPx() }
                setOnClickListener {
                    playKeyFeedback(it)
                    insertAiChatPrediction(word)
                }
            }
            aiChatSuggestionsRow?.addView(chip)
        }
    }

    private fun hideAiChatSuggestions() {
        aiChatSuggestionsScroll?.visibility = View.GONE
        aiChatSuggestionsRow?.removeAllViews()
    }

    private fun insertAiChatPrediction(word: String) {
        val wordChars: (Char) -> Boolean = { c -> c.isLetter() || c == '\'' }
        val textBefore = aiChatInputText.toString()
        val wordLen = textBefore.takeLastWhile(wordChars).length
        if (wordLen > 0) {
            aiChatInputText.delete(aiChatInputText.length - wordLen, aiChatInputText.length)
        }
        aiChatInputText.append("$word ")
        updateAiChatInputDisplay()
        PredictionProvider.recordWord(aiChatKeyboardLanguage, word)
        val words = aiChatInputText.toString().trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size >= 2) {
            PredictionProvider.recordSequence(aiChatKeyboardLanguage, words.takeLast(3))
        }
    }
    
    /**
     * Send message to AI
     */
    private fun sendAiChatMessage() {
        val message = aiChatInputText.toString().trim()
        if (message.isEmpty()) {
            Toast.makeText(this, getString(R.string.please_type_message), Toast.LENGTH_SHORT).show()
            return
        }
        if (!isNetworkAvailable()) {
            Toast.makeText(this, getString(R.string.internet_required_ai_chat), Toast.LENGTH_LONG).show()
        }
        
        // Add user message to chat
        addChatMessage(message, isUser = true)
        val words = message.split(Regex("\\s+")).filter { it.isNotBlank() }
        words.forEach { PredictionProvider.recordWord(aiChatKeyboardLanguage, it) }
        if (words.size >= 2) PredictionProvider.recordSequence(aiChatKeyboardLanguage, words.takeLast(3))
        aiChatInputText.clear()
        updateAiChatInputDisplay()
        
        // Show loading
        addChatMessage("Thinking...", isUser = false, isLoading = true)
        
        // Call AI API
        serviceScope.launch {
            var responseToShow: String? = null
            try {
                val response = callAiApi(message)
                responseToShow = response
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("DeltaVoice", "AI chat send failed: ${e.message}", e)
                responseToShow = "Sorry, I couldn't process that. Please try again."
            } finally {
                withContext(Dispatchers.Main) {
                    removeLoadingMessage()
                    val toShow = responseToShow ?: "Sorry, something went wrong. Please try again."
                    addChatMessage(toShow, isUser = false)
                }
            }
        }
    }
    
    /**
     * Add message to chat UI
     */
    private fun addChatMessage(message: String, isUser: Boolean, isLoading: Boolean = false) {
        val view = aiChatContainer
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
    
    /**
     * Remove loading message
     */
    private fun removeLoadingMessage() {
        val view = aiChatContainer
        run {
            val messagesContainer = view.findViewById<LinearLayout>(R.id.ai_chat_messages) ?: return@run
            for (i in messagesContainer.childCount - 1 downTo 0) {
                val child = messagesContainer.getChildAt(i)
                if (child is LinearLayout) {
                    for (j in 0 until child.childCount) {
                        val bubble = child.getChildAt(j)
                        if (bubble?.tag == "loading") {
                            messagesContainer.removeViewAt(i)
                            return@run
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
                
                // Convex first (DeepSeek), then Supabase — DNS may not resolve Supabase host on some networks.
                val edgeResponse = if (isNetworkAvailable()) {
                    withTimeoutOrNull(aiChatEdgeTotalTimeoutMs) {
                        callOpenAiViaConvex(message) ?: callOpenAiViaSupabase(message)
                    }
                } else null
                if (edgeResponse != null) {
                    aiConversationHistory.add(mapOf("role" to "assistant", "content" to edgeResponse))
                    return@withContext edgeResponse
                }
                
                // Fallback to smart local responses if all API calls fail
                android.util.Log.w("DeltaVoice", "AI chat cloud unavailable, using local response")
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
            OutboundHttpPolicy.applyTo(connection)
            connection.connectTimeout = aiChatConnectTimeoutMs
            connection.readTimeout = aiChatReadTimeoutMs
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
                connection.disconnect()
                return parseAiChatResponse(responseText)
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
     * Get OpenAI API key from user preferences (optional).
     * When set, bypasses Supabase and calls OpenAI directly — useful when Supabase is unreachable.
     */
    private fun getOpenAiApiKey(): String {
        return getSharedPreferences("deltavoice_prefs", MODE_PRIVATE)
            .getString("openai_api_key", "") ?: ""
    }
    
    /**
     * Call OpenAI via Convex HTTP endpoint (no auth needed, uses Convex env OPENAI_API_KEY)
     */
    private fun callOpenAiViaConvex(message: String): String? {
        if (!com.deltavoice.config.ConvexConfig.USE_CONVEX_FOR_VOICE_WORKFLOW) return null
        if (com.deltavoice.config.ConvexConfig.CONVEX_SITE_URL.contains("YOUR_DEPLOYMENT")) return null
        try {
            val convexUrl = com.deltavoice.config.ConvexConfig.AI_CHAT_URL
            val url = java.net.URL(convexUrl)
            val connection = url.openConnection() as java.net.HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            OutboundHttpPolicy.applyTo(connection)
            connection.connectTimeout = aiChatConnectTimeoutMs
            connection.readTimeout = aiChatReadTimeoutMs
            connection.doOutput = true

            val messagesJson = buildAiChatMessagesJson()
            val requestBody = """{"messages":$messagesJson}"""

            android.util.Log.d("DeltaVoice", "Calling Convex AI chat...")
            java.io.OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestBody)
                writer.flush()
            }

            val responseCode = connection.responseCode
            val responseText = if (responseCode == 200) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }
            connection.disconnect()

            if (responseCode == 200 && responseText.isNotBlank()) {
                android.util.Log.d("DeltaVoice", "Convex AI response: ${responseText.take(200)}")
                return parseAiChatResponse(responseText)
            }
        } catch (e: Exception) {
            android.util.Log.e("DeltaVoice", "Convex AI call failed: ${e.message}")
        }
        return null
    }

    /**
     * Call OpenAI via Supabase edge function (fallback when Convex unavailable)
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
            OutboundHttpPolicy.applyTo(connection)
            connection.connectTimeout = aiChatConnectTimeoutMs
            connection.readTimeout = aiChatReadTimeoutMs
            connection.doOutput = true

            val messagesJson = buildAiChatMessagesJson()
            val requestBody = """{"messages":$messagesJson}"""

            android.util.Log.d("DeltaVoice", "Calling Supabase AI chat...")
            java.io.OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestBody)
                writer.flush()
            }

            val responseCode = connection.responseCode
            val responseText = if (responseCode == 200) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }
            connection.disconnect()

            if (responseCode == 200 && responseText.isNotBlank()) {
                android.util.Log.d("DeltaVoice", "Supabase AI response: ${responseText.take(200)}")
                return parseAiChatResponse(responseText)
            }
        } catch (e: Exception) {
            if (e is UnknownHostException) {
                android.util.Log.d("DeltaVoice", "Supabase AI skipped (DNS): ${e.message}")
            } else {
                android.util.Log.e("DeltaVoice", "Supabase AI call failed: ${e.message}")
                val msg = e.message ?: ""
                if (msg.contains("Unable to resolve host", ignoreCase = true) || msg.contains("No address", ignoreCase = true)) {
                    Handler(android.os.Looper.getMainLooper()).post {
                        val hint = if (getOpenAiApiKey().isNotBlank()) "" else "\n\nTip: Add your OpenAI API key in AI Assistant settings to use when server is unreachable."
                        Toast.makeText(this@MainKeyboardService, getString(R.string.cant_reach_server_with_hint, hint), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        return null
    }

    /** Build JSON array of messages for AI chat API */
    private fun buildAiChatMessagesJson(): String {
        val sb = StringBuilder("[")
        sb.append("""{"role":"system","content":"You are a helpful, friendly AI assistant like ChatGPT. Be concise but informative. Use emojis occasionally. Respond in the same language the user writes in."}""")
        aiConversationHistory.takeLast(8).forEach { msg ->
            val role = msg["role"] ?: "user"
            val content = (msg["content"] ?: "")
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")
                .replace("\t", " ")
            sb.append(""",{"role":"$role","content":"$content"}""")
        }
        sb.append("]")
        return sb.toString()
    }

    /** Parse AI chat response JSON - handles Convex/Supabase format and OpenAI API format */
    private fun parseAiChatResponse(responseText: String): String? {
        return try {
            val json = org.json.JSONObject(responseText)
            if (json.has("success") && !json.optBoolean("success", true)) {
                val errBody = json.optString("content", "")
                if (errBody.isNotBlank()) return errBody
                return null
            }
            // Convex/Supabase: { content, response, message }
            listOf("content", "response", "message", "text").forEach { key ->
                if (json.has(key)) {
                    val value = json.optString(key, "")
                    if (value.isNotBlank()) return value
                }
            }
            // OpenAI API: { choices: [{ message: { content: "..." } }] }
            val choices = json.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val msg = choices.getJSONObject(0).optJSONObject("message")
                msg?.optString("content", "")?.takeIf { it.isNotBlank() }?.let { return it }
            }
            null
        } catch (_: Exception) {
            // Fallback to regex for malformed JSON
            val patterns = listOf(
                Regex(""""content"\s*:\s*"((?:[^"\\\\]|\\\\.)*)""""),
                Regex(""""response"\s*:\s*"((?:[^"\\\\]|\\\\.)*)""""),
                Regex(""""message"\s*:\s*"((?:[^"\\\\]|\\\\.)*)"""")
            )
            for (pattern in patterns) {
                pattern.find(responseText)?.groupValues?.getOrNull(1)?.let { escaped ->
                    return escaped.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")
                }
            }
            null
        }
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
        aiChatContainer.findViewById<LinearLayout>(R.id.ai_chat_messages)?.removeAllViews()
        
        // Add welcome message
        addChatMessage("Hello! 👋 I'm your AI assistant. How can I help you today?", isUser = false)
    }
    
    /**
     * Show AI Chat
     */
    private fun showAiChat() {
        hideTopBarsForOverlay()
        // AI chat uses its own mini keyboard layout.
        applyImeKeyboardContainerVisible(false)
        if (!overlayBubbleKeyboardIsolated) {
            emojiPickerContainer?.visibility = View.GONE
            isEmojiPickerVisible = false
            calculatorContainer.visibility = View.GONE
            isCalculatorVisible = false
            dictionaryContainer.visibility = View.GONE
            isDictionaryVisible = false
            aiFeaturesContainer.visibility = View.GONE
            voiceRecordingContainer.visibility = View.GONE
            voiceProcessingStep2Container.visibility = View.GONE
            moreOptionsContainer.visibility = View.GONE
        }
        aiChatContainer.visibility = View.VISIBLE
        isAiChatVisible = true
        
        // Ensure AI chat mini keyboard is visible.
        aiChatContainer.findViewById<View>(R.id.ai_chat_row0)?.visibility = View.VISIBLE
        aiChatContainer.findViewById<View>(R.id.ai_chat_row1)?.visibility = View.VISIBLE
        aiChatContainer.findViewById<View>(R.id.ai_chat_row2)?.visibility = View.VISIBLE
        aiChatContainer.findViewById<View>(R.id.ai_chat_row3)?.visibility = View.VISIBLE
        aiChatContainer.findViewById<View>(R.id.ai_chat_mini_keyboard_bottom_row)?.visibility = View.VISIBLE
        
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
        // Proactive internet notice when offline
        if (!isNetworkAvailable()) {
            Toast.makeText(this, getString(R.string.internet_required_ai_chat), Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * Hide AI Chat
     */
    private fun hideAiChat() {
        aiChatContainer.visibility = View.GONE
        applyImeKeyboardContainerVisible(true)
        showTopBarsAfterOverlay()
        isAiChatVisible = false
    }
    
    /**
     * Toggle AI Chat visibility
     */
    private fun toggleAiChat() {
        if (isVoiceUiActive()) {
            Toast.makeText(this, getString(R.string.finish_voice_input_first), Toast.LENGTH_SHORT).show()
            return
        }
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
        videoUiHost = view.takeUnless { it === rootView }
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
        
        // Upload button - pick video from gallery
        view.findViewById<ImageButton>(R.id.btn_video_upload)?.setOnClickListener {
            launchVideoUploadPicker()
        }

        // Switch camera button
        view.findViewById<ImageButton>(R.id.btn_switch_camera)?.setOnClickListener {
            switchCamera()
        }
        
        // Close preview button
        view.findViewById<ImageButton>(R.id.btn_video_close)?.setOnClickListener {
            hideVideoPreview()
        }

        // Send original video immediately (no processing)
        view.findViewById<ImageButton>(R.id.btn_video_send_raw)?.setOnClickListener {
            sendOriginalVideoFromPreview()
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
            Toast.makeText(this, getString(R.string.camera_permission_required_short), Toast.LENGTH_SHORT).show()
            return
        }
        
        // Hide other components
        hideAllOverlays()
        
        // Show video recording container
        videoRecordingContainer?.visibility = View.VISIBLE
        applyImeKeyboardContainerVisible(false)
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
        // Release camera immediately on background thread so status bar icon disappears
        releaseCameraAndStopThread()
        
        videoRecordingContainer?.visibility = View.GONE
        applyImeKeyboardContainerVisible(true)
        isVideoRecordingVisible = false
        
        // Reset timer
        videoRecordingTimer?.visibility = View.GONE
        videoRecordingSeconds = 0
    }
    
    /**
     * Launch the video/image upload picker (opens VideoUploadActivity).
     */
    private fun launchVideoUploadPicker() {
        val intent = Intent(this, VideoUploadActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
            putExtra(VideoUploadActivity.EXTRA_LAUNCHED_FROM, VideoUploadActivity.LAUNCHED_FROM_KEYBOARD)
        }
        startActivity(intent)
    }

    /**
     * Show video preview with an uploaded file path (called when VideoUploadActivity finishes).
     */
    private fun showVideoPreviewWithPath(path: String) {
        videoFilePath = path
        showVideoPreview()
    }

    /**
     * Show video preview UI
     */
    private fun showVideoPreview() {
        hideVideoRecording()
        
        videoPreviewContainer?.visibility = View.VISIBLE
        applyImeKeyboardContainerVisible(false)
        isVideoPreviewVisible = true
        
        // Setup video player — listener must be registered before setVideoPath so it
        // isn't missed if a small local file prepares before the listener is attached.
        videoFilePath?.let { path ->
            videoPlayer?.setOnPreparedListener { mp ->
                val duration = mp.duration / 1000
                val minutes = duration / 60
                val seconds = duration % 60
                videoUiContent()?.findViewById<TextView>(R.id.video_duration_text)?.text =
                    String.format("%d:%02d", minutes, seconds)
                // start() initializes the surface renderer; seekTo(1) + pause() leaves the
                // first frame visible as a thumbnail without auto-playing the video.
                mp.start()
                mp.seekTo(1)
                mp.setOnSeekCompleteListener { it.pause() }
            }
            videoPlayer?.setVideoPath(path)
        }
    }
    
    /**
     * Hide video preview UI
     */
    private fun hideVideoPreview() {
        unregisterNetworkRestoredCallback()
        videoPlayer?.stopPlayback()
        videoPreviewContainer?.visibility = View.GONE
        applyImeKeyboardContainerVisible(true)
        isVideoPreviewVisible = false
        cleanupProcessedVideoFiles()
        resetVideoProcessButton()
    }
    
    /**
     * Play recorded video
     */
    private fun playRecordedVideo() {
        val playButton = videoUiContent()?.findViewById<ImageButton>(R.id.btn_play_video)
        
        if (videoPlayer?.isPlaying == true) {
            videoPlayer?.pause()
            playButton?.setImageResource(R.drawable.ic_play)
        } else {
            // If muxed video is ready, preview that instead of original
            val muxedPath = processedVideoFilePath
            if (!muxedPath.isNullOrBlank() && File(muxedPath).exists()) {
                val currentPath = videoPlayer?.tag as? String
                if (currentPath != muxedPath) {
                    videoPlayer?.setVideoPath(muxedPath)
                    videoPlayer?.tag = muxedPath
                }
            }

            videoPlayer?.start()
            playButton?.setImageResource(R.drawable.ic_pause)
            
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
            Toast.makeText(this, getString(R.string.failed_open_camera), Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this@MainKeyboardService, getString(R.string.camera_config_failed), Toast.LENGTH_SHORT).show()
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Close camera - must run on camera background thread for proper release.
     */
    private fun closeCamera() {
        try {
            cameraCaptureSession?.close()
        } catch (_: Exception) {}
        cameraCaptureSession = null
        try {
            cameraDevice?.close()
        } catch (_: Exception) {}
        cameraDevice = null
    }
    
    /**
     * Release camera on background thread then stop it, so the status bar icon disappears immediately.
     */
    private fun releaseCameraAndStopThread() {
        val handler = backgroundHandler
        val thread = backgroundThread
        if (handler != null && thread != null) {
            handler.post {
                closeCamera()
            }
        } else {
            closeCamera()
        }
        stopBackgroundThread()
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
            Toast.makeText(this, getString(R.string.audio_permission_required), Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this@MainKeyboardService, getString(R.string.recording_config_failed), Toast.LENGTH_SHORT).show()
                }
            }, backgroundHandler)
            
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.failed_start_recording, e.message ?: ""), Toast.LENGTH_SHORT).show()
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
        val statusText = videoUiContent()?.findViewById<TextView>(R.id.video_status_text)
        val recordButton = videoUiContent()?.findViewById<ImageButton>(R.id.btn_video_record)
        
        if (recording) {
            statusText?.text = getString(R.string.recording_tap_to_stop)
            statusText?.setTextColor(Color.parseColor("#FF5252"))
            recordButton?.setBackgroundResource(R.drawable.voice_mode_button_green)
            videoRecordingTimer?.visibility = View.VISIBLE
        } else {
            statusText?.text = getString(R.string.tap_to_start_recording)
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
            Toast.makeText(this, getString(R.string.no_video_recorded), Toast.LENGTH_LONG).show()
            return
        }
        
        val videoFile = File(videoPath)
        if (!videoFile.exists() || videoFile.length() == 0L) {
            Toast.makeText(this, getString(R.string.video_file_not_found_record_again), Toast.LENGTH_LONG).show()
            return
        }
        
        android.util.Log.d("DeltaVoice", "Processing video: $videoPath, size: ${videoFile.length()} bytes")
        android.util.Log.d("DeltaVoice", "Target: $languageName ($targetLanguage), Voice: $voiceStyleName ($voiceStyle)")
        
        Toast.makeText(this, getString(R.string.processing_video_to, languageName, voiceStyleName), Toast.LENGTH_LONG).show()
        
        serviceScope.launch {
            try {
                // Update UI for processing state
                withContext(Dispatchers.Main) {
                    videoUiContent()?.findViewById<Button>(R.id.btn_process_video)?.apply {
                        isEnabled = false
                        text = "⏳ Processing..."
                    }
                }
                
                // Step 1: Extract audio from video
                android.util.Log.d("DeltaVoice", "Step 1: Extracting audio from video...")
                val audioFile = extractAudioFromVideo(videoFile)
                
                if (audioFile == null || !audioFile.exists()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainKeyboardService, getString(R.string.failed_extract_audio), Toast.LENGTH_LONG).show()
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
                        withContext(Dispatchers.Main) {
                            videoUiContent()?.findViewById<Button>(R.id.btn_process_video)?.apply {
                                text = "⏳ Muxing video..."
                            }
                        }
                        
                        // Save processed audio to file
                        val processedAudioFile = withContext(Dispatchers.IO) {
                            val fileName = "processed_audio_${System.currentTimeMillis()}.mp3"
                            val file = File(cacheDir, fileName)
                            file.writeBytes(android.util.Base64.decode(response.convertedAudioBase64, android.util.Base64.NO_WRAP))
                            file
                        }
                        processedVideoAudioFilePath = processedAudioFile.absolutePath
                        
                        // Get AAC audio for muxing (MediaMuxer requires AAC; MP3 cannot be muxed into MP4)
                        var aacAudioFile = convertMp3ToAac(processedAudioFile)
                        if (aacAudioFile == null) {
                            android.util.Log.w("DeltaVoice", "Local MP3->AAC failed, trying VideoProcessingHelper")
                            aacAudioFile = VideoProcessingHelper.convertMp3ToAac(processedAudioFile, cacheDir)
                            if (aacAudioFile == processedAudioFile) aacAudioFile = null  // Helper returns original on fail
                        }
                        
                        var muxedVideo: File? = null
                        if (aacAudioFile != null) {
                            muxedVideo = muxVideoWithProcessedAudio(videoFile, aacAudioFile)
                            if (muxedVideo == null) {
                                android.util.Log.w("DeltaVoice", "Local mux failed, trying VideoProcessingHelper")
                                muxedVideo = VideoProcessingHelper.muxVideoWithProcessedAudio(videoFile, aacAudioFile, cacheDir)
                            }
                        }
                        
                        // Clean up intermediate audio files
                        if (aacAudioFile != null && aacAudioFile != processedAudioFile) {
                            try { aacAudioFile.delete() } catch (_: Exception) {}
                        }
                        
                        if (muxedVideo != null && muxedVideo.exists()) {
                            processedVideoFilePath = muxedVideo.absolutePath
                            isVideoProcessedAudioReady = true
                            withContext(Dispatchers.Main) {
                                response.translatedText?.takeIf { it.isNotBlank() }?.let { insertText(it) }
                                playBase64Audio(response.convertedAudioBase64, "mp3")
                                Toast.makeText(this@MainKeyboardService, getString(R.string.video_ready_tap_preview), Toast.LENGTH_LONG).show()
                                videoUiContent()?.findViewById<Button>(R.id.btn_process_video)?.apply {
                                    isEnabled = true
                                    text = "  Send Video"
                                    setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_send, 0, 0, 0)
                                    background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.voice_mode_button_green)
                                }
                            }
                        } else {
                            // Muxing failed – fall back to sharing audio only
                            isVideoProcessedAudioReady = true
                            withContext(Dispatchers.Main) {
                                response.translatedText?.takeIf { it.isNotBlank() }?.let { insertText(it) }
                                playBase64Audio(response.convertedAudioBase64, "mp3")
                                Toast.makeText(this@MainKeyboardService, getString(R.string.audio_ready_video_mux_failed), Toast.LENGTH_LONG).show()
                                videoUiContent()?.findViewById<Button>(R.id.btn_process_video)?.apply {
                                    isEnabled = true
                                    text = "  Send Audio"
                                    setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_send, 0, 0, 0)
                                    background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.voice_mode_button_green)
                                }
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            response.translatedText?.takeIf { it.isNotBlank() }?.let { insertText(it) }
                            Toast.makeText(this@MainKeyboardService, getString(R.string.video_transcribed_translated), Toast.LENGTH_SHORT).show()
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
     * Mux original video (visuals only) with new processed audio into a final MP4.
     * Keeps original video track, replaces audio track with processedAudioFile.
     */
    private suspend fun muxVideoWithProcessedAudio(
        originalVideoFile: File,
        processedAudioFile: File
    ): File? = withContext(Dispatchers.IO) {
        try {
            val outputFile = File(cacheDir, "final_video_${System.currentTimeMillis()}.mp4")

            // Extract video track from original
            val videoExtractor = android.media.MediaExtractor()
            videoExtractor.setDataSource(originalVideoFile.absolutePath)

            var videoTrackIndex = -1
            var videoFormat: android.media.MediaFormat? = null
            for (i in 0 until videoExtractor.trackCount) {
                val fmt = videoExtractor.getTrackFormat(i)
                if (fmt.getString(android.media.MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                    videoTrackIndex = i
                    videoFormat = fmt
                    break
                }
            }
            if (videoTrackIndex == -1 || videoFormat == null) {
                android.util.Log.e("DeltaVoice", "Mux: No video track in original file")
                videoExtractor.release()
                return@withContext null
            }
            videoExtractor.selectTrack(videoTrackIndex)

            // Prepare audio from processed file (could be raw MP3 or M4A inside MPEG-4)
            val audioExtractor = android.media.MediaExtractor()
            audioExtractor.setDataSource(processedAudioFile.absolutePath)

            var audioTrackIndex = -1
            var audioFormat: android.media.MediaFormat? = null
            for (i in 0 until audioExtractor.trackCount) {
                val fmt = audioExtractor.getTrackFormat(i)
                if (fmt.getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    audioFormat = fmt
                    break
                }
            }
            if (audioTrackIndex == -1 || audioFormat == null) {
                android.util.Log.e("DeltaVoice", "Mux: No audio track in processed file")
                videoExtractor.release()
                audioExtractor.release()
                return@withContext null
            }
            val audioMime = audioFormat.getString(android.media.MediaFormat.KEY_MIME) ?: ""
            if (audioMime.contains("mpeg", ignoreCase = true) && !audioMime.contains("mp4a", ignoreCase = true)) {
                android.util.Log.e("DeltaVoice", "Mux: Audio is MP3 (not AAC). MediaMuxer MPEG-4 requires AAC. Run convertMp3ToAac first.")
                videoExtractor.release()
                audioExtractor.release()
                return@withContext null
            }
            audioExtractor.selectTrack(audioTrackIndex)

            // Create output muxer
            val muxer = android.media.MediaMuxer(
                outputFile.absolutePath,
                android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )
            val muxVideoTrack = muxer.addTrack(videoFormat)
            val muxAudioTrack = muxer.addTrack(audioFormat)
            muxer.start()

            val bufferSize = 1024 * 1024
            val buffer = java.nio.ByteBuffer.allocate(bufferSize)
            val bufferInfo = android.media.MediaCodec.BufferInfo()

            // Write video track
            while (true) {
                val sampleSize = videoExtractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = videoExtractor.sampleTime
                bufferInfo.flags = videoExtractor.sampleFlags
                muxer.writeSampleData(muxVideoTrack, buffer, bufferInfo)
                videoExtractor.advance()
            }

            // Write audio track
            while (true) {
                val sampleSize = audioExtractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = audioExtractor.sampleTime
                bufferInfo.flags = audioExtractor.sampleFlags
                muxer.writeSampleData(muxAudioTrack, buffer, bufferInfo)
                audioExtractor.advance()
            }

            muxer.stop()
            muxer.release()
            videoExtractor.release()
            audioExtractor.release()

            android.util.Log.d("DeltaVoice", "Muxed video created: ${outputFile.absolutePath}, size: ${outputFile.length()} bytes")
            outputFile
        } catch (e: Exception) {
            android.util.Log.e("DeltaVoice", "Video muxing failed: ${e.message}", e)
            null
        }
    }

    /**
     * Convert raw MP3 bytes to an M4A/AAC container so MediaMuxer can handle it.
     * MediaMuxer's MPEG-4 output only accepts AAC; raw MP3 cannot be muxed into MP4.
     * Returns null on failure so caller never passes MP3 to muxer.
     */
    private suspend fun convertMp3ToAac(mp3File: File): File? = withContext(Dispatchers.IO) {
        try {
            val aacFile = File(cacheDir, "converted_audio_${System.currentTimeMillis()}.m4a")

            val extractor = android.media.MediaExtractor()
            extractor.setDataSource(mp3File.absolutePath)

            var trackIndex = -1
            var inputFormat: android.media.MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                if (fmt.getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    trackIndex = i
                    inputFormat = fmt
                    break
                }
            }
            if (trackIndex == -1 || inputFormat == null) {
                android.util.Log.e("DeltaVoice", "convertMp3ToAac: No audio track in file")
                extractor.release()
                return@withContext null
            }
            extractor.selectTrack(trackIndex)

            val inputMime = inputFormat.getString(android.media.MediaFormat.KEY_MIME) ?: "audio/mpeg"
            // If already AAC, use directly (API may return M4A)
            if (inputMime.contains("mp4a", ignoreCase = true) || inputMime == "audio/mp4a-latm") {
                extractor.release()
                android.util.Log.d("DeltaVoice", "Audio already AAC, using directly")
                return@withContext mp3File
            }

            val sampleRate = try {
                inputFormat.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE)
            } catch (_: Exception) { 44100 }
            val channelCount = try {
                inputFormat.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT)
            } catch (_: Exception) { 1 }

            // Setup decoder
            val decoder = android.media.MediaCodec.createDecoderByType(inputMime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            // Setup encoder (AAC)
            val outputFormat = android.media.MediaFormat.createAudioFormat("audio/mp4a-latm", sampleRate, channelCount)
            outputFormat.setInteger(android.media.MediaFormat.KEY_BIT_RATE, 128000)
            outputFormat.setInteger(android.media.MediaFormat.KEY_AAC_PROFILE, android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            val encoder = android.media.MediaCodec.createEncoderByType("audio/mp4a-latm")
            encoder.configure(outputFormat, null, null, android.media.MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            // Setup muxer
            val muxer = android.media.MediaMuxer(aacFile.absolutePath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var muxerTrackIndex = -1
            var muxerStarted = false

            val timeoutUs = 10000L
            val bufferInfo = android.media.MediaCodec.BufferInfo()
            var inputDone = false
            var decoderDone = false
            var allDone = false

            while (!allDone) {
                // Feed data into decoder
                if (!inputDone) {
                    val inIdx = decoder.dequeueInputBuffer(timeoutUs)
                    if (inIdx >= 0) {
                        val inBuf = decoder.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                // Drain decoder -> feed encoder (chunked: decoder output can exceed encoder input buffer)
                if (!decoderDone) {
                    val outIdx = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                    if (outIdx >= 0) {
                        val decoded = decoder.getOutputBuffer(outIdx)!!
                        val isEos = bufferInfo.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0

                        if (bufferInfo.size > 0) {
                            decoded.position(bufferInfo.offset)
                            decoded.limit(bufferInfo.offset + bufferInfo.size)
                            var pending = bufferInfo.size
                            var feedOffset = 0
                            val bytesPerSample = (channelCount * 2).coerceAtLeast(1)

                            while (pending > 0) {
                                val encInIdx = encoder.dequeueInputBuffer(timeoutUs)
                                if (encInIdx < 0) break
                                val encBuf = encoder.getInputBuffer(encInIdx)!!
                                encBuf.clear()
                                val toCopy = minOf(pending, encBuf.capacity())
                                decoded.limit(decoded.position() + toCopy)
                                encBuf.put(decoded)
                                val pts = if (sampleRate > 0) bufferInfo.presentationTimeUs + (feedOffset * 1_000_000L / (sampleRate * bytesPerSample))
                                    else bufferInfo.presentationTimeUs
                                encoder.queueInputBuffer(encInIdx, 0, toCopy, pts, 0)
                                feedOffset += toCopy
                                pending -= toCopy
                            }
                        }
                        decoder.releaseOutputBuffer(outIdx, false)
                        if (isEos) {
                            val encInIdx = encoder.dequeueInputBuffer(timeoutUs)
                            if (encInIdx >= 0) {
                                encoder.queueInputBuffer(encInIdx, 0, 0, 0, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            }
                            decoderDone = true
                        }
                    }
                }

                // Drain encoder -> write to muxer
                val encOutIdx = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (encOutIdx == android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (!muxerStarted) {
                        muxerTrackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                } else if (encOutIdx >= 0) {
                    val encData = encoder.getOutputBuffer(encOutIdx)!!
                    if (muxerStarted && bufferInfo.size > 0) {
                        muxer.writeSampleData(muxerTrackIndex, encData, bufferInfo)
                    }
                    val isEos = bufferInfo.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    encoder.releaseOutputBuffer(encOutIdx, false)
                    if (isEos) allDone = true
                }
            }

            decoder.stop(); decoder.release()
            encoder.stop(); encoder.release()
            if (muxerStarted) { muxer.stop(); muxer.release() }
            extractor.release()

            android.util.Log.d("DeltaVoice", "MP3 -> AAC conversion done: ${aacFile.length()} bytes")
            aacFile
        } catch (e: Exception) {
            android.util.Log.e("DeltaVoice", "MP3->AAC conversion failed: ${e.message}", e)
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
            Toast.makeText(this, getString(R.string.video_processing_failed_short, errorMessage.take(50)), Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * Reset video process button state
     */
    private fun resetVideoProcessButton() {
        videoUiContent()?.findViewById<Button>(R.id.btn_process_video)?.apply {
            isEnabled = true
            text = "  Process Video"
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_ai_mode, 0, 0, 0)
            background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.voice_mode_button_purple)
        }
    }
    
    /**
     * Share the processed video (original visuals + translated voice) or fallback to audio.
     */
    private fun shareProcessedVideoAudio() {
        if (!isVideoProcessedAudioReady) {
            Toast.makeText(this, getString(R.string.no_processed_content_send), Toast.LENGTH_SHORT).show()
            return
        }
        
        // Prefer sharing the muxed video (original visuals + processed voice)
        val videoPath = processedVideoFilePath
        if (!videoPath.isNullOrBlank() && File(videoPath).exists()) {
            shareVideoFile(File(videoPath), "Send translated video via") {
                cleanupProcessedVideoFiles()
                resetVideoProcessButton()
                hideVideoPreview()
            }
            return
        }
        
        // Fallback: share audio only if muxed video is unavailable
        val audioPath = processedVideoAudioFilePath
        if (!audioPath.isNullOrBlank() && File(audioPath).exists()) {
            shareAudioFile(File(audioPath), "Send translated voice via") {
                cleanupProcessedVideoFiles()
                resetVideoProcessButton()
                hideVideoPreview()
            }
            return
        }
        
        Toast.makeText(this, getString(R.string.no_processed_content_send), Toast.LENGTH_SHORT).show()
    }
    
    private fun cleanupProcessedVideoFiles() {
        processedVideoAudioFilePath?.let { try { File(it).delete() } catch (_: Exception) {} }
        processedVideoFilePath?.let { try { File(it).delete() } catch (_: Exception) {} }
        processedVideoAudioFilePath = null
        processedVideoFilePath = null
        isVideoProcessedAudioReady = false
    }

    /**
     * Send the original recorded or uploaded video directly, skipping processing.
     * Used by the send icon in the Preview & Process Video header.
     */
    private fun sendOriginalVideoFromPreview() {
        val path = videoFilePath
        if (path.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.no_video_to_send), Toast.LENGTH_SHORT).show()
            return
        }

        val file = File(path)
        if (!file.exists() || file.length() == 0L) {
            Toast.makeText(this, getString(R.string.video_file_not_found), Toast.LENGTH_SHORT).show()
            return
        }

        shareVideoFile(file, "Send video via") {
            // After sending, close preview and clear processed state (but keep original file)
            cleanupProcessedVideoFiles()
            resetVideoProcessButton()
            hideVideoPreview()
        }
    }
    
    /**
     * Hide all overlay components
     */
    private fun hideAllOverlays() {
        if (overlayBubbleKeyboardIsolated) {
            hideAllOverlaysForBubbleOverlayOnly()
            return
        }
        isClipboardVisible = false
        rootView?.let { v ->
            val topBar = v.findViewById<View>(R.id.top_bar_container)
            val predictionsContainer = v.findViewById<View>(R.id.predictions_container)
            topBar?.visibility = View.VISIBLE
            predictionsContainer?.visibility = View.GONE
        }

        emojiPickerContainer?.visibility = View.GONE
        isEmojiPickerVisible = false

        calculatorContainer.visibility = View.GONE
        isCalculatorVisible = false

        dictionaryContainer.visibility = View.GONE
        isDictionaryVisible = false

        aiChatContainer.visibility = View.GONE
        isAiChatVisible = false

        aiWritingToolsContainer.visibility = View.GONE
        isAiWritingToolsVisible = false

        moreOptionsContainer.visibility = View.GONE

        voiceRecordingContainer.visibility = View.GONE
        voiceProcessingStep2Container.visibility = View.GONE

        videoRecordingContainer?.visibility = View.GONE
        isVideoRecordingVisible = false

        videoPreviewContainer?.visibility = View.GONE
        isVideoPreviewVisible = false
    }

    /**
     * Bubble overlay uses a separate window; only clear the feature panels we rebind to the overlay
     * — do not strip emoji/calculator/more-options rows on the IME [rootView].
     */
    private fun hideAllOverlaysForBubbleOverlayOnly() {
        isClipboardVisible = false
        aiChatContainer.visibility = View.GONE
        isAiChatVisible = false
        aiWritingToolsContainer.visibility = View.GONE
        isAiWritingToolsVisible = false
        voiceRecordingContainer.visibility = View.GONE
        voiceProcessingStep2Container.visibility = View.GONE
        videoRecordingContainer?.visibility = View.GONE
        isVideoRecordingVisible = false
        videoPreviewContainer?.visibility = View.GONE
        isVideoPreviewVisible = false
        dictionaryContainer.visibility = View.GONE
        isDictionaryVisible = false
    }

    private fun isVoiceUiActive(): Boolean {
        return voiceRecordingContainer.visibility == View.VISIBLE ||
            voiceProcessingStep2Container.visibility == View.VISIBLE
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
            Toast.makeText(this, getString(R.string.failed_open_homepage), Toast.LENGTH_SHORT).show()
        }
    }

    private fun openKeyboardSettings() {
        try {
            val intent = Intent(this, SettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.failed_open_app_settings), Toast.LENGTH_SHORT).show()
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

        val enterButton = createSpecialKeyButton("↵", "ENTER", weight = 1.2f, isEnterKey = true)
        rowBottom.addView(enterButton)
    }

    /**
     * Create a key button with dark theme styling
     */
    private fun createKeyButton(label: String, value: String, weight: Float = 1f): Button {
        val button = Button(this).apply {
            val baseLabel = label
            tag = value
            text = if (baseLabel.length == 1 && baseLabel[0].isLetter()) {
                if (isShiftPressed) baseLabel.uppercase() else baseLabel.lowercase()
            } else {
                baseLabel
            }
            // Use smaller font for non-Latin scripts to prevent truncation to "..."
            val needsCompactFont = label.any { c ->
                val code = c.code
                code in 0x0600..0x06FF || code in 0x0900..0x097F || code in 0x0400..0x04FF ||
                code in 0x3040..0x309F || code in 0x30A0..0x30FF || code in 0x3130..0x318F || code in 0xAC00..0xD7AF
            }
            val isLandscapeKey = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            textSize = when {
                needsCompactFont -> if (isLandscapeKey) 10f else 12f
                label.length > 1 -> if (isLandscapeKey) 10f else 12f
                else -> if (isLandscapeKey) 13f else 16f
            }
            setTextColor(Color.parseColor("#F2F2F2"))
            gravity = Gravity.CENTER
            isAllCaps = false
            setIncludeFontPadding(true)
            val keyVertPad = if (isLandscapeKey) 4 else 8
            setPadding(6, keyVertPad, 6, keyVertPad)
            minHeight = getKeyHeightDp().dpToPx()
            background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.glass_key_background)
            
            // Gboard-style: minimal gaps for larger touch area, edge-to-edge feel
            val keyMargin = if (isLandscapeKey) 1 else 1
            val layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                this.weight = weight
                marginStart = keyMargin.dpToPx()
                marginEnd = keyMargin.dpToPx()
                topMargin = keyMargin.dpToPx()
                bottomMargin = keyMargin.dpToPx()
            }
            this.layoutParams = layoutParams
            
            // Prevent focus
            isFocusable = false
            isFocusableInTouchMode = false

            setKeyPressWithAnimation(this) { handleKeyPress(value) }
        }
        return button
    }

    /**
     * Create a key button with number above letter (for QWERTY row)
     */
    private fun createKeyButtonWithNumber(number: String, letter: String): Button {
        val button = Button(this).apply {
            tag = letter
            val letterDisplay = if (isShiftPressed) {
                letter.uppercase()
            } else {
                letter.lowercase()
            }
            val isLandscapeKey = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            text = "$number\n$letterDisplay"
            textSize = if (isLandscapeKey) 10f else 12f
            setTextColor(Color.parseColor("#333333"))
            gravity = Gravity.CENTER
            isAllCaps = false
            setIncludeFontPadding(true)
            setLineSpacing(0f, 1.12f)
            val vertPad = if (isLandscapeKey) 4 else 8
            setPadding(6, vertPad, 6, if (isLandscapeKey) 4 else 10)
            minHeight = (getKeyHeightDp() + if (isLandscapeKey) 0 else 4).dpToPx()
            background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.glass_key_background)
            
            val keyMargin = 1
            val layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                weight = 1f
                marginStart = keyMargin.dpToPx()
                marginEnd = keyMargin.dpToPx()
                topMargin = keyMargin.dpToPx()
                bottomMargin = keyMargin.dpToPx()
            }
            this.layoutParams = layoutParams
            
            // Prevent focus
            isFocusable = false
            isFocusableInTouchMode = false

            setKeyPressWithAnimation(this) {
                val key = if (isShiftPressed) letter else letter.lowercase()
                handleKeyPress(key)
            }
        }
        return button
    }
    
    /**
     * Create a special function key button (Liquid Glass styling)
     * @param isEnterKey use glass_key_enter_background for return/enter key
     */
    private fun createSpecialKeyButton(label: String, value: String, weight: Float = 1f, isEnterKey: Boolean = false): Button {
        val drawableRes = if (isEnterKey) R.drawable.glass_key_enter_background else R.drawable.glass_key_special_background
        val isLandscapeKey = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val button = Button(this).apply {
            text = label
            textSize = if (isLandscapeKey) 11f else 14f
            setTextColor(Color.parseColor("#F2F2F2"))
            gravity = Gravity.CENTER
            isAllCaps = false
            setIncludeFontPadding(true)
            val keyVertPad = if (isLandscapeKey) 4 else 8
            setPadding(6, keyVertPad, 6, keyVertPad)
            minHeight = getKeyHeightDp().dpToPx()
            background = ContextCompat.getDrawable(this@MainKeyboardService, drawableRes)
            
            val keyMargin = 1
            val layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                this.weight = weight
                marginStart = keyMargin.dpToPx()
                marginEnd = keyMargin.dpToPx()
                topMargin = keyMargin.dpToPx()
                bottomMargin = keyMargin.dpToPx()
            }
            this.layoutParams = layoutParams
            
            // Prevent focus
            isFocusable = false
            isFocusableInTouchMode = false

            if (value == "BACKSPACE") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    stateListAnimator = null
                    elevation = 0f
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) foreground = null
                setOnTouchListener { touchedView, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            touchedView.animate().scaleX(0.94f).scaleY(0.94f)
                                .setDuration(KEY_PRESS_ANIM_DURATION_MS)
                                .setInterpolator(LinearInterpolator()).start()
                            startBackspaceRepeat(touchedView)
                            true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            stopBackspaceRepeat(applyPredictionUpdate = true)
                            playKeyFeedback(touchedView)
                            touchedView.animate().scaleX(1f).scaleY(1f)
                                .setDuration(KEY_PRESS_ANIM_DURATION_MS)
                                .setInterpolator(LinearInterpolator()).start()
                            true
                        }
                        else -> false
                    }
                }
            } else {
                setKeyPressWithAnimation(this) { handleKeyPress(value) }
            }
        }
        return button
    }

    /**
     * Show Gboard-style accent popup above the anchor key.
     * Dark gray panel with blue selection highlight; grid layout, ~6 chars per row.
     */
    private fun showAccentPopup(anchor: View, chars: List<String>) {
        dismissAccentPopup()
        if (chars.isEmpty()) return

        accentPopupChars = chars
        accentPopupAnchor = anchor
        isLongPressActive = true

        val density = resources.displayMetrics.density
        val cellW = (44 * density).toInt()
        val cellH = (48 * density).toInt()
        val padH = (6 * density).toInt()
        val padV = (6 * density).toInt()
        val gap = (2 * density).toInt()
        val textSizeSp = 22f
        val maxPerRow = 6

        val columns = chars.size.coerceAtMost(maxPerRow)
        val rows = (chars.size + maxPerRow - 1) / maxPerRow

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.accent_popup_background)
            setPadding(padH, padV, padH, padV)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) elevation = 12 * density
        }

        val allTextViews = mutableListOf<TextView>()
        var charIndex = 0
        for (r in 0 until rows) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            val colsThisRow = (chars.size - charIndex).coerceAtMost(maxPerRow)
            for (c in 0 until colsThisRow) {
                val ch = chars[charIndex]
                val lp = LinearLayout.LayoutParams(cellW, cellH)
                if (c > 0) lp.marginStart = gap
                val tv = TextView(this).apply {
                    text = ch
                    textSize = textSizeSp
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    layoutParams = lp
                    background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.accent_char_normal)
                }
                allTextViews.add(tv)
                row.addView(tv)
                charIndex++
            }
            val rowLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            if (r > 0) rowLp.topMargin = gap
            row.layoutParams = rowLp
            container.addView(row)
        }
        accentPopupViews = allTextViews

        val popupW = ViewGroup.LayoutParams.WRAP_CONTENT
        val popupH = ViewGroup.LayoutParams.WRAP_CONTENT
        container.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val measuredW = container.measuredWidth
        val measuredH = container.measuredHeight

        val popup = android.widget.PopupWindow(container, measuredW, measuredH, false).apply {
            setBackgroundDrawable(null)
            isOutsideTouchable = false
            isFocusable = false
            inputMethodMode = android.widget.PopupWindow.INPUT_METHOD_NOT_NEEDED
        }

        val loc = IntArray(2)
        anchor.getLocationInWindow(loc)
        val anchorCenterX = loc[0] + anchor.width / 2
        val xOff = anchorCenterX - measuredW / 2
        val screenW = resources.displayMetrics.widthPixels
        val clampedX = xOff.coerceIn(8, screenW - measuredW - 8)
        val yOff = loc[1] - measuredH - (6 * density).toInt()

        rootView?.let { rv ->
            popup.showAtLocation(rv, Gravity.NO_GRAVITY, clampedX, yOff.coerceAtLeast(0))
        }
        accentPopup = popup

        accentPopupSelectedIndex = 0
        updateAccentPopupHighlight(0)

        anchor.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    private fun updateAccentPopupHighlight(index: Int) {
        val normalBg = ContextCompat.getDrawable(this, R.drawable.accent_char_normal)
        val selectedBg = ContextCompat.getDrawable(this, R.drawable.accent_char_selected)
        accentPopupViews.forEachIndexed { i, tv ->
            tv.background = if (i == index) selectedBg else normalBg
        }
        accentPopupSelectedIndex = index
    }

    private fun updateAccentSelectionFromTouch(rawX: Float, rawY: Float) {
        val popup = accentPopup ?: return
        val container = popup.contentView as? ViewGroup ?: return

        for (i in accentPopupViews.indices) {
            val tv = accentPopupViews.getOrNull(i) ?: continue
            val loc = IntArray(2)
            tv.getLocationOnScreen(loc)
            if (rawX >= loc[0] && rawX < loc[0] + tv.width &&
                rawY >= loc[1] && rawY < loc[1] + tv.height) {
                if (i != accentPopupSelectedIndex) {
                    updateAccentPopupHighlight(i)
                    accentPopupViews.getOrNull(i)?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                return
            }
        }
    }

    private fun commitAccentSelection() {
        if (accentPopupSelectedIndex in accentPopupChars.indices) {
            val selected = accentPopupChars[accentPopupSelectedIndex]
            when {
                isAiChatVisible -> {
                    aiChatInputText.append(selected)
                    updateAiChatInputDisplay()
                }
                isDictionaryVisible -> {
                    dictSearchText.append(selected)
                    updateDictSearchDisplay()
                }
                else -> {
                    currentInputConnection?.let { ic ->
                        ic.commitText(selected, 1)
                    }
                }
            }
            schedulePredictionUpdate()
        }
        dismissAccentPopup()
    }

    private fun dismissAccentPopup() {
        accentPopup?.dismiss()
        accentPopup = null
        accentPopupChars = emptyList()
        accentPopupViews = emptyList()
        accentPopupSelectedIndex = -1
        accentPopupAnchor = null
        isLongPressActive = false
        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
        longPressRunnable = null
    }

    /** Returns lowercase + uppercase variants for letters; symbol variants for numbers/symbols. */
    private fun getAccentsForKey(value: String): List<String> {
        val key = if (value.length == 1) value.lowercase() else value
        return ACCENT_MAP[key] ?: emptyList()
    }

    /**
     * Apply lightweight scale animation and execute action. Fires on ACTION_DOWN for immediate
     * response (supports 12-15 chars/sec). commitText/action runs synchronously; no debounce.
     * For letter/number keys with accent variants, a long-press (300ms) shows the accent popup.
     */
    private fun setKeyPressWithAnimation(button: Button, action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            button.stateListAnimator = null
            button.elevation = 0f
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            button.foreground = null
        }
        button.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val keyValue = (v as? Button)?.tag as? String ?: ""
                    val accents = getAccentsForKey(keyValue)
                    val hasAccents = accents.isNotEmpty()

                    if (!hasAccents) {
                        action()
                    }
                    Handler(Looper.getMainLooper()).post { playKeyFeedback(v) }
                    v.animate().scaleX(0.94f).scaleY(0.94f)
                        .setDuration(KEY_PRESS_ANIM_DURATION_MS)
                        .setInterpolator(LinearInterpolator()).start()

                    if (hasAccents) {
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                        longPressRunnable = Runnable { showAccentPopup(v, accents) }
                        longPressHandler.postDelayed(longPressRunnable!!, LONG_PRESS_DELAY_MS)
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isLongPressActive) {
                        updateAccentSelectionFromTouch(event.rawX, event.rawY)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                    longPressRunnable = null
                    if (isLongPressActive) {
                        if (event.actionMasked == MotionEvent.ACTION_UP) {
                            commitAccentSelection()
                        } else {
                            dismissAccentPopup()
                        }
                    } else {
                        val keyValue = (v as? Button)?.tag as? String ?: ""
                        if (getAccentsForKey(keyValue).isNotEmpty()) {
                            action()
                        }
                    }
                    v.animate().scaleX(1f).scaleY(1f)
                        .setDuration(KEY_PRESS_ANIM_DURATION_MS)
                        .setInterpolator(LinearInterpolator()).start()
                    true
                }
                else -> false
            }
        }
    }

    private fun isArabicCodePoint(codePoint: Int): Boolean {
        return codePoint in 0x0600..0x06FF ||
            codePoint in 0x0750..0x077F ||
            codePoint in 0x08A0..0x08FF ||
            codePoint in 0xFB50..0xFDFF ||
            codePoint in 0xFE70..0xFEFF
    }

    /**
     * Play sound and haptic feedback for key press. Uses cached prefs; direct performHapticFeedback.
     */
    private fun playKeyFeedback(sourceView: android.view.View) {
        refreshKeyFeedbackSettingsIfNeeded()
        if (keyFeedbackSoundEnabled) {
            sourceView.playSoundEffect(SoundEffectConstants.CLICK)
        }
        if (keyFeedbackVibrationEnabled) {
            sourceView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    private fun refreshKeyFeedbackSettingsIfNeeded() {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - keyFeedbackPrefsLastReadMs < KEY_FEEDBACK_PREFS_CACHE_MS) return
        val prefs = getSharedPreferences("deltavoice_prefs", MODE_PRIVATE)
        keyFeedbackSoundEnabled = prefs.getBoolean("sound_enabled", false)
        keyFeedbackVibrationEnabled = prefs.getBoolean("vibration_enabled", true)
        keyFeedbackPrefsLastReadMs = now
    }

    private fun refreshPredictionSettingsIfNeeded() {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - predictionPrefsLastReadMs < PREDICTION_PREFS_CACHE_MS) return
        val prefs = getSharedPreferences("deltavoice_prefs", MODE_PRIVATE)
        predictiveTextEnabled = prefs.getBoolean("predictive_text", true)
        autoCorrectionEnabled = prefs.getBoolean("auto_correction", true)
        predictionPrefsLastReadMs = now
    }

    private fun markTypingCadence() {
        val now = android.os.SystemClock.uptimeMillis()
        lastTypingIntervalMs = if (lastTypingEventMs > 0L) now - lastTypingEventMs else Long.MAX_VALUE
        lastTypingEventMs = now
    }

    /**
     * Delete one character. Used for single tap and repeat. No debounce; immediate execution.
     * If the last action was auto-correct and user immediately backspaces, undo the correction.
     */
    private fun deleteOneCharacter(triggerPredictionUpdate: Boolean) {
        markTypingCadence()
        if (isAiChatVisible) {
            if (aiChatInputText.isNotEmpty()) {
                aiChatInputText.deleteCharAt(aiChatInputText.length - 1)
                updateAiChatInputDisplay()
            }
        } else if (autoCorrectUndoable && lastAutoCorrectOriginal != null && lastAutoCorrectReplacement != null) {
            val ic = currentInputConnection
            if (ic != null) {
                val replacement = lastAutoCorrectReplacement!!
                val original = lastAutoCorrectOriginal!!
                ic.deleteSurroundingText(replacement.length + 1, 0)
                ic.commitText(original, 1)
                autoCorrectUndoable = false
                lastAutoCorrectOriginal = null
                lastAutoCorrectReplacement = null
                pendingAutoCorrect = null
            }
        } else {
            autoCorrectUndoable = false
            currentInputConnection?.deleteSurroundingText(1, 0)
        }
        if (triggerPredictionUpdate) {
            schedulePredictionUpdate()
        }
    }

    /**
     * Start backspace repeat. First delete immediate; then repeat at BACKSPACE_REPEAT_INTERVAL_MS
     * (~45 chars/sec when held). Prediction deferred until release to avoid blocking.
     */
    private fun startBackspaceRepeat(sourceView: View) {
        stopBackspaceRepeat(applyPredictionUpdate = false)
        playKeyFeedback(sourceView)
        deleteOneCharacter(triggerPredictionUpdate = false)
        isBackspaceRepeating = false
        backspaceRepeatRunnable = object : Runnable {
            override fun run() {
                isBackspaceRepeating = true
                deleteOneCharacter(triggerPredictionUpdate = false)
                backspaceRepeatHandler.postDelayed(this, BACKSPACE_REPEAT_INTERVAL_MS)
            }
        }
        backspaceRepeatHandler.postDelayed(backspaceRepeatRunnable!!, BACKSPACE_REPEAT_START_DELAY_MS)
    }

    private fun stopBackspaceRepeat(applyPredictionUpdate: Boolean) {
        backspaceRepeatRunnable?.let { backspaceRepeatHandler.removeCallbacks(it) }
        backspaceRepeatRunnable = null
        val hadRepeatingDeletes = isBackspaceRepeating
        isBackspaceRepeating = false
        if (applyPredictionUpdate && hadRepeatingDeletes) {
            schedulePredictionUpdate()
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
                    deleteOneCharacter(triggerPredictionUpdate = true)
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
                        Toast.makeText(this, getString(R.string.type_something_search), Toast.LENGTH_SHORT).show()
                    }
                }
                " " -> {
                    markTypingCadence()
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
                if (isShiftPressed && !isCapsLocked && value.length == 1 && value[0].isLetter()) {
                    isShiftPressed = false
                    updateShiftButton()
                    updateLetterKeysForShift()
                }
                    if (value.length == 1 && value[0].isLetter()) {
                        markTypingCadence()
                        schedulePredictionUpdate()
                    }
                }
            }
            return
        }
        
        val inputConnection: InputConnection? = currentInputConnection
        when (value) {
            "BACKSPACE" -> {
                deleteOneCharacter(triggerPredictionUpdate = true)
            }
            "SHIFT" -> toggleShift()
            "NUMBERS" -> toggleNumbersSymbols()
            "EMOJI" -> toggleEmojiPicker()
            "LANGUAGE" -> showLanguageSelector()
            "TOGGLE_SYMBOLS" -> toggleSymbolsMode()
            "ENTER" -> {
                val editorInfo = currentInputEditorInfo
                val actionId = editorInfo?.imeOptions?.let { opts ->
                    if ((opts and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) EditorInfo.IME_ACTION_NONE
                    else (opts and EditorInfo.IME_MASK_ACTION)
                } ?: EditorInfo.IME_ACTION_DONE
                if (actionId != EditorInfo.IME_ACTION_NONE) {
                    inputConnection?.performEditorAction(actionId)
                } else {
                    inputConnection?.commitText("\n", 1)
                }
            }
            "SEARCH" -> performSearch(inputConnection)
            "\n" -> inputConnection?.commitText("\n", 1)
            " " -> {
                markTypingCadence()
                applyAutoCorrectOnSpace(inputConnection)
                schedulePredictionUpdate()
            }
            else -> {
                val isPunctuation = value.length == 1 && value[0] in ".,:;!?)-"
                if (isPunctuation && pendingAutoCorrect != null && autoCorrectionEnabled) {
                    applyAutoCorrectOnPunctuation(inputConnection, value)
                } else {
                    autoCorrectUndoable = false
                    val charToInsert = if (value.length == 1 && value[0].isLetter()) {
                        if (isShiftPressed) value.uppercase() else value.lowercase()
                    } else value
                    inputConnection?.commitText(charToInsert, 1)
                    if (isShiftPressed && !isCapsLocked && value.length == 1 && value[0].isLetter()) {
                        isShiftPressed = false
                        updateShiftButton()
                        updateLetterKeysForShift()
                    }
                    if (value.length == 1 && value[0].isLetter()) {
                        markTypingCadence()
                        schedulePredictionUpdate()
                    }
                }
            }
        }
    }

    /**
     * Apply auto-correction when punctuation is typed (same as space, but inserts punctuation instead).
     */
    private fun applyAutoCorrectOnPunctuation(inputConnection: InputConnection?, punctuation: String) {
        val ic = inputConnection ?: return
        val correction = pendingAutoCorrect ?: run {
            ic.commitText(punctuation, 1)
            return
        }
        val textBefore = ic.getTextBeforeCursor(100, 0)?.toString() ?: ""
        val currentWord = textBefore.takeLastWhile { c -> c.isLetter() || c == '\'' }
        if (currentWord.isNotEmpty()) {
            lastAutoCorrectOriginal = currentWord
            lastAutoCorrectReplacement = correction
            autoCorrectUndoable = true
            ic.deleteSurroundingText(currentWord.length, 0)
            val correctedWithCase = matchCase(currentWord, correction)
            ic.commitText("$correctedWithCase$punctuation", 1)
            PredictionProvider.recordWord(currentKeyboardLanguage, correction)
            pendingAutoCorrect = null
        } else {
            ic.commitText(punctuation, 1)
            pendingAutoCorrect = null
        }
    }

    /**
     * Apply auto-correction when space is pressed (Gboard behavior).
     * If a pending correction exists, replace the current word with the correction + space.
     * Otherwise just insert a space.
     */
    private fun applyAutoCorrectOnSpace(inputConnection: InputConnection?) {
        val ic = inputConnection ?: run {
            pendingAutoCorrect = null
            return
        }
        val correction = pendingAutoCorrect
        if (correction != null && autoCorrectionEnabled) {
            val textBefore = ic.getTextBeforeCursor(100, 0)?.toString() ?: ""
            val currentWord = textBefore.takeLastWhile { c -> c.isLetter() || c == '\'' }
            if (currentWord.isNotEmpty()) {
                lastAutoCorrectOriginal = currentWord
                lastAutoCorrectReplacement = correction
                autoCorrectUndoable = true
                ic.deleteSurroundingText(currentWord.length, 0)
                val correctedWithCase = matchCase(currentWord, correction)
                ic.commitText("$correctedWithCase ", 1)
                PredictionProvider.recordWord(currentKeyboardLanguage, correction)
                val contextWords = textBefore.dropLast(currentWord.length).trim()
                    .split(Regex("\\s+")).filter { it.isNotBlank() }
                if (contextWords.isNotEmpty()) {
                    PredictionProvider.recordSequence(
                        currentKeyboardLanguage,
                        (contextWords.takeLast(2) + correction).takeLast(3)
                    )
                }
                pendingAutoCorrect = null
                return
            }
        }
        pendingAutoCorrect = null
        ic.commitText(" ", 1)
    }

    /**
     * Match the case pattern of the original word to the correction.
     */
    private fun matchCase(original: String, correction: String): String {
        if (original.isEmpty() || correction.isEmpty()) return correction
        return when {
            original.all { it.isUpperCase() } -> correction.uppercase()
            original[0].isUpperCase() -> correction.replaceFirstChar { it.uppercase() }
            else -> correction
        }
    }
    
    /**
     * Schedule a delayed prediction update (debounced)
     */
    private fun schedulePredictionUpdate() {
        refreshPredictionSettingsIfNeeded()
        if (!predictiveTextEnabled) {
            // #region agent log
            AgentDebugLog.log("H1", "MainKeyboardService.schedulePredictionUpdate", "skipped", mapOf("reason" to "predictive_off"))
            // #endregion
            return
        }
        if (isNumbersMode) {
            // #region agent log
            AgentDebugLog.log("H1", "MainKeyboardService.schedulePredictionUpdate", "skipped", mapOf("reason" to "numbers_mode"))
            // #endregion
            return
        }
        val delayMs = if (lastTypingIntervalMs in 1..FAST_TYPING_INTERVAL_MS) {
            PREDICTION_DELAY_FAST_MS
        } else {
            PREDICTION_DELAY_NORMAL_MS
        }
        // #region agent log
        AgentDebugLog.log("H1", "MainKeyboardService.schedulePredictionUpdate", "scheduled", mapOf("delayMs" to delayMs))
        // #endregion
        predictionComputeJob?.cancel()
        predictionRunnable?.let { predictionHandler.removeCallbacks(it) }
        predictionRunnable = Runnable { updatePredictions() }
        predictionHandler.postDelayed(predictionRunnable!!, delayMs)
    }
    
    /**
     * Update predictive text suggestions based on current input
     */
    private fun updatePredictions() {
        // #region agent log
        AgentDebugLog.log("H2", "MainKeyboardService.updatePredictions", "entry", emptyMap())
        // #endregion
        val root = rootView ?: run {
            // #region agent log
            AgentDebugLog.log("H2", "MainKeyboardService.updatePredictions", "early_exit", mapOf("reason" to "rootView_null"))
            // #endregion
            return
        }
        if (isAiChatVisible) {
            // #region agent log
            AgentDebugLog.log("H2", "MainKeyboardService.updatePredictions", "early_exit", mapOf("reason" to "ai_chat_visible"))
            // #endregion
            return
        }
        val keyboardContainer = root.findViewById<View>(R.id.keyboard_container)
        if (keyboardContainer?.visibility != View.VISIBLE) {
            // #region agent log
            AgentDebugLog.log("H2", "MainKeyboardService.updatePredictions", "early_exit", mapOf("reason" to "keyboard_container_not_visible", "kbVis" to (keyboardContainer?.visibility ?: -1)))
            // #endregion
            return
        }
        val aiRow = root.findViewById<View>(R.id.ai_features_row)
        val predictionsContainer = root.findViewById<View>(R.id.predictions_container)
        val predictionsRow = root.findViewById<LinearLayout>(R.id.predictions_row)
        if (aiRow == null || predictionsContainer == null || predictionsRow == null) {
            // #region agent log
            AgentDebugLog.log("H2", "MainKeyboardService.updatePredictions", "early_exit", mapOf("reason" to "missing_views", "aiRowNull" to (aiRow == null), "predNull" to (predictionsContainer == null)))
            // #endregion
            return
        }

        refreshPredictionSettingsIfNeeded()
        val autoCorrection = autoCorrectionEnabled

        val textBefore = currentInputConnection?.getTextBeforeCursor(100, 0)?.toString() ?: run {
            // #region agent log
            AgentDebugLog.log("H2", "MainKeyboardService.updatePredictions", "early_exit", mapOf("reason" to "textBefore_null"))
            // #endregion
            if (!isClipboardVisible) showIconsHidePredictions(aiRow, predictionsContainer)
            return
        }
        val currentWord = textBefore.takeLastWhile { c -> c.isLetter() || c == '\'' }

        if (currentWord.isEmpty()) {
            // #region agent log
            AgentDebugLog.log("H5", "MainKeyboardService.updatePredictions", "early_exit", mapOf("reason" to "currentWord_empty", "textBeforeLen" to textBefore.length))
            // #endregion
            lastPredictionQuery = ""
            lastRenderedSuggestions = emptyList()
            if (!isClipboardVisible) showIconsHidePredictions(aiRow, predictionsContainer)
            return
        }

        val query = currentWord.lowercase(Locale.ROOT)
        if (query == lastPredictionQuery) {
            // #region agent log
            AgentDebugLog.log("H5", "MainKeyboardService.updatePredictions", "early_exit", mapOf("reason" to "same_query", "query" to query))
            // #endregion
            return
        }
        lastPredictionQuery = query

        val lang = currentKeyboardLanguage
        val contextBefore = textBefore.dropLast(currentWord.length).trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        predictionComputeJob?.cancel()
        PredictionProvider.getPredictions(
            prefix = currentWord,
            contextBefore = contextBefore,
            lang = lang,
            limit = 5,
            includeCorrections = autoCorrection
        ) { result ->
            if (query != lastPredictionQuery) return@getPredictions
            pendingAutoCorrect = result.autoCorrect
            if (result.suggestions.isNotEmpty()) {
                isClipboardVisible = false
                showPredictionsHideIcons(aiRow, predictionsContainer, predictionsRow, result.suggestions, result.autoCorrect)
            } else if (!isClipboardVisible) {
                pendingAutoCorrect = null
                lastRenderedSuggestions = emptyList()
                showIconsHidePredictions(aiRow, predictionsContainer)
            }
        }
    }
    
    private fun showPredictionsHideIcons(aiRow: View, predictionsContainer: View, predictionsRow: LinearLayout, suggestions: List<String>, autoCorrect: String? = null) {
        val topBar = (aiRow.parent as? View)
        // #region agent log
        AgentDebugLog.log("H4", "MainKeyboardService.showPredictionsHideIcons", "before_visibility", mapOf(
            "topBarVis" to (topBar?.visibility ?: -1),
            "aiRowVis" to aiRow.visibility,
            "predVis" to predictionsContainer.visibility,
            "predParentIsTopBar" to (predictionsContainer.parent == topBar),
            "suggestionCount" to suggestions.size
        ))
        // #endregion
        if (suggestions == lastRenderedSuggestions &&
            aiRow.visibility == View.GONE &&
            predictionsContainer.visibility == View.VISIBLE) {
            topBar?.visibility = View.VISIBLE
            return
        }

        lastRenderedSuggestions = suggestions.toList()
        // Keep top_bar_container visible: predictions_container is inside it; GONE hid the whole suggestion strip.
        topBar?.visibility = View.VISIBLE
        aiRow.visibility = View.GONE
        predictionsContainer.visibility = View.VISIBLE
        // #region agent log
        AgentDebugLog.log("H4", "MainKeyboardService.showPredictionsHideIcons", "after_visibility", mapOf(
            "topBarVis" to (topBar?.visibility ?: -1),
            "aiRowVis" to aiRow.visibility,
            "predVis" to predictionsContainer.visibility
        ))
        // #endregion
        predictionsRow.removeAllViews()
        
        suggestions.forEach { word ->
            val isAutoCorrect = autoCorrect != null && word == autoCorrect
            val chip = Button(this).apply {
                text = word
                textSize = if (isAutoCorrect) 15f else 14f
                setTextColor(if (isAutoCorrect) Color.WHITE else Color.parseColor("#EDEFF4"))
                if (isAutoCorrect) {
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                }
                setPadding(16.dpToPx(), 8.dpToPx(), 16.dpToPx(), 8.dpToPx())
                background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.glass_key_background)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 6.dpToPx()
                }
                setOnClickListener {
                    playKeyFeedback(it)
                    pendingAutoCorrect = null
                    autoCorrectUndoable = false
                    insertPrediction(word)
                    showIconsHidePredictions(aiRow, predictionsContainer)
                }
            }
            predictionsRow.addView(chip)
        }
    }
    
    private fun showIconsHidePredictions(aiRow: View, predictionsContainer: View) {
        val topBar = (aiRow.parent as? View)
        if (aiRow.visibility == View.VISIBLE && predictionsContainer.visibility == View.GONE) return
        lastRenderedSuggestions = emptyList()
        pendingAutoCorrect = null
        isClipboardVisible = false
        topBar?.visibility = View.VISIBLE
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
        PredictionProvider.recordWord(currentKeyboardLanguage, word)
        val contextWords = textBefore.dropLast(wordLen).trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (contextWords.isNotEmpty()) {
            PredictionProvider.recordSequence(currentKeyboardLanguage, (contextWords.takeLast(2) + word).takeLast(3))
        }
    }

    /**
     * Perform web search with current text
     */
    private fun performSearch(inputConnection: InputConnection?) {
        // Try to get text from input field
        val extractedText = inputConnection?.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
        val searchText = extractedText?.text?.toString()?.trim()
        
        if (searchText.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.type_something_search), Toast.LENGTH_SHORT).show()
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
            
            Toast.makeText(this, getString(R.string.searching_label, searchText), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.could_not_open_search), Toast.LENGTH_SHORT).show()
            android.util.Log.e("DeltaVoice", "Search error: ${e.message}")
        }
    }
    
    /**
     * Toggle shift state
     */
    private fun toggleShift() {
        val now = android.os.SystemClock.uptimeMillis()
        val isDoubleTap = now - lastShiftTapTimeMs in 0..500
        lastShiftTapTimeMs = now

        when {
            // Off → single shift
            !isShiftPressed && !isCapsLocked -> {
                isShiftPressed = true
                isCapsLocked = false
            }
            // Single shift → caps lock (double tap)
            isShiftPressed && !isCapsLocked && isDoubleTap -> {
                isShiftPressed = true
                isCapsLocked = true
            }
            // Caps lock → off
            isCapsLocked -> {
                isShiftPressed = false
                isCapsLocked = false
            }
            // Fallback: single shift → off
            else -> {
                isShiftPressed = false
                isCapsLocked = false
            }
        }
        updateShiftButton()
        updateLetterKeysForShift()
    }

    /**
     * Update shift button appearance
     */
    private fun updateShiftButton() {
        shiftButton?.let { button ->
            when {
                isCapsLocked -> {
                    // Stronger highlight for caps lock
                    button.setBackgroundColor(Color.parseColor("#99FFFFFF"))
                }
                isShiftPressed -> {
                    // Lighter highlight for single-shift
                    button.setBackgroundColor(Color.parseColor("#33FFFFFF"))
                }
                else -> {
                    // Default background when shift is off
                    button.background = ContextCompat.getDrawable(this, R.drawable.glass_key_special_background)
                }
            }
        }
    }

    /**
     * Update visible letter key labels to match current shift state
     */
    private fun updateLetterKeysForShift() {
        val view = rootView ?: return
        val rows = listOf(
            view.findViewById<LinearLayout>(R.id.row_qwerty),
            view.findViewById<LinearLayout>(R.id.row_asdf),
            view.findViewById<LinearLayout>(R.id.row_zxcv),
            view.findViewById<LinearLayout>(R.id.row_numbers)
        )

        rows.forEach { row ->
            row ?: return@forEach
            for (i in 0 until row.childCount) {
                val child = row.getChildAt(i) as? Button ?: continue
                val value = (child.tag as? String) ?: child.text.toString()
                if (value.length == 1 && value[0].isLetter()) {
                    child.text = if (isShiftPressed) {
                        value.uppercase()
                    } else {
                        value.lowercase()
                    }
                }
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
        updateRecordingMicColor(false)
        stopRecordingTimer(reset = true)
    }

    /**
     * Start voice input recognition
     */
    private fun startVoiceInput() {
        // Don't start if recording
        if (isRecording) {
            Toast.makeText(this, getString(R.string.stop_recording_first), Toast.LENGTH_SHORT).show()
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
        if (!isRecording) {
            updateRecordingMicColor(false)
            stopRecordingTimer(reset = true)
        }
    }

    /**
     * Insert text into the current input field
     */
    private fun insertText(text: String) {
        val ic = currentInputConnection
        if (ic != null) {
            ic.commitText(text, 1)
        } else {
            clipboardManager?.setPrimaryClip(ClipData.newPlainText("deltavoice", text))
            Toast.makeText(this, getString(R.string.text_copied_clipboard), Toast.LENGTH_SHORT).show()
        }
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
            Toast.makeText(this, getString(R.string.no_text_to_speak), Toast.LENGTH_SHORT).show()
            return
        }
        
        Toast.makeText(this, getString(R.string.generating_speech), Toast.LENGTH_SHORT).show()
        
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
                        Toast.makeText(this@MainKeyboardService, getString(R.string.playing_speech), Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, getString(R.string.tts_not_initialized), Toast.LENGTH_SHORT).show()
            return
        }
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    /**
     * Show more options (Calculator & Dictionary), hide keyboard.
     * Voice panel and three-dots panel are mutually exclusive.
     */
    private fun showMoreOptions() {
        hideAllOverlays()
        hideTopBarsForOverlay()
        applyImeKeyboardContainerVisible(false)
        moreOptionsContainer.visibility = View.VISIBLE
    }

    /**
     * Hide more options, show keyboard
     */
    private fun hideMoreOptions() {
        moreOptionsContainer.visibility = View.GONE
        applyImeKeyboardContainerVisible(true)
        showTopBarsAfterOverlay()
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
                    applyImeKeyboardContainerVisible(true)
                }
                KeyboardMode.AI -> {
                    // Show AI features container (emoji grid, language selector)
                    aiFeaturesContainer.visibility = View.VISIBLE
                    // Always show keyboard
                    applyImeKeyboardContainerVisible(true)
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
            Toast.makeText(this, getString(R.string.toast_voice_input_mode), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, getString(R.string.toast_text_to_speech_mode), Toast.LENGTH_SHORT).show()
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
        val availableLanguages = KeyboardLayouts.layouts.map { (code, layout) ->
            code to layout.displayName
        }.sortedBy { it.second }
        
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
        
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                350.dpToPx()
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
        // Use isFocusable = false so the popup does not steal focus and hide the keyboard
        val popupWindow = android.widget.PopupWindow(
            popupView,
            280.dpToPx(),
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 10f
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            isFocusable = false  // Prevents keyboard from hiding when language selector opens
            inputMethodMode = android.widget.PopupWindow.INPUT_METHOD_NOT_NEEDED
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
        PredictionProvider.switchLanguage(languageCode)

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
        
        Toast.makeText(this, getString(R.string.keyboard_lang, languageName), Toast.LENGTH_SHORT).show()
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
                numbersButton?.text = getString(R.string.numbers_mode_abc)
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
                numbersButton?.text = getString(R.string.numbers_mode_symbols)
                // Ensure shift button and key labels reflect current shift state
                updateShiftButton()
                updateLetterKeysForShift()
            }
        }
    }
    
    /**
     * Toggle between letters and numbers/symbols keyboard
     */
    private fun toggleNumbersSymbols() {
        isNumbersMode = !isNumbersMode
        isSymbolsMode = false // Reset symbols mode when toggling
        predictionComputeJob?.cancel()
        lastPredictionQuery = ""
        predictionRunnable?.let { predictionHandler.removeCallbacks(it) }
        rootView?.let { v ->
            v.findViewById<View>(R.id.top_bar_container)?.visibility = View.VISIBLE
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
     * Show translation language selection dialog.
     * Language names are displayed in the user's device language.
     */
    private fun showTranslationLanguageSelector() {
        val languages = listOf(
            Locale.ENGLISH, Locale("es"), Locale("fr"), Locale("de"), Locale("it"),
            Locale("pt"), Locale("ru"), Locale("ja"), Locale("ko"), Locale("zh"),
            Locale("ar"), Locale("hi")
        )
        val displayLocale = Locale.getDefault()
        val languageNames = languages.map { it.getDisplayName(displayLocale) }.toTypedArray()
        
        android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_translation_language))
            .setItems(languageNames) { _, which ->
                targetTranslationLanguage = languages[which]
                val name = languages[which].getDisplayName(displayLocale)
                translateButton.text = getString(R.string.translate_to, name)
                Toast.makeText(this, getString(R.string.translation_target_set, name), Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, getString(R.string.no_text_to_translate), Toast.LENGTH_SHORT).show()
            return
        }
        
        Toast.makeText(this, getString(R.string.translating), Toast.LENGTH_SHORT).show()
        
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
                    Toast.makeText(this@MainKeyboardService, getString(R.string.translation_complete), Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, getString(R.string.no_text_to_convert), Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, getString(R.string.converting_text_voice), Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(this@MainKeyboardService, getString(R.string.playing_converted_voice), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainKeyboardService, getString(R.string.no_audio_returned), Toast.LENGTH_SHORT).show()
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

        Toast.makeText(this, getString(R.string.creating_voice_clone), Toast.LENGTH_SHORT).show()
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
        audioDurationText.text = getString(R.string.status_connecting)

        serviceScope.launch {
            try {
                android.util.Log.d("DeltaVoice", "Coroutine launched, calling Supabase backend...")
                audioDurationText.text = getString(R.string.processing)
                
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
        // TTS fallback: backend returned text but no audio (cloud TTS failed)
        if (response.ttsFallback == true) {
            val text = response.translatedText?.takeIf { it.isNotBlank() }
            if (text != null) {
                val fallbackLang = response.targetLanguage?.takeIf { it.isNotBlank() } ?: "en"
                android.util.Log.d("DeltaVoice", "TTS fallback: synthesizing with device TTS, lang=$fallbackLang")
                handleTtsFallback(text, fallbackLang, workflowType)
                return
            }
        }

        val audioBase64 = response.convertedAudioBase64
        val hasAudio = !audioBase64.isNullOrBlank() && audioBase64.length > 100
        
        // When no audio returned but we have text (complete/voice-only), try device TTS fallback
        if (!hasAudio && (workflowType == "complete" || workflowType == "voice-only")) {
            val text = response.translatedText?.takeIf { it.isNotBlank() }
            if (text != null) {
                val fallbackLang = response.targetLanguage?.takeIf { it.isNotBlank() } ?: "en"
                android.util.Log.d("DeltaVoice", "No cloud audio - trying device TTS fallback, lang=$fallbackLang")
                handleTtsFallback(text, fallbackLang, workflowType)
                return
            }
        }
        
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
                    Toast.makeText(this, getString(R.string.ready_tap_hear_send), Toast.LENGTH_LONG).show()
                } else {
                    android.util.Log.w("DeltaVoice", "No audio returned - showing text only result")
                    Toast.makeText(this, getString(R.string.text_translated_no_audio), Toast.LENGTH_LONG).show()
                    // Don't hide UI - let user see the result and retry if needed
                    audioDurationText.text = getString(R.string.status_no_audio)
                    resetButtonState()
                }
            }
            
            "voice-only" -> {
                // Translate My Same Voice (Voice Cloning): Your voice in different language
                if (hasAudio) {
                    android.util.Log.d("DeltaVoice", "Saving cloned voice audio...")
                    saveAndShowProcessedAudio(audioBase64!!, "mp3")
                    Toast.makeText(this, getString(R.string.voice_cloned_tap_hear), Toast.LENGTH_LONG).show()
                } else {
                    android.util.Log.w("DeltaVoice", "No audio returned for voice cloning")
                    Toast.makeText(this, getString(R.string.voice_cloning_failed), Toast.LENGTH_LONG).show()
                    // Don't hide UI - let user retry
                    audioDurationText.text = getString(R.string.status_failed)
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
                    Toast.makeText(this, getString(R.string.translated_text_inserted), Toast.LENGTH_SHORT).show()
                    // For text-only, hide the UI since there's no audio to play
                    hideVoiceProcessingUI()
                    recordingFilePath = null
                } else {
                    android.util.Log.w("DeltaVoice", "No text detected")
                    Toast.makeText(this, getString(R.string.no_speech_detected_try), Toast.LENGTH_LONG).show()
                    // Don't hide UI - let user retry
                    audioDurationText.text = getString(R.string.status_no_speech)
                    resetButtonState()
                }
            }
            
            else -> {
                response.translatedText?.takeIf { it.isNotBlank() }?.let { insertText(it) }
                if (hasAudio) {
                    saveAndShowProcessedAudio(audioBase64!!, "mp3")
                }
                Toast.makeText(this, getString(R.string.done_exclamation), Toast.LENGTH_SHORT).show()
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

    /**
     * When cloud TTS fails, synthesize with device Android TTS and show result.
     */
    private fun handleTtsFallback(text: String, targetLang: String, workflowType: String) {
        if (!ttsInitialized) {
            android.util.Log.w("DeltaVoice", "TTS fallback: TTS not initialized")
            insertText(text)
            Toast.makeText(this, getString(R.string.text_ready_device_voice_unavailable), Toast.LENGTH_LONG).show()
            if (workflowType == "text-only") hideVoiceProcessingUI() else resetButtonState()
            return
        }

        when (workflowType) {
            "text-only" -> {
                insertText(text)
                Toast.makeText(this, getString(R.string.translated_text_inserted), Toast.LENGTH_SHORT).show()
                hideVoiceProcessingUI()
                recordingFilePath = null
                return
            }
        }

        // For complete/voice-only: insert text and synthesize with device TTS
        insertText(text)
        audioDurationText.text = getString(R.string.status_device_voice)

        // Map language codes to proper Locale for TTS (some need region for best results)
        val locale = when (val code = targetLang.trim().lowercase()) {
            "" -> java.util.Locale.getDefault()
            "zh" -> java.util.Locale.SIMPLIFIED_CHINESE
            "ja" -> java.util.Locale.JAPANESE
            "ko" -> java.util.Locale.KOREAN
            "ar" -> java.util.Locale("ar")
            "hi" -> java.util.Locale("hi")
            else -> try {
                java.util.Locale.forLanguageTag(code.replace("_", "-"))
            } catch (_: Exception) {
                java.util.Locale.getDefault()
            }
        }
        val tts = textToSpeech ?: return
        val prevLang = selectedLanguage
        var setOk = tts.setLanguage(locale)
        if (setOk == TextToSpeech.LANG_MISSING_DATA || setOk == TextToSpeech.LANG_NOT_SUPPORTED) {
            setOk = tts.setLanguage(java.util.Locale.getDefault())
        }
        if (setOk == TextToSpeech.LANG_MISSING_DATA || setOk == TextToSpeech.LANG_NOT_SUPPORTED) {
            android.util.Log.w("DeltaVoice", "TTS fallback: language $targetLang not available, using default")
        }

        val outFile = File(cacheDir, "tts_fallback_${System.currentTimeMillis()}.wav")
        val utteranceId = "tts_fallback_${System.currentTimeMillis()}"

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                Handler(Looper.getMainLooper()).post {
                    try {
                        if (outFile.exists() && outFile.length() > 0) {
                            val bytes = outFile.readBytes()
                            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                            saveAndShowProcessedAudio(base64, "wav")
                            Toast.makeText(this@MainKeyboardService,
                                "✓ Using device voice (cloud service unavailable)", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this@MainKeyboardService,
                                "✓ Text ready (device voice failed)", Toast.LENGTH_LONG).show()
                            resetButtonState()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("DeltaVoice", "TTS fallback error", e)
                        Toast.makeText(this@MainKeyboardService,
                            "✓ Text ready (audio failed)", Toast.LENGTH_LONG).show()
                        resetButtonState()
                    } finally {
                        outFile.delete()
                        tts.setLanguage(prevLang)
                    }
                }
            }
            @Suppress("DEPRECATION")
            override fun onError(utteranceId: String?) {
                Handler(Looper.getMainLooper()).post {
                    outFile.delete()
                    tts.setLanguage(prevLang)
                    Toast.makeText(this@MainKeyboardService,
                        "✓ Text ready (device voice failed)", Toast.LENGTH_LONG).show()
                    resetButtonState()
                }
            }
            override fun onError(utteranceId: String?, errorCode: Int) {
                Handler(Looper.getMainLooper()).post {
                    outFile.delete()
                    tts.setLanguage(prevLang)
                    Toast.makeText(this@MainKeyboardService,
                        "✓ Text ready (device voice failed)", Toast.LENGTH_LONG).show()
                    resetButtonState()
                }
            }
        })

        val params = Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId) }
        val result = tts.synthesizeToFile(text, params, outFile, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            tts.setLanguage(prevLang)
            Toast.makeText(this, getString(R.string.text_ready_device_voice_failed), Toast.LENGTH_LONG).show()
            resetButtonState()
        }
    }
    
    private fun resetProcessingUI() {
        hideVoiceProcessingUI()
        resetButtonState()
    }
    
    private fun resetButtonState() {
        voiceStep2ActionButton()?.apply {
            isEnabled = true
            text = "  Done"
            background = ContextCompat.getDrawable(this@MainKeyboardService, R.drawable.voice_mode_button_purple)
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_ai_mode, 0, 0, 0)
            compoundDrawables.forEach { drawable ->
                drawable?.setTint(Color.WHITE)
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
            Toast.makeText(this, getString(R.string.already_recording), Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this, getString(R.string.failed_create_recordings_dir), Toast.LENGTH_SHORT).show()
                    return
                }
            }
            
            val recordingFile = File(recordingsDir, fileName)
            recordingFilePath = recordingFile.absolutePath
            
            // Verify we can create the file
            val parentDir = recordingFile.parentFile
            if (parentDir == null || !parentDir.exists()) {
                Toast.makeText(this, getString(R.string.cannot_access_recordings_dir), Toast.LENGTH_SHORT).show()
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
                    if (recordingAction == RecordingAction.COMPLETE_WORKFLOW) {
                        startRecordingTimer()
                        updateRecordingMicColor(true)
                    }
                    
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
                Toast.makeText(this, getString(R.string.failed_init_mediarecorder), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.error_starting_recording, e.message ?: ""), Toast.LENGTH_LONG).show()
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
            
            val action = recordingAction
            recordingAction = RecordingAction.TRANSCRIBE

            // Update UI
            voiceButton.setImageResource(R.drawable.ic_mic)
            if (action == RecordingAction.COMPLETE_WORKFLOW) {
                stopRecordingTimer(reset = false)
                updateRecordingMicColor(false)
            }
            
            // Only show Toast for actions that don't have UI transition
            if (action != RecordingAction.COMPLETE_WORKFLOW) {
                Toast.makeText(this, getString(R.string.recording_stopped), Toast.LENGTH_SHORT).show()
            }

            // Process recording based on action
            filePath?.let { path ->
                val audioFile = File(path)
                if (audioFile.exists() && audioFile.length() > 0) {
                    when (action) {
                        RecordingAction.TRANSCRIBE -> {
                            Toast.makeText(this, getString(R.string.converting_speech_to_text), Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this, getString(R.string.recording_file_empty_or_missing), Toast.LENGTH_SHORT).show()
                    recordingFilePath = null
                }
            }
            // removed recordingFilePath = null here because COMPLETE_WORKFLOW needs it later
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.error_stopping_recording, e.message ?: ""), Toast.LENGTH_LONG).show()
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
        if (::voiceButton.isInitialized) {
            voiceButton.setImageResource(R.drawable.ic_mic)
        }
    }

    /** Keyboard layout is inflated and bound (works even if the IME window has never been shown). */
    fun isOverlayAttachReady(): Boolean = ensureKeyboardLayoutInflated()

    fun attachDictionaryFromOverlay(panel: android.view.View, onDismiss: () -> Unit): Boolean {
        if (!ensureKeyboardLayoutInflated()) return false
        overlayBubbleKeyboardIsolated = true
        overlayDictionaryRoot = panel
        overlayDictionaryClose = onDismiss
        dictionaryContainer = panel.findViewById(R.id.dictionary_include) ?: return false
        setupDictionary(panel)
        showDictionary()
        snapImeTypingUiToVisibleForBubbleOverlay()
        return true
    }

    fun dismissDictionaryOverlayFromBubble() {
        hideDictionary()
    }

    fun attachVideoRecordingFromOverlay(panel: android.view.View, onDismiss: () -> Unit): Boolean {
        if (!ensureKeyboardLayoutInflated()) return false
        overlayBubbleKeyboardIsolated = true
        setupVideoRecording(panel)
        showVideoRecording()
        snapImeTypingUiToVisibleForBubbleOverlay()
        return true
    }

    fun dismissVideoOverlayFromBubble() {
        overlayBubbleKeyboardIsolated = false
        rootView?.let { setupVideoRecording(it) }
        hideVideoRecording()
    }

    fun attachAiChatFromOverlay(panel: android.view.View, onDismiss: () -> Unit): Boolean {
        if (!ensureKeyboardLayoutInflated()) return false
        overlayBubbleKeyboardIsolated = true
        aiChatContainer = panel.findViewById(R.id.ai_chat_container) ?: return false
        setupAiChat(panel)
        showAiChat()
        snapImeTypingUiToVisibleForBubbleOverlay()
        return true
    }

    fun dismissAiChatOverlayFromBubble() {
        overlayBubbleKeyboardIsolated = false
        rootView?.let { setupAiChat(it) }
        hideAiChat()
    }

    fun attachAiWritingToolsFromOverlay(panel: android.view.View, onDismiss: () -> Unit): Boolean {
        if (!ensureKeyboardLayoutInflated()) return false
        overlayBubbleKeyboardIsolated = true
        aiWritingToolsContainer = panel.findViewById(R.id.ai_writing_tools_include) ?: return false
        setupAiWritingTools(panel)
        showAiWritingTools()
        snapImeTypingUiToVisibleForBubbleOverlay()
        return true
    }

    fun dismissAiWritingOverlayFromBubble() {
        overlayBubbleKeyboardIsolated = false
        rootView?.let { setupAiWritingTools(it) }
        hideAiWritingTools()
    }

    fun attachVoiceRecordingFromOverlay(panel: android.view.View, onDismiss: () -> Unit): Boolean {
        if (!ensureKeyboardLayoutInflated()) return false
        overlayBubbleKeyboardIsolated = true
        voiceRecordingContainer = panel.findViewById(R.id.voice_recording_container) ?: return false
        voiceProcessingStep2Container = panel.findViewById(R.id.voice_processing_step2_container) ?: return false
        keyboardSpinnerLanguage = panel.findViewById(R.id.keyboard_spinner_language)
        keyboardSpinnerVoice = panel.findViewById(R.id.keyboard_spinner_voice)
        keyboardCardFull = panel.findViewById(R.id.keyboard_option_full)
        keyboardCardVoice = panel.findViewById(R.id.keyboard_option_voice)
        keyboardCardText = panel.findViewById(R.id.keyboard_option_text)
        recordingStatusText = panel.findViewById(R.id.recording_status_text)
        recordingMicButton = panel.findViewById(R.id.btn_recording_mic)
        recordingTimerText = panel.findViewById(R.id.recording_timer_text)
        playRecordingButton = panel.findViewById(R.id.btn_play_recording)
        sendRawRecordingButton = panel.findViewById(R.id.btn_send_raw_recording)
        audioDurationText = panel.findViewById(R.id.audio_duration_text)
        audioPlaybackSeekBar = panel.findViewById(R.id.audio_playback_seekbar)
        setupRecordingUI(panel)
        setupProcessingUI(panel)
        showRecordingUI()
        snapImeTypingUiToVisibleForBubbleOverlay()
        return true
    }

    fun dismissVoiceOverlayFromBubble() {
        overlayBubbleKeyboardIsolated = false
        rootView?.let {
            voiceRecordingContainer = it.findViewById(R.id.voice_recording_container)
            voiceProcessingStep2Container = it.findViewById(R.id.voice_processing_step2_container)
            keyboardSpinnerLanguage = it.findViewById(R.id.keyboard_spinner_language)
            keyboardSpinnerVoice = it.findViewById(R.id.keyboard_spinner_voice)
            keyboardCardFull = it.findViewById(R.id.keyboard_option_full)
            keyboardCardVoice = it.findViewById(R.id.keyboard_option_voice)
            keyboardCardText = it.findViewById(R.id.keyboard_option_text)
            recordingStatusText = it.findViewById(R.id.recording_status_text)
            recordingMicButton = it.findViewById(R.id.btn_recording_mic)
            recordingTimerText = it.findViewById(R.id.recording_timer_text)
            playRecordingButton = it.findViewById(R.id.btn_play_recording)
            sendRawRecordingButton = it.findViewById(R.id.btn_send_raw_recording)
            audioDurationText = it.findViewById(R.id.audio_duration_text)
            audioPlaybackSeekBar = it.findViewById(R.id.audio_playback_seekbar)
            setupRecordingUI(it)
            setupProcessingUI(it)
        }
        hideRecordingUI()
    }

    /**
     * Bubble overlay removed without going through a feature-specific dismiss: rebind IME [rootView] widgets
     * so typing UI is not left pointing at detached overlay views.
     */
    fun dismissAllBubbleOverlayState() {
        if (!overlayBubbleKeyboardIsolated) return
        overlayBubbleKeyboardIsolated = false
        videoUiHost = null
        overlayDictionaryRoot = null
        overlayDictionaryClose = null
        val root = rootView ?: return
        try {
            voiceRecordingContainer = root.findViewById(R.id.voice_recording_container)
            voiceProcessingStep2Container = root.findViewById(R.id.voice_processing_step2_container)
            keyboardSpinnerLanguage = root.findViewById(R.id.keyboard_spinner_language)
            keyboardSpinnerVoice = root.findViewById(R.id.keyboard_spinner_voice)
            keyboardCardFull = root.findViewById(R.id.keyboard_option_full)
            keyboardCardVoice = root.findViewById(R.id.keyboard_option_voice)
            keyboardCardText = root.findViewById(R.id.keyboard_option_text)
            recordingStatusText = root.findViewById(R.id.recording_status_text)
            recordingMicButton = root.findViewById(R.id.btn_recording_mic)
            recordingTimerText = root.findViewById(R.id.recording_timer_text)
            playRecordingButton = root.findViewById(R.id.btn_play_recording)
            sendRawRecordingButton = root.findViewById(R.id.btn_send_raw_recording)
            audioDurationText = root.findViewById(R.id.audio_duration_text)
            audioPlaybackSeekBar = root.findViewById(R.id.audio_playback_seekbar)
            setupRecordingUI(root)
            setupProcessingUI(root)
        } catch (_: Exception) {
        }
        try {
            setupVideoRecording(root)
        } catch (_: Exception) {
        }
        try {
            aiChatContainer = root.findViewById(R.id.ai_chat_container)
            setupAiChat(root)
        } catch (_: Exception) {
        }
        try {
            aiWritingToolsContainer = root.findViewById(R.id.ai_writing_tools_include)
            setupAiWritingTools(root)
        } catch (_: Exception) {
        }
        try {
            dictionaryContainer = root.findViewById(R.id.dictionary_include)
        } catch (_: Exception) {
        }
        try {
            hideRecordingUI()
            if (isVideoRecordingVisible || isVideoRecording) hideVideoRecording()
            if (isVideoPreviewVisible) hideVideoPreview()
            if (isAiChatVisible) hideAiChat()
            if (isAiWritingToolsVisible) hideAiWritingTools()
            dictionaryContainer.visibility = View.GONE
            isDictionaryVisible = false
        } catch (_: Exception) {
        }
    }

    private fun cleanupBeforeImeTakeover() {
        overlayBubbleKeyboardIsolated = false
        videoUploadReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {
            }
        }
        videoUploadReceiver = null
        audioUploadReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {
            }
        }
        audioUploadReceiver = null
        clipboardListener?.let { clipboardManager?.removePrimaryClipChangedListener(it) }
        clipboardListener = null
        clipboardManager = null
        releaseCameraAndStopThread()
        stopBackspaceRepeat(applyPredictionUpdate = false)
        predictionComputeJob?.cancel()
        predictionRunnable?.let { predictionHandler.removeCallbacks(it) }
        speechRecognizer?.destroy()
        speechRecognizer = null
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        try {
            releaseMediaRecorder()
        } catch (_: Exception) {
        }
        stopRecordingPlayback()
        rootView = null
    }

    override fun onDestroy() {
        if (standaloneOverlayInstance === this) standaloneOverlayInstance = null
        serviceInstance = null
        videoUploadReceiver?.let { unregisterReceiver(it) }
        videoUploadReceiver = null
        audioUploadReceiver?.let { unregisterReceiver(it) }
        audioUploadReceiver = null
        clipboardListener?.let { clipboardManager?.removePrimaryClipChangedListener(it) }
        clipboardListener = null
        clipboardManager = null
        // Release camera so status bar icon disappears
        releaseCameraAndStopThread()
        super.onDestroy()
        stopBackspaceRepeat(applyPredictionUpdate = false)
        predictionComputeJob?.cancel()
        predictionRunnable?.let { predictionHandler.removeCallbacks(it) }
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        releaseMediaRecorder()
        stopRecordingPlayback()
    }
}

