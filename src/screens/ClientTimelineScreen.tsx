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
import { colors, spacing, radius, typography, shadows, commonStyles } from '@/styles/theme';

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
        return colors.error;
      case 'reserved':
        return colors.warning;
      case 'completed':
        return colors.success;
      default:
        return colors.textSecondary;
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
          <ActivityIndicator size="large" color={colors.primary} />
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
  container: commonStyles.screen,
  loadingContainer: {
    ...commonStyles.centered,
    backgroundColor: colors.background,
  },
  loadingText: {
    marginTop: spacing.md,
    fontSize: typography.base,
    color: colors.textSecondary,
  },
  clientHeader: {
    ...commonStyles.rowBetween,
    padding: spacing.lg,
    backgroundColor: colors.white,
    borderBottomWidth: 1,
    borderBottomColor: colors.borderLight,
  },
  clientInfo: {
    flex: 1,
  },
  clientName: {
    ...commonStyles.heading,
  },
  clientPhone: {
    fontSize: typography.base,
    color: colors.primary,
    marginTop: spacing.xs,
  },
  clientAddress: {
    fontSize: typography.sm,
    color: colors.textSecondary,
    marginTop: spacing.xs,
  },
  callButton: {
    backgroundColor: colors.success,
    paddingVertical: spacing.md,
    paddingHorizontal: spacing.lg,
    borderRadius: radius.md,
  },
  callButtonText: {
    color: colors.textInverse,
    fontSize: typography.base,
    fontWeight: typography.semibold,
  },
  statsRow: {
    flexDirection: 'row',
    backgroundColor: colors.white,
    paddingVertical: spacing.md,
    paddingHorizontal: spacing.lg,
    borderBottomWidth: 1,
    borderBottomColor: colors.borderLight,
  },
  statItem: {
    flex: 1,
    alignItems: 'center',
  },
  statValue: {
    fontSize: typography.xl,
    fontWeight: typography.bold,
    color: colors.primary,
  },
  statLabel: {
    fontSize: typography.xs,
    color: colors.textSecondary,
    marginTop: 2,
  },
  listContent: {
    padding: spacing.lg,
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
    backgroundColor: colors.border,
    marginVertical: 4,
  },
  timelineContent: {
    flex: 1,
    backgroundColor: colors.white,
    borderRadius: radius.lg,
    padding: spacing.lg,
    marginLeft: spacing.sm,
    marginBottom: spacing.md,
    ...shadows.sm,
  },
  timelineHeader: {
    ...commonStyles.rowBetween,
    marginBottom: spacing.sm,
  },
  timelineDate: {
    fontSize: typography.sm,
    color: colors.textSecondary,
  },
  statusBadge: {
    paddingVertical: 4,
    paddingHorizontal: 8,
    borderRadius: 12,
  },
  statusText: {
    fontSize: typography.xs,
    fontWeight: typography.bold,
  },
  handledBy: {
    fontSize: typography.xs,
    color: colors.textTertiary,
    marginBottom: spacing.sm,
  },
  voiceReportSection: {
    marginTop: spacing.sm,
  },
  sectionTitle: {
    fontSize: typography.sm,
    fontWeight: typography.semibold,
    color: colors.primary,
    marginBottom: spacing.xs,
  },
  summaryContainer: {
    backgroundColor: colors.background,
    borderRadius: radius.md,
    padding: spacing.md,
  },
  summaryHeader: {
    fontSize: typography.sm,
    fontWeight: typography.semibold,
    color: colors.textPrimary,
    marginBottom: spacing.xs,
    marginTop: 2,
  },
  summaryText: {
    fontSize: typography.sm,
    color: colors.textSecondary,
    lineHeight: 18,
    marginBottom: 2,
  },
  bulletRow: {
    flexDirection: 'row',
    marginBottom: 2,
  },
  bullet: {
    fontSize: typography.sm,
    color: colors.primary,
    marginRight: spacing.sm,
  },
  bulletText: {
    flex: 1,
    fontSize: typography.sm,
    color: colors.textSecondary,
    lineHeight: 18,
  },
  actions: {
    flexDirection: 'row',
    gap: spacing.sm,
    marginTop: spacing.md,
  },
  actionButton: {
    flex: 1,
    backgroundColor: colors.primary,
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.md,
    borderRadius: radius.sm,
    alignItems: 'center',
  },
  actionButtonActive: {
    backgroundColor: colors.error,
  },
  actionButtonSecondary: {
    backgroundColor: colors.white,
    borderWidth: 1,
    borderColor: colors.primary,
  },
  actionButtonText: {
    color: colors.textInverse,
    fontSize: typography.sm,
    fontWeight: typography.semibold,
  },
  actionButtonTextSecondary: {
    color: colors.primary,
    fontSize: typography.sm,
    fontWeight: typography.semibold,
  },
  noReportBadge: {
    marginTop: spacing.sm,
    padding: spacing.sm,
    backgroundColor: colors.errorLight,
    borderRadius: radius.sm,
  },
  noReportText: {
    fontSize: typography.sm,
    color: colors.error,
    fontWeight: typography.medium,
  },
  transcriptionSection: {
    marginTop: spacing.md,
    paddingTop: spacing.md,
    borderTopWidth: 1,
    borderTopColor: colors.borderLight,
  },
  transcriptionText: {
    fontSize: typography.sm,
    color: colors.textSecondary,
    lineHeight: 20,
    backgroundColor: colors.background,
    borderRadius: radius.md,
    padding: spacing.md,
  },
  emptyContainer: {
    ...commonStyles.emptyState,
    paddingVertical: 60,
  },
  emptyIcon: {
    ...commonStyles.emptyStateIcon,
  },
  emptyTitle: {
    ...commonStyles.emptyStateTitle,
  },
  emptyText: {
    ...commonStyles.emptyStateText,
  },
});
