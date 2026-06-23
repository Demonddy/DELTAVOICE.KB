
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

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response(null, { headers: { ...corsHeadersForRequest(req), 'Content-Type': 'application/json' } });
  }

  const auth = await secureEdgeRequest(req, "create-voice-clone");
  if (auth instanceof Response) return auth;

  try {
    const { name, audioBase64, description, format } = await req.json();

    if (!name || !audioBase64) {
      return jsonResponse({ error: 'Name and audio data are required.' }, 400, req);
    }

    const elevenLabsApiKey = Deno.env.get('ELEVENLABS_API_KEY');
    if (!elevenLabsApiKey) {
      throw new Error('ElevenLabs API key not configured');
    }

    const audioBuffer = Uint8Array.from(atob(audioBase64), c => c.charCodeAt(0));
    const audioMeta = resolveAudioMeta(format);
    const audioBlob = new Blob([audioBuffer], { type: audioMeta.mimeType });

    const formData = new FormData();
    formData.append('name', name);
    formData.append('description', description || `Voice clone: ${name}`);
    formData.append('files', audioBlob, audioMeta.fileName);

    const response = await fetch('https://api.elevenlabs.io/v1/voices/add', {
      method: 'POST',
      headers: {
        'xi-api-key': elevenLabsApiKey,
      },
      body: formData,
    });

    if (!response.ok) {
      const errorText = await response.text();
      logger.error("create-voice-clone", `ElevenLabs API error: ${response.status}`, errorText.slice(0, 400));
      throw new Error(`ElevenLabs API error: ${response.status}`);
    }

    const result = await response.json();

    return jsonResponse({
      success: true,
      voiceId: result.voice_id,
      name: name,
      message: 'Voice clone created successfully'
    }, 200, req);

  } catch (error) {
    return handleServerError(
      "create-voice-clone",
      error,
      req,
      "Voice clone creation failed. Please try again.",
    );
  }
});
