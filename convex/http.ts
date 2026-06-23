/**
 * Convex HTTP router - Voice workflow and AI chat.
 * Handles: complete (Change Language & Voice), voice-only (Translate My Same Voice), ai-chat
 */
import { httpRouter } from "convex/server";
import { httpAction } from "./_generated/server";
import {
  runVoiceWorkflow,
  corsHeadersForRequest,
  rejectDisallowedOrigin,
  type WorkflowRequest,
} from "./voiceWorkflow";
import { runVideoPipeline } from "./videoPipeline";
import { secureConvexRequest } from "./auth";
import { logger } from "./logger";

const http = httpRouter();

type ChatMessage = { role: string; content: string };

async function callChatCompletions(
  apiMessages: ChatMessage[],
): Promise<{ ok: true; text: string } | { ok: false; status: number; detail: string }> {
  const deepSeekApiKey = process.env.DEEPSEEK_API || process.env.DEEPSEEKA;
  const openAIApiKey =
    process.env.OPENAI_API_KEY77 || process.env.OPENAI_API_KEY;

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
    return { ok: false, status: 503, detail: "No AI API key configured" };
  }

  let lastStatus = 503;
  let lastDetail = "AI service temporarily unavailable";

  for (const attempt of attempts) {
    const response = await fetch(attempt.url, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${attempt.key}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        model: attempt.model,
        messages: apiMessages,
        max_tokens: 1000,
        temperature: 0.7,
      }),
    });

    if (response.ok) {
      const result = (await response.json()) as {
        choices?: Array<{ message?: { content?: string } }>;
      };
      const text =
        result.choices?.[0]?.message?.content ||
        "I couldn't generate a response. Please try again.";
      return { ok: true, text };
    }

    lastStatus = response.status;
    lastDetail = await response.text();
    logger.error("ai-chat", `AI chat ${attempt.model} error`, { status: lastStatus, detail: lastDetail.slice(0, 400) });
  }

  return { ok: false, status: lastStatus, detail: lastDetail };
}

// --- AI Chat endpoint (alternative to Supabase when unreachable) ---
http.route({
  path: "/ai-chat",
  method: "OPTIONS",
  handler: httpAction(async (_ctx, request) => {
    return new Response(null, {
      status: 204,
      headers: corsHeadersForRequest(request),
    });
  }),
});

http.route({
  path: "/ai-chat",
  method: "POST",
  handler: httpAction(async (_ctx, request) => {
    const originDenied = rejectDisallowedOrigin(request);
    if (originDenied) return originDenied;

    const jsonHeaders = {
      ...corsHeadersForRequest(request),
      "Content-Type": "application/json",
    };

    const auth = await secureConvexRequest(request, "ai-chat");
    if (auth instanceof Response) {
      const body = await auth.text();
      return new Response(body, { status: auth.status, headers: jsonHeaders });
    }

    try {
      const body = (await request.json()) as { messages?: Array<{ role: string; content: string }> };
      const messages = body.messages;
      if (!messages || !Array.isArray(messages)) {
        return new Response(
          JSON.stringify({
            success: false,
            error: "Messages array is required",
            content: "Please send a message to start chatting!",
          }),
          { status: 200, headers: jsonHeaders }
        );
      }

      const systemMessage = {
        role: "system",
        content: `You are a helpful, friendly AI assistant integrated into a mobile keyboard app. Be concise but informative (2-4 sentences typically). Use emojis occasionally. Respond in the same language the user writes in. If asked to write something, provide complete, ready-to-use content.`,
      };
      const sanitizedMessages = messages
        .filter((m) => m.role === "user" || m.role === "assistant")
        .slice(-50);
      const apiMessages = [systemMessage, ...sanitizedMessages];

      const result = await callChatCompletions(apiMessages);
      if (result.ok) {
        return new Response(
          JSON.stringify({
            success: true,
            content: result.text,
            response: result.text,
          }),
          { status: 200, headers: jsonHeaders }
        );
      }

      return new Response(
        JSON.stringify({
          success: false,
          error: "AI service temporarily unavailable",
          content: "I'm experiencing some issues. Please try again in a moment. 🔄",
        }),
        { status: 200, headers: jsonHeaders }
      );
    } catch (error) {
      const err = error instanceof Error ? error : new Error(String(error));
      logger.error("ai-chat", "AI chat error", err);
      return new Response(
        JSON.stringify({
          success: false,
          error: "An unexpected error occurred. Please try again.",
          content: "Sorry, I encountered an error. Please try again. 🔄",
        }),
        { status: 200, headers: jsonHeaders }
      );
    }
  }),
});

// --- Voice workflow ---

http.route({
  path: "/complete-voice-workflow",
  method: "OPTIONS",
  handler: httpAction(async (_ctx, request) => {
    return new Response(null, {
      status: 204,
      headers: corsHeadersForRequest(request),
    });
  }),
});

http.route({
  path: "/complete-voice-workflow",
  method: "POST",
  handler: httpAction(async (_ctx, request) => {
    const originDenied = rejectDisallowedOrigin(request);
    if (originDenied) return originDenied;

    const jsonHeaders = {
      ...corsHeadersForRequest(request),
      "Content-Type": "application/json",
    };

    const auth = await secureConvexRequest(request, "complete-voice-workflow");
    if (auth instanceof Response) {
      const body = await auth.text();
      return new Response(body, { status: auth.status, headers: jsonHeaders });
    }

    try {
      const openAIApiKey =
        process.env.OPENAI_API_KEY77 || process.env.OPENAI_API_KEY;
      const elevenLabsApiKey =
        process.env.ELEVENLABS_API_KEY77 || process.env.ELEVENLABS_API_KEY;

      if (!openAIApiKey) {
        logger.error("complete-voice-workflow", "OpenAI API key not configured");
        return new Response(
          JSON.stringify({
            error: "Voice processing is temporarily unavailable. Please try again later.",
            code: "SERVICE_UNAVAILABLE",
          }),
          { status: 500, headers: jsonHeaders }
        );
      }

      const body = (await request.json()) as {
        audioBase64?: string;
        targetLanguage?: string;
        voiceStyle?: string;
        workflowType?: string;
        format?: string;
      };

      const { audioBase64, targetLanguage, voiceStyle, workflowType, format } =
        body;

      if (!audioBase64) {
        return new Response(
          JSON.stringify({ success: false, error: "Audio data is required" }),
          { status: 400, headers: jsonHeaders }
        );
      }

      const MAX_AUDIO_BASE64 = 14_000_000;
      if (audioBase64.length > MAX_AUDIO_BASE64) {
        return new Response(
          JSON.stringify({ success: false, error: "Audio file too large. Maximum size is 10 MB." }),
          { status: 413, headers: jsonHeaders }
        );
      }

      const wfType = workflowType || "complete";
      if (
        wfType !== "complete" &&
        wfType !== "voice-only" &&
        wfType !== "text-only"
      ) {
        return new Response(
          JSON.stringify({
            success: false,
            error: `Unsupported workflowType: ${wfType}. Use 'complete', 'voice-only', or 'text-only'.`,
          }),
          { status: 400, headers: jsonHeaders }
        );
      }

      if (wfType !== "text-only" && !elevenLabsApiKey) {
        logger.error("complete-voice-workflow", "ElevenLabs API key not configured");
        return new Response(
          JSON.stringify({
            error: "Voice processing is temporarily unavailable. Please try again later.",
            code: "SERVICE_UNAVAILABLE",
          }),
          { status: 500, headers: jsonHeaders }
        );
      }

      // Ensure voice and language are never empty - use selected values or sensible defaults
      const sanitizedLang =
        (typeof targetLanguage === "string" && targetLanguage.trim())
          ? targetLanguage.trim()
          : "en";
      const sanitizedVoice =
        (typeof voiceStyle === "string" && voiceStyle.trim())
          ? voiceStyle.trim().toLowerCase()
          : "aria";

      const req: WorkflowRequest = {
        audioBase64,
        targetLanguage: sanitizedLang,
        voiceStyle: sanitizedVoice,
        workflowType: wfType as "complete" | "voice-only" | "text-only",
        format,
      };

      const result = await runVoiceWorkflow(req);

      return new Response(JSON.stringify(result), {
        status: 200,
        headers: jsonHeaders,
      });
    } catch (error) {
      const err = error instanceof Error ? error : new Error(String(error));
      const isTtsError =
        err.message?.includes("Voice conversion failed") ||
        err.message?.includes("Voice clone creation failed");

      if (isTtsError) {
        return new Response(
          JSON.stringify({
            success: false,
            error: "Voice conversion failed. Please try again.",
            timestamp: new Date().toISOString(),
          }),
          { status: 500, headers: jsonHeaders }
        );
      }

      let userFriendlyMessage = "Voice processing failed. Please try again.";
      if (err.message.includes("No speech detected")) {
        userFriendlyMessage = err.message;
      } else if (err.message.includes("Transcription failed")) {
        userFriendlyMessage =
          "Failed to transcribe audio. Please ensure good audio quality and try again.";
      } else if (err.message.includes("Translation failed")) {
        userFriendlyMessage =
          "Translation failed. Please check your settings and try again.";
      } else if (err.message.includes("Voice conversion failed")) {
        userFriendlyMessage =
          "Voice conversion failed. Please try again or select a different voice.";
      }

      logger.error("complete-voice-workflow", userFriendlyMessage, err);

      return new Response(
        JSON.stringify({
          success: false,
          error: userFriendlyMessage,
          timestamp: new Date().toISOString(),
        }),
        { status: 500, headers: jsonHeaders }
      );
    }
  }),
});

// --- Video workflow (extract → voice AI → mux via external FFmpeg service) ---

http.route({
  path: "/video-workflow",
  method: "OPTIONS",
  handler: httpAction(async (_ctx, request) => {
    return new Response(null, {
      status: 204,
      headers: corsHeadersForRequest(request),
    });
  }),
});

http.route({
  path: "/video-workflow",
  method: "POST",
  handler: httpAction(async (_ctx, request) => {
    const originDenied = rejectDisallowedOrigin(request);
    if (originDenied) return originDenied;

    const jsonHeaders = {
      ...corsHeadersForRequest(request),
      "Content-Type": "application/json",
    };

    const auth = await secureConvexRequest(request, "video-workflow");
    if (auth instanceof Response) {
      const body = await auth.text();
      return new Response(body, { status: auth.status, headers: jsonHeaders });
    }

    try {
      const body = (await request.json()) as {
        videoBase64?: string;
        targetLanguage?: string;
        voiceStyle?: string;
        videoFormat?: string;
      };

      if (!body.videoBase64 || body.videoBase64.length < 100) {
        return new Response(
          JSON.stringify({ success: false, error: "videoBase64 is required" }),
          { status: 400, headers: jsonHeaders }
        );
      }

      const MAX_VIDEO_BASE64 = 140_000_000;
      if (body.videoBase64.length > MAX_VIDEO_BASE64) {
        return new Response(
          JSON.stringify({ success: false, error: "Video file too large. Maximum size is 100 MB." }),
          { status: 413, headers: jsonHeaders }
        );
      }

      const openAIApiKey =
        process.env.OPENAI_API_KEY77 || process.env.OPENAI_API_KEY;
      const elevenLabsApiKey =
        process.env.ELEVENLABS_API_KEY77 || process.env.ELEVENLABS_API_KEY;

      if (!openAIApiKey) {
        logger.error("video-workflow", "OpenAI API key not configured");
        return new Response(
          JSON.stringify({
            error: "Video processing is temporarily unavailable. Please try again later.",
            code: "SERVICE_UNAVAILABLE",
          }),
          { status: 500, headers: jsonHeaders }
        );
      }
      if (!elevenLabsApiKey) {
        logger.error("video-workflow", "ElevenLabs API key not configured");
        return new Response(
          JSON.stringify({
            error: "Video processing is temporarily unavailable. Please try again later.",
            code: "SERVICE_UNAVAILABLE",
          }),
          { status: 500, headers: jsonHeaders }
        );
      }

      const sanitizedLang =
        typeof body.targetLanguage === "string" && body.targetLanguage.trim()
          ? body.targetLanguage.trim()
          : "en";
      const sanitizedVoice =
        typeof body.voiceStyle === "string" && body.voiceStyle.trim()
          ? body.voiceStyle.trim().toLowerCase()
          : "aria";

      const result = await runVideoPipeline({
        videoBase64: body.videoBase64,
        targetLanguage: sanitizedLang,
        voiceStyle: sanitizedVoice,
        videoFormat: body.videoFormat,
      });

      return new Response(JSON.stringify(result), {
        status: 200,
        headers: jsonHeaders,
      });
    } catch (error) {
      const err = error instanceof Error ? error : new Error(String(error));
      logger.error("video-workflow", "Video workflow error", err);
      return new Response(
        JSON.stringify({
          success: false,
          error: "Video processing failed. Please try again.",
          timestamp: new Date().toISOString(),
        }),
        { status: 500, headers: jsonHeaders }
      );
    }
  }),
});

export default http;
