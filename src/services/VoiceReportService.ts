/**
 * VoiceReportService
 *
 * Handles voice note recording, upload and transcription.
 *
 * Workflow:
 * 1. Record audio using expo-av
 * 2. Upload to Supabase Storage
 * 3. Transcribe using OpenAI Whisper API
 * 4. Save voice_report to database
 */

import { Audio } from 'expo-av';
import * as FileSystem from 'expo-file-system/legacy';
import { supabase, supabaseUrl } from '@/api/supabaseClient';
import AsyncStorage from '@react-native-async-storage/async-storage';

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

      // Create new recording
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

      // Create and play sound
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
   * Transcribe audio using Supabase Edge Function (which calls OpenAI Whisper)
   * Returns 'ERROR_EMPTY' if transcription is empty, too short, or appears to be hallucination
   */
  async transcribeAudio(audioUrl: string): Promise<string | null> {
    try {
      console.log('Transcribing audio via Edge Function...');

      // React Native requires file object format for FormData
      const formData = new FormData();
      formData.append('file', {
        uri: audioUrl,
        type: 'audio/m4a',
        name: 'audio.m4a',
      } as any);

      // Call Supabase Edge Function
      const { data: { session } } = await supabase.auth.getSession();

      const response = await fetch(`${supabaseUrl}/functions/v1/transcribe-audio`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${session?.access_token || ''}`,
        },
        body: formData,
      });

      if (!response.ok) {
        const errorData = await response.text();
        console.error('Edge Function error:', errorData);
        return null;
      }

      const result = await response.json();

      if (result.isEmptyOrHallucination) {
        console.log('Transcription appears to be empty or hallucination');
        return 'ERROR_EMPTY';
      }

      console.log('Transcription completed:', result.transcription?.substring(0, 100) + '...');
      return result.transcription;
    } catch (error) {
      console.error('Error transcribing audio:', error);
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
   * Delete local audio file
   */
  async deleteLocalAudio(audioUri: string): Promise<void> {
    try {
      const fileInfo = await FileSystem.getInfoAsync(audioUri);
      if (fileInfo.exists) {
        await FileSystem.deleteAsync(audioUri, { idempotent: true });
        console.log('Local audio file deleted:', audioUri);
      }
    } catch (error) {
      console.error('Error deleting local audio:', error);
    }
  }

  /**
   * Full workflow: Transcribe from local file and save text only
   * Audio is deleted after successful transcription (not stored in database)
   */
  async processVoiceReport(
    callLogId: string,
    audioUri: string
  ): Promise<{ success: boolean; error?: string }> {
    try {
      // Step 1: Transcribe from local file
      const transcription = await this.transcribeAudio(audioUri);

      if (!transcription) {
        // Save to pending uploads for retry
        await this.addToPendingUploads(callLogId, audioUri);
        return { success: false, error: 'Failed to transcribe audio. Saved for retry.' };
      }

      // Step 2: Save to database (text only, no audio URL)
      const saved = await this.saveVoiceReport(callLogId, null, transcription, null);
      if (!saved) {
        return { success: false, error: 'Failed to save voice report to database.' };
      }

      // Step 3: Delete local audio file after successful save
      await this.deleteLocalAudio(audioUri);

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
