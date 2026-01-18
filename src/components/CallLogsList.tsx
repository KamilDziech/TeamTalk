/**
 * CallLogsList Component
 *
 * Displays call logs with:
 * - Status indicators (idle/calling/completed)
 * - "WYMAGA NOTATKI" alert for completed calls without voice_report
 * - Client information
 * - Reservation functionality
 * - Realtime updates
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
} from 'react-native';
import { supabase } from '@/api/supabaseClient';
import type { CallLog, Client } from '@/types';

interface CallLogWithClient extends CallLog {
  client: Client;
  hasVoiceReport: boolean;
}

export const CallLogsList: React.FC = () => {
  const [callLogs, setCallLogs] = useState<CallLogWithClient[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  useEffect(() => {
    fetchCallLogs();
    setupRealtimeSubscription();
  }, []);

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
    // For now, use a mock employee ID
    // TODO: Implement proper authentication
    const employeeId = 'mock-employee-123';

    try {
      const { error } = await supabase
        .from('call_logs')
        .update({
          status: 'calling',
          reservation_by: employeeId,
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

  const onRefresh = () => {
    setRefreshing(true);
    fetchCallLogs();
  };

  const getStatusColor = (callLog: CallLogWithClient) => {
    if (callLog.status === 'completed') {
      return callLog.hasVoiceReport ? '#4CAF50' : '#FF9800'; // Green or Orange
    }
    if (callLog.status === 'calling') return '#FFC107'; // Yellow
    return '#F44336'; // Red (idle)
  };

  const getStatusText = (callLog: CallLogWithClient) => {
    if (callLog.status === 'completed') {
      return callLog.hasVoiceReport ? '🟢 Załatwione' : '⚠️ Załatwione BEZ NOTATKI';
    }
    if (callLog.status === 'calling') return '🟡 Ktoś oddzwania';
    return '🔴 Nieobsłużone';
  };

  const renderCallLog = ({ item }: { item: CallLogWithClient }) => {
    const statusColor = getStatusColor(item);
    const needsNote = item.status === 'completed' && !item.hasVoiceReport;

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
            {getStatusText(item)}
          </Text>
        </View>

        {needsNote && (
          <View style={styles.alertBox}>
            <Text style={styles.alertText}>
              🔴 WYMAGA NOTATKI - dodaj notatkę głosową do tej rozmowy
            </Text>
          </View>
        )}

        <View style={styles.cardDetails}>
          <Text style={styles.detailText}>
            🕐 {new Date(item.timestamp).toLocaleString('pl-PL')}
          </Text>
          {item.client?.address && (
            <Text style={styles.detailText}>📍 {item.client.address}</Text>
          )}
          {item.reservation_by && (
            <Text style={styles.detailText}>
              👤 Zarezerwowane przez: {item.reservation_by}
            </Text>
          )}
        </View>

        <View style={styles.actions}>
          {item.status === 'idle' && (
            <TouchableOpacity
              style={[styles.button, styles.reserveButton]}
              onPress={() => handleReserve(item)}
            >
              <Text style={styles.buttonText}>Rezerwuję</Text>
            </TouchableOpacity>
          )}
          {item.status === 'calling' && (
            <TouchableOpacity
              style={[styles.button, styles.completeButton]}
              onPress={() => handleComplete(item)}
            >
              <Text style={styles.buttonText}>Oznacz jako załatwione</Text>
            </TouchableOpacity>
          )}
          {needsNote && (
            <TouchableOpacity
              style={[styles.button, styles.noteButton]}
              onPress={() => {
                // TODO: Navigate to AddNoteScreen
                console.log('Navigate to add note for', item.id);
              }}
            >
              <Text style={styles.buttonText}>Dodaj notatkę</Text>
            </TouchableOpacity>
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

  if (callLogs.length === 0) {
    return (
      <View style={styles.centerContainer}>
        <Text style={styles.emptyText}>📞 Brak połączeń</Text>
        <Text style={styles.emptySubtext}>
          Nieodebrane połączenia od znanych klientów pojawią się tutaj
        </Text>
      </View>
    );
  }

  return (
    <FlatList
      data={callLogs}
      renderItem={renderCallLog}
      keyExtractor={(item) => item.id}
      contentContainerStyle={styles.listContent}
      refreshControl={
        <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
      }
    />
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
  button: {
    flex: 1,
    paddingVertical: 12,
    borderRadius: 8,
    alignItems: 'center',
  },
  reserveButton: {
    backgroundColor: '#FFC107',
  },
  completeButton: {
    backgroundColor: '#4CAF50',
  },
  noteButton: {
    backgroundColor: '#FF5722',
  },
  buttonText: {
    color: '#fff',
    fontSize: 14,
    fontWeight: 'bold',
  },
});
