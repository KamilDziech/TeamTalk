/**
 * Supabase Edge Function: transcribe-audio
 *
 * Accepts audio file and returns transcription using OpenAI Whisper API.
 * Keeps API key secure on server side.
 */

import { serve } from 'https://deno.land/std@0.168.0/http/server.ts';

// CORS headers
const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

// Hallucination patterns - common AI hallucinations for empty/silent recordings
const HALLUCINATION_PATTERNS = [
  /^dzi[eę]kuj[eę]?\s*(za\s*ogl[aą]danie|za\s*uwag[eę])/i,
  /^thank\s*you\s*(for\s*watching|for\s*listening)/i,
  /^thanks\s*for\s*watching/i,
  /^subscribe/i,
  /^like\s*(and\s*)?subscribe/i,
  /^napisy\s*(stworzone|wygenerowane)/i,
  /^subtitles?\s*(by|created)/i,
  /^\.+$/,
  /^\s*$/,
  /^(do\s*)?zobaczenia/i,
  /^see\s*you/i,
  /^bye(\s*bye)?$/i,
  /^(to|te)?\s*(tyle|wszystko)/i,
  /^muzyka$/i,
  /^music$/i,
  /^\[.*\]$/,
];

const MIN_TRANSCRIPTION_LENGTH = 10;

function isHallucinationOrEmpty(text: string | null): boolean {
  if (!text) return true;

  const trimmed = text.trim();

  if (trimmed.length < MIN_TRANSCRIPTION_LENGTH) return true;

  for (const pattern of HALLUCINATION_PATTERNS) {
    if (pattern.test(trimmed)) {
      console.log('Hallucination detected:', trimmed);
      return true;
    }
  }

  return false;
}

serve(async (req) => {
  // Handle CORS preflight
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders });
  }

  try {
    const openaiApiKey = Deno.env.get('OPENAI_API_KEY');
    if (!openaiApiKey) {
      console.error('OPENAI_API_KEY not configured');
      return new Response(
        JSON.stringify({ error: 'OpenAI API key not configured on server' }),
        { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    // Get form data with audio file
    const formData = await req.formData();
    const audioFile = formData.get('file');

    if (!audioFile || !(audioFile instanceof File)) {
      return new Response(
        JSON.stringify({ error: 'No audio file provided' }),
        { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    console.log('Received audio file:', audioFile.name, 'size:', audioFile.size);

    // Prepare form data for OpenAI
    const whisperFormData = new FormData();
    whisperFormData.append('file', audioFile, audioFile.name || 'audio.m4a');
    whisperFormData.append('model', 'whisper-1');
    whisperFormData.append('language', 'pl');

    // Call OpenAI Whisper API
    const whisperResponse = await fetch('https://api.openai.com/v1/audio/transcriptions', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${openaiApiKey}`,
      },
      body: whisperFormData,
    });

    if (!whisperResponse.ok) {
      const errorText = await whisperResponse.text();
      console.error('Whisper API error:', errorText);
      return new Response(
        JSON.stringify({ error: 'Transcription failed', details: errorText }),
        { status: whisperResponse.status, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const result = await whisperResponse.json();
    const transcription = result.text;

    console.log('Transcription completed:', transcription?.substring(0, 100));

    // Check for hallucination
    if (isHallucinationOrEmpty(transcription)) {
      return new Response(
        JSON.stringify({ transcription: null, isEmptyOrHallucination: true }),
        { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    return new Response(
      JSON.stringify({ transcription, isEmptyOrHallucination: false }),
      { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  } catch (error) {
    console.error('Error in transcribe-audio:', error);
    return new Response(
      JSON.stringify({ error: 'Internal server error', details: error.message }),
      { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }
});
