
import "https://deno.land/x/xhr@0.1.0/mod.ts";
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { corsHeadersForRequest, handleServerError, jsonResponse, logger, secureEdgeRequest } from "../_shared/security.ts";

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
    return new Response(null, { headers: { ...corsHeadersForRequest(req), 'Content-Type': 'application/json' } });
  }

  const auth = await secureEdgeRequest(req, "voice-conversion");
  if (auth instanceof Response) return auth;

  try {
    const { text, voiceStyle, targetLanguage } = await req.json();

    if (!text || !voiceStyle) {
      return jsonResponse({ error: 'Text and voice style are required.' }, 400, req);
    }

    let finalText = text;

    if (targetLanguage && targetLanguage !== 'en') {
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
      } else {
        logger.warn("voice-conversion", "Translation step failed");
        throw new Error("Translation failed");
      }
    }

    const elevenLabsApiKey = Deno.env.get('ELEVENLABS_API_KEY');
    if (!elevenLabsApiKey) {
      throw new Error('ElevenLabs API key not configured');
    }

    let voiceId: string;
    if (voiceStyle.startsWith('clone_')) {
      voiceId = '9BWtsMINqrJLrRacOk9x';
    } else {
      voiceId = elevenLabsVoiceMap[voiceStyle] || '9BWtsMINqrJLrRacOk9x';
    }

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
      logger.error("voice-conversion", `ElevenLabs API error: ${ttsResponse.status}`, errorText.slice(0, 400));
      throw new Error(`ElevenLabs API error: ${ttsResponse.status}`);
    }

    const audioBuffer = await ttsResponse.arrayBuffer();
    const base64Audio = btoa(String.fromCharCode(...new Uint8Array(audioBuffer)));

    return jsonResponse({
      success: true,
      audioBase64: base64Audio,
      originalText: text,
      translatedText: finalText,
      voiceStyle: voiceStyle,
      targetLanguage: targetLanguage || 'en',
      isVoiceClone: voiceStyle.startsWith('clone_')
    }, 200, req);

  } catch (error) {
    return handleServerError(
      "voice-conversion",
      error,
      req,
      "Voice conversion failed. Please try again.",
    );
  }
});
