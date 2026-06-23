
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

const WHISPER_BACKEND_URL = "https://your-repl-name.replit.dev";

function processBase64Chunks(base64String: string, chunkSize = 32768) {
  const chunks: Uint8Array[] = [];
  let position = 0;
  while (position < base64String.length) {
    const chunk = base64String.slice(position, position + chunkSize);
    const binaryChunk = atob(chunk);
    const bytes = new Uint8Array(binaryChunk.length);
    for (let i = 0; i < binaryChunk.length; i++) {
      bytes[i] = binaryChunk.charCodeAt(i);
    }
    chunks.push(bytes);
    position += chunkSize;
  }
  const totalLength = chunks.reduce((acc, chunk) => acc + chunk.length, 0);
  const result = new Uint8Array(totalLength);
  let offset = 0;
  for (const chunk of chunks) {
    result.set(chunk, offset);
    offset += chunk.length;
  }
  return result;
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response(null, { headers: { ...corsHeadersForRequest(req), 'Content-Type': 'application/json' } });
  }

  const auth = await secureEdgeRequest(req, "free-voice-translate");
  if (auth instanceof Response) return auth;

  try {
    const { audio, targetLanguage, format } = await req.json();

    if (!audio || !targetLanguage) {
      return jsonResponse({ error: "Audio and target language are required." }, 400, req);
    }

    const binaryAudio = processBase64Chunks(audio);
    const formData = new FormData();
    const audioMeta = resolveAudioMeta(format);
    const audioBlob = new Blob([binaryAudio], { type: audioMeta.mimeType });
    formData.append('file', audioBlob, audioMeta.fileName);

    const whisperRes = await fetch(`${WHISPER_BACKEND_URL}/transcribe`, {
      method: 'POST',
      body: formData,
    });

    if (!whisperRes.ok) {
      const errorText = await whisperRes.text();
      logger.error("free-voice-translate", "Transcription failed", errorText.slice(0, 400));
      return jsonResponse({ error: "Transcription failed. Please try again." }, 500, req);
    }

    const whisperData = await whisperRes.json();
    const transcribedText = whisperData.text;

    if (!transcribedText || transcribedText.trim().length === 0) {
      return jsonResponse({ error: "No speech detected in audio." }, 400, req);
    }

    const translationRes = await fetch(
      `${req.url.split("/functions/v1/")[0]}/functions/v1/translate-text`,
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": req.headers.get("Authorization") || "",
          "apikey": req.headers.get("apikey") || "",
        },
        body: JSON.stringify({
          text: transcribedText,
          targetLanguage,
        }),
      }
    );

    if (!translationRes.ok) {
      logger.error("free-voice-translate", `Translation step failed: ${translationRes.status}`);
      return jsonResponse({ error: "Translation failed. Please try again." }, 500, req);
    }

    const translationData = await translationRes.json();

    return jsonResponse({
      originalText: transcribedText,
      translatedText: translationData.translatedText,
      detectedLanguage: "en",
    }, 200, req);
  } catch (error) {
    return handleServerError(
      "free-voice-translate",
      error,
      req,
      "Voice translation failed. Please try again.",
    );
  }
});
