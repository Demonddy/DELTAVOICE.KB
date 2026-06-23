
import "https://deno.land/x/xhr@0.1.0/mod.ts";
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { corsHeadersForRequest, handleServerError, jsonResponse, logger, secureEdgeRequest } from "../_shared/security.ts";

const languageNames: { [key: string]: string } = {
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

  const auth = await secureEdgeRequest(req, "translate-text");
  if (auth instanceof Response) return auth;

  try {
    const reqBody = await req.json();
    const { text, targetLanguage } = reqBody;
    const ALLOWED_MODELS = ["gpt-4o-mini", "gpt-3.5-turbo"];
    const model = ALLOWED_MODELS.includes(reqBody.model) ? reqBody.model : "gpt-4o-mini";

    if (!text || !targetLanguage) {
      logger.error("translate-text", "Missing required parameters");
      return jsonResponse({ error: "Text and target language are required." }, 400, req);
    }

    const openAIApiKey = Deno.env.get('OPENAI_API_KEY');
    if (!openAIApiKey) {
      logger.error("translate-text", "OpenAI API key not configured");
      return jsonResponse({ error: "Translation service temporarily unavailable." }, 503, req);
    }

    const languageName = languageNames[targetLanguage] || targetLanguage;

    let response;
    let attempts = 0;
    const maxAttempts = 3;

    while (attempts < maxAttempts) {
      response = await fetch('https://api.openai.com/v1/chat/completions', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${openAIApiKey}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          model,
          messages: [
            {
              role: 'system',
              content: `You are a professional translator. Translate the given text accurately into ${languageName}. Preserve the tone, meaning, and context. Only return the translated text, nothing else.`
            },
            { role: 'user', content: text }
          ],
          temperature: 0.3,
          max_tokens: 1000,
        }),
      });

      if (response.ok) {
        break;
      }

      attempts++;
      const errorText = await response.text();
      logger.error("translate-text", `OpenAI API error (attempt ${attempts}): ${response.status}`, errorText.slice(0, 400));

      if (response.status === 429) {
        return jsonResponse({ error: "Translation service is busy. Please try again later." }, 429, req);
      }

      if (response.status === 401) {
        return jsonResponse({ error: "Translation service temporarily unavailable." }, 503, req);
      }

      if (attempts >= maxAttempts) {
        throw new Error(`Translation failed after ${maxAttempts} attempts`);
      }

      await new Promise(resolve => setTimeout(resolve, Math.pow(2, attempts) * 1000));
    }

    const data = await response!.json();
    const translatedText = data.choices[0].message.content;

    return jsonResponse({ translatedText }, 200, req);
  } catch (error) {
    return handleServerError(
      "translate-text",
      error,
      req,
      "Translation failed. Please try again.",
    );
  }
});
