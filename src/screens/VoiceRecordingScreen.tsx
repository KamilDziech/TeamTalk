/**
 * VoiceRecordingScreen
 *
 * Screen for recording voice notes for call logs.
 * Provides UI for recording, playback, and processing.
 *
 * Phase 4 implementation
 */

import React, { useState, useEffect, useRef } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ActivityIndicator,
  Alert,
  SafeAreaView,
  TextInput,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
} from 'react-native';
import { voiceReportService } from '@/services/VoiceReportService';
import { deviceService } from '@/services/DeviceService';
import { useTheme } from '@/contexts/ThemeContext';
import { spacing, radius, typography } from '@/styles/theme';
import type { Client } from '@/types';

export type NoteMode = 'voice' | 'text';

interface VoiceRecordingScreenProps {
  callLogId: string;
  client: Client | null;
  callerPhone?: string | null;
  mode?: NoteMode;
  onComplete: () => void;
  onCancel: () => void;
}

type RecordingState = 'idle' | 'recording' | 'recorded' | 'processing' | 'completed' | 'error';

const MIN_RECORDING_DURATION_SECONDS = 2;

export const VoiceRecordingScreen: React.FC<VoiceRecordingScreenProps> = ({
  callLogId,
  client,
  callerPhone,
  mode = 'voice',
  onComplete,
  onCancel,
}) => {
  // Display name: prefer client name, fall back to caller phone
  const displayName = client?.name || callerPhone || 'Nieznany numer';
  const displayPhone = client?.phone || (callerPhone ? `+48${callerPhone}` : '');
  const { colors } = useTheme();
  const styles = createStyles(colors);
  const [state, setState] = useState<RecordingState>('idle');
  const [recordingDuration, setRecordingDuration] = useState(0);
  const [audioUri, setAudioUri] = useState<string | null>(null);
  const [processingStep, setProcessingStep] = useState('');
  const [transcription, setTranscription] = useState<string | null>(null);
  const [aiSummary, setAiSummary] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [textNote, setTextNote] = useState('');

  const timerRef = useRef<NodeJS.Timeout | null>(null);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (timerRef.current) {
        clearInterval(timerRef.current);
      }
      voiceReportService.cancelRecording();
      voiceReportService.stopPlayback();
    };
  }, []);

  // Format duration as MM:SS
  const formatDuration = (seconds: number): string => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  // Start recording
  const handleStartRecording = async () => {
    const started = await voiceReportService.startRecording();
    if (started) {
      setState('recording');
      setRecordingDuration(0);

      // Start timer
      timerRef.current = setInterval(() => {
        setRecordingDuration((prev) => prev + 1);
      }, 1000);
    } else {
      Alert.alert(
        'Brak uprawnień',
        'Aby nagrać notatkę głosową, zezwól aplikacji na dostęp do mikrofonu.'
      );
    }
  };

  // Stop recording
  const handleStopRecording = async () => {
    // Stop timer
    if (timerRef.current) {
      clearInterval(timerRef.current);
      timerRef.current = null;
    }

    // Check minimum duration
    if (recordingDuration < MIN_RECORDING_DURATION_SECONDS) {
      await voiceReportService.cancelRecording();
      setState('idle');
      setRecordingDuration(0);
      Alert.alert(
        'Nagranie zbyt krótkie',
        `Nagranie musi trwać co najmniej ${MIN_RECORDING_DURATION_SECONDS} sekundy. Spróbuj ponownie.`
      );
      return;
    }

    const uri = await voiceReportService.stopRecording();
    if (uri) {
      setAudioUri(uri);
      setState('recorded');
    } else {
      setState('idle');
      Alert.alert('Błąd', 'Nie udało się zapisać nagrania.');
    }
  };

  // Cancel recording
  const handleCancelRecording = async () => {
    if (timerRef.current) {
      clearInterval(timerRef.current);
      timerRef.current = null;
    }

    await voiceReportService.cancelRecording();
    setState('idle');
    setRecordingDuration(0);
  };

  // Play recorded audio
  const handlePlayRecording = async () => {
    if (audioUri) {
      await voiceReportService.playAudio(audioUri);
    }
  };

  // Process and save the recording
  const handleSaveRecording = async () => {
    if (!audioUri) return;

    setState('processing');
    setProcessingStep('Przesyłanie nagrania...');

    try {
      // Upload audio
      setProcessingStep('Przesyłanie nagrania...');
      const audioUrl = await voiceReportService.uploadAudio(audioUri, callLogId);

      if (!audioUrl) {
        // Save locally for retry
        setErrorMessage('Nie udało się przesłać nagrania. Zapisano do ponowienia.');
        setState('error');
        return;
      }

      // Transcribe
      setProcessingStep('Transkrypcja audio...');
      const transcriptionResult = await voiceReportService.transcribeAudio(audioUrl);

      // Check for empty/invalid transcription
      if (transcriptionResult === 'ERROR_EMPTY' || !transcriptionResult) {
        setErrorMessage('Nie wykryto mowy w nagraniu. Spróbuj nagrać jeszcze raz.');
        setState('error');
        return;
      }

      setTranscription(transcriptionResult);

      // Save to database (no AI summary)
      setProcessingStep('Zapisywanie...');
      const saved = await voiceReportService.saveVoiceReport(
        callLogId,
        audioUrl,
        transcriptionResult,
        null // no AI summary
      );

      if (saved) {
        setState('completed');

        // Notify team about new voice report
        deviceService.notifyNewVoiceReport(displayName);
      } else {
        setErrorMessage('Nie udało się zapisać notatki.');
        setState('error');
      }
    } catch (error) {
      console.error('Error processing recording:', error);
      setErrorMessage('Wystąpił błąd podczas przetwarzania.');
      setState('error');
    }
  };

  // Retry recording
  const handleRetry = () => {
    setState('idle');
    setAudioUri(null);
    setRecordingDuration(0);
    setTranscription(null);
    setAiSummary(null);
    setErrorMessage(null);
  };

  // Save text note (no audio recording)
  const handleSaveTextNote = async () => {
    if (!textNote.trim()) {
      Alert.alert('Błąd', 'Wpisz treść notatki');
      return;
    }

    setState('processing');
    setProcessingStep('Zapisywanie notatki...');

    try {
      // Save text note without audio
      const saved = await voiceReportService.saveVoiceReport(
        callLogId,
        null, // no audio
        textNote.trim(), // text as transcription
        null // no AI summary for manual notes
      );

      if (saved) {
        setState('completed');
        // Notify team about new note
        deviceService.notifyNewVoiceReport(displayName);
      } else {
        setErrorMessage('Nie udało się zapisać notatki.');
        setState('error');
      }
    } catch (error) {
      console.error('Error saving text note:', error);
      setErrorMessage('Wystąpił błąd podczas zapisywania.');
      setState('error');
    }
  };

  // Render recording button based on state
  const renderRecordButton = () => {
    if (state === 'recording') {
      return (
        <TouchableOpacity
          style={[styles.recordButton, styles.recordingActive]}
          onPress={handleStopRecording}
        >
          <View style={styles.stopIcon} />
        </TouchableOpacity>
      );
    }

    return (
      <TouchableOpacity style={styles.recordButton} onPress={handleStartRecording}>
        <View style={styles.micIcon}>
          <Text style={styles.micText}>🎤</Text>
        </View>
      </TouchableOpacity>
    );
  };

  // Render text note form for manual entry
  const renderTextNoteForm = () => {
    return (
      <View style={styles.textNoteContainer}>
        <View style={styles.textNoteHeader}>
          <Text style={styles.textNoteLabel}>Wpisz notatkę:</Text>
          <TouchableOpacity
            style={[styles.saveTextButton, !textNote.trim() && styles.saveTextButtonDisabled]}
            onPress={handleSaveTextNote}
            disabled={!textNote.trim()}
          >
            <Text style={styles.saveTextButtonText}>💾 Zapisz</Text>
          </TouchableOpacity>
        </View>

        <TextInput
          style={styles.textNoteInput}
          multiline
          placeholder="Opisz rozmowę z klientem..."
          placeholderTextColor="#999"
          value={textNote}
          onChangeText={setTextNote}
          autoFocus
        />
      </View>
    );
  };

  // Render content based on state
  const renderContent = () => {
    // Text mode uses different UI
    if (mode === 'text') {
      if (state === 'idle') {
        return renderTextNoteForm();
      }
      // For processing/completed/error states, use standard rendering
    }

    switch (state) {
      case 'idle':
        return (
          <View style={styles.recordingContainer}>
            <Text style={styles.instructionText}>
              Naciśnij przycisk, aby rozpocząć nagrywanie
            </Text>
            {renderRecordButton()}
            <Text style={styles.durationText}>00:00</Text>
          </View>
        );

      case 'recording':
        return (
          <View style={styles.recordingContainer}>
            <Text style={styles.recordingLabel}>Nagrywanie...</Text>
            {renderRecordButton()}
            <Text style={styles.durationText}>{formatDuration(recordingDuration)}</Text>
            <TouchableOpacity style={styles.cancelButton} onPress={handleCancelRecording}>
              <Text style={styles.cancelButtonText}>Anuluj</Text>
            </TouchableOpacity>
          </View>
        );

      case 'recorded':
        return (
          <View style={styles.recordingContainer}>
            <Text style={styles.instructionText}>Nagranie gotowe</Text>
            <Text style={styles.durationText}>{formatDuration(recordingDuration)}</Text>

            <View style={styles.actionButtons}>
              <TouchableOpacity style={styles.playButton} onPress={handlePlayRecording}>
                <Text style={styles.playButtonText}>▶️ Odsłuchaj</Text>
              </TouchableOpacity>

              <TouchableOpacity style={styles.retryButton} onPress={handleRetry}>
                <Text style={styles.retryButtonText}>🔄 Nagraj ponownie</Text>
              </TouchableOpacity>
            </View>

            <TouchableOpacity style={styles.saveButton} onPress={handleSaveRecording}>
              <Text style={styles.saveButtonText}>✓ Zapisz notatkę</Text>
            </TouchableOpacity>
          </View>
        );

      case 'processing':
        return (
          <View style={styles.processingContainer}>
            <ActivityIndicator size="large" color="#007AFF" />
            <Text style={styles.processingText}>{processingStep}</Text>
          </View>
        );

      case 'completed':
        return (
          <View style={styles.resultContainer}>
            <Text style={styles.successIcon}>✅</Text>
            <Text style={styles.successText}>Notatka zapisana!</Text>

            {transcription && (
              <View style={styles.resultBox}>
                <Text style={styles.resultLabel}>Transkrypcja:</Text>
                <Text style={styles.resultText}>{transcription}</Text>
              </View>
            )}

            {aiSummary && (
              <View style={styles.resultBox}>
                <Text style={styles.resultLabel}>Streszczenie AI:</Text>
                <Text style={styles.resultText}>{aiSummary}</Text>
              </View>
            )}

            <TouchableOpacity style={styles.doneButton} onPress={onComplete}>
              <Text style={styles.doneButtonText}>Gotowe</Text>
            </TouchableOpacity>
          </View>
        );

      case 'error':
        return (
          <View style={styles.errorContainer}>
            <Text style={styles.errorIcon}>❌</Text>
            <Text style={styles.errorText}>{errorMessage}</Text>

            <View style={styles.actionButtons}>
              <TouchableOpacity style={styles.retryButton} onPress={handleRetry}>
                <Text style={styles.retryButtonText}>Spróbuj ponownie</Text>
              </TouchableOpacity>
              <TouchableOpacity style={styles.cancelButton} onPress={onCancel}>
                <Text style={styles.cancelButtonText}>Zamknij</Text>
              </TouchableOpacity>
            </View>
          </View>
        );

      default:
        return null;
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      {/* Header */}
      <View style={styles.header}>
        <TouchableOpacity style={styles.closeButton} onPress={onCancel}>
          <Text style={styles.closeButtonText}>✕</Text>
        </TouchableOpacity>
        <Text style={styles.headerTitle}>
          {mode === 'text' ? 'Notatka tekstowa' : 'Notatka głosowa'}
        </Text>
        <View style={styles.headerSpacer} />
      </View>

      {/* Client info */}
      <View style={styles.clientInfo}>
        <Text style={styles.clientName}>{displayName}</Text>
        <Text style={styles.clientPhone}>{displayPhone}</Text>
      </View>

      {/* Main content */}
      <View style={[styles.content, mode === 'text' && styles.contentNoPadding]}>
        {renderContent()}
      </View>
    </SafeAreaView>
  );
};

const createStyles = (colors: ReturnType<typeof import('@/contexts/ThemeContext').useTheme>['colors']) =>
  StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: spacing.lg,
    backgroundColor: colors.error,
  },
  closeButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: 'rgba(255,255,255,0.2)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  closeButtonText: {
    color: colors.textInverse,
    fontSize: 20,
    fontWeight: 'bold',
  },
  headerTitle: {
    fontSize: typography.lg,
    fontWeight: typography.bold,
    color: colors.textInverse,
  },
  headerSpacer: {
    width: 40,
  },
  clientInfo: {
    backgroundColor: colors.surface,
    padding: spacing.lg,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  clientName: {
    fontSize: typography.xl,
    fontWeight: typography.bold,
    color: colors.textPrimary,
    marginBottom: spacing.xs,
  },
  clientPhone: {
    fontSize: typography.base,
    color: colors.primary,
  },
  content: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: spacing.xl,
  },
  contentNoPadding: {
    padding: 0,
    justifyContent: 'flex-start',
    alignItems: 'stretch',
  },
  recordingContainer: {
    alignItems: 'center',
  },
  instructionText: {
    fontSize: typography.base,
    color: colors.textSecondary,
    marginBottom: spacing.xl,
  },
  recordingLabel: {
    fontSize: typography.lg,
    color: colors.error,
    fontWeight: typography.bold,
    marginBottom: spacing.xl,
  },
  recordButton: {
    width: 100,
    height: 100,
    borderRadius: 50,
    backgroundColor: colors.error,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 6,
    elevation: 8,
  },
  recordingActive: {
    backgroundColor: '#D32F2F',
  },
  micIcon: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  micText: {
    fontSize: 40,
  },
  stopIcon: {
    width: 30,
    height: 30,
    backgroundColor: colors.textInverse,
    borderRadius: radius.sm,
  },
  durationText: {
    fontSize: 32,
    fontWeight: typography.bold,
    color: colors.textPrimary,
    marginTop: spacing.xl,

  },
  cancelButton: {
    marginTop: spacing.xl,
    paddingVertical: spacing.md,
    paddingHorizontal: spacing.lg,
  },
  cancelButtonText: {
    fontSize: typography.base,
    color: colors.textTertiary,
  },
  actionButtons: {
    flexDirection: 'row',
    marginTop: spacing.xl,

  },
  playButton: {
    backgroundColor: colors.infoLight,
    paddingVertical: spacing.md,
    paddingHorizontal: spacing.lg,
    borderRadius: radius.md,
  },
  playButtonText: {
    fontSize: typography.sm,
    color: colors.info,
    fontWeight: typography.semibold,
  },
  retryButton: {
    backgroundColor: colors.warningLight || '#FFF3E0',
    paddingVertical: spacing.md,
    paddingHorizontal: spacing.lg,
    borderRadius: radius.md,
  },
  retryButtonText: {
    fontSize: typography.sm,
    color: colors.warning || '#E65100',
    fontWeight: typography.semibold,
  },
  saveButton: {
    marginTop: spacing.xl,
    backgroundColor: colors.success,
    paddingVertical: spacing.lg,
    paddingHorizontal: 40,
    borderRadius: radius.md,
  },
  saveButtonText: {
    fontSize: typography.base,
    color: colors.textInverse,
    fontWeight: typography.bold,
  },
  processingContainer: {
    alignItems: 'center',
  },
  processingText: {
    marginTop: spacing.lg,
    fontSize: typography.base,
    color: colors.textSecondary,
  },
  resultContainer: {
    alignItems: 'center',
    width: '100%',
  },
  successIcon: {
    fontSize: 64,
    marginBottom: spacing.lg,
  },
  successText: {
    fontSize: typography.xxl,
    fontWeight: typography.bold,
    color: colors.success,
    marginBottom: spacing.lg,
  },
  resultBox: {
    width: '100%',
    backgroundColor: colors.surface,
    borderRadius: radius.xl,
    padding: spacing.lg,
    marginBottom: spacing.lg,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  resultLabel: {
    fontSize: typography.sm,
    fontWeight: typography.bold,
    color: colors.textPrimary,
    marginBottom: spacing.sm,
  },
  resultText: {
    fontSize: typography.sm,
    color: colors.textSecondary,
    lineHeight: 22,
  },
  doneButton: {
    marginTop: spacing.lg,
    backgroundColor: colors.primary,
    paddingVertical: spacing.lg,
    paddingHorizontal: 60,
    borderRadius: radius.md,
  },
  doneButtonText: {
    fontSize: typography.base,
    color: colors.textInverse,
    fontWeight: typography.bold,
  },
  errorContainer: {
    alignItems: 'center',
  },
  errorIcon: {
    fontSize: 64,
    marginBottom: spacing.lg,
  },
  errorText: {
    fontSize: typography.base,
    color: colors.error,
    textAlign: 'center',
    marginBottom: spacing.lg,
  },
  // Text note form styles
  textNoteContainer: {
    flex: 1,
  },
  textNoteHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
    backgroundColor: colors.background,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  textNoteLabel: {
    fontSize: typography.base,
    fontWeight: typography.semibold,
    color: colors.textPrimary,
  },
  textNoteInput: {
    flex: 1,
    backgroundColor: colors.surface,
    borderRadius: 0,
    padding: spacing.lg,
    fontSize: typography.base,
    color: colors.textPrimary,
    textAlignVertical: 'top',
  },
  saveTextButton: {
    backgroundColor: colors.primary,
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.lg,
    borderRadius: radius.sm,
    alignItems: 'center',
  },
  saveTextButtonDisabled: {
    backgroundColor: colors.borderLight,
  },
  saveTextButtonText: {
    color: colors.textInverse,
    fontSize: typography.sm,
    fontWeight: typography.semibold,
  },
});
