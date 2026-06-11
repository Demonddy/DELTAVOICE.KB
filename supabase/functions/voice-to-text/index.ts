
import "https://deno.land/x/xhr@0.1.0/mod.ts"
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { corsHeaders, secureEdgeRequest } from "../_shared/security.ts"

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

// Improved base64 processing with better error handling
function processBase64Safely(base64String: string) {
  try {
    console.log(`Processing base64 string of length: ${base64String.length}`);
    
    // For smaller files, use simple approach
    if (base64String.length < 100000) { // ~75KB
      const binaryString = atob(base64String);
      const bytes = new Uint8Array(binaryString.length);
      for (let i = 0; i < binaryString.length; i++) {
        bytes[i] = binaryString.charCodeAt(i);
      }
      console.log(`Direct processing: ${bytes.length} bytes`);
      return bytes;
    }
    
    // For larger files, use chunked approach
    const chunkSize = 8192; // Smaller chunks for stability
    const chunks: Uint8Array[] = [];
    let position = 0;
    
    while (position < base64String.length) {
      const chunkEnd = Math.min(position + chunkSize, base64String.length);
      const chunk = base64String.slice(position, chunkEnd);
      
      try {
        const binaryString = atob(chunk);
        const bytes = new Uint8Array(binaryString.length);
        
        for (let i = 0; i < binaryString.length; i++) {
          bytes[i] = binaryString.charCodeAt(i);
        }
        
        chunks.push(bytes);
        position = chunkEnd;
        
      } catch (chunkError) {
        console.error(`Error processing chunk at position ${position}:`, chunkError);
        throw new Error(`Invalid base64 data at position ${position}`);
      }
    }

    // Combine chunks
    const totalLength = chunks.reduce((sum, chunk) => sum + chunk.length, 0);
    const result = new Uint8Array(totalLength);
    let offset = 0;

    for (const chunk of chunks) {
      result.set(chunk, offset);
      offset += chunk.length;
    }

    console.log(`Chunked processing complete: ${result.length} bytes`);
    return result;
    
  } catch (error) {
    console.error('Base64 processing error:', error);
    throw new Error(`Failed to decode audio data: ${error.message}`);
  }
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  const auth = await secureEdgeRequest(req, "voice-to-text");
  if (auth instanceof Response) return auth;

  try {
    console.log('Voice-to-text function called');
    const { audio, language, format } = await req.json()
    
    if (!audio) {
      console.error('No audio data provided');
      return new Response(
        JSON.stringify({ error: 'No audio data provided' }),
        {
          status: 400,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        }
      )
    }

    console.log('Processing audio data, base64 length:', audio.length);
    console.log('Target language:', language || 'auto-detect');

    // Validate API key
    const apiKey = Deno.env.get('OPENAI_API_KEY');
    if (!apiKey) {
      console.error('OpenAI API key not found');
      return new Response(
        JSON.stringify({ error: 'OpenAI API key not configured' }),
        {
          status: 500,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        }
      )
    }

    // Process audio safely to prevent stack overflow
    const binaryAudio = processBase64Safely(audio);
    
    // Prepare form data with proper mime type detection
    const formData = new FormData()
    const audioMeta = resolveAudioMeta(format)
    const blob = new Blob([binaryAudio], { type: audioMeta.mimeType })
    formData.append('file', blob, audioMeta.fileName)
    formData.append('model', 'whisper-1')
    
    // Add language parameter if specified, otherwise let Whisper auto-detect
    if (language && language !== 'auto') {
      formData.append('language', language)
      console.log(`Using specified language: ${language}`);
    } else {
      console.log('Using auto-detection for language');
    }

    console.log('Sending request to OpenAI Whisper API');

    // Send to OpenAI with enhanced error handling
    const response = await fetch('https://api.openai.com/v1/audio/transcriptions', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${apiKey}`,
      },
      body: formData,
    })

    console.log(`OpenAI API response status: ${response.status}`);

    if (!response.ok) {
      const errorText = await response.text();
      console.error(`OpenAI API error: ${response.status} - ${errorText}`);
      
      // Handle specific error cases
      if (response.status === 429) {
        return new Response(
          JSON.stringify({ 
            error: 'OpenAI API quota exceeded. Please check your OpenAI billing and usage limits.',
            details: 'Your OpenAI account has reached its usage quota. Please add credits or upgrade your plan.',
            status: response.status
          }),
          {
            status: 429,
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
          }
        )
      }

      if (response.status === 401) {
        return new Response(
          JSON.stringify({ 
            error: 'Invalid OpenAI API key. Please check your API key configuration.',
            details: 'The provided OpenAI API key is invalid or has been revoked.',
            status: response.status
          }),
          {
            status: 401,
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
          }
        )
      }

      if (response.status === 400) {
        return new Response(
          JSON.stringify({ 
            error: 'Invalid audio format or corrupted audio data.',
            details: 'The audio file format is not supported or the audio data is corrupted.',
            status: response.status
          }),
          {
            status: 400,
            headers: { ...corsHeaders, 'Content-Type': 'application/json' },
          }
        )
      }

      return new Response(
        JSON.stringify({ 
          error: `OpenAI API error: ${response.status}`,
          details: errorText,
          status: response.status
        }),
        {
          status: response.status,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        }
      )
    }

    const result = await response.json()
    console.log('Transcription successful:', result);

    return new Response(
      JSON.stringify({ 
        text: result.text || '',
        language: result.language || 'unknown'
      }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    )

  } catch (error) {
    console.error('Error in voice-to-text function:', error);
    return new Response(
      JSON.stringify({ 
        error: error.message,
        details: 'Check the function logs for more information. For long recordings, consider breaking them into shorter segments.',
        timestamp: new Date().toISOString()
      }),
      {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      }
    )
  }
})
