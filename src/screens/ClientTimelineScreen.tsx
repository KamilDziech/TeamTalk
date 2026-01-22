/**
 * ClientTimelineScreen
 *
 * Displays the complete history of calls and voice reports for a specific client.
 * Shows a timeline of all interactions with the client.
 */

import React, { useState, useEffect, useCallback } from 'react';
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  TouchableOpacity,
  ActivityIndicator,
  RefreshControl,
  Linking,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { supabase } from '@/api/supabaseClient';
import { voiceReportService } from '@/services/VoiceReportService';
import type { Client, CallLog, VoiceReport } from '@/types';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

type ClientsStackParamList = {
  ClientsList: undefined;
  ClientTimeline: { client: Client };
};

type Props = NativeStackScreenProps<ClientsStackParamList, 'ClientTimeline'>;

interface TimelineItem {
  callLog: CallLog;
  voiceReport: VoiceReport | null;
}

export const ClientTimelineScreen: React.FC<Props> = ({ route }) => {
  const { client } = route.params;
  const [timelineItems, setTimelineItems] = useState<TimelineItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [expandedItems, setExpandedItems] = useState<Set<string>>(new Set());
  const [playingAudio, setPlayingAudio] = useState<string | null>(null);

  const fetchTimeline = useCallback(async () => {
    try {
      // Fetch all call_logs for this client
      const { data: callLogs, error: callLogsError } = await supabase
        .from('call_logs')
        .select('*')
        .eq('client_id', client.id)
        .order('timestamp', { ascending: false });

      if (callLogsError) {
        console.error('Error fetching call logs:', callLogsError);
        return;
      }

      if (!callLogs || callLogs.length === 0) {
        setTimelineItems([]);
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
      }

      const voiceReportMap = new Map(
        voiceReports?.map((vr) => [vr.call_log_id, vr]) || []
      );

      // Combine data
      const items: TimelineItem[] = callLogs.map((callLog) => ({
        callLog,
        voiceReport: voiceReportMap.get(callLog.id) || null,
      }));

      setTimelineItems(items);
    } catch (error) {
      console.error('Error in fetchTimeline:', error);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [client.id]);

  useEffect(() => {
    fetchTimeline();
  }, [fetchTimeline]);

  const handleRefresh = () => {
    setRefreshing(true);
    fetchTimeline();
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
        await voiceReportService.stopPlayback();
        setPlayingAudio(null);
      } else {
        if (playingAudio) {
          await voiceReportService.stopPlayback();
        }
        setPlayingAudio(callLogId);
        await voiceReportService.playAudio(audioUrl);
      }
    } catch (error) {
      console.error('Error playing audio:', error);
      setPlayingAudio(null);
    }
  };

  const handleCall = () => {
    Linking.openURL(`tel:${client.phone}`);
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

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'missed':
        return '#FF3B30';
      case 'reserved':
        return '#FF9500';
      case 'completed':
        return '#34C759';
      default:
        return '#666';
    }
  };

  const getStatusLabel = (status: string) => {
    switch (status) {
      case 'missed':
        return 'Nieodebrane';
      case 'reserved':
        return 'Zarezerwowane';
      case 'completed':
        return 'Zakończone';
      default:
        return status;
    }
  };

  const renderSummary = (summary: string | null) => {
    if (!summary) return null;

    const lines = summary.split('\n').filter((line) => line.trim());

    return (
      <View style={styles.summaryContainer}>
        {lines.map((line, index) => {
          const isHeader = line.startsWith('**') || line.startsWith('#');
          const isBullet = line.startsWith('-') || line.startsWith('•');

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

          if (isBullet) {
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

  const renderTimelineItem = ({ item, index }: { item: TimelineItem; index: number }) => {
    const isExpanded = expandedItems.has(item.callLog.id);
    const isPlaying = playingAudio === item.callLog.id;
    const hasVoiceReport = !!item.voiceReport;

    return (
      <View style={styles.timelineItem}>
        {/* Timeline connector */}
        <View style={styles.timelineConnector}>
          <View
            style={[
              styles.timelineDot,
              { backgroundColor: getStatusColor(item.callLog.status) },
            ]}
          />
          {index < timelineItems.length - 1 && <View style={styles.timelineLine} />}
        </View>

        {/* Content */}
        <View style={styles.timelineContent}>
          <View style={styles.timelineHeader}>
            <Text style={styles.timelineDate}>
              {formatDate(item.callLog.timestamp)}
            </Text>
            <View
              style={[
                styles.statusBadge,
                { backgroundColor: getStatusColor(item.callLog.status) + '20' },
              ]}
            >
              <Text
                style={[
                  styles.statusText,
                  { color: getStatusColor(item.callLog.status) },
                ]}
              >
                {getStatusLabel(item.callLog.status)}
              </Text>
            </View>
          </View>

          {item.callLog.reservation_by && (
            <Text style={styles.handledBy}>
              Obsłużył: {item.callLog.reservation_by}
            </Text>
          )}

          {/* Voice Report Summary */}
          {hasVoiceReport && item.voiceReport?.ai_summary && (
            <View style={styles.voiceReportSection}>
              <Text style={styles.sectionTitle}>📝 Streszczenie</Text>
              {renderSummary(item.voiceReport.ai_summary)}
            </View>
          )}

          {/* Action Buttons */}
          {hasVoiceReport && (
            <View style={styles.actions}>
              {item.voiceReport?.audio_url && (
                <TouchableOpacity
                  style={[styles.actionButton, isPlaying && styles.actionButtonActive]}
                  onPress={() =>
                    handlePlayAudio(item.voiceReport!.audio_url!, item.callLog.id)
                  }
                >
                  <Text style={styles.actionButtonText}>
                    {isPlaying ? '⏹ Stop' : '▶ Odtwórz'}
                  </Text>
                </TouchableOpacity>
              )}

              {item.voiceReport?.transcription && (
                <TouchableOpacity
                  style={[styles.actionButton, styles.actionButtonSecondary]}
                  onPress={() => toggleExpanded(item.callLog.id)}
                >
                  <Text style={styles.actionButtonTextSecondary}>
                    {isExpanded ? '▲ Zwiń' : '▼ Transkrypcja'}
                  </Text>
                </TouchableOpacity>
              )}
            </View>
          )}

          {/* No voice report indicator */}
          {!hasVoiceReport && item.callLog.status === 'completed' && (
            <View style={styles.noReportBadge}>
              <Text style={styles.noReportText}>🔴 Brak notatki</Text>
            </View>
          )}

          {/* Expanded Transcription */}
          {isExpanded && item.voiceReport?.transcription && (
            <View style={styles.transcriptionSection}>
              <Text style={styles.sectionTitle}>📄 Pełna transkrypcja</Text>
              <Text style={styles.transcriptionText}>
                {item.voiceReport.transcription}
              </Text>
            </View>
          )}
        </View>
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
      {/* Client Header */}
      <View style={styles.clientHeader}>
        <View style={styles.clientInfo}>
          <Text style={styles.clientName}>{client.name || 'Nieznany klient'}</Text>
          <Text style={styles.clientPhone}>{client.phone}</Text>
          {client.address && (
            <Text style={styles.clientAddress}>📍 {client.address}</Text>
          )}
        </View>
        <TouchableOpacity style={styles.callButton} onPress={handleCall}>
          <Text style={styles.callButtonText}>📞 Zadzwoń</Text>
        </TouchableOpacity>
      </View>

      {/* Stats */}
      <View style={styles.statsRow}>
        <View style={styles.statItem}>
          <Text style={styles.statValue}>{timelineItems.length}</Text>
          <Text style={styles.statLabel}>Połączeń</Text>
        </View>
        <View style={styles.statItem}>
          <Text style={styles.statValue}>
            {timelineItems.filter((i) => i.voiceReport).length}
          </Text>
          <Text style={styles.statLabel}>Notatek</Text>
        </View>
        <View style={styles.statItem}>
          <Text style={styles.statValue}>
            {timelineItems.filter((i) => i.callLog.status === 'missed').length}
          </Text>
          <Text style={styles.statLabel}>Nieodebranych</Text>
        </View>
      </View>

      {/* Timeline */}
      <FlatList
        data={timelineItems}
        keyExtractor={(item) => item.callLog.id}
        renderItem={renderTimelineItem}
        contentContainerStyle={styles.listContent}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={handleRefresh} />
        }
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <Text style={styles.emptyIcon}>📭</Text>
            <Text style={styles.emptyTitle}>Brak historii</Text>
            <Text style={styles.emptyText}>
              Nie ma jeszcze żadnych połączeń z tym klientem.
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
  clientHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: '#fff',
    padding: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#eee',
  },
  clientInfo: {
    flex: 1,
  },
  clientName: {
    fontSize: 20,
    fontWeight: '700',
    color: '#333',
  },
  clientPhone: {
    fontSize: 16,
    color: '#007AFF',
    marginTop: 2,
  },
  clientAddress: {
    fontSize: 14,
    color: '#666',
    marginTop: 4,
  },
  callButton: {
    backgroundColor: '#34C759',
    paddingVertical: 10,
    paddingHorizontal: 16,
    borderRadius: 8,
  },
  callButtonText: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '600',
  },
  statsRow: {
    flexDirection: 'row',
    backgroundColor: '#fff',
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#eee',
  },
  statItem: {
    flex: 1,
    alignItems: 'center',
  },
  statValue: {
    fontSize: 20,
    fontWeight: '700',
    color: '#007AFF',
  },
  statLabel: {
    fontSize: 12,
    color: '#666',
    marginTop: 2,
  },
  listContent: {
    padding: 16,
  },
  timelineItem: {
    flexDirection: 'row',
    marginBottom: 0,
  },
  timelineConnector: {
    width: 24,
    alignItems: 'center',
  },
  timelineDot: {
    width: 12,
    height: 12,
    borderRadius: 6,
    marginTop: 4,
  },
  timelineLine: {
    flex: 1,
    width: 2,
    backgroundColor: '#ddd',
    marginVertical: 4,
  },
  timelineContent: {
    flex: 1,
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 14,
    marginLeft: 8,
    marginBottom: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 3,
    elevation: 2,
  },
  timelineHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  timelineDate: {
    fontSize: 13,
    color: '#666',
  },
  statusBadge: {
    paddingVertical: 4,
    paddingHorizontal: 8,
    borderRadius: 12,
  },
  statusText: {
    fontSize: 11,
    fontWeight: '600',
  },
  handledBy: {
    fontSize: 12,
    color: '#999',
    marginBottom: 8,
  },
  voiceReportSection: {
    marginTop: 8,
  },
  sectionTitle: {
    fontSize: 13,
    fontWeight: '600',
    color: '#007AFF',
    marginBottom: 6,
  },
  summaryContainer: {
    backgroundColor: '#f8f9fa',
    borderRadius: 8,
    padding: 10,
  },
  summaryHeader: {
    fontSize: 13,
    fontWeight: '600',
    color: '#333',
    marginBottom: 4,
    marginTop: 2,
  },
  summaryText: {
    fontSize: 13,
    color: '#444',
    lineHeight: 18,
    marginBottom: 2,
  },
  bulletRow: {
    flexDirection: 'row',
    marginBottom: 2,
  },
  bullet: {
    fontSize: 13,
    color: '#007AFF',
    marginRight: 6,
  },
  bulletText: {
    flex: 1,
    fontSize: 13,
    color: '#444',
    lineHeight: 18,
  },
  actions: {
    flexDirection: 'row',
    gap: 8,
    marginTop: 10,
  },
  actionButton: {
    flex: 1,
    backgroundColor: '#007AFF',
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderRadius: 6,
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
    fontSize: 13,
    fontWeight: '600',
  },
  actionButtonTextSecondary: {
    color: '#007AFF',
    fontSize: 13,
    fontWeight: '600',
  },
  noReportBadge: {
    marginTop: 8,
    padding: 8,
    backgroundColor: '#FFF0F0',
    borderRadius: 6,
  },
  noReportText: {
    fontSize: 12,
    color: '#FF3B30',
    fontWeight: '500',
  },
  transcriptionSection: {
    marginTop: 10,
    paddingTop: 10,
    borderTopWidth: 1,
    borderTopColor: '#eee',
  },
  transcriptionText: {
    fontSize: 13,
    color: '#444',
    lineHeight: 20,
    backgroundColor: '#f8f9fa',
    borderRadius: 8,
    padding: 10,
  },
  emptyContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingVertical: 60,
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
  },
});
