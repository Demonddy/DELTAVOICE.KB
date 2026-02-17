# Supabase Setup Guide

This guide will help you set up Supabase Edge Functions for voice-to-text, text-to-speech, and translation features.

## Prerequisites

1. A Supabase account (sign up at https://supabase.com)
2. A Supabase project created
3. Node.js installed (for Edge Functions)

## Step 1: Configure Supabase Credentials

1. Open `app/src/main/java/com/deltavoice/config/SupabaseConfig.kt`
2. Replace the placeholder values:
   - `SUPABASE_URL`: Your Supabase project URL (found in Project Settings > API)
   - `SUPABASE_ANON_KEY`: Your Supabase anon/public key (found in Project Settings > API)

## Step 2: Create Edge Functions

You need to create three Edge Functions in your Supabase project:

### Function 1: voice-to-text

This function converts audio to text using a speech recognition service (e.g., OpenAI Whisper, Google Cloud Speech-to-Text).

**Location**: `supabase/functions/voice-to-text/index.ts`

```typescript
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
};

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const { audioBase64, language, format } = await req.json();

    // Example using OpenAI Whisper API
    // You'll need to add your OpenAI API key to Supabase secrets
    const openaiApiKey = Deno.env.get("OPENAI_API_KEY");

    const response = await fetch(
      "https://api.openai.com/v1/audio/transcriptions",
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${openaiApiKey}`,
          "Content-Type": "multipart/form-data",
        },
        body: JSON.stringify({
          file: audioBase64,
          model: "whisper-1",
          language: language,
        }),
      }
    );

    const data = await response.json();

    return new Response(
      JSON.stringify({
        text: data.text,
        language: language,
        confidence: 1.0,
      }),
      { headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  } catch (error) {
    return new Response(JSON.stringify({ error: error.message }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
```

### Function 2: text-to-speech

This function converts text to speech audio using a TTS service (e.g., Google Cloud TTS, Azure TTS).

**Location**: `supabase/functions/text-to-speech/index.ts`

```typescript
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
};

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const { text, language, voice } = await req.json();

    // Example using Google Cloud TTS
    // You'll need to add your Google Cloud credentials to Supabase secrets
    const googleApiKey = Deno.env.get("GOOGLE_CLOUD_API_KEY");

    const response = await fetch(
      `https://texttospeech.googleapis.com/v1/text:synthesize?key=${googleApiKey}`,
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          input: { text },
          voice: {
            languageCode: language,
            name: voice || `${language}-Standard-A`,
          },
          audioConfig: { audioEncoding: "MP3" },
        }),
      }
    );

    const data = await response.json();

    return new Response(
      JSON.stringify({
        audioBase64: data.audioContent,
        format: "mp3",
      }),
      { headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  } catch (error) {
    return new Response(JSON.stringify({ error: error.message }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
```

### Function 3: translate

This function translates text between languages using a translation service (e.g., Google Translate API, DeepL).

**Location**: `supabase/functions/translate/index.ts`

```typescript
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
};

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const { text, sourceLanguage, targetLanguage } = await req.json();

    // Example using Google Translate API
    const googleApiKey = Deno.env.get("GOOGLE_CLOUD_API_KEY");

    const response = await fetch(
      `https://translation.googleapis.com/language/translate/v2?key=${googleApiKey}`,
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          q: text,
          source: sourceLanguage,
          target: targetLanguage,
        }),
      }
    );

    const data = await response.json();
    const translatedText = data.data.translations[0].translatedText;

    return new Response(
      JSON.stringify({
        translatedText,
        sourceLanguage,
        targetLanguage,
        confidence: 1.0,
      }),
      { headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  } catch (error) {
    return new Response(JSON.stringify({ error: error.message }), {
      status: 500,
      headers: { ...corsHeaders, "Content-Type": "application/json" },
    });
  }
});
```

## Step 3: Deploy Edge Functions

1. Install Supabase CLI:

   ```bash
   npm install -g supabase
   ```

2. Login to Supabase:

   ```bash
   supabase login
   ```

3. Link your project:

   ```bash
   supabase link --project-ref your-project-ref
   ```

4. Deploy functions:
   ```bash
   supabase functions deploy voice-to-text
   supabase functions deploy text-to-speech
   supabase functions deploy translate
   ```

## Step 4: Set Up API Keys

1. Go to your Supabase project dashboard
2. Navigate to Project Settings > Edge Functions > Secrets
3. Add the following secrets:
   - `OPENAI_API_KEY`: Your OpenAI API key (for voice-to-text)
   - `GOOGLE_CLOUD_API_KEY`: Your Google Cloud API key (for TTS and translation)

## Alternative: Use Supabase Built-in Services

If you prefer not to use external APIs, you can:

1. Use Supabase's built-in database for storing translations
2. Use third-party services through Supabase Edge Functions
3. Implement your own ML models using Supabase's ML capabilities

## Testing

After setup, test the functions using the Supabase dashboard or the Android app. The app will automatically fall back to Android's built-in TTS and SpeechRecognizer if Supabase functions fail.

## Troubleshooting

- **Function not found**: Make sure you've deployed the functions and they're named correctly
- **Authentication errors**: Verify your Supabase URL and anon key are correct
- **API errors**: Check that your API keys are set correctly in Supabase secrets
- **CORS errors**: The Edge Functions include CORS headers, but verify they're working

## Notes

- The app includes fallback mechanisms to Android's native TTS and SpeechRecognizer
- Edge Functions can be customized to use different services (Azure, AWS, etc.)
- Consider implementing caching for frequently translated text
- Monitor your API usage to avoid unexpected costs
