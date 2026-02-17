# Edge Functions Setup – OpenAI API Key

The **writing-tool** and **ai-chat** Edge Functions are deployed and use the OpenAI API. They read the `OPENAI_API_KEY77` secret in Supabase.

## Secret: OPENAI_API_KEY77

The functions use `OPENAI_API_KEY77` (to avoid conflict with an existing `OPENAI_API_KEY`). Ensure this secret is set in Supabase:

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

## Get an OpenAI API Key

1. Go to [platform.openai.com/api-keys](https://platform.openai.com/api-keys)
2. Sign in or create an account
3. Create a new secret key
4. Copy the key and use it as the value for `OPENAI_API_KEY77`

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

After setting `OPENAI_API_KEY77`, restart the app and test:

1. **Writing tool:** Select text → tap AI writing icon → choose a task (e.g. Grammar)
2. **AI chat:** Open AI chat from the keyboard → send a message
