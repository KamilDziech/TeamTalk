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
import { useNavigation } from '@react-navigation/native';
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
  voiceReport: VoiceReport;
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
  const navigation = useNavigation<NavigationProp>();
  const { colors, isDark } = useTheme();
  const styles = createStyles(colors);
  const [historyItems, setHistoryItems] = useState<HistoryItem[]>([]);
  const [filteredItems, setFilteredItems] = useState<HistoryItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  useEffect(() => {
    loadDeviceContacts();
    fetchHistory();
  }, []);

  const loadDeviceContacts = async () => {
    await contactLookupService.loadDeviceContacts();
  };

  const fetchHistory = useCallback(async () => {
    try {
      // Fetch completed call_logs
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
      const callLogsWithReports = callLogs.filter((cl) =>
        voiceReportMap.has(cl.id)
      );

      if (callLogsWithReports.length === 0) {
        setHistoryItems([]);
        setFilteredItems([]);
        return;
      }

      // Fetch clients
      const clientIds = [...new Set(
        callLogsWithReports
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

      // Combine data
      const items: HistoryItem[] = callLogsWithReports
        .map((callLog) => ({
          callLog,
          client: callLog.client_id ? clientMap.get(callLog.client_id) || null : null,
          voiceReport: voiceReportMap.get(callLog.id)!,
        }))
        .filter((item) => item.voiceReport);

      setHistoryItems(items);
      setFilteredItems(items);
    } catch (error) {
      console.error('Error in fetchHistory:', error);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  // Filter items when search query changes
  useEffect(() => {
    if (!searchQuery.trim()) {
      setFilteredItems(historyItems);
      return;
    }

    const query = searchQuery.toLowerCase();
    const filtered = historyItems.filter((item) => {
      const phoneNumber = item.client?.phone || item.callLog.caller_phone || '';
      const deviceContactName = contactLookupService.lookupContactName(phoneNumber) || '';
      const clientName = item.client?.name?.toLowerCase() || '';
      const callerPhone = item.callLog.caller_phone?.toLowerCase() || '';
      const summary = item.voiceReport.ai_summary?.toLowerCase() || '';
      const transcription = item.voiceReport.transcription?.toLowerCase() || '';
      return (
        deviceContactName.toLowerCase().includes(query) ||
        clientName.includes(query) ||
        callerPhone.includes(query) ||
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

  const handleRowPress = (item: HistoryItem) => {
    navigation.navigate('NoteDetail', { item });
  };

  // Render minimalist row
  const renderRow = ({ item }: { item: HistoryItem }) => {
    // Get display name from device contacts or CRM
    const phoneNumber = item.client?.phone || item.callLog.caller_phone || null;
    const deviceContactName = contactLookupService.lookupContactName(phoneNumber);
    const crmClientName = item.client?.name || null;

    const hasContactName = !!(deviceContactName || crmClientName);
    const displayPrimary = deviceContactName || crmClientName || phoneNumber || 'Nieznany';
    const displaySecondary = hasContactName ? phoneNumber : null;

    // Format time
    const relativeTime = formatRelativeTime(item.callLog.timestamp);

    // Check if has summary
    const hasSummary = !!item.voiceReport.ai_summary;

    return (
      <TouchableOpacity
        style={styles.row}
        onPress={() => handleRowPress(item)}
        activeOpacity={0.7}
      >
        {/* Left: Icon */}
        <View style={styles.iconContainer}>
          <MaterialIcons
            name={hasSummary ? 'task-alt' : 'check-circle'}
            size={24}
            color={colors.success}
          />
        </View>

        {/* Center: Name/Phone + Subtitle */}
        <View style={styles.rowCenter}>
          <Text style={styles.primaryText} numberOfLines={1}>
            {displayPrimary}
          </Text>
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
        {filteredItems.length} {filteredItems.length === 1 ? 'rozmowa' : 'rozmów'}
        {searchQuery ? ` dla "${searchQuery}"` : ''}
      </Text>

      {/* List */}
      <FlatList
        data={filteredItems}
        keyExtractor={(item) => item.callLog.id}
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
