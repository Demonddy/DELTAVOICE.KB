package com.deltavoice.config

/**
 * Supabase Configuration
 * 
 * Replace these values with your actual Supabase project credentials:
 * 1. Go to https://supabase.com
 * 2. Create a new project or use an existing one
 * 3. Go to Project Settings > API
 * 4. Copy your Project URL and anon/public key
 * 5. Replace the values below
 * 
 * For production, consider using BuildConfig or a secure configuration system
 */
object SupabaseConfig {
    const val SUPABASE_URL = "https://rkfveqzktfmgegtsoxlf.supabase.co"

    const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJrZnZlcXprdGZtZ2VndHNveGxmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzY3NzAyMDYsImV4cCI6MjA5MjM0NjIwNn0.dOmPCxz5Dq5ZtnX3LU7LTNjyHFcxWbJ5XLNrWPUF0NM"
    
    // Edge Function names (matching supabase/functions/ folder)
    const val FUNCTION_VOICE_TO_TEXT = "voice-to-text"
    const val FUNCTION_TRANSLATE_TEXT = "translate-text"
    const val FUNCTION_FREE_VOICE_TRANSLATE = "free-voice-translate"
    const val FUNCTION_FREE_TRANSLATE_TEXT = "free-translate-text"
    const val FUNCTION_CREATE_VOICE_CLONE = "create-voice-clone"
    const val FUNCTION_VOICE_CONVERSION = "voice-conversion"
    const val FUNCTION_COMPLETE_VOICE_WORKFLOW = "complete-voice-workflow"
    const val FUNCTION_WRITING_TOOL = "writing-tool"
    const val FUNCTION_AI_CHAT = "ai-chat"
    
    /** Writing-tool Edge Function endpoint. */
    const val WRITING_TOOL_ENDPOINT = "https://rkfveqzktfmgegtsoxlf.supabase.co/functions/v1/writing-tool"
    /** Anon key for writing-tool. If the function is on a different project, set that project's anon key here. */
    const val WRITING_TOOL_ANON_KEY = SUPABASE_ANON_KEY
    
    // Whisper Backend Configuration
    // TODO: Replace with your self-hosted Whisper backend URL (e.g., Replit deployment)
    // Set to empty string to use Supabase Edge Functions only
    // Example: "https://your-repl-name.your-username.repl.co"
    const val WHISPER_BACKEND_URL = ""
    
    // Enable direct whisper-backend usage (bypasses Supabase)
    const val USE_DIRECT_WHISPER_BACKEND = false
}

