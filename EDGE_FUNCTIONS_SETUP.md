# Edge Functions Setup – API Keys

The **writing-tool** Edge Function uses the OpenAI API. The **ai-chat** Edge Function uses the DeepSeek API.

## Secret: OPENAI_API_KEY77 (for writing-tool)

The writing-tool function uses `OPENAI_API_KEY77`. Ensure this secret is set in Supabase:

### Option 1: Supabase Dashboard (recommended)

1. Go to [Supabase Dashboard](https://supabase.com/dashboard/project/yvizvsojpwgvaisoahda)
2. Open **Project Settings** → **Edge Functions** → **Secrets**
3. Add or verify secret:
   - **Key:** `OPENAI_API_KEY77`
   - **Value:** Your OpenAI API key (e.g. `sk-...`)
4. Save

### Option 2: Supabase CLI

```bash
cd c:\Users\rrr\Desktop\keyboard
supabase secrets set OPENAI_API_KEY77=sk-your-openai-api-key-here --project-ref yvizvsojpwgvaisoahda
```

## Secret: Deepseeka (for ai-chat)

The ai-chat function uses the DeepSeek API. Set the `Deepseeka` secret in Supabase:

### Option 1: Supabase Dashboard

1. Go to [Supabase Dashboard](https://supabase.com/dashboard/project/yvizvsojpwgvaisoahda)
2. Open **Project Settings** → **Edge Functions** → **Secrets**
3. Add or verify secret:
   - **Key:** `Deepseeka`
   - **Value:** Your DeepSeek API key
4. Save

### Option 2: Supabase CLI

```bash
supabase secrets set Deepseeka=your-deepseek-api-key-here --project-ref yvizvsojpwgvaisoahda
```

## Get an OpenAI API Key (for writing-tool)

1. Go to [platform.openai.com/api-keys](https://platform.openai.com/api-keys)
2. Sign in or create an account
3. Create a new secret key
4. Copy the key and use it as the value for `OPENAI_API_KEY77`

## Get a DeepSeek API Key (for ai-chat)

1. Go to [platform.deepseek.com](https://platform.deepseek.com)
2. Sign in or create an account
3. Create an API key
4. Copy the key and use it as the value for `Deepseeka`

## What Was Done

| Item | Status |
|------|--------|
| **writing-tool** | Deployed (v3) – grammar, translate, enhance, tone, paraphrase, longer, summarize, synonyms |
| **ai-chat** | Deployed (v4) – DeltaVoice AI chatbot |
| **SupabaseConfig.kt** | Updated to voicetexco.ai project URL and anon key |
| **web/config.js** | Updated to voicetexco.ai project |
| **web/app.js** | Updated fallback config |

## Endpoints

- **Writing tool:** `https://yvizvsojpwgvaisoahda.supabase.co/functions/v1/writing-tool`
- **AI chat:** `https://yvizvsojpwgvaisoahda.supabase.co/functions/v1/ai-chat`

## Verify

After setting the secrets, restart the app and test:

1. **Writing tool:** Select text → tap AI writing icon → choose a task (e.g. Grammar) — requires `OPENAI_API_KEY77`
2. **AI chat:** Open AI chat from the keyboard → send a message — requires `Deepseeka`
