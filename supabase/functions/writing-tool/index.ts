import "https://deno.land/x/xhr@0.1.0/mod.ts";
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

const TASK_SYSTEM_PROMPTS: Record<string, (option?: string) => string> = {
  grammar: () =>
    `You are a grammar corrector. Fix only grammar, spelling, and punctuation in the user's text. Preserve meaning and style. Output ONLY the corrected text, nothing else. No explanations.`,
  reply: () =>
    `You are a reply writer. The user has shared a message or text. Write a natural, appropriate reply to it. Output ONLY the reply text, nothing else.`,
  translate: (lang) =>
    `You are a translator. Translate the user's text into ${lang || "English"}. Output ONLY the translation, nothing else. No explanations or notes.`,
  enhance: () =>
    `You are a writing enhancer. Improve the user's text: better word choice, clarity, and flow. Keep the same meaning and length roughly similar. Output ONLY the enhanced text, nothing else.`,
  tone: (tone) =>
    `You are a tone rewriter. Rewrite the user's text to sound ${tone || "professional"}. Keep the same meaning and approximate length. Output ONLY the rewritten text, nothing else.`,
  paraphrase: () =>
    `You are a paraphraser. Rewrite the user's text in different words while keeping the same meaning. Output ONLY the paraphrased text, nothing else.`,
  continue: () =>
    `You are a writing assistant. The user's text is incomplete. Continue writing from where they left off, in the same style and tone. Output ONLY the continuation (include the original text plus your continuation), nothing else.`,
  longer: () =>
    `You are a writing expander. Expand the user's text to make it longer: add detail, examples, or elaboration without changing the core message. Output ONLY the longer text, nothing else.`,
  shorter: () =>
    `You are a writing condenser. Make the user's text shorter and more concise while keeping the key points. Output ONLY the shortened text, nothing else.`,
  summarize: () =>
    `You are a summarizer. Summarize the user's text concisely. Output ONLY the summary, nothing else.`,
  synonyms: () =>
    `You are a synonym rewriter. Replace words in the user's text with appropriate synonyms where it improves the text. Keep the same meaning and tone. Output ONLY the revised text, nothing else.`,
  email: () =>
    `You are an email writer. Rewrite the user's text as a professional email: clear subject line if needed, proper greeting and sign-off, professional tone. Output ONLY the email text, nothing else.`,
};

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response(null, { headers: corsHeaders });
  }

  try {
    const openAIApiKey = Deno.env.get('OPENAI_API_KEY77') || Deno.env.get('OPENAI_API_KEY');
    if (!openAIApiKey) {
      return new Response(
        JSON.stringify({ success: false, error: 'OpenAI API key not configured', content: '' }),
        { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const body = await req.json();
    const task = (body.task as string)?.toLowerCase() || '';
    const text = typeof body.text === 'string' ? body.text.trim() : '';
    const option = typeof body.option === 'string' ? body.option.trim() : undefined;

    if (!text) {
      return new Response(
        JSON.stringify({ success: false, error: 'Text is required', content: '' }),
        { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const getPrompt = TASK_SYSTEM_PROMPTS[task];
    if (!getPrompt) {
      return new Response(
        JSON.stringify({ success: false, error: `Unknown task: ${task}`, content: '' }),
        { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const systemContent = getPrompt(option);
    const response = await fetch('https://api.openai.com/v1/chat/completions', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${openAIApiKey}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        model: 'gpt-4o-mini',
        messages: [
          { role: 'system', content: systemContent },
          { role: 'user', content: text },
        ],
        max_tokens: 2000,
        temperature: 0.5,
      }),
    });

    if (!response.ok) {
      const errText = await response.text();
      console.error('OpenAI error:', response.status, errText);
      return new Response(
        JSON.stringify({ success: false, error: 'AI service error', content: '' }),
        { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const result = await response.json();
    const content = result.choices?.[0]?.message?.content?.trim() || '';

    return new Response(
      JSON.stringify({ success: true, content, response: content, message: content }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  } catch (error) {
    console.error('writing-tool error:', error);
    return new Response(
      JSON.stringify({ success: false, error: String(error), content: '' }),
      { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }
});
