package com.deltavoice

object VoiceProcessIntent {
    const val ACTION_START_VOICE_PROCESS = "com.deltavoice.action.START_VOICE_PROCESS"

    const val EXTRA_MODE = "com.deltavoice.extra.MODE"
    const val EXTRA_LANGUAGE = "com.deltavoice.extra.LANGUAGE"
    const val EXTRA_VOICE_STYLE = "com.deltavoice.extra.VOICE_STYLE"
    const val EXTRA_AUDIO_FILE_PATH = "com.deltavoice.extra.AUDIO_FILE_PATH"

    const val MODE_FULL = "full"
    const val MODE_VOICE_ONLY = "voice_only"
    const val MODE_TEXT_ONLY = "text_only"
    const val MODE_QUICK_TEXT = "quick_text"
}

