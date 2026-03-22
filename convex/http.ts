/**
 * Convex HTTP router - Voice workflow and AI chat.
 * Handles: complete (Change Language & Voice), voice-only (Translate My Same Voice), ai-chat
 */
import { httpRouter } from "convex/server";
import { httpAction } from "./_generated/server";
import {
  runVoiceWorkflow,
  CORS_HEADERS,
  type WorkflowRequest,
} from "./voiceWorkflow";

const http = httpRouter();

// --- AI Chat endpoint (alternative to Supabase when unreachable) ---
http.route({
  path: "/ai-chat",
  method: "OPTIONS",
  handler: httpAction(async () => {
    return new Response(null, {
      status: 204,
      headers: CORS_HEADERS,
    });
  }),
});

http.route({
  path: "/ai-chat",
  method: "POST",
  handler: httpAction(async (_ctx, request) => {
    const jsonHeaders = {
      ...CORS_HEADERS,
      "Content-Type": "application/json",
    };

    try {
      const deepSeekApiKey =
        process.env.DEEPSEEK_API || process.env.DEEPSEEKA;
      if (!deepSeekApiKey) {
        return new Response(
          JSON.stringify({
            success: false,
            error: "DeepSeek API key not configured",
            content: "I'm having trouble connecting. Please try again later.",
          }),
          { status: 200, headers: jsonHeaders }
        );
      }

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
      const apiMessages =
        messages[0]?.role === "system" ? messages : [systemMessage, ...messages];

      const response = await fetch("https://api.deepseek.com/v1/chat/completions", {
        method: "POST",
        headers: {
          Authorization: `Bearer ${deepSeekApiKey}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          model: "deepseek-chat",
          messages: apiMessages,
          max_tokens: 1000,
          temperature: 0.7,
        }),
      });

      if (!response.ok) {
        const errText = await response.text();
        console.error("DeepSeek AI chat error:", response.status, errText);
        return new Response(
          JSON.stringify({
            success: false,
            error: "AI service temporarily unavailable",
            content: "I'm experiencing some issues. Please try again in a moment. 🔄",
          }),
          { status: 200, headers: jsonHeaders }
        );
      }

      const result = (await response.json()) as {
        choices?: Array<{ message?: { content?: string } }>;
      };
      const aiResponse =
        result.choices?.[0]?.message?.content ||
        "I couldn't generate a response. Please try again.";

      return new Response(
        JSON.stringify({
          success: true,
          content: aiResponse,
          response: aiResponse,
        }),
        { status: 200, headers: jsonHeaders }
      );
    } catch (error) {
      const err = error instanceof Error ? error : new Error(String(error));
      console.error("AI chat error:", err);
      return new Response(
        JSON.stringify({
          success: false,
          error: err.message,
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
  handler: httpAction(async () => {
    return new Response(null, {
      status: 204,
      headers: CORS_HEADERS,
    });
  }),
});

http.route({
  path: "/complete-voice-workflow",
  method: "POST",
  handler: httpAction(async (_ctx, request) => {
    const jsonHeaders = {
      ...CORS_HEADERS,
      "Content-Type": "application/json",
    };

    try {
      const openAIApiKey =
        process.env.OPENAI_API_KEY77 || process.env.OPENAI_API_KEY;
      const elevenLabsApiKey =
        process.env.ELEVENLABS_API_KEY77 || process.env.ELEVENLABS_API_KEY;

      if (!openAIApiKey) {
        return new Response(
          JSON.stringify({
            error:
              "OpenAI API key not configured. Please add your OpenAI API key in Convex environment variables.",
            code: "MISSING_OPENAI_KEY",
          }),
          { status: 500, headers: jsonHeaders }
        );
      }

      if (!elevenLabsApiKey) {
        return new Response(
          JSON.stringify({
            error:
              "ElevenLabs API key not configured. Please add your ElevenLabs API key in Convex environment variables.",
            code: "MISSING_ELEVENLABS_KEY",
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

      const wfType = workflowType || "complete";
      if (wfType !== "complete" && wfType !== "voice-only") {
        return new Response(
          JSON.stringify({
            success: false,
            error: `Unsupported workflowType: ${wfType}. Only 'complete' and 'voice-only' are supported by this endpoint.`,
          }),
          { status: 400, headers: jsonHeaders }
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
        workflowType: wfType,
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
            error: err.message,
            details: err.message,
            timestamp: new Date().toISOString(),
          }),
          { status: 500, headers: jsonHeaders }
        );
      }

      let userFriendlyMessage = "Voice processing failed. Please try again.";
      if (err.message.includes("OpenAI API key not configured")) {
        userFriendlyMessage =
          "OpenAI API key not configured. Please add your OpenAI API key to continue.";
      } else if (err.message.includes("ElevenLabs API key not configured")) {
        userFriendlyMessage =
          "ElevenLabs API key not configured. Please add your ElevenLabs API key to continue.";
      } else if (err.message.includes("No speech detected")) {
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

      return new Response(
        JSON.stringify({
          success: false,
          error: userFriendlyMessage,
          details: err.message,
          timestamp: new Date().toISOString(),
        }),
        { status: 500, headers: jsonHeaders }
      );
    }
  }),
});

export default http;
