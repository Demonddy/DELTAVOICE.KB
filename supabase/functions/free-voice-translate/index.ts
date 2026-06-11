
import "https://deno.land/x/xhr@0.1.0/mod.ts";
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { corsHeaders, secureEdgeRequest } from "../_shared/security.ts";

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

// IMPORTANT: Replace with your actual Replit backend URL!
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
    return new Response(null, { headers: corsHeaders });
  }

  const auth = await secureEdgeRequest(req, "free-voice-translate");
  if (auth instanceof Response) return auth;

  try {
    const { audio, targetLanguage, format } = await req.json();

    if (!audio || !targetLanguage) {
      return new Response(
        JSON.stringify({ error: "Missing audio or target language" }),
        { status: 400, headers: corsHeaders }
      );
    }

    // Convert base64 to binary audio
    const binaryAudio = processBase64Chunks(audio);
    const formData = new FormData();
    const audioMeta = resolveAudioMeta(format);
    const audioBlob = new Blob([binaryAudio], { type: audioMeta.mimeType });
    formData.append('file', audioBlob, audioMeta.fileName);

    // Call self-hosted Whisper backend for transcription
    const whisperRes = await fetch(`${WHISPER_BACKEND_URL}/transcribe`, {
      method: 'POST',
      body: formData,
    });

    if (!whisperRes.ok) {
      const errorText = await whisperRes.text();
      return new Response(
        JSON.stringify({ error: "Transcription failed", details: errorText }),
        { status: 500, headers: corsHeaders }
      );
    }

    const whisperData = await whisperRes.json();
    const transcribedText = whisperData.text;

    if (!transcribedText || transcribedText.trim().length === 0) {
      return new Response(
        JSON.stringify({ error: "No speech detected in audio." }),
        { status: 400, headers: corsHeaders }
      );
    }

    // Translate the transcribed text via the existing translate-text function
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
      const errorText = await translationRes.text();
      return new Response(
        JSON.stringify({ error: "Translation failed", details: errorText }),
        { status: 500, headers: corsHeaders }
      );
    }

    const translationData = await translationRes.json();

    return new Response(
      JSON.stringify({
        originalText: transcribedText,
        translatedText: translationData.translatedText,
        detectedLanguage: "en", // Whisper doesn't detect language (yet)
      }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  } catch (error) {
    return new Response(
      JSON.stringify({
        error: error.message || "Unknown error in free-voice-translate function",
        timestamp: new Date().toISOString()
      }),
      { status: 500, headers: corsHeaders }
    );
  }
});
