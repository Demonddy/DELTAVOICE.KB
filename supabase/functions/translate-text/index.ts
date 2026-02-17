
import "https://deno.land/x/xhr@0.1.0/mod.ts";
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

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
    return new Response(null, { headers: corsHeaders });
  }

  try {
    console.log('Translate-text function called');
    const { text, targetLanguage, model = 'gpt-4o-mini' } = await req.json();

    if (!text || !targetLanguage) {
      console.error('Missing required parameters:', { text: !!text, targetLanguage: !!targetLanguage });
      throw new Error('Text and target language are required');
    }

    const openAIApiKey = Deno.env.get('OPENAI_API_KEY');
    if (!openAIApiKey) {
      console.error('OpenAI API key not found');
      throw new Error('OpenAI API key not configured');
    }

    const languageName = languageNames[targetLanguage] || targetLanguage;
    console.log(`Translating to ${languageName}:`, text.substring(0, 100));

    // Retry logic for API calls
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
      console.error(`OpenAI API error (attempt ${attempts}):`, response.status, errorText);

      // Handle specific error cases
      if (response.status === 429) {
        return new Response(JSON.stringify({ 
          error: 'OpenAI API quota exceeded. Please check your OpenAI billing and usage limits.',
          details: 'Your OpenAI account has reached its usage quota. Please add credits or upgrade your plan.',
          status: response.status
        }), {
          status: 429,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        });
      }

      if (response.status === 401) {
        return new Response(JSON.stringify({ 
          error: 'Invalid OpenAI API key. Please check your API key configuration.',
          details: 'The provided OpenAI API key is invalid or has been revoked.',
          status: response.status
        }), {
          status: 401,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        });
      }

      if (attempts >= maxAttempts) {
        const error = JSON.parse(errorText);
        throw new Error(error.error?.message || `Translation failed after ${maxAttempts} attempts`);
      }

      // Wait before retry (exponential backoff)
      await new Promise(resolve => setTimeout(resolve, Math.pow(2, attempts) * 1000));
    }

    const data = await response.json();
    const translatedText = data.choices[0].message.content;
    console.log('Translation successful:', translatedText.substring(0, 100));

    return new Response(JSON.stringify({ translatedText }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  } catch (error) {
    console.error('Error in translate-text function:', error);
    return new Response(JSON.stringify({ 
      error: error.message,
      details: 'Check the function logs for more information. If this is a quota issue, please check your OpenAI billing.',
      timestamp: new Date().toISOString()
    }), {
      status: 500,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  }
});
