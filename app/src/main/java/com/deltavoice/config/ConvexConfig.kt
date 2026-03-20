package com.deltavoice.config

/**
 * Convex Configuration for real-time voice workflow (complete & voice-only).
 *
 * Setup:
 * 1. Run `npx convex dev` or `npx convex deploy` from project root
 * 2. In Convex Dashboard: Settings > Environment Variables, add:
 *    - OPENAI_API_KEY (or OPENAI_API_KEY77) — for voice workflow
 *    - Deepseeka — for AI chat
 *    - ELEVENLABS_API_KEY (or ELEVENLABS_API_KEY77)
 * 3. Replace CONVEX_SITE_URL below with your deployment URL from Convex Dashboard
 *    Format: https://<your-deployment>.convex.site
 */
object ConvexConfig {
    /**
     * Convex HTTP site URL. Get from Convex Dashboard after deployment.
     * Example: https://happy-animal-123.convex.site
     */
    const val CONVEX_SITE_URL = "https://kindred-curlew-363.eu-west-1.convex.site"

    /** Voice workflow path - handles complete and voice-only workflows */
    const val VOICE_WORKFLOW_PATH = "/complete-voice-workflow"

    /** AI chat path - ChatGPT-like assistant (alternative to Supabase when unreachable) */
    const val AI_CHAT_PATH = "/ai-chat"

    /** Full endpoint URL for voice workflow */
    val VOICE_WORKFLOW_URL: String
        get() = "$CONVEX_SITE_URL$VOICE_WORKFLOW_PATH"

    /** Full endpoint URL for AI chat */
    val AI_CHAT_URL: String
        get() = "$CONVEX_SITE_URL$AI_CHAT_PATH"

    /**
     * Use Convex for real-time delivery of complete and voice-only workflows when true.
     * Convex is tried first (with retries) before falling back to Supabase.
     * Set to false to use Supabase only.
     */
    const val USE_CONVEX_FOR_VOICE_WORKFLOW = true
}
