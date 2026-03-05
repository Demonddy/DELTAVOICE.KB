package com.deltavoice

/**
 * Shared language and voice data for the keyboard.
 */
object KeyboardData {
    val languages = listOf(
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

    val voiceStyles = listOf(
        "Adam" to "adam",
        "Aria" to "aria",
        "Roger" to "roger",
        "Sarah" to "sarah",
        "Laura" to "laura",
        "Charlie" to "charlie",
        "George" to "george",
        "Liam" to "liam"
    )

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
