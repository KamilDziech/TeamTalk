/**
 * VoiceReportService
 *
 * Handles voice note recording, upload, transcription and AI summarization.
 *
 * Workflow:
 * 1. Record audio using expo-av
 * 2. Upload to Supabase Storage
 * 3. Transcribe using OpenAI Whisper API
 * 4. Summarize using Claude API
 * 5. Save voice_report to database
 *
 * Phase 4 implementation
 */

import { Audio } from 'expo-av';
import * as FileSystem from 'expo-file-system';
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

// Get API keys from environment
const getOpenAIKey = () => Constants.expoConfig?.extra?.openaiApiKey || process.env.OPENAI_API_KEY;
const getClaudeKey = () => Constants.expoConfig?.extra?.claudeApiKey || process.env.CLAUDE_API_KEY;

export class VoiceReportService {
  private recording: Audio.Recording | null = null;
  private sound: Audio.Sound | null = null;

  /**
   * Request microphone permissions
   */
  async requestPermissions(): Promise<boolean> {
    try {
      const { status } = await Audio.requestPermissionsAsync();
      if (status !== 'granted') {
        console.warn('Microphone permission not granted');
        return false;
      }

      // Configure audio mode for recording
      await Audio.setAudioModeAsync({
        allowsRecordingIOS: true,
        playsInSilentModeIOS: true,
        staysActiveInBackground: false,
        shouldDuckAndroid: true,
        playThroughEarpieceAndroid: false,
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

      const { recording } = await Audio.Recording.createAsync(
        Audio.RecordingOptionsPresets.HIGH_QUALITY
      );

      this.recording = recording;
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
      await this.recording.stopAndUnloadAsync();

      // Reset audio mode
      await Audio.setAudioModeAsync({
        allowsRecordingIOS: false,
      });

      const uri = this.recording.getURI();
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
        await this.recording.stopAndUnloadAsync();
        await Audio.setAudioModeAsync({
          allowsRecordingIOS: false,
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
  async getRecordingStatus(): Promise<Audio.RecordingStatus | null> {
    if (!this.recording) {
      return null;
    }
    return this.recording.getStatusAsync();
  }

  /**
   * Play audio from URI
   */
  async playAudio(uri: string): Promise<void> {
    try {
      // Stop any existing playback
      if (this.sound) {
        await this.sound.unloadAsync();
      }

      const { sound } = await Audio.Sound.createAsync({ uri });
      this.sound = sound;
      await sound.playAsync();
    } catch (error) {
      console.error('Error playing audio:', error);
    }
  }

  /**
   * Stop audio playback
   */
  async stopPlayback(): Promise<void> {
    if (this.sound) {
      try {
        await this.sound.stopAsync();
        await this.sound.unloadAsync();
      } catch (error) {
        console.error('Error stopping playback:', error);
      }
      this.sound = null;
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
   */
  async transcribeAudio(audioUrl: string): Promise<string | null> {
    const apiKey = getOpenAIKey();
    if (!apiKey) {
      console.warn('OpenAI API key not configured, skipping transcription');
      return null;
    }

    try {
      console.log('Transcribing audio with Whisper...');

      // Download audio file
      const response = await fetch(audioUrl);
      const blob = await response.blob();

      // Create form data for Whisper API
      const formData = new FormData();
      formData.append('file', blob, 'audio.m4a');
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
      console.log('Transcription completed:', result.text?.substring(0, 100) + '...');
      return result.text;
    } catch (error) {
      console.error('Error transcribing audio:', error);
      return null;
    }
  }

  /**
   * Generate AI summary using Claude API
   */
  async generateSummary(transcription: string, clientName?: string): Promise<string | null> {
    const apiKey = getClaudeKey();
    if (!apiKey) {
      console.warn('Claude API key not configured, skipping summary');
      return null;
    }

    try {
      console.log('Generating AI summary with Claude...');

      const systemPrompt = `Jesteś asystentem CRM pomagającym w zarządzaniu relacjami z klientami.
Twoim zadaniem jest streszczenie notatki głosowej z rozmowy telefonicznej.

Stwórz zwięzłe podsumowanie w formie:
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
      const summary = result.content?.[0]?.text || null;
      console.log('Summary generated:', summary?.substring(0, 100) + '...');
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

      const { error } = await supabase.from('voice_reports').insert({
        call_log_id: callLogId,
        audio_url: audioUrl,
        transcription: transcription,
        ai_summary: aiSummary,
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
