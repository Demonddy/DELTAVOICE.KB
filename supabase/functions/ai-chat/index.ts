import "https://deno.land/x/xhr@0.1.0/mod.ts";
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import {
  corsHeadersForRequest,
  handleServerError,
  jsonResponse,
  logger,
  secureEdgeRequest,
} from "../_shared/security.ts";

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { headers: corsHeadersForRequest(req) });
  }

  const auth = await secureEdgeRequest(req, "ai-chat");
  if (auth instanceof Response) return auth;

  try {
    const openAIApiKey = Deno.env.get("OPENAI_API_KEY77") || Deno.env.get("OPENAI_API_KEY");

    if (!openAIApiKey) {
      logger.error("ai-chat", "OpenAI API key not configured");
      return jsonResponse(
        {
          success: false,
          error: "Service temporarily unavailable",
          content: "I'm having trouble connecting. Please try again later.",
          response: "I'm having trouble connecting. Please try again later.",
        },
        200,
        req,
      );
    }

    const { messages } = await req.json();

    if (!messages || !Array.isArray(messages)) {
      return jsonResponse(
        {
          success: false,
          error: "Messages array is required",
          content: "Please send a message to start chatting!",
          response: "Please send a message to start chatting!",
        },
        200,
        req,
      );
    }

    const systemMessage = {
      role: "system",
      content: `You are a helpful, friendly AI assistant integrated into a mobile keyboard app. 
Your name is DeltaVoice AI. Be concise but informative (2-4 sentences typically).
Use emojis occasionally to be friendly 😊.
Respond in the same language the user writes in.
If asked to write something (email, message, etc.), provide complete, ready-to-use content.
For translations, provide the translation directly without extra explanation.
Be helpful, accurate, and conversational like ChatGPT.`,
    };

    const MAX_MESSAGES = 50;
    const sanitizedMessages = messages
      .filter((m: { role?: string }) => m.role === "user" || m.role === "assistant")
      .slice(-MAX_MESSAGES);
    const apiMessages = [systemMessage, ...sanitizedMessages];

    const response = await fetch("https://api.openai.com/v1/chat/completions", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${openAIApiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        model: "gpt-4o-mini",
        messages: apiMessages,
        max_tokens: 1000,
        temperature: 0.7,
        presence_penalty: 0.1,
        frequency_penalty: 0.1,
      }),
    });

    if (!response.ok) {
      const errorText = await response.text();
      logger.error("ai-chat", `OpenAI API error: ${response.status}`, errorText.slice(0, 400));

      return jsonResponse(
        {
          success: false,
          error: "AI service temporarily unavailable",
          content: "I'm experiencing some issues. Please try again in a moment. 🔄",
          response: "I'm experiencing some issues. Please try again in a moment. 🔄",
        },
        200,
        req,
      );
    }

    const result = await response.json();
    const aiResponse = result.choices[0]?.message?.content ||
      "I couldn't generate a response. Please try again.";

    return jsonResponse(
      {
        success: true,
        content: aiResponse,
        response: aiResponse,
        message: aiResponse,
      },
      200,
      req,
    );
  } catch (error) {
    return handleServerError(
      "ai-chat",
      error,
      req,
      "Sorry, I encountered an error. Please try again. 🔄",
    );
  }
});
