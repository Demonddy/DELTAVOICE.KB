#!/usr/bin/env python3
"""Generates PredictiveWordList.kt with comprehensive multi-language dictionaries."""
import math
import os
import glob

OUTPUT = os.path.join(
    r"c:\Users\rrr\Desktop\keyboard",
    "app", "src", "main", "java", "com", "deltavoice", "PredictiveWordList.kt"
)

WORDDATA_DIR = os.path.join(r"c:\Users\rrr\Desktop\keyboard", "worddata")

LANG_META = {
    "en": "english", "es": "spanish", "fr": "french", "de": "german",
    "it": "italian", "pt": "portuguese", "ru": "russian", "ar": "arabic",
    "hi": "hindi", "ja": "japanese", "ko": "korean", "zh": "chinesePinyin",
    "tr": "turkish", "nl": "dutch", "pl": "polish", "cs": "czech",
    "sv": "swedish", "no": "norwegian", "da": "danish", "fi": "finnish",
    "hu": "hungarian", "ro": "romanian", "el": "greek", "uk": "ukrainian",
    "bg": "bulgarian", "hr": "croatian", "sr": "serbian", "sk": "slovak",
    "sl": "slovenian", "et": "estonian", "lv": "latvian", "lt": "lithuanian",
    "vi": "vietnamese", "th": "thai", "id": "indonesian", "ms": "malay",
    "tl": "filipino", "sw": "swahili", "he": "hebrew", "fa": "persian",
    "ur": "urdu", "bn": "bengali", "ta": "tamil", "te": "telugu",
    "ca": "catalan", "gl": "galician", "eu": "basque",
}

LARGE = {"en","es","fr","de","it","pt","ru","ar","hi","ja","ko","zh"}

LANG_ORDER = [
    "en","es","fr","de","it","pt","ru","ar","hi","ja","ko","zh",
    "tr","nl","pl","cs","sv","no","da","fi","hu","ro","el","uk",
    "bg","hr","sr","sk","sl","et","lv","lt","vi","th","id","ms",
    "tl","sw","he","fa","ur","bn","ta","te","ca","gl","eu",
]

def freq_for_rank(rank, is_large):
    if is_large:
        return max(1, int(10000 * math.exp(-0.004 * (rank - 1))))
    else:
        return max(1, int(10000 * math.exp(-0.012 * (rank - 1))))

def load_words(lang_code):
    path = os.path.join(WORDDATA_DIR, f"{lang_code}.txt")
    if not os.path.exists(path):
        return []
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    words = content.split()
    seen = set()
    unique = []
    for w in words:
        if w and w not in seen:
            seen.add(w)
            unique.append(w)
    return unique

def generate():
    available = []
    for code in LANG_ORDER:
        if code in LANG_META:
            words = load_words(code)
            if words:
                available.append((code, words))

    print(f"Found {len(available)} languages with word data:")
    for code, words in available:
        print(f"  {code}: {len(words)} unique words")

    with open(OUTPUT, "w", encoding="utf-8") as f:
        f.write("package com.deltavoice\n\n")
        f.write("object PredictiveWordList {\n\n")

        f.write("    private val dictionaries by lazy {\n")
        f.write("        mapOf(\n")
        for i, (code, _) in enumerate(available):
            comma = "," if i < len(available) - 1 else ""
            name = LANG_META[code]
            f.write(f'            "{code}" to {name}Words{comma}\n')
        f.write("        )\n")
        f.write("    }\n\n")

        f.write("    fun getWordsForLanguage(languageCode: String): Set<String> =\n")
        f.write('        (dictionaries[languageCode] ?: dictionaries["en"]!!).keys\n\n')
        f.write("    fun getWordsWithFrequency(languageCode: String): Map<String, Int> =\n")
        f.write('        dictionaries[languageCode] ?: dictionaries["en"]!!\n\n')
        f.write("    fun getSupportedLanguages(): Set<String> = dictionaries.keys\n\n")

        for code, words in available:
            is_large = code in LARGE
            name = LANG_META[code]
            f.write(f"    private val {name}Words: Map<String, Int> by lazy {{ mapOf(\n")
            for j, w in enumerate(words):
                fr = freq_for_rank(j + 1, is_large)
                comma = "," if j < len(words) - 1 else ""
                escaped = w.replace("\\", "\\\\").replace('"', '\\"')
                f.write(f'        "{escaped}" to {fr}{comma}\n')
            f.write("    )}\n\n")

        f.write("}\n")

    total_words = sum(len(w) for _, w in available)
    print(f"\nGenerated {OUTPUT}")
    print(f"Total: {len(available)} languages, {total_words} words")

if __name__ == "__main__":
    generate()
