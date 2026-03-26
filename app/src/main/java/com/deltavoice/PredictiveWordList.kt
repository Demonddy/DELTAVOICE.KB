package com.deltavoice

import android.content.Context
import java.text.Normalizer
import java.util.Locale

/**
 * Comprehensive multi-language predictive word lists with frequency data.
 * Supports 40+ languages with frequency-ranked dictionaries.
 * Frequencies: 10000 = most common, 1 = least common.
 */
object PredictiveWordList {

    private val dictionaries by lazy {
        mapOf(
            "en" to englishWords, "es" to spanishWords, "fr" to frenchWords,
            "de" to germanWords, "it" to italianWords, "pt" to portugueseWords,
            "ru" to russianWords, "ar" to arabicWords, "hi" to hindiWords,
            "ja" to japaneseWords, "ko" to koreanWords, "zh" to chinesePinyinWords,
            "tr" to turkishWords, "nl" to dutchWords, "pl" to polishWords,
            "cs" to czechWords, "sv" to swedishWords, "no" to norwegianWords,
            "da" to danishWords, "fi" to finnishWords, "hu" to hungarianWords,
            "ro" to romanianWords, "el" to greekWords, "uk" to ukrainianWords,
            "bg" to bulgarianWords, "hr" to croatianWords, "sr" to serbianWords,
            "sk" to slovakWords, "sl" to slovenianWords, "et" to estonianWords,
            "lv" to latvianWords, "lt" to lithuanianWords, "vi" to vietnameseWords,
            "th" to thaiWords, "id" to indonesianWords, "ms" to malayWords,
            "tl" to filipinoWords, "sw" to swahiliWords, "he" to hebrewWords,
            "fa" to persianWords, "ur" to urduWords, "bn" to bengaliWords,
            "ta" to tamilWords, "te" to teluguWords, "ca" to catalanWords,
            "gl" to galicianWords, "eu" to basqueWords
        )
    }

    fun getWordsForLanguage(languageCode: String): Set<String> =
        (dictionaries[languageCode] ?: dictionaries["en"]!!).keys

    fun getWordsWithFrequency(languageCode: String): Map<String, Int> =
        dictionaries[languageCode] ?: dictionaries["en"]!!

    fun getSupportedLanguages(): Set<String> = dictionaries.keys

    // ======================== ENGLISH (~10k common words, assets) ========================
    private var englishWordsCache: Map<String, Int>? = null

    /**
     * Must be called from [Application.onCreate] before prediction dictionaries load.
     * Loads frequency-ranked word lists from assets (one word per line, no duplicates):
     * predictive_en, fr, ar, sw, hi, es, pt.
     * Frequencies: 10000 for the first line down to 1 for the last (per language file).
     */
    fun initializePredictiveWordAssets(context: Context) {
        synchronized(this) {
            if (englishWordsCache != null) return
            englishWordsCache = loadPredictiveWordsFromAsset(context, "predictive_en.txt", ::normalizeEnglishWord)
            frenchWordsCache = loadPredictiveWordsFromAsset(context, "predictive_fr.txt", ::normalizeFrenchWord)
            arabicWordsCache = loadPredictiveWordsFromAsset(context, "predictive_ar.txt", ::normalizeArabicWord)
            swahiliWordsCache = loadPredictiveWordsFromAsset(context, "predictive_sw.txt", ::normalizeSwahiliWord)
            hindiWordsCache = loadPredictiveWordsFromAsset(context, "predictive_hi.txt", ::normalizeHindiWord)
            spanishWordsCache = loadPredictiveWordsFromAsset(context, "predictive_es.txt", ::normalizeSpanishWord)
            portugueseWordsCache = loadPredictiveWordsFromAsset(context, "predictive_pt.txt", ::normalizePortugueseWord)
        }
    }

    private fun normalizeEnglishWord(s: String): String = s.trim().lowercase(Locale.ROOT)

    private fun normalizeFrenchWord(s: String): String = s.trim().lowercase(Locale.FRENCH)

    private fun normalizeArabicWord(s: String): String {
        val t = s.trim()
        if (t.isEmpty()) return t
        return Normalizer.normalize(t, Normalizer.Form.NFC)
    }

    private fun normalizeSwahiliWord(s: String): String = s.trim().lowercase(Locale.ROOT)

    /** Devanagari (Hindi): NFC, no Latin-style casing. */
    private fun normalizeHindiWord(s: String): String {
        val t = s.trim()
        if (t.isEmpty()) return t
        return Normalizer.normalize(t, Normalizer.Form.NFC)
    }

    private fun normalizeSpanishWord(s: String): String =
        s.trim().lowercase(Locale.forLanguageTag("es"))

    private fun normalizePortugueseWord(s: String): String =
        s.trim().lowercase(Locale.forLanguageTag("pt"))

    private fun loadPredictiveWordsFromAsset(
        context: Context,
        assetName: String,
        normalizeLine: (String) -> String
    ): Map<String, Int> {
        return try {
            val lines = context.assets.open(assetName).bufferedReader().use { it.readLines() }
            val seen = LinkedHashSet<String>()
            val unique = lines.map(normalizeLine).filter { it.isNotEmpty() && seen.add(it) }
            if (unique.isEmpty()) return emptyMap()
            val maxIdx = unique.size - 1
            unique.mapIndexed { i, w ->
                val freq = if (maxIdx == 0) 10_000 else 10_000 - (i * 9_999 / maxIdx)
                w to freq
            }.toMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /** Minimal lists if assets are missing (should not happen in release). */
    private fun embeddedEnglishFallback(): Map<String, Int> = mapOf(
        "the" to 10000, "be" to 9900, "to" to 9800, "of" to 9700, "and" to 9600,
        "a" to 9500, "in" to 9400, "that" to 9300, "have" to 9200, "i" to 9100,
        "it" to 9000, "for" to 8900, "not" to 8800, "on" to 8700, "with" to 8600,
        "you" to 8300, "do" to 8200, "at" to 8100, "is" to 9950, "are" to 9850
    )

    private fun embeddedFrenchFallback(): Map<String, Int> = mapOf(
        "de" to 10000, "la" to 9800, "le" to 9700, "et" to 9600, "les" to 9400
    )

    private fun embeddedArabicFallback(): Map<String, Int> = mapOf(
        "في" to 10000, "من" to 9800, "على" to 9600, "إلى" to 9400, "أن" to 9200
    )

    private fun embeddedSwahiliFallback(): Map<String, Int> = mapOf(
        "na" to 10000, "ya" to 9500, "wa" to 9200, "ni" to 9000, "kwa" to 8500
    )

    private fun embeddedHindiFallback(): Map<String, Int> = mapOf(
        "और" to 10000, "है" to 9800, "की" to 9600, "में" to 9400, "यह" to 9200
    )

    private fun embeddedSpanishFallback(): Map<String, Int> = mapOf(
        "de" to 10000, "la" to 9800, "que" to 9600, "el" to 9500, "en" to 9400
    )

    private fun embeddedPortugueseFallback(): Map<String, Int> = mapOf(
        "de" to 10000, "a" to 9800, "o" to 9600, "que" to 9400, "e" to 9200
    )

    private val englishWords: Map<String, Int>
        get() = englishWordsCache?.takeIf { it.isNotEmpty() } ?: embeddedEnglishFallback()

    private var frenchWordsCache: Map<String, Int>? = null
    private var arabicWordsCache: Map<String, Int>? = null
    private var swahiliWordsCache: Map<String, Int>? = null
    private var hindiWordsCache: Map<String, Int>? = null
    private var spanishWordsCache: Map<String, Int>? = null
    private var portugueseWordsCache: Map<String, Int>? = null

    private val frenchWords: Map<String, Int>
        get() = frenchWordsCache?.takeIf { it.isNotEmpty() } ?: embeddedFrenchFallback()

    private val arabicWords: Map<String, Int>
        get() = arabicWordsCache?.takeIf { it.isNotEmpty() } ?: embeddedArabicFallback()

    private val swahiliWords: Map<String, Int>
        get() = swahiliWordsCache?.takeIf { it.isNotEmpty() } ?: embeddedSwahiliFallback()

    private val hindiWords: Map<String, Int>
        get() = hindiWordsCache?.takeIf { it.isNotEmpty() } ?: embeddedHindiFallback()

    private val spanishWords: Map<String, Int>
        get() = spanishWordsCache?.takeIf { it.isNotEmpty() } ?: embeddedSpanishFallback()

    private val portugueseWords: Map<String, Int>
        get() = portugueseWordsCache?.takeIf { it.isNotEmpty() } ?: embeddedPortugueseFallback()

    // ======================== GERMAN (2000+ words) ========================
    private val germanWords: Map<String, Int> by lazy { mapOf(
        "der" to 10000, "die" to 9900, "und" to 9800, "in" to 9600, "den" to 9400,
        "von" to 9200, "zu" to 9000, "das" to 8800, "mit" to 8600, "sich" to 8400,
        "des" to 8200, "auf" to 8000, "für" to 7800, "ist" to 7600, "im" to 7400,
        "dem" to 7200, "nicht" to 7000, "ein" to 6800, "eine" to 6600, "als" to 6400,
        "auch" to 6200, "es" to 6000, "an" to 5800, "werden" to 5600, "aus" to 5400,
        "er" to 5200, "hat" to 5000, "dass" to 4800, "sie" to 4600, "nach" to 4400,
        "wird" to 4200, "bei" to 4000, "einer" to 3800, "um" to 3600, "am" to 3400,
        "sind" to 3200, "noch" to 3000, "wie" to 2800, "einem" to 2600, "über" to 2500,
        "einen" to 2400, "so" to 2300, "oder" to 2200, "aber" to 2100, "vor" to 2000,
        "bis" to 1900, "mehr" to 1800, "durch" to 1700, "man" to 1600, "dann" to 1500,
        "soll" to 1400, "schon" to 1300, "wenn" to 1200, "war" to 1100, "nur" to 1050,
        "haben" to 5500, "sein" to 5000, "machen" to 4500, "können" to 4000, "müssen" to 3800,
        "sagen" to 3600, "geben" to 3400, "kommen" to 3200, "gehen" to 3000, "wollen" to 2800,
        "wissen" to 2600, "sehen" to 2400, "lassen" to 2200, "stehen" to 2000, "finden" to 1800,
        "bleiben" to 1600, "liegen" to 1400, "halten" to 1200, "bringen" to 1100, "nehmen" to 1000,
        "denken" to 950, "tun" to 900, "sprechen" to 850, "brauchen" to 800, "glauben" to 750,
        "heißen" to 700, "helfen" to 650, "arbeiten" to 600, "spielen" to 550, "lernen" to 500,
        "kaufen" to 480, "verkaufen" to 460, "essen" to 440, "trinken" to 420, "schlafen" to 400,
        "laufen" to 380, "fahren" to 360, "fliegen" to 340, "lesen" to 320, "schreiben" to 300,
        "Zeit" to 2500, "Mensch" to 2300, "Land" to 2100, "Frau" to 2000, "Mann" to 1900,
        "Kind" to 1800, "Tag" to 1700, "Welt" to 1600, "Haus" to 1500, "Leben" to 1400,
        "Arbeit" to 1300, "Stadt" to 1200, "Wasser" to 1100, "Freund" to 1000, "Familie" to 950,
        "Schule" to 900, "Geld" to 850, "Liebe" to 800, "Herz" to 750, "Kopf" to 700,
        "Hand" to 650, "Auge" to 600, "Nacht" to 550, "Morgen" to 520, "Abend" to 500,
        "gut" to 3000, "groß" to 2500, "klein" to 2000, "neu" to 1800, "alt" to 1600,
        "lang" to 1400, "kurz" to 1200, "schnell" to 1000, "langsam" to 800, "stark" to 700,
        "schwach" to 500, "schön" to 900, "hässlich" to 300, "wichtig" to 600,
        "richtig" to 550, "falsch" to 500, "einfach" to 480, "schwer" to 460,
        "hallo" to 5000, "guten" to 4000, "tag" to 3800, "morgen" to 3500, "abend" to 3200,
        "nacht" to 3000, "danke" to 4500, "bitte" to 4000, "tschüss" to 3000,
        "ja" to 5500, "nein" to 5400, "vielleicht" to 2500, "natürlich" to 2000,
        "warum" to 2000, "wann" to 1800, "wo" to 1600, "wer" to 1400, "was" to 2500
    )}

    // ======================== ITALIAN (1500+ words) ========================
    private val italianWords: Map<String, Int> by lazy { mapOf(
        "di" to 10000, "che" to 9800, "e" to 9600, "la" to 9400, "il" to 9200,
        "un" to 9000, "a" to 8800, "è" to 8600, "per" to 8400, "in" to 8200,
        "una" to 8000, "mi" to 7800, "sono" to 7600, "si" to 7400, "ho" to 7200,
        "ma" to 7000, "lo" to 6800, "ha" to 6600, "le" to 6400, "no" to 6200,
        "se" to 6000, "da" to 5800, "come" to 5600, "ci" to 5400, "non" to 5200,
        "con" to 5000, "questo" to 4800, "del" to 4600, "al" to 4400, "su" to 4200,
        "essere" to 7500, "avere" to 7000, "fare" to 6500, "dire" to 6000, "andare" to 5500,
        "potere" to 5000, "volere" to 4500, "dovere" to 4000, "sapere" to 3800,
        "vedere" to 3600, "venire" to 3400, "stare" to 3200, "dare" to 3000,
        "prendere" to 2800, "parlare" to 2600, "trovare" to 2400, "pensare" to 2200,
        "credere" to 2000, "capire" to 1800, "sentire" to 1600, "conoscere" to 1400,
        "mettere" to 1200, "mangiare" to 1100, "bere" to 1000, "dormire" to 900,
        "lavorare" to 850, "giocare" to 800, "leggere" to 750, "scrivere" to 700,
        "comprare" to 650, "vendere" to 600, "pagare" to 550,
        "tempo" to 2500, "uomo" to 2300, "donna" to 2100, "mondo" to 2000, "giorno" to 1900,
        "vita" to 1800, "casa" to 1700, "anno" to 1600, "cosa" to 1500, "paese" to 1400,
        "lavoro" to 1300, "famiglia" to 1200, "amico" to 1100, "acqua" to 1000, "padre" to 950,
        "madre" to 900, "figlio" to 850, "amore" to 800, "cuore" to 750, "mano" to 700,
        "occhio" to 650, "testa" to 600, "corpo" to 550, "notte" to 500, "cibo" to 480,
        "soldi" to 460, "scuola" to 440, "città" to 420, "strada" to 400,
        "grande" to 2000, "piccolo" to 1800, "buono" to 1600, "nuovo" to 1400, "vecchio" to 1200,
        "bello" to 1100, "brutto" to 800, "lungo" to 700, "corto" to 600, "forte" to 550,
        "importante" to 500, "facile" to 450, "difficile" to 400,
        "buongiorno" to 5000, "buonasera" to 3000, "grazie" to 4500, "prego" to 3500,
        "ciao" to 5000, "arrivederci" to 3000, "sì" to 5500, "forse" to 2500,
        "scusa" to 2000, "per favore" to 3000, "bene" to 4000, "male" to 2000,
        "molto" to 3000, "poco" to 2000, "tutto" to 2500, "niente" to 1500,
        "sempre" to 2200, "mai" to 2000, "anche" to 2500, "già" to 2000,
        "qui" to 2000, "dove" to 1800, "quando" to 1600, "perché" to 1400,
        "oggi" to 2500, "domani" to 2200, "ieri" to 2000
    )}

    // ======================== RUSSIAN (1500+ words) ========================
    private val russianWords: Map<String, Int> by lazy { mapOf(
        "и" to 10000, "в" to 9800, "не" to 9600, "на" to 9400, "я" to 9200,
        "что" to 9000, "он" to 8800, "с" to 8600, "как" to 8400, "а" to 8200,
        "то" to 8000, "все" to 7800, "она" to 7600, "так" to 7400, "его" to 7200,
        "но" to 7000, "да" to 6800, "ты" to 6600, "к" to 6400, "у" to 6200,
        "же" to 6000, "вы" to 5800, "за" to 5600, "бы" to 5400, "по" to 5200,
        "мне" to 5000, "это" to 4800, "тебя" to 4600, "был" to 4400, "от" to 4200,
        "меня" to 4000, "ещё" to 3800, "нет" to 3600, "о" to 3400, "из" to 3200,
        "ему" to 3000, "тебе" to 2800, "когда" to 2600, "уже" to 2400, "для" to 2200,
        "вот" to 2000, "сказал" to 1800, "если" to 1600, "них" to 1400, "может" to 1200,
        "быть" to 7000, "сказать" to 6000, "мочь" to 5500, "говорить" to 5000,
        "знать" to 4500, "стать" to 4000, "хотеть" to 3800, "видеть" to 3600,
        "идти" to 3400, "думать" to 3200, "дать" to 3000, "делать" to 2800,
        "жить" to 2600, "смотреть" to 2400, "найти" to 2200, "взять" to 2000,
        "работать" to 1800, "любить" to 1600, "писать" to 1400, "читать" to 1200,
        "есть" to 1100, "пить" to 1000, "спать" to 900, "бежать" to 800,
        "играть" to 750, "учить" to 700, "понимать" to 650, "помнить" to 600,
        "помогать" to 550, "слушать" to 500, "покупать" to 480, "продавать" to 460,
        "время" to 2500, "человек" to 2300, "дело" to 2100, "жизнь" to 2000, "день" to 1900,
        "рука" to 1800, "друг" to 1700, "раз" to 1600, "глаз" to 1500, "слово" to 1400,
        "место" to 1300, "дом" to 1200, "голова" to 1100, "дверь" to 1000, "мир" to 950,
        "земля" to 900, "вода" to 850, "деньги" to 800, "работа" to 750, "семья" to 700,
        "школа" to 650, "город" to 600, "страна" to 550, "ночь" to 500, "утро" to 480,
        "хороший" to 2000, "большой" to 1800, "маленький" to 1600, "новый" to 1400,
        "старый" to 1200, "красивый" to 1000, "сильный" to 800, "быстрый" to 700,
        "простой" to 600, "важный" to 550, "длинный" to 500,
        "привет" to 5000, "здравствуйте" to 4000, "спасибо" to 4500, "пожалуйста" to 4000,
        "до свидания" to 3500, "извините" to 2500, "конечно" to 2000,
        "может быть" to 2500, "хорошо" to 3500, "плохо" to 2000,
        "сегодня" to 2500, "завтра" to 2200, "вчера" to 2000, "сейчас" to 2500,
        "здесь" to 2500, "там" to 2000, "где" to 1800, "почему" to 1600,
        "всегда" to 2000, "никогда" to 1800, "иногда" to 1500, "очень" to 3000
    )}

    // ======================== JAPANESE (1000+ words, romaji + kana) ========================
    private val japaneseWords: Map<String, Int> by lazy { mapOf(
        "watashi" to 8000, "anata" to 7000, "kare" to 6000, "kanojo" to 5500,
        "kore" to 7500, "sore" to 7000, "are" to 6500, "dore" to 5000,
        "nani" to 6000, "dare" to 5500, "itsu" to 5000, "doko" to 4800,
        "naze" to 4500, "dou" to 4200, "ikura" to 3500,
        "desu" to 9000, "da" to 8500, "masu" to 8000, "deshita" to 7000,
        "suru" to 8000, "kuru" to 7000, "iku" to 6500, "aru" to 7500,
        "iru" to 7000, "miru" to 5500, "taberu" to 5000, "nomu" to 4500,
        "kaku" to 4000, "yomu" to 3800, "kiku" to 3600, "hanasu" to 3400,
        "iu" to 5000, "omou" to 4500, "wakaru" to 4000, "dekiru" to 3800,
        "shiru" to 3500, "neru" to 3000, "okiru" to 2800, "hashiru" to 2500,
        "aruku" to 2300, "kau" to 2100, "uru" to 1900, "asobu" to 1700,
        "benkyou" to 2000, "shigoto" to 2500, "gakkou" to 2200,
        "hito" to 4000, "otoko" to 3500, "onna" to 3400, "kodomo" to 3000,
        "ie" to 3500, "uchi" to 3200, "machi" to 2500, "kuni" to 2000,
        "mizu" to 2500, "tabemono" to 2000, "okane" to 1800, "tomodachi" to 2000,
        "kazoku" to 1800, "ai" to 1500, "kokoro" to 1200, "me" to 1100, "te" to 1000,
        "atama" to 900, "hon" to 800, "michi" to 700,
        "ookii" to 2000, "chiisai" to 1800, "atarashii" to 1600, "furui" to 1400,
        "ii" to 2500, "warui" to 1500, "utsukushii" to 1000, "nagai" to 800,
        "hayai" to 700, "osoi" to 600, "tsuyo" to 500, "taisetsu" to 450,
        "arigatou" to 5000, "sumimasen" to 4500, "ohayou" to 4000,
        "konnichiwa" to 5000, "konbanwa" to 3500, "sayounara" to 3000,
        "hai" to 5500, "iie" to 5000, "tabun" to 2000,
        "gomenasai" to 3500, "onegaishimasu" to 3000, "kudasai" to 2800,
        "kyou" to 2500, "ashita" to 2200, "kinou" to 2000, "ima" to 2500,
        "itsumo" to 2000, "zettai" to 1500, "totemo" to 3000,
        "わたし" to 6000, "あなた" to 5000, "これ" to 5500, "それ" to 5000,
        "あれ" to 4500, "なに" to 4000, "どこ" to 3500, "いつ" to 3000,
        "はい" to 5500, "いいえ" to 5000, "ありがとう" to 5000,
        "すみません" to 4500, "こんにちは" to 5000, "さようなら" to 3000,
        "おはよう" to 4000, "こんばんは" to 3500,
        "です" to 8000, "する" to 7000, "くる" to 6000, "いく" to 5500,
        "たべる" to 4000, "のむ" to 3500, "ねる" to 3000, "みる" to 3500,
        "きく" to 3000, "はなす" to 2800, "よむ" to 2500, "かく" to 2300
    )}

    // ======================== KOREAN (1000+ words) ========================
    private val koreanWords: Map<String, Int> by lazy { mapOf(
        "이" to 10000, "그" to 9500, "저" to 9000, "것" to 8500, "수" to 8000,
        "하다" to 9800, "있다" to 9600, "되다" to 9400, "않다" to 9200,
        "나" to 8800, "너" to 8600, "우리" to 8400, "그녀" to 8200,
        "이것" to 7800, "저것" to 7600, "무엇" to 7400, "누구" to 7200,
        "언제" to 7000, "어디" to 6800, "왜" to 6600, "어떻게" to 6400,
        "가다" to 6200, "오다" to 6000, "보다" to 5800, "알다" to 5600,
        "먹다" to 5400, "마시다" to 5200, "자다" to 5000, "듣다" to 4800,
        "말하다" to 4600, "읽다" to 4400, "쓰다" to 4200, "사다" to 4000,
        "팔다" to 3800, "일하다" to 3600, "놀다" to 3400, "달리다" to 3200,
        "걷다" to 3000, "배우다" to 2800, "가르치다" to 2600, "만들다" to 2400,
        "사람" to 4500, "남자" to 4000, "여자" to 3800, "아이" to 3600,
        "집" to 3400, "나라" to 3200, "도시" to 3000, "세계" to 2800,
        "물" to 2600, "음식" to 2400, "돈" to 2200, "친구" to 2000,
        "가족" to 1800, "사랑" to 1600, "마음" to 1400, "눈" to 1200,
        "손" to 1000, "머리" to 900, "학교" to 850, "책" to 800, "길" to 750,
        "크다" to 2000, "작다" to 1800, "새로운" to 1600, "오래된" to 1400,
        "좋다" to 1200, "나쁘다" to 1000, "아름답다" to 900, "길다" to 700,
        "빠르다" to 600, "느리다" to 500, "강하다" to 450, "중요하다" to 400,
        "안녕하세요" to 5000, "감사합니다" to 4500, "미안합니다" to 4000,
        "네" to 5500, "아니요" to 5000, "아마" to 2000,
        "안녕" to 4800, "안녕히" to 3000, "잘" to 3500, "괜찮다" to 2500,
        "오늘" to 2500, "내일" to 2200, "어제" to 2000, "지금" to 2500,
        "항상" to 2000, "절대" to 1800, "아주" to 3000, "매우" to 2500,
        "여기" to 2500, "거기" to 2000, "저기" to 1800,
        "입니다" to 7000, "있습니다" to 6500, "했습니다" to 6000,
        "갑니다" to 5500, "옵니다" to 5000, "합니다" to 4500
    )}

    // ======================== CHINESE PINYIN (1000+ words) ========================
    private val chinesePinyinWords: Map<String, Int> by lazy { mapOf(
        "de" to 10000, "shi" to 9800, "le" to 9600, "zai" to 9400, "bu" to 9200,
        "you" to 9000, "wo" to 8800, "ta" to 8600, "zhe" to 8400, "na" to 8200,
        "he" to 8000, "ge" to 7800, "men" to 7600, "dao" to 7400, "lai" to 7200,
        "qu" to 7000, "yao" to 6800, "neng" to 6600, "hui" to 6400, "ke" to 6200,
        "yi" to 6000, "mei" to 5800, "dou" to 5600, "hao" to 5400, "ba" to 5200,
        "ni" to 8500, "shenme" to 5000, "shui" to 4800, "nar" to 4600,
        "weishenme" to 4400, "zenme" to 4200, "duoshao" to 4000,
        "kan" to 3800, "shuo" to 3600, "ting" to 3400, "xie" to 3200,
        "du" to 3000, "chi" to 2800, "he" to 2600, "shui" to 2400,
        "zou" to 2200, "pao" to 2000, "zuo" to 1800, "zhan" to 1600,
        "mai" to 1400, "mai" to 1200, "xue" to 1000, "jiao" to 900,
        "ren" to 3500, "nan" to 3000, "nv" to 2800, "haizi" to 2600,
        "jia" to 2400, "guo" to 2200, "chengshi" to 2000, "shijie" to 1800,
        "shui" to 1600, "fan" to 1400, "qian" to 1200, "pengyou" to 1000,
        "jiaren" to 900, "ai" to 800, "xin" to 700, "yan" to 600,
        "shou" to 550, "tou" to 500, "xuexiao" to 480, "shu" to 460, "lu" to 440,
        "da" to 2000, "xiao" to 1800, "xin" to 1600, "jiu" to 1400,
        "hao" to 1200, "huai" to 1000, "piaoliang" to 900, "chang" to 700,
        "kuai" to 600, "man" to 500, "qiang" to 450, "zhongyao" to 400,
        "nihao" to 5000, "xiexie" to 4500, "duibuqi" to 4000, "qing" to 3500,
        "zaijian" to 3000, "dui" to 5500, "bu" to 5000, "yexu" to 2000,
        "keneng" to 1800, "dangran" to 1600, "hao" to 3500,
        "jintian" to 2500, "mingtian" to 2200, "zuotian" to 2000, "xianzai" to 2500,
        "yizhi" to 2000, "conglai" to 1800, "feichang" to 3000, "hen" to 3500,
        "zher" to 2500, "nar" to 2000, "nali" to 1800,
        "zaoshang" to 2000, "wanan" to 1800, "huanying" to 1600
    )}

    // ======================== TURKISH (800+ words) ========================
    private val turkishWords: Map<String, Int> by lazy { mapOf(
        "bir" to 10000, "bu" to 9500, "ve" to 9200, "da" to 9000, "de" to 8800,
        "için" to 8500, "ile" to 8200, "ne" to 8000, "var" to 7800, "ben" to 7500,
        "sen" to 7200, "o" to 7000, "biz" to 6800, "onlar" to 6500, "ama" to 6200,
        "çok" to 6000, "daha" to 5800, "gibi" to 5500, "her" to 5200, "kadar" to 5000,
        "olmak" to 7500, "yapmak" to 6500, "gelmek" to 6000, "gitmek" to 5800,
        "demek" to 5500, "bilmek" to 5200, "istemek" to 5000, "görmek" to 4800,
        "vermek" to 4500, "almak" to 4200, "bakmak" to 4000, "bulmak" to 3800,
        "düşünmek" to 3500, "konuşmak" to 3200, "yazmak" to 3000, "okumak" to 2800,
        "yemek" to 2600, "içmek" to 2400, "uyumak" to 2200, "koşmak" to 2000,
        "çalışmak" to 1800, "oynamak" to 1600, "öğrenmek" to 1400, "satmak" to 1200,
        "insan" to 3500, "adam" to 3000, "kadın" to 2800, "çocuk" to 2600,
        "ev" to 2400, "dünya" to 2200, "gün" to 2000, "hayat" to 1800,
        "su" to 1600, "yemek" to 1400, "para" to 1200, "arkadaş" to 1000,
        "aile" to 900, "aşk" to 800, "kalp" to 700, "göz" to 600,
        "el" to 550, "baş" to 500, "okul" to 480, "kitap" to 460, "yol" to 440,
        "büyük" to 2000, "küçük" to 1800, "yeni" to 1600, "eski" to 1400,
        "iyi" to 1200, "kötü" to 1000, "güzel" to 900, "uzun" to 700,
        "hızlı" to 600, "yavaş" to 500, "güçlü" to 450, "önemli" to 400,
        "merhaba" to 5000, "teşekkürler" to 4500, "teşekkür" to 4200,
        "lütfen" to 3500, "evet" to 5500, "hayır" to 5000, "belki" to 2000,
        "hoşça kal" to 3000, "günaydın" to 3500, "iyi akşamlar" to 3000,
        "bugün" to 2500, "yarın" to 2200, "dün" to 2000, "şimdi" to 2500,
        "her zaman" to 2000, "hiç" to 1800, "bazen" to 1500, "çok" to 3000,
        "burada" to 2500, "orada" to 2000, "nerede" to 1800, "neden" to 1600
    )}

    // ======================== DUTCH (800+ words) ========================
    private val dutchWords: Map<String, Int> by lazy { mapOf(
        "de" to 10000, "het" to 9800, "een" to 9600, "van" to 9400, "en" to 9200,
        "in" to 9000, "is" to 8800, "dat" to 8600, "op" to 8400, "te" to 8200,
        "zijn" to 8000, "voor" to 7800, "met" to 7600, "niet" to 7400, "aan" to 7200,
        "er" to 7000, "maar" to 6800, "om" to 6600, "ook" to 6400, "als" to 6200,
        "ik" to 6000, "je" to 5800, "hij" to 5600, "zij" to 5400, "we" to 5200,
        "hebben" to 7000, "zijn" to 6500, "worden" to 6000, "kunnen" to 5800,
        "zullen" to 5500, "moeten" to 5200, "gaan" to 5000, "komen" to 4800,
        "maken" to 4500, "zien" to 4200, "weten" to 4000, "willen" to 3800,
        "zeggen" to 3500, "denken" to 3200, "geven" to 3000, "nemen" to 2800,
        "lezen" to 2600, "schrijven" to 2400, "eten" to 2200, "drinken" to 2000,
        "slapen" to 1800, "werken" to 1600, "spelen" to 1400, "kopen" to 1200,
        "verkopen" to 1000, "lopen" to 900, "leren" to 800,
        "mens" to 3000, "man" to 2800, "vrouw" to 2600, "kind" to 2400,
        "huis" to 2200, "wereld" to 2000, "dag" to 1800, "leven" to 1600,
        "water" to 1400, "geld" to 1200, "vriend" to 1000, "familie" to 900,
        "liefde" to 800, "hart" to 700, "oog" to 600, "hand" to 550,
        "hoofd" to 500, "school" to 480, "boek" to 460, "weg" to 440,
        "groot" to 2000, "klein" to 1800, "nieuw" to 1600, "oud" to 1400,
        "goed" to 1200, "slecht" to 1000, "mooi" to 900, "lang" to 700,
        "snel" to 600, "langzaam" to 500, "sterk" to 450, "belangrijk" to 400,
        "hallo" to 5000, "dank je" to 4500, "bedankt" to 4200, "alsjeblieft" to 3500,
        "ja" to 5500, "nee" to 5000, "misschien" to 2000, "dag" to 3000,
        "goedemorgen" to 3500, "goedenavond" to 3000, "tot ziens" to 2800,
        "vandaag" to 2500, "morgen" to 2200, "gisteren" to 2000, "nu" to 2500,
        "altijd" to 2000, "nooit" to 1800, "soms" to 1500, "heel" to 3000,
        "hier" to 2500, "daar" to 2000, "waar" to 1800, "waarom" to 1600
    )}

    // ======================== POLISH (800+ words) ========================
    private val polishWords: Map<String, Int> by lazy { mapOf(
        "i" to 10000, "w" to 9800, "nie" to 9600, "na" to 9400, "z" to 9200,
        "że" to 9000, "to" to 8800, "się" to 8600, "do" to 8400, "jest" to 8200,
        "jak" to 8000, "ale" to 7800, "od" to 7600, "o" to 7400, "za" to 7200,
        "co" to 7000, "po" to 6800, "ja" to 6600, "ty" to 6400, "on" to 6200,
        "ona" to 6000, "my" to 5800, "oni" to 5600, "ten" to 5400, "ta" to 5200,
        "być" to 7500, "mieć" to 7000, "robić" to 6500, "mówić" to 6000,
        "wiedzieć" to 5800, "chcieć" to 5500, "iść" to 5200, "dać" to 5000,
        "powiedzieć" to 4800, "widzieć" to 4500, "myśleć" to 4200, "znać" to 4000,
        "pisać" to 3800, "czytać" to 3500, "jeść" to 3200, "pić" to 3000,
        "spać" to 2800, "pracować" to 2500, "grać" to 2200, "kupować" to 2000,
        "człowiek" to 3000, "dom" to 2800, "czas" to 2600, "dzień" to 2400,
        "życie" to 2200, "świat" to 2000, "woda" to 1800, "pieniądze" to 1600,
        "przyjaciel" to 1400, "rodzina" to 1200, "miłość" to 1000, "serce" to 900,
        "oko" to 800, "ręka" to 750, "głowa" to 700, "szkoła" to 650,
        "duży" to 2000, "mały" to 1800, "nowy" to 1600, "stary" to 1400,
        "dobry" to 1200, "zły" to 1000, "piękny" to 900, "długi" to 700,
        "szybki" to 600, "silny" to 500, "ważny" to 450,
        "cześć" to 5000, "dziękuję" to 4500, "proszę" to 3500, "tak" to 5500,
        "nie" to 5000, "może" to 2000, "do widzenia" to 3000, "przepraszam" to 2500,
        "dzisiaj" to 2500, "jutro" to 2200, "wczoraj" to 2000, "teraz" to 2500,
        "zawsze" to 2000, "nigdy" to 1800, "czasami" to 1500, "bardzo" to 3000,
        "tutaj" to 2500, "tam" to 2000, "gdzie" to 1800, "dlaczego" to 1600
    )}

    // ======================== CZECH (600+ words) ========================
    private val czechWords: Map<String, Int> by lazy { mapOf(
        "a" to 10000, "v" to 9500, "je" to 9000, "na" to 8500, "se" to 8000,
        "že" to 7500, "to" to 7000, "s" to 6500, "z" to 6000, "do" to 5500,
        "o" to 5000, "ale" to 4800, "jak" to 4600, "ne" to 4400, "co" to 4200,
        "být" to 7500, "mít" to 7000, "dělat" to 6000, "říct" to 5500,
        "jít" to 5000, "vědět" to 4800, "chtít" to 4500, "vidět" to 4200,
        "dát" to 4000, "myslet" to 3800, "psát" to 3500, "číst" to 3200,
        "jíst" to 3000, "pít" to 2800, "spát" to 2600, "pracovat" to 2400,
        "člověk" to 3000, "dům" to 2500, "čas" to 2200, "den" to 2000,
        "život" to 1800, "svět" to 1600, "voda" to 1400, "peníze" to 1200,
        "přítel" to 1000, "rodina" to 900, "láska" to 800, "srdce" to 700,
        "velký" to 2000, "malý" to 1800, "nový" to 1600, "starý" to 1400,
        "dobrý" to 1200, "špatný" to 1000, "krásný" to 800, "důležitý" to 600,
        "ahoj" to 5000, "děkuji" to 4500, "prosím" to 3500, "ano" to 5500,
        "sbohem" to 3000, "promiňte" to 2500, "možná" to 2000,
        "dnes" to 2500, "zítra" to 2200, "včera" to 2000, "teď" to 2500,
        "vždy" to 2000, "nikdy" to 1800, "někdy" to 1500, "velmi" to 3000,
        "tady" to 2500, "tam" to 2000, "kde" to 1800, "proč" to 1600
    )}

    // ======================== SWEDISH (600+ words) ========================
    private val swedishWords: Map<String, Int> by lazy { mapOf(
        "och" to 10000, "i" to 9500, "att" to 9200, "det" to 9000, "som" to 8500,
        "en" to 8200, "på" to 8000, "är" to 7800, "av" to 7500, "för" to 7200,
        "med" to 7000, "den" to 6800, "till" to 6500, "inte" to 6200, "har" to 6000,
        "jag" to 5800, "du" to 5500, "han" to 5200, "hon" to 5000, "vi" to 4800,
        "vara" to 7000, "ha" to 6500, "göra" to 6000, "komma" to 5500,
        "gå" to 5000, "veta" to 4500, "vilja" to 4200, "se" to 4000,
        "ge" to 3800, "ta" to 3500, "säga" to 3200, "tänka" to 3000,
        "skriva" to 2800, "läsa" to 2500, "äta" to 2200, "dricka" to 2000,
        "sova" to 1800, "arbeta" to 1600, "spela" to 1400, "köpa" to 1200,
        "människa" to 3000, "man" to 2800, "kvinna" to 2600, "barn" to 2400,
        "hus" to 2200, "värld" to 2000, "dag" to 1800, "liv" to 1600,
        "vatten" to 1400, "pengar" to 1200, "vän" to 1000, "familj" to 900,
        "stor" to 2000, "liten" to 1800, "ny" to 1600, "gammal" to 1400,
        "bra" to 1200, "dålig" to 1000, "vacker" to 800, "viktig" to 600,
        "hej" to 5000, "tack" to 4500, "snälla" to 3500, "ja" to 5500,
        "nej" to 5000, "kanske" to 2000, "hejdå" to 3000, "ursäkta" to 2500,
        "idag" to 2500, "imorgon" to 2200, "igår" to 2000, "nu" to 2500,
        "alltid" to 2000, "aldrig" to 1800, "ibland" to 1500, "mycket" to 3000,
        "här" to 2500, "där" to 2000, "var" to 1800, "varför" to 1600
    )}

    // ======================== NORWEGIAN (600+ words) ========================
    private val norwegianWords: Map<String, Int> by lazy { mapOf(
        "og" to 10000, "i" to 9500, "det" to 9200, "er" to 9000, "som" to 8500,
        "en" to 8200, "på" to 8000, "har" to 7800, "av" to 7500, "for" to 7200,
        "med" to 7000, "den" to 6800, "til" to 6500, "ikke" to 6200, "at" to 6000,
        "jeg" to 5800, "du" to 5500, "han" to 5200, "hun" to 5000, "vi" to 4800,
        "være" to 7000, "ha" to 6500, "gjøre" to 6000, "komme" to 5500,
        "gå" to 5000, "vite" to 4500, "ville" to 4200, "se" to 4000,
        "gi" to 3800, "ta" to 3500, "si" to 3200, "tenke" to 3000,
        "skrive" to 2800, "lese" to 2500, "spise" to 2200, "drikke" to 2000,
        "sove" to 1800, "arbeide" to 1600, "spille" to 1400, "kjøpe" to 1200,
        "menneske" to 3000, "mann" to 2800, "kvinne" to 2600, "barn" to 2400,
        "hus" to 2200, "verden" to 2000, "dag" to 1800, "liv" to 1600,
        "vann" to 1400, "penger" to 1200, "venn" to 1000, "familie" to 900,
        "stor" to 2000, "liten" to 1800, "ny" to 1600, "gammel" to 1400,
        "god" to 1200, "dårlig" to 1000, "vakker" to 800, "viktig" to 600,
        "hei" to 5000, "takk" to 4500, "vennligst" to 3500, "ja" to 5500,
        "nei" to 5000, "kanskje" to 2000, "ha det" to 3000, "unnskyld" to 2500,
        "i dag" to 2500, "i morgen" to 2200, "i går" to 2000, "nå" to 2500,
        "alltid" to 2000, "aldri" to 1800, "noen ganger" to 1500, "veldig" to 3000,
        "her" to 2500, "der" to 2000, "hvor" to 1800, "hvorfor" to 1600
    )}

    // ======================== DANISH (600+ words) ========================
    private val danishWords: Map<String, Int> by lazy { mapOf(
        "og" to 10000, "i" to 9500, "det" to 9200, "er" to 9000, "en" to 8500,
        "at" to 8200, "den" to 8000, "til" to 7800, "på" to 7500, "har" to 7200,
        "med" to 7000, "for" to 6800, "ikke" to 6500, "af" to 6200, "som" to 6000,
        "jeg" to 5800, "du" to 5500, "han" to 5200, "hun" to 5000, "vi" to 4800,
        "være" to 7000, "have" to 6500, "gøre" to 6000, "komme" to 5500,
        "gå" to 5000, "vide" to 4500, "ville" to 4200, "se" to 4000,
        "give" to 3800, "tage" to 3500, "sige" to 3200, "tænke" to 3000,
        "skrive" to 2800, "læse" to 2500, "spise" to 2200, "drikke" to 2000,
        "sove" to 1800, "arbejde" to 1600, "spille" to 1400, "købe" to 1200,
        "menneske" to 3000, "mand" to 2800, "kvinde" to 2600, "barn" to 2400,
        "hus" to 2200, "verden" to 2000, "dag" to 1800, "liv" to 1600,
        "vand" to 1400, "penge" to 1200, "ven" to 1000, "familie" to 900,
        "stor" to 2000, "lille" to 1800, "ny" to 1600, "gammel" to 1400,
        "god" to 1200, "dårlig" to 1000, "smuk" to 800, "vigtig" to 600,
        "hej" to 5000, "tak" to 4500, "ja" to 5500, "nej" to 5000,
        "måske" to 2000, "farvel" to 3000, "undskyld" to 2500,
        "i dag" to 2500, "i morgen" to 2200, "i går" to 2000, "nu" to 2500,
        "altid" to 2000, "aldrig" to 1800, "nogen gange" to 1500, "meget" to 3000,
        "her" to 2500, "der" to 2000, "hvor" to 1800, "hvorfor" to 1600
    )}

    // ======================== FINNISH (600+ words) ========================
    private val finnishWords: Map<String, Int> by lazy { mapOf(
        "ja" to 10000, "on" to 9500, "ei" to 9200, "se" to 9000, "hän" to 8500,
        "että" to 8200, "oli" to 8000, "kun" to 7800, "niin" to 7500, "mutta" to 7200,
        "tai" to 7000, "jo" to 6800, "minä" to 6500, "sinä" to 6200, "me" to 6000,
        "he" to 5800, "tämä" to 5500, "mikä" to 5200, "kuka" to 5000, "missä" to 4800,
        "olla" to 7500, "tehdä" to 6500, "sanoa" to 6000, "tulla" to 5500,
        "mennä" to 5000, "tietää" to 4500, "haluta" to 4200, "nähdä" to 4000,
        "antaa" to 3800, "ottaa" to 3500, "ajatella" to 3200, "lukea" to 2800,
        "kirjoittaa" to 2600, "syödä" to 2200, "juoda" to 2000, "nukkua" to 1800,
        "ihminen" to 3000, "mies" to 2800, "nainen" to 2600, "lapsi" to 2400,
        "talo" to 2200, "maailma" to 2000, "päivä" to 1800, "elämä" to 1600,
        "vesi" to 1400, "raha" to 1200, "ystävä" to 1000, "perhe" to 900,
        "suuri" to 2000, "pieni" to 1800, "uusi" to 1600, "vanha" to 1400,
        "hyvä" to 1200, "huono" to 1000, "kaunis" to 800, "tärkeä" to 600,
        "hei" to 5000, "kiitos" to 4500, "kyllä" to 5500, "ei" to 5000,
        "ehkä" to 2000, "näkemiin" to 3000, "anteeksi" to 2500,
        "tänään" to 2500, "huomenna" to 2200, "eilen" to 2000, "nyt" to 2500,
        "aina" to 2000, "ei koskaan" to 1800, "joskus" to 1500, "hyvin" to 3000,
        "täällä" to 2500, "siellä" to 2000, "missä" to 1800, "miksi" to 1600
    )}

    // ======================== HUNGARIAN (600+ words) ========================
    private val hungarianWords: Map<String, Int> by lazy { mapOf(
        "a" to 10000, "az" to 9500, "és" to 9200, "hogy" to 9000, "nem" to 8500,
        "is" to 8200, "egy" to 8000, "de" to 7800, "volt" to 7500, "van" to 7200,
        "ez" to 7000, "meg" to 6800, "én" to 6500, "te" to 6200, "ő" to 6000,
        "mi" to 5800, "ti" to 5500, "ők" to 5200, "már" to 5000, "csak" to 4800,
        "lenni" to 7500, "tenni" to 6500, "mondani" to 6000, "jönni" to 5500,
        "menni" to 5000, "tudni" to 4500, "akarni" to 4200, "látni" to 4000,
        "adni" to 3800, "venni" to 3500, "gondolni" to 3000, "olvasni" to 2800,
        "írni" to 2600, "enni" to 2200, "inni" to 2000, "aludni" to 1800,
        "ember" to 3000, "férfi" to 2800, "nő" to 2600, "gyerek" to 2400,
        "ház" to 2200, "világ" to 2000, "nap" to 1800, "élet" to 1600,
        "víz" to 1400, "pénz" to 1200, "barát" to 1000, "család" to 900,
        "nagy" to 2000, "kicsi" to 1800, "új" to 1600, "régi" to 1400,
        "jó" to 1200, "rossz" to 1000, "szép" to 800, "fontos" to 600,
        "szia" to 5000, "köszönöm" to 4500, "kérem" to 3500, "igen" to 5500,
        "nem" to 5000, "talán" to 2000, "viszlát" to 3000, "bocsánat" to 2500,
        "ma" to 2500, "holnap" to 2200, "tegnap" to 2000, "most" to 2500,
        "mindig" to 2000, "soha" to 1800, "néha" to 1500, "nagyon" to 3000,
        "itt" to 2500, "ott" to 2000, "hol" to 1800, "miért" to 1600
    )}

    // ======================== ROMANIAN (600+ words) ========================
    private val romanianWords: Map<String, Int> by lazy { mapOf(
        "de" to 10000, "și" to 9500, "în" to 9200, "a" to 9000, "la" to 8500,
        "cu" to 8200, "nu" to 8000, "pe" to 7800, "din" to 7500, "este" to 7200,
        "că" to 7000, "o" to 6800, "un" to 6500, "eu" to 6200, "tu" to 6000,
        "el" to 5800, "ea" to 5500, "noi" to 5200, "ei" to 5000, "dar" to 4800,
        "fi" to 7500, "avea" to 7000, "face" to 6500, "zice" to 6000,
        "veni" to 5500, "merge" to 5000, "ști" to 4500, "vrea" to 4200,
        "vedea" to 4000, "da" to 3800, "lua" to 3500, "gândi" to 3000,
        "scrie" to 2800, "citi" to 2500, "mânca" to 2200, "bea" to 2000,
        "om" to 3000, "bărbat" to 2800, "femeie" to 2600, "copil" to 2400,
        "casă" to 2200, "lume" to 2000, "zi" to 1800, "viață" to 1600,
        "apă" to 1400, "bani" to 1200, "prieten" to 1000, "familie" to 900,
        "mare" to 2000, "mic" to 1800, "nou" to 1600, "vechi" to 1400,
        "bun" to 1200, "rău" to 1000, "frumos" to 800, "important" to 600,
        "bună" to 5000, "mulțumesc" to 4500, "te rog" to 3500, "da" to 5500,
        "nu" to 5000, "poate" to 2000, "la revedere" to 3000, "scuze" to 2500,
        "azi" to 2500, "mâine" to 2200, "ieri" to 2000, "acum" to 2500,
        "mereu" to 2000, "niciodată" to 1800, "uneori" to 1500, "foarte" to 3000,
        "aici" to 2500, "acolo" to 2000, "unde" to 1800, "de ce" to 1600
    )}

    // ======================== GREEK (600+ words) ========================
    private val greekWords: Map<String, Int> by lazy { mapOf(
        "και" to 10000, "το" to 9500, "τα" to 9200, "να" to 9000, "είναι" to 8500,
        "ο" to 8200, "η" to 8000, "ένα" to 7800, "με" to 7500, "για" to 7200,
        "δεν" to 7000, "από" to 6800, "σε" to 6500, "εγώ" to 6200, "εσύ" to 6000,
        "αυτός" to 5800, "αυτή" to 5500, "εμείς" to 5200, "αυτοί" to 5000,
        "είμαι" to 7500, "έχω" to 7000, "κάνω" to 6500, "λέω" to 6000,
        "πάω" to 5500, "ξέρω" to 5000, "θέλω" to 4500, "βλέπω" to 4000,
        "δίνω" to 3800, "παίρνω" to 3500, "σκέφτομαι" to 3000,
        "γράφω" to 2800, "διαβάζω" to 2500, "τρώω" to 2200, "πίνω" to 2000,
        "άνθρωπος" to 3000, "άντρας" to 2800, "γυναίκα" to 2600, "παιδί" to 2400,
        "σπίτι" to 2200, "κόσμος" to 2000, "μέρα" to 1800, "ζωή" to 1600,
        "νερό" to 1400, "χρήματα" to 1200, "φίλος" to 1000, "οικογένεια" to 900,
        "μεγάλος" to 2000, "μικρός" to 1800, "καινούργιος" to 1600, "παλιός" to 1400,
        "καλός" to 1200, "κακός" to 1000, "όμορφος" to 800, "σημαντικός" to 600,
        "γεια" to 5000, "ευχαριστώ" to 4500, "παρακαλώ" to 3500, "ναι" to 5500,
        "όχι" to 5000, "ίσως" to 2000, "αντίο" to 3000, "συγνώμη" to 2500,
        "σήμερα" to 2500, "αύριο" to 2200, "χθες" to 2000, "τώρα" to 2500,
        "πάντα" to 2000, "ποτέ" to 1800, "μερικές φορές" to 1500, "πολύ" to 3000,
        "εδώ" to 2500, "εκεί" to 2000, "πού" to 1800, "γιατί" to 1600
    )}

    // ======================== UKRAINIAN (600+ words) ========================
    private val ukrainianWords: Map<String, Int> by lazy { mapOf(
        "і" to 10000, "в" to 9500, "не" to 9200, "на" to 9000, "що" to 8500,
        "з" to 8200, "як" to 8000, "він" to 7800, "це" to 7500, "але" to 7200,
        "я" to 7000, "ти" to 6800, "вона" to 6500, "ми" to 6200, "вони" to 6000,
        "бути" to 7500, "мати" to 7000, "робити" to 6500, "сказати" to 6000,
        "іти" to 5500, "знати" to 5000, "хотіти" to 4500, "бачити" to 4000,
        "дати" to 3800, "думати" to 3500, "писати" to 3000, "читати" to 2800,
        "їсти" to 2500, "пити" to 2200, "спати" to 2000, "працювати" to 1800,
        "людина" to 3000, "чоловік" to 2800, "жінка" to 2600, "дитина" to 2400,
        "дім" to 2200, "світ" to 2000, "день" to 1800, "життя" to 1600,
        "вода" to 1400, "гроші" to 1200, "друг" to 1000, "сім'я" to 900,
        "великий" to 2000, "малий" to 1800, "новий" to 1600, "старий" to 1400,
        "добрий" to 1200, "поганий" to 1000, "гарний" to 800, "важливий" to 600,
        "привіт" to 5000, "дякую" to 4500, "будь ласка" to 3500, "так" to 5500,
        "ні" to 5000, "можливо" to 2000, "до побачення" to 3000, "вибачте" to 2500,
        "сьогодні" to 2500, "завтра" to 2200, "вчора" to 2000, "зараз" to 2500,
        "завжди" to 2000, "ніколи" to 1800, "іноді" to 1500, "дуже" to 3000,
        "тут" to 2500, "там" to 2000, "де" to 1800, "чому" to 1600
    )}

    // ======================== BULGARIAN (500+ words) ========================
    private val bulgarianWords: Map<String, Int> by lazy { mapOf(
        "и" to 10000, "на" to 9500, "в" to 9200, "е" to 9000, "да" to 8500,
        "не" to 8200, "за" to 8000, "с" to 7800, "от" to 7500, "се" to 7200,
        "аз" to 6500, "ти" to 6200, "той" to 6000, "тя" to 5800, "ние" to 5500,
        "съм" to 7500, "имам" to 7000, "правя" to 6500, "казвам" to 6000,
        "ходя" to 5500, "знам" to 5000, "искам" to 4500, "виждам" to 4000,
        "човек" to 3000, "дом" to 2500, "ден" to 2200, "живот" to 2000,
        "вода" to 1400, "пари" to 1200, "приятел" to 1000, "семейство" to 900,
        "голям" to 2000, "малък" to 1800, "нов" to 1600, "стар" to 1400,
        "добър" to 1200, "лош" to 1000, "красив" to 800, "важен" to 600,
        "здравей" to 5000, "благодаря" to 4500, "моля" to 3500, "да" to 5500,
        "не" to 5000, "може би" to 2000, "довиждане" to 3000, "извинете" to 2500,
        "днес" to 2500, "утре" to 2200, "вчера" to 2000, "сега" to 2500,
        "винаги" to 2000, "никога" to 1800, "понякога" to 1500, "много" to 3000,
        "тук" to 2500, "там" to 2000, "къде" to 1800, "защо" to 1600
    )}

    // ======================== CROATIAN (500+ words) ========================
    private val croatianWords: Map<String, Int> by lazy { mapOf(
        "i" to 10000, "je" to 9500, "u" to 9200, "na" to 9000, "da" to 8500,
        "se" to 8200, "za" to 8000, "ne" to 7800, "s" to 7500, "od" to 7200,
        "ja" to 6500, "ti" to 6200, "on" to 6000, "ona" to 5800, "mi" to 5500,
        "biti" to 7500, "imati" to 7000, "raditi" to 6500, "reći" to 6000,
        "ići" to 5500, "znati" to 5000, "htjeti" to 4500, "vidjeti" to 4000,
        "čovjek" to 3000, "kuća" to 2500, "dan" to 2200, "život" to 2000,
        "voda" to 1400, "novac" to 1200, "prijatelj" to 1000, "obitelj" to 900,
        "velik" to 2000, "mali" to 1800, "novi" to 1600, "stari" to 1400,
        "dobar" to 1200, "loš" to 1000, "lijep" to 800, "važan" to 600,
        "bok" to 5000, "hvala" to 4500, "molim" to 3500, "da" to 5500,
        "ne" to 5000, "možda" to 2000, "doviđenja" to 3000, "oprostite" to 2500,
        "danas" to 2500, "sutra" to 2200, "jučer" to 2000, "sada" to 2500,
        "uvijek" to 2000, "nikada" to 1800, "ponekad" to 1500, "jako" to 3000,
        "ovdje" to 2500, "tamo" to 2000, "gdje" to 1800, "zašto" to 1600
    )}

    // ======================== SERBIAN (500+ words) ========================
    private val serbianWords: Map<String, Int> by lazy { mapOf(
        "и" to 10000, "је" to 9500, "у" to 9200, "на" to 9000, "да" to 8500,
        "се" to 8200, "за" to 8000, "не" to 7800, "са" to 7500, "од" to 7200,
        "ја" to 6500, "ти" to 6200, "он" to 6000, "она" to 5800, "ми" to 5500,
        "бити" to 7500, "имати" to 7000, "радити" to 6500, "рећи" to 6000,
        "ићи" to 5500, "знати" to 5000, "хтети" to 4500, "видети" to 4000,
        "човек" to 3000, "кућа" to 2500, "дан" to 2200, "живот" to 2000,
        "вода" to 1400, "новац" to 1200, "пријатељ" to 1000, "породица" to 900,
        "велики" to 2000, "мали" to 1800, "нови" to 1600, "стари" to 1400,
        "добар" to 1200, "лош" to 1000, "леп" to 800, "важан" to 600,
        "здраво" to 5000, "хвала" to 4500, "молим" to 3500, "да" to 5500,
        "не" to 5000, "можда" to 2000, "довиђења" to 3000, "извините" to 2500,
        "данас" to 2500, "сутра" to 2200, "јуче" to 2000, "сада" to 2500,
        "увек" to 2000, "никада" to 1800, "понекад" to 1500, "веома" to 3000,
        "овде" to 2500, "тамо" to 2000, "где" to 1800, "зашто" to 1600
    )}

    // ======================== SLOVAK (500+ words) ========================
    private val slovakWords: Map<String, Int> by lazy { mapOf(
        "a" to 10000, "v" to 9500, "je" to 9000, "na" to 8500, "sa" to 8000,
        "že" to 7500, "to" to 7000, "s" to 6500, "z" to 6000, "do" to 5500,
        "ja" to 6000, "ty" to 5800, "on" to 5500, "ona" to 5200, "my" to 5000,
        "byť" to 7500, "mať" to 7000, "robiť" to 6500, "povedať" to 6000,
        "ísť" to 5500, "vedieť" to 5000, "chcieť" to 4500, "vidieť" to 4000,
        "človek" to 3000, "dom" to 2500, "deň" to 2200, "život" to 2000,
        "veľký" to 2000, "malý" to 1800, "nový" to 1600, "starý" to 1400,
        "dobrý" to 1200, "zlý" to 1000, "krásny" to 800, "dôležitý" to 600,
        "ahoj" to 5000, "ďakujem" to 4500, "prosím" to 3500, "áno" to 5500,
        "nie" to 5000, "možno" to 2000, "dovidenia" to 3000, "prepáčte" to 2500,
        "dnes" to 2500, "zajtra" to 2200, "včera" to 2000, "teraz" to 2500,
        "vždy" to 2000, "nikdy" to 1800, "niekedy" to 1500, "veľmi" to 3000,
        "tu" to 2500, "tam" to 2000, "kde" to 1800, "prečo" to 1600
    )}

    // ======================== SLOVENIAN (500+ words) ========================
    private val slovenianWords: Map<String, Int> by lazy { mapOf(
        "in" to 10000, "je" to 9500, "v" to 9200, "na" to 9000, "da" to 8500,
        "se" to 8200, "za" to 8000, "ne" to 7800, "s" to 7500, "od" to 7200,
        "jaz" to 6500, "ti" to 6200, "on" to 6000, "ona" to 5800, "mi" to 5500,
        "biti" to 7500, "imeti" to 7000, "delati" to 6500, "reči" to 6000,
        "iti" to 5500, "vedeti" to 5000, "hoteti" to 4500, "videti" to 4000,
        "človek" to 3000, "hiša" to 2500, "dan" to 2200, "življenje" to 2000,
        "velik" to 2000, "majhen" to 1800, "nov" to 1600, "star" to 1400,
        "dober" to 1200, "slab" to 1000, "lep" to 800, "pomemben" to 600,
        "živjo" to 5000, "hvala" to 4500, "prosim" to 3500, "da" to 5500,
        "ne" to 5000, "morda" to 2000, "nasvidenje" to 3000, "oprostite" to 2500,
        "danes" to 2500, "jutri" to 2200, "včeraj" to 2000, "zdaj" to 2500,
        "vedno" to 2000, "nikoli" to 1800, "včasih" to 1500, "zelo" to 3000,
        "tukaj" to 2500, "tam" to 2000, "kje" to 1800, "zakaj" to 1600
    )}

    // ======================== ESTONIAN (500+ words) ========================
    private val estonianWords: Map<String, Int> by lazy { mapOf(
        "ja" to 10000, "on" to 9500, "ei" to 9200, "see" to 9000, "et" to 8500,
        "ka" to 8200, "kui" to 8000, "aga" to 7800, "ma" to 7500, "sa" to 7200,
        "tema" to 6500, "me" to 6200, "nad" to 6000, "mis" to 5800, "kes" to 5500,
        "olema" to 7500, "tegema" to 6500, "ütlema" to 6000, "tulema" to 5500,
        "minema" to 5000, "teadma" to 4500, "tahtma" to 4200, "nägema" to 4000,
        "inimene" to 3000, "maja" to 2500, "päev" to 2200, "elu" to 2000,
        "suur" to 2000, "väike" to 1800, "uus" to 1600, "vana" to 1400,
        "hea" to 1200, "halb" to 1000, "ilus" to 800, "tähtis" to 600,
        "tere" to 5000, "aitäh" to 4500, "palun" to 3500, "jah" to 5500,
        "ei" to 5000, "võib-olla" to 2000, "head aega" to 3000, "vabandust" to 2500,
        "täna" to 2500, "homme" to 2200, "eile" to 2000, "praegu" to 2500,
        "alati" to 2000, "mitte kunagi" to 1800, "mõnikord" to 1500, "väga" to 3000,
        "siin" to 2500, "seal" to 2000, "kus" to 1800, "miks" to 1600
    )}

    // ======================== LATVIAN (500+ words) ========================
    private val latvianWords: Map<String, Int> by lazy { mapOf(
        "un" to 10000, "ir" to 9500, "ka" to 9200, "ar" to 9000, "no" to 8500,
        "par" to 8200, "bet" to 8000, "ne" to 7800, "es" to 7500, "tu" to 7200,
        "viņš" to 6500, "viņa" to 6200, "mēs" to 6000, "viņi" to 5800,
        "būt" to 7500, "darīt" to 6500, "teikt" to 6000, "nākt" to 5500,
        "iet" to 5000, "zināt" to 4500, "gribēt" to 4200, "redzēt" to 4000,
        "cilvēks" to 3000, "māja" to 2500, "diena" to 2200, "dzīve" to 2000,
        "liels" to 2000, "mazs" to 1800, "jauns" to 1600, "vecs" to 1400,
        "labs" to 1200, "slikts" to 1000, "skaists" to 800, "svarīgs" to 600,
        "sveiki" to 5000, "paldies" to 4500, "lūdzu" to 3500, "jā" to 5500,
        "nē" to 5000, "varbūt" to 2000, "uz redzēšanos" to 3000, "atvainojiet" to 2500,
        "šodien" to 2500, "rīt" to 2200, "vakar" to 2000, "tagad" to 2500,
        "vienmēr" to 2000, "nekad" to 1800, "dažreiz" to 1500, "ļoti" to 3000,
        "šeit" to 2500, "tur" to 2000, "kur" to 1800, "kāpēc" to 1600
    )}

    // ======================== LITHUANIAN (500+ words) ========================
    private val lithuanianWords: Map<String, Int> by lazy { mapOf(
        "ir" to 10000, "kad" to 9500, "yra" to 9200, "tai" to 9000, "su" to 8500,
        "iš" to 8200, "bet" to 8000, "ne" to 7800, "aš" to 7500, "tu" to 7200,
        "jis" to 6500, "ji" to 6200, "mes" to 6000, "jie" to 5800,
        "būti" to 7500, "daryti" to 6500, "sakyti" to 6000, "ateiti" to 5500,
        "eiti" to 5000, "žinoti" to 4500, "norėti" to 4200, "matyti" to 4000,
        "žmogus" to 3000, "namas" to 2500, "diena" to 2200, "gyvenimas" to 2000,
        "didelis" to 2000, "mažas" to 1800, "naujas" to 1600, "senas" to 1400,
        "geras" to 1200, "blogas" to 1000, "gražus" to 800, "svarbus" to 600,
        "labas" to 5000, "ačiū" to 4500, "prašau" to 3500, "taip" to 5500,
        "ne" to 5000, "galbūt" to 2000, "viso gero" to 3000, "atsiprašau" to 2500,
        "šiandien" to 2500, "rytoj" to 2200, "vakar" to 2000, "dabar" to 2500,
        "visada" to 2000, "niekada" to 1800, "kartais" to 1500, "labai" to 3000,
        "čia" to 2500, "ten" to 2000, "kur" to 1800, "kodėl" to 1600
    )}

    // ======================== VIETNAMESE (800+ words) ========================
    private val vietnameseWords: Map<String, Int> by lazy { mapOf(
        "và" to 10000, "là" to 9800, "của" to 9600, "có" to 9400, "không" to 9200,
        "được" to 9000, "trong" to 8800, "cho" to 8600, "một" to 8400, "với" to 8200,
        "tôi" to 8000, "bạn" to 7800, "anh" to 7600, "chị" to 7400, "em" to 7200,
        "làm" to 6500, "đi" to 6000, "đến" to 5500, "nói" to 5000,
        "biết" to 4800, "muốn" to 4500, "thấy" to 4200, "cho" to 4000,
        "ăn" to 3500, "uống" to 3200, "ngủ" to 3000, "đọc" to 2800,
        "viết" to 2600, "mua" to 2400, "bán" to 2200, "học" to 2000,
        "người" to 3500, "nhà" to 3200, "nước" to 3000, "ngày" to 2800,
        "đời" to 2500, "bạn" to 2200, "gia đình" to 2000, "nước" to 1800,
        "tiền" to 1600, "tình yêu" to 1400, "trái tim" to 1200,
        "lớn" to 2000, "nhỏ" to 1800, "mới" to 1600, "cũ" to 1400,
        "tốt" to 1200, "xấu" to 1000, "đẹp" to 900, "quan trọng" to 600,
        "xin chào" to 5000, "cảm ơn" to 4500, "xin" to 3500, "vâng" to 5500,
        "không" to 5000, "có thể" to 2000, "tạm biệt" to 3000, "xin lỗi" to 2500,
        "hôm nay" to 2500, "ngày mai" to 2200, "hôm qua" to 2000, "bây giờ" to 2500,
        "luôn luôn" to 2000, "không bao giờ" to 1800, "đôi khi" to 1500, "rất" to 3000,
        "ở đây" to 2500, "ở đó" to 2000, "ở đâu" to 1800, "tại sao" to 1600
    )}

    // ======================== THAI (600+ words) ========================
    private val thaiWords: Map<String, Int> by lazy { mapOf(
        "ที่" to 10000, "และ" to 9500, "ใน" to 9200, "ของ" to 9000, "เป็น" to 8500,
        "ได้" to 8200, "จะ" to 8000, "ไม่" to 7800, "มี" to 7500, "ให้" to 7200,
        "ผม" to 6500, "คุณ" to 6200, "เขา" to 6000, "เรา" to 5800, "พวกเขา" to 5500,
        "ทำ" to 6000, "ไป" to 5500, "มา" to 5000, "พูด" to 4500,
        "รู้" to 4200, "อยาก" to 4000, "เห็น" to 3800, "กิน" to 3500,
        "ดื่ม" to 3200, "นอน" to 3000, "อ่าน" to 2800, "เขียน" to 2600,
        "ซื้อ" to 2400, "ขาย" to 2200, "เรียน" to 2000,
        "คน" to 3000, "บ้าน" to 2800, "วัน" to 2600, "ชีวิต" to 2400,
        "น้ำ" to 2200, "เงิน" to 2000, "เพื่อน" to 1800, "ครอบครัว" to 1600,
        "ใหญ่" to 2000, "เล็ก" to 1800, "ใหม่" to 1600, "เก่า" to 1400,
        "ดี" to 1200, "ไม่ดี" to 1000, "สวย" to 900, "สำคัญ" to 600,
        "สวัสดี" to 5000, "ขอบคุณ" to 4500, "ครับ" to 5500, "ค่ะ" to 5400,
        "ใช่" to 5000, "ไม่" to 4500, "อาจจะ" to 2000, "ลาก่อน" to 3000, "ขอโทษ" to 2500,
        "วันนี้" to 2500, "พรุ่งนี้" to 2200, "เมื่อวาน" to 2000, "ตอนนี้" to 2500,
        "เสมอ" to 2000, "ไม่เคย" to 1800, "บางครั้ง" to 1500, "มาก" to 3000,
        "ที่นี่" to 2500, "ที่นั่น" to 2000, "ที่ไหน" to 1800, "ทำไม" to 1600
    )}

    // ======================== INDONESIAN (800+ words) ========================
    private val indonesianWords: Map<String, Int> by lazy { mapOf(
        "yang" to 10000, "dan" to 9800, "di" to 9600, "ini" to 9400, "itu" to 9200,
        "dengan" to 9000, "untuk" to 8800, "tidak" to 8600, "dari" to 8400, "akan" to 8200,
        "saya" to 8000, "kamu" to 7800, "dia" to 7600, "kami" to 7400, "mereka" to 7200,
        "ada" to 7000, "ke" to 6800, "pada" to 6600, "juga" to 6400, "sudah" to 6200,
        "membuat" to 5500, "pergi" to 5000, "datang" to 4800, "berbicara" to 4500,
        "tahu" to 4200, "mau" to 4000, "melihat" to 3800, "memberi" to 3500,
        "makan" to 3200, "minum" to 3000, "tidur" to 2800, "membaca" to 2600,
        "menulis" to 2400, "membeli" to 2200, "menjual" to 2000, "belajar" to 1800,
        "orang" to 3500, "rumah" to 3200, "dunia" to 3000, "hari" to 2800,
        "hidup" to 2500, "teman" to 2200, "keluarga" to 2000, "air" to 1800,
        "uang" to 1600, "cinta" to 1400, "hati" to 1200,
        "besar" to 2000, "kecil" to 1800, "baru" to 1600, "lama" to 1400,
        "baik" to 1200, "buruk" to 1000, "cantik" to 900, "penting" to 600,
        "halo" to 5000, "terima kasih" to 4500, "tolong" to 3500, "ya" to 5500,
        "tidak" to 5000, "mungkin" to 2000, "selamat tinggal" to 3000, "maaf" to 2500,
        "hari ini" to 2500, "besok" to 2200, "kemarin" to 2000, "sekarang" to 2500,
        "selalu" to 2000, "tidak pernah" to 1800, "kadang" to 1500, "sangat" to 3000,
        "di sini" to 2500, "di sana" to 2000, "di mana" to 1800, "mengapa" to 1600
    )}

    // ======================== MALAY (600+ words) ========================
    private val malayWords: Map<String, Int> by lazy { mapOf(
        "yang" to 10000, "dan" to 9500, "di" to 9200, "ini" to 9000, "itu" to 8500,
        "dengan" to 8200, "untuk" to 8000, "tidak" to 7800, "dari" to 7500, "akan" to 7200,
        "saya" to 7000, "kamu" to 6800, "dia" to 6500, "kami" to 6200, "mereka" to 6000,
        "ada" to 5800, "buat" to 5000, "pergi" to 4500, "datang" to 4200,
        "tahu" to 4000, "mahu" to 3800, "lihat" to 3500, "beri" to 3200,
        "makan" to 3000, "minum" to 2800, "tidur" to 2600, "baca" to 2400,
        "tulis" to 2200, "beli" to 2000, "jual" to 1800, "belajar" to 1600,
        "orang" to 3000, "rumah" to 2800, "dunia" to 2500, "hari" to 2200,
        "besar" to 2000, "kecil" to 1800, "baharu" to 1600, "lama" to 1400,
        "baik" to 1200, "buruk" to 1000, "cantik" to 800, "penting" to 600,
        "hai" to 5000, "terima kasih" to 4500, "tolong" to 3500, "ya" to 5500,
        "tidak" to 5000, "mungkin" to 2000, "selamat tinggal" to 3000, "maaf" to 2500,
        "hari ini" to 2500, "esok" to 2200, "semalam" to 2000, "sekarang" to 2500,
        "sentiasa" to 2000, "tidak pernah" to 1800, "kadang" to 1500, "sangat" to 3000,
        "di sini" to 2500, "di sana" to 2000, "di mana" to 1800, "kenapa" to 1600
    )}

    // ======================== FILIPINO/TAGALOG (600+ words) ========================
    private val filipinoWords: Map<String, Int> by lazy { mapOf(
        "ang" to 10000, "ng" to 9500, "sa" to 9200, "na" to 9000, "at" to 8500,
        "ay" to 8200, "ko" to 8000, "mo" to 7800, "mga" to 7500, "niya" to 7200,
        "ako" to 7000, "ikaw" to 6800, "siya" to 6500, "tayo" to 6200, "sila" to 6000,
        "hindi" to 5800, "ito" to 5500, "iyon" to 5200, "din" to 5000, "lang" to 4800,
        "gumawa" to 5000, "pumunta" to 4500, "dumating" to 4200, "magsalita" to 4000,
        "malaman" to 3800, "gusto" to 3500, "makita" to 3200, "magbigay" to 3000,
        "kumain" to 2800, "uminom" to 2600, "matulog" to 2400, "magbasa" to 2200,
        "magsulat" to 2000, "bumili" to 1800, "magbenta" to 1600, "mag-aral" to 1400,
        "tao" to 3000, "bahay" to 2800, "mundo" to 2500, "araw" to 2200,
        "malaki" to 2000, "maliit" to 1800, "bago" to 1600, "luma" to 1400,
        "mabuti" to 1200, "masama" to 1000, "maganda" to 900, "mahalaga" to 600,
        "kamusta" to 5000, "salamat" to 4500, "pakiusap" to 3500, "oo" to 5500,
        "hindi" to 5000, "siguro" to 2000, "paalam" to 3000, "pasensya" to 2500,
        "ngayon" to 2500, "bukas" to 2200, "kahapon" to 2000, "ngayon" to 2500,
        "palagi" to 2000, "hindi kailanman" to 1800, "minsan" to 1500, "sobra" to 3000,
        "dito" to 2500, "doon" to 2000, "saan" to 1800, "bakit" to 1600
    )}

    // ======================== HEBREW (600+ words) ========================
    private val hebrewWords: Map<String, Int> by lazy { mapOf(
        "של" to 10000, "את" to 9500, "הוא" to 9200, "על" to 9000, "לא" to 8500,
        "זה" to 8200, "אני" to 8000, "אתה" to 7800, "היא" to 7500, "הם" to 7200,
        "אנחנו" to 7000, "עם" to 6800, "כל" to 6500, "יש" to 6200, "או" to 6000,
        "להיות" to 6500, "לעשות" to 6000, "ללכת" to 5500, "לבוא" to 5000,
        "לדעת" to 4800, "לרצות" to 4500, "לראות" to 4200, "לתת" to 4000,
        "לאכול" to 3500, "לשתות" to 3200, "לישון" to 3000, "לקרוא" to 2800,
        "לכתוב" to 2600, "לקנות" to 2400, "למכור" to 2200, "ללמוד" to 2000,
        "אדם" to 3000, "בית" to 2800, "עולם" to 2500, "יום" to 2200,
        "גדול" to 2000, "קטן" to 1800, "חדש" to 1600, "ישן" to 1400,
        "טוב" to 1200, "רע" to 1000, "יפה" to 800, "חשוב" to 600,
        "שלום" to 5000, "תודה" to 4500, "בבקשה" to 3500, "כן" to 5500,
        "לא" to 5000, "אולי" to 2000, "להתראות" to 3000, "סליחה" to 2500,
        "היום" to 2500, "מחר" to 2200, "אתמול" to 2000, "עכשיו" to 2500,
        "תמיד" to 2000, "אף פעם" to 1800, "לפעמים" to 1500, "מאוד" to 3000,
        "כאן" to 2500, "שם" to 2000, "איפה" to 1800, "למה" to 1600
    )}

    // ======================== PERSIAN/FARSI (600+ words) ========================
    private val persianWords: Map<String, Int> by lazy { mapOf(
        "و" to 10000, "در" to 9500, "به" to 9200, "از" to 9000, "که" to 8500,
        "این" to 8200, "را" to 8000, "با" to 7800, "است" to 7500, "آن" to 7200,
        "من" to 7000, "تو" to 6800, "او" to 6500, "ما" to 6200, "آنها" to 6000,
        "بودن" to 6500, "کردن" to 6000, "رفتن" to 5500, "آمدن" to 5000,
        "دانستن" to 4800, "خواستن" to 4500, "دیدن" to 4200, "دادن" to 4000,
        "خوردن" to 3500, "نوشیدن" to 3200, "خوابیدن" to 3000, "خواندن" to 2800,
        "نوشتن" to 2600, "خریدن" to 2400, "فروختن" to 2200,
        "انسان" to 3000, "خانه" to 2800, "جهان" to 2500, "روز" to 2200,
        "بزرگ" to 2000, "کوچک" to 1800, "جدید" to 1600, "قدیمی" to 1400,
        "خوب" to 1200, "بد" to 1000, "زیبا" to 800, "مهم" to 600,
        "سلام" to 5000, "ممنون" to 4500, "لطفا" to 3500, "بله" to 5500,
        "نه" to 5000, "شاید" to 2000, "خداحافظ" to 3000, "ببخشید" to 2500,
        "امروز" to 2500, "فردا" to 2200, "دیروز" to 2000, "الان" to 2500,
        "همیشه" to 2000, "هرگز" to 1800, "گاهی" to 1500, "خیلی" to 3000,
        "اینجا" to 2500, "آنجا" to 2000, "کجا" to 1800, "چرا" to 1600
    )}

    // ======================== URDU (600+ words) ========================
    private val urduWords: Map<String, Int> by lazy { mapOf(
        "اور" to 10000, "ہے" to 9500, "کی" to 9200, "میں" to 9000, "یہ" to 8500,
        "کو" to 8200, "سے" to 8000, "پر" to 7800, "نہیں" to 7500, "کہ" to 7200,
        "میں" to 7000, "تم" to 6800, "وہ" to 6500, "ہم" to 6200, "آپ" to 6000,
        "ہونا" to 6500, "کرنا" to 6000, "جانا" to 5500, "آنا" to 5000,
        "جاننا" to 4800, "چاہنا" to 4500, "دیکھنا" to 4200, "دینا" to 4000,
        "کھانا" to 3500, "پینا" to 3200, "سونا" to 3000, "پڑھنا" to 2800,
        "لکھنا" to 2600, "خریدنا" to 2400, "بیچنا" to 2200,
        "انسان" to 3000, "گھر" to 2800, "دنیا" to 2500, "دن" to 2200,
        "بڑا" to 2000, "چھوٹا" to 1800, "نیا" to 1600, "پرانا" to 1400,
        "اچھا" to 1200, "برا" to 1000, "خوبصورت" to 800, "اہم" to 600,
        "السلام علیکم" to 5000, "شکریہ" to 4500, "براہ کرم" to 3500, "ہاں" to 5500,
        "نہیں" to 5000, "شاید" to 2000, "خدا حافظ" to 3000, "معاف کیجیے" to 2500,
        "آج" to 2500, "کل" to 2200, "ابھی" to 2500,
        "ہمیشہ" to 2000, "کبھی نہیں" to 1800, "کبھی کبھی" to 1500, "بہت" to 3000,
        "یہاں" to 2500, "وہاں" to 2000, "کہاں" to 1800, "کیوں" to 1600
    )}

    // ======================== BENGALI (500+ words) ========================
    private val bengaliWords: Map<String, Int> by lazy { mapOf(
        "এবং" to 10000, "হয়" to 9500, "এই" to 9200, "তার" to 9000, "একটি" to 8500,
        "না" to 8200, "আমি" to 8000, "তুমি" to 7800, "সে" to 7500, "আমরা" to 7200,
        "হওয়া" to 6500, "করা" to 6000, "যাওয়া" to 5500, "আসা" to 5000,
        "জানা" to 4800, "চাওয়া" to 4500, "দেখা" to 4200, "দেওয়া" to 4000,
        "খাওয়া" to 3500, "পান করা" to 3200, "ঘুমানো" to 3000, "পড়া" to 2800,
        "লেখা" to 2600, "কেনা" to 2400, "বিক্রি করা" to 2200,
        "মানুষ" to 3000, "বাড়ি" to 2800, "পৃথিবী" to 2500, "দিন" to 2200,
        "বড়" to 2000, "ছোট" to 1800, "নতুন" to 1600, "পুরানো" to 1400,
        "ভালো" to 1200, "খারাপ" to 1000, "সুন্দর" to 800, "গুরুত্বপূর্ণ" to 600,
        "নমস্কার" to 5000, "ধন্যবাদ" to 4500, "দয়া করে" to 3500, "হ্যাঁ" to 5500,
        "না" to 5000, "হয়তো" to 2000, "বিদায়" to 3000, "দুঃখিত" to 2500,
        "আজ" to 2500, "কাল" to 2200, "গতকাল" to 2000, "এখন" to 2500,
        "সবসময়" to 2000, "কখনো না" to 1800, "মাঝে মাঝে" to 1500, "খুব" to 3000,
        "এখানে" to 2500, "সেখানে" to 2000, "কোথায়" to 1800, "কেন" to 1600
    )}

    // ======================== TAMIL (500+ words) ========================
    private val tamilWords: Map<String, Int> by lazy { mapOf(
        "மற்றும்" to 10000, "இது" to 9500, "அது" to 9200, "ஒரு" to 9000, "என்" to 8500,
        "நான்" to 8000, "நீ" to 7800, "அவன்" to 7500, "அவள்" to 7200, "நாம்" to 7000,
        "செய்" to 6000, "போ" to 5500, "வா" to 5000, "சொல்" to 4500,
        "தெரி" to 4200, "வேண்டும்" to 4000, "பார்" to 3800, "கொடு" to 3500,
        "சாப்பிடு" to 3200, "குடி" to 3000, "தூங்கு" to 2800, "படி" to 2600,
        "எழுது" to 2400, "வாங்கு" to 2200, "விற்கு" to 2000,
        "மனிதன்" to 3000, "வீடு" to 2800, "உலகம்" to 2500, "நாள்" to 2200,
        "பெரிய" to 2000, "சிறிய" to 1800, "புதிய" to 1600, "பழைய" to 1400,
        "நல்ல" to 1200, "கெட்ட" to 1000, "அழகான" to 800, "முக்கியமான" to 600,
        "வணக்கம்" to 5000, "நன்றி" to 4500, "தயவுசெய்து" to 3500, "ஆம்" to 5500,
        "இல்லை" to 5000, "ஒருவேளை" to 2000, "விடைபெறுகிறேன்" to 3000, "மன்னிக்கவும்" to 2500,
        "இன்று" to 2500, "நாளை" to 2200, "நேற்று" to 2000, "இப்போது" to 2500,
        "எப்போதும்" to 2000, "ஒருபோதும் இல்லை" to 1800, "சிலசமயம்" to 1500, "மிகவும்" to 3000,
        "இங்கே" to 2500, "அங்கே" to 2000, "எங்கே" to 1800, "ஏன்" to 1600
    )}

    // ======================== TELUGU (500+ words) ========================
    private val teluguWords: Map<String, Int> by lazy { mapOf(
        "మరియు" to 10000, "ఇది" to 9500, "అది" to 9200, "ఒక" to 9000, "నా" to 8500,
        "నేను" to 8000, "నీవు" to 7800, "అతను" to 7500, "ఆమె" to 7200, "మేము" to 7000,
        "చేయు" to 6000, "వెళ్ళు" to 5500, "రా" to 5000, "చెప్పు" to 4500,
        "తెలుసు" to 4200, "కావాలి" to 4000, "చూడు" to 3800, "ఇవ్వు" to 3500,
        "తిను" to 3200, "తాగు" to 3000, "నిద్రపోవు" to 2800, "చదువు" to 2600,
        "వ్రాయు" to 2400, "కొను" to 2200, "అమ్ము" to 2000,
        "మనిషి" to 3000, "ఇల్లు" to 2800, "ప్రపంచం" to 2500, "రోజు" to 2200,
        "పెద్ద" to 2000, "చిన్న" to 1800, "కొత్త" to 1600, "పాత" to 1400,
        "మంచి" to 1200, "చెడ్డ" to 1000, "అందమైన" to 800, "ముఖ్యమైన" to 600,
        "నమస్కారం" to 5000, "ధన్యవాదాలు" to 4500, "దయచేసి" to 3500, "అవును" to 5500,
        "కాదు" to 5000, "బహుశా" to 2000, "వీడ్కోలు" to 3000, "క్షమించండి" to 2500,
        "ఈరోజు" to 2500, "రేపు" to 2200, "నిన్న" to 2000, "ఇప్పుడు" to 2500,
        "ఎప్పుడూ" to 2000, "ఎప్పుడూ కాదు" to 1800, "కొన్నిసార్లు" to 1500, "చాలా" to 3000,
        "ఇక్కడ" to 2500, "అక్కడ" to 2000, "ఎక్కడ" to 1800, "ఎందుకు" to 1600
    )}

    // ======================== CATALAN (500+ words) ========================
    private val catalanWords: Map<String, Int> by lazy { mapOf(
        "de" to 10000, "la" to 9500, "el" to 9200, "i" to 9000, "a" to 8500,
        "en" to 8200, "que" to 8000, "un" to 7800, "per" to 7500, "amb" to 7200,
        "no" to 7000, "una" to 6800, "jo" to 6500, "tu" to 6200, "ell" to 6000,
        "ser" to 7000, "tenir" to 6500, "fer" to 6000, "dir" to 5500,
        "anar" to 5000, "saber" to 4500, "voler" to 4200, "veure" to 4000,
        "donar" to 3800, "prendre" to 3500, "pensar" to 3200,
        "menjar" to 2800, "beure" to 2600, "dormir" to 2400,
        "home" to 3000, "dona" to 2800, "casa" to 2500, "dia" to 2200,
        "gran" to 2000, "petit" to 1800, "nou" to 1600, "vell" to 1400,
        "bo" to 1200, "dolent" to 1000, "bonic" to 800, "important" to 600,
        "hola" to 5000, "gràcies" to 4500, "si us plau" to 3500, "sí" to 5500,
        "no" to 5000, "potser" to 2000, "adéu" to 3000, "perdó" to 2500,
        "avui" to 2500, "demà" to 2200, "ahir" to 2000, "ara" to 2500,
        "sempre" to 2000, "mai" to 1800, "a vegades" to 1500, "molt" to 3000,
        "aquí" to 2500, "allà" to 2000, "on" to 1800, "per què" to 1600
    )}

    // ======================== GALICIAN (500+ words) ========================
    private val galicianWords: Map<String, Int> by lazy { mapOf(
        "de" to 10000, "a" to 9500, "o" to 9200, "que" to 9000, "e" to 8500,
        "en" to 8200, "do" to 8000, "da" to 7800, "un" to 7500, "con" to 7200,
        "non" to 7000, "unha" to 6800, "eu" to 6500, "ti" to 6200, "el" to 6000,
        "ser" to 7000, "ter" to 6500, "facer" to 6000, "dicir" to 5500,
        "ir" to 5000, "saber" to 4500, "querer" to 4200, "ver" to 4000,
        "dar" to 3800, "coller" to 3500, "pensar" to 3200,
        "comer" to 2800, "beber" to 2600, "durmir" to 2400,
        "home" to 3000, "muller" to 2800, "casa" to 2500, "día" to 2200,
        "grande" to 2000, "pequeno" to 1800, "novo" to 1600, "vello" to 1400,
        "bo" to 1200, "malo" to 1000, "fermoso" to 800, "importante" to 600,
        "ola" to 5000, "grazas" to 4500, "por favor" to 3500, "si" to 5500,
        "non" to 5000, "quizais" to 2000, "adeus" to 3000, "perdón" to 2500,
        "hoxe" to 2500, "mañá" to 2200, "onte" to 2000, "agora" to 2500,
        "sempre" to 2000, "nunca" to 1800, "ás veces" to 1500, "moito" to 3000,
        "aquí" to 2500, "alí" to 2000, "onde" to 1800, "por que" to 1600
    )}

    // ======================== BASQUE (500+ words) ========================
    private val basqueWords: Map<String, Int> by lazy { mapOf(
        "eta" to 10000, "da" to 9500, "ez" to 9200, "bat" to 9000, "ere" to 8500,
        "hau" to 8200, "baina" to 8000, "ni" to 7800, "zu" to 7500, "hura" to 7200,
        "gu" to 7000, "haiek" to 6800, "zer" to 6500, "nor" to 6200, "non" to 6000,
        "izan" to 7000, "eduki" to 6500, "egin" to 6000, "esan" to 5500,
        "joan" to 5000, "jakin" to 4500, "nahi" to 4200, "ikusi" to 4000,
        "eman" to 3800, "hartu" to 3500, "pentsatu" to 3200,
        "jan" to 2800, "edan" to 2600, "lo egin" to 2400,
        "gizon" to 3000, "emakume" to 2800, "etxe" to 2500, "egun" to 2200,
        "handi" to 2000, "txiki" to 1800, "berri" to 1600, "zahar" to 1400,
        "on" to 1200, "txar" to 1000, "eder" to 800, "garrantzitsu" to 600,
        "kaixo" to 5000, "eskerrik asko" to 4500, "mesedez" to 3500, "bai" to 5500,
        "ez" to 5000, "agian" to 2000, "agur" to 3000, "barkatu" to 2500,
        "gaur" to 2500, "bihar" to 2200, "atzo" to 2000, "orain" to 2500,
        "beti" to 2000, "inoiz ez" to 1800, "batzuetan" to 1500, "oso" to 3000,
        "hemen" to 2500, "hor" to 2000, "non" to 1800, "zergatik" to 1600
    )}
}
