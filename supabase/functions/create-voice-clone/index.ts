
import "https://deno.land/x/xhr@0.1.0/mod.ts";
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { corsHeaders, secureEdgeRequest } from "../_shared/security.ts";

const audioMimeMap: Record<string, string> = {
  m4a: 'audio/mp4',
  mp4: 'audio/mp4',
  mp3: 'audio/mpeg',
  wav: 'audio/wav',
  webm: 'audio/webm',
  ogg: 'audio/ogg',
  oga: 'audio/ogg',
  flac: 'audio/flac',
  aac: 'audio/aac',
  caf: 'audio/x-caf',
  '3gp': 'audio/3gpp',
}

function resolveAudioMeta(format?: string) {
  const ext = (format || 'webm').toLowerCase().replace('.', '')
  const safeExt = audioMimeMap[ext] ? ext : 'webm'
  return {
    mimeType: audioMimeMap[safeExt] || 'audio/webm',
    fileName: `audio.${safeExt}`,
  }
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response(null, { headers: corsHeaders });
  }

  const auth = await secureEdgeRequest(req, "create-voice-clone");
  if (auth instanceof Response) return auth;

  try {
    const { name, audioBase64, description, format } = await req.json();

    if (!name || !audioBase64) {
      return new Response(
        JSON.stringify({ error: 'Name and audio data are required' }),
        { status: 400, headers: corsHeaders }
      );
    }

    const elevenLabsApiKey = Deno.env.get('ELEVENLABS_API_KEY');
    if (!elevenLabsApiKey) {
      throw new Error('ElevenLabs API key not configured');
    }

    console.log('Creating voice clone:', name);

    // Convert base64 to blob for ElevenLabs
    const audioBuffer = Uint8Array.from(atob(audioBase64), c => c.charCodeAt(0));
    const audioMeta = resolveAudioMeta(format);
    const audioBlob = new Blob([audioBuffer], { type: audioMeta.mimeType });

    // Create FormData for ElevenLabs API
    const formData = new FormData();
    formData.append('name', name);
    formData.append('description', description || `Voice clone: ${name}`);
    formData.append('files', audioBlob, audioMeta.fileName);

    // Create voice clone with ElevenLabs
    const response = await fetch('https://api.elevenlabs.io/v1/voices/add', {
      method: 'POST',
      headers: {
        'xi-api-key': elevenLabsApiKey,
      },
      body: formData,
    });

    if (!response.ok) {
      const errorText = await response.text();
      console.error('ElevenLabs API error:', response.status, errorText);
      throw new Error(`ElevenLabs API error: ${response.status} - ${errorText}`);
    }

    const result = await response.json();
    console.log('Voice clone created successfully:', result.voice_id);

    return new Response(
      JSON.stringify({
        success: true,
        voiceId: result.voice_id,
        name: name,
        message: 'Voice clone created successfully'
      }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );

  } catch (error) {
    console.error('Voice clone creation error:', error);
    return new Response(
      JSON.stringify({ 
        error: error.message || 'Voice clone creation failed',
        timestamp: new Date().toISOString()
      }),
      { status: 500, headers: corsHeaders }
    );
  }
});
