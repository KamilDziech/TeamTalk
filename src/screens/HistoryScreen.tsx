/**
 * HistoryScreen
 *
 * Displays history of completed calls with voice reports.
 * Features:
 * - List of completed calls with AI summaries
 * - Audio playback from Supabase Storage
 * - Expandable full transcription
 * - Search by client name or summary keywords
 */

import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  TouchableOpacity,
  TextInput,
  ActivityIndicator,
  RefreshControl,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { supabase } from '@/api/supabaseClient';
import { voiceReportService } from '@/services/VoiceReportService';
import type { CallLog, Client, VoiceReport } from '@/types';

interface HistoryItem {
  callLog: CallLog;
  client: Client;
  voiceReport: VoiceReport;
}

export const HistoryScreen: React.FC = () => {
  const [historyItems, setHistoryItems] = useState<HistoryItem[]>([]);
  const [filteredItems, setFilteredItems] = useState<HistoryItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [expandedItems, setExpandedItems] = useState<Set<string>>(new Set());
  const [playingAudio, setPlayingAudio] = useState<string | null>(null);

  const fetchHistory = useCallback(async () => {
    try {
      // Fetch call_logs with status 'completed' that have voice_reports
      const { data: callLogs, error: callLogsError } = await supabase
        .from('call_logs')
        .select('*')
        .eq('status', 'completed')
        .order('timestamp', { ascending: false });

      if (callLogsError) {
        console.error('Error fetching call logs:', callLogsError);
        return;
      }

      if (!callLogs || callLogs.length === 0) {
        setHistoryItems([]);
        setFilteredItems([]);
        return;
      }

      // Fetch voice_reports for these call_logs
      const callLogIds = callLogs.map((cl) => cl.id);
      const { data: voiceReports, error: voiceReportsError } = await supabase
        .from('voice_reports')
        .select('*')
        .in('call_log_id', callLogIds);

      if (voiceReportsError) {
        console.error('Error fetching voice reports:', voiceReportsError);
        return;
      }

      // Filter call_logs that have voice_reports
      const voiceReportMap = new Map(
        voiceReports?.map((vr) => [vr.call_log_id, vr]) || []
      );
      const callLogsWithReports = callLogs.filter((cl) =>
        voiceReportMap.has(cl.id)
      );

      if (callLogsWithReports.length === 0) {
        setHistoryItems([]);
        setFilteredItems([]);
        return;
      }

      // Fetch clients for these call_logs
      const clientIds = [...new Set(callLogsWithReports.map((cl) => cl.client_id))];
      const { data: clients, error: clientsError } = await supabase
        .from('clients')
        .select('*')
        .in('id', clientIds);

      if (clientsError) {
        console.error('Error fetching clients:', clientsError);
        return;
      }

      const clientMap = new Map(clients?.map((c) => [c.id, c]) || []);

      // Combine all data
      const items: HistoryItem[] = callLogsWithReports
        .map((callLog) => ({
          callLog,
          client: clientMap.get(callLog.client_id)!,
          voiceReport: voiceReportMap.get(callLog.id)!,
        }))
        .filter((item) => item.client && item.voiceReport);

      setHistoryItems(items);
      setFilteredItems(items);
    } catch (error) {
      console.error('Error in fetchHistory:', error);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    fetchHistory();
  }, [fetchHistory]);

  // Filter items when search query changes
  useEffect(() => {
    if (!searchQuery.trim()) {
      setFilteredItems(historyItems);
      return;
    }

    const query = searchQuery.toLowerCase();
    const filtered = historyItems.filter((item) => {
      const clientName = item.client.name?.toLowerCase() || '';
      const summary = item.voiceReport.ai_summary?.toLowerCase() || '';
      const transcription = item.voiceReport.transcription?.toLowerCase() || '';
      return (
        clientName.includes(query) ||
        summary.includes(query) ||
        transcription.includes(query)
      );
    });
    setFilteredItems(filtered);
  }, [searchQuery, historyItems]);

  const handleRefresh = () => {
    setRefreshing(true);
    fetchHistory();
  };

  const toggleExpanded = (id: string) => {
    setExpandedItems((prev) => {
      const newSet = new Set(prev);
      if (newSet.has(id)) {
        newSet.delete(id);
      } else {
        newSet.add(id);
      }
      return newSet;
    });
  };

  const handlePlayAudio = async (audioUrl: string, callLogId: string) => {
    try {
      if (playingAudio === callLogId) {
        // Stop playing
        await voiceReportService.stopPlayback();
        setPlayingAudio(null);
      } else {
        // Stop any current playback
        if (playingAudio) {
          await voiceReportService.stopPlayback();
        }
        // Start new playback
        setPlayingAudio(callLogId);
        await voiceReportService.playAudio(audioUrl);
        // Note: In a real app, we'd listen for playback completion
        // For now, user needs to tap again to stop
      }
    } catch (error) {
      console.error('Error playing audio:', error);
      setPlayingAudio(null);
    }
  };

  const formatDate = (timestamp: string) => {
    const date = new Date(timestamp);
    return date.toLocaleDateString('pl-PL', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  const renderSummary = (summary: string | null) => {
    if (!summary) return null;

    // Parse markdown-like summary into sections
    const lines = summary.split('\n').filter((line) => line.trim());

    return (
      <View style={styles.summaryContainer}>
        {lines.map((line, index) => {
          const isHeader = line.startsWith('**') || line.startsWith('#');
          const isBullet = line.startsWith('-') || line.startsWith('•');
          const isNumbered = /^\d+\./.test(line.trim());

          let displayLine = line
            .replace(/\*\*/g, '')
            .replace(/^#+\s*/, '')
            .replace(/^-\s*/, '')
            .replace(/^•\s*/, '')
            .trim();

          if (isHeader) {
            return (
              <Text key={index} style={styles.summaryHeader}>
                {displayLine}
              </Text>
            );
          }

          if (isBullet || isNumbered) {
            return (
              <View key={index} style={styles.bulletRow}>
                <Text style={styles.bullet}>•</Text>
                <Text style={styles.bulletText}>{displayLine}</Text>
              </View>
            );
          }

          return (
            <Text key={index} style={styles.summaryText}>
              {displayLine}
            </Text>
          );
        })}
      </View>
    );
  };

  const renderItem = ({ item }: { item: HistoryItem }) => {
    const isExpanded = expandedItems.has(item.callLog.id);
    const isPlaying = playingAudio === item.callLog.id;

    return (
      <View style={styles.card}>
        {/* Header */}
        <View style={styles.cardHeader}>
          <View style={styles.clientInfo}>
            <Text style={styles.clientName}>
              {item.client.name || 'Nieznany klient'}
            </Text>
            <Text style={styles.phone}>{item.client.phone}</Text>
          </View>
          <View style={styles.dateInfo}>
            <Text style={styles.date}>{formatDate(item.callLog.timestamp)}</Text>
            {item.callLog.reservation_by && (
              <Text style={styles.handledBy}>
                Obsłużył: {item.callLog.reservation_by}
              </Text>
            )}
          </View>
        </View>

        {/* AI Summary */}
        {item.voiceReport.ai_summary && (
          <View style={styles.summarySection}>
            <Text style={styles.sectionTitle}>📝 Streszczenie AI</Text>
            {renderSummary(item.voiceReport.ai_summary)}
          </View>
        )}

        {/* Action Buttons */}
        <View style={styles.actions}>
          {item.voiceReport.audio_url && (
            <TouchableOpacity
              style={[styles.actionButton, isPlaying && styles.actionButtonActive]}
              onPress={() =>
                handlePlayAudio(item.voiceReport.audio_url!, item.callLog.id)
              }
            >
              <Text style={styles.actionButtonText}>
                {isPlaying ? '⏹ Stop' : '▶ Odtwórz'}
              </Text>
            </TouchableOpacity>
          )}

          {item.voiceReport.transcription && (
            <TouchableOpacity
              style={[styles.actionButton, styles.actionButtonSecondary]}
              onPress={() => toggleExpanded(item.callLog.id)}
            >
              <Text style={styles.actionButtonTextSecondary}>
                {isExpanded ? '▲ Zwiń' : '▼ Pełna notatka'}
              </Text>
            </TouchableOpacity>
          )}
        </View>

        {/* Expanded Transcription */}
        {isExpanded && item.voiceReport.transcription && (
          <View style={styles.transcriptionSection}>
            <Text style={styles.sectionTitle}>📄 Pełna transkrypcja</Text>
            <Text style={styles.transcriptionText}>
              {item.voiceReport.transcription}
            </Text>
          </View>
        )}
      </View>
    );
  };

  if (loading) {
    return (
      <SafeAreaView style={styles.container} edges={['bottom']}>
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color="#007AFF" />
          <Text style={styles.loadingText}>Ładowanie historii...</Text>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.container} edges={['bottom']}>
      {/* Search Bar */}
      <View style={styles.searchContainer}>
        <TextInput
          style={styles.searchInput}
          placeholder="Szukaj po nazwisku lub słowach kluczowych..."
          placeholderTextColor="#999"
          value={searchQuery}
          onChangeText={setSearchQuery}
        />
        {searchQuery.length > 0 && (
          <TouchableOpacity
            style={styles.clearButton}
            onPress={() => setSearchQuery('')}
          >
            <Text style={styles.clearButtonText}>✕</Text>
          </TouchableOpacity>
        )}
      </View>

      {/* Results count */}
      <Text style={styles.resultsCount}>
        {filteredItems.length} {filteredItems.length === 1 ? 'rozmowa' : 'rozmów'}
        {searchQuery ? ` dla "${searchQuery}"` : ''}
      </Text>

      {/* List */}
      <FlatList
        data={filteredItems}
        keyExtractor={(item) => item.callLog.id}
        renderItem={renderItem}
        contentContainerStyle={styles.listContent}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={handleRefresh} />
        }
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <Text style={styles.emptyIcon}>📋</Text>
            <Text style={styles.emptyTitle}>Brak historii</Text>
            <Text style={styles.emptyText}>
              {searchQuery
                ? 'Nie znaleziono rozmów pasujących do wyszukiwania.'
                : 'Nie ma jeszcze żadnych zakończonych rozmów z notatkami.'}
            </Text>
          </View>
        }
      />
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  loadingText: {
    marginTop: 12,
    fontSize: 16,
    color: '#666',
  },
  searchContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#fff',
    margin: 12,
    marginBottom: 0,
    borderRadius: 10,
    paddingHorizontal: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
    elevation: 2,
  },
  searchInput: {
    flex: 1,
    height: 44,
    fontSize: 16,
    color: '#333',
  },
  clearButton: {
    padding: 8,
  },
  clearButtonText: {
    fontSize: 18,
    color: '#999',
  },
  resultsCount: {
    fontSize: 13,
    color: '#666',
    marginHorizontal: 16,
    marginVertical: 8,
  },
  listContent: {
    paddingHorizontal: 12,
    paddingBottom: 20,
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
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 12,
    paddingBottom: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#eee',
  },
  clientInfo: {
    flex: 1,
  },
  clientName: {
    fontSize: 18,
    fontWeight: '600',
    color: '#333',
  },
  phone: {
    fontSize: 14,
    color: '#666',
    marginTop: 2,
  },
  dateInfo: {
    alignItems: 'flex-end',
  },
  date: {
    fontSize: 13,
    color: '#666',
  },
  handledBy: {
    fontSize: 12,
    color: '#999',
    marginTop: 2,
  },
  summarySection: {
    marginBottom: 12,
  },
  sectionTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#007AFF',
    marginBottom: 8,
  },
  summaryContainer: {
    backgroundColor: '#f8f9fa',
    borderRadius: 8,
    padding: 12,
  },
  summaryHeader: {
    fontSize: 14,
    fontWeight: '600',
    color: '#333',
    marginBottom: 6,
    marginTop: 4,
  },
  summaryText: {
    fontSize: 14,
    color: '#444',
    lineHeight: 20,
    marginBottom: 4,
  },
  bulletRow: {
    flexDirection: 'row',
    marginBottom: 4,
    paddingLeft: 4,
  },
  bullet: {
    fontSize: 14,
    color: '#007AFF',
    marginRight: 8,
    lineHeight: 20,
  },
  bulletText: {
    flex: 1,
    fontSize: 14,
    color: '#444',
    lineHeight: 20,
  },
  actions: {
    flexDirection: 'row',
    gap: 10,
  },
  actionButton: {
    flex: 1,
    backgroundColor: '#007AFF',
    paddingVertical: 10,
    paddingHorizontal: 16,
    borderRadius: 8,
    alignItems: 'center',
  },
  actionButtonActive: {
    backgroundColor: '#FF3B30',
  },
  actionButtonSecondary: {
    backgroundColor: '#fff',
    borderWidth: 1,
    borderColor: '#007AFF',
  },
  actionButtonText: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '600',
  },
  actionButtonTextSecondary: {
    color: '#007AFF',
    fontSize: 14,
    fontWeight: '600',
  },
  transcriptionSection: {
    marginTop: 12,
    paddingTop: 12,
    borderTopWidth: 1,
    borderTopColor: '#eee',
  },
  transcriptionText: {
    fontSize: 14,
    color: '#444',
    lineHeight: 22,
    backgroundColor: '#f8f9fa',
    borderRadius: 8,
    padding: 12,
  },
  emptyContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingVertical: 60,
    paddingHorizontal: 40,
  },
  emptyIcon: {
    fontSize: 48,
    marginBottom: 16,
  },
  emptyTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#333',
    marginBottom: 8,
  },
  emptyText: {
    fontSize: 14,
    color: '#666',
    textAlign: 'center',
    lineHeight: 20,
  },
});
