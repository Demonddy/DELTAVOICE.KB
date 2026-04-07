package com.deltavoice

import java.util.Locale

/**
 * Maps [AppLocaleOption.tag] (BCP-47) to Android `res/values-<qualifier>` folder name
 * (the qualifier segment only, without the `values-` prefix).
 *
 * Used to align [AppLocaleCatalog] picker tags with resource bundles under `res/`.
 */
object AppLocaleResourceQualifiers {

    fun valuesFolderSuffixForTag(tag: String): String? {
        if (tag.isEmpty()) return null
        return when (tag) {
            "zh-CN" -> "zh-rCN"
            "zh-HK" -> "zh-rHK"
            "zh-TW" -> "zh-rTW"
            "pt-BR" -> "pt-rBR"
            "pt-PT" -> "pt-rPT"
            else -> tag.lowercase(Locale.ROOT)
        }
    }
}
