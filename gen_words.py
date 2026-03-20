#!/usr/bin/env python3
"""Generate PredictiveWordList.kt from word_data.txt."""
import os, sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_FILE = os.path.join(SCRIPT_DIR, 'word_data.txt')
OUTPUT = os.path.join(SCRIPT_DIR, 'app', 'src', 'main', 'java', 'com', 'deltavoice', 'PredictiveWordList.kt')

LANG_CONFIG = [
    ("en", "englishWords"), ("es", "spanishWords"), ("fr", "frenchWords"),
    ("de", "germanWords"), ("it", "italianWords"), ("pt", "portugueseWords"),
    ("ru", "russianWords"), ("ar", "arabicWords"), ("hi", "hindiWords"),
    ("ja", "japaneseWords"), ("ko", "koreanWords"), ("zh", "chinesePinyinWords"),
    ("tr", "turkishWords"), ("nl", "dutchWords"), ("pl", "polishWords"),
    ("cs", "czechWords"), ("sv", "swedishWords"), ("no", "norwegianWords"),
    ("da", "danishWords"), ("fi", "finnishWords"), ("hu", "hungarianWords"),
    ("ro", "romanianWords"), ("el", "greekWords"), ("uk", "ukrainianWords"),
    ("bg", "bulgarianWords"), ("hr", "croatianWords"), ("sr", "serbianWords"),
    ("sk", "slovakWords"), ("sl", "slovenianWords"), ("et", "estonianWords"),
    ("lv", "latvianWords"), ("lt", "lithuanianWords"), ("vi", "vietnameseWords"),
    ("th", "thaiWords"), ("id", "indonesianWords"), ("ms", "malayWords"),
    ("tl", "filipinoWords"), ("sw", "swahiliWords"), ("he", "hebrewWords"),
    ("fa", "persianWords"), ("ur", "urduWords"), ("bn", "bengaliWords"),
    ("ta", "tamilWords"), ("te", "teluguWords"), ("ca", "catalanWords"),
    ("gl", "galicianWords"), ("eu", "basqueWords"),
]

def parse_data(path):
    langs = {}
    cur = None
    with open(path, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line or line == '__END__':
                continue
            if line.startswith('[') and line.endswith(']'):
                cur = line[1:-1]
                langs[cur] = []
            elif cur:
                langs[cur].extend(line.split())
    # deduplicate while preserving order
    for k in langs:
        seen = set()
        deduped = []
        for w in langs[k]:
            if w not in seen:
                seen.add(w)
                deduped.append(w)
        langs[k] = deduped
    return langs

def gen(data):
    L = []
    L.append('package com.deltavoice')
    L.append('')
    L.append('import kotlin.math.pow')
    L.append('')
    L.append('object PredictiveWordList {')
    L.append('')
    L.append('    private fun wordsFromRanked(vararg words: String): Map<String, Int> {')
    L.append('        val n = words.size')
    L.append('        if (n == 0) return emptyMap()')
    L.append('        return words.mapIndexed { i, word ->')
    L.append('            val freq = maxOf(1, (10000.0 * (1.0 - i.toDouble() / n).pow(1.5)).toInt())')
    L.append('            word to freq')
    L.append('        }.toMap()')
    L.append('    }')
    L.append('')
    L.append('    private val dictionaries by lazy {')
    L.append('        mapOf(')
    for i, (code, vn) in enumerate(LANG_CONFIG):
        c = ',' if i < len(LANG_CONFIG) - 1 else ''
        L.append(f'            "{code}" to {vn}{c}')
    L.append('        )')
    L.append('    }')
    L.append('')
    L.append('    fun getWordsForLanguage(languageCode: String): Set<String> =')
    L.append('        (dictionaries[languageCode] ?: dictionaries["en"]!!).keys')
    L.append('')
    L.append('    fun getWordsWithFrequency(languageCode: String): Map<String, Int> =')
    L.append('        dictionaries[languageCode] ?: dictionaries["en"]!!')
    L.append('')
    L.append('    fun getSupportedLanguages(): Set<String> = dictionaries.keys')
    L.append('')
    for code, vn in LANG_CONFIG:
        words = data.get(code, [])
        if not words:
            L.append(f'    private val {vn}: Map<String, Int> by lazy {{ emptyMap() }}')
        else:
            L.append(f'    private val {vn}: Map<String, Int> by lazy {{ wordsFromRanked(')
            for j in range(0, len(words), 8):
                chunk = words[j:j+8]
                ws = ', '.join(f'"{w}"' for w in chunk)
                comma = ',' if j + 8 < len(words) else ''
                L.append(f'        {ws}{comma}')
            L.append('    )}')
        L.append('')
    L.append('}')
    return '\n'.join(L)

def main():
    data = parse_data(DATA_FILE)
    kt = gen(data)
    os.makedirs(os.path.dirname(OUTPUT), exist_ok=True)
    with open(OUTPUT, 'w', encoding='utf-8') as f:
        f.write(kt)
    total = 0
    for code, vn in LANG_CONFIG:
        n = len(data.get(code, []))
        total += n
        print(f'  {code}: {n} words')
    print(f'\nTotal: {total} words across {len([c for c,_ in LANG_CONFIG if c in data])} languages')
    print(f'Written to {OUTPUT}')

if __name__ == '__main__':
    main()
