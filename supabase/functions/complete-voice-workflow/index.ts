import "https://deno.land/x/xhr@0.1.0/mod.ts";
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { corsHeadersForRequest, handleServerError, jsonResponse, logger, secureEdgeRequest } from "../_shared/security.ts";

const audioMimeMap: Record<string, string> = {
  m4a: 'audio/mp4',
  mp4: 'audio/mp4',
  mp3: 'audio/mpeg',
  wav: 'audio/wav',
  webm: 'audio/webm',
  ogg: 'audio/ogg',
  oga: 'audio/ogg',
  flac: 'audio/flac',
  aac: 'audio/aac',
  caf: 'audio/x-caf',
  '3gp': 'audio/3gpp',
}

function resolveAudioMeta(format?: string) {
  const ext = (format || 'webm').toLowerCase().replace('.', '')
  const safeExt = audioMimeMap[ext] ? ext : 'webm'
  return {
    mimeType: audioMimeMap[safeExt] || 'audio/webm',
    fileName: `audio.${safeExt}`,
  }
}

const elevenLabsVoiceMap: { [key: string]: string } = {
  'aria': '9BWtsMINqrJLrRacOk9x',
  'roger': 'CwhRBWXzGAHq8TQ4Fs17',
  'sarah': 'EXAVITQu4vr4xnSDxMaL',
  'laura': 'FGY2WhTYpPnrIDTdsKH5',
  'charlie': 'IKne3meq5aSn9XLyUdCD',
  'george': 'JBFqnCBsd6RMkjVDRZzb',
  'liam': 'TX3LPaxmHKxFdv7VOQHJ',
  'charlotte': 'XB0fDUnXU5powFXDhCwa',
  'alice': 'Xb7hH8MSUJpSbSDYk0k2',
  'matilda': 'XrExE9yKIg1WjnnlVkGX',
  // Adding missing voices that were causing fallback to Aria
  'adam': 'pNInz6obpgDQGcFmaJgB',
  'bill': 'pqHfZKP75CvOlQylNhV4',
  'carter': 'EXAVITQu4vr4xnSDxMaL',
  'daniel': 'onwK4e9ZLuTAKqWW03F9',
  'cassidy': 'cgSgspJ2msm6clMCkdW9',
  'jessica': 'cgSgspJ2msm6clMCkdW9',
  'lily': 'pFZP5JQG7iQjIQuC4Bku'
};

// Helper to get OpenAI API key (supports OPENAI_API_KEY77 or OPENAI_API_KEY)
function getOpenAIApiKey(): string {
  return Deno.env.get('OPENAI_API_KEY77') || Deno.env.get('OPENAI_API_KEY') || '';
}

// Helper function to transcribe audio using OpenAI Whisper with optimizations for longer audio
async function transcribeAudio(audioBase64: string, format?: string): Promise<string> {
  const openAIApiKey = getOpenAIApiKey();
  if (!openAIApiKey) {
    throw new Error('OpenAI API key not configured');
  }

  
  // Convert base64 to binary with chunked processing for large files
  const chunkSize = 32768; // 32KB chunks
  const chunks: Uint8Array[] = [];
  let position = 0;
  
  while (position < audioBase64.length) {
    const chunk = audioBase64.slice(position, position + chunkSize);
    const binaryChunk = atob(chunk);
    const bytes = new Uint8Array(binaryChunk.length);
    
    for (let i = 0; i < binaryChunk.length; i++) {
      bytes[i] = binaryChunk.charCodeAt(i);
    }
    
    chunks.push(bytes);
    position += chunkSize;
  }

  const totalLength = chunks.reduce((acc, chunk) => acc + chunk.length, 0);
  const binaryAudio = new Uint8Array(totalLength);
  let offset = 0;

  for (const chunk of chunks) {
    binaryAudio.set(chunk, offset);
    offset += chunk.length;
  }
  
  
  // Prepare form data
  const formData = new FormData();
  const audioMeta = resolveAudioMeta(format);
  const blob = new Blob([binaryAudio], { type: audioMeta.mimeType });
  formData.append('file', blob, audioMeta.fileName);
  formData.append('model', 'whisper-1');
  // Remove invalid 'auto' language parameter - Whisper auto-detects by default

  // Set longer timeout for longer videos
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 120000); // 2 minute timeout

  try {
    const response = await fetch('https://api.openai.com/v1/audio/transcriptions', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${openAIApiKey}`,
      },
      body: formData,
      signal: controller.signal,
    });

    clearTimeout(timeoutId);

    if (!response.ok) {
      const errorText = await response.text();
      logger.error("complete-voice-workflow", `Whisper API error: ${response.status}`, errorText);
      throw new Error("TRANSCRIPTION_FAILED");
    }

    const result = await response.json();
    return result.text;
  } catch (error) {
    clearTimeout(timeoutId);
    if (error.name === 'AbortError') {
      throw new Error('TRANSCRIPTION_TIMEOUT');
    }
    throw error;
  }
}

// Helper function to translate text using OpenAI
async function translateText(text: string, targetLanguage: string): Promise<string> {
  const openAIApiKey = getOpenAIApiKey();
  if (!openAIApiKey) {
    throw new Error('OpenAI API key not configured');
  }


  const languageNames: { [key: string]: string } = {
    'en': 'English',
    'es': 'Spanish', 'fr': 'French', 'de': 'German', 'it': 'Italian',
    'pt': 'Portuguese', 'ru': 'Russian', 'ja': 'Japanese', 'ko': 'Korean',
    'zh': 'Chinese', 'ar': 'Arabic', 'hi': 'Hindi', 'nl': 'Dutch',
    'pl': 'Polish', 'tr': 'Turkish', 'sv': 'Swedish', 'da': 'Danish',
    'no': 'Norwegian', 'fi': 'Finnish', 'he': 'Hebrew', 'th': 'Thai',
    'vi': 'Vietnamese', 'uk': 'Ukrainian', 'cs': 'Czech', 'hu': 'Hungarian',
    'ro': 'Romanian'
  };

  const targetLanguageName = languageNames[targetLanguage] || targetLanguage;

  const response = await fetch('https://api.openai.com/v1/chat/completions', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${openAIApiKey}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      model: 'gpt-4o-mini',
      messages: [
        {
          role: 'system',
          content: `You are a professional translator. Translate the given text accurately to ${targetLanguageName}. Only return the translated text, nothing else.`
        },
        {
          role: 'user',
          content: text
        }
      ],
      temperature: 0.3,
    }),
  });

  if (!response.ok) {
    const errorText = await response.text();
    logger.error("complete-voice-workflow", `Translation API error: ${response.status}`, errorText);
    throw new Error("TRANSLATION_FAILED");
  }

  const result = await response.json();
  const translatedText = result.choices[0].message.content.trim();
  return translatedText;
}

// Helper to get ElevenLabs API key
function getElevenLabsApiKey(): string {
  return Deno.env.get('ELEVENLABS_API_KEY77') || Deno.env.get('ELEVENLABS_API_KEY') || '';
}

// Helper function to create voice clone from audio using ElevenLabs
async function createVoiceCloneFromAudio(audioBase64: string, name: string, format?: string): Promise<string> {
  
  const elevenLabsApiKey = getElevenLabsApiKey();
  if (!elevenLabsApiKey) {
    logger.error("complete-voice-workflow", "ElevenLabs API key not configured");
    throw new Error('ElevenLabs API key not configured');
  }


  try {
    // Convert base64 to blob for ElevenLabs
    const audioBuffer = Uint8Array.from(atob(audioBase64), c => c.charCodeAt(0));
    const audioMeta = resolveAudioMeta(format);
    const audioBlob = new Blob([audioBuffer], { type: audioMeta.mimeType });

    // Create FormData for ElevenLabs API
    const formData = new FormData();
    formData.append('name', name);
    formData.append('description', `Auto-generated voice clone: ${name}`);
    formData.append('files', audioBlob, audioMeta.fileName);

    // Create voice clone with ElevenLabs
    const response = await fetch('https://api.elevenlabs.io/v1/voices/add', {
      method: 'POST',
      headers: {
        'xi-api-key': elevenLabsApiKey,
      },
      body: formData,
    });


    if (!response.ok) {
      const errorText = await response.text();
      logger.error("complete-voice-workflow", `Voice clone creation error: ${response.status}`, errorText);
      throw new Error("VOICE_CLONE_FAILED");
    }

    const result = await response.json();
    return result.voice_id;
  } catch (error) {
    logger.error("complete-voice-workflow", "Voice clone creation failed", error);
    throw new Error("VOICE_CLONE_FAILED");
  }
}

// Map language codes to ElevenLabs ISO 639-1 format
const languageCodeMap: Record<string, string> = {
  'en': 'en', 'es': 'es', 'fr': 'fr', 'de': 'de', 'it': 'it', 'pt': 'pt',
  'ru': 'ru', 'ja': 'ja', 'ko': 'ko', 'zh': 'zh', 'ar': 'ar', 'hi': 'hi',
  'nl': 'nl', 'pl': 'pl', 'tr': 'tr', 'sv': 'sv', 'da': 'da', 'no': 'no',
  'fi': 'fi', 'he': 'he', 'th': 'th', 'vi': 'vi', 'uk': 'uk'
};

// Helper function to convert text to speech using ElevenLabs
async function convertTextToSpeech(
  text: string,
  voiceStyle: string,
  req: Request,
  audioBase64?: string,
  audioFormat?: string,
  targetLanguage?: string
): Promise<string> {
  const elevenLabsApiKey = getElevenLabsApiKey();
  if (!elevenLabsApiKey) {
    throw new Error('ElevenLabs API key not configured');
  }

  // Handle MyVoiceClone by retrieving from database or creating automatically
  let voiceId: string;
  if (voiceStyle === 'myvoiceclone') {

    if (!audioBase64 || audioBase64.length < 2000) {
      throw new Error('Voice clone requires a valid recording. Please record a longer voice sample and try again.');
    }

    try {
      // Always build clone from THIS recording so output matches the latest speaker sample.
      const cloneName = `Live Voice Clone ${Date.now()}`;
      voiceId = await createVoiceCloneFromAudio(audioBase64, cloneName, audioFormat);
    } catch (error) {
      logger.error("complete-voice-workflow", "Fresh voice clone creation failed", error);
      throw new Error("VOICE_CLONE_FAILED");
    }
  } else if (voiceStyle.startsWith('clone_')) {
    // For existing voice clones, get the actual ElevenLabs voice ID
    voiceId = '9BWtsMINqrJLrRacOk9x'; // Default to Aria for now
    
    // TODO: In production, you would:
    // 1. Store clone_id -> elevenlabs_voice_id mapping in database
    // 2. Look up the actual ElevenLabs voice ID here
    // 3. Use that voice ID for TTS
  } else {
    voiceId = elevenLabsVoiceMap[voiceStyle] || '9BWtsMINqrJLrRacOk9x';
    
    if (!elevenLabsVoiceMap[voiceStyle]) {
      logger.warn("complete-voice-workflow", `Voice style not found, defaulting to Aria: ${voiceStyle}`);
    } else {
    }
  }

  // For cloned voices, prioritize quality/fidelity over speed.
  const isClonedVoice = voiceStyle === 'myvoiceclone' || voiceStyle.startsWith('clone_');
  const langCode = targetLanguage ? languageCodeMap[targetLanguage] || targetLanguage : undefined;
  const ttsUrl = `https://api.elevenlabs.io/v1/text-to-speech/${voiceId}?optimize_streaming_latency=${isClonedVoice ? 0 : 2}`;
  const voiceSettings = isClonedVoice
    ? { stability: 0.35, similarity_boost: 1.0, style: 0.0, use_speaker_boost: true }
    : { stability: 0.5, similarity_boost: 0.75, style: 0.0, use_speaker_boost: true };
  const modelsToTry = isClonedVoice
    ? ['eleven_multilingual_v2', 'eleven_flash_v2_5']
    : ['eleven_flash_v2_5', 'eleven_multilingual_v2'];
  let response: Response | null = null;
  let lastError = '';
  for (const modelId of modelsToTry) {
    const ttsBody: Record<string, unknown> = { text, model_id: modelId, voice_settings: voiceSettings };
    if (langCode) ttsBody.language_code = langCode;
    response = await fetch(ttsUrl, {
      method: 'POST',
      headers: { 'Accept': 'audio/mpeg', 'Content-Type': 'application/json', 'xi-api-key': elevenLabsApiKey },
      body: JSON.stringify(ttsBody),
    });
    if (response.ok) break;
    lastError = await response.text();
    logger.warn("complete-voice-workflow", `ElevenLabs ${modelId} failed: ${response.status}`, lastError);
  }
  if (!response || !response.ok) {
    logger.error("complete-voice-workflow", `ElevenLabs API error: ${response?.status}`, lastError);
    throw new Error("VOICE_CONVERSION_FAILED");
  }

  const audioBuffer = await response.arrayBuffer();
  const base64Audio = btoa(String.fromCharCode(...new Uint8Array(audioBuffer)));
  return base64Audio;
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response(null, { headers: { ...corsHeadersForRequest(req), 'Content-Type': 'application/json' } });
  }

  const auth = await secureEdgeRequest(req, "complete-voice-workflow");
  if (auth instanceof Response) return auth;

  let originalText = '';
  let translatedText = '';
  let convertedAudioBase64 = '';
  let workflowType = 'complete';
  let targetLanguage = '';
  let voiceStyle = '';

  try {
    // Peek at workflowType to enforce premium for voice-only (creates ElevenLabs clones)
    const clonedReq = req.clone();
    const peekBody = await clonedReq.json().catch(() => ({}));
    if (peekBody.workflowType === "voice-only" && !auth.isPremium) {
      return new Response(
        JSON.stringify({
          error: "Premium subscription required for voice cloning.",
          code: "PREMIUM_REQUIRED",
        }),
        { status: 403, headers: { ...corsHeadersForRequest(req), "Content-Type": "application/json" } },
      );
    }

    // Check API keys first (support OPENAI_API_KEY77 for OpenAI)
    const openAIApiKey = getOpenAIApiKey();
    const elevenLabsApiKey = Deno.env.get('ELEVENLABS_API_KEY77') || Deno.env.get('ELEVENLABS_API_KEY');

    if (!openAIApiKey) {
      logger.error("complete-voice-workflow", "OpenAI API key not configured");
      return new Response(
        JSON.stringify({ 
          error: 'Voice processing is temporarily unavailable. Please try again later.',
          code: 'SERVICE_UNAVAILABLE'
        }),
        { status: 500, headers: { ...corsHeadersForRequest(req), 'Content-Type': 'application/json' } }
      );
    }

    if (!elevenLabsApiKey) {
      logger.error("complete-voice-workflow", "ElevenLabs API key not configured");
      return new Response(
        JSON.stringify({ 
          error: 'Voice processing is temporarily unavailable. Please try again later.',
          code: 'SERVICE_UNAVAILABLE'
        }),
        { status: 500, headers: { ...corsHeadersForRequest(req), 'Content-Type': 'application/json' } }
      );
    }

    
    const reqBody = await req.json();
    const { audioBase64, format } = reqBody;
    targetLanguage = (typeof reqBody.targetLanguage === 'string' && reqBody.targetLanguage.trim()) ? reqBody.targetLanguage.trim() : 'en';
    voiceStyle = (typeof reqBody.voiceStyle === 'string' && reqBody.voiceStyle.trim()) ? reqBody.voiceStyle.trim().toLowerCase() : 'aria';
    workflowType = reqBody.workflowType || 'complete';

    if (!audioBase64) {
      return new Response(
        JSON.stringify({ success: false, error: 'Audio data is required' }),
        { status: 400, headers: { ...corsHeadersForRequest(req), 'Content-Type': 'application/json' } }
      );
    }

    const MAX_AUDIO_BASE64_LENGTH = 14_000_000; // ~10 MB decoded
    if (audioBase64.length > MAX_AUDIO_BASE64_LENGTH) {
      return new Response(
        JSON.stringify({ success: false, error: 'Audio file too large. Maximum size is 10 MB.' }),
        { status: 413, headers: { ...corsHeadersForRequest(req), 'Content-Type': 'application/json' } }
      );
    }

    // Step 1: Transcribe the audio to text (shared by all modes)
    originalText = await transcribeAudio(audioBase64, format);

    if (!originalText || originalText.trim().length === 0) {
      throw new Error('No speech detected in audio. Please speak clearly and try again.');
    }

    // Step 2 & 3: Explicit per-mode handling — each workflow is fully independent
    const shouldTranslate = targetLanguage && targetLanguage.trim().length > 0;

    switch (workflowType) {
      case 'complete': {
        // Change Language & Voice: Preset voice (Adam, Aria, etc.) + optional translation + TTS
        translatedText = shouldTranslate ? await translateText(originalText, targetLanguage) : originalText;
        convertedAudioBase64 = await convertTextToSpeech(translatedText, voiceStyle, req, undefined, format, targetLanguage);
        break;
      }
      case 'voice-only': {
        // Translate My Same Voice: Clone user's voice from recording + optional translation + TTS with clone
        translatedText = shouldTranslate ? await translateText(originalText, targetLanguage) : originalText;
        convertedAudioBase64 = await convertTextToSpeech(translatedText, 'myvoiceclone', req, audioBase64, format, targetLanguage);
        break;
      }
      case 'text-only': {
        // Transcript & Translate: Translation only, no TTS
        translatedText = shouldTranslate ? await translateText(originalText, targetLanguage) : originalText;
        break;
      }
      default: {
        translatedText = shouldTranslate ? await translateText(originalText, targetLanguage) : originalText;
        convertedAudioBase64 = await convertTextToSpeech(translatedText, voiceStyle, req, undefined, format, targetLanguage);
        break;
      }
    }

    // Return the workflow result
    const result = {
      success: true,
      originalText,
      translatedText,
      convertedAudioBase64,
      targetLanguage,
      voiceStyle,
      workflowType
    };

    return new Response(
      JSON.stringify(result),
      { 
        headers: { 
          ...corsHeadersForRequest(req), 
          'Content-Type': 'application/json' 
        } 
      }
    );

  } catch (error) {
    logger.error("complete-voice-workflow", "Voice workflow error", error);
    const errMsg = error instanceof Error ? error.message : String(error);

    // When TTS fails but we have transcription + translation, return partial success for device fallback
    const isTtsError = errMsg === "VOICE_CONVERSION_FAILED" || errMsg === "VOICE_CLONE_FAILED";
    const hasText = (originalText && originalText.trim()) || (translatedText && translatedText.trim());
    if (isTtsError && hasText && (workflowType === 'complete' || workflowType === 'voice-only')) {
      return new Response(JSON.stringify({
        success: true,
        originalText: originalText || '',
        translatedText: translatedText || originalText || '',
        convertedAudioBase64: '',
        targetLanguage,
        voiceStyle,
        workflowType,
        ttsFallback: true
      }), {
        status: 200,
        headers: { ...corsHeadersForRequest(req), 'Content-Type': 'application/json' }
      });
    }

    // Provide user-safe error messages; log details server-side only
    let userFriendlyMessage = 'Voice processing failed. Please try again.';
    
    if (errMsg.includes('No speech detected')) {
      userFriendlyMessage = errMsg;
    } else if (errMsg === "TRANSCRIPTION_FAILED" || errMsg === "TRANSCRIPTION_TIMEOUT") {
      userFriendlyMessage = 'Failed to transcribe audio. Please ensure good audio quality and try again.';
    } else if (errMsg === "TRANSLATION_FAILED") {
      userFriendlyMessage = 'Translation failed. Please check your settings and try again.';
    } else if (errMsg === "VOICE_CONVERSION_FAILED") {
      userFriendlyMessage = 'Voice conversion failed. Please try again or select a different voice.';
    } else if (errMsg === "VOICE_CLONE_FAILED") {
      userFriendlyMessage = 'Voice clone creation failed. Please record a longer sample and try again.';
    }

    logger.error('complete-voice-workflow', userFriendlyMessage, error);
    return new Response(
      JSON.stringify({ 
        success: false,
        error: userFriendlyMessage,
      }),
      { 
        status: 500, 
        headers: { ...corsHeadersForRequest(req), 'Content-Type': 'application/json' }
      }
    );
  }
});
