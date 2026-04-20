package com.deltavoice

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * One-shot device [TextToSpeech] synthesis to a WAV file (for video dub when cloud TTS is missing).
 */
object DeviceTtsWav {

    fun localeForLangCode(targetLang: String): Locale {
        val code = targetLang.trim().lowercase()
        return when (code) {
            "" -> Locale.getDefault()
            "zh" -> Locale.SIMPLIFIED_CHINESE
            "ja" -> Locale.JAPANESE
            "ko" -> Locale.KOREAN
            "ar" -> Locale("ar")
            "hi" -> Locale("hi")
            else -> try {
                Locale.forLanguageTag(code.replace("_", "-"))
            } catch (_: Exception) {
                Locale.getDefault()
            }
        }
    }

    /**
     * Creates a temporary [TextToSpeech] engine, writes [text] to a WAV file, then shuts down.
     */
    suspend fun synthesizeToWav(
        context: Context,
        text: String,
        targetLangCode: String,
        cacheDir: File
    ): File? {
        if (text.isBlank()) return null
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val appCtx = context.applicationContext
                val outFile = File(cacheDir, "device_tts_${System.currentTimeMillis()}.wav")
                val utteranceId = "device_tts_${System.currentTimeMillis()}"

                var engine: TextToSpeech? = null
                var finished = false

                fun cleanupEngine() {
                    try {
                        engine?.stop()
                    } catch (_: Exception) {
                    }
                    try {
                        engine?.shutdown()
                    } catch (_: Exception) {
                    }
                    engine = null
                }

                fun resumeOnce(value: File?) {
                    if (finished) return
                    finished = true
                    cleanupEngine()
                    if (cont.isActive) cont.resume(value)
                }

                cont.invokeOnCancellation {
                    Handler(Looper.getMainLooper()).post {
                        try {
                            outFile.delete()
                        } catch (_: Exception) {
                        }
                        cleanupEngine()
                    }
                }

                engine = TextToSpeech(appCtx) { status ->
                    if (status != TextToSpeech.SUCCESS) {
                        try {
                            outFile.delete()
                        } catch (_: Exception) {
                        }
                        resumeOnce(null)
                        return@TextToSpeech
                    }
                    val tts = engine ?: run {
                        resumeOnce(null)
                        return@TextToSpeech
                    }
                    val locale = localeForLangCode(targetLangCode)
                    var setOk = tts.setLanguage(locale)
                    if (setOk == TextToSpeech.LANG_MISSING_DATA || setOk == TextToSpeech.LANG_NOT_SUPPORTED) {
                        setOk = tts.setLanguage(Locale.getDefault())
                    }
                    if (setOk == TextToSpeech.LANG_MISSING_DATA || setOk == TextToSpeech.LANG_NOT_SUPPORTED) {
                        android.util.Log.w("DeltaVoice", "DeviceTtsWav: language not fully available, using default")
                    }

                    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}

                        override fun onDone(utteranceId: String?) {
                            Handler(Looper.getMainLooper()).post {
                                if (outFile.exists() && outFile.length() > 0) {
                                    resumeOnce(outFile)
                                } else {
                                    try {
                                        outFile.delete()
                                    } catch (_: Exception) {
                                    }
                                    resumeOnce(null)
                                }
                            }
                        }

                        @Suppress("DEPRECATION")
                        override fun onError(utteranceId: String?) {
                            Handler(Looper.getMainLooper()).post {
                                try {
                                    outFile.delete()
                                } catch (_: Exception) {
                                }
                                resumeOnce(null)
                            }
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            Handler(Looper.getMainLooper()).post {
                                try {
                                    outFile.delete()
                                } catch (_: Exception) {
                                }
                                resumeOnce(null)
                            }
                        }
                    })

                    val params = Bundle().apply {
                        putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                    }
                    val syn = tts.synthesizeToFile(text, params, outFile, utteranceId)
                    if (syn != TextToSpeech.SUCCESS) {
                        try {
                            outFile.delete()
                        } catch (_: Exception) {
                        }
                        resumeOnce(null)
                    }
                }
            }
        }
    }
}
