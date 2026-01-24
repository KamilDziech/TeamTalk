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
} from 'react-native';
import { supabase } from '@/api/supabaseClient';
import { callLogScanner } from '@/services/CallLogScanner';
import { useAuth } from '@/contexts/AuthContext';
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

export const CallLogsList: React.FC = () => {
  const { user } = useAuth();
  const [callLogs, setCallLogs] = useState<CallLogWithClient[]>([]);
  const [groupedLogs, setGroupedLogs] = useState<GroupedCallLog[]>([]);
  const [profiles, setProfiles] = useState<Map<string, Profile>>(new Map());
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [syncStatus, setSyncStatus] = useState<string | null>(null);

  useEffect(() => {
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
      return callLog.hasVoiceReport ? '#4CAF50' : '#FF9800'; // Green or Orange (bez notatki)
    }
    if (callLog.status === 'reserved') return '#2196F3'; // Blue (w trakcie)
    return '#F44336'; // Red (missed - do obsłużenia)
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

    return (
      <View style={[styles.card, { borderLeftColor: statusColor }]}>
        <View style={styles.cardHeader}>
          <View style={styles.clientInfo}>
            <Text style={styles.clientName}>
              {item.client?.name || 'Nieznany klient'}
            </Text>
            <Text style={styles.clientPhone}>{item.client?.phone}</Text>
          </View>
          <Text style={[styles.statusBadge, { backgroundColor: statusColor }]}>
            {getStatusText(item.latestCall)}
          </Text>
        </View>

        {/* Alert o wielokrotnym dzwonieniu - tylko dla nieobsłużonych */}
        {missedCount > 1 && (
          <View style={styles.multiCallAlert}>
            <Text style={styles.multiCallText}>
              🔔 Klient dzwonił {missedCount} razy!
            </Text>
          </View>
        )}

        <View style={styles.cardDetails}>
          <Text style={styles.detailText}>
            🕐 Ostatnio: {new Date(item.lastCallTime).toLocaleString('pl-PL')}
          </Text>
          {item.callCount > 1 && (
            <Text style={styles.detailText}>
              📊 Łącznie prób: {item.allCalls.length}
            </Text>
          )}
          {item.client?.address && (
            <Text style={styles.detailText}>📍 {item.client.address}</Text>
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

  if (loading) {
    return (
      <View style={styles.centerContainer}>
        <ActivityIndicator size="large" color="#007AFF" />
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
  centerContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
    backgroundColor: '#f5f5f5',
  },
  loadingText: {
    marginTop: 12,
    fontSize: 16,
    color: '#666',
  },
  emptyText: {
    fontSize: 20,
    fontWeight: '600',
    color: '#666',
    marginBottom: 8,
  },
  emptySubtext: {
    fontSize: 14,
    color: '#999',
    textAlign: 'center',
  },
  listContent: {
    padding: 16,
  },
  card: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
    borderLeftWidth: 4,
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 12,
  },
  clientInfo: {
    flex: 1,
  },
  clientName: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 4,
  },
  clientPhone: {
    fontSize: 14,
    color: '#007AFF',
  },
  statusBadge: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 12,
    marginLeft: 8,
  },
  alertBox: {
    backgroundColor: '#FFEBEE',
    borderRadius: 8,
    padding: 12,
    marginBottom: 12,
    borderLeftWidth: 4,
    borderLeftColor: '#F44336',
  },
  alertText: {
    color: '#C62828',
    fontSize: 14,
    fontWeight: 'bold',
  },
  noteInfoBox: {
    backgroundColor: '#E3F2FD',
    borderRadius: 8,
    padding: 10,
    marginBottom: 12,
    borderLeftWidth: 4,
    borderLeftColor: '#2196F3',
  },
  noteInfoText: {
    color: '#1565C0',
    fontSize: 13,
    fontWeight: '500',
  },
  noteBox: {
    backgroundColor: '#E8F5E9',
    borderRadius: 8,
    padding: 12,
    marginBottom: 12,
    borderLeftWidth: 4,
    borderLeftColor: '#4CAF50',
  },
  noteLabel: {
    color: '#2E7D32',
    fontSize: 14,
    fontWeight: 'bold',
    marginBottom: 4,
  },
  noteText: {
    color: '#388E3C',
    fontSize: 13,
  },
  multiCallAlert: {
    backgroundColor: '#FFF3E0',
    borderRadius: 8,
    padding: 10,
    marginBottom: 12,
    borderLeftWidth: 4,
    borderLeftColor: '#FF9800',
  },
  multiCallText: {
    color: '#E65100',
    fontSize: 14,
    fontWeight: 'bold',
  },
  cardDetails: {
    marginBottom: 12,
  },
  detailText: {
    fontSize: 14,
    color: '#666',
    marginBottom: 4,
  },
  actions: {
    flexDirection: 'row',
    gap: 8,
  },
  reservedActions: {
    flex: 1,
    flexDirection: 'row',
    gap: 8,
    flexWrap: 'wrap',
  },
  button: {
    flex: 1,
    paddingVertical: 12,
    borderRadius: 8,
    alignItems: 'center',
    minWidth: 80,
  },
  smallButton: {
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderRadius: 6,
    alignItems: 'center',
  },
  reserveButton: {
    backgroundColor: '#FFC107',
  },
  callButton: {
    backgroundColor: '#2196F3',
  },
  completeButton: {
    backgroundColor: '#4CAF50',
  },
  releaseButton: {
    backgroundColor: '#9E9E9E',
  },
  noteButton: {
    backgroundColor: '#FF5722',
  },
  smallButtonText: {
    color: '#fff',
    fontSize: 12,
    fontWeight: '600',
  },
  buttonText: {
    color: '#fff',
    fontSize: 14,
    fontWeight: 'bold',
  },
  // Nowe style dla synchronizacji
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  syncStatusBar: {
    backgroundColor: '#007AFF',
    paddingVertical: 8,
    paddingHorizontal: 16,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
  },
  syncSpinner: {
    marginRight: 8,
  },
  syncStatusText: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '500',
  },
  emptyListContent: {
    flexGrow: 1,
    justifyContent: 'center',
  },
  emptyContainer: {
    alignItems: 'center',
    padding: 20,
  },
  pullHint: {
    marginTop: 16,
    fontSize: 12,
    color: '#999',
    fontStyle: 'italic',
  },
  // Style dla przycisków testowych
  footerContainer: {
    paddingVertical: 20,
    paddingHorizontal: 16,
    borderTopWidth: 1,
    borderTopColor: '#eee',
    marginTop: 16,
    gap: 12,
  },
  fullRescanButton: {
    backgroundColor: '#E3F2FD',
    padding: 16,
    borderRadius: 8,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#2196F3',
    borderStyle: 'dashed',
  },
  fullRescanButtonText: {
    color: '#1565C0',
    fontSize: 14,
    fontWeight: '600',
  },
  clearQueueButton: {
    backgroundColor: '#FFEBEE',
    padding: 16,
    borderRadius: 8,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#F44336',
    borderStyle: 'dashed',
  },
  clearQueueButtonText: {
    color: '#C62828',
    fontSize: 14,
    fontWeight: '600',
  },
});
