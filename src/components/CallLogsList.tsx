/**
 * CallLogsList Component
 *
 * Master list view in Master-Detail pattern.
 * Displays minimalist, clickable rows mimicking native Android call history.
 * Tapping a row navigates to CallDetailsScreen.
 *
 * Shared database model: All calls visible to everyone with recipient labels.
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
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { MaterialIcons } from '@expo/vector-icons';
import { supabase } from '@/api/supabaseClient';
import { callLogScanner } from '@/services/CallLogScanner';
import { contactLookupService } from '@/services/ContactLookupService';
import { useAuth } from '@/contexts/AuthContext';
import { useTheme } from '@/contexts/ThemeContext';
import { spacing, radius, typography } from '@/styles/theme';
import type { CallLog, Client, Profile } from '@/types';
import type { CallLogsStackParamList } from '@/navigation/CallLogsStackNavigator';

interface CallLogWithClient extends CallLog {
  client: Client | null;
  hasVoiceReport: boolean;
}

// Exported for use in CallDetailsScreen
export interface GroupedCallLog {
  groupKey: string;
  clientId: string | null;
  client: Client | null;
  callerPhone: string | null;
  callCount: number;
  latestCall: CallLogWithClient;
  allCalls: CallLogWithClient[];
  firstCallTime: string;
  lastCallTime: string;
  // Recipients: user IDs who missed this call
  recipients: string[];
  // Multi-agent alert: true if this number contacted multiple agents
  isMultiAgent: boolean;
  involvedAgentIds: string[];
}

/**
 * Groups call logs by client or phone number
 */
const groupCallLogsByClient = (logs: CallLogWithClient[]): GroupedCallLog[] => {
  const groupMap = new Map<string, CallLogWithClient[]>();

  logs.forEach((log) => {
    const groupKey = log.client_id || log.caller_phone;
    if (!groupKey) return;

    const existing = groupMap.get(groupKey) || [];
    existing.push(log);
    groupMap.set(groupKey, existing);
  });

  const grouped: GroupedCallLog[] = [];

  groupMap.forEach((calls, groupKey) => {
    calls.sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime());

    const priorityOrder = { missed: 0, reserved: 1, completed: 2 };
    const latestCall = calls.reduce((prev, curr) => {
      if (priorityOrder[curr.status] < priorityOrder[prev.status]) return curr;
      return prev;
    }, calls[0]);

    const missedCalls = calls.filter((c) => c.status === 'missed');
    const hasClient = calls[0].client_id !== null;
    // Collect recipients from all calls
    const allRecipients = new Set<string>();
    calls.forEach((call) => {
      if (call.recipients) {
        call.recipients.forEach((r: string) => allRecipients.add(r));
      }
    });

    // Multi-agent detection: collect unique agent IDs
    const agentIds = new Set<string>();
    calls.forEach((call) => {
      if (call.recipients) {
        call.recipients.forEach((r: string) => agentIds.add(r));
      }
      if (call.employee_id) {
        agentIds.add(call.employee_id);
      }
    });
    const involvedAgentIds = Array.from(agentIds);
    const isMultiAgent = involvedAgentIds.length > 1;

    grouped.push({
      groupKey,
      clientId: hasClient ? groupKey : null,
      client: calls[0].client,
      callerPhone: !hasClient ? calls[0].caller_phone : null,
      callCount: missedCalls.length > 0 ? missedCalls.length : calls.length,
      latestCall,
      allCalls: calls,
      firstCallTime: calls[calls.length - 1].timestamp,
      lastCallTime: calls[0].timestamp,
      recipients: Array.from(allRecipients),
      isMultiAgent,
      involvedAgentIds,
    });
  });

  grouped.sort((a, b) => {
    const aHasMissed = a.allCalls.some((c) => c.status === 'missed');
    const bHasMissed = b.allCalls.some((c) => c.status === 'missed');
    if (aHasMissed && !bHasMissed) return -1;
    if (!aHasMissed && bHasMissed) return 1;
    return new Date(b.lastCallTime).getTime() - new Date(a.lastCallTime).getTime();
  });

  return grouped;
};

type NavigationProp = NativeStackNavigationProp<CallLogsStackParamList, 'CallLogsList'>;

export const CallLogsList: React.FC = () => {
  console.log('📋 CallLogsList: Component rendering START');
  const { user } = useAuth();
  const { colors } = useTheme();
  const styles = createStyles(colors);
  const navigation = useNavigation<NavigationProp>();
  const [groupedLogs, setGroupedLogs] = useState<GroupedCallLog[]>([]);
  const [profiles, setProfiles] = useState<Map<string, Profile>>(new Map());
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [syncStatus, setSyncStatus] = useState<string | null>(null);

  useEffect(() => {
    console.log('📋 CallLogsList: useEffect running');
    initializeData();
    setupRealtimeSubscription();
  }, []);

  // Load device contacts FIRST, then fetch data
  const initializeData = async () => {
    const loaded = await contactLookupService.loadDeviceContacts();
    console.log('📱 Device contacts loaded:', loaded);
    console.log('📱 Now fetching call logs and profiles...');
    await fetchProfiles();
    await fetchCallLogs();
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

  const getDisplayName = (userId: string | null): string | null => {
    if (!userId) return null;
    const profile = profiles.get(userId);
    return profile?.display_name || null;
  };

  const fetchCallLogs = async () => {
    try {
      setLoading(true);

      // Fetch ALL call logs (shared database - all visible to everyone)
      const { data: allLogs, error } = await supabase
        .from('call_logs')
        .select(`*, clients (*)`)
        .order('timestamp', { ascending: false })
        .limit(100);

      if (error) throw error;

      // Check for voice reports
      const logsWithReports = await Promise.all(
        (allLogs || []).map(async (log: any) => {
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

      // Filter only queue items (missed and reserved)
      const queueLogs = (logsWithReports as CallLogWithClient[]).filter(
        (log) => log.status === 'missed' || log.status === 'reserved'
      );

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

  const onRefresh = async () => {
    setRefreshing(true);
    setSyncStatus('Synchronizacja połączeń...');

    try {
      console.log('🔄 Manual refresh - scanning call log...');
      await callLogScanner.scanMissedCalls();
      await fetchCallLogs();
      setSyncStatus('Zsynchronizowano');
    } catch (error) {
      console.error('Error during refresh:', error);
      setSyncStatus('Błąd synchronizacji');
    } finally {
      setTimeout(() => setSyncStatus(null), 2000);
      setRefreshing(false);
    }
  };

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
                .gte('id', '00000000-0000-0000-0000-000000000000');

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

  // Navigate to detail screen
  const handleRowPress = (group: GroupedCallLog) => {
    navigation.navigate('CallDetails', { group });
  };

  // Render minimalist row
  const renderRow = ({ item }: { item: GroupedCallLog }) => {
    const hasMissedCalls = item.allCalls.some((c) => c.status === 'missed');
    const missedCount = item.allCalls.filter((c) => c.status === 'missed').length;

    // Get phone number
    const phoneNumber = item.client?.phone || item.callerPhone || null;

    // Priority: 1. Device contacts, 2. CRM client name, 3. Phone number
    const deviceContactName = contactLookupService.lookupContactName(phoneNumber);
    const crmClientName = item.client?.name || null;

    const hasContactName = !!(deviceContactName || crmClientName);
    const displayPrimary = deviceContactName || crmClientName || phoneNumber || 'Nieznany';
    const displaySecondary = hasContactName ? phoneNumber : null;
    const displayCount = missedCount > 1 ? ` (${missedCount})` : '';

    // Handler name for reserved calls
    const handlerName = item.latestCall.reservation_by && item.latestCall.status === 'reserved'
      ? getDisplayName(item.latestCall.reservation_by)
      : null;

    // Recipients label: "Do: Kamil, Marcin"
    const recipientNames = item.recipients
      .map((id) => getDisplayName(id))
      .filter(Boolean)
      .join(', ');

    // Time of last call
    const lastCallTime = new Date(item.lastCallTime).toLocaleTimeString('pl-PL', {
      hour: '2-digit',
      minute: '2-digit',
    });

    // Icon and color based on status
    const iconName = hasMissedCalls ? 'phone-missed' : 'phone-in-talk';
    const statusColor = hasMissedCalls ? colors.error : colors.primary;

    return (
      <TouchableOpacity
        style={styles.row}
        onPress={() => handleRowPress(item)}
        activeOpacity={0.7}
      >
        {/* Left: Material Icon */}
        <View style={styles.iconContainer}>
          <MaterialIcons name={iconName} size={24} color={statusColor} />
        </View>

        {/* Center: Name/Phone + Subtitle + Recipients */}
        <View style={styles.rowCenter}>
          <Text style={styles.phoneText}>
            {displayPrimary}{displayCount}
          </Text>
          {displaySecondary && (
            <Text style={styles.subtitleText}>{displaySecondary}</Text>
          )}
          {recipientNames && (
            <Text style={styles.recipientLabel}>Do: {recipientNames}</Text>
          )}
          {handlerName && (
            <Text style={styles.handlerText}>Obsługuje: {handlerName}</Text>
          )}
        </View>

        {/* Right: Multi-agent warning + Time + Chevron */}
        <View style={styles.rowRight}>
          {item.isMultiAgent && (
            <MaterialIcons name="warning" size={18} color="#F59E0B" style={styles.multiAgentIcon} />
          )}
          <Text style={styles.timeText}>{lastCallTime}</Text>
          <MaterialIcons name="chevron-right" size={24} color={colors.textTertiary} />
        </View>
      </TouchableOpacity>
    );
  };

  // Sync status bar
  const SyncStatusBar = () => {
    if (!syncStatus) return null;
    return (
      <View style={styles.syncStatusBar}>
        {refreshing && <ActivityIndicator size="small" color="#fff" style={styles.syncSpinner} />}
        <Text style={styles.syncStatusText}>{syncStatus}</Text>
      </View>
    );
  };

  // Footer (empty - test buttons removed)
  const ListFooter = () => null;

  if (loading) {
    return (
      <View style={styles.centerContainer}>
        <ActivityIndicator size="large" color={colors.primary} />
        <Text style={styles.loadingText}>Ładowanie połączeń...</Text>
      </View>
    );
  }

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
              <MaterialIcons name="phone-missed" size={64} color={colors.textTertiary} />
              <Text style={styles.emptyText}>Brak połączeń</Text>
              <Text style={styles.emptySubtext}>
                Nieodebrane połączenia pojawią się tutaj
              </Text>
              <Text style={styles.pullHint}>↓ Pociągnij w dół aby odświeżyć</Text>
            </View>
          }
        />
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <SyncStatusBar />
      <FlatList
        data={groupedLogs}
        renderItem={renderRow}
        keyExtractor={(item) => item.groupKey}
        contentContainerStyle={styles.listContent}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
        }
        ListFooterComponent={<ListFooter />}
        ItemSeparatorComponent={() => <View style={styles.separator} />}
      />
    </View>
  );
};

// Dynamic styles generator
const createStyles = (colors: ReturnType<typeof import('@/contexts/ThemeContext').useTheme>['colors']) =>
  StyleSheet.create({
    // Container
    container: {
      flex: 1,
      backgroundColor: colors.background,
    },

    // Header
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

    // Layout
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
      paddingVertical: spacing.sm,
    },

    // Row - Minimalist call log style
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
    phoneText: {
      fontSize: typography.lg,
      fontWeight: typography.semibold,
      color: colors.textPrimary,
      marginBottom: 2,
    },
    subtitleText: {
      fontSize: typography.sm,
      color: colors.textSecondary,
    },
    handlerText: {
      fontSize: typography.xs,
      color: colors.primary,
      marginTop: 2,
    },
    recipientLabel: {
      fontSize: typography.xs,
      color: colors.textSecondary,
      marginTop: 2,
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
    multiAgentIcon: {
      marginRight: spacing.xs,
    },
    separator: {
      height: 1,
      backgroundColor: colors.borderLight,
      marginLeft: 72,
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
      alignItems: 'center',
      paddingVertical: spacing.xxxl,
      paddingHorizontal: spacing.xl,
    },
    emptyText: {
      fontSize: typography.lg,
      fontWeight: typography.semibold,
      color: colors.textPrimary,
      marginTop: spacing.lg,
      marginBottom: spacing.sm,
    },
    emptySubtext: {
      fontSize: typography.base,
      color: colors.textSecondary,
      textAlign: 'center',
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
    },
    fullRescanButton: {
      backgroundColor: colors.infoLight,
      padding: spacing.lg,
      borderRadius: radius.lg,
      alignItems: 'center',
      borderWidth: 1,
      borderColor: colors.info,
      marginBottom: spacing.md,
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
  });

