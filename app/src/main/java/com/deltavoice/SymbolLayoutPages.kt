package com.deltavoice

/**
 * Two-page symbol layout for number/symbol mode (main IME + mini keyboards).
 * Page 1 and page 2 have disjoint character sets (27 keys each in three rows).
 */
object SymbolLayoutPages {

    /** First symbol row: 10 keys (maps to QWERTY row slot). */
    val PAGE_1_ROW_QWERTY = listOf("+", "×", "÷", "=", "/", "_", "<", ">", "[", "]")

    val PAGE_1_ROW_ASDF = listOf("!", "@", "#", "£", "%", "^", "&", "*", "(", ")")

    /** Third row: 7 keys between page toggle and backspace. */
    val PAGE_1_ROW_ZXCV = listOf("-", "'", "\"", ":", ";", ",", "?")

    val PAGE_2_ROW_QWERTY = listOf("`", "~", "\\", "|", "{", "}", "€", "$", "¥", "₩")

    val PAGE_2_ROW_ASDF = listOf("°", "•", "○", "●", "□", "■", "♠", "♡", "♢", "♣")

    val PAGE_2_ROW_ZXCV = listOf("☆", "▪", "¤", "«", "»", "¡", "¿")

    fun pageToggleLabel(isSecondPage: Boolean): String = if (isSecondPage) "2/2" else "1/2"

    fun rowsForPage(secondPage: Boolean): Triple<List<String>, List<String>, List<String>> =
        if (secondPage) {
            Triple(PAGE_2_ROW_QWERTY, PAGE_2_ROW_ASDF, PAGE_2_ROW_ZXCV)
        } else {
            Triple(PAGE_1_ROW_QWERTY, PAGE_1_ROW_ASDF, PAGE_1_ROW_ZXCV)
        }
}
