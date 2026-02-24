import "https://deno.land/x/xhr@0.1.0/mod.ts";
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

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

  console.log('Starting audio transcription with OpenAI Whisper');
  console.log('Audio data size:', audioBase64.length, 'bytes');
  
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
  
  console.log('Processed audio size:', binaryAudio.length, 'bytes');
  
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
      console.error('OpenAI Whisper API error:', response.status, errorText);
      throw new Error(`Transcription failed: ${response.status} - ${errorText}`);
    }

    const result = await response.json();
    console.log('Transcription successful:', result.text);
    return result.text;
  } catch (error) {
    clearTimeout(timeoutId);
    if (error.name === 'AbortError') {
      throw new Error('Transcription timeout - video too long or processing too slow');
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

  console.log('Translating text to:', targetLanguage);

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
    console.error('OpenAI Translation API error:', response.status, errorText);
    throw new Error(`Translation failed: ${response.status} - ${errorText}`);
  }

  const result = await response.json();
  const translatedText = result.choices[0].message.content.trim();
  console.log('Translation successful:', translatedText);
  return translatedText;
}

// Helper to get ElevenLabs API key
function getElevenLabsApiKey(): string {
  return Deno.env.get('ELEVENLABS_API_KEY77') || Deno.env.get('ELEVENLABS_API_KEY') || '';
}

// Helper function to create voice clone from audio using ElevenLabs
async function createVoiceCloneFromAudio(audioBase64: string, name: string, format?: string): Promise<string> {
  console.log('🎭 STARTING VOICE CLONE CREATION:', name);
  console.log('🎭 Audio data size:', audioBase64.length);
  
  const elevenLabsApiKey = getElevenLabsApiKey();
  if (!elevenLabsApiKey) {
    console.error('🎭 ElevenLabs API key not found!');
    throw new Error('ElevenLabs API key not configured');
  }

  console.log('🎭 ElevenLabs API key found, proceeding...');

  try {
    // Convert base64 to blob for ElevenLabs
    console.log('🎭 Converting base64 to blob...');
    const audioBuffer = Uint8Array.from(atob(audioBase64), c => c.charCodeAt(0));
    const audioMeta = resolveAudioMeta(format);
    const audioBlob = new Blob([audioBuffer], { type: audioMeta.mimeType });
    console.log('🎭 Audio blob size:', audioBlob.size);

    // Create FormData for ElevenLabs API
    const formData = new FormData();
    formData.append('name', name);
    formData.append('description', `Auto-generated voice clone: ${name}`);
    formData.append('files', audioBlob, audioMeta.fileName);
    console.log('🎭 FormData prepared for ElevenLabs');

    // Create voice clone with ElevenLabs
    console.log('🎭 Calling ElevenLabs API...');
    const response = await fetch('https://api.elevenlabs.io/v1/voices/add', {
      method: 'POST',
      headers: {
        'xi-api-key': elevenLabsApiKey,
      },
      body: formData,
    });

    console.log('🎭 ElevenLabs API response status:', response.status);

    if (!response.ok) {
      const errorText = await response.text();
      console.error('🎭 ElevenLabs voice clone creation error:', response.status, errorText);
      throw new Error(`Voice clone creation failed: ${response.status} - ${errorText}`);
    }

    const result = await response.json();
    console.log('🎭 VOICE CLONE CREATED SUCCESSFULLY! Voice ID:', result.voice_id);
    return result.voice_id;
  } catch (error) {
    console.error('🎭 ERROR in voice clone creation:', error);
    throw error;
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
    console.log('🎭 MYVOICECLONE REQUESTED - strict fresh clone from current recording');
    console.log('🎭 Audio data available:', !!audioBase64, 'Size:', audioBase64 ? audioBase64.length : 0);

    if (!audioBase64 || audioBase64.length < 2000) {
      throw new Error('Voice clone requires a valid recording. Please record a longer voice sample and try again.');
    }

    try {
      // Always build clone from THIS recording so output matches the latest speaker sample.
      const cloneName = `Live Voice Clone ${Date.now()}`;
      voiceId = await createVoiceCloneFromAudio(audioBase64, cloneName, audioFormat);
      console.log('🎭 Fresh voice clone created for current request:', voiceId);
    } catch (error) {
      console.error('🎭 Fresh voice clone creation failed:', error);
      throw new Error(`Voice clone creation failed for current recording: ${error instanceof Error ? error.message : String(error)}`);
    }
  } else if (voiceStyle.startsWith('clone_')) {
    // For existing voice clones, get the actual ElevenLabs voice ID
    console.log('Using voice clone:', voiceStyle);
    voiceId = '9BWtsMINqrJLrRacOk9x'; // Default to Aria for now
    
    // TODO: In production, you would:
    // 1. Store clone_id -> elevenlabs_voice_id mapping in database
    // 2. Look up the actual ElevenLabs voice ID here
    // 3. Use that voice ID for TTS
  } else {
    console.log('🎤 VOICE SELECTION - Standard voice requested:', voiceStyle);
    voiceId = elevenLabsVoiceMap[voiceStyle] || '9BWtsMINqrJLrRacOk9x';
    console.log('🎤 VOICE SELECTION - Mapped to voice ID:', voiceId);
    
    if (!elevenLabsVoiceMap[voiceStyle]) {
      console.warn('⚠️ VOICE SELECTION - Voice style not found in mapping, defaulting to Aria:', voiceStyle);
      console.log('🎤 Available voice styles:', Object.keys(elevenLabsVoiceMap));
    } else {
      console.log('✅ VOICE SELECTION - Voice style found and mapped successfully');
    }
  }

  console.log('🎵 FINAL VOICE SELECTION:', {
    requestedStyle: voiceStyle,
    selectedVoiceId: voiceId,
    text: text.substring(0, 50) + '...'
  });

  // For cloned voices, prioritize quality/fidelity over speed.
  const isClonedVoice = voiceStyle === 'myvoiceclone' || voiceStyle.startsWith('clone_');
  const langCode = targetLanguage ? languageCodeMap[targetLanguage] || targetLanguage : undefined;
  const ttsUrl = `https://api.elevenlabs.io/v1/text-to-speech/${voiceId}?optimize_streaming_latency=${isClonedVoice ? 0 : 2}`;
  const voiceSettings = isClonedVoice
    ? { stability: 0.35, similarity_boost: 1.0, style: 0.0, use_speaker_boost: true }
    : { stability: 0.5, similarity_boost: 0.75, style: 0.0, use_speaker_boost: true };
  const modelsToTry = isClonedVoice
    ? ['eleven_multilingual_v2', 'eleven_turbo_v2_5']
    : ['eleven_turbo_v2_5', 'eleven_multilingual_v2'];
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
    console.warn(`ElevenLabs ${modelId} failed:`, response.status, lastError);
  }
  if (!response || !response.ok) {
    console.error('ElevenLabs API error:', response?.status, lastError);
    throw new Error(`Voice conversion failed: ${response?.status || 'unknown'} - ${lastError}`);
  }

  const audioBuffer = await response.arrayBuffer();
  const base64Audio = btoa(String.fromCharCode(...new Uint8Array(audioBuffer)));
  console.log('Voice conversion successful');
  return base64Audio;
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response(null, { headers: corsHeaders });
  }

  let originalText = '';
  let translatedText = '';
  let convertedAudioBase64 = '';
  let workflowType = 'complete';
  let targetLanguage = '';
  let voiceStyle = '';

  try {
    // Check API keys first (support OPENAI_API_KEY77 for OpenAI)
    const openAIApiKey = getOpenAIApiKey();
    const elevenLabsApiKey = Deno.env.get('ELEVENLABS_API_KEY77') || Deno.env.get('ELEVENLABS_API_KEY');
    
    console.log('🔍 API Keys Check:', {
      openAI: !!openAIApiKey,
      elevenLabs: !!elevenLabsApiKey
    });

    if (!openAIApiKey) {
      return new Response(
        JSON.stringify({ 
          error: 'OpenAI API key not configured. Please add your OpenAI API key in Supabase secrets.',
          code: 'MISSING_OPENAI_KEY'
        }),
        { status: 500, headers: corsHeaders }
      );
    }

    if (!elevenLabsApiKey) {
      return new Response(
        JSON.stringify({ 
          error: 'ElevenLabs API key not configured. Please add your ElevenLabs API key in Supabase secrets.',
          code: 'MISSING_ELEVENLABS_KEY'
        }),
        { status: 500, headers: corsHeaders }
      );
    }

    console.log('🚀 Edge function started - complete-voice-workflow');
    console.log('🚀 Request method:', req.method);
    
    const reqBody = await req.json();
    const { audioBase64, format } = reqBody;
    targetLanguage = reqBody.targetLanguage || '';
    voiceStyle = reqBody.voiceStyle || '';
    workflowType = reqBody.workflowType || 'complete';

    console.log('🚀 Request payload received:', { 
      audioSize: audioBase64?.length || 0,
      targetLanguage, 
      voiceStyle, 
      workflowType,
      format
    });

    if (!audioBase64) {
      console.error('🚀 ERROR: No audio data provided');
      return new Response(
        JSON.stringify({ 
          success: false,
          error: 'Audio data is required' 
        }),
        { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    // Step 1: Transcribe the audio to text (shared by all modes)
    console.log('Step 1: Transcribing audio...');
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
        console.log('Step 2 [complete]: Translation done. Step 3: TTS with preset voice:', voiceStyle);
        convertedAudioBase64 = await convertTextToSpeech(translatedText, voiceStyle, req, undefined, format, targetLanguage);
        break;
      }
      case 'voice-only': {
        // Translate My Same Voice: Clone user's voice from recording + optional translation + TTS with clone
        translatedText = shouldTranslate ? await translateText(originalText, targetLanguage) : originalText;
        console.log('Step 2 [voice-only]: Translation done. Step 3: TTS with voice clone from recording');
        convertedAudioBase64 = await convertTextToSpeech(translatedText, 'myvoiceclone', req, audioBase64, format, targetLanguage);
        break;
      }
      case 'text-only': {
        // Transcript & Translate: Translation only, no TTS
        translatedText = shouldTranslate ? await translateText(originalText, targetLanguage) : originalText;
        console.log('Step 2 [text-only]: Translation done. Step 3: Skipped (text output only)');
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

    console.log('Workflow completed:', {
      originalText: originalText.substring(0, 50) + '...',
      translatedText: translatedText.substring(0, 50) + '...',
      targetLanguage,
      voiceStyle,
      workflowType,
      hasAudio: !!convertedAudioBase64
    });

    return new Response(
      JSON.stringify(result),
      { 
        headers: { 
          ...corsHeaders, 
          'Content-Type': 'application/json' 
        } 
      }
    );

  } catch (error) {
    console.error('Voice workflow error:', error);
    const errMsg = error instanceof Error ? error.message : String(error);

    // When TTS fails but we have transcription + translation, return partial success for device fallback
    const isTtsError = errMsg.includes('Voice conversion failed') ||
      errMsg.includes('Voice clone creation failed');
    const hasText = (originalText && originalText.trim()) || (translatedText && translatedText.trim());
    if (isTtsError && hasText && (workflowType === 'complete' || workflowType === 'voice-only')) {
      console.log('TTS failed - returning text for device TTS fallback');
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
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      });
    }

    // Provide specific error messages based on the error type
    let userFriendlyMessage = 'Voice processing failed. Please try again.';
    
    if (errMsg.includes('OpenAI API key not configured')) {
      userFriendlyMessage = 'OpenAI API key not configured. Please add your OpenAI API key to continue.';
    } else if (errMsg.includes('ElevenLabs API key not configured')) {
      userFriendlyMessage = 'ElevenLabs API key not configured. Please add your ElevenLabs API key to continue.';
    } else if (errMsg.includes('No speech detected')) {
      userFriendlyMessage = errMsg;
    } else if (errMsg.includes('Transcription failed')) {
      userFriendlyMessage = 'Failed to transcribe audio. Please ensure good audio quality and try again.';
    } else if (errMsg.includes('Translation failed')) {
      userFriendlyMessage = 'Translation failed. Please check your settings and try again.';
    } else if (errMsg.includes('Voice conversion failed')) {
      userFriendlyMessage = 'Voice conversion failed. Please try again or select a different voice.';
    }

    return new Response(
      JSON.stringify({ 
        success: false,
        error: userFriendlyMessage,
        details: errMsg,
        timestamp: new Date().toISOString()
      }),
      { 
        status: 500, 
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      }
    );
  }
});
