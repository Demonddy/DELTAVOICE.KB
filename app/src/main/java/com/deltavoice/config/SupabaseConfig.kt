package com.deltavoice.config

import com.deltavoice.BuildConfig

object SupabaseConfig {
    val SUPABASE_URL: String = BuildConfig.SUPABASE_URL.ifBlank {
        "https://yvizvsojpwgvaisoahda.supabase.co"
    }

    val SUPABASE_ANON_KEY: String by lazy {
        BuildConfig.SUPABASE_ANON_KEY.also {
            if (it.isBlank()) {
                android.util.Log.e("SupabaseConfig",
                    "SUPABASE_ANON_KEY not set! Add it to local.properties or CI environment.")
            }
        }
    }

    const val FUNCTION_VOICE_TO_TEXT = "voice-to-text"
    const val FUNCTION_TRANSLATE_TEXT = "translate-text"
    const val FUNCTION_FREE_VOICE_TRANSLATE = "free-voice-translate"
    const val FUNCTION_FREE_TRANSLATE_TEXT = "free-translate-text"
    const val FUNCTION_CREATE_VOICE_CLONE = "create-voice-clone"
    const val FUNCTION_VOICE_CONVERSION = "voice-conversion"
    const val FUNCTION_COMPLETE_VOICE_WORKFLOW = "complete-voice-workflow"
    const val FUNCTION_WRITING_TOOL = "writing-tool"
    const val FUNCTION_AI_CHAT = "ai-chat"

    val WRITING_TOOL_ENDPOINT: String
        get() = "$SUPABASE_URL/functions/v1/writing-tool"

    val WRITING_TOOL_ANON_KEY: String
        get() = SUPABASE_ANON_KEY

    const val WHISPER_BACKEND_URL = ""
    const val USE_DIRECT_WHISPER_BACKEND = false
}
