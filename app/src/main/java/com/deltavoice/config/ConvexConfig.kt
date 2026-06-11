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

    val AI_CHAT_URL: String
        get() = "$CONVEX_SITE_URL$AI_CHAT_PATH"

    val VIDEO_WORKFLOW_URL: String
        get() = "$CONVEX_SITE_URL$VIDEO_WORKFLOW_PATH"

    const val USE_CONVEX_FOR_VOICE_WORKFLOW = true
}
