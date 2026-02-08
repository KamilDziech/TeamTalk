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
import { VoiceRecordingScreen, NoteMode } from './VoiceRecordingScreen';
import { useTheme } from '@/contexts/ThemeContext';
import { spacing, radius, typography, shadows, commonStyles } from '@/styles/theme';
import type { CallLog, Client } from '@/types';

interface CallLogWithClient extends CallLog {
  client: Client | null;
  hasVoiceReport: boolean;
}

export const AddNoteScreen: React.FC = () => {
  const { colors } = useTheme();
  const styles = createStyles(colors);
  const [callLogs, setCallLogs] = useState<CallLogWithClient[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [selectedCall, setSelectedCall] = useState<CallLogWithClient | null>(null);
  const [isRecordingModalVisible, setIsRecordingModalVisible] = useState(false);
  const [noteMode, setNoteMode] = useState<NoteMode>('voice');

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
        .limit(100);

      if (error) throw error;

      // Fetch all voice reports in one query
      const callLogIds = logs?.map((log: any) => log.id) || [];
      const { data: reports } = await supabase
        .from('voice_reports')
        .select('call_log_id')
        .in('call_log_id', callLogIds);

      const reportMap = new Set(reports?.map((r) => r.call_log_id) || []);

      const logsWithReportStatus = (logs || []).map((log: any) => ({
        ...log,
        client: log.clients,
        hasVoiceReport: reportMap.has(log.id),
      }));

      // Filtruj tylko te BEZ notatki
      const logsNeedingNotes = logsWithReportStatus.filter(
        (log: any) => !log.hasVoiceReport
      );

      // GROUP by client_id or caller_phone to show only ONE card per client
      // This prevents showing 10 cards when someone called 10 times
      const groupedMap = new Map<string, any>();

      logsNeedingNotes.forEach((log: any) => {
        const groupKey = log.client_id || log.caller_phone || 'unknown';

        // Keep only the NEWEST completed call for each client/number
        const existing = groupedMap.get(groupKey);
        if (!existing || new Date(log.timestamp) > new Date(existing.timestamp)) {
          groupedMap.set(groupKey, log);
        }
      });

      // Convert map to array - one call per client/number
      const groupedLogs = Array.from(groupedMap.values()).sort(
        (a: any, b: any) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()
      );

      setCallLogs(groupedLogs as CallLogWithClient[]);
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

  const handleSelectCall = (callLog: CallLogWithClient, mode: NoteMode = 'voice') => {
    setSelectedCall(callLog);
    setNoteMode(mode);
    setIsRecordingModalVisible(true);
  };

  const handleRecordingComplete = async () => {
    if (!selectedCall) return;

    // Find and mark all other completed calls from the same client as merged
    // This prevents showing duplicate entries when client called multiple times
    try {
      const groupKey = selectedCall.client_id || selectedCall.caller_phone;

      if (groupKey) {
        // Find all other completed calls without voice_report from this client
        const query = supabase
          .from('call_logs')
          .select('id')
          .eq('status', 'completed')
          .neq('id', selectedCall.id); // Exclude the one we just added note to

        if (selectedCall.client_id) {
          query.eq('client_id', selectedCall.client_id);
        } else {
          query.eq('caller_phone', selectedCall.caller_phone);
        }

        const { data: otherCalls } = await query;

        if (otherCalls && otherCalls.length > 0) {
          // Mark these calls as 'merged' so they won't appear again
          const otherCallIds = otherCalls.map((c: any) => c.id);
          await supabase
            .from('call_logs')
            .update({ type: 'merged', status: 'completed' })
            .in('id', otherCallIds);

          console.log(`Merged ${otherCalls.length} duplicate completed calls`);
        }
      }
    } catch (error) {
      console.error('Error merging duplicate calls:', error);
    }

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
    const displayName = callLog.client?.name || callLog.caller_phone || 'Nieznany numer';
    Alert.alert(
      'Pomiń połączenie',
      `Czy na pewno chcesz usunąć to połączenie bez dodawania notatki?\n\nKlient: ${displayName}\n\nTa operacja jest nieodwracalna.`,
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
              {item.client?.name || item.caller_phone || 'Nieznany numer'}
            </Text>
            <Text style={styles.clientPhone}>
              {item.client?.phone || (item.caller_phone ? `+48${item.caller_phone}` : '')}
            </Text>
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
            onPress={() => handleSelectCall(item, 'voice')}
            activeOpacity={0.7}
          >
            <Text style={styles.recordButtonText}>
              🎤 Nagraj
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.textButton}
            onPress={() => handleSelectCall(item, 'text')}
            activeOpacity={0.7}
          >
            <Text style={styles.textButtonText}>
              ✏️ Napisz
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.skipButton}
            onPress={() => handleSkipCall(item)}
            activeOpacity={0.7}
          >
            <Text style={styles.skipButtonText}>
              🗑️
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

      {/* Voice/Text Recording Modal */}
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
            callerPhone={selectedCall.caller_phone}
            mode={noteMode}
            onComplete={handleRecordingComplete}
            onCancel={handleRecordingCancel}
          />
        )}
      </Modal>
    </View>
  );
};

// Dynamic styles generator
const createStyles = (colors: ReturnType<typeof import('@/contexts/ThemeContext').useTheme>['colors']) =>
  StyleSheet.create({
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

    // Header
    header: {
      backgroundColor: colors.surface,
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
      gap: spacing.xs,
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
    textButton: {
      flex: 1,
      backgroundColor: colors.info,
      borderRadius: radius.lg,
      padding: spacing.md,
      alignItems: 'center',
    },
    textButtonText: {
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
