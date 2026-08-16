/**
 * Hybrid voice workflow (Phase 1):
 * - text-only: ElevenLabs Scribe (+ DeepSeek translate when needed)
 * - voice-only: Scribe + translate + TTS with saved clone (clone_<id>)
 * - complete + same language: ElevenLabs Speech-to-Speech (preset voice)
 * - complete + new language: ElevenLabs Dubbing (library voice)
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

/** Scribe ISO-639-3 (or short) → app ISO-639-1 */
const SCRIBE_LANG_TO_ISO: Record<string, string> = {
  eng: "en",
  spa: "es",
  fra: "fr",
  fre: "fr",
  deu: "de",
  ger: "de",
  ita: "it",
  por: "pt",
  rus: "ru",
  jpn: "ja",
  kor: "ko",
  cmn: "zh",
  zho: "zh",
  ara: "ar",
  hin: "hi",
  nld: "nl",
  dut: "nl",
  pol: "pl",
  tur: "tr",
  swe: "sv",
  dan: "da",
  nor: "no",
  fin: "fi",
  heb: "he",
  tha: "th",
  vie: "vi",
  ukr: "uk",
  ces: "cs",
  cze: "cs",
  hun: "hu",
  ron: "ro",
  rum: "ro",
};

function resolveAudioMeta(format?: string) {
  const ext = (format || "webm").toLowerCase().replace(".", "");
  const safeExt = AUDIO_MIME_MAP[ext] ? ext : "webm";
  return {
    mimeType: AUDIO_MIME_MAP[safeExt] || "audio/webm",
    fileName: `audio.${safeExt}`,
  };
}

function getDeepSeekApiKey(): string {
  return process.env.DEEPSEEK_API || process.env.DEEPSEEKA || "";
}

function getOpenAIApiKey(): string {
  return process.env.OPENAI_API_KEY77 || process.env.OPENAI_API_KEY || "";
}

function getElevenLabsApiKey(): string {
  return (
    process.env.ELEVENLABS_API_KEY77 || process.env.ELEVENLABS_API_KEY || ""
  );
}

function decodeBase64Audio(audioBase64: string): Uint8Array {
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
  return binaryAudio;
}

function encodeBase64Audio(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = "";
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

function normalizeLangCode(code?: string): string {
  if (!code) return "";
  const lower = code.toLowerCase();
  if (SCRIBE_LANG_TO_ISO[lower]) return SCRIBE_LANG_TO_ISO[lower];
  if (LANGUAGE_CODE_MAP[lower]) return lower;
  return lower.length >= 2 ? lower.slice(0, 2) : lower;
}

function languagesMatch(detected?: string, target?: string): boolean {
  const d = normalizeLangCode(detected);
  const t = normalizeLangCode(target);
  if (!d || !t) return false;
  return d === t;
}

function resolveTargetLang(targetLanguage: string): string {
  return LANGUAGE_CODE_MAP[targetLanguage] || targetLanguage;
}

function resolvePresetVoiceId(voiceStyle: string): string {
  if (voiceStyle.startsWith("clone_")) {
    const savedId = voiceStyle.slice("clone_".length).trim();
    if (savedId) return savedId;
  }
  return ELEVENLABS_VOICE_MAP[voiceStyle] || ELEVENLABS_VOICE_MAP.aria;
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

interface ScribeResult {
  text: string;
  languageCode?: string;
}

async function transcribeWithScribe(
  audioBase64: string,
  format?: string,
): Promise<ScribeResult> {
  const elevenLabsApiKey = getElevenLabsApiKey();
  if (!elevenLabsApiKey) {
    throw new Error("ElevenLabs API key not configured");
  }

  const binaryAudio = decodeBase64Audio(audioBase64);
  const audioMeta = resolveAudioMeta(format);
  const formData = new FormData();
  formData.append(
    "file",
    new Blob([binaryAudio], { type: audioMeta.mimeType }),
    audioMeta.fileName,
  );
  formData.append("model_id", "scribe_v2");

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 120000);

  try {
    const response = await fetch("https://api.elevenlabs.io/v1/speech-to-text", {
      method: "POST",
      headers: { "xi-api-key": elevenLabsApiKey },
      body: formData,
      signal: controller.signal,
    });
    clearTimeout(timeoutId);

    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(`Transcription failed: ${response.status} - ${errorText}`);
    }

    const result = (await response.json()) as {
      text?: string;
      language_code?: string;
    };
    return {
      text: (result.text || "").trim(),
      languageCode: result.language_code,
    };
  } catch (err) {
    clearTimeout(timeoutId);
    if (err instanceof Error && err.name === "AbortError") {
      throw new Error(
        "Transcription timeout - audio too long or processing too slow",
      );
    }
    throw err;
  }
}

async function translateText(
  text: string,
  targetLanguage: string,
): Promise<string> {
  const targetLanguageName =
    LANGUAGE_NAMES[targetLanguage] || targetLanguage;
  const systemPrompt = `You are a professional translator. Translate the given text accurately to ${targetLanguageName}. Only return the translated text, nothing else.`;
  const messages = [
    { role: "system", content: systemPrompt },
    { role: "user", content: text },
  ];

  const deepSeekApiKey = getDeepSeekApiKey();
  const openAIApiKey = getOpenAIApiKey();
  const attempts: Array<{ url: string; key: string; model: string }> = [];

  if (deepSeekApiKey) {
    attempts.push({
      url: "https://api.deepseek.com/v1/chat/completions",
      key: deepSeekApiKey,
      model: "deepseek-chat",
    });
  }
  if (openAIApiKey) {
    attempts.push({
      url: "https://api.openai.com/v1/chat/completions",
      key: openAIApiKey,
      model: "gpt-4o-mini",
    });
  }

  if (attempts.length === 0) {
    throw new Error("Translation API key not configured");
  }

  let lastError = "Translation failed";
  for (const attempt of attempts) {
    const response = await fetch(attempt.url, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${attempt.key}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        model: attempt.model,
        messages,
        temperature: 0.3,
        max_tokens: 4000,
      }),
    });

    if (response.ok) {
      const result = (await response.json()) as {
        choices: Array<{ message: { content: string } }>;
      };
      return result.choices[0].message.content.trim();
    }
    lastError = await response.text();
    logger.error(
      "voiceWorkflow",
      `Translation failed (${attempt.model}): ${response.status}`,
      lastError.substring(0, 300),
    );
  }

  throw new Error(`Translation failed: ${lastError.substring(0, 200)}`);
}

async function speechToSpeech(
  audioBase64: string,
  voiceStyle: string,
  format?: string,
): Promise<string> {
  const elevenLabsApiKey = getElevenLabsApiKey();
  if (!elevenLabsApiKey) {
    throw new Error("ElevenLabs API key not configured");
  }

  const voiceId = resolvePresetVoiceId(voiceStyle);
  const binaryAudio = decodeBase64Audio(audioBase64);
  const audioMeta = resolveAudioMeta(format);
  const formData = new FormData();
  formData.append(
    "audio",
    new Blob([binaryAudio], { type: audioMeta.mimeType }),
    audioMeta.fileName,
  );
  formData.append("model_id", "eleven_multilingual_sts_v2");

  const url = `https://api.elevenlabs.io/v1/speech-to-speech/${voiceId}?output_format=mp3_44100_128`;
  const response = await fetch(url, {
    method: "POST",
    headers: { "xi-api-key": elevenLabsApiKey },
    body: formData,
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(
      `Speech-to-speech failed: ${response.status} - ${errorText.substring(0, 300)}`,
    );
  }

  return encodeBase64Audio(await response.arrayBuffer());
}

async function textToSpeech(
  text: string,
  voiceStyle: string,
  targetLanguage?: string,
): Promise<string> {
  const elevenLabsApiKey = getElevenLabsApiKey();
  if (!elevenLabsApiKey) {
    throw new Error("ElevenLabs API key not configured");
  }

  const voiceId = resolvePresetVoiceId(voiceStyle);
  const isClonedVoice = voiceStyle.startsWith("clone_");
  const langCode = targetLanguage
    ? resolveTargetLang(targetLanguage)
    : undefined;
  const voiceSettings = isClonedVoice
    ? { stability: 0.35, similarity_boost: 1.0, style: 0.0, use_speaker_boost: true }
    : { stability: 0.5, similarity_boost: 0.75, style: 0.0, use_speaker_boost: true };
  const modelsToTry = isClonedVoice
    ? ["eleven_multilingual_v2", "eleven_flash_v2_5"]
    : ["eleven_flash_v2_5", "eleven_multilingual_v2"];
  const latency = isClonedVoice ? 0 : 2;

  let lastError = "";
  for (const modelId of modelsToTry) {
    const ttsBody: Record<string, unknown> = {
      text,
      model_id: modelId,
      voice_settings: voiceSettings,
    };
    if (langCode) ttsBody.language_code = langCode;

    const response = await fetch(
      `https://api.elevenlabs.io/v1/text-to-speech/${voiceId}?optimize_streaming_latency=${latency}`,
      {
        method: "POST",
        headers: {
          Accept: "audio/mpeg",
          "Content-Type": "application/json",
          "xi-api-key": elevenLabsApiKey,
        },
        body: JSON.stringify(ttsBody),
      },
    );

    if (response.ok) {
      return encodeBase64Audio(await response.arrayBuffer());
    }
    lastError = await response.text();
    logger.error(
      "voiceWorkflow",
      `TTS ${modelId} failed: ${response.status}`,
      lastError.substring(0, 300),
    );
  }

  throw new Error(`Voice conversion failed: ${lastError.substring(0, 300)}`);
}

async function dubAudio(
  audioBase64: string,
  targetLanguage: string,
  format?: string,
  options?: { disableVoiceCloning?: boolean; sourceLang?: string },
): Promise<string> {
  const elevenLabsApiKey = getElevenLabsApiKey();
  if (!elevenLabsApiKey) {
    throw new Error("ElevenLabs API key not configured");
  }

  const targetLang = resolveTargetLang(targetLanguage);
  const binaryAudio = decodeBase64Audio(audioBase64);
  const audioMeta = resolveAudioMeta(format);
  const formData = new FormData();
  formData.append(
    "file",
    new Blob([binaryAudio], { type: audioMeta.mimeType }),
    audioMeta.fileName,
  );
  formData.append("target_lang", targetLang);
  formData.append(
    "disable_voice_cloning",
    String(options?.disableVoiceCloning ?? false),
  );
  formData.append("drop_background_audio", "true");
  formData.append("num_speakers", "1");
  if (options?.sourceLang) {
    formData.append("source_lang", options.sourceLang);
  }

  const createRes = await fetch("https://api.elevenlabs.io/v1/dubbing", {
    method: "POST",
    headers: { "xi-api-key": elevenLabsApiKey },
    body: formData,
  });

  if (!createRes.ok) {
    const errorText = await createRes.text();
    throw new Error(
      `Dubbing create failed: ${createRes.status} - ${errorText.substring(0, 300)}`,
    );
  }

  const created = (await createRes.json()) as { dubbing_id?: string };
  const dubbingId = created.dubbing_id;
  if (!dubbingId) {
    throw new Error("Dubbing did not return a dubbing_id");
  }

  const maxWaitMs = 110_000;
  const pollIntervalMs = 2500;
  const started = Date.now();

  while (Date.now() - started < maxWaitMs) {
    await sleep(pollIntervalMs);
    const statusRes = await fetch(
      `https://api.elevenlabs.io/v1/dubbing/${dubbingId}`,
      { headers: { "xi-api-key": elevenLabsApiKey } },
    );

    if (!statusRes.ok) {
      const t = await statusRes.text();
      logger.error("voiceWorkflow", `Dubbing status error: ${statusRes.status}`, t.substring(0, 200));
      continue;
    }

    const statusBody = (await statusRes.json()) as {
      status?: string;
      error?: string;
    };

    if (statusBody.status === "dubbed") {
      const audioRes = await fetch(
        `https://api.elevenlabs.io/v1/dubbing/${dubbingId}/audio/${targetLang}`,
        { headers: { "xi-api-key": elevenLabsApiKey } },
      );
      if (!audioRes.ok) {
        const t = await audioRes.text();
        throw new Error(
          `Dubbing audio fetch failed: ${audioRes.status} - ${t.substring(0, 200)}`,
        );
      }
      return encodeBase64Audio(await audioRes.arrayBuffer());
    }

    if (statusBody.status === "failed") {
      throw new Error(statusBody.error || "Dubbing failed");
    }
  }

  throw new Error("Dubbing timed out. Please try a shorter recording.");
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
  ttsError?: string;
}

/**
 * Hybrid routing:
 * - text-only: Scribe (+ DeepSeek translate when languages differ)
 * - voice-only: Scribe + translate + TTS with saved clone (clone_<id>)
 * - complete + same language: Speech-to-Speech (preset voice)
 * - complete + new language: Dubbing (library voice, cloning disabled)
 */
export async function runVoiceWorkflow(
  req: WorkflowRequest,
): Promise<WorkflowResult> {
  const { audioBase64, targetLanguage, voiceStyle, workflowType, format } =
    req;

  if (!audioBase64) {
    throw new Error("Audio data is required");
  }

  const scribe = await transcribeWithScribe(audioBase64, format);
  const originalText = scribe.text;
  if (!originalText) {
    throw new Error(
      "No speech detected in audio. Please speak clearly and try again.",
    );
  }

  const detectedLang = scribe.languageCode;
  const targetLang = targetLanguage?.trim() || "";
  const needsTranslation =
    Boolean(targetLang) && !languagesMatch(detectedLang, targetLang);

  let translatedText = originalText;
  let convertedAudioBase64 = "";

  if (workflowType === "text-only") {
    if (needsTranslation) {
      translatedText = await translateText(originalText, targetLang);
    }
    return {
      success: true,
      originalText,
      translatedText,
      convertedAudioBase64: "",
      targetLanguage: targetLang,
      voiceStyle: voiceStyle || "",
      workflowType,
    };
  }

  try {
    if (workflowType === "voice-only") {
      if (!voiceStyle.startsWith("clone_")) {
        throw new Error(
          "Saved voice clone is required. Please record or select your voice sample first.",
        );
      }
      if (needsTranslation) {
        translatedText = await translateText(originalText, targetLang);
      }
      convertedAudioBase64 = await textToSpeech(
        translatedText,
        voiceStyle,
        targetLang,
      );
    } else if (workflowType === "complete") {
      if (needsTranslation) {
        convertedAudioBase64 = await dubAudio(audioBase64, targetLang, format, {
          disableVoiceCloning: true,
          sourceLang: detectedLang,
        });
        translatedText = await translateText(originalText, targetLang);
      } else {
        convertedAudioBase64 = await speechToSpeech(
          audioBase64,
          voiceStyle,
          format,
        );
        translatedText = originalText;
      }
    } else {
      throw new Error(
        `Unsupported workflowType: ${workflowType}. Use 'complete', 'voice-only', or 'text-only'.`,
      );
    }
  } catch (audioError) {
    const err =
      audioError instanceof Error ? audioError : new Error(String(audioError));
    const msg = err.message || "";
    logger.error("voiceWorkflow", "Audio pipeline error", msg);

    const isAudioPipelineError =
      msg.includes("Speech-to-speech") ||
      msg.includes("Dubbing") ||
      msg.includes("Voice conversion failed") ||
      msg.includes("ElevenLabs");

    if (isAudioPipelineError) {
      let fallbackText = originalText;
      if (needsTranslation) {
        try {
          fallbackText = await translateText(originalText, targetLang);
        } catch {
          fallbackText = originalText;
        }
      }
      return {
        success: true,
        originalText,
        translatedText: fallbackText,
        convertedAudioBase64: "",
        targetLanguage: targetLang,
        voiceStyle,
        workflowType,
        ttsFallback: true,
        ttsError: msg.substring(0, 300),
      };
    }
    throw audioError;
  }

  return {
    success: true,
    originalText,
    translatedText,
    convertedAudioBase64,
    targetLanguage: targetLang,
    voiceStyle,
    workflowType,
  };
}
