package com.deltavoice.config

import com.deltavoice.BuildConfig

object ConvexConfig {
    val CONVEX_SITE_URL: String = BuildConfig.CONVEX_SITE_URL.ifBlank {
        "https://kindred-curlew-363.eu-west-1.convex.site"
    }

    const val VOICE_WORKFLOW_PATH = "/complete-voice-workflow"
    const val VIDEO_WORKFLOW_PATH = "/video-workflow"
    const val AI_CHAT_PATH = "/ai-chat"

    val VOICE_WORKFLOW_URL: String
        get() = "$CONVEX_SITE_URL$VOICE_WORKFLOW_PATH"

    /** Dedicated Convex site for AI chat (prod deployment with OpenAI keys). Falls back to [CONVEX_SITE_URL]. */
    val AI_CHAT_CONVEX_SITE: String = BuildConfig.AI_CHAT_CONVEX_SITE.ifBlank { CONVEX_SITE_URL }

    val AI_CHAT_URL: String
        get() = "$AI_CHAT_CONVEX_SITE$AI_CHAT_PATH"

    val VIDEO_WORKFLOW_URL: String
        get() = "$CONVEX_SITE_URL$VIDEO_WORKFLOW_PATH"

    const val USE_CONVEX_FOR_VOICE_WORKFLOW = true
}
