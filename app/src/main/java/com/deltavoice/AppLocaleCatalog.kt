package com.deltavoice

import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * App language options (native label + English subtitle) and BCP-47 tags for [LocaleListCompat].
 * Empty [tag] means follow the device locale (system default).
 */
data class AppLocaleOption(
    val tag: String,
    val nativeLabel: String,
    val englishLabel: String
)

object AppLocaleCatalog {

    val options: List<AppLocaleOption> = listOf(
        AppLocaleOption("", "English", "(device's language)"),
        AppLocaleOption("ar", "العربية", "Arabic"),
        AppLocaleOption("af", "Afrikaans", "Afrikaans"),
        AppLocaleOption("sq", "Shqip", "Albanian"),
        AppLocaleOption("am", "አማርኛ", "Amharic"),
        AppLocaleOption("az", "Azərbaycan dili", "Azerbaijani"),
        AppLocaleOption("bn", "বাংলা", "Bangla"),
        AppLocaleOption("bg", "Български", "Bulgarian"),
        AppLocaleOption("ca", "Català", "Catalan"),
        AppLocaleOption("zh-CN", "简体中文", "Simplified Chinese"),
        AppLocaleOption("zh-HK", "繁體中文 (香港)", "Traditional Chinese (Hong Kong)"),
        AppLocaleOption("zh-TW", "繁體中文 (台灣)", "Traditional Chinese (Taiwan)"),
        AppLocaleOption("hr", "Hrvatski", "Croatian"),
        AppLocaleOption("cs", "Čeština", "Czech"),
        AppLocaleOption("da", "Dansk", "Danish"),
        AppLocaleOption("nl", "Nederlands", "Dutch"),
        AppLocaleOption("et", "Eesti", "Estonian"),
        AppLocaleOption("fil", "Filipino", "Filipino"),
        AppLocaleOption("fi", "Suomi", "Finnish"),
        AppLocaleOption("fr", "Français", "French"),
        AppLocaleOption("de", "Deutsch", "German"),
        AppLocaleOption("el", "Ελληνικά", "Greek"),
        AppLocaleOption("gu", "ગુજરાતી", "Gujarati"),
        AppLocaleOption("ha", "Hausa", "Hausa"),
        AppLocaleOption("he", "עברית", "Hebrew"),
        AppLocaleOption("hi", "हिन्दी", "Hindi"),
        AppLocaleOption("hu", "Magyar", "Hungarian"),
        AppLocaleOption("id", "Bahasa Indonesia", "Indonesian"),
        AppLocaleOption("ga", "Gaeilge", "Irish"),
        AppLocaleOption("it", "Italiano", "Italian"),
        AppLocaleOption("ja", "日本語", "Japanese"),
        AppLocaleOption("kn", "ಕನ್ನಡ", "Kannada"),
        AppLocaleOption("kk", "Қазақ тілі", "Kazakh"),
        AppLocaleOption("ko", "한국어", "Korean"),
        AppLocaleOption("lo", "ລາວ", "Lao"),
        AppLocaleOption("lv", "Latviešu", "Latvian"),
        AppLocaleOption("lt", "Lietuvių", "Lithuanian"),
        AppLocaleOption("mk", "Македонски", "Macedonian"),
        AppLocaleOption("ms", "Melayu", "Malay"),
        AppLocaleOption("ml", "മലയാളം", "Malayalam"),
        AppLocaleOption("mr", "मराठी", "Marathi"),
        AppLocaleOption("nb", "Norsk bokmål", "Norwegian Bokmål"),
        AppLocaleOption("om", "Oromoo", "Oromo"),
        AppLocaleOption("fa", "فارسی", "Persian"),
        AppLocaleOption("pl", "Polski", "Polish"),
        AppLocaleOption("pt-BR", "Português (Brasil)", "Portuguese (Brazil)"),
        AppLocaleOption("pt-PT", "Português (Portugal)", "Portuguese (Portugal)"),
        AppLocaleOption("pa", "ਪੰਜਾਬੀ", "Punjabi"),
        AppLocaleOption("ro", "Română", "Romanian"),
        AppLocaleOption("ru", "Русский", "Russian"),
        AppLocaleOption("sr", "Српски", "Serbian"),
        AppLocaleOption("sk", "Slovenčina", "Slovak"),
        AppLocaleOption("sl", "Slovenščina", "Slovenian"),
        AppLocaleOption("es", "Español", "Spanish"),
        AppLocaleOption("sw", "Kiswahili", "Swahili"),
        AppLocaleOption("sv", "Svenska", "Swedish"),
        AppLocaleOption("ta", "தமிழ்", "Tamil"),
        AppLocaleOption("te", "తెలుగు", "Telugu"),
        AppLocaleOption("th", "ไทย", "Thai"),
        AppLocaleOption("tr", "Türkçe", "Turkish"),
        AppLocaleOption("uk", "Українська", "Ukrainian"),
        AppLocaleOption("ur", "اردو", "Urdu"),
        AppLocaleOption("uz", "O'zbek", "Uzbek"),
        AppLocaleOption("vi", "Tiếng Việt", "Vietnamese")
    )

    fun findSelectedIndex(locales: LocaleListCompat): Int {
        if (locales.isEmpty) return 0
        val tag = locales[0]?.toLanguageTag() ?: return 0
        if (tag.isEmpty()) return 0

        options.forEachIndexed { i, opt ->
            if (opt.tag.isEmpty()) return@forEachIndexed
            if (tag.equals(opt.tag, ignoreCase = true)) return i
        }

        val selected = try {
            Locale.forLanguageTag(tag)
        } catch (_: Exception) {
            return 0
        }

        options.forEachIndexed { i, opt ->
            if (opt.tag.isEmpty()) return@forEachIndexed
            val candidate = Locale.forLanguageTag(opt.tag)
            if (candidate.language.isNotEmpty() && candidate.language == selected.language) {
                if (opt.tag.contains("-") && tag.contains("-")) {
                    if (tag.equals(opt.tag, ignoreCase = true)) return i
                } else if (!opt.tag.contains("-") && !tag.contains("-")) {
                    return i
                }
            }
        }

        return 0
    }
}
