package com.deltavoice

/**
 * Gboard-style long-press alternate characters for every supported language.
 *
 * Each map is keyed by the **base character** that appears on the key (exactly as
 * stored in [KeyboardLayouts]).  The value list contains the popup variants shown
 * when the user long-presses that key.
 *
 * The global [latin] map covers English and all Latin-script keyboards.
 * Language-specific maps add script-specific variants (Arabic tashkeel, Hebrew
 * nikud, Cyrillic ё, Greek tonos, Korean ssang-jamo, etc.).
 *
 * Lookup order (see [forKey]):
 *   1. language-specific map for the active keyboard language
 *   2. global [latin] fallback (covers a-z, digits, punctuation)
 */
object KeyboardAccentMaps {

    // ── public API ─────────────────────────────────────────────────────

    fun forKey(key: String, lang: String): List<String> {
        val normalised = if (key.length == 1) key.lowercase() else key
        val langMap = langMaps[lang]
        if (langMap != null) {
            langMap[normalised]?.let { return it }
            langMap[key]?.let { return it }
        }
        return latin[normalised] ?: emptyList()
    }

    // ── language → map registry ────────────────────────────────────────

    private val langMaps: Map<String, Map<String, List<String>>> by lazy { mapOf(
        "ar" to arabic,
        "fa" to persian,
        "ur" to urdu,
        "he" to hebrew,
        "iw" to hebrew,
        "ru" to russian,
        "uk" to ukrainian,
        "bg" to bulgarian,
        "sr" to serbian,
        "el" to greek,
        "ko" to korean,
        "ja" to japanese,
        "th" to thai,
        "hi" to hindi,
        "bn" to bengali,
        "ta" to tamil,
        "te" to telugu
    ) }

    // ── Global Latin / symbol map (used for en and all Latin-script langs) ──

    private val latin: Map<String, List<String>> = mapOf(
        "a" to listOf("à", "á", "â", "ã", "ä", "å", "æ", "ā", "ă", "ą", "ª"),
        "c" to listOf("ç", "ć", "č", "ĉ"),
        "d" to listOf("ð", "ď", "đ"),
        "e" to listOf("è", "é", "ê", "ë", "ē", "ė", "ę", "ě"),
        "g" to listOf("ğ", "ĝ", "ġ"),
        "h" to listOf("ĥ", "ħ"),
        "i" to listOf("ì", "í", "î", "ï", "ī", "ĩ", "į", "ı"),
        "j" to listOf("ĵ"),
        "l" to listOf("ł", "ĺ", "ľ", "ļ"),
        "n" to listOf("ñ", "ń", "ň", "ņ"),
        "o" to listOf("ò", "ó", "ô", "õ", "ö", "ø", "ō", "ő", "œ", "ọ"),
        "r" to listOf("ŕ", "ř"),
        "s" to listOf("ß", "ś", "š", "ş", "ŝ"),
        "t" to listOf("ţ", "ť", "þ"),
        "u" to listOf("ù", "ú", "û", "ü", "ū", "ů", "ű", "ų"),
        "w" to listOf("ŵ"),
        "y" to listOf("ý", "ŷ", "ÿ"),
        "z" to listOf("ž", "ź", "ż"),
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
        "'" to listOf("\u2018", "\u2019", "\u201A", "\u201B"),
        "\"" to listOf("\u201C", "\u201D", "\u201E", "\u201F"),
        "/" to listOf("\\"),
        "$" to listOf("€", "£", "¥", "₩", "₹", "₽"),
        "&" to listOf("§"),
        "%" to listOf("‰")
    )

    // ── Arabic ─────────────────────────────────────────────────────────

    private val arabic: Map<String, List<String>> = mapOf(
        "ا" to listOf("أ", "إ", "آ", "ٱ"),
        "ه" to listOf("ة"),
        "ي" to listOf("ئ", "ى"),
        "و" to listOf("ؤ"),
        "ل" to listOf("لا", "لأ", "لإ", "لآ"),
        "ك" to listOf("گ"),
        "ع" to listOf("ء"),
        "ت" to listOf("ة"),
        "ن" to listOf("ں"),
        "ص" to listOf("ض"),
        "س" to listOf("ش"),
        "ح" to listOf("خ"),
        "د" to listOf("ذ"),
        "ر" to listOf("ز"),
        "ط" to listOf("ظ"),
        "ف" to listOf("ڤ"),
        // Tashkeel on period / comma
        "." to listOf("َ", "ِ", "ُ", "ْ", "ّ", "ً", "ٍ", "ٌ"),
        "،" to listOf("؛", "َ", "ِ", "ُ", "ْ", "ّ"),
        // Number row: Arabic-Indic ↔ Western
        "١" to listOf("1"), "٢" to listOf("2"), "٣" to listOf("3"),
        "٤" to listOf("4"), "٥" to listOf("5"), "٦" to listOf("6"),
        "٧" to listOf("7"), "٨" to listOf("8"), "٩" to listOf("9"),
        "٠" to listOf("0")
    )

    // ── Persian ────────────────────────────────────────────────────────

    private val persian: Map<String, List<String>> = mapOf(
        "ا" to listOf("أ", "إ", "آ", "ٱ"),
        "ه" to listOf("ة", "ۀ"),
        "ی" to listOf("ي", "ئ", "ى"),
        "و" to listOf("ؤ"),
        "ک" to listOf("ك"),
        "گ" to listOf("ک", "ك"),
        "ل" to listOf("لا", "لأ", "لإ", "لآ"),
        "ت" to listOf("ة"),
        "ع" to listOf("ء"),
        "ح" to listOf("خ"),
        "ص" to listOf("ض"),
        "س" to listOf("ش"),
        "د" to listOf("ذ"),
        "ر" to listOf("ز", "ژ"),
        "ط" to listOf("ظ"),
        "ف" to listOf("ڤ"),
        "." to listOf("َ", "ِ", "ُ", "ْ", "ّ", "ً", "ٍ", "ٌ"),
        "۱" to listOf("1"), "۲" to listOf("2"), "۳" to listOf("3"),
        "۴" to listOf("4"), "۵" to listOf("5"), "۶" to listOf("6"),
        "۷" to listOf("7"), "۸" to listOf("8"), "۹" to listOf("9"),
        "۰" to listOf("0")
    )

    // ── Urdu ───────────────────────────────────────────────────────────

    private val urdu: Map<String, List<String>> = mapOf(
        "ا" to listOf("أ", "إ", "آ", "ٱ"),
        "ہ" to listOf("ھ", "ة"),
        "ی" to listOf("ئ", "ے", "ي"),
        "و" to listOf("ؤ"),
        "ک" to listOf("ك"),
        "گ" to listOf("ک"),
        "ت" to listOf("ة", "ٹ"),
        "د" to listOf("ذ", "ڈ"),
        "ر" to listOf("ز", "ڑ"),
        "ن" to listOf("ں"),
        "ح" to listOf("خ"),
        "ص" to listOf("ض"),
        "س" to listOf("ش", "ث"),
        "ع" to listOf("ء", "غ"),
        "ط" to listOf("ظ"),
        "ف" to listOf("ق"),
        "ب" to listOf("پ"),
        "ج" to listOf("چ"),
        "." to listOf("َ", "ِ", "ُ", "ْ", "ّ", "ً", "ٍ", "ٌ"),
        "۱" to listOf("1"), "۲" to listOf("2"), "۳" to listOf("3"),
        "۴" to listOf("4"), "۵" to listOf("5"), "۶" to listOf("6"),
        "۷" to listOf("7"), "۸" to listOf("8"), "۹" to listOf("9"),
        "۰" to listOf("0")
    )

    // ── Hebrew ─────────────────────────────────────────────────────────

    private val hebrew: Map<String, List<String>> = mapOf(
        "ש" to listOf("שׁ", "שׂ"),
        "כ" to listOf("ך"),
        "מ" to listOf("ם"),
        "נ" to listOf("ן"),
        "פ" to listOf("ף"),
        "צ" to listOf("ץ"),
        "ח" to listOf("חּ"),
        "ת" to listOf("תּ"),
        "ב" to listOf("בּ"),
        "ג" to listOf("גּ"),
        "ד" to listOf("דּ"),
        "ה" to listOf("הּ"),
        "ו" to listOf("וּ", "וֹ"),
        "א" to listOf("אָ", "אֵ", "אַ"),
        "ע" to listOf("עָ", "עַ"),
        "י" to listOf("יִ", "יּ"),
        "ק" to listOf("קּ"),
        "ר" to listOf("רּ"),
        "ל" to listOf("לּ")
    )

    // ── Russian ────────────────────────────────────────────────────────

    private val russian: Map<String, List<String>> = mapOf(
        "е" to listOf("ё"),
        "ъ" to listOf("ь"),
        "ь" to listOf("ъ"),
        "и" to listOf("й"),
        "й" to listOf("и")
    )

    // ── Ukrainian ──────────────────────────────────────────────────────

    private val ukrainian: Map<String, List<String>> = mapOf(
        "і" to listOf("ї"),
        "г" to listOf("ґ"),
        "е" to listOf("є", "ё"),
        "и" to listOf("й", "і"),
        "ъ" to listOf("ь"),
        "ь" to listOf("ъ")
    )

    // ── Bulgarian ──────────────────────────────────────────────────────

    private val bulgarian: Map<String, List<String>> = mapOf(
        "и" to listOf("й"),
        "й" to listOf("и"),
        "ъ" to listOf("ь"),
        "ь" to listOf("ъ")
    )

    // ── Serbian ────────────────────────────────────────────────────────

    private val serbian: Map<String, List<String>> = mapOf(
        "ђ" to listOf("д"),
        "д" to listOf("ђ"),
        "ћ" to listOf("ч"),
        "ч" to listOf("ћ"),
        "џ" to listOf("ж"),
        "ж" to listOf("џ"),
        "љ" to listOf("л"),
        "л" to listOf("љ"),
        "њ" to listOf("н"),
        "н" to listOf("њ")
    )

    // ── Greek ──────────────────────────────────────────────────────────

    private val greek: Map<String, List<String>> = mapOf(
        "α" to listOf("ά"),
        "ε" to listOf("έ"),
        "η" to listOf("ή"),
        "ι" to listOf("ί", "ϊ", "ΐ"),
        "ο" to listOf("ό"),
        "υ" to listOf("ύ", "ϋ", "ΰ"),
        "ω" to listOf("ώ"),
        "ς" to listOf("σ"),
        "σ" to listOf("ς")
    )

    // ── Korean ─────────────────────────────────────────────────────────

    private val korean: Map<String, List<String>> = mapOf(
        "ㅂ" to listOf("ㅃ"),
        "ㅈ" to listOf("ㅉ"),
        "ㄷ" to listOf("ㄸ"),
        "ㄱ" to listOf("ㄲ"),
        "ㅅ" to listOf("ㅆ"),
        "ㅐ" to listOf("ㅒ"),
        "ㅔ" to listOf("ㅖ")
    )

    // ── Japanese (hiragana → dakuten / handakuten / small) ─────────

    private val japanese: Map<String, List<String>> = mapOf(
        "か" to listOf("が"),
        "き" to listOf("ぎ"),
        "く" to listOf("ぐ"),
        "け" to listOf("げ"),
        "こ" to listOf("ご"),
        "さ" to listOf("ざ"),
        "し" to listOf("じ"),
        "す" to listOf("ず"),
        "せ" to listOf("ぜ"),
        "そ" to listOf("ぞ"),
        "た" to listOf("だ"),
        "ち" to listOf("ぢ"),
        "つ" to listOf("づ", "っ"),
        "て" to listOf("で"),
        "と" to listOf("ど"),
        "は" to listOf("ば", "ぱ"),
        "ひ" to listOf("び", "ぴ"),
        "ふ" to listOf("ぶ", "ぷ"),
        "へ" to listOf("べ", "ぺ"),
        "ほ" to listOf("ぼ", "ぽ"),
        "あ" to listOf("ぁ"),
        "い" to listOf("ぃ"),
        "う" to listOf("ぅ", "ゔ"),
        "え" to listOf("ぇ"),
        "お" to listOf("ぉ"),
        "や" to listOf("ゃ"),
        "ゆ" to listOf("ゅ"),
        "よ" to listOf("ょ"),
        "わ" to listOf("ゎ", "ゐ", "ゑ", "を")
    )

    // ── Thai ───────────────────────────────────────────────────────────

    private val thai: Map<String, List<String>> = mapOf(
        "า" to listOf("ำ"),
        "ก" to listOf("ข", "ฃ", "ค", "ฅ", "ฆ"),
        "จ" to listOf("ฉ", "ช", "ซ", "ฌ"),
        "ด" to listOf("ฎ", "ฏ", "ฐ", "ฑ", "ฒ"),
        "ท" to listOf("ธ", "ถ"),
        "น" to listOf("ณ"),
        "บ" to listOf("ป", "ผ", "ฝ", "พ", "ฟ", "ภ"),
        "ม" to listOf("ย"),
        "ร" to listOf("ล", "ฬ"),
        "ส" to listOf("ศ", "ษ"),
        "ห" to listOf("ฮ"),
        "่" to listOf("้", "๊", "๋"),
        "้" to listOf("่", "๊", "๋"),
        "ั" to listOf("็"),
        "ิ" to listOf("ี", "ึ", "ื"),
        "ี" to listOf("ิ", "ึ", "ื"),
        "ุ" to listOf("ู"),
        "ู" to listOf("ุ"),
        "เ" to listOf("แ"),
        "แ" to listOf("เ"),
        "ไ" to listOf("ใ"),
        "ใ" to listOf("ไ"),
        "๑" to listOf("1"), "๒" to listOf("2"), "๓" to listOf("3"),
        "๔" to listOf("4"), "๕" to listOf("5"), "๖" to listOf("6"),
        "๗" to listOf("7"), "๘" to listOf("8"), "๙" to listOf("9"),
        "๐" to listOf("0")
    )

    // ── Hindi (Devanagari) ─────────────────────────────────────────────

    private val hindi: Map<String, List<String>> = mapOf(
        "क" to listOf("क़", "ख"),
        "ख" to listOf("ख़"),
        "ग" to listOf("ग़", "घ"),
        "ज" to listOf("ज़", "झ"),
        "ड" to listOf("ड़", "ढ"),
        "ढ" to listOf("ढ़"),
        "फ" to listOf("फ़"),
        "य" to listOf("य़"),
        "र" to listOf("ऱ"),
        "त" to listOf("थ"),
        "प" to listOf("फ"),
        "च" to listOf("छ"),
        "ट" to listOf("ठ"),
        "स" to listOf("श", "ष"),
        "ब" to listOf("भ"),
        "न" to listOf("ण"),
        "म" to listOf("ं", "ँ"),
        "ं" to listOf("ँ", "ः"),
        "ा" to listOf("ॉ", "ॅ"),
        "ी" to listOf("ि"),
        "ि" to listOf("ी"),
        "ू" to listOf("ु"),
        "ु" to listOf("ू"),
        "े" to listOf("ै", "ॅ"),
        "ै" to listOf("े"),
        "ो" to listOf("ौ", "ॉ"),
        "ौ" to listOf("ो"),
        "्" to listOf("ऽ")
    )

    // ── Bengali ────────────────────────────────────────────────────────

    private val bengali: Map<String, List<String>> = mapOf(
        "ক" to listOf("খ"),
        "গ" to listOf("ঘ"),
        "চ" to listOf("ছ"),
        "জ" to listOf("ঝ", "য"),
        "ট" to listOf("ঠ"),
        "ড" to listOf("ঢ", "ড়", "ঢ়"),
        "ত" to listOf("থ"),
        "দ" to listOf("ধ"),
        "প" to listOf("ফ"),
        "ব" to listOf("ভ"),
        "স" to listOf("শ", "ষ"),
        "ন" to listOf("ণ"),
        "র" to listOf("ঋ"),
        "ম" to listOf("ং", "ঁ"),
        "ং" to listOf("ঁ", "ঃ"),
        "া" to listOf("ে", "ৈ"),
        "ি" to listOf("ী"),
        "ী" to listOf("ি"),
        "ু" to listOf("ূ"),
        "ূ" to listOf("ু"),
        "ে" to listOf("ৈ"),
        "ো" to listOf("ৌ"),
        "্" to listOf("ঽ"),
        "১" to listOf("1"), "২" to listOf("2"), "৩" to listOf("3"),
        "৪" to listOf("4"), "৫" to listOf("5"), "৬" to listOf("6"),
        "৭" to listOf("7"), "৮" to listOf("8"), "৯" to listOf("9"),
        "০" to listOf("0")
    )

    // ── Tamil ──────────────────────────────────────────────────────────

    private val tamil: Map<String, List<String>> = mapOf(
        "க" to listOf("ங"),
        "ச" to listOf("ஞ"),
        "ட" to listOf("ண"),
        "த" to listOf("ந", "ன"),
        "ப" to listOf("ம"),
        "ற" to listOf("ன"),
        "ஷ" to listOf("ஸ", "ஹ"),
        "ஸ" to listOf("ஷ", "ஹ"),
        "ழ" to listOf("ள"),
        "ள" to listOf("ழ")
    )

    // ── Telugu ──────────────────────────────────────────────────────────

    private val telugu: Map<String, List<String>> = mapOf(
        "క" to listOf("ఖ"),
        "గ" to listOf("ఘ"),
        "చ" to listOf("ఛ"),
        "జ" to listOf("ఝ"),
        "ట" to listOf("ఠ"),
        "డ" to listOf("ఢ"),
        "త" to listOf("థ"),
        "ద" to listOf("ధ"),
        "ప" to listOf("ఫ"),
        "బ" to listOf("భ"),
        "శ" to listOf("ష"),
        "న" to listOf("ణ"),
        "స" to listOf("హ")
    )
}
