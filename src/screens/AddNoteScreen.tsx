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

import React, { useState, useCallback } from 'react';
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
  callCount: number;  // How many calls from this client are being grouped
}

const formatCallDate = (timestamp: string): string => {
  const date = new Date(timestamp);
  const now = new Date();
  const yesterday = new Date(now);
  yesterday.setDate(yesterday.getDate() - 1);

  const time = date.toLocaleTimeString('pl-PL', { hour: '2-digit', minute: '2-digit' });

  if (date.toDateString() === now.toDateString()) return `Dzisiaj ${time}`;
  if (date.toDateString() === yesterday.toDateString()) return `Wczoraj ${time}`;
  return `${date.toLocaleDateString('pl-PL', { day: '2-digit', month: '2-digit' })} ${time}`;
};

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

      // Filtruj tylko te BEZ notatki i NIE pominięte/zmergowane
      const logsNeedingNotes = logsWithReportStatus.filter(
        (log: any) => !log.hasVoiceReport && log.type !== 'skipped' && log.type !== 'merged'
      );

      // GROUP by client_id or caller_phone to show only ONE card per client
      // This prevents showing 10 cards when someone called 10 times
      // Also count how many calls are in each group
      const groupedMap = new Map<string, { log: any; count: number }>();

      logsNeedingNotes.forEach((log: any) => {
        const groupKey = log.client_id || log.caller_phone || 'unknown';

        const existing = groupedMap.get(groupKey);
        if (!existing) {
          groupedMap.set(groupKey, { log, count: 1 });
        } else {
          // Keep the NEWEST completed call, but increment count
          existing.count++;
          if (new Date(log.timestamp) > new Date(existing.log.timestamp)) {
            existing.log = log;
          }
        }
      });

      // Convert map to array - one call per client/number, with callCount
      const groupedLogs = Array.from(groupedMap.values())
        .map(({ log, count }) => ({ ...log, callCount: count }))
        .sort((a: any, b: any) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime());

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
    console.log('📝 handleRecordingComplete called, selectedCall:', selectedCall?.id);
    if (!selectedCall) return;

    // Find and mark all other completed calls from the same phone number as merged
    // This prevents showing duplicate entries when client called multiple times
    try {
      const callerPhone = selectedCall.caller_phone;
      console.log('📞 Looking for calls to merge with caller_phone:', callerPhone);

      if (callerPhone) {
        // Find other completed calls from same phone that:
        // - are NOT already merged/skipped
        // - do NOT have a voice report (note) already
        const { data: otherCalls, error: findError } = await supabase
          .from('call_logs')
          .select('id, type, status, voice_reports(id)')
          .eq('status', 'completed')
          .eq('caller_phone', callerPhone)
          .eq('type', 'completed')  // Only get calls that are not yet merged/skipped
          .neq('id', selectedCall.id); // Exclude the one we just added note to

        // Filter out calls that already have a voice report
        const callsWithoutNotes = otherCalls?.filter((c: any) =>
          !c.voice_reports || c.voice_reports.length === 0
        ) || [];

        console.log('🔍 Found other calls to merge:', callsWithoutNotes.length, 'of', otherCalls?.length, 'Error:', findError);

        if (callsWithoutNotes && callsWithoutNotes.length > 0) {
          // Mark these calls as 'merged' and link to main call
          const callIds = callsWithoutNotes.map((c: any) => c.id);
          const { error: updateError } = await supabase
            .from('call_logs')
            .update({
              type: 'merged',
              merged_into_id: selectedCall.id  // Link to the main call with the note
            })
            .in('id', callIds);

          console.log(`✅ Merged ${callsWithoutNotes.length} calls for ${callerPhone} into ${selectedCall.id}, updateError:`, updateError);
        } else {
          console.log('ℹ️ No other calls to merge');
        }
      } else {
        console.log('⚠️ No caller_phone in selectedCall');
      }
    } catch (error) {
      console.error('❌ Error merging duplicate calls:', error);
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

  // Skip call without adding note - mark as 'skipped' so it goes to History without note
  const handleSkipCall = (callLog: CallLogWithClient) => {
    const displayName = callLog.client?.name || callLog.caller_phone || 'Nieznany numer';
    Alert.alert(
      'Pomiń połączenie',
      `Czy na pewno chcesz pominąć to połączenie bez dodawania notatki?\n\nKlient: ${displayName}`,
      [
        { text: 'Anuluj', style: 'cancel' },
        {
          text: 'Pomiń bez notatki',
          style: 'destructive',
          onPress: async () => {
            try {
              // Mark as 'skipped' type - will appear in History but not in AddNote anymore
              const { error } = await supabase
                .from('call_logs')
                .update({ type: 'skipped' })
                .eq('id', callLog.id);

              if (error) {
                console.error('Error skipping call log:', error);
                Alert.alert('Błąd', 'Nie udało się pominąć połączenia.');
                return;
              }

              // Refresh the list
              fetchCallLogsNeedingNotes();
            } catch (error) {
              console.error('Error skipping call:', error);
              Alert.alert('Błąd', 'Wystąpił błąd podczas pomijania.');
            }
          },
        },
      ]
    );
  };

  const renderCallLog = ({ item }: { item: CallLogWithClient }) => {
    const displayName = item.client?.name || item.caller_phone || 'Nieznany numer';
    const displayPhone = item.client?.phone || (item.caller_phone ? `+48${item.caller_phone}` : '');
    const callTime = formatCallDate(item.timestamp);

    return (
      <>
        {/* Main row - tappable */}
        <TouchableOpacity
          style={styles.row}
          onPress={() => handleSelectCall(item, 'voice')}
          activeOpacity={0.7}
        >
          {/* Center: Name + Phone */}
          <View style={styles.rowCenter}>
            <Text style={styles.nameText}>{displayName}</Text>
            {displayPhone && <Text style={styles.phoneText}>{displayPhone}</Text>}
            <Text style={styles.statusText}>Wymaga notatki</Text>
          </View>

          {/* Right: Time */}
          <View style={styles.rowRight}>
            <Text style={styles.timeText}>{callTime}</Text>
          </View>
        </TouchableOpacity>

        {/* Action buttons row */}
        <View style={styles.actionsRow}>
          <TouchableOpacity
            style={styles.actionButton}
            onPress={() => handleSelectCall(item, 'voice')}
            activeOpacity={0.7}
          >
            <Text style={styles.actionButtonText}>🎤 Nagraj</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.actionButton}
            onPress={() => handleSelectCall(item, 'text')}
            activeOpacity={0.7}
          >
            <Text style={styles.actionButtonText}>✏️ Napisz</Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={styles.deleteButton}
            onPress={() => handleSkipCall(item)}
            activeOpacity={0.7}
          >
            <Text style={styles.deleteButtonText}>🗑️ Pomiń</Text>
          </TouchableOpacity>
        </View>
      </>
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
          ItemSeparatorComponent={() => <View style={styles.separator} />}
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
            callCount={selectedCall.callCount}
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
      backgroundColor: colors.surface,
      paddingVertical: spacing.sm,
      paddingHorizontal: spacing.lg,
      borderBottomWidth: 1,
      borderBottomColor: colors.border,
    },
    listHeaderText: {
      fontSize: typography.sm,
      color: colors.error,
      fontWeight: typography.medium,
    },
    listContent: {
      paddingVertical: spacing.sm,
    },

    // Minimalist row (like CallLogsList)
    row: {
      flexDirection: 'row',
      alignItems: 'center',
      backgroundColor: colors.surface,
      paddingVertical: spacing.md,
      paddingHorizontal: spacing.lg,
    },
    rowCenter: {
      flex: 1,
    },
    nameText: {
      fontSize: typography.lg,
      fontWeight: typography.semibold,
      color: colors.textPrimary,
      marginBottom: 2,
    },
    phoneText: {
      fontSize: typography.sm,
      color: colors.textSecondary,
    },
    statusText: {
      fontSize: typography.xs,
      color: colors.error,
      marginTop: 2,
    },
    rowRight: {
      alignItems: 'flex-end',
    },
    timeText: {
      fontSize: typography.sm,
      color: colors.textTertiary,
    },
    separator: {
      height: 1,
      backgroundColor: colors.borderLight,
    },

    // Action buttons row - subtle style
    actionsRow: {
      flexDirection: 'row',
      backgroundColor: colors.surface,
      paddingHorizontal: spacing.lg,
      paddingBottom: spacing.md,
      gap: spacing.sm,
    },
    actionButton: {
      flex: 1,
      backgroundColor: 'transparent',
      paddingVertical: spacing.xs,
      paddingHorizontal: spacing.sm,
      borderRadius: radius.sm,
      alignItems: 'center',
      borderWidth: 1,
      borderColor: colors.border,
    },
    actionButtonText: {
      color: colors.textSecondary,
      fontSize: typography.xs,
      fontWeight: typography.medium,
    },
    deleteButton: {
      backgroundColor: 'transparent',
      paddingVertical: spacing.xs,
      paddingHorizontal: spacing.sm,
      borderRadius: radius.sm,
      alignItems: 'center',
      borderWidth: 1,
      borderColor: colors.border,
    },
    deleteButtonText: {
      color: colors.textTertiary,
      fontSize: typography.xs,
      fontWeight: typography.medium,
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
