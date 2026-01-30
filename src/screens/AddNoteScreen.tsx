/**
 * AddNoteScreen
 *
 * Ekran wyboru połączenia do dodania notatki głosowej.
 *
 * WORKFLOW:
 * 1. W zakładce "Kolejka" użytkownik klika "Rezerwuj" (missed → reserved)
 * 2. Po oddzwonieniu klika "Oznacz jako wykonane" (reserved → completed)
 * 3. Dopiero teraz połączenie pojawia się tutaj - można dodać notatkę
 *
 * Wyświetla TYLKO połączenia ze statusem 'completed' bez voice_report.
 * Faza 4: Pełna implementacja nagrywania audio
 */

import React, { useEffect, useState, useCallback } from 'react';
import {
  View,
  Text,
  FlatList,
  StyleSheet,
  TouchableOpacity,
  ActivityIndicator,
  RefreshControl,
  Modal,
  Alert,
} from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import { supabase } from '@/api/supabaseClient';
import { VoiceRecordingScreen } from './VoiceRecordingScreen';
import { colors, spacing, radius, typography, shadows, commonStyles } from '@/styles/theme';
import type { CallLog, Client } from '@/types';

interface CallLogWithClient extends CallLog {
  client: Client;
  hasVoiceReport: boolean;
}

export const AddNoteScreen: React.FC = () => {
  const [callLogs, setCallLogs] = useState<CallLogWithClient[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [selectedCall, setSelectedCall] = useState<CallLogWithClient | null>(null);
  const [isRecordingModalVisible, setIsRecordingModalVisible] = useState(false);

  // Fetch data when screen comes into focus
  useFocusEffect(
    useCallback(() => {
      fetchCallLogsNeedingNotes();
    }, [])
  );

  const fetchCallLogsNeedingNotes = async () => {
    try {
      setLoading(true);

      // Pobierz TYLKO połączenia ze statusem 'completed' (oznaczone jako wykonane)
      // Te połączenia przeszły pełny workflow: missed → reserved → completed
      const { data: logs, error } = await supabase
        .from('call_logs')
        .select(`
          *,
          clients (*)
        `)
        .eq('status', 'completed')
        .order('timestamp', { ascending: false })
        .limit(50);

      if (error) throw error;

      // Sprawdź które mają voice_report
      const logsWithReportStatus = await Promise.all(
        (logs || []).map(async (log) => {
          const { data: report } = await supabase
            .from('voice_reports')
            .select('id')
            .eq('call_log_id', log.id)
            .single();

          return {
            ...log,
            client: log.clients,
            hasVoiceReport: !!report,
          };
        })
      );

      // Filtruj tylko te BEZ notatki (do których można jeszcze dodać notatkę)
      const logsNeedingNotes = logsWithReportStatus.filter(
        (log) => !log.hasVoiceReport
      );

      setCallLogs(logsNeedingNotes as CallLogWithClient[]);
    } catch (error) {
      console.error('Error fetching call logs:', error);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  const onRefresh = () => {
    setRefreshing(true);
    fetchCallLogsNeedingNotes();
  };

  const handleSelectCall = (callLog: CallLogWithClient) => {
    setSelectedCall(callLog);
    setIsRecordingModalVisible(true);
  };

  const handleRecordingComplete = () => {
    setIsRecordingModalVisible(false);
    setSelectedCall(null);
    // Refresh the list to remove the completed item
    fetchCallLogsNeedingNotes();
  };

  const handleRecordingCancel = () => {
    setIsRecordingModalVisible(false);
    setSelectedCall(null);
  };

  // Skip/Delete call without adding note
  const handleSkipCall = (callLog: CallLogWithClient) => {
    Alert.alert(
      'Pomiń połączenie',
      `Czy na pewno chcesz usunąć to połączenie bez dodawania notatki?\n\nKlient: ${callLog.client?.name || 'Nieznany'}\n\nTa operacja jest nieodwracalna.`,
      [
        { text: 'Anuluj', style: 'cancel' },
        {
          text: 'Pomiń bez notatki',
          style: 'destructive',
          onPress: async () => {
            try {
              // Delete the call log entry
              const { error } = await supabase
                .from('call_logs')
                .delete()
                .eq('id', callLog.id);

              if (error) {
                console.error('Error deleting call log:', error);
                Alert.alert('Błąd', 'Nie udało się usunąć połączenia.');
                return;
              }

              // Refresh the list
              fetchCallLogsNeedingNotes();
            } catch (error) {
              console.error('Error skipping call:', error);
              Alert.alert('Błąd', 'Wystąpił błąd podczas usuwania.');
            }
          },
        },
      ]
    );
  };

  const renderCallLog = ({ item }: { item: CallLogWithClient }) => {
    return (
      <View style={styles.card}>
        {/* Czerwony wskaźnik WYMAGA NOTATKI */}
        <View style={styles.requiresNoteAlert}>
          <Text style={styles.requiresNoteText}>🔴 WYMAGA NOTATKI</Text>
        </View>

        <View style={styles.cardHeader}>
          <View style={styles.clientInfo}>
            <Text style={styles.clientName}>
              {item.client?.name || 'Nieznany klient'}
            </Text>
            <Text style={styles.clientPhone}>{item.client?.phone}</Text>
          </View>
        </View>

        <View style={styles.cardDetails}>
          <Text style={styles.timestamp}>
            {new Date(item.timestamp).toLocaleString('pl-PL')}
          </Text>
          <Text style={styles.callType}>Rozmowa wykonana</Text>
        </View>

        {/* Action buttons */}
        <View style={styles.cardActions}>
          <TouchableOpacity
            style={styles.recordButton}
            onPress={() => handleSelectCall(item)}
            activeOpacity={0.7}
          >
            <Text style={styles.recordButtonText}>
              🎤 Nagraj notatkę
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.skipButton}
            onPress={() => handleSkipCall(item)}
            activeOpacity={0.7}
          >
            <Text style={styles.skipButtonText}>
              🗑️ Pomiń
            </Text>
          </TouchableOpacity>
        </View>
      </View>
    );
  };

  if (loading) {
    return (
      <View style={styles.centerContainer}>
        <ActivityIndicator size="large" color={colors.primary} />
        <Text style={styles.loadingText}>Ładowanie połączeń...</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      {/* Header z instrukcją */}
      <View style={styles.header}>
        <Text style={styles.headerTitle}>Notatki głosowe</Text>
        <Text style={styles.headerSubtitle}>
          Rozmowy wymagające notatki głosowej
        </Text>
      </View>

      {callLogs.length === 0 ? (
        <View style={styles.emptyContainer}>
          <Text style={styles.emptyIcon}>✅</Text>
          <Text style={styles.emptyText}>Wszystko uzupełnione!</Text>
          <Text style={styles.emptySubtext}>
            Brak rozmów wymagających notatki.{'\n'}
            Kliknij "Wykonane" w zakładce Kolejka po rozmowie,{'\n'}
            aby dodać tutaj notatkę.
          </Text>
        </View>
      ) : (
        <FlatList
          data={callLogs}
          renderItem={renderCallLog}
          keyExtractor={(item) => item.id}
          contentContainerStyle={styles.listContent}
          refreshControl={
            <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
          }
          ListHeaderComponent={
            <View style={styles.listHeader}>
              <Text style={styles.listHeaderText}>
                🔴 {callLogs.length} {callLogs.length === 1 ? 'rozmowa wymaga' : 'rozmów wymaga'} notatki
              </Text>
            </View>
          }
        />
      )}

      {/* Voice Recording Modal */}
      <Modal
        visible={isRecordingModalVisible}
        animationType="slide"
        presentationStyle="fullScreen"
        onRequestClose={handleRecordingCancel}
      >
        {selectedCall && (
          <VoiceRecordingScreen
            callLogId={selectedCall.id}
            client={selectedCall.client}
            onComplete={handleRecordingComplete}
            onCancel={handleRecordingCancel}
          />
        )}
      </Modal>
    </View>
  );
};

const styles = StyleSheet.create({
  // Layout
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  centerContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: spacing.xl,
    backgroundColor: colors.background,
  },
  loadingText: {
    marginTop: spacing.md,
    fontSize: typography.base,
    color: colors.textSecondary,
  },

  // Header - Light theme with subtle warning
  header: {
    backgroundColor: colors.white,
    padding: spacing.lg,
    paddingTop: spacing.sm,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  headerTitle: {
    fontSize: typography.lg,
    fontWeight: typography.semibold,
    color: colors.textPrimary,
    marginBottom: spacing.xs,
  },
  headerSubtitle: {
    fontSize: typography.sm,
    color: colors.textSecondary,
  },

  // List
  listHeader: {
    paddingBottom: spacing.sm,
  },
  listHeaderText: {
    fontSize: typography.sm,
    color: colors.error,
    fontWeight: typography.medium,
  },
  listContent: {
    padding: spacing.lg,
  },

  // Cards
  card: {
    backgroundColor: colors.surface,
    borderRadius: radius.xl,
    padding: spacing.lg,
    marginBottom: spacing.md,
    borderWidth: 1,
    borderColor: colors.border,
    borderLeftWidth: 4,
    borderLeftColor: colors.error,
    ...shadows.sm,
  },
  cardHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: spacing.md,
  },
  clientInfo: {
    flex: 1,
  },
  clientName: {
    fontSize: typography.lg,
    fontWeight: typography.semibold,
    color: colors.textPrimary,
    marginBottom: spacing.xs,
  },
  clientPhone: {
    fontSize: typography.sm,
    color: colors.primary,
  },
  cardDetails: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: spacing.md,
  },
  timestamp: {
    fontSize: typography.sm,
    color: colors.textSecondary,
  },
  callType: {
    fontSize: typography.sm,
    color: colors.textSecondary,
    fontWeight: typography.medium,
  },

  // Alert badge
  requiresNoteAlert: {
    backgroundColor: colors.errorLight,
    borderRadius: radius.md,
    padding: spacing.md,
    marginBottom: spacing.md,
    alignItems: 'center',
  },
  requiresNoteText: {
    color: colors.error,
    fontSize: typography.sm,
    fontWeight: typography.semibold,
  },

  // Action buttons
  cardActions: {
    flexDirection: 'row',
    gap: spacing.sm,
  },
  recordButton: {
    flex: 1,
    backgroundColor: colors.error,
    borderRadius: radius.lg,
    padding: spacing.md,
    alignItems: 'center',
  },
  recordButtonText: {
    color: colors.textInverse,
    fontSize: typography.base,
    fontWeight: typography.semibold,
  },
  skipButton: {
    backgroundColor: colors.textTertiary,
    borderRadius: radius.lg,
    padding: spacing.md,
    alignItems: 'center',
    minWidth: 80,
  },
  skipButtonText: {
    color: colors.textInverse,
    fontSize: typography.sm,
    fontWeight: typography.semibold,
  },

  // Empty state
  emptyContainer: {
    ...commonStyles.emptyState,
    flex: 1,
  },
  emptyIcon: {
    ...commonStyles.emptyStateIcon,
  },
  emptyText: {
    fontSize: typography.xl,
    fontWeight: typography.semibold,
    color: colors.success,
    marginBottom: spacing.sm,
  },
  emptySubtext: {
    ...commonStyles.emptyStateText,
  },
});
