/**
 * HistoryScreen
 *
 * Master list view for completed calls with notes.
 * Minimalist, clickable rows navigating to NoteDetailScreen.
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
  SafeAreaView,
  StatusBar,
  Platform,
} from 'react-native';
import { useNavigation, useFocusEffect } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { MaterialIcons } from '@expo/vector-icons';
import { supabase } from '@/api/supabaseClient';
import { contactLookupService } from '@/services/ContactLookupService';
import { useTheme } from '@/contexts/ThemeContext';
import { spacing, radius, typography } from '@/styles/theme';
import type { CallLog, Client, VoiceReport, Profile } from '@/types';
import type { HistoryStackParamList } from '@/navigation/HistoryStackNavigator';

// Exported for use in NoteDetailScreen
export interface HistoryItem {
  callLog: CallLog;
  client: Client | null;
  voiceReport: VoiceReport | null; // Can be null if user clicked "Skip" without adding note
}

// Grouped history item - multiple calls from same client shown as one entry
export interface HistoryGroup {
  callerPhone: string;
  client: Client | null;
  callLogs: CallLog[];
  voiceReport: VoiceReport | null; // Latest voice report (if any)
  latestTimestamp: string;
  callCount: number;
}

type NavigationProp = NativeStackNavigationProp<HistoryStackParamList, 'HistoryList'>;

/**
 * Format relative time
 */
const formatRelativeTime = (timestamp: string): string => {
  const date = new Date(timestamp);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMins = Math.floor(diffMs / (1000 * 60));
  const diffHours = Math.floor(diffMins / 60);
  const diffDays = Math.floor(diffHours / 24);

  if (diffMins < 1) {
    return 'Teraz';
  } else if (diffMins < 60) {
    return `${diffMins} min temu`;
  } else if (diffHours < 24) {
    return `${diffHours}h temu`;
  } else if (diffDays === 1) {
    return 'Wczoraj';
  } else if (diffDays < 7) {
    return `${diffDays} dni temu`;
  } else {
    return date.toLocaleDateString('pl-PL', {
      day: '2-digit',
      month: '2-digit',
    });
  }
};

export const HistoryScreen: React.FC = () => {
  console.log('📜 Historia: Component rendering');
  const navigation = useNavigation<NavigationProp>();
  const { colors, isDark } = useTheme();
  const styles = createStyles(colors);
  const [historyGroups, setHistoryGroups] = useState<HistoryGroup[]>([]);
  const [filteredGroups, setFilteredGroups] = useState<HistoryGroup[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  // Load contacts once on mount
  useEffect(() => {
    console.log('📜 Historia: useEffect mount');
    loadContacts();
  }, []);

  const loadContacts = async () => {
    await contactLookupService.loadDeviceContacts();
    console.log('📱 Contacts loaded');
  };

  const fetchHistory = useCallback(async () => {
    console.log('📜 Historia: fetchHistory called');
    try {
      // Fetch completed call_logs
      const { data: callLogs, error: callLogsError } = await supabase
        .from('call_logs')
        .select('*')
        .eq('status', 'completed')
        .order('timestamp', { ascending: false });

      console.log('📜 Historia: Query result:', {
        error: callLogsError,
        count: callLogs?.length || 0,
        data: callLogs?.map(cl => ({
          id: cl.id.substring(0, 8),
          phone: cl.caller_phone,
          status: cl.status,
          type: cl.type,
          timestamp: cl.timestamp,
        }))
      });

      if (callLogsError) {
        console.error('Error fetching call logs:', callLogsError);
        return;
      }

      if (!callLogs || callLogs.length === 0) {
        console.log('📜 Historia: No completed calls found, clearing list');
        setHistoryGroups([]);
        setFilteredGroups([]);
        return;
      }

      // Fetch voice_reports
      const callLogIds = callLogs.map((cl) => cl.id);
      const { data: voiceReports, error: voiceReportsError } = await supabase
        .from('voice_reports')
        .select('*')
        .in('call_log_id', callLogIds);

      if (voiceReportsError) {
        console.error('Error fetching voice reports:', voiceReportsError);
        return;
      }

      const voiceReportMap = new Map(
        voiceReports?.map((vr) => [vr.call_log_id, vr]) || []
      );

      // Show ALL completed calls (with or without voice reports)
      if (callLogs.length === 0) {
        setHistoryGroups([]);
        setFilteredGroups([]);
        return;
      }

      // Fetch clients
      const clientIds = [...new Set(
        callLogs
          .map((cl) => cl.client_id)
          .filter((id): id is string => id !== null)
      )];

      let clientMap = new Map<string, Client>();
      if (clientIds.length > 0) {
        const { data: clients } = await supabase
          .from('clients')
          .select('*')
          .in('id', clientIds);
        clientMap = new Map(clients?.map((c) => [c.id, c]) || []);
      }

      // Group call logs by caller_phone
      const groupedByPhone = new Map<string, CallLog[]>();
      for (const callLog of callLogs) {
        const phone = callLog.caller_phone || 'unknown';
        if (!groupedByPhone.has(phone)) {
          groupedByPhone.set(phone, []);
        }
        groupedByPhone.get(phone)!.push(callLog);
      }

      // Create grouped items
      const groups: HistoryGroup[] = [];
      for (const [callerPhone, logs] of groupedByPhone) {
        // Sort by timestamp descending to get latest first
        logs.sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime());
        const latestLog = logs[0];

        // Find any voice report from these call logs (prefer the one with transcription)
        let voiceReport: VoiceReport | null = null;
        for (const log of logs) {
          const vr = voiceReportMap.get(log.id);
          if (vr) {
            voiceReport = vr;
            break; // Use first found (latest)
          }
        }

        groups.push({
          callerPhone,
          client: latestLog.client_id ? clientMap.get(latestLog.client_id) || null : null,
          callLogs: logs,
          voiceReport,
          latestTimestamp: latestLog.timestamp,
          callCount: logs.length,
        });
      }

      // Sort groups by latest timestamp
      groups.sort((a, b) => new Date(b.latestTimestamp).getTime() - new Date(a.latestTimestamp).getTime());

      setHistoryGroups(groups);
      setFilteredGroups(groups);
    } catch (error) {
      console.error('Error in fetchHistory:', error);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  // Fetch history every time screen comes into focus
  useFocusEffect(
    useCallback(() => {
      console.log('📜 Historia: Screen focused!');
      fetchHistory();
    }, [fetchHistory])
  );

  // Filter groups when search query changes
  useEffect(() => {
    if (!searchQuery.trim()) {
      setFilteredGroups(historyGroups);
      return;
    }

    const query = searchQuery.toLowerCase();
    const filtered = historyGroups.filter((group) => {
      const phoneNumber = group.client?.phone || group.callerPhone || '';
      const deviceContactName = contactLookupService.lookupContactName(phoneNumber) || '';
      const clientName = group.client?.name?.toLowerCase() || '';
      const callerPhone = group.callerPhone?.toLowerCase() || '';
      const transcription = group.voiceReport?.transcription?.toLowerCase() || '';
      return (
        deviceContactName.toLowerCase().includes(query) ||
        clientName.includes(query) ||
        callerPhone.includes(query) ||
        transcription.includes(query)
      );
    });
    setFilteredGroups(filtered);
  }, [searchQuery, historyGroups]);

  const handleRefresh = () => {
    console.log('📜 Historia: handleRefresh called');
    setRefreshing(true);
    fetchHistory();
  };

  const handleRowPress = (group: HistoryGroup) => {
    // Navigate to detail view with the first call log that has voice report
    // or just the latest call log if no voice report
    const callLogWithReport = group.callLogs.find(cl =>
      group.voiceReport && group.voiceReport.call_log_id === cl.id
    ) || group.callLogs[0];

    const item: HistoryItem = {
      callLog: callLogWithReport,
      client: group.client,
      voiceReport: group.voiceReport,
    };

    if (group.voiceReport) {
      navigation.navigate('NoteDetail', { item });
    }
    // If no voice report (all skipped) - do nothing
  };

  // Render grouped row
  const renderRow = ({ item: group }: { item: HistoryGroup }) => {
    // Get display name from device contacts or CRM
    const phoneNumber = group.client?.phone || group.callerPhone || null;
    const deviceContactName = contactLookupService.lookupContactName(phoneNumber);
    const crmClientName = group.client?.name || null;

    const hasContactName = !!(deviceContactName || crmClientName);
    const displayPrimary = deviceContactName || crmClientName || phoneNumber || 'Nieznany';
    const displaySecondary = hasContactName ? phoneNumber : null;

    // Format time
    const relativeTime = formatRelativeTime(group.latestTimestamp);

    // Check if has voice report
    const hasVoiceReport = !!group.voiceReport;

    return (
      <TouchableOpacity
        style={styles.row}
        onPress={() => handleRowPress(group)}
        activeOpacity={0.7}
      >
        {/* Left: Icon - check-circle for with note, mic-none for skipped */}
        <View style={styles.iconContainer}>
          <MaterialIcons
            name={hasVoiceReport ? "check-circle" : "mic-none"}
            size={24}
            color={hasVoiceReport ? colors.success : colors.warning}
          />
        </View>

        {/* Center: Name/Phone + Subtitle + Call count */}
        <View style={styles.rowCenter}>
          <View style={styles.rowCenterTop}>
            <Text style={styles.primaryText} numberOfLines={1}>
              {displayPrimary}
            </Text>
            {group.callCount > 1 && (
              <View style={styles.callCountBadge}>
                <Text style={styles.callCountText}>{group.callCount}</Text>
              </View>
            )}
          </View>
          {displaySecondary && (
            <Text style={styles.secondaryText} numberOfLines={1}>
              {displaySecondary}
            </Text>
          )}
        </View>

        {/* Right: Time + Chevron */}
        <View style={styles.rowRight}>
          <Text style={styles.timeText}>{relativeTime}</Text>
          <MaterialIcons name="chevron-right" size={24} color={colors.textTertiary} />
        </View>
      </TouchableOpacity>
    );
  };

  // Separator
  const ItemSeparator = () => <View style={styles.separator} />;

  // Screen header
  const ScreenHeader = () => (
    <View style={styles.headerContainer}>
      <Text style={styles.headerTitle}>Historia</Text>
    </View>
  );

  if (loading) {
    return (
      <SafeAreaView style={styles.safeArea}>
        <StatusBar barStyle={isDark ? 'light-content' : 'dark-content'} backgroundColor={colors.background} />
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color={colors.primary} />
          <Text style={styles.loadingText}>Ładowanie historii...</Text>
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="dark-content" backgroundColor={colors.background} />
      <ScreenHeader />

      {/* Search Bar */}
      <View style={styles.searchContainer}>
        <MaterialIcons name="search" size={20} color={colors.textTertiary} />
        <TextInput
          style={styles.searchInput}
          placeholder="Szukaj..."
          placeholderTextColor={colors.textTertiary}
          value={searchQuery}
          onChangeText={setSearchQuery}
        />
        {searchQuery.length > 0 && (
          <TouchableOpacity onPress={() => setSearchQuery('')}>
            <MaterialIcons name="close" size={20} color={colors.textTertiary} />
          </TouchableOpacity>
        )}
      </View>

      {/* Results count */}
      <Text style={styles.resultsCount}>
        {filteredGroups.length} {filteredGroups.length === 1 ? 'klient' : 'klientów'}
        {searchQuery ? ` dla "${searchQuery}"` : ''}
      </Text>

      {/* List */}
      <FlatList
        data={filteredGroups}
        keyExtractor={(group) => group.callerPhone}
        renderItem={renderRow}
        ItemSeparatorComponent={ItemSeparator}
        contentContainerStyle={styles.listContent}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={handleRefresh} />
        }
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <MaterialIcons name="history" size={48} color={colors.textTertiary} />
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

// Dynamic styles generator
const createStyles = (colors: ReturnType<typeof import('@/contexts/ThemeContext').useTheme>['colors']) =>
  StyleSheet.create({
    safeArea: {
      flex: 1,
      backgroundColor: colors.background,
      paddingTop: Platform.OS === 'android' ? StatusBar.currentHeight : 0,
    },
    headerContainer: {
      backgroundColor: colors.surface,
      paddingHorizontal: spacing.lg,
      paddingVertical: spacing.lg,
      borderBottomWidth: 1,
      borderBottomColor: colors.border,
    },
    headerTitle: {
      fontSize: typography.xxl,
      fontWeight: typography.bold,
      color: colors.textPrimary,
    },
    loadingContainer: {
      flex: 1,
      justifyContent: 'center',
      alignItems: 'center',
    },
    loadingText: {
      marginTop: spacing.md,
      fontSize: typography.base,
      color: colors.textSecondary,
    },

    // Search
    searchContainer: {
      flexDirection: 'row',
      alignItems: 'center',
      backgroundColor: colors.surface,
      marginHorizontal: spacing.lg,
      marginTop: spacing.md,
      marginBottom: spacing.xs,
      paddingHorizontal: spacing.md,
      paddingVertical: spacing.sm,
      borderRadius: radius.lg,
      borderWidth: 1,
      borderColor: colors.border,
    },
    searchInput: {
      flex: 1,
      marginLeft: spacing.sm,
      fontSize: typography.base,
      color: colors.textPrimary,
      paddingVertical: spacing.xs,
    },
    resultsCount: {
      fontSize: typography.sm,
      color: colors.textTertiary,
      marginHorizontal: spacing.lg,
      marginVertical: spacing.sm,
    },

    // List
    listContent: {
      paddingBottom: spacing.xl,
    },

    // Row
    row: {
      flexDirection: 'row',
      alignItems: 'center',
      backgroundColor: colors.surface,
      paddingVertical: spacing.md,
      paddingHorizontal: spacing.lg,
    },
    iconContainer: {
      width: 44,
      height: 44,
      borderRadius: 22,
      justifyContent: 'center',
      alignItems: 'center',
      marginRight: spacing.md,
      backgroundColor: colors.borderLight,
    },
    rowCenter: {
      flex: 1,
    },
    rowCenterTop: {
      flexDirection: 'row',
      alignItems: 'center',
    },
    callCountBadge: {
      backgroundColor: colors.primary,
      borderRadius: 10,
      paddingHorizontal: 6,
      paddingVertical: 2,
      marginLeft: spacing.sm,
    },
    callCountText: {
      fontSize: typography.xs,
      fontWeight: typography.bold,
      color: colors.textInverse,
    },
    primaryText: {
      fontSize: typography.lg,
      fontWeight: typography.semibold,
      color: colors.textPrimary,
      marginBottom: 2,
    },
    secondaryText: {
      fontSize: typography.sm,
      color: colors.textSecondary,
    },
    rowRight: {
      flexDirection: 'row',
      alignItems: 'center',
    },
    timeText: {
      fontSize: typography.sm,
      color: colors.textTertiary,
      marginRight: spacing.xs,
    },
    separator: {
      height: 1,
      backgroundColor: colors.borderLight,
      marginLeft: 72,
    },

    // Empty
    emptyContainer: {
      flex: 1,
      justifyContent: 'center',
      alignItems: 'center',
      paddingVertical: 60,
      paddingHorizontal: 40,
    },
    emptyTitle: {
      fontSize: typography.lg,
      fontWeight: typography.semibold,
      color: colors.textPrimary,
      marginTop: spacing.md,
      marginBottom: spacing.sm,
    },
    emptyText: {
      fontSize: typography.sm,
      color: colors.textTertiary,
      textAlign: 'center',
      lineHeight: 20,
    },
  });
