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
  Alert,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useFocusEffect } from '@react-navigation/native';
import { supabase } from '@/api/supabaseClient';
import { contactLookupService } from '@/services/ContactLookupService';
import { useTheme } from '@/contexts/ThemeContext';
import type { Client, CallLog, VoiceReport, Profile } from '@/types';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

type ClientsStackParamList = {
  ClientsList: undefined;
  ClientTimeline: { client: Client };
  EditClient: { client: Client; onClientUpdated?: (updatedClient: Client) => void };
};

type Props = NativeStackScreenProps<ClientsStackParamList, 'ClientTimeline'>;

interface TimelineItem {
  callLog: CallLog;
  voiceReport: VoiceReport | null;
  mergedCalls: CallLog[];  // Other calls that were merged with this one
}

export const ClientTimelineScreen: React.FC<Props> = ({ route, navigation }) => {
  const { client: initialClient } = route.params;
  const { colors } = useTheme();
  const styles = createStyles(colors);
  const [client, setClient] = useState<Client>(initialClient);
  const [timelineItems, setTimelineItems] = useState<TimelineItem[]>([]);
  const [profiles, setProfiles] = useState<Map<string, Profile>>(new Map());
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [expandedItems, setExpandedItems] = useState<Set<string>>(new Set());

  const handleEditClient = () => {
    navigation.navigate('EditClient', {
      client,
      onClientUpdated: (updatedClient: Client) => {
        setClient(updatedClient);
      },
    });
  };

  const handleDeleteClient = () => {
    Alert.alert(
      'Usuń klienta',
      `Czy na pewno chcesz usunąć klienta "${client.name || client.phone}"?\n\nTa operacja usunie również wszystkie połączenia i notatki powiązane z tym klientem.`,
      [
        { text: 'Anuluj', style: 'cancel' },
        {
          text: 'Usuń',
          style: 'destructive',
          onPress: async () => {
            try {
              const { error } = await supabase
                .from('clients')
                .delete()
                .eq('id', client.id);

              if (error) {
                throw error;
              }

              navigation.goBack();
            } catch (err) {
              console.error('Error deleting client:', err);
              Alert.alert('Błąd', 'Nie udało się usunąć klienta. Spróbuj ponownie.');
            }
          },
        },
      ]
    );
  };

  const fetchProfiles = async () => {
    try {
      const { data, error } = await supabase.from('profiles').select('*');
      if (error) {
        console.error('Error fetching profiles:', error);
        return;
      }
      const profileMap = new Map<string, Profile>();
      data?.forEach((profile: Profile) => profileMap.set(profile.id, profile));
      setProfiles(profileMap);
    } catch (error) {
      console.error('Error fetching profiles:', error);
    }
  };

  // Fetch fresh client data from database
  const fetchClient = useCallback(async () => {
    try {
      const { data, error } = await supabase
        .from('clients')
        .select('*')
        .eq('id', initialClient.id)
        .single();

      if (error) {
        console.error('Error fetching client:', error);
        return;
      }

      if (data) {
        setClient(data);
      }
    } catch (error) {
      console.error('Error fetching client:', error);
    }
  }, [initialClient.id]);

  // Refresh client data when screen comes into focus
  useFocusEffect(
    useCallback(() => {
      fetchClient();
    }, [fetchClient])
  );

  const getDisplayName = (userId: string | null): string | null => {
    if (!userId) return null;
    const profile = profiles.get(userId);
    return profile?.display_name || null;
  };

  const getClientDisplayName = (): string => {
    // Priority: 1. Device contacts, 2. CRM client name, 3. Phone number
    const deviceContactName = contactLookupService.lookupContactName(client.phone);
    const crmClientName = client.name;
    return deviceContactName || crmClientName || client.phone || 'Nieznany klient';
  };

  const fetchTimeline = useCallback(async () => {
    try {
      // Fetch ALL call_logs for this client (including merged)
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

      // Separate main calls from merged calls
      const mainCalls = callLogs.filter((cl) => cl.type !== 'merged');
      const mergedCalls = callLogs.filter((cl) => cl.type === 'merged');

      // Track which merged calls have been assigned
      const assignedMergedIds = new Set<string>();

      // Group merged calls with their main call
      const items: TimelineItem[] = mainCalls.map((callLog) => {
        // First: find merged calls that explicitly point to this call (new behavior)
        const linkedMerged = mergedCalls.filter(
          (mc) => mc.merged_into_id === callLog.id
        );
        linkedMerged.forEach((mc) => assignedMergedIds.add(mc.id));

        // Second: for calls with voice report, find old merged calls (without merged_into_id)
        // that have same caller_phone and were merged around the same time
        let legacyMerged: CallLog[] = [];
        if (voiceReportMap.has(callLog.id)) {
          const mainTime = new Date(callLog.timestamp).getTime();
          legacyMerged = mergedCalls.filter((mc) => {
            if (assignedMergedIds.has(mc.id)) return false;
            if (mc.merged_into_id) return false; // Has explicit link, skip
            if (mc.caller_phone !== callLog.caller_phone) return false;
            // Only group if within 24 hours
            const mcTime = new Date(mc.timestamp).getTime();
            return Math.abs(mcTime - mainTime) < 24 * 60 * 60 * 1000;
          });
          legacyMerged.forEach((mc) => assignedMergedIds.add(mc.id));
        }

        return {
          callLog,
          voiceReport: voiceReportMap.get(callLog.id) || null,
          mergedCalls: [...linkedMerged, ...legacyMerged],
        };
      });

      setTimelineItems(items);
    } catch (error) {
      console.error('Error in fetchTimeline:', error);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [client.id]);

  useEffect(() => {
    contactLookupService.loadDeviceContacts();
    fetchProfiles();
    fetchTimeline();
  }, [fetchTimeline]);

  const handleRefresh = () => {
    setRefreshing(true);
    fetchClient();
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
    const hasVoiceReport = !!item.voiceReport;
    const hasMergedCalls = item.mergedCalls && item.mergedCalls.length > 0;
    const totalCalls = 1 + (item.mergedCalls?.length || 0);

    // Collect all call timestamps (main + merged), sorted by time
    const allCallDates = [
      item.callLog.timestamp,
      ...(item.mergedCalls?.map((mc) => mc.timestamp) || []),
    ].sort((a, b) => new Date(b).getTime() - new Date(a).getTime());

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
            <View>
              <Text style={styles.timelineDate}>
                {formatDate(item.callLog.timestamp)}
              </Text>
              {hasMergedCalls && (
                <Text style={styles.callCountLabel}>
                  📞 {totalCalls} połączeń
                </Text>
              )}
            </View>
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

          {/* Show all call dates if there are merged calls */}
          {hasMergedCalls && (
            <View style={styles.mergedCallsSection}>
              <Text style={styles.mergedCallsLabel}>Wszystkie próby połączeń:</Text>
              {allCallDates.map((date, idx) => (
                <Text key={idx} style={styles.mergedCallDate}>
                  • {formatDate(date)}
                </Text>
              ))}
            </View>
          )}

          {item.callLog.reservation_by && (
            <Text style={styles.handledBy}>
              Obsłużył: {getDisplayName(item.callLog.reservation_by) || item.callLog.reservation_by}
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
          {hasVoiceReport && item.voiceReport?.transcription && (
            <View style={styles.actions}>
              <TouchableOpacity
                style={styles.actionButton}
                onPress={() => toggleExpanded(item.callLog.id)}
              >
                <Text style={styles.actionButtonText}>
                  {isExpanded ? '▲ Zwiń' : '▼ Notatka'}
                </Text>
              </TouchableOpacity>
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
              <Text style={styles.sectionTitle}>📄 Pełna notatka</Text>
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
          <Text style={styles.clientName}>{getClientDisplayName()}</Text>
          <Text style={styles.clientPhone}>{client.phone}</Text>
          {client.address && (
            <Text style={styles.clientAddress}>📍 {client.address}</Text>
          )}
          {client.notes && (
            <Text style={styles.clientNotes}>📝 {client.notes}</Text>
          )}
        </View>
        <View style={styles.headerButtons}>
          <TouchableOpacity style={styles.callButton} onPress={handleCall}>
            <Text style={styles.buttonIcon}>📞</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.editButton} onPress={handleEditClient}>
            <Text style={styles.buttonIcon}>✏️</Text>
          </TouchableOpacity>
          <TouchableOpacity style={styles.deleteButton} onPress={handleDeleteClient}>
            <Text style={styles.buttonIcon}>🗑️</Text>
          </TouchableOpacity>
        </View>
      </View>

      {/* Stats */}
      <View style={styles.statsRow}>
        <View style={styles.statItem}>
          <Text style={styles.statValue}>
            {timelineItems.reduce((sum, i) => sum + 1 + (i.mergedCalls?.length || 0), 0)}
          </Text>
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
            {timelineItems.reduce((sum, i) => {
              const mainMissed = i.callLog.status === 'missed' ? 1 : 0;
              const mergedMissed = i.mergedCalls?.filter((mc) => mc.status === 'missed').length || 0;
              return sum + mainMissed + mergedMissed;
            }, 0)}
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

// Dynamic styles generator
const createStyles = (colors: ReturnType<typeof import('@/contexts/ThemeContext').useTheme>['colors']) =>
  StyleSheet.create({
    container: {
      flex: 1,
      backgroundColor: colors.background,
    },
    loadingContainer: {
      flex: 1,
      justifyContent: 'center',
      alignItems: 'center',
    },
    loadingText: {
      marginTop: 12,
      fontSize: 16,
      color: colors.textSecondary,
    },
    clientHeader: {
      flexDirection: 'row',
      justifyContent: 'space-between',
      alignItems: 'center',
      backgroundColor: colors.surface,
      padding: 16,
      borderBottomWidth: 1,
      borderBottomColor: colors.border,
    },
    clientInfo: {
      flex: 1,
    },
    clientName: {
      fontSize: 20,
      fontWeight: '700',
      color: colors.textPrimary,
    },
    clientPhone: {
      fontSize: 16,
      color: colors.primary,
      marginTop: 2,
    },
    clientAddress: {
      fontSize: 14,
      color: colors.textSecondary,
      marginTop: 4,
    },
    clientNotes: {
      fontSize: 13,
      color: colors.textTertiary,
      marginTop: 4,
      fontStyle: 'italic',
    },
    headerButtons: {
      flexDirection: 'row',
      gap: 8,
    },
    callButton: {
      backgroundColor: colors.success,
      paddingVertical: 10,
      paddingHorizontal: 14,
      borderRadius: 8,
    },
    editButton: {
      backgroundColor: colors.surface,
      borderWidth: 1,
      borderColor: colors.border,
      paddingVertical: 10,
      paddingHorizontal: 14,
      borderRadius: 8,
    },
    deleteButton: {
      backgroundColor: colors.errorLight,
      paddingVertical: 10,
      paddingHorizontal: 14,
      borderRadius: 8,
    },
    buttonIcon: {
      fontSize: 16,
    },
    statsRow: {
      flexDirection: 'row',
      backgroundColor: colors.surface,
      paddingVertical: 12,
      paddingHorizontal: 16,
      borderBottomWidth: 1,
      borderBottomColor: colors.border,
    },
    statItem: {
      flex: 1,
      alignItems: 'center',
    },
    statValue: {
      fontSize: 20,
      fontWeight: '700',
      color: colors.primary,
    },
    statLabel: {
      fontSize: 12,
      color: colors.textSecondary,
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
      backgroundColor: colors.borderLight,
      marginVertical: 4,
    },
    timelineContent: {
      flex: 1,
      backgroundColor: colors.surface,
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
      color: colors.textSecondary,
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
      color: colors.textTertiary,
      marginBottom: 8,
    },
    callCountLabel: {
      fontSize: 12,
      color: colors.primary,
      fontWeight: '600',
      marginTop: 2,
    },
    mergedCallsSection: {
      backgroundColor: colors.background,
      borderRadius: 8,
      padding: 10,
      marginBottom: 8,
    },
    mergedCallsLabel: {
      fontSize: 12,
      fontWeight: '600',
      color: colors.textSecondary,
      marginBottom: 4,
    },
    mergedCallDate: {
      fontSize: 12,
      color: colors.textTertiary,
      marginLeft: 4,
      marginTop: 2,
    },
    voiceReportSection: {
      marginTop: 8,
    },
    sectionTitle: {
      fontSize: 13,
      fontWeight: '600',
      color: colors.primary,
      marginBottom: 6,
    },
    summaryContainer: {
      backgroundColor: colors.background,
      borderRadius: 8,
      padding: 10,
    },
    summaryHeader: {
      fontSize: 13,
      fontWeight: '600',
      color: colors.textPrimary,
      marginBottom: 4,
      marginTop: 2,
    },
    summaryText: {
      fontSize: 13,
      color: colors.textSecondary,
      lineHeight: 18,
      marginBottom: 2,
    },
    bulletRow: {
      flexDirection: 'row',
      marginBottom: 2,
    },
    bullet: {
      fontSize: 13,
      color: colors.primary,
      marginRight: 6,
    },
    bulletText: {
      flex: 1,
      fontSize: 13,
      color: colors.textSecondary,
      lineHeight: 18,
    },
    actions: {
      flexDirection: 'row',
      marginTop: 10,
    },
    actionButton: {
      flex: 1,
      backgroundColor: colors.surface,
      borderWidth: 1,
      borderColor: colors.primary,
      paddingVertical: 8,
      paddingHorizontal: 12,
      borderRadius: 6,
      alignItems: 'center',
    },
    actionButtonText: {
      color: colors.primary,
      fontSize: 13,
      fontWeight: '600',
    },
    noReportBadge: {
      marginTop: 8,
      padding: 8,
      backgroundColor: colors.errorLight,
      borderRadius: 6,
    },
    noReportText: {
      fontSize: 12,
      color: colors.error,
      fontWeight: '500',
    },
    transcriptionSection: {
      marginTop: 10,
      paddingTop: 10,
      borderTopWidth: 1,
      borderTopColor: colors.border,
    },
    transcriptionText: {
      fontSize: 13,
      color: colors.textSecondary,
      lineHeight: 20,
      backgroundColor: colors.background,
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
      color: colors.textPrimary,
      marginBottom: 8,
    },
    emptyText: {
      fontSize: 14,
      color: colors.textSecondary,
      textAlign: 'center',
    },
  });
