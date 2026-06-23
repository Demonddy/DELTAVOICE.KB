import "https://deno.land/x/xhr@0.1.0/mod.ts";
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { corsHeadersForRequest, handleServerError, jsonResponse, logger, secureEdgeRequest } from "../_shared/security.ts";

const languageNames: { [key: string]: string } = {
  'en': 'English',
  'es': 'Spanish',
  'fr': 'French',
  'de': 'German',
  'it': 'Italian',
  'pt': 'Portuguese',
  'ru': 'Russian',
  'ja': 'Japanese',
  'ko': 'Korean',
  'zh': 'Chinese',
  'ar': 'Arabic',
  'hi': 'Hindi',
  'nl': 'Dutch',
};

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response(null, { headers: { ...corsHeadersForRequest(req), 'Content-Type': 'application/json' } });
  }

  const auth = await secureEdgeRequest(req, "free-translate-text");
  if (auth instanceof Response) return auth;

  try {
    const reqBody = await req.json();
    const { text, targetLanguage, sourceLanguage } = reqBody;
    const ALLOWED_MODELS = ["gpt-4o-mini", "gpt-3.5-turbo"];
    const model = ALLOWED_MODELS.includes(reqBody.model) ? reqBody.model : "gpt-4o-mini";

    if (!text || !targetLanguage) {
      return jsonResponse({ error: "Text and target language are required." }, 400, req);
    }

    const openAIApiKey = Deno.env.get('OPENAI_API_KEY77') || Deno.env.get('OPENAI_API_KEY');
    if (!openAIApiKey) {
      logger.error("free-translate-text", "OpenAI API key not configured");
      return jsonResponse({ error: "Translation service temporarily unavailable." }, 503, req);
    }

    const targetName = languageNames[targetLanguage] || targetLanguage;
    const sourceName = sourceLanguage ? (languageNames[sourceLanguage] || sourceLanguage) : null;
    const systemPrompt = sourceName
      ? `You are a professional translator. Translate the given text from ${sourceName} into ${targetName}. Only return the translated text, nothing else.`
      : `You are a professional translator. Translate the given text accurately into ${targetName}. Only return the translated text, nothing else.`;

    const response = await fetch('https://api.openai.com/v1/chat/completions', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${openAIApiKey}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        model,
        messages: [
          { role: 'system', content: systemPrompt },
          { role: 'user', content: text }
        ],
        temperature: 0.3,
        max_tokens: 1000,
      }),
    });

    const data = await response.json();

    if (!response.ok) {
      logger.error("free-translate-text", `OpenAI API error: ${response.status}`, data?.error?.message);
      return jsonResponse({ error: "Translation failed. Please try again." }, response.status >= 500 ? 502 : 400, req);
    }

    const translatedText = data?.choices?.[0]?.message?.content?.trim() || '';

    return jsonResponse({ translatedText }, 200, req);
  } catch (error) {
    return handleServerError(
      "free-translate-text",
      error,
      req,
      "Translation failed. Please try again.",
    );
  }
});
