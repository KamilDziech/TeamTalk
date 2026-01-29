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
} from 'react-native';
import { voiceReportService } from '@/services/VoiceReportService';
import { deviceService } from '@/services/DeviceService';
import { colors, spacing, radius, typography, shadows } from '@/styles/theme';
import type { Client } from '@/types';

interface VoiceRecordingScreenProps {
  callLogId: string;
  client: Client | null;
  onComplete: () => void;
  onCancel: () => void;
}

type RecordingState = 'idle' | 'recording' | 'recorded' | 'processing' | 'completed' | 'error';

const MIN_RECORDING_DURATION_SECONDS = 2;

export const VoiceRecordingScreen: React.FC<VoiceRecordingScreenProps> = ({
  callLogId,
  client,
  onComplete,
  onCancel,
}) => {
  const [state, setState] = useState<RecordingState>('idle');
  const [recordingDuration, setRecordingDuration] = useState(0);
  const [audioUri, setAudioUri] = useState<string | null>(null);
  const [processingStep, setProcessingStep] = useState('');
  const [transcription, setTranscription] = useState<string | null>(null);
  const [aiSummary, setAiSummary] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

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

      // Generate AI summary
      let summary: string | null = null;
      if (transcriptionResult) {
        setProcessingStep('Generowanie streszczenia AI...');
        summary = await voiceReportService.generateSummary(
          transcriptionResult,
          client?.name || undefined
        );

        // Check for hallucination in summary
        if (summary === 'ERROR_EMPTY') {
          setErrorMessage('Nie wykryto mowy w nagraniu. Spróbuj nagrać jeszcze raz.');
          setState('error');
          return;
        }

        setAiSummary(summary);
      }

      // Save to database
      setProcessingStep('Zapisywanie...');
      const saved = await voiceReportService.saveVoiceReport(
        callLogId,
        audioUrl,
        transcriptionResult,
        summary
      );

      if (saved) {
        setState('completed');

        // Notify team about new voice report
        const clientName = client?.name || 'Nieznany klient';
        deviceService.notifyNewVoiceReport(clientName);
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

  // Render content based on state
  const renderContent = () => {
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
            <ActivityIndicator size="large" color={colors.primary} />
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
        <Text style={styles.headerTitle}>Notatka głosowa</Text>
        <View style={styles.headerSpacer} />
      </View>

      {/* Client info */}
      <View style={styles.clientInfo}>
        <Text style={styles.clientName}>{client?.name || 'Nieznany klient'}</Text>
        <Text style={styles.clientPhone}>{client?.phone}</Text>
      </View>

      {/* Main content */}
      <View style={styles.content}>{renderContent()}</View>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  // Layout
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },

  // Header - Light theme
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: spacing.lg,
    backgroundColor: colors.white,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  closeButton: {
    width: 40,
    height: 40,
    borderRadius: radius.full,
    backgroundColor: colors.background,
    alignItems: 'center',
    justifyContent: 'center',
  },
  closeButtonText: {
    color: colors.textSecondary,
    fontSize: typography.xl,
    fontWeight: typography.semibold,
  },
  headerTitle: {
    fontSize: typography.lg,
    fontWeight: typography.semibold,
    color: colors.textPrimary,
  },
  headerSpacer: {
    width: 40,
  },

  // Client info
  clientInfo: {
    backgroundColor: colors.surface,
    padding: spacing.lg,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  clientName: {
    fontSize: typography.xl,
    fontWeight: typography.semibold,
    color: colors.textPrimary,
    marginBottom: spacing.xs,
  },
  clientPhone: {
    fontSize: typography.base,
    color: colors.primary,
  },

  // Content area
  content: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: spacing.xl,
  },
  recordingContainer: {
    alignItems: 'center',
  },
  instructionText: {
    fontSize: typography.base,
    color: colors.textSecondary,
    marginBottom: spacing.xxxl,
  },
  recordingLabel: {
    fontSize: typography.lg,
    color: colors.error,
    fontWeight: typography.semibold,
    marginBottom: spacing.xxxl,
  },

  // Record button
  recordButton: {
    width: 100,
    height: 100,
    borderRadius: 50,
    backgroundColor: colors.error,
    alignItems: 'center',
    justifyContent: 'center',
    ...shadows.lg,
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
    backgroundColor: colors.white,
    borderRadius: radius.sm,
  },
  durationText: {
    fontSize: typography.xxxl,
    fontWeight: typography.bold,
    color: colors.textPrimary,
    marginTop: spacing.xxxl,
  },

  // Action buttons
  cancelButton: {
    marginTop: spacing.xxxl,
    paddingVertical: spacing.md,
    paddingHorizontal: spacing.xxl,
  },
  cancelButtonText: {
    fontSize: typography.base,
    color: colors.textTertiary,
  },
  actionButtons: {
    flexDirection: 'row',
    marginTop: spacing.xxxl,
    gap: spacing.lg,
  },
  playButton: {
    backgroundColor: colors.primaryLight,
    paddingVertical: spacing.md,
    paddingHorizontal: spacing.xl,
    borderRadius: radius.lg,
  },
  playButtonText: {
    fontSize: typography.sm,
    color: colors.primary,
    fontWeight: typography.semibold,
  },
  retryButton: {
    backgroundColor: colors.warningLight,
    paddingVertical: spacing.md,
    paddingHorizontal: spacing.xl,
    borderRadius: radius.lg,
  },
  retryButtonText: {
    fontSize: typography.sm,
    color: colors.warning,
    fontWeight: typography.semibold,
  },
  saveButton: {
    marginTop: spacing.xxxl,
    backgroundColor: colors.success,
    paddingVertical: spacing.lg,
    paddingHorizontal: spacing.xxxl + spacing.lg,
    borderRadius: radius.lg,
  },
  saveButtonText: {
    fontSize: typography.base,
    color: colors.textInverse,
    fontWeight: typography.semibold,
  },

  // Processing state
  processingContainer: {
    alignItems: 'center',
  },
  processingText: {
    marginTop: spacing.xl,
    fontSize: typography.base,
    color: colors.textSecondary,
  },

  // Result state
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
    marginBottom: spacing.xl,
  },
  resultBox: {
    width: '100%',
    backgroundColor: colors.surface,
    borderRadius: radius.xl,
    padding: spacing.lg,
    marginBottom: spacing.lg,
    borderWidth: 1,
    borderColor: colors.border,
    ...shadows.sm,
  },
  resultLabel: {
    fontSize: typography.sm,
    fontWeight: typography.semibold,
    color: colors.textPrimary,
    marginBottom: spacing.sm,
  },
  resultText: {
    fontSize: typography.sm,
    color: colors.textSecondary,
    lineHeight: 22,
  },
  doneButton: {
    marginTop: spacing.xl,
    backgroundColor: colors.primary,
    paddingVertical: spacing.lg,
    paddingHorizontal: spacing.xxxl * 2,
    borderRadius: radius.lg,
  },
  doneButtonText: {
    fontSize: typography.base,
    color: colors.textInverse,
    fontWeight: typography.semibold,
  },

  // Error state
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
    marginBottom: spacing.xl,
  },
});
