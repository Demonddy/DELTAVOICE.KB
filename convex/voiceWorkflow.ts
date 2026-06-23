/**
 * Voice Workflow Logic - Complete (Change Language & Voice) and Voice-only (Translate My Same Voice)
 * Transferred from Supabase Edge Functions for Convex real-time functionality.
 * Only handles workflowType: 'complete' | 'voice-only'
 */

import { logger } from "./logger";

const DEFAULT_ALLOWED_ORIGINS = [
  "https://deltavoice.com",
  "https://www.deltavoice.com",
];
const ALLOWED_ORIGINS_RAW =
  process.env.ALLOWED_ORIGINS || DEFAULT_ALLOWED_ORIGINS.join(",");
const parsedOrigins = ALLOWED_ORIGINS_RAW.split(",")
  .map((s: string) => s.trim())
  .filter(Boolean);
const ALLOWED_ORIGINS =
  ALLOWED_ORIGINS_RAW === "*" || parsedOrigins.length === 0
    ? DEFAULT_ALLOWED_ORIGINS
    : parsedOrigins;

function getCorsOrigin(request?: Request): string {
  const origin = request?.headers.get("origin") || "";
  if (!origin) return "";
  return ALLOWED_ORIGINS.includes(origin) ? origin : "";
}

/** Native clients omit Origin; only reject when a disallowed Origin is present. */
export function rejectDisallowedOrigin(request: Request): Response | null {
  const origin = request.headers.get("origin");
  if (!origin) return null;
  if (!ALLOWED_ORIGINS.includes(origin)) {
    return new Response(
      JSON.stringify({ error: "Origin not allowed.", code: "ORIGIN_NOT_ALLOWED" }),
      {
        status: 403,
        headers: {
          ...corsHeadersForRequest(request),
          "Content-Type": "application/json",
        },
      },
    );
  }
  return null;
}

export function corsHeadersForRequest(request: Request): Record<string, string> {
  return {
    "Access-Control-Allow-Origin": getCorsOrigin(request),
    "Access-Control-Allow-Headers":
      "authorization, x-client-info, apikey, content-type",
  };
}

const CORS_HEADERS: Record<string, string> = {
  "Access-Control-Allow-Origin": "",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
};

const AUDIO_MIME_MAP: Record<string, string> = {
  m4a: "audio/mp4",
  mp4: "audio/mp4",
  mp3: "audio/mpeg",
  wav: "audio/wav",
  webm: "audio/webm",
  ogg: "audio/ogg",
  oga: "audio/ogg",
  flac: "audio/flac",
  aac: "audio/aac",
  caf: "audio/x-caf",
  "3gp": "audio/3gpp",
};

const ELEVENLABS_VOICE_MAP: Record<string, string> = {
  aria: "9BWtsMINqrJLrRacOk9x",
  roger: "CwhRBWXzGAHq8TQ4Fs17",
  sarah: "EXAVITQu4vr4xnSDxMaL",
  laura: "FGY2WhTYpPnrIDTdsKH5",
  charlie: "IKne3meq5aSn9XLyUdCD",
  george: "JBFqnCBsd6RMkjVDRZzb",
  liam: "TX3LPaxmHKxFdv7VOQHJ",
  charlotte: "XB0fDUnXU5powFXDhCwa",
  alice: "Xb7hH8MSUJpSbSDYk0k2",
  matilda: "XrExE9yKIg1WjnnlVkGX",
  adam: "pNInz6obpgDQGcFmaJgB",
  bill: "pqHfZKP75CvOlQylNhV4",
  carter: "EXAVITQu4vr4xnSDxMaL",
  daniel: "onwK4e9ZLuTAKqWW03F9",
  cassidy: "cgSgspJ2msm6clMCkdW9",
  jessica: "cgSgspJ2msm6clMCkdW9",
  lily: "pFZP5JQG7iQjIQuC4Bku",
};

const LANGUAGE_NAMES: Record<string, string> = {
  en: "English",
  es: "Spanish",
  fr: "French",
  de: "German",
  it: "Italian",
  pt: "Portuguese",
  ru: "Russian",
  ja: "Japanese",
  ko: "Korean",
  zh: "Chinese",
  ar: "Arabic",
  hi: "Hindi",
  nl: "Dutch",
  pl: "Polish",
  tr: "Turkish",
  sv: "Swedish",
  da: "Danish",
  no: "Norwegian",
  fi: "Finnish",
  he: "Hebrew",
  th: "Thai",
  vi: "Vietnamese",
  uk: "Ukrainian",
  cs: "Czech",
  hu: "Hungarian",
  ro: "Romanian",
};

const LANGUAGE_CODE_MAP: Record<string, string> = {
  en: "en",
  es: "es",
  fr: "fr",
  de: "de",
  it: "it",
  pt: "pt",
  ru: "ru",
  ja: "ja",
  ko: "ko",
  zh: "zh",
  ar: "ar",
  hi: "hi",
  nl: "nl",
  pl: "pl",
  tr: "tr",
  sv: "sv",
  da: "da",
  no: "no",
  fi: "fi",
  he: "he",
  th: "th",
  vi: "vi",
  uk: "uk",
};

function resolveAudioMeta(format?: string) {
  const ext = (format || "webm").toLowerCase().replace(".", "");
  const safeExt = AUDIO_MIME_MAP[ext] ? ext : "webm";
  return {
    mimeType: AUDIO_MIME_MAP[safeExt] || "audio/webm",
    fileName: `audio.${safeExt}`,
  };
}

function getOpenAIApiKey(): string {
  return (
    process.env.OPENAI_API_KEY77 || process.env.OPENAI_API_KEY || ""
  );
}

function getElevenLabsApiKey(): string {
  return (
    process.env.ELEVENLABS_API_KEY77 || process.env.ELEVENLABS_API_KEY || ""
  );
}

async function transcribeAudio(
  audioBase64: string,
  format?: string
): Promise<string> {
  const openAIApiKey = getOpenAIApiKey();
  if (!openAIApiKey) {
    throw new Error("OpenAI API key not configured");
  }

  const chunkSize = 32768;
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

  const formData = new FormData();
  const audioMeta = resolveAudioMeta(format);
  const blob = new Blob([binaryAudio], { type: audioMeta.mimeType });
  formData.append("file", blob, audioMeta.fileName);
  formData.append("model", "whisper-1");

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 120000);

  try {
    const response = await fetch(
      "https://api.openai.com/v1/audio/transcriptions",
      {
        method: "POST",
        headers: { Authorization: `Bearer ${openAIApiKey}` },
        body: formData,
        signal: controller.signal,
      }
    );
    clearTimeout(timeoutId);

    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(`Transcription failed: ${response.status} - ${errorText}`);
    }
    const result = (await response.json()) as { text: string };
    return result.text;
  } catch (err) {
    clearTimeout(timeoutId);
    if (err instanceof Error && err.name === "AbortError") {
      throw new Error(
        "Transcription timeout - video too long or processing too slow"
      );
    }
    throw err;
  }
}

async function translateText(
  text: string,
  targetLanguage: string
): Promise<string> {
  const openAIApiKey = getOpenAIApiKey();
  if (!openAIApiKey) {
    throw new Error("OpenAI API key not configured");
  }

  const targetLanguageName =
    LANGUAGE_NAMES[targetLanguage] || targetLanguage;

  const response = await fetch("https://api.openai.com/v1/chat/completions", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${openAIApiKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      model: "gpt-4o-mini",
      messages: [
        {
          role: "system",
          content: `You are a professional translator. Translate the given text accurately to ${targetLanguageName}. Only return the translated text, nothing else.`,
        },
        { role: "user", content: text },
      ],
      temperature: 0.3,
    }),
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Translation failed: ${response.status} - ${errorText}`);
  }

  const result = (await response.json()) as {
    choices: Array<{ message: { content: string } }>;
  };
  return result.choices[0].message.content.trim();
}

async function createVoiceCloneFromAudio(
  audioBase64: string,
  name: string,
  format?: string
): Promise<string> {
  const elevenLabsApiKey = getElevenLabsApiKey();
  if (!elevenLabsApiKey) {
    throw new Error("ElevenLabs API key not configured");
  }

  const audioBuffer = Uint8Array.from(atob(audioBase64), (c) =>
    c.charCodeAt(0)
  );
  const audioMeta = resolveAudioMeta(format);
  const audioBlob = new Blob([audioBuffer], { type: audioMeta.mimeType });

  const formData = new FormData();
  formData.append("name", name);
  formData.append("description", `Auto-generated voice clone: ${name}`);
  formData.append("files", audioBlob, audioMeta.fileName);

  const response = await fetch("https://api.elevenlabs.io/v1/voices/add", {
    method: "POST",
    headers: { "xi-api-key": elevenLabsApiKey },
    body: formData,
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(
      `Voice clone creation failed: ${response.status} - ${errorText}`
    );
  }

  const result = (await response.json()) as { voice_id: string };
  return result.voice_id;
}

async function convertTextToSpeech(
  text: string,
  voiceStyle: string,
  audioBase64?: string,
  audioFormat?: string,
  targetLanguage?: string
): Promise<string> {
  const elevenLabsApiKey = getElevenLabsApiKey();
  if (!elevenLabsApiKey) {
    throw new Error("ElevenLabs API key not configured");
  }

  let voiceId: string;

  if (voiceStyle === "myvoiceclone") {
    if (!audioBase64 || audioBase64.length < 2000) {
      throw new Error(
        "Voice clone requires a valid recording. Please record a longer voice sample and try again."
      );
    }
    const cloneName = `Live Voice Clone ${Date.now()}`;
    voiceId = await createVoiceCloneFromAudio(
      audioBase64,
      cloneName,
      audioFormat
    );
  } else if (voiceStyle.startsWith("clone_")) {
    voiceId = "9BWtsMINqrJLrRacOk9x";
  } else {
    voiceId =
      ELEVENLABS_VOICE_MAP[voiceStyle] || "9BWtsMINqrJLrRacOk9x";
  }

  const isClonedVoice =
    voiceStyle === "myvoiceclone" || voiceStyle.startsWith("clone_");
  const langCode = targetLanguage
    ? LANGUAGE_CODE_MAP[targetLanguage] || targetLanguage
    : undefined;
  const ttsUrl = `https://api.elevenlabs.io/v1/text-to-speech/${voiceId}?optimize_streaming_latency=${isClonedVoice ? 0 : 2}`;
  const voiceSettings = isClonedVoice
    ? {
        stability: 0.35,
        similarity_boost: 1.0,
        style: 0.0,
        use_speaker_boost: true,
      }
    : {
        stability: 0.5,
        similarity_boost: 0.75,
        style: 0.0,
        use_speaker_boost: true,
      };
  const modelsToTry = isClonedVoice
    ? ["eleven_multilingual_v2", "eleven_flash_v2_5"]
    : ["eleven_flash_v2_5", "eleven_multilingual_v2"];

  let response: Response | null = null;
  let lastError = "";

  async function trySynthesize(includeLanguageCode: boolean): Promise<Response | null> {
    let res: Response | null = null;
    for (const modelId of modelsToTry) {
      const ttsBody: Record<string, unknown> = {
        text,
        model_id: modelId,
        voice_settings: voiceSettings,
      };
      if (includeLanguageCode && langCode) {
        ttsBody.language_code = langCode;
      }

      res = await fetch(ttsUrl, {
        method: "POST",
        headers: {
          Accept: "audio/mpeg",
          "Content-Type": "application/json",
          "xi-api-key": elevenLabsApiKey,
        },
        body: JSON.stringify(ttsBody),
      });
      if (res.ok) {
        return res;
      }
      lastError = await res.text();
      logger.error("voiceWorkflow", `ElevenLabs TTS failed: model=${modelId}, status=${res.status}`, lastError.substring(0, 300));
    }
    return null;
  }

  // Prefer language_code when API accepts it; many failures are fixed by omitting it.
  response = await trySynthesize(true);
  if (!response || !response.ok) {
    response = await trySynthesize(false);
  }

  if (!response || !response.ok) {
    throw new Error(
      `Voice conversion failed: ${response?.status || "unknown"} - ${lastError}`
    );
  }

  const audioBuffer = await response.arrayBuffer();
  const bytes = new Uint8Array(audioBuffer);
  let binary = "";
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  const base64Audio = btoa(binary);
  return base64Audio;
}

export interface WorkflowRequest {
  audioBase64: string;
  targetLanguage: string;
  voiceStyle: string;
  workflowType: "complete" | "voice-only" | "text-only";
  format?: string;
}

export interface WorkflowResult {
  success: boolean;
  originalText: string;
  translatedText: string;
  convertedAudioBase64: string;
  targetLanguage: string;
  voiceStyle: string;
  workflowType: string;
  ttsFallback?: boolean;
}

/**
 * Runs complete, voice-only, or text-only workflow.
 * - complete: Preset voice (Adam, Aria, etc.) from voiceStyle, optional translation, TTS
 * - voice-only: Always myvoiceclone from audioBase64, optional translation, TTS with clone
 * - text-only: Transcribe + optional translation only (no ElevenLabs)
 */
export async function runVoiceWorkflow(
  req: WorkflowRequest
): Promise<WorkflowResult> {
  const {
    audioBase64,
    targetLanguage,
    voiceStyle,
    workflowType,
    format,
  } = req;

  if (!audioBase64) {
    throw new Error("Audio data is required");
  }

  const originalText = await transcribeAudio(audioBase64, format);
  if (!originalText || originalText.trim().length === 0) {
    throw new Error(
      "No speech detected in audio. Please speak clearly and try again."
    );
  }

  const shouldTranslate =
    targetLanguage && targetLanguage.trim().length > 0;
  // Must default so the T catch block never reads an unassigned variable (TDZ)
  // when translateText throws before assignment.
  let translatedText = "";
  let convertedAudioBase64 = "";

  if (workflowType === "text-only") {
    translatedText = shouldTranslate
      ? await translateText(originalText, targetLanguage)
      : originalText;
    return {
      success: true,
      originalText,
      translatedText,
      convertedAudioBase64: "",
      targetLanguage: targetLanguage || "",
      voiceStyle: voiceStyle || "",
      workflowType,
    };
  }

  try {
    if (workflowType === "complete") {
      translatedText = shouldTranslate
        ? await translateText(originalText, targetLanguage)
        : originalText;
      convertedAudioBase64 = await convertTextToSpeech(
        translatedText,
        voiceStyle,
        undefined,
        format,
        targetLanguage
      );
    } else if (workflowType === "voice-only") {
      translatedText = shouldTranslate
        ? await translateText(originalText, targetLanguage)
        : originalText;
      convertedAudioBase64 = await convertTextToSpeech(
        translatedText,
        "myvoiceclone",
        audioBase64,
        format,
        targetLanguage
      );
    } else {
      throw new Error(
        `Unsupported workflowType: ${workflowType}. Use 'complete', 'voice-only', or 'text-only'.`
      );
    }
  } catch (ttsError) {
    const err =
      ttsError instanceof Error ? ttsError : new Error(String(ttsError));
    const msg = err.message || "";
    logger.error("voiceWorkflow", "TTS error", msg);
    const isTtsError =
      msg.includes("Voice conversion failed") ||
      msg.includes("Voice clone creation failed") ||
      msg.includes("Voice clone requires") ||
      msg.includes("text-to-speech") ||
      msg.includes("ElevenLabs") ||
      msg.includes("voice_id");
    const hasText =
      Boolean(originalText?.trim()) || Boolean(translatedText?.trim());

    if (isTtsError && hasText) {
      return {
        success: true,
        originalText,
        translatedText: translatedText || originalText || "",
        convertedAudioBase64: "",
        targetLanguage: targetLanguage || "",
        voiceStyle: workflowType === "voice-only" ? "myvoiceclone" : voiceStyle,
        workflowType,
        ttsFallback: true,
        ttsError: msg.substring(0, 300),
      } as WorkflowResult;
    }
    throw ttsError;
  }

  return {
    success: true,
    originalText,
    translatedText,
    convertedAudioBase64,
    targetLanguage: targetLanguage || "",
    voiceStyle: workflowType === "voice-only" ? "myvoiceclone" : voiceStyle,
    workflowType,
  };
}

export { CORS_HEADERS, corsHeadersForRequest };
