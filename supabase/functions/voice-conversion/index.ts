
import "https://deno.land/x/xhr@0.1.0/mod.ts";
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

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
  // Legacy mappings for backward compatibility
  'professional-male': 'CwhRBWXzGAHq8TQ4Fs17',
  'friendly-male': 'TX3LPaxmHKxFdv7VOQHJ',
  'professional-female': 'EXAVITQu4vr4xnSDxMaL',
  'friendly-female': 'FGY2WhTYpPnrIDTdsKH5',
  'child-voice': 'IKne3meq5aSn9XLyUdCD',
  'robotic-voice': '9BWtsMINqrJLrRacOk9x',
  'celebrity-deep': 'JBFqnCBsd6RMkjVDRZzb',
  'funny-voice': 'XB0fDUnXU5powFXDhCwa',
  'narrator-voice': 'CwhRBWXzGAHq8TQ4Fs17',
  'whispery-voice': 'FGY2WhTYpPnrIDTdsKH5'
};

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response(null, { headers: corsHeaders });
  }

  try {
    const { text, voiceStyle, targetLanguage } = await req.json();

    if (!text || !voiceStyle) {
      return new Response(
        JSON.stringify({ error: 'Text and voice style are required' }),
        { status: 400, headers: corsHeaders }
      );
    }

    console.log('Voice conversion request:', { text, voiceStyle, targetLanguage });

    let finalText = text;

    // Step 1: Translate text if target language is specified and not English
    if (targetLanguage && targetLanguage !== 'en') {
      console.log('Translating text to:', targetLanguage);
      
      const translationResponse = await fetch(`${req.url.split('/functions/v1/')[0]}/functions/v1/translate-text`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': req.headers.get('Authorization') || '',
        },
        body: JSON.stringify({
          text: text,
          targetLanguage: targetLanguage,
          model: 'gpt-4o-mini'
        }),
      });

      if (translationResponse.ok) {
        const translationData = await translationResponse.json();
        finalText = translationData.translatedText || text;
        console.log('Translation successful:', finalText);
      } else {
        const errorData = await translationResponse.json();
        console.warn('Translation failed:', errorData);
        throw new Error(`Translation failed: ${errorData.error || 'Unknown translation error'}`);
      }
    }

    // Step 2: Convert translated text to speech using ElevenLabs
    const elevenLabsApiKey = Deno.env.get('ELEVENLABS_API_KEY');
    if (!elevenLabsApiKey) {
      throw new Error('ElevenLabs API key not configured');
    }

    // Check if voiceStyle is a voice clone (starts with 'clone_') or a regular voice
    let voiceId: string;
    if (voiceStyle.startsWith('clone_')) {
      // For voice clones, we need to get the actual ElevenLabs voice ID
      // In a real implementation, you'd store the mapping between clone IDs and ElevenLabs voice IDs
      // For now, we'll use a default voice for clones
      console.log('Using voice clone:', voiceStyle);
      voiceId = '9BWtsMINqrJLrRacOk9x'; // Default to Aria for now
      
      // TODO: In production, you would:
      // 1. Store clone_id -> elevenlabs_voice_id mapping in database
      // 2. Look up the actual ElevenLabs voice ID here
      // 3. Use that voice ID for TTS
    } else {
      voiceId = elevenLabsVoiceMap[voiceStyle] || '9BWtsMINqrJLrRacOk9x';
    }
    
    console.log('Converting to speech with voice:', voiceId, 'Text:', finalText.substring(0, 100));

    const ttsResponse = await fetch(`https://api.elevenlabs.io/v1/text-to-speech/${voiceId}`, {
      method: 'POST',
      headers: {
        'Accept': 'audio/mpeg',
        'Content-Type': 'application/json',
        'xi-api-key': elevenLabsApiKey,
      },
      body: JSON.stringify({
        text: finalText,
        model_id: 'eleven_multilingual_v2',
        voice_settings: {
          stability: 0.5,
          similarity_boost: 0.75,
          style: 0.0,
          use_speaker_boost: true
        }
      }),
    });

    if (!ttsResponse.ok) {
      const errorText = await ttsResponse.text();
      console.error('ElevenLabs API error:', ttsResponse.status, errorText);
      throw new Error(`ElevenLabs API error: ${ttsResponse.status} - ${errorText}`);
    }

    const audioBuffer = await ttsResponse.arrayBuffer();
    const base64Audio = btoa(String.fromCharCode(...new Uint8Array(audioBuffer)));

    return new Response(
      JSON.stringify({
        success: true,
        audioBase64: base64Audio,
        originalText: text,
        translatedText: finalText,
        voiceStyle: voiceStyle,
        targetLanguage: targetLanguage || 'en',
        isVoiceClone: voiceStyle.startsWith('clone_')
      }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );

  } catch (error) {
    console.error('Voice conversion error:', error);
    return new Response(
      JSON.stringify({ 
        error: error.message || 'Voice conversion failed',
        timestamp: new Date().toISOString()
      }),
      { status: 500, headers: corsHeaders }
    );
  }
});
