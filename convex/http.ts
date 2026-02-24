/**
 * Convex HTTP router - Voice workflow endpoint for real-time functionality.
 * Handles: complete (Change Language & Voice) and voice-only (Translate My Same Voice)
 */
import { httpRouter } from "convex/server";
import { httpAction } from "./_generated/server";
import {
  runVoiceWorkflow,
  CORS_HEADERS,
  type WorkflowRequest,
} from "./voiceWorkflow";

const http = httpRouter();

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

      const req: WorkflowRequest = {
        audioBase64,
        targetLanguage: targetLanguage || "",
        voiceStyle: voiceStyle || "aria",
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
