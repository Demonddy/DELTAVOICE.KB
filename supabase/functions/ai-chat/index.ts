import "https://deno.land/x/xhr@0.1.0/mod.ts";
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { corsHeaders, secureEdgeRequest } from "../_shared/security.ts";

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response(null, { headers: corsHeaders });
  }

  const auth = await secureEdgeRequest(req, "ai-chat");
  if (auth instanceof Response) return auth;

  try {
    const openAIApiKey = Deno.env.get('OPENAI_API_KEY77') || Deno.env.get('OPENAI_API_KEY');
    
    if (!openAIApiKey) {
      console.error('OpenAI API key not configured');
      return new Response(
        JSON.stringify({ 
          success: false,
          error: 'OpenAI API key not configured',
          content: "I'm having trouble connecting. Please try again later.",
          response: "I'm having trouble connecting. Please try again later."
        }),
        { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const { messages } = await req.json();

    if (!messages || !Array.isArray(messages)) {
      return new Response(
        JSON.stringify({ 
          success: false,
          error: 'Messages array is required',
          content: "Please send a message to start chatting!",
          response: "Please send a message to start chatting!"
        }),
        { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    console.log('🤖 AI Chat request - messages count:', messages.length);
    console.log('🤖 Last message:', messages[messages.length - 1]?.content?.substring(0, 100));

    const systemMessage = {
      role: "system",
      content: `You are a helpful, friendly AI assistant integrated into a mobile keyboard app. 
Your name is DeltaVoice AI. Be concise but informative (2-4 sentences typically).
Use emojis occasionally to be friendly 😊.
Respond in the same language the user writes in.
If asked to write something (email, message, etc.), provide complete, ready-to-use content.
For translations, provide the translation directly without extra explanation.
Be helpful, accurate, and conversational like ChatGPT.`
    };

    const MAX_MESSAGES = 50;
    const sanitizedMessages = messages
      .filter((m: { role?: string }) => m.role === "user" || m.role === "assistant")
      .slice(-MAX_MESSAGES);
    const apiMessages = [systemMessage, ...sanitizedMessages];

    // Call OpenAI API
    const response = await fetch('https://api.openai.com/v1/chat/completions', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${openAIApiKey}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        model: 'gpt-4o-mini',
        messages: apiMessages,
        max_tokens: 1000,
        temperature: 0.7,
        presence_penalty: 0.1,
        frequency_penalty: 0.1,
      }),
    });

    if (!response.ok) {
      const errorText = await response.text();
      console.error('🤖 OpenAI API error:', response.status, errorText);
      
      return new Response(
        JSON.stringify({ 
          success: false,
          error: 'AI service temporarily unavailable',
          content: "I'm experiencing some issues. Please try again in a moment. 🔄",
          response: "I'm experiencing some issues. Please try again in a moment. 🔄"
        }),
        { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const result = await response.json();
    const aiResponse = result.choices[0]?.message?.content || "I couldn't generate a response. Please try again.";

    console.log('🤖 AI response generated successfully:', aiResponse.substring(0, 100));

    return new Response(
      JSON.stringify({ 
        success: true,
        content: aiResponse,
        response: aiResponse,
        message: aiResponse
      }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );

  } catch (error) {
    console.error('🤖 AI Chat error:', error);
    
    return new Response(
      JSON.stringify({ 
        success: false,
        error: error instanceof Error ? error.message : String(error),
        content: "Sorry, I encountered an error. Please try again. 🔄",
        response: "Sorry, I encountered an error. Please try again. 🔄"
      }),
      { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }
});
