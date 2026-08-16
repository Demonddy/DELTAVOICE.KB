import { supabase, SUPABASE_URL, SUPABASE_ANON_KEY } from "./supabase";

const CONVEX_SITE_URL = "https://kindred-curlew-363.eu-west-1.convex.site";
const AI_CHAT_CONVEX_SITE = "https://spotted-guanaco-278.eu-west-1.convex.site";

async function getAuthHeaders(): Promise<Record<string, string>> {
  const { data } = await supabase.auth.getSession();
  const token = data.session?.access_token;
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
  };
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }
  return headers;
}

export interface VoiceWorkflowRequest {
  audioBase64: string;
  targetLanguage: string;
  voiceStyle: string;
  workflowType: "complete" | "voice-only" | "text-only";
  format?: string;
}

export interface VoiceWorkflowResult {
  success: boolean;
  originalText: string;
  translatedText: string;
  convertedAudioBase64: string;
  targetLanguage: string;
  voiceStyle: string;
  workflowType: string;
  ttsFallback?: boolean;
  error?: string;
}

export async function callCreateVoiceClone(req: {
  audioBase64: string;
  name: string;
  format?: string;
}): Promise<{ success: boolean; voiceId: string; name: string }> {
  const { data } = await supabase.auth.getSession();
  const token = data.session?.access_token;
  if (!token) {
    throw new Error("Please sign in to save your voice.");
  }

  const res = await fetch(`${SUPABASE_URL}/functions/v1/create-voice-clone`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
      apikey: SUPABASE_ANON_KEY || token,
    },
    body: JSON.stringify({
      name: req.name,
      audioBase64: req.audioBase64,
      format: req.format || "webm",
      description: "Saved from Translate My Same Voice",
    }),
  });

  const body = await res.json().catch(() => ({ error: res.statusText }));
  if (!res.ok || !body.voiceId) {
    throw new Error(body.error || `Could not save voice: ${res.status}`);
  }
  return {
    success: true,
    voiceId: body.voiceId,
    name: body.name || req.name,
  };
}

export async function callVoiceWorkflow(
  req: VoiceWorkflowRequest
): Promise<VoiceWorkflowResult> {
  const headers = await getAuthHeaders();
  const res = await fetch(`${CONVEX_SITE_URL}/complete-voice-workflow`, {
    method: "POST",
    headers,
    body: JSON.stringify(req),
  });

  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(err.error || `Voice workflow failed: ${res.status}`);
  }
  return res.json();
}

export interface AiChatMessage {
  role: "user" | "assistant";
  content: string;
}

export async function callAiChat(
  messages: AiChatMessage[]
): Promise<{ success: boolean; content: string }> {
  const headers = await getAuthHeaders();
  const res = await fetch(`${AI_CHAT_CONVEX_SITE}/ai-chat`, {
    method: "POST",
    headers,
    body: JSON.stringify({ messages }),
  });

  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(err.error || `AI chat failed: ${res.status}`);
  }
  return res.json();
}

export async function callVideoWorkflow(req: {
  videoBase64: string;
  targetLanguage: string;
  voiceStyle: string;
  videoFormat?: string;
}): Promise<VoiceWorkflowResult & { convertedVideoBase64?: string }> {
  const headers = await getAuthHeaders();
  const res = await fetch(`${CONVEX_SITE_URL}/video-workflow`, {
    method: "POST",
    headers,
    body: JSON.stringify(req),
  });

  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(err.error || `Video workflow failed: ${res.status}`);
  }
  return res.json();
}

export async function callWritingTool(
  text: string,
  tool: string,
  options?: { tone?: string; targetLanguage?: string }
): Promise<{ success: boolean; result: string }> {
  const headers = await getAuthHeaders();
  const { data } = await supabase.auth.getSession();
  const token = data.session?.access_token;
  const supabaseUrl = "https://yvizvsojpwgvaisoahda.supabase.co";

  const res = await fetch(`${supabaseUrl}/functions/v1/writing-tool`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token || ""}`,
      apikey: token || "",
    },
    body: JSON.stringify({ text, tool, ...options }),
  });

  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(err.error || `Writing tool failed: ${res.status}`);
  }
  return res.json();
}

export const LANGUAGES = [
  { code: "en", name: "English" },
  { code: "es", name: "Spanish" },
  { code: "fr", name: "French" },
  { code: "de", name: "German" },
  { code: "it", name: "Italian" },
  { code: "pt", name: "Portuguese" },
  { code: "ru", name: "Russian" },
  { code: "ja", name: "Japanese" },
  { code: "ko", name: "Korean" },
  { code: "zh", name: "Chinese" },
  { code: "ar", name: "Arabic" },
  { code: "hi", name: "Hindi" },
];

export const VOICES = [
  "Adam", "Aria", "Roger", "Sarah", "Laura",
  "Charlie", "George", "Liam",
];
