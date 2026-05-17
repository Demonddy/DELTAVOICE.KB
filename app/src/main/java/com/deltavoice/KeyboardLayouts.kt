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
        // ──────────────────────────────────────────────
        // Existing layouts
        // ──────────────────────────────────────────────

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
        ),

        // ──────────────────────────────────────────────
        // Latin-script European languages
        // ──────────────────────────────────────────────

        "tr" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P", "Ğ", "Ü"),
            row2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L", "Ş", "İ"),
            row3 = listOf("Z", "X", "C", "V", "B", "N", "M", "Ö", "Ç"),
            displayName = "Türkçe"
        ),
        "nl" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
            row2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
            row3 = listOf("Z", "X", "C", "V", "B", "N", "M"),
            displayName = "Nederlands"
        ),
        "pl" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
            row2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
            row3 = listOf("Z", "X", "C", "V", "B", "N", "M"),
            displayName = "Polski"
        ),
        "cs" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("Q", "W", "E", "R", "T", "Z", "U", "I", "O", "P"),
            row2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L", "Ů"),
            row3 = listOf("Y", "X", "C", "V", "B", "N", "M", "Š", "Č", "Ž"),
            displayName = "Čeština"
        ),
        "sv" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P", "Å"),
            row2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L", "Ö", "Ä"),
            row3 = listOf("Z", "X", "C", "V", "B", "N", "M"),
            displayName = "Svenska"
        ),
        "no" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P", "Å"),
            row2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L", "Ø", "Æ"),
            row3 = listOf("Z", "X", "C", "V", "B", "N", "M"),
            displayName = "Norsk"
        ),
        "da" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P", "Å"),
            row2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L", "Æ", "Ø"),
            row3 = listOf("Z", "X", "C", "V", "B", "N", "M"),
            displayName = "Dansk"
        ),
        "fi" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P", "Å"),
            row2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L", "Ö", "Ä"),
            row3 = listOf("Z", "X", "C", "V", "B", "N", "M"),
            displayName = "Suomi"
        ),
        "hu" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("Q", "W", "E", "R", "T", "Z", "U", "I", "O", "P", "Ö", "Ü"),
            row2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L", "É", "Á"),
            row3 = listOf("Y", "X", "C", "V", "B", "N", "M", "Ő", "Ú", "Ű"),
            displayName = "Magyar"
        ),
        "ro" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
            row2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L", "Ă", "Â"),
            row3 = listOf("Z", "X", "C", "V", "B", "N", "M", "Î", "Ș", "Ț"),
            displayName = "Română"
        ),
        "hr" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("Q", "W", "E", "R", "T", "Z", "U", "I", "O", "P", "Š", "Đ"),
            row2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L", "Č", "Ć"),
            row3 = listOf("Y", "X", "C", "V", "B", "N", "M", "Ž"),
            displayName = "Hrvatski"
        ),
        "sk" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("Q", "W", "E", "R", "T", "Z", "U", "I", "O", "P"),
            row2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L", "Ô", "Ä"),
            row3 = listOf("Y", "X", "C", "V", "B", "N", "M", "Ž", "Š", "Č"),
            displayName = "Slovenčina"
        ),
        "sl" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("Q", "W", "E", "R", "T", "Z", "U", "I", "O", "P", "Š"),
            row2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L", "Č", "Ž"),
            row3 = listOf("Y", "X", "C", "V", "B", "N", "M"),
            displayName = "Slovenščina"
        ),
        "et" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P", "Ü", "Õ"),
            row2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L", "Ö", "Ä"),
            row3 = listOf("Z", "X", "C", "V", "B", "N", "M"),
            displayName = "Eesti"
        ),
        "lv" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
            row2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
            row3 = listOf("Z", "X", "C", "V", "B", "N", "M"),
            displayName = "Latviešu"
        ),
        "lt" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
            row2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
            row3 = listOf("Z", "X", "C", "V", "B", "N", "M"),
            displayName = "Lietuvių"
        ),
        "ca" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
            row2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L", "Ç"),
            row3 = listOf("Z", "X", "C", "V", "B", "N", "M"),
            displayName = "Català"
        ),
        "gl" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
            row2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L", "Ñ"),
            row3 = listOf("Z", "X", "C", "V", "B", "N", "M"),
            displayName = "Galego"
        ),
        "eu" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
            row2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L", "Ñ"),
            row3 = listOf("Z", "X", "C", "V", "B", "N", "M"),
            displayName = "Euskara"
        ),

        // ──────────────────────────────────────────────
        // Latin-script Asian / African languages
        // ──────────────────────────────────────────────

        "vi" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P", "Ư", "Ơ"),
            row2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L", "Đ"),
            row3 = listOf("Z", "X", "C", "V", "B", "N", "M", "Ă", "Â", "Ê", "Ô"),
            displayName = "Tiếng Việt"
        ),
        "id" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
            row2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
            row3 = listOf("Z", "X", "C", "V", "B", "N", "M"),
            displayName = "Bahasa Indonesia"
        ),
        "ms" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
            row2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
            row3 = listOf("Z", "X", "C", "V", "B", "N", "M"),
            displayName = "Bahasa Melayu"
        ),
        "tl" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
            row2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L", "Ñ"),
            row3 = listOf("Z", "X", "C", "V", "B", "N", "M"),
            displayName = "Filipino"
        ),
        "sw" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
            row2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
            row3 = listOf("Z", "X", "C", "V", "B", "N", "M"),
            displayName = "Kiswahili"
        ),

        // ──────────────────────────────────────────────
        // Greek
        // ──────────────────────────────────────────────

        "el" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("ς", "Ε", "Ρ", "Τ", "Υ", "Θ", "Ι", "Ο", "Π"),
            row2 = listOf("Α", "Σ", "Δ", "Φ", "Γ", "Η", "Ξ", "Κ", "Λ"),
            row3 = listOf("Ζ", "Χ", "Ψ", "Ω", "Β", "Ν", "Μ"),
            displayName = "Ελληνικά"
        ),

        // ──────────────────────────────────────────────
        // Cyrillic-script languages
        // ──────────────────────────────────────────────

        "uk" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("Й", "Ц", "У", "К", "Е", "Н", "Г", "Ш", "Щ", "З", "Х", "Ї"),
            row2 = listOf("Ф", "І", "В", "А", "П", "Р", "О", "Л", "Д", "Ж", "Є"),
            row3 = listOf("Я", "Ч", "С", "М", "И", "Т", "Ь", "Б", "Ю", "Ґ"),
            displayName = "Українська"
        ),
        "bg" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("Я", "В", "Е", "Р", "Т", "Ъ", "У", "И", "О", "П", "Ч"),
            row2 = listOf("А", "С", "Д", "Ф", "Г", "Х", "Й", "К", "Л", "Ш", "Щ"),
            row3 = listOf("З", "Ь", "Ц", "Ж", "Б", "Н", "М", "Ю"),
            displayName = "Български"
        ),
        "sr" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("Љ", "Њ", "Е", "Р", "Т", "З", "У", "И", "О", "П", "Ш"),
            row2 = listOf("А", "С", "Д", "Ф", "Г", "Х", "Ј", "К", "Л", "Ч", "Ћ"),
            row3 = listOf("Ж", "Џ", "Ц", "В", "Б", "Н", "М", "Ђ"),
            displayName = "Српски"
        ),

        // ──────────────────────────────────────────────
        // RTL scripts: Hebrew, Persian, Urdu
        // ──────────────────────────────────────────────

        "he" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("ק", "ר", "א", "ט", "ו", "ן", "ם", "פ"),
            row2 = listOf("ש", "ד", "ג", "כ", "ע", "י", "ח", "ל", "ך", "ף"),
            row3 = listOf("ז", "ס", "ב", "ה", "נ", "מ", "צ", "ת", "ץ"),
            displayName = "עברית"
        ),
        "fa" to KeyboardLayout(
            numbers = listOf("۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹", "۰"),
            row1 = listOf("ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه", "خ", "ح", "ج", "چ"),
            row2 = listOf("ش", "س", "ی", "ب", "ل", "ا", "ت", "ن", "م", "ک", "گ"),
            row3 = listOf("ظ", "ط", "ز", "ر", "ذ", "د", "پ", "و", "ژ"),
            displayName = "فارسی"
        ),
        "ur" to KeyboardLayout(
            numbers = listOf("۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹", "۰"),
            row1 = listOf("ق", "و", "ع", "ر", "ت", "ے", "ء", "ی", "ہ", "پ", "ٹ"),
            row2 = listOf("ا", "س", "د", "ف", "گ", "ح", "ج", "ک", "ل", "ں"),
            row3 = listOf("ز", "ش", "خ", "ذ", "ب", "ن", "م", "ط", "ھ", "ڈ", "ڑ"),
            displayName = "اردو"
        ),

        // ──────────────────────────────────────────────
        // South Asian scripts: Bengali, Tamil, Telugu
        // ──────────────────────────────────────────────

        "bn" to KeyboardLayout(
            numbers = listOf("১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯", "০"),
            row1 = listOf("ৌ", "ৈ", "া", "ী", "ূ", "ভ", "হ", "গ", "দ", "জ", "ড"),
            row2 = listOf("ো", "ে", "্", "ি", "ু", "প", "র", "ক", "ত", "চ", "ট"),
            row3 = listOf("ং", "ম", "ন", "শ", "ল", "স", "য"),
            displayName = "বাংলা"
        ),
        "ta" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("அ", "ஆ", "இ", "ஈ", "உ", "ஊ", "எ", "ஏ", "ஐ", "ஒ"),
            row2 = listOf("க", "ச", "ட", "த", "ப", "ம", "ய", "ர", "ல", "வ"),
            row3 = listOf("ங", "ஞ", "ண", "ந", "ன", "ழ", "ள", "ற", "ஷ", "ஸ"),
            displayName = "தமிழ்"
        ),
        "te" to KeyboardLayout(
            numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            row1 = listOf("అ", "ఆ", "ఇ", "ఈ", "ఉ", "ఊ", "ఎ", "ఏ", "ఐ", "ఒ"),
            row2 = listOf("క", "చ", "ట", "త", "ప", "మ", "య", "ర", "ల", "వ"),
            row3 = listOf("గ", "జ", "డ", "ద", "బ", "న", "శ", "ష", "స", "హ"),
            displayName = "తెలుగు"
        ),

        // ──────────────────────────────────────────────
        // Thai (Kedmanee layout)
        // ──────────────────────────────────────────────

        "th" to KeyboardLayout(
            numbers = listOf("๑", "๒", "๓", "๔", "๕", "๖", "๗", "๘", "๙", "๐"),
            row1 = listOf("ๆ", "ไ", "ำ", "พ", "ะ", "ั", "ี", "ร", "น", "ย", "บ", "ล"),
            row2 = listOf("ฟ", "ห", "ก", "ด", "เ", "้", "่", "า", "ส", "ว", "ง"),
            row3 = listOf("ผ", "ป", "แ", "อ", "ิ", "ื", "ท", "ม", "ใ", "ฝ"),
            displayName = "ไทย"
        )
    )
}
