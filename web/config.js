/**
 * DeltaVoice Configuration
 * 
 * This configuration matches the Android app's SupabaseConfig.kt
 * Both platforms share the same Supabase backend
 */

const DeltaVoiceConfig = {
    // Supabase project URL (voicetexco.ai)
    SUPABASE_URL: 'https://yvizvsojpwgvaisoahda.supabase.co',
    
    // Supabase anon/public key
    SUPABASE_ANON_KEY: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inl2aXp2c29qcHdndmFpc29haGRhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDk3NDMyNDEsImV4cCI6MjA2NTMxOTI0MX0.EUdnCzjgKD9fTKopQlDIkPSrDd7cke1OtW2IlXraJek',
    
    // Edge Function names (matching supabase/functions/ folder)
    FUNCTIONS: {
        VOICE_TO_TEXT: 'voice-to-text',
        TRANSLATE_TEXT: 'translate-text',
        FREE_VOICE_TRANSLATE: 'free-voice-translate',
        FREE_TRANSLATE_TEXT: 'free-translate-text',
        CREATE_VOICE_CLONE: 'create-voice-clone',
        VOICE_CONVERSION: 'voice-conversion',
        COMPLETE_VOICE_WORKFLOW: 'complete-voice-workflow',
        WRITING_TOOL: 'writing-tool',
        AI_CHAT: 'ai-chat'
    },
    
    // Supported languages
    LANGUAGES: [
        { code: 'en', name: 'English' },
        { code: 'es', name: 'Spanish' },
        { code: 'fr', name: 'French' },
        { code: 'de', name: 'German' },
        { code: 'it', name: 'Italian' },
        { code: 'pt', name: 'Portuguese' },
        { code: 'ru', name: 'Russian' },
        { code: 'ja', name: 'Japanese' },
        { code: 'ko', name: 'Korean' },
        { code: 'zh', name: 'Chinese' },
        { code: 'ar', name: 'Arabic' },
        { code: 'hi', name: 'Hindi' }
    ],
    
    // Available voice styles
    VOICE_STYLES: [
        { id: 'adam', name: 'Adam' },
        { id: 'aria', name: 'Aria' },
        { id: 'roger', name: 'Roger' },
        { id: 'sarah', name: 'Sarah' },
        { id: 'laura', name: 'Laura' },
        { id: 'charlie', name: 'Charlie' },
        { id: 'george', name: 'George' },
        { id: 'liam', name: 'Liam' }
    ],
    
    // Workflow types
    WORKFLOW_TYPES: {
        COMPLETE: 'complete',      // Full: transcribe → translate → voice convert
        VOICE_ONLY: 'voice-only',  // Only change voice style
        TEXT_ONLY: 'text-only'     // Only transcribe and translate to text
    }
};

// Helper function to build Edge Function URL
DeltaVoiceConfig.getFunctionUrl = function(functionName) {
    return `${this.SUPABASE_URL}/functions/v1/${functionName}`;
};

// Helper function to get auth headers
DeltaVoiceConfig.getHeaders = function() {
    return {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${this.SUPABASE_ANON_KEY}`,
        'apikey': this.SUPABASE_ANON_KEY
    };
};

// Export for module systems
if (typeof module !== 'undefined' && module.exports) {
    module.exports = DeltaVoiceConfig;
}

// Make available globally
if (typeof window !== 'undefined') {
    window.DeltaVoiceConfig = DeltaVoiceConfig;
}
