
import "https://deno.land/x/xhr@0.1.0/mod.ts"
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { corsHeadersForRequest, handleServerError, jsonResponse, logger, secureEdgeRequest } from "../_shared/security.ts"

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

function processBase64Safely(base64String: string) {
  try {
    if (base64String.length < 100000) {
      const binaryString = atob(base64String);
      const bytes = new Uint8Array(binaryString.length);
      for (let i = 0; i < binaryString.length; i++) {
        bytes[i] = binaryString.charCodeAt(i);
      }
      return bytes;
    }

    const chunkSize = 8192;
    const chunks: Uint8Array[] = [];
    let position = 0;

    while (position < base64String.length) {
      const chunkEnd = Math.min(position + chunkSize, base64String.length);
      const chunk = base64String.slice(position, chunkEnd);

      try {
        const binaryString = atob(chunk);
        const bytes = new Uint8Array(binaryString.length);

        for (let i = 0; i < binaryString.length; i++) {
          bytes[i] = binaryString.charCodeAt(i);
        }

        chunks.push(bytes);
        position = chunkEnd;

      } catch (chunkError) {
        logger.error("voice-to-text", `Invalid base64 at position ${position}`, chunkError);
        throw new Error(`Invalid base64 data at position ${position}`);
      }
    }

    const totalLength = chunks.reduce((sum, chunk) => sum + chunk.length, 0);
    const result = new Uint8Array(totalLength);
    let offset = 0;

    for (const chunk of chunks) {
      result.set(chunk, offset);
      offset += chunk.length;
    }

    return result;

  } catch (error) {
    logger.error("voice-to-text", "Base64 processing error", error);
    throw new Error("Failed to decode audio data");
  }
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: { ...corsHeadersForRequest(req), 'Content-Type': 'application/json' } })
  }

  const auth = await secureEdgeRequest(req, "voice-to-text");
  if (auth instanceof Response) return auth;

  try {
    const { audio, language, format } = await req.json()

    if (!audio) {
      return jsonResponse({ error: 'No audio data provided.' }, 400, req);
    }

    const apiKey = Deno.env.get('OPENAI_API_KEY');
    if (!apiKey) {
      logger.error("voice-to-text", "OpenAI API key not configured");
      return jsonResponse({ error: 'Transcription service temporarily unavailable.' }, 503, req);
    }

    const binaryAudio = processBase64Safely(audio);

    const formData = new FormData()
    const audioMeta = resolveAudioMeta(format)
    const blob = new Blob([binaryAudio], { type: audioMeta.mimeType })
    formData.append('file', blob, audioMeta.fileName)
    formData.append('model', 'whisper-1')

    if (language && language !== 'auto') {
      formData.append('language', language)
    }

    const response = await fetch('https://api.openai.com/v1/audio/transcriptions', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${apiKey}`,
      },
      body: formData,
    })

    if (!response.ok) {
      const errorText = await response.text();
      logger.error("voice-to-text", `OpenAI API error: ${response.status}`, errorText.slice(0, 400));

      if (response.status === 429) {
        return jsonResponse({ error: 'Transcription service is busy. Please try again later.' }, 429, req);
      }

      if (response.status === 401) {
        return jsonResponse({ error: 'Transcription service temporarily unavailable.' }, 503, req);
      }

      if (response.status === 400) {
        return jsonResponse({ error: 'Invalid audio format or corrupted audio data.' }, 400, req);
      }

      return jsonResponse({ error: 'Transcription failed. Please try again.' }, 502, req);
    }

    const result = await response.json()

    return jsonResponse({
      text: result.text || '',
      language: result.language || 'unknown'
    }, 200, req);

  } catch (error) {
    return handleServerError(
      "voice-to-text",
      error,
      req,
      "Transcription failed. Please try again.",
    );
  }
})
