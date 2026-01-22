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
} from 'react-native';
import { useFocusEffect } from '@react-navigation/native';
import { supabase } from '@/api/supabaseClient';
import { VoiceRecordingScreen } from './VoiceRecordingScreen';
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

  const renderCallLog = ({ item }: { item: CallLogWithClient }) => {
    return (
      <TouchableOpacity
        style={styles.card}
        onPress={() => handleSelectCall(item)}
        activeOpacity={0.7}
      >
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

        <View style={styles.recordButton}>
          <Text style={styles.recordButtonText}>
            🎤 Kliknij, aby nagrać notatkę
          </Text>
        </View>
      </TouchableOpacity>
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
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
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
  header: {
    backgroundColor: '#F44336',
    padding: 16,
    paddingTop: 8,
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#fff',
    marginBottom: 4,
  },
  headerSubtitle: {
    fontSize: 14,
    color: 'rgba(255,255,255,0.9)',
  },
  listHeader: {
    paddingBottom: 8,
  },
  listHeaderText: {
    fontSize: 14,
    color: '#666',
    fontWeight: '500',
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
    borderLeftColor: '#F44336',
  },
  cardHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 12,
  },
  clientInfo: {
    flex: 1,
  },
  clientName: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 2,
  },
  clientPhone: {
    fontSize: 14,
    color: '#007AFF',
  },
  cardDetails: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 12,
  },
  timestamp: {
    fontSize: 13,
    color: '#666',
  },
  callType: {
    fontSize: 13,
    color: '#666',
    fontWeight: '500',
  },
  requiresNoteAlert: {
    backgroundColor: '#FFEBEE',
    borderRadius: 8,
    padding: 10,
    marginBottom: 12,
    alignItems: 'center',
  },
  requiresNoteText: {
    color: '#C62828',
    fontSize: 14,
    fontWeight: 'bold',
  },
  recordButton: {
    backgroundColor: '#F44336',
    borderRadius: 8,
    padding: 14,
    alignItems: 'center',
  },
  recordButtonText: {
    color: '#fff',
    fontSize: 15,
    fontWeight: 'bold',
  },
  emptyContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  emptyIcon: {
    fontSize: 64,
    marginBottom: 16,
  },
  emptyText: {
    fontSize: 20,
    fontWeight: '600',
    color: '#4CAF50',
    marginBottom: 8,
  },
  emptySubtext: {
    fontSize: 14,
    color: '#666',
    textAlign: 'center',
  },
});
