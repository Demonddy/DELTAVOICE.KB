package com.deltavoice

/**
 * Multi-language predictive text with optimized dictionaries.
 * Supports: en, es, fr, de, it, pt, ru, ar, hi, ja, ko, zh (pinyin).
 * Sorted for efficient prefix matching; language-aware predictions.
 */
object PredictiveWordList {
    
    private val dictionaries = mapOf(
        "en" to englishWords,
        "es" to spanishWords,
        "fr" to frenchWords,
        "de" to germanWords,
        "it" to italianWords,
        "pt" to portugueseWords,
        "ru" to russianWords,
        "ar" to arabicWords,
        "hi" to hindiWords,
        "ja" to japaneseWords,
        "ko" to koreanWords,
        "zh" to chinesePinyinWords
    )
    
    /**
     * Get predictions for the given prefix in the specified language.
     * @param prefix User's partial input
     * @param limit Max suggestions to return
     * @param languageCode e.g. "en", "es", "fr"
     */
    fun getPredictions(prefix: String, limit: Int = 5, languageCode: String = "en"): List<String> {
        if (prefix.isBlank()) return emptyList()
        val words = dictionaries[languageCode] ?: dictionaries["en"]!!
        val normalized = normalizeForMatch(prefix, languageCode)
        if (normalized.isBlank()) return emptyList()
        return words.asSequence()
            .filter { normalizeForMatch(it, languageCode).startsWith(normalized) && normalizeForMatch(it, languageCode) != normalized }
            .take(limit)
            .toList()
    }
    
    /**
     * Get autocorrect suggestions for misspelled word.
     */
    fun getCorrections(word: String, limit: Int = 5, languageCode: String = "en"): List<String> {
        if (word.length < 2) return emptyList()
        val words = dictionaries[languageCode] ?: dictionaries["en"]!!
        val normalized = normalizeForMatch(word, languageCode)
        if (words.contains(normalized)) return emptyList()
        return words.asSequence()
            .filter { levenshteinDistance(normalized, normalizeForMatch(it, languageCode)) <= 2 }
            .sortedBy { levenshteinDistance(normalized, normalizeForMatch(it, languageCode)) }
            .take(limit)
            .toList()
    }
    
    private fun normalizeForMatch(s: String, lang: String): String {
        return when (lang) {
            "ar", "hi" -> s  // No case fold for these scripts
            else -> s.lowercase()
        }
    }
    
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        val dp = Array(len1 + 1) { IntArray(len2 + 1) }
        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j
        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[len1][len2]
    }
    
    // ==================== ENGLISH (expanded) ====================
    private val englishWords = setOf(
        "the", "be", "to", "of", "and", "a", "in", "that", "have", "i", "it", "for", "not", "on", "with",
        "he", "as", "you", "do", "at", "this", "but", "his", "by", "from", "they", "we", "say", "her", "she",
        "or", "an", "will", "my", "one", "all", "would", "there", "their", "what", "so", "up", "out", "if",
        "about", "who", "get", "which", "go", "me", "when", "make", "can", "like", "time", "no", "just",
        "him", "know", "take", "people", "into", "year", "your", "good", "some", "could", "them", "see",
        "other", "than", "then", "now", "look", "only", "come", "its", "over", "think", "also", "back",
        "after", "use", "two", "how", "our", "work", "first", "well", "way", "even", "new", "want",
        "because", "any", "these", "give", "day", "most", "us", "is", "are", "was", "were", "been", "being",
        "has", "had", "does", "did", "doing", "could", "should", "may", "might", "must", "shall", "need",
        "hello", "world", "love", "life", "friend", "family", "home", "school", "today", "tomorrow",
        "yesterday", "here", "there", "where", "why", "how", "what", "which", "who", "whom",
        "something", "nothing", "everything", "someone", "anyone", "everyone", "please", "thank",
        "thanks", "sorry", "yes", "ok", "okay", "really", "actually", "probably", "maybe", "perhaps",
        "right", "wrong", "good", "bad", "great", "nice", "fine", "better", "best", "more", "many",
        "much", "some", "any", "all", "both", "each", "every", "another", "other", "different",
        "important", "interesting", "beautiful", "happy", "sad", "sure", "ready", "possible",
        "message", "email", "phone", "call", "text", "send", "write", "read", "book", "letter",
        "word", "sentence", "question", "answer", "problem", "solution", "idea", "thing",
        "help", "need", "want", "like", "love", "try", "start", "stop", "continue", "create",
        "make", "build", "change", "improve", "fix", "add", "get", "take", "give", "show",
        "tell", "ask", "say", "speak", "talk", "listen", "see", "look", "watch", "hear", "feel",
        "think", "know", "understand", "remember", "forget", "learn", "study", "work", "play",
        "live", "come", "go", "leave", "arrive", "return", "stay", "move", "run", "walk",
        "eat", "drink", "cook", "buy", "sell", "pay", "find", "keep", "hold", "carry", "bring",
        "please", "thanks", "welcome", "excuse", "sorry", "congratulations", "wonderful",
        "amazing", "awesome", "excellent", "perfect", "fantastic", "incredible", "great",
        "business", "meeting", "project", "team", "company", "office", "schedule", "appointment",
        "information", "document", "file", "folder", "download", "upload", "share", "copy",
        "delete", "save", "open", "close", "search", "find", "replace", "edit", "update",
        "password", "account", "login", "logout", "register", "subscribe", "unsubscribe",
        "address", "number", "date", "time", "place", "location", "direction", "map",
        "weather", "temperature", "rain", "sun", "cloud", "wind", "snow", "hot", "cold",
        "morning", "afternoon", "evening", "night", "week", "month", "year", "today",
        "tomorrow", "yesterday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday",
        "Saturday", "Sunday", "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
        "language", "translate", "dictionary", "grammar", "vocabulary", "pronunciation",
        "available", "languages", "keyboard", "settings", "correct", "suggestion"
    )
    
    // ==================== SPANISH ====================
    private val spanishWords = setOf(
        "el", "la", "de", "que", "y", "a", "en", "un", "ser", "se", "no", "haber", "por", "su", "para",
        "con", "como", "estar", "tener", "le", "lo", "todo", "pero", "más", "hacer", "o", "poder",
        "decir", "este", "ir", "otro", "ese", "si", "me", "ya", "ver", "porque", "dar", "cuando",
        "él", "muy", "sin", "vez", "mucho", "saber", "qué", "sobre", "mi", "alguno", "mismo", "yo",
        "también", "hasta", "año", "dos", "querer", "entre", "así", "primero", "desde", "grande",
        "eso", "ni", "nosotros", "llegar", "pasar", "tiempo", "ella", "sí", "día", "uno", "bien",
        "poco", "deber", "entonces", "poner", "cosa", "tanto", "hombre", "parecer", "nuestro",
        "tan", "donde", "ahora", "parte", "después", "vida", "quedar", "siempre", "creer", "hablar",
        "llevar", "dejar", "nada", "cada", "seguir", "menos", "nuevo", "encontrar", "algo",
        "solo", "decir", "salir", "volver", "tomar", "conocer", "vivir", "sentir", "tratar",
        "mirar", "contar", "empezar", "esperar", "buscar", "existir", "entrar", "trabajar",
        "escribir", "perder", "producir", "ocurrir", "entender", "pedir", "recibir", "recordar",
        "terminar", "permitir", "aparecer", "conseguir", "comenzar", "servir", "sacar",
        "necesitar", "mantener", "resultar", "leer", "caer", "cambiar", "presentar", "crear",
        "abrir", "considerar", "oír", "acabar", "guardar", "comprar", "vender", "pagar",
        "hola", "gracias", "por favor", "adiós", "buenos", "días", "buenas", "noches",
        "amor", "familia", "amigo", "casa", "trabajo", "escuela", "agua", "comida", "dinero"
    )
    
    // ==================== FRENCH ====================
    private val frenchWords = setOf(
        "le", "la", "de", "et", "les", "des", "un", "une", "être", "avoir", "que", "qui", "ce",
        "dans", "en", "du", "pas", "plus", "pour", "sur", "avec", "tout", "faire", "mais", "son",
        "même", "nous", "vous", "ils", "elle", "on", "comme", "bien", "sans", "donc", "alors",
        "aussi", "très", "encore", "toujours", "jamais", "ici", "là", "où", "quand", "comment",
        "pourquoi", "parce", "avant", "après", "pendant", "entre", "sous", "vers", "chez",
        "autre", "chaque", "quelque", "certain", "plusieurs", "tout", "tous", "toute", "toutes",
        "premier", "dernier", "grand", "petit", "bon", "mauvais", "nouveau", "vieux", "jeune",
        "long", "court", "haut", "bas", "beau", "belle", "joli", "content", "triste", "heureux",
        "aller", "venir", "partir", "arriver", "rester", "entrer", "sortir", "monter", "descendre",
        "voir", "regarder", "écouter", "entendre", "sentir", "toucher", "savoir", "connaître",
        "penser", "croire", "vouloir", "pouvoir", "devoir", "falloir", "dire", "parler",
        "demander", "répondre", "écrire", "lire", "apprendre", "comprendre", "oublier",
        "bonjour", "merci", "s'il vous plaît", "au revoir", "oui", "non", "peut-être",
        "amour", "famille", "ami", "maison", "travail", "école", "eau", "nourriture", "argent"
    )
    
    // ==================== GERMAN ====================
    private val germanWords = setOf(
        "der", "die", "und", "in", "den", "von", "zu", "das", "mit", "sich", "des", "auf", "für",
        "ist", "im", "dem", "nicht", "ein", "eine", "als", "auch", "es", "sind", "war", "werden",
        "hat", "haben", "nach", "bei", "aus", "oder", "bis", "zum", "zur", "nur", "noch",
        "sehr", "mehr", "durch", "wenn", "aber", "dass", "sie", "er", "wir", "was", "wie",
        "alle", "kann", "muss", "soll", "will", "dürfen", "möchte", "können", "müssen",
        "gehen", "kommen", "machen", "sehen", "lassen", "stehen", "finden", "bleiben",
        "liegen", "halten", "bringen", "denken", "nehmen", "tun", "geben", "sprechen",
        "wissen", "glauben", "hoffen", "wollen", "brauchen", "vergessen", "verstehen",
        "guten", "tag", "morgen", "abend", "nacht", "danke", "bitte", "tschüss", "ja", "nein",
        "liebe", "familie", "freund", "haus", "arbeit", "schule", "wasser", "essen", "geld"
    )
    
    // ==================== ITALIAN ====================
    private val italianWords = setOf(
        "il", "la", "di", "che", "e", "a", "in", "un", "per", "una", "sono", "mi", "si", "ho",
        "ma", "ha", "come", "se", "lo", "le", "del", "da", "dal", "dalla", "dei", "delle",
        "al", "alla", "nel", "nella", "sul", "sulla", "con", "su", "tra", "fra", "questo",
        "questa", "quello", "quella", "questi", "queste", "quegli", "quelle", "altro",
        "altra", "altri", "altre", "tutto", "tutta", "tutti", "tutte", "molto", "molti",
        "poco", "pochi", "tanto", "tanti", "qualche", "alcuni", "alcune", "ogni", "nessun",
        "essere", "avere", "fare", "dire", "andare", "venire", "potere", "volere", "dovere",
        "vedere", "sapere", "conoscere", "pensare", "credere", "parlare", "capire",
        "buongiorno", "grazie", "prego", "arrivederci", "sì", "no", "forse", "ciao",
        "amore", "famiglia", "amico", "casa", "lavoro", "scuola", "acqua", "cibo", "soldi"
    )
    
    // ==================== PORTUGUESE ====================
    private val portugueseWords = setOf(
        "o", "a", "de", "que", "e", "do", "da", "em", "um", "para", "com", "não", "uma", "os",
        "no", "se", "na", "por", "mais", "as", "dos", "como", "mas", "ao", "ele", "ela", "ou",
        "ser", "quando", "muito", "há", "nos", "já", "está", "eu", "também", "só", "pelo",
        "pela", "até", "isso", "ela", "entre", "era", "depois", "sem", "mesmo", "aos", "ter",
        "seus", "quem", "nas", "me", "esse", "eles", "estão", "você", "tinha", "foram",
        "essa", "num", "nem", "suas", "meu", "minha", "nossa", "nosso", "dela", "dele",
        "fazer", "falar", "saber", "querer", "poder", "ver", "ir", "vir", "dar", "ter",
        "ficar", "deixar", "encontrar", "passar", "conhecer", "levar", "trazer", "achar",
        "olá", "obrigado", "por favor", "adeus", "sim", "não", "talvez", "oi",
        "amor", "família", "amigo", "casa", "trabalho", "escola", "água", "comida", "dinheiro"
    )
    
    // ==================== RUSSIAN ====================
    private val russianWords = setOf(
        "и", "в", "не", "на", "я", "что", "он", "с", "как", "а", "по", "это", "она", "но", "они",
        "мы", "из", "у", "к", "до", "вы", "за", "же", "бы", "то", "его", "ей", "её", "если",
        "уже", "или", "нет", "да", "там", "здесь", "очень", "только", "ещё", "когда",
        "все", "можно", "надо", "нужно", "должен", "хочу", "могу", "знаю", "вижу", "думаю",
        "сказать", "говорить", "делать", "знать", "хотеть", "мочь", "видеть", "идти",
        "прийти", "дать", "взять", "жить", "работать", "любить", "думать", "смотреть",
        "привет", "спасибо", "пожалуйста", "до свидания", "да", "нет", "может быть",
        "любовь", "семья", "друг", "дом", "работа", "школа", "вода", "еда", "деньги"
    )
    
    // ==================== ARABIC ====================
    private val arabicWords = setOf(
        "في", "من", "إلى", "على", "أن", "هذا", "هذه", "الذي", "التي", "كان", "هو", "هي",
        "ما", "لا", "نعم", "مع", "عن", "بين", "بعد", "قبل", "عند", "حتى", "أيضاً", "كذلك",
        "أنا", "نحن", "هم", "أنت", "أنتم", "كل", "بعض", "كثير", "قليل", "جديد", "قديم",
        "كبير", "صغير", "جيد", "سيء", "جميل", "قبيح", "سعيد", "حزين", "مهم", "ضروري",
        "يعمل", "يذهب", "يأتي", "يعرف", "يريد", "يستطيع", "يجب", "يقول", "يتكلم",
        "مرحبا", "شكرا", "من فضلك", "مع السلامة", "نعم", "لا", "ربما",
        "حب", "عائلة", "صديق", "بيت", "عمل", "مدرسة", "ماء", "طعام", "مال"
    )
    
    // ==================== HINDI ====================
    private val hindiWords = setOf(
        "और", "है", "की", "में", "यह", "हैं", "को", "से", "पर", "कि", "नहीं", "या", "एक",
        "हो", "था", "कर", "होता", "किया", "होना", "होगा", "करने", "करता", "करते",
        "हूं", "होती", "करती", "करें", "होने", "किया", "करेंगे", "करोगे", "करूंगा",
        "मैं", "तुम", "वह", "हम", "आप", "ये", "वे", "इस", "उस", "कौन", "क्या", "कब",
        "कहाँ", "कैसे", "क्यों", "कितना", "कौन सा", "सब", "कुछ", "बहुत", "थोड़ा",
        "नमस्ते", "धन्यवाद", "कृपया", "अलविदा", "हाँ", "नहीं", "शायद",
        "प्यार", "परिवार", "दोस्त", "घर", "काम", "स्कूल", "पानी", "खाना", "पैसा"
    )
    
    // ==================== JAPANESE (romaji + hiragana - main kb is QWERTY, mini kb is hiragana) ====================
    private val japaneseWords = setOf(
        "watashi", "anata", "kare", "kanojo", "sore", "are", "kore", "dore", "nani",
        "dare", "itsu", "doko", "naze", "dou", "ikura", "hai", "iie", "tabun",
        "arigatou", "sumimasen", "ohayou", "konnichiwa", "konbanwa", "sayounara",
        "desu", "da", "masu", "suru", "kuru", "iku", "aru", "iru", "miru", "iu", "omou", "wakaru", "dekiru",
        "ai", "kazoku", "tomodachi", "ie", "shigoto", "gakkou", "mizu", "tabemono", "okane",
        "わたし", "あなた", "これ", "それ", "あれ", "なに", "どこ", "いつ", "はい", "いいえ",
        "ありがとう", "すみません", "こんにちは", "さようなら", "です", "する", "くる", "いく"
    )
    
    // ==================== KOREAN ====================
    private val koreanWords = setOf(
        "나", "너", "그", "그녀", "이것", "저것", "무엇", "누구", "언제", "어디", "왜", "어떻게",
        "네", "아니요", "아마", "감사합니다", "미안합니다", "안녕하세요", "안녕", "안녕히",
        "가다", "오다", "하다", "보다", "듣다", "말하다", "알다", "모르다", "원하다",
        "사랑", "가족", "친구", "집", "일", "학교", "물", "음식", "돈",
        "입니다", "있습니다", "했습니다", "갑니다", "옵니다", "합니다"
    )
    
    // ==================== CHINESE (pinyin) ====================
    private val chinesePinyinWords = setOf(
        "wo", "ni", "ta", "zhe", "na", "shenme", "shui", "shenme", "nar", "weishenme",
        "zenme", "shi", "bu", "shi", "de", "le", "zai", "you", "he", "ge", "zhe",
        "xie", "na", "xie", "wo", "men", "ni", "men", "ta", "men", "zhe", "ge",
        "na", "ge", "yi", "ge", "mei", "ge", "da", "xiao", "hao", "huai", "duo", "shao",
        "xiexie", "duibuqi", "qing", "zaijian", "shi", "bu", "yexu", "keneng",
        "ai", "jia", "pengyou", "jia", "gongzuo", "xuexiao", "shui", "fan", "qian",
        "nihao", "zaoshang", "wanan", "huanying"
    )
}
