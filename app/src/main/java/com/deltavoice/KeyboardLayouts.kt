package com.deltavoice

/**
 * Keyboard layout definitions for supported languages.
 */
data class KeyboardLayout(
    val numbers: List<String>,
    val row1: List<String>,
    val row2: List<String>,
    val row3: List<String>,
    val displayName: String
)

object KeyboardLayouts {
    val layouts = mapOf(
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
            numbers = listOf("١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩", "."),
            row1 = listOf("ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه", "خ", "ح", "ج"),
            row2 = listOf("ش", "س", "ي", "ب", "ل", "أ", "ت", "ن", "م", "ك", "ط"),
            row3 = listOf("ذ", "ء", "ؤ", "ر", "ئ", "ة", "و", "ز", "ظ", "د"),
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
            row1 = listOf("あ", "い", "う", "え", "お", "か", "き", "く", "け", "こ"),
            row2 = listOf("さ", "し", "す", "せ", "そ", "た", "ち", "つ", "て", "と"),
            row3 = listOf("な", "に", "ぬ", "ね", "の", "は", "ひ", "ふ", "へ", "ほ"),
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
}
