/**
 * CallLogsList Component
 *
 * Displays call logs with workflow-based status:
 * - 'missed' (Do obsłużenia) -> Przycisk [REZERWUJ]
 * - 'reserved' (W trakcie) -> Przyciski [ZADZWOŃ] [WYKONANE] [UWOLNIJ]
 * - 'completed' -> Znika z kolejki, pojawia się w zakładce Notatka
 *
 * Workflow: missed ↔ reserved → completed (completed nie wyświetla się w kolejce)
 */

import React, { useEffect, useState } from 'react';
import {
  View,
  Text,
  FlatList,
  StyleSheet,
  TouchableOpacity,
  ActivityIndicator,
  RefreshControl,
  Alert,
  Linking,
  LayoutAnimation,
  UIManager,
  Platform,
} from 'react-native';

// Enable LayoutAnimation for Android
if (Platform.OS === 'android' && UIManager.setLayoutAnimationEnabledExperimental) {
  UIManager.setLayoutAnimationEnabledExperimental(true);
}
import { supabase } from '@/api/supabaseClient';
import { callLogScanner } from '@/services/CallLogScanner';
import { useAuth } from '@/contexts/AuthContext';
import { colors, spacing, radius, typography, shadows, commonStyles } from '@/styles/theme';
import type { CallLog, Client, Profile } from '@/types';

interface CallLogWithClient extends CallLog {
  client: Client;
  hasVoiceReport: boolean;
}

// Zgrupowane połączenia od tego samego klienta
interface GroupedCallLog {
  clientId: string;
  client: Client;
  callCount: number; // Ile razy klient próbował dzwonić
  latestCall: CallLogWithClient; // Najnowsze połączenie (do wyświetlenia statusu)
  allCalls: CallLogWithClient[]; // Wszystkie połączenia (do szczegółów)
  firstCallTime: string; // Czas pierwszej próby
  lastCallTime: string; // Czas ostatniej próby
}

/**
 * Grupuje połączenia po kliencie - łączy wielokrotne próby dzwonienia
 * Priorytet: nieobsłużone (missed) > zarezerwowane (reserved) > załatwione (completed)
 */
const groupCallLogsByClient = (logs: CallLogWithClient[]): GroupedCallLog[] => {
  const groupMap = new Map<string, CallLogWithClient[]>();

  // Grupuj po client_id
  logs.forEach((log) => {
    if (!log.client_id) return;
    const existing = groupMap.get(log.client_id) || [];
    existing.push(log);
    groupMap.set(log.client_id, existing);
  });

  // Przekształć na GroupedCallLog[]
  const grouped: GroupedCallLog[] = [];

  groupMap.forEach((calls, clientId) => {
    // Sortuj po czasie (najnowsze pierwsze)
    calls.sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime());

    // Znajdź najważniejsze połączenie (priorytet: missed > reserved > completed)
    const priorityOrder = { missed: 0, reserved: 1, completed: 2 };
    const latestCall = calls.reduce((prev, curr) => {
      if (priorityOrder[curr.status] < priorityOrder[prev.status]) return curr;
      return prev;
    }, calls[0]);

    // Policz nieobsłużone (missed) połączenia
    const missedCalls = calls.filter((c) => c.status === 'missed');

    grouped.push({
      clientId,
      client: calls[0].client,
      callCount: missedCalls.length > 0 ? missedCalls.length : calls.length,
      latestCall,
      allCalls: calls,
      firstCallTime: calls[calls.length - 1].timestamp,
      lastCallTime: calls[0].timestamp,
    });
  });

  // Sortuj grupy: najpierw te z nieobsłużonymi (missed), potem po czasie
  grouped.sort((a, b) => {
    const aHasMissed = a.allCalls.some((c) => c.status === 'missed');
    const bHasMissed = b.allCalls.some((c) => c.status === 'missed');
    if (aHasMissed && !bHasMissed) return -1;
    if (!aHasMissed && bHasMissed) return 1;
    return new Date(b.lastCallTime).getTime() - new Date(a.lastCallTime).getTime();
  });

  return grouped;
};

// SLA threshold in milliseconds (1 hour)
const SLA_THRESHOLD_MS = 60 * 60 * 1000;

/**
 * Format time elapsed since a given timestamp
 * Returns string like "2h 15m temu" or "45m temu"
 */
const formatTimeElapsed = (timestamp: string): string => {
  const now = new Date().getTime();
  const then = new Date(timestamp).getTime();
  const diffMs = now - then;

  const minutes = Math.floor(diffMs / (1000 * 60));
  const hours = Math.floor(minutes / 60);
  const remainingMinutes = minutes % 60;

  if (hours > 0) {
    return `${hours}h ${remainingMinutes}m temu`;
  }
  return `${minutes}m temu`;
};

/**
 * Check if the oldest active call exceeds SLA threshold
 */
const checkSlaExceeded = (group: GroupedCallLog): { exceeded: boolean; waitTime: string } => {
  // Find oldest active (missed/reserved) call
  const activeCalls = group.allCalls.filter(c => c.status === 'missed' || c.status === 'reserved');
  if (activeCalls.length === 0) {
    return { exceeded: false, waitTime: '' };
  }

  // Sort by timestamp ascending to get oldest
  const sortedCalls = [...activeCalls].sort(
    (a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime()
  );
  const oldestCall = sortedCalls[0];

  const now = new Date().getTime();
  const oldestTime = new Date(oldestCall.timestamp).getTime();
  const diffMs = now - oldestTime;

  if (diffMs > SLA_THRESHOLD_MS) {
    const minutes = Math.floor(diffMs / (1000 * 60));
    const hours = Math.floor(minutes / 60);
    const remainingMinutes = minutes % 60;
    return {
      exceeded: true,
      waitTime: `${hours}h ${remainingMinutes}m`,
    };
  }

  return { exceeded: false, waitTime: '' };
};

export const CallLogsList: React.FC = () => {
  console.log('📋 CallLogsList: Component rendering START');
  const { user } = useAuth();
  console.log('📋 CallLogsList: useAuth called, user:', user?.id);
  const [callLogs, setCallLogs] = useState<CallLogWithClient[]>([]);
  const [groupedLogs, setGroupedLogs] = useState<GroupedCallLog[]>([]);
  const [profiles, setProfiles] = useState<Map<string, Profile>>(new Map());
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [syncStatus, setSyncStatus] = useState<string | null>(null);
  const [expandedCards, setExpandedCards] = useState<Set<string>>(new Set());
  console.log('📋 CallLogsList: useState hooks called');

  // Toggle card expansion with animation
  const toggleCardExpansion = (clientId: string) => {
    LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
    setExpandedCards(prev => {
      const newSet = new Set(prev);
      if (newSet.has(clientId)) {
        newSet.delete(clientId);
      } else {
        newSet.add(clientId);
      }
      return newSet;
    });
  };

  useEffect(() => {
    console.log('📋 CallLogsList: useEffect running');
    fetchCallLogs();
    fetchProfiles();
    setupRealtimeSubscription();
  }, []);

  const fetchProfiles = async () => {
    try {
      const { data, error } = await supabase.from('profiles').select('*');
      if (error) {
        console.error('Error fetching profiles:', error);
        return;
      }
      const profileMap = new Map<string, Profile>();
      data?.forEach((profile) => profileMap.set(profile.id, profile));
      setProfiles(profileMap);
    } catch (error) {
      console.error('Error fetching profiles:', error);
    }
  };

  const getDisplayName = (userId: string | null): string | null => {
    if (!userId) return null;
    const profile = profiles.get(userId);
    return profile?.display_name || null;
  };

  const fetchCallLogs = async () => {
    try {
      setLoading(true);

      // Fetch call logs with client data
      const { data: logs, error } = await supabase
        .from('call_logs')
        .select(`
          *,
          clients (*)
        `)
        .order('timestamp', { ascending: false })
        .limit(50);

      if (error) throw error;

      // Check which logs have voice reports
      const logsWithReports = await Promise.all(
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

      setCallLogs(logsWithReports as CallLogWithClient[]);

      // Filtruj tylko połączenia, które powinny być w kolejce (missed i reserved)
      // Completed połączenia znikają z kolejki i pojawiają się w zakładce Notatka
      const queueLogs = (logsWithReports as CallLogWithClient[]).filter(
        (log) => log.status === 'missed' || log.status === 'reserved'
      );

      // Grupuj połączenia po kliencie
      const grouped = groupCallLogsByClient(queueLogs);
      console.log('📋 Grouped logs:', grouped.length, 'groups');
      setGroupedLogs(grouped);
    } catch (error) {
      console.error('Error fetching call logs:', error);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  const setupRealtimeSubscription = () => {
    const channel = supabase
      .channel('call_logs_changes')
      .on(
        'postgres_changes',
        {
          event: '*',
          schema: 'public',
          table: 'call_logs',
        },
        () => {
          fetchCallLogs();
        }
      )
      .subscribe();

    return () => {
      supabase.removeChannel(channel);
    };
  };

  const handleReserve = async (callLog: CallLogWithClient) => {
    if (!user) {
      Alert.alert('Błąd', 'Musisz być zalogowany, aby rezerwować połączenia.');
      return;
    }

    try {
      const { error } = await supabase
        .from('call_logs')
        .update({
          status: 'reserved',
          reservation_by: user.id,
          reservation_at: new Date().toISOString(),
        })
        .eq('id', callLog.id);

      if (error) throw error;

      fetchCallLogs();
    } catch (error) {
      console.error('Error reserving call:', error);
    }
  };

  const handleComplete = async (callLog: CallLogWithClient) => {
    try {
      const { error } = await supabase
        .from('call_logs')
        .update({
          type: 'completed',
          status: 'completed',
        })
        .eq('id', callLog.id);

      if (error) throw error;

      fetchCallLogs();
    } catch (error) {
      console.error('Error completing call:', error);
    }
  };

  const onRefresh = async () => {
    setRefreshing(true);
    setSyncStatus('Synchronizacja połączeń...');

    try {
      // Najpierw skanuj CallLog systemowy i zaktualizuj bazę
      console.log('🔄 Manual refresh - scanning call log...');
      await callLogScanner.scanMissedCalls();

      // Potem odśwież listę z bazy
      await fetchCallLogs();
      setSyncStatus('Zsynchronizowano');
    } catch (error) {
      console.error('Error during refresh:', error);
      setSyncStatus('Błąd synchronizacji');
    } finally {
      // Ukryj status po 2 sekundach
      setTimeout(() => setSyncStatus(null), 2000);
      setRefreshing(false);
    }
  };

  const getStatusColor = (callLog: CallLogWithClient) => {
    if (callLog.status === 'completed') {
      return callLog.hasVoiceReport ? colors.success : colors.warning;
    }
    if (callLog.status === 'reserved') return colors.primary;
    return colors.error;
  };

  const getStatusText = (callLog: CallLogWithClient) => {
    if (callLog.status === 'completed') {
      return callLog.hasVoiceReport ? '🟢 Załatwione' : '⚠️ BEZ NOTATKI';
    }
    if (callLog.status === 'reserved') return '🔵 W trakcie';
    return '🔴 Do obsłużenia';
  };

  // Rezerwuj wszystkie nieobsłużone połączenia od klienta
  const handleReserveGroup = async (group: GroupedCallLog) => {
    if (!user) {
      Alert.alert('Błąd', 'Musisz być zalogowany, aby rezerwować połączenia.');
      return;
    }

    const missedCalls = group.allCalls.filter((c) => c.status === 'missed');

    if (missedCalls.length === 0) {
      return;
    }

    try {
      for (const call of missedCalls) {
        const { error } = await supabase
          .from('call_logs')
          .update({
            status: 'reserved',
            reservation_by: user.id,
            reservation_at: new Date().toISOString(),
          })
          .eq('id', call.id);

        if (error) {
          console.error('Error reserving call:', error);
        }
      }
      fetchCallLogs();
    } catch (error) {
      console.error('Error reserving calls:', error);
    }
  };

  // Zadzwoń - otwiera dialer systemowy z numerem klienta
  const handleCall = (group: GroupedCallLog) => {
    const phoneNumber = group.client?.phone;
    if (phoneNumber) {
      Linking.openURL(`tel:${phoneNumber}`);
    }
  };

  // Uwolnij rezerwację - wraca do statusu missed
  const handleReleaseGroup = async (group: GroupedCallLog) => {
    const reservedCalls = group.allCalls.filter((c) => c.status === 'reserved');

    try {
      for (const call of reservedCalls) {
        await supabase
          .from('call_logs')
          .update({
            status: 'missed',
            reservation_by: null,
            reservation_at: null,
          })
          .eq('id', call.id);
      }
      fetchCallLogs();
    } catch (error) {
      console.error('Error releasing calls:', error);
    }
  };

  // Oznacz jako wykonane - przenosi do zakładki Notatka
  const handleCompleteGroup = async (group: GroupedCallLog) => {
    const reservedCalls = group.allCalls.filter((c) => c.status === 'reserved');

    try {
      for (const call of reservedCalls) {
        await supabase
          .from('call_logs')
          .update({
            type: 'completed',
            status: 'completed',
          })
          .eq('id', call.id);
      }
      fetchCallLogs();
    } catch (error) {
      console.error('Error completing calls:', error);
    }
  };

  // Wyczyść całą kolejkę (na testy)
  const handleClearQueue = () => {
    Alert.alert(
      'Wyczyść kolejkę',
      'Czy na pewno chcesz usunąć WSZYSTKIE wpisy z kolejki połączeń?\n\nTa operacja jest nieodwracalna.',
      [
        { text: 'Anuluj', style: 'cancel' },
        {
          text: 'Usuń wszystko',
          style: 'destructive',
          onPress: async () => {
            try {
              setLoading(true);
              const { error } = await supabase
                .from('call_logs')
                .delete()
                .gte('id', '00000000-0000-0000-0000-000000000000'); // Delete all rows

              if (error) throw error;

              console.log('🗑️ Queue cleared');
              fetchCallLogs();
            } catch (error) {
              console.error('Error clearing queue:', error);
              Alert.alert('Błąd', 'Nie udało się wyczyścić kolejki.');
            }
          },
        },
      ]
    );
  };

  // Pełne skanowanie (ostatnie 7 dni)
  const handleFullRescan = async () => {
    setRefreshing(true);
    setSyncStatus('Pełne skanowanie (7 dni)...');

    try {
      await callLogScanner.fullRescan();
      await fetchCallLogs();
      setSyncStatus('Skanowanie zakończone');
    } catch (error) {
      console.error('Error during full rescan:', error);
      setSyncStatus('Błąd skanowania');
    } finally {
      setTimeout(() => setSyncStatus(null), 2000);
      setRefreshing(false);
    }
  };

  const renderGroupedCallLog = ({ item }: { item: GroupedCallLog }) => {
    const statusColor = getStatusColor(item.latestCall);
    const hasMissedCalls = item.allCalls.some((c) => c.status === 'missed');
    const hasReservedCalls = item.allCalls.some((c) => c.status === 'reserved');
    const missedCount = item.allCalls.filter((c) => c.status === 'missed').length;
    const isExpanded = expandedCards.has(item.clientId);

    // Check SLA
    const slaStatus = checkSlaExceeded(item);

    // Get active calls for accordion (missed + reserved only)
    const activeCalls = item.allCalls.filter(c => c.status === 'missed' || c.status === 'reserved');

    // Find oldest active call to show who is responsible
    const oldestActiveCall = activeCalls.length > 0
      ? [...activeCalls].sort((a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime())[0]
      : null;
    const responsibleEmployee = oldestActiveCall?.employee_id
      ? getDisplayName(oldestActiveCall.employee_id)
      : null;

    return (
      <View style={[styles.card, { borderLeftColor: statusColor }]}>
        {/* SLA Alert Banner */}
        {slaStatus.exceeded && (
          <View style={styles.slaAlertBanner}>
            <Text style={styles.slaAlertText}>
              ❗ Czeka: {slaStatus.waitTime}
              {responsibleEmployee && ` (${responsibleEmployee})`}
            </Text>
          </View>
        )}

        <View style={styles.cardHeader}>
          <View style={styles.clientInfo}>
            <Text style={styles.clientName}>
              {slaStatus.exceeded && <Text style={styles.slaIcon}>❗ </Text>}
              {item.client?.name || 'Nieznany klient'}
            </Text>
            <Text style={styles.clientPhone}>{item.client?.phone}</Text>
          </View>
          <Text style={[styles.statusBadge, { backgroundColor: statusColor }]}>
            {getStatusText(item.latestCall)}
          </Text>
        </View>

        {/* Alert o wielokrotnym dzwonieniu - klikalny accordion */}
        {activeCalls.length > 1 && (
          <TouchableOpacity
            style={styles.multiCallAlert}
            onPress={() => toggleCardExpansion(item.clientId)}
            activeOpacity={0.7}
          >
            <View style={styles.multiCallHeader}>
              <Text style={styles.multiCallText}>
                🔔 Klient dzwonił {activeCalls.length} razy!
              </Text>
              <Text style={styles.expandIcon}>
                {isExpanded ? '▼' : '▶'}
              </Text>
            </View>

            {/* Expanded list of attempts */}
            {isExpanded && (
              <View style={styles.attemptsList}>
                {activeCalls.map((call) => (
                  <View key={call.id} style={styles.attemptRow}>
                    <View style={styles.attemptInfo}>
                      <Text style={styles.attemptTime}>
                        🕒 {new Date(call.timestamp).toLocaleTimeString('pl-PL', { hour: '2-digit', minute: '2-digit' })}
                        {' '}
                        <Text style={styles.attemptDate}>
                          ({new Date(call.timestamp).toLocaleDateString('pl-PL')})
                        </Text>
                      </Text>
                      <Text style={styles.attemptElapsed}>
                        ⏳ {formatTimeElapsed(call.timestamp)}
                      </Text>
                      {/* Show who missed this call */}
                      {call.employee_id && (
                        <Text style={styles.attemptMissedBy}>
                          📵 Nie odebrał: {getDisplayName(call.employee_id) || 'Nieznany'}
                        </Text>
                      )}
                    </View>
                    <View style={styles.attemptStatus}>
                      {call.status === 'reserved' && call.reservation_by && (
                        <Text style={styles.attemptReservedBy}>
                          👤 {getDisplayName(call.reservation_by) || 'Ktoś'}
                        </Text>
                      )}
                      <View style={[
                        styles.attemptStatusDot,
                        { backgroundColor: call.status === 'missed' ? '#F44336' : '#2196F3' }
                      ]} />
                    </View>
                  </View>
                ))}
              </View>
            )}
          </TouchableOpacity>
        )}

        <View style={styles.cardDetails}>
          <Text style={styles.detailText}>
            🕐 Ostatnio: {new Date(item.lastCallTime).toLocaleString('pl-PL')}
          </Text>
          {item.client?.address && (
            <Text style={styles.detailText}>📍 {item.client.address}</Text>
          )}
          {/* Show who missed the latest call */}
          {item.latestCall.employee_id && item.latestCall.status === 'missed' && (
            <Text style={styles.missedByText}>
              📵 Nie odebrał: {getDisplayName(item.latestCall.employee_id) || 'Nieznany'}
            </Text>
          )}
          {item.latestCall.reservation_by && item.latestCall.status === 'reserved' && (
            <Text style={styles.detailText}>
              👤 Obsługuje: {getDisplayName(item.latestCall.reservation_by) || 'Nieznany użytkownik'}
            </Text>
          )}
        </View>

        <View style={styles.actions}>
          {/* Status: missed -> Przycisk [REZERWUJ] */}
          {hasMissedCalls && (
            <TouchableOpacity
              style={[styles.button, styles.reserveButton]}
              onPress={() => handleReserveGroup(item)}
            >
              <Text style={styles.buttonText}>
                Rezerwuj {missedCount > 1 ? `(${missedCount})` : ''}
              </Text>
            </TouchableOpacity>
          )}

          {/* Status: reserved -> Trzy przyciski */}
          {hasReservedCalls && !hasMissedCalls && (
            <View style={styles.reservedActions}>
              {/* Przycisk ZADZWOŃ - niebieski */}
              <TouchableOpacity
                style={[styles.button, styles.callButton]}
                onPress={() => handleCall(item)}
              >
                <Text style={styles.buttonText}>📞 Zadzwoń</Text>
              </TouchableOpacity>

              {/* Przycisk WYKONANE - zielony */}
              <TouchableOpacity
                style={[styles.button, styles.completeButton]}
                onPress={() => handleCompleteGroup(item)}
              >
                <Text style={styles.buttonText}>✓ Wykonane</Text>
              </TouchableOpacity>

              {/* Przycisk UWOLNIJ - szary, mniejszy */}
              <TouchableOpacity
                style={[styles.smallButton, styles.releaseButton]}
                onPress={() => handleReleaseGroup(item)}
              >
                <Text style={styles.smallButtonText}>Uwolnij</Text>
              </TouchableOpacity>
            </View>
          )}
        </View>
      </View>
    );
  };

  console.log('📋 CallLogsList: Before render, loading:', loading, 'groupedLogs:', groupedLogs.length);

  if (loading) {
    console.log('📋 CallLogsList: Showing loading state');
    return (
      <View style={styles.centerContainer}>
        <ActivityIndicator size="large" color={colors.primary} />
        <Text style={styles.loadingText}>Ładowanie połączeń...</Text>
      </View>
    );
  }

  // Komponent statusu synchronizacji
  const SyncStatusBar = () => {
    if (!syncStatus) return null;
    return (
      <View style={styles.syncStatusBar}>
        {refreshing && <ActivityIndicator size="small" color="#fff" style={styles.syncSpinner} />}
        <Text style={styles.syncStatusText}>{syncStatus}</Text>
      </View>
    );
  };

  if (groupedLogs.length === 0) {
    return (
      <View style={styles.container}>
        <SyncStatusBar />
        <FlatList
          data={[]}
          renderItem={() => null}
          contentContainerStyle={styles.emptyListContent}
          refreshControl={
            <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
          }
          ListEmptyComponent={
            <View style={styles.emptyContainer}>
              <Text style={styles.emptyText}>📞 Brak połączeń</Text>
              <Text style={styles.emptySubtext}>
                Nieodebrane połączenia od znanych klientów pojawią się tutaj
              </Text>
              <Text style={styles.pullHint}>↓ Pociągnij w dół aby odświeżyć</Text>
            </View>
          }
        />
      </View>
    );
  }

  // Footer z przyciskami testowymi
  const ListFooter = () => (
    <View style={styles.footerContainer}>
      <TouchableOpacity
        style={styles.fullRescanButton}
        onPress={handleFullRescan}
        disabled={refreshing}
      >
        <Text style={styles.fullRescanButtonText}>
          🔄 Pełne skanowanie (ostatnie 7 dni)
        </Text>
      </TouchableOpacity>

      <TouchableOpacity
        style={styles.clearQueueButton}
        onPress={handleClearQueue}
      >
        <Text style={styles.clearQueueButtonText}>
          🗑️ Wyczyść całą kolejkę (testy)
        </Text>
      </TouchableOpacity>
    </View>
  );

  return (
    <View style={styles.container}>
      <SyncStatusBar />
      <FlatList
        data={groupedLogs}
        renderItem={renderGroupedCallLog}
        keyExtractor={(item) => item.clientId}
        contentContainerStyle={styles.listContent}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
        }
        ListFooterComponent={<ListFooter />}
      />
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
  listContent: {
    padding: spacing.lg,
  },

  // Cards - Modern SaaS style
  card: {
    backgroundColor: colors.surface,
    borderRadius: radius.xl,
    padding: spacing.xl,
    marginBottom: spacing.md,
    borderWidth: 1,
    borderColor: colors.border,
    borderLeftWidth: 4,
    ...shadows.sm,
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
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
    fontWeight: typography.medium,
  },
  statusBadge: {
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    borderRadius: radius.full,
    marginLeft: spacing.sm,
    fontSize: typography.xs,
    color: colors.textInverse,
    fontWeight: typography.medium,
  },

  // Alert boxes
  alertBox: {
    backgroundColor: colors.errorLight,
    borderRadius: radius.md,
    padding: spacing.md,
    marginBottom: spacing.md,
  },
  alertText: {
    color: colors.error,
    fontSize: typography.sm,
    fontWeight: typography.semibold,
  },
  noteInfoBox: {
    backgroundColor: colors.infoLight,
    borderRadius: radius.md,
    padding: spacing.md,
    marginBottom: spacing.md,
  },
  noteInfoText: {
    color: colors.info,
    fontSize: typography.sm,
    fontWeight: typography.medium,
  },
  noteBox: {
    backgroundColor: colors.successLight,
    borderRadius: radius.md,
    padding: spacing.md,
    marginBottom: spacing.md,
  },
  noteLabel: {
    color: colors.success,
    fontSize: typography.sm,
    fontWeight: typography.semibold,
    marginBottom: spacing.xs,
  },
  noteText: {
    color: colors.success,
    fontSize: typography.sm,
  },
  multiCallAlert: {
    backgroundColor: colors.warningLight,
    borderRadius: radius.md,
    padding: spacing.md,
    marginBottom: spacing.md,
  },
  multiCallHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  multiCallText: {
    color: colors.warning,
    fontSize: typography.sm,
    fontWeight: typography.semibold,
    flex: 1,
  },
  expandIcon: {
    color: colors.warning,
    fontSize: typography.xs,
    marginLeft: spacing.sm,
  },
  attemptsList: {
    marginTop: spacing.md,
    paddingTop: spacing.md,
    borderTopWidth: 1,
    borderTopColor: colors.warningLight,
  },

  // Card details
  cardDetails: {
    marginBottom: spacing.md,
  },
  detailText: {
    fontSize: typography.sm,
    color: colors.textSecondary,
    marginBottom: spacing.xs,
    lineHeight: 20,
  },
  missedByText: {
    fontSize: typography.sm,
    color: colors.error,
    marginBottom: spacing.xs,
    lineHeight: 20,
    fontWeight: typography.medium,
  },

  // Actions and Buttons
  actions: {
    flexDirection: 'row',
    gap: spacing.sm,
  },
  reservedActions: {
    flex: 1,
    flexDirection: 'row',
    gap: spacing.sm,
    flexWrap: 'wrap',
  },
  button: {
    flex: 1,
    paddingVertical: spacing.md,
    borderRadius: radius.lg,
    alignItems: 'center',
    minWidth: 80,
  },
  smallButton: {
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.md,
    borderRadius: radius.md,
    alignItems: 'center',
  },
  reserveButton: {
    backgroundColor: colors.warning,
  },
  callButton: {
    backgroundColor: colors.primary,
  },
  completeButton: {
    backgroundColor: colors.success,
  },
  releaseButton: {
    backgroundColor: colors.textTertiary,
  },
  noteButton: {
    backgroundColor: colors.error,
  },
  smallButtonText: {
    color: colors.textInverse,
    fontSize: typography.xs,
    fontWeight: typography.semibold,
  },
  buttonText: {
    color: colors.textInverse,
    fontSize: typography.sm,
    fontWeight: typography.semibold,
  },

  // Sync status bar
  syncStatusBar: {
    backgroundColor: colors.primary,
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.lg,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
  },
  syncSpinner: {
    marginRight: spacing.sm,
  },
  syncStatusText: {
    color: colors.textInverse,
    fontSize: typography.sm,
    fontWeight: typography.medium,
  },

  // Empty state
  emptyListContent: {
    flexGrow: 1,
    justifyContent: 'center',
  },
  emptyContainer: {
    ...commonStyles.emptyState,
  },
  emptyText: {
    ...commonStyles.emptyStateTitle,
  },
  emptySubtext: {
    ...commonStyles.emptyStateText,
    marginBottom: spacing.lg,
  },
  pullHint: {
    marginTop: spacing.lg,
    fontSize: typography.xs,
    color: colors.textTertiary,
  },

  // Footer buttons
  footerContainer: {
    paddingVertical: spacing.xl,
    paddingHorizontal: spacing.lg,
    borderTopWidth: 1,
    borderTopColor: colors.border,
    marginTop: spacing.lg,
    gap: spacing.md,
  },
  fullRescanButton: {
    backgroundColor: colors.infoLight,
    padding: spacing.lg,
    borderRadius: radius.lg,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: colors.info,
  },
  fullRescanButtonText: {
    color: colors.info,
    fontSize: typography.sm,
    fontWeight: typography.semibold,
  },
  clearQueueButton: {
    backgroundColor: colors.errorLight,
    padding: spacing.lg,
    borderRadius: radius.lg,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: colors.error,
  },
  clearQueueButtonText: {
    color: colors.error,
    fontSize: typography.sm,
    fontWeight: typography.semibold,
  },
  // SLA Alert styles
  slaAlertBanner: {
    backgroundColor: '#FFCDD2',
    borderRadius: 8,
    padding: 10,
    marginBottom: 12,
    alignItems: 'center',
  },
  slaAlertText: {
    color: '#D32F2F',
    fontSize: 14,
    fontWeight: 'bold',
  },
  slaIcon: {
    color: '#D32F2F',
  },
  // Accordion attempt rows
  attemptRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: '#FFE0B2',
  },
  attemptInfo: {
    flex: 1,
  },
  attemptTime: {
    fontSize: 13,
    color: '#5D4037',
    fontWeight: '500',
  },
  attemptDate: {
    fontSize: 11,
    color: '#8D6E63',
    fontWeight: 'normal',
  },
  attemptElapsed: {
    fontSize: 11,
    color: '#A1887F',
    marginTop: 2,
  },
  attemptMissedBy: {
    fontSize: 11,
    color: '#D32F2F',
    marginTop: 2,
    fontWeight: '500',
  },
  attemptStatus: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  attemptReservedBy: {
    fontSize: 11,
    color: '#1976D2',
  },
  attemptStatusDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
});
