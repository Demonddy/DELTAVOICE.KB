package com.deltavoice

import android.content.Context

/**
 * Shared language and voice data for the keyboard and config screens.
 * Display labels come from localized string arrays; codes are fixed.
 */
object KeyboardData {

    fun languageOptions(context: Context): List<Pair<String, String>> {
        val res = context.resources
        val labels = res.getStringArray(R.array.default_language_options)
        val codes = res.getStringArray(R.array.default_language_codes)
        val n = minOf(labels.size, codes.size)
        return (0 until n).map { i -> labels[i] to codes[i] }
    }

    fun voiceOptions(context: Context): List<Pair<String, String>> {
        val res = context.resources
        val labels = res.getStringArray(R.array.default_voice_options)
        val codes = res.getStringArray(R.array.default_voice_codes)
        val n = minOf(labels.size, codes.size)
        return (0 until n).map { i -> labels[i] to codes[i] }
    }

    /** Video/voice process screens use a wider voice set (includes Charlotte, Alice). */
    fun videoVoiceOptions(context: Context): List<Pair<String, String>> {
        val res = context.resources
        val labels = res.getStringArray(R.array.video_voice_options)
        val codes = res.getStringArray(R.array.video_voice_codes)
        val n = minOf(labels.size, codes.size)
        return (0 until n).map { i -> labels[i] to codes[i] }
    }

    val dictLanguages = listOf(
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
        "hi" to "हिंदी"
    )
}
