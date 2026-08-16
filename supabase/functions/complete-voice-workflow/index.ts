import "https://deno.land/x/xhr@0.1.0/mod.ts";
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { corsHeadersForRequest, jsonResponse, logger, secureEdgeRequest } from "../_shared/security.ts";

const audioMimeMap: Record<string, string> = {
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

const elevenLabsVoiceMap: Record<string, string> = {
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

const languageNames: Record<string, string> = {
  en: "English", es: "Spanish", fr: "French", de: "German", it: "Italian",
  pt: "Portuguese", ru: "Russian", ja: "Japanese", ko: "Korean", zh: "Chinese",
  ar: "Arabic", hi: "Hindi", nl: "Dutch", pl: "Polish", tr: "Turkish",
  sv: "Swedish", da: "Danish", no: "Norwegian", fi: "Finnish", he: "Hebrew",
  th: "Thai", vi: "Vietnamese", uk: "Ukrainian", cs: "Czech", hu: "Hungarian",
  ro: "Romanian",
};

const languageCodeMap: Record<string, string> = {
  en: "en", es: "es", fr: "fr", de: "de", it: "it", pt: "pt", ru: "ru",
  ja: "ja", ko: "ko", zh: "zh", ar: "ar", hi: "hi", nl: "nl", pl: "pl",
  tr: "tr", sv: "sv", da: "da", no: "no", fi: "fi", he: "he", th: "th",
  vi: "vi", uk: "uk",
};

const scribeLangToIso: Record<string, string> = {
  eng: "en", spa: "es", fra: "fr", fre: "fr", deu: "de", ger: "de",
  ita: "it", por: "pt", rus: "ru", jpn: "ja", kor: "ko", cmn: "zh",
  zho: "zh", ara: "ar", hin: "hi", nld: "nl", dut: "nl", pol: "pl",
  tur: "tr", swe: "sv", dan: "da", nor: "no", fin: "fi", heb: "he",
  tha: "th", vie: "vi", ukr: "uk", ces: "cs", cze: "cs", hun: "hu",
  ron: "ro", rum: "ro",
};

function resolveAudioMeta(format?: string) {
  const ext = (format || "webm").toLowerCase().replace(".", "");
  const safeExt = audioMimeMap[ext] ? ext : "webm";
  return {
    mimeType: audioMimeMap[safeExt] || "audio/webm",
    fileName: `audio.${safeExt}`,
  };
}

function getDeepSeekApiKey(): string {
  return Deno.env.get("DEEPSEEK_API") || Deno.env.get("DEEPSEEKA") || "";
}

function getOpenAIApiKey(): string {
  return Deno.env.get("OPENAI_API_KEY77") || Deno.env.get("OPENAI_API_KEY") || "";
}

function getElevenLabsApiKey(): string {
  return Deno.env.get("ELEVENLABS_API_KEY77") || Deno.env.get("ELEVENLABS_API_KEY") || "";
}

function decodeBase64Audio(audioBase64: string): Uint8Array {
  const chunkSize = 32768;
  const chunks: Uint8Array[] = [];
  let position = 0;
  while (position < audioBase64.length) {
    const chunk = audioBase64.slice(position, position + chunkSize);
    const binaryChunk = atob(chunk);
    const bytes = new Uint8Array(binaryChunk.length);
    for (let i = 0; i < binaryChunk.length; i++) bytes[i] = binaryChunk.charCodeAt(i);
    chunks.push(bytes);
    position += chunkSize;
  }
  const totalLength = chunks.reduce((acc, c) => acc + c.length, 0);
  const out = new Uint8Array(totalLength);
  let offset = 0;
  for (const c of chunks) {
    out.set(c, offset);
    offset += c.length;
  }
  return out;
}

function encodeBase64Audio(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = "";
  for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i]);
  return btoa(binary);
}

function normalizeLangCode(code?: string): string {
  if (!code) return "";
  const lower = code.toLowerCase();
  if (scribeLangToIso[lower]) return scribeLangToIso[lower];
  if (languageCodeMap[lower]) return lower;
  return lower.length >= 2 ? lower.slice(0, 2) : lower;
}

function languagesMatch(detected?: string, target?: string): boolean {
  const d = normalizeLangCode(detected);
  const t = normalizeLangCode(target);
  return Boolean(d && t && d === t);
}

function resolveTargetLang(targetLanguage: string): string {
  return languageCodeMap[targetLanguage] || targetLanguage;
}

function resolvePresetVoiceId(voiceStyle: string): string {
  if (voiceStyle.startsWith("clone_")) {
    const savedId = voiceStyle.slice("clone_".length).trim();
    if (savedId) return savedId;
  }
  return elevenLabsVoiceMap[voiceStyle] || elevenLabsVoiceMap.aria;
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function transcribeWithScribe(
  audioBase64: string,
  format?: string,
): Promise<{ text: string; languageCode?: string }> {
  const key = getElevenLabsApiKey();
  if (!key) throw new Error("ElevenLabs API key not configured");

  const binary = decodeBase64Audio(audioBase64);
  const meta = resolveAudioMeta(format);
  const form = new FormData();
  form.append("file", new Blob([binary], { type: meta.mimeType }), meta.fileName);
  form.append("model_id", "scribe_v2");

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), 120000);
  try {
    const res = await fetch("https://api.elevenlabs.io/v1/speech-to-text", {
      method: "POST",
      headers: { "xi-api-key": key },
      body: form,
      signal: controller.signal,
    });
    clearTimeout(timeoutId);
    if (!res.ok) {
      const t = await res.text();
      logger.error("complete-voice-workflow", `Scribe error: ${res.status}`, t.slice(0, 300));
      throw new Error("TRANSCRIPTION_FAILED");
    }
    const body = await res.json();
    return { text: (body.text || "").trim(), languageCode: body.language_code };
  } catch (e) {
    clearTimeout(timeoutId);
    if (e instanceof Error && e.name === "AbortError") throw new Error("TRANSCRIPTION_TIMEOUT");
    throw e;
  }
}

async function translateText(text: string, targetLanguage: string): Promise<string> {
  const targetLanguageName = languageNames[targetLanguage] || targetLanguage;
  const messages = [
    {
      role: "system",
      content: `You are a professional translator. Translate the given text accurately to ${targetLanguageName}. Only return the translated text, nothing else.`,
    },
    { role: "user", content: text },
  ];

  const attempts: Array<{ url: string; key: string; model: string }> = [];
  const deepSeek = getDeepSeekApiKey();
  const openAI = getOpenAIApiKey();
  if (deepSeek) {
    attempts.push({
      url: "https://api.deepseek.com/v1/chat/completions",
      key: deepSeek,
      model: "deepseek-chat",
    });
  }
  if (openAI) {
    attempts.push({
      url: "https://api.openai.com/v1/chat/completions",
      key: openAI,
      model: "gpt-4o-mini",
    });
  }
  if (attempts.length === 0) throw new Error("TRANSLATION_FAILED");

  for (const attempt of attempts) {
    const res = await fetch(attempt.url, {
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
    if (res.ok) {
      const result = await res.json();
      return result.choices[0].message.content.trim();
    }
    logger.error("complete-voice-workflow", `Translation error (${attempt.model}): ${res.status}`);
  }
  throw new Error("TRANSLATION_FAILED");
}

async function speechToSpeech(
  audioBase64: string,
  voiceStyle: string,
  format?: string,
): Promise<string> {
  const key = getElevenLabsApiKey();
  const voiceId = resolvePresetVoiceId(voiceStyle);
  const binary = decodeBase64Audio(audioBase64);
  const meta = resolveAudioMeta(format);
  const form = new FormData();
  form.append("audio", new Blob([binary], { type: meta.mimeType }), meta.fileName);
  form.append("model_id", "eleven_multilingual_sts_v2");

  const res = await fetch(
    `https://api.elevenlabs.io/v1/speech-to-speech/${voiceId}?output_format=mp3_44100_128`,
    { method: "POST", headers: { "xi-api-key": key }, body: form },
  );
  if (!res.ok) {
    logger.error("complete-voice-workflow", `STS error: ${res.status}`, (await res.text()).slice(0, 300));
    throw new Error("VOICE_CONVERSION_FAILED");
  }
  return encodeBase64Audio(await res.arrayBuffer());
}

async function textToSpeech(
  text: string,
  voiceStyle: string,
  targetLanguage?: string,
): Promise<string> {
  const key = getElevenLabsApiKey();
  const voiceId = resolvePresetVoiceId(voiceStyle);
  const isClonedVoice = voiceStyle.startsWith("clone_");
  const langCode = targetLanguage ? resolveTargetLang(targetLanguage) : undefined;
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

    const res = await fetch(
      `https://api.elevenlabs.io/v1/text-to-speech/${voiceId}?optimize_streaming_latency=${latency}`,
      {
        method: "POST",
        headers: {
          Accept: "audio/mpeg",
          "Content-Type": "application/json",
          "xi-api-key": key,
        },
        body: JSON.stringify(ttsBody),
      },
    );
    if (res.ok) return encodeBase64Audio(await res.arrayBuffer());
    lastError = await res.text();
    logger.error("complete-voice-workflow", `TTS ${modelId} failed: ${res.status}`, lastError.slice(0, 300));
  }
  throw new Error("VOICE_CONVERSION_FAILED");
}

async function dubAudio(
  audioBase64: string,
  targetLanguage: string,
  format?: string,
  options?: { disableVoiceCloning?: boolean; sourceLang?: string },
): Promise<string> {
  const key = getElevenLabsApiKey();
  const targetLang = resolveTargetLang(targetLanguage);
  const binary = decodeBase64Audio(audioBase64);
  const meta = resolveAudioMeta(format);
  const form = new FormData();
  form.append("file", new Blob([binary], { type: meta.mimeType }), meta.fileName);
  form.append("target_lang", targetLang);
  form.append("disable_voice_cloning", String(options?.disableVoiceCloning ?? false));
  form.append("drop_background_audio", "true");
  form.append("num_speakers", "1");
  if (options?.sourceLang) form.append("source_lang", options.sourceLang);

  const createRes = await fetch("https://api.elevenlabs.io/v1/dubbing", {
    method: "POST",
    headers: { "xi-api-key": key },
    body: form,
  });
  if (!createRes.ok) {
    logger.error("complete-voice-workflow", `Dubbing create error: ${createRes.status}`);
    throw new Error("VOICE_CONVERSION_FAILED");
  }
  const created = await createRes.json();
  const dubbingId = created.dubbing_id;
  if (!dubbingId) throw new Error("VOICE_CONVERSION_FAILED");

  const started = Date.now();
  while (Date.now() - started < 110_000) {
    await sleep(2500);
    const statusRes = await fetch(`https://api.elevenlabs.io/v1/dubbing/${dubbingId}`, {
      headers: { "xi-api-key": key },
    });
    if (!statusRes.ok) continue;
    const statusBody = await statusRes.json();
    if (statusBody.status === "dubbed") {
      const audioRes = await fetch(
        `https://api.elevenlabs.io/v1/dubbing/${dubbingId}/audio/${targetLang}`,
        { headers: { "xi-api-key": key } },
      );
      if (!audioRes.ok) throw new Error("VOICE_CONVERSION_FAILED");
      return encodeBase64Audio(await audioRes.arrayBuffer());
    }
    if (statusBody.status === "failed") throw new Error("VOICE_CONVERSION_FAILED");
  }
  throw new Error("TRANSCRIPTION_TIMEOUT");
}

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response(null, {
      headers: { ...corsHeadersForRequest(req), "Content-Type": "application/json" },
    });
  }

  const auth = await secureEdgeRequest(req, "complete-voice-workflow");
  if (auth instanceof Response) return auth;

  let originalText = "";
  let translatedText = "";
  let convertedAudioBase64 = "";
  let workflowType = "complete";
  let targetLanguage = "";
  let voiceStyle = "";

  try {
    const clonedReq = req.clone();
    const peekBody = await clonedReq.json().catch(() => ({}));
    if (peekBody.workflowType === "voice-only" && !auth.isPremium) {
      return new Response(
        JSON.stringify({
          error: "Premium subscription required for voice cloning.",
          code: "PREMIUM_REQUIRED",
        }),
        {
          status: 403,
          headers: { ...corsHeadersForRequest(req), "Content-Type": "application/json" },
        },
      );
    }

    const elevenLabsApiKey = getElevenLabsApiKey();

    if (!elevenLabsApiKey) {
      logger.error("complete-voice-workflow", "ElevenLabs API key not configured");
      return jsonResponse(
        {
          error: "Voice processing is temporarily unavailable. Please try again later.",
          code: "SERVICE_UNAVAILABLE",
        },
        500,
        req,
      );
    }

    const reqBody = await req.json();
    const { audioBase64, format } = reqBody;
    targetLanguage =
      typeof reqBody.targetLanguage === "string" && reqBody.targetLanguage.trim()
        ? reqBody.targetLanguage.trim()
        : "en";
    voiceStyle =
      typeof reqBody.voiceStyle === "string" && reqBody.voiceStyle.trim()
        ? reqBody.voiceStyle.trim().toLowerCase()
        : "aria";
    workflowType = reqBody.workflowType || "complete";

    if (!audioBase64) {
      return jsonResponse({ success: false, error: "Audio data is required" }, 400, req);
    }

    if (audioBase64.length > 14_000_000) {
      return jsonResponse(
        { success: false, error: "Audio file too large. Maximum size is 10 MB." },
        413,
        req,
      );
    }

    const scribe = await transcribeWithScribe(audioBase64, format);
    originalText = scribe.text;
    if (!originalText) {
      throw new Error("No speech detected in audio. Please speak clearly and try again.");
    }

    const needsTranslation =
      Boolean(targetLanguage) && !languagesMatch(scribe.languageCode, targetLanguage);
    translatedText = originalText;

    if (workflowType === "text-only") {
      if (needsTranslation) {
        translatedText = await translateText(originalText, targetLanguage);
      }
    } else if (workflowType === "voice-only") {
      if (!voiceStyle.startsWith("clone_")) {
        throw new Error(
          "Saved voice clone is required. Please record or select your voice sample first.",
        );
      }
      try {
        if (needsTranslation) {
          translatedText = await translateText(originalText, targetLanguage);
        }
        convertedAudioBase64 = await textToSpeech(
          translatedText,
          voiceStyle,
          targetLanguage,
        );
      } catch (audioErr) {
        if (needsTranslation) {
          try {
            translatedText = await translateText(originalText, targetLanguage);
          } catch {
            translatedText = originalText;
          }
        }
        throw audioErr;
      }
    } else if (workflowType === "complete") {
      if (needsTranslation) {
        convertedAudioBase64 = await dubAudio(audioBase64, targetLanguage, format, {
          disableVoiceCloning: true,
          sourceLang: scribe.languageCode,
        });
        translatedText = await translateText(originalText, targetLanguage);
      } else {
        convertedAudioBase64 = await speechToSpeech(audioBase64, voiceStyle, format);
        translatedText = originalText;
      }
    } else {
      throw new Error(`Unsupported workflowType: ${workflowType}`);
    }

    return jsonResponse(
      {
        success: true,
        originalText,
        translatedText,
        convertedAudioBase64,
        targetLanguage,
        voiceStyle,
        workflowType,
      },
      200,
      req,
    );
  } catch (error) {
    logger.error("complete-voice-workflow", "Voice workflow error", error);
    const errMsg = error instanceof Error ? error.message : String(error);

    const isTtsError =
      errMsg === "VOICE_CONVERSION_FAILED" ||
      errMsg === "VOICE_CLONE_FAILED" ||
      errMsg.includes("Speech-to-speech") ||
      errMsg.includes("Dubbing");
    const hasText =
      (originalText && originalText.trim()) || (translatedText && translatedText.trim());

    if (isTtsError && hasText && (workflowType === "complete" || workflowType === "voice-only")) {
      return jsonResponse(
        {
          success: true,
          originalText: originalText || "",
          translatedText: translatedText || originalText || "",
          convertedAudioBase64: "",
          targetLanguage,
          voiceStyle,
          workflowType,
          ttsFallback: true,
        },
        200,
        req,
      );
    }

    let userFriendlyMessage = "Voice processing failed. Please try again.";
    if (errMsg.includes("No speech detected")) {
      userFriendlyMessage = errMsg;
    } else if (errMsg === "TRANSCRIPTION_FAILED" || errMsg === "TRANSCRIPTION_TIMEOUT") {
      userFriendlyMessage =
        "Failed to transcribe audio. Please ensure good audio quality and try again.";
    } else if (errMsg === "TRANSLATION_FAILED") {
      userFriendlyMessage = "Translation failed. Please check your settings and try again.";
    } else if (errMsg === "VOICE_CONVERSION_FAILED") {
      userFriendlyMessage =
        "Voice conversion failed. Please try again or select a different voice.";
    }

    return jsonResponse({ success: false, error: userFriendlyMessage }, 500, req);
  }
});
