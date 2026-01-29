/**
 * VoiceReportService
 *
 * Handles voice note recording, upload, transcription and AI summarization.
 *
 * Workflow:
 * 1. Record audio using expo-audio
 * 2. Upload to Supabase Storage
 * 3. Transcribe using OpenAI Whisper API
 * 4. Summarize using Claude API
 * 5. Save voice_report to database
 *
 * Phase 4 implementation
 */

import { AudioModule, RecordingPresets, setAudioModeAsync, createAudioPlayer, requestRecordingPermissionsAsync } from 'expo-audio';
import type { AudioPlayer, AudioRecorder, RecorderState } from 'expo-audio';
import * as FileSystem from 'expo-file-system/legacy';
import { supabase } from '@/api/supabaseClient';
import AsyncStorage from '@react-native-async-storage/async-storage';
import Constants from 'expo-constants';
import { Platform } from 'react-native';

// Types
export interface VoiceReportData {
  callLogId: string;
  audioUri: string;
  transcription?: string;
  aiSummary?: string;
}

export interface PendingUpload {
  id: string;
  callLogId: string;
  audioUri: string;
  createdAt: string;
}

const PENDING_UPLOADS_KEY = 'voice_reports_pending_uploads';

// Hallucination patterns - common AI hallucinations for empty/silent recordings
const HALLUCINATION_PATTERNS = [
  /^dzi[eę]kuj[eę]?\s*(za\s*ogl[aą]danie|za\s*uwag[eę])/i,
  /^thank\s*you\s*(for\s*watching|for\s*listening)/i,
  /^thanks\s*for\s*watching/i,
  /^subscribe/i,
  /^like\s*(and\s*)?subscribe/i,
  /^napisy\s*(stworzone|wygenerowane)/i,
  /^subtitles?\s*(by|created)/i,
  /^\.+$/,  // Only dots
  /^\s*$/,  // Empty or whitespace only
  /^(do\s*)?zobaczenia/i,
  /^see\s*you/i,
  /^bye(\s*bye)?$/i,
  /^(to|te)?\s*(tyle|wszystko)/i,
  /^muzyka$/i,
  /^music$/i,
  /^\[.*\]$/,  // Just brackets like [muzyka] or [silence]
];

// Minimum transcription length to be considered valid
const MIN_TRANSCRIPTION_LENGTH = 10;

/**
 * Check if transcription appears to be a hallucination or empty
 */
const isHallucinationOrEmpty = (text: string | null): boolean => {
  if (!text) return true;

  const trimmed = text.trim();

  // Check length
  if (trimmed.length < MIN_TRANSCRIPTION_LENGTH) return true;

  // Check against hallucination patterns
  for (const pattern of HALLUCINATION_PATTERNS) {
    if (pattern.test(trimmed)) {
      console.log('Hallucination detected:', trimmed);
      return true;
    }
  }

  return false;
};

// Get API keys from environment
const getOpenAIKey = () => Constants.expoConfig?.extra?.openaiApiKey || process.env.OPENAI_API_KEY;
const getClaudeKey = () => Constants.expoConfig?.extra?.claudeApiKey || process.env.CLAUDE_API_KEY;

export class VoiceReportService {
  private recording: AudioRecorder | null = null;
  private player: AudioPlayer | null = null;

  /**
   * Request microphone permissions
   */
  async requestPermissions(): Promise<boolean> {
    try {
      const { status } = await requestRecordingPermissionsAsync();
      if (status !== 'granted') {
        console.warn('Microphone permission not granted');
        return false;
      }

      // Configure audio mode for recording
      await setAudioModeAsync({
        allowsRecording: true,
        playsInSilentMode: true,
      });

      return true;
    } catch (error) {
      console.error('Error requesting audio permissions:', error);
      return false;
    }
  }

  /**
   * Start recording audio
   */
  async startRecording(): Promise<boolean> {
    try {
      // Check permissions first
      const hasPermission = await this.requestPermissions();
      if (!hasPermission) {
        return false;
      }

      // Stop any existing recording
      if (this.recording) {
        await this.stopRecording();
      }

      console.log('Starting recording...');

      // Create new recorder with HIGH_QUALITY preset
      this.recording = new AudioModule.AudioRecorder(RecordingPresets.HIGH_QUALITY);
      await this.recording.prepareToRecordAsync();
      this.recording.record();

      console.log('Recording started');
      return true;
    } catch (error) {
      console.error('Error starting recording:', error);
      return false;
    }
  }

  /**
   * Stop recording and return the audio URI
   */
  async stopRecording(): Promise<string | null> {
    if (!this.recording) {
      console.warn('No active recording to stop');
      return null;
    }

    try {
      console.log('Stopping recording...');
      await this.recording.stop();

      // Reset audio mode
      await setAudioModeAsync({
        allowsRecording: false,
      });

      const uri = this.recording.uri;
      this.recording = null;

      console.log('Recording stopped, URI:', uri);
      return uri;
    } catch (error) {
      console.error('Error stopping recording:', error);
      this.recording = null;
      return null;
    }
  }

  /**
   * Cancel recording without saving
   */
  async cancelRecording(): Promise<void> {
    if (this.recording) {
      try {
        await this.recording.stop();
        await setAudioModeAsync({
          allowsRecording: false,
        });
      } catch (error) {
        console.error('Error canceling recording:', error);
      }
      this.recording = null;
    }
  }

  /**
   * Get recording status
   */
  async getRecordingStatus(): Promise<any | null> {
    if (!this.recording) {
      return null;
    }
    return this.recording.getStatus();
  }

  /**
   * Play audio from URI
   */
  async playAudio(uri: string): Promise<void> {
    try {
      // Stop any existing playback
      if (this.player) {
        this.player.remove();
      }

      // Create player with the audio URI
      this.player = createAudioPlayer({ uri }, { updateInterval: 100, keepAudioSessionActive: false });
      this.player.play();
    } catch (error) {
      console.error('Error playing audio:', error);
    }
  }

  /**
   * Stop audio playback
   */
  async stopPlayback(): Promise<void> {
    if (this.player) {
      try {
        this.player.pause();
        this.player.remove();
      } catch (error) {
        console.error('Error stopping playback:', error);
      }
      this.player = null;
    }
  }

  /**
   * Upload audio file to Supabase Storage
   */
  async uploadAudio(audioUri: string, callLogId: string): Promise<string | null> {
    try {
      console.log('Uploading audio to Supabase Storage...');

      // Read file as base64
      const base64 = await FileSystem.readAsStringAsync(audioUri, {
        encoding: 'base64',
      });

      // Generate unique filename
      const timestamp = Date.now();
      const filename = `voice_reports/${callLogId}_${timestamp}.m4a`;

      // Convert base64 to Uint8Array for upload
      const binaryString = atob(base64);
      const bytes = new Uint8Array(binaryString.length);
      for (let i = 0; i < binaryString.length; i++) {
        bytes[i] = binaryString.charCodeAt(i);
      }

      // Upload to Supabase Storage
      const { data, error } = await supabase.storage
        .from('voice-reports')
        .upload(filename, bytes, {
          contentType: 'audio/m4a',
          upsert: false,
        });

      if (error) {
        console.error('Error uploading audio:', error);
        return null;
      }

      // Get public URL
      const { data: urlData } = supabase.storage
        .from('voice-reports')
        .getPublicUrl(data.path);

      console.log('Audio uploaded successfully:', urlData.publicUrl);
      return urlData.publicUrl;
    } catch (error) {
      console.error('Error in uploadAudio:', error);
      return null;
    }
  }

  /**
   * Transcribe audio using OpenAI Whisper API
   * Returns 'ERROR_EMPTY' if transcription is empty, too short, or appears to be hallucination
   */
  async transcribeAudio(audioUrl: string): Promise<string | null> {
    const apiKey = getOpenAIKey();
    if (!apiKey) {
      console.warn('OpenAI API key not configured, skipping transcription');
      return null;
    }

    try {
      console.log('Transcribing audio with Whisper...');

      // React Native requires file object format for FormData
      const formData = new FormData();
      formData.append('file', {
        uri: audioUrl,
        type: 'audio/m4a',
        name: 'audio.m4a',
      } as any);
      formData.append('model', 'whisper-1');
      formData.append('language', 'pl'); // Polish language

      const whisperResponse = await fetch('https://api.openai.com/v1/audio/transcriptions', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${apiKey}`,
        },
        body: formData,
      });

      if (!whisperResponse.ok) {
        const errorData = await whisperResponse.text();
        console.error('Whisper API error:', errorData);
        return null;
      }

      const result = await whisperResponse.json();
      const transcription = result.text;

      console.log('Transcription completed:', transcription?.substring(0, 100) + '...');

      // Check for hallucination or empty transcription
      if (isHallucinationOrEmpty(transcription)) {
        console.log('Transcription appears to be empty or hallucination');
        return 'ERROR_EMPTY';
      }

      return transcription;
    } catch (error) {
      console.error('Error transcribing audio:', error);
      return null;
    }
  }

  /**
   * Generate AI summary using Claude API
   * Returns 'ERROR_EMPTY' if the transcription appears to be invalid or hallucination
   */
  async generateSummary(transcription: string, clientName?: string): Promise<string | null> {
    const apiKey = getClaudeKey();
    if (!apiKey) {
      console.warn('Claude API key not configured, skipping summary');
      return null;
    }

    // Don't process if transcription is marked as empty
    if (transcription === 'ERROR_EMPTY') {
      return 'ERROR_EMPTY';
    }

    try {
      console.log('Generating AI summary with Claude...');

      const systemPrompt = `Jesteś asystentem CRM pomagającym w zarządzaniu relacjami z klientami.
Twoim zadaniem jest streszczenie notatki głosowej z rozmowy telefonicznej.

WAŻNE: Jeśli otrzymana transkrypcja jest:
- pusta lub zawiera tylko znaki przestankowe
- niezrozumiała lub wydaje się być błędem transkrypcji
- zawiera typowe halucynacje AI (np. "Dziękuję za oglądanie", "Subscribe", "Napisy stworzone przez...")
- nie zawiera żadnej merytorycznej treści rozmowy

Wtedy zwróć TYLKO słowo: ERROR_EMPTY
Nie wymyślaj treści notatki - jeśli nie ma sensu, zwróć ERROR_EMPTY.

Jeśli transkrypcja jest prawidłowa, stwórz zwięzłe podsumowanie w formie:
1. **Temat rozmowy:** (1-2 zdania)
2. **Ustalenia:** (lista punktów)
3. **Zadania do wykonania:** (lista punktów, jeśli są)

Odpowiadaj po polsku. Bądź zwięzły i konkretny.`;

      const userPrompt = clientName
        ? `Notatka z rozmowy z klientem "${clientName}":\n\n${transcription}`
        : `Notatka z rozmowy:\n\n${transcription}`;

      const claudeResponse = await fetch('https://api.anthropic.com/v1/messages', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'x-api-key': apiKey,
          'anthropic-version': '2023-06-01',
        },
        body: JSON.stringify({
          model: 'claude-3-haiku-20240307',
          max_tokens: 1024,
          system: systemPrompt,
          messages: [
            { role: 'user', content: userPrompt },
          ],
        }),
      });

      if (!claudeResponse.ok) {
        const errorData = await claudeResponse.text();
        console.error('Claude API error:', errorData);
        return null;
      }

      const result = await claudeResponse.json();
      const summary = result.content?.[0]?.text?.trim() || null;
      console.log('Summary generated:', summary?.substring(0, 100) + '...');

      // Check if Claude detected invalid transcription
      if (summary === 'ERROR_EMPTY' || summary?.startsWith('ERROR_EMPTY')) {
        console.log('Claude detected invalid transcription');
        return 'ERROR_EMPTY';
      }

      return summary;
    } catch (error) {
      console.error('Error generating summary:', error);
      return null;
    }
  }

  /**
   * Save voice report to database
   */
  async saveVoiceReport(
    callLogId: string,
    audioUrl: string | null,
    transcription: string | null,
    aiSummary: string | null
  ): Promise<boolean> {
    try {
      console.log('Saving voice report to database...');

      // Get current user ID
      const { data: { user } } = await supabase.auth.getUser();
      const createdBy = user?.id || null;

      const { error } = await supabase.from('voice_reports').insert({
        call_log_id: callLogId,
        audio_url: audioUrl,
        transcription: transcription,
        ai_summary: aiSummary,
        created_by: createdBy,
      });

      if (error) {
        console.error('Error saving voice report:', error);
        return false;
      }

      console.log('Voice report saved successfully');
      return true;
    } catch (error) {
      console.error('Error in saveVoiceReport:', error);
      return false;
    }
  }

  /**
   * Full workflow: Upload, transcribe, summarize and save
   */
  async processVoiceReport(
    callLogId: string,
    audioUri: string,
    clientName?: string
  ): Promise<{ success: boolean; error?: string }> {
    try {
      // Step 1: Upload audio
      const audioUrl = await this.uploadAudio(audioUri, callLogId);
      if (!audioUrl) {
        // Save to pending uploads for retry
        await this.addToPendingUploads(callLogId, audioUri);
        return { success: false, error: 'Failed to upload audio. Saved for retry.' };
      }

      // Step 2: Transcribe
      const transcription = await this.transcribeAudio(audioUrl);

      // Step 3: Generate summary (only if transcription succeeded)
      let aiSummary: string | null = null;
      if (transcription) {
        aiSummary = await this.generateSummary(transcription, clientName);
      }

      // Step 4: Save to database
      const saved = await this.saveVoiceReport(callLogId, audioUrl, transcription, aiSummary);
      if (!saved) {
        return { success: false, error: 'Failed to save voice report to database.' };
      }

      return { success: true };
    } catch (error) {
      console.error('Error processing voice report:', error);
      await this.addToPendingUploads(callLogId, audioUri);
      return { success: false, error: 'Processing failed. Saved for retry.' };
    }
  }

  /**
   * Add to pending uploads queue (for offline support)
   */
  private async addToPendingUploads(callLogId: string, audioUri: string): Promise<void> {
    try {
      const existingData = await AsyncStorage.getItem(PENDING_UPLOADS_KEY);
      const pending: PendingUpload[] = existingData ? JSON.parse(existingData) : [];

      pending.push({
        id: `${callLogId}_${Date.now()}`,
        callLogId,
        audioUri,
        createdAt: new Date().toISOString(),
      });

      await AsyncStorage.setItem(PENDING_UPLOADS_KEY, JSON.stringify(pending));
      console.log('Added to pending uploads queue');
    } catch (error) {
      console.error('Error adding to pending uploads:', error);
    }
  }

  /**
   * Get pending uploads
   */
  async getPendingUploads(): Promise<PendingUpload[]> {
    try {
      const data = await AsyncStorage.getItem(PENDING_UPLOADS_KEY);
      return data ? JSON.parse(data) : [];
    } catch (error) {
      console.error('Error getting pending uploads:', error);
      return [];
    }
  }

  /**
   * Remove from pending uploads
   */
  async removePendingUpload(id: string): Promise<void> {
    try {
      const existingData = await AsyncStorage.getItem(PENDING_UPLOADS_KEY);
      const pending: PendingUpload[] = existingData ? JSON.parse(existingData) : [];
      const updated = pending.filter((p) => p.id !== id);
      await AsyncStorage.setItem(PENDING_UPLOADS_KEY, JSON.stringify(updated));
    } catch (error) {
      console.error('Error removing pending upload:', error);
    }
  }

  /**
   * Process all pending uploads (call when online)
   */
  async processPendingUploads(): Promise<number> {
    const pending = await this.getPendingUploads();
    let processedCount = 0;

    for (const upload of pending) {
      const result = await this.processVoiceReport(upload.callLogId, upload.audioUri);
      if (result.success) {
        await this.removePendingUpload(upload.id);
        processedCount++;
      }
    }

    return processedCount;
  }
}

// Export singleton instance
export const voiceReportService = new VoiceReportService();
