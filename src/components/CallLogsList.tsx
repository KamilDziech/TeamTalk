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

import React, { useEffect, useRef, useState } from 'react';
import {
  View,
  Text,
  FlatList,
  StyleSheet,
  TouchableOpacity,
  ActivityIndicator,
  RefreshControl,
  Alert,
  AppState,
  AppStateStatus,
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
 * Formats a timestamp as "Dzisiaj HH:MM", "Wczoraj HH:MM", or "DD.MM HH:MM"
 */
const formatCallDate = (timestamp: string): string => {
  const date = new Date(timestamp);
  const now = new Date();
  const yesterday = new Date(now);
  yesterday.setDate(yesterday.getDate() - 1);

  const time = date.toLocaleTimeString('pl-PL', { hour: '2-digit', minute: '2-digit' });

  if (date.toDateString() === now.toDateString()) return `Dzisiaj ${time}`;
  if (date.toDateString() === yesterday.toDateString()) return `Wczoraj ${time}`;
  return `${date.toLocaleDateString('pl-PL', { day: '2-digit', month: '2-digit' })} ${time}`;
};

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

export const CallLogsList: React.FC = React.memo(() => {
  console.log('📋 CallLogsList: Component rendering START');
  const { user, session } = useAuth();
  // Ref keeping the latest session value accessible inside closures without stale captures
  const sessionRef = useRef(session);
  useEffect(() => { sessionRef.current = session; }, [session]);
  const { colors } = useTheme();
  const styles = createStyles(colors);
  const navigation = useNavigation<NavigationProp>();
  const [groupedLogs, setGroupedLogs] = useState<GroupedCallLog[]>([]);
  const [profiles, setProfiles] = useState<Map<string, Profile>>(new Map());
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [syncStatus, setSyncStatus] = useState<string | null>(null);
  // True when all fetch attempts failed (network/server error), false = genuinely empty
  const [connectionError, setConnectionError] = useState(false);

  // Concurrency guard: prevents multiple simultaneous fetchCallLogs() calls
  const isFetchingRef = useRef(false);
  // Tracks whether call logs were loaded at least once (used for cold-start retry)
  const callLogsLoadedRef = useRef(false);
  // Debounce timer for realtime subscription events
  const realtimeDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  // Track app state to refresh data when returning from background
  const appStateRef = useRef<AppStateStatus>(AppState.currentState);

  useEffect(() => {
    console.log('📋 CallLogsList: useEffect running');
    initializeData();
    const cleanup = setupRealtimeSubscription();

    // Refresh call logs when app returns from background (Doze mode kills WebSocket)
    const appStateSubscription = AppState.addEventListener('change', (nextAppState) => {
      console.log(`📋 AppState: ${appStateRef.current} → ${nextAppState} (isFetchingRef=${isFetchingRef.current})`);
      if (
        appStateRef.current.match(/inactive|background/) &&
        nextAppState === 'active'
      ) {
        console.log('📋 CallLogsList: app returned to foreground, refreshing...');
        fetchCallLogs(false, 'AppState');
      }
      appStateRef.current = nextAppState;
    });

    return () => {
      if (realtimeDebounceRef.current) {
        clearTimeout(realtimeDebounceRef.current);
      }
      appStateSubscription.remove();
      cleanup();
    };
  }, []);

  // Load contacts in parallel with data fetch (don't block on permission dialog)
  const initializeData = async () => {
    const t0 = Date.now();
    console.log('📋 initializeData: START');
    try {
      setLoading(true);

      // Fire contacts loading in background - permission dialog must not block queue fetch
      contactLookupService.loadDeviceContacts()
        .then((loaded) => console.log('📱 Device contacts loaded:', loaded))
        .catch((contactsError) => console.error('📱 Error loading contacts, non-fatal:', contactsError));

      // First attempt: 20s timeout. Supabase free tier REST (PostgREST) can take 10–20s
      // to wake from cold state after idle — the original 3s was too aggressive and caused
      // premature retries that then also timed out. Auth (GoTrue) is always fast (<1s),
      // but REST lives on a separate server that can pause after ~5 min of inactivity.
      // Connection: close in customFetch prevents stale OkHttp connections, so we no longer
      // need a short first-attempt timeout to detect them.
      console.log('📋 initializeData: fetchProfiles + fetchCallLogs — pierwsza próba (timeout 20s)...');
      await Promise.allSettled([fetchProfiles(20000), fetchCallLogs(false, 'initializeData', 20000)]);
      // Snapshot immediately after first attempt — before any 2s delay during which
      // a realtime event could flip callLogsLoadedRef to true concurrently.
      const loadedAfterFirstAttempt = callLogsLoadedRef.current;
      console.log(`📋 initializeData: pierwsza próba zakończona (${Date.now() - t0}ms), dane: ${loadedAfterFirstAttempt ? 'OK' : 'BRAK'}`);

      // Retry: if first 20s attempt failed (e.g. Supabase took >20s to warm up or
      // AppState listener set isFetchingRef=true mid-flight), try once more with 30s.
      if (!loadedAfterFirstAttempt) {
        console.log('📋 initializeData: brak danych — retry za 2s...');
        await new Promise((r) => setTimeout(r, 2000));
        // Force-reset the concurrency guard before retry: the AppState listener
        // (triggered by dismissing the permissions dialog) may have set isFetchingRef=true
        // during the 2s wait, which would block fetchCallLogs() silently.
        console.log(`📋 initializeData: przed retry — isFetchingRef=${isFetchingRef.current}, resetuję do false`);
        isFetchingRef.current = false;
        console.log('📋 initializeData: retry START (timeout 30s)');
        await Promise.allSettled([fetchProfiles(20000), fetchCallLogs(false, 'initializeData-retry', 30000)]);
        console.log(`📋 initializeData: retry zakończony (${Date.now() - t0}ms), dane: ${callLogsLoadedRef.current ? 'OK' : 'NADAL BRAK'}`);
      }

    } catch (error) {
      console.error('📋 initializeData: BŁĄD KRYTYCZNY:', error);
    } finally {
      console.log(`📋 initializeData: END (${Date.now() - t0}ms)`);
      if (!callLogsLoadedRef.current) {
        setConnectionError(true);
      }
      setLoading(false);
    }
  };

  const fetchProfiles = async (timeoutMs = 20000) => {
    const t0 = Date.now();
    // Log auth session state from React context (synchronous — never hangs).
    {
      const sess = sessionRef.current;
      if (sess) {
        const expiresInMin = sess.expires_at
          ? Math.round((sess.expires_at * 1000 - Date.now()) / 60000)
          : null;
        console.log(`👤 fetchProfiles: AUTH — sesja istnieje | ${expiresInMin !== null ? (expiresInMin > 0 ? `ważny (za ${expiresInMin} min)` : `⚠️ WYGASŁY`) : 'brak expires_at'}`);
      } else {
        console.warn(`👤 fetchProfiles: AUTH — ⚠️ BRAK SESJI (context)!`);
      }
    }
    console.log(`👤 fetchProfiles: START (timeout=${timeoutMs}ms)`);
    const controller = new AbortController();
    let timeoutId: ReturnType<typeof setTimeout> | undefined;
    try {
      const { data, error } = await Promise.race([
        supabase.from('profiles').select('*').abortSignal(controller.signal).then((r) => r),
        new Promise<never>((_, reject) => {
          timeoutId = setTimeout(() => {
            console.warn(`👤 fetchProfiles: timeout po ${timeoutMs / 1000}s`);
            controller.abort();
            reject(new Error('ProfilesTimeout'));
          }, timeoutMs);
        }),
      ]);
      if (error) {
        console.error(`👤 fetchProfiles: błąd Supabase (${Date.now() - t0}ms):`, error);
        return;
      }
      const profileMap = new Map<string, Profile>();
      data?.forEach((profile: Profile) => profileMap.set(profile.id, profile));
      console.log(`👤 fetchProfiles: OK — ${profileMap.size} profili (${Date.now() - t0}ms)`);
      setProfiles(profileMap);
    } catch (error) {
      console.error(`👤 fetchProfiles: BŁĄD (${Date.now() - t0}ms):`, error);
    } finally {
      clearTimeout(timeoutId);
      controller.abort();
    }
  };

  const getDisplayName = (userId: string | null): string | null => {
    if (!userId) return null;
    const profile = profiles.get(userId);
    return profile?.display_name || null;
  };

  // showLoading=true only for initial load; realtime/background calls pass false
  const fetchCallLogs = async (showLoading = false, caller = 'unknown', timeoutMs = 30000) => {
    // Concurrency guard: skip if a fetch is already in progress
    if (isFetchingRef.current) {
      console.log(`📋 fetchCallLogs: POMINIĘTY — caller=${caller}, isFetchingRef=true`);
      return;
    }
    isFetchingRef.current = true;
    const t0 = Date.now();

    // Log auth session state from React context (synchronous — never hangs).
    // Do NOT use supabase.auth.getSession() here: when called while a token refresh
    // is still in-flight internally, it deadlocks and isFetchingRef never resets.
    {
      const sess = sessionRef.current;
      if (sess) {
        const expiresInMin = sess.expires_at
          ? Math.round((sess.expires_at * 1000 - Date.now()) / 60000)
          : null;
        const tokenStatus = expiresInMin !== null
          ? (expiresInMin > 0 ? `ważny (za ${expiresInMin} min)` : `⚠️ WYGASŁY (${Math.abs(expiresInMin)} min temu)`)
          : 'brak expires_at';
        console.log(`📋 fetchCallLogs: AUTH — sesja istnieje | ${tokenStatus}`);
      } else {
        console.warn(`📋 fetchCallLogs: AUTH — ⚠️ BRAK SESJI (context)! caller=${caller}`);
      }
    }

    console.log(`📋 fetchCallLogs: START — caller=${caller}, showLoading=${showLoading}, timeout=${timeoutMs}ms`);

    const controller = new AbortController();
    let timeoutId: ReturnType<typeof setTimeout> | undefined;
    // Periodic checkpoints: log progress every 5s so we know the query is still running
    const checkpointIntervals = [5000, 10000, 15000, 20000, 25000].filter((ms) => ms < timeoutMs);
    const checkpoints = checkpointIntervals.map((ms) =>
      setTimeout(() => {
        console.warn(`📋 fetchCallLogs: wciąż czeka... (${ms / 1000}s, caller=${caller})`);
      }, ms)
    );

    try {
      if (showLoading) setLoading(true);

      // Promise.race guarantees the await resolves/rejects within the timeout
      // even if AbortController doesn't cause the Supabase promise to throw.
      const { data: allLogs, error } = await Promise.race([
        supabase
          .from('call_logs')
          .select(`*, clients (*)`)
          .in('status', ['missed', 'reserved'])
          .order('timestamp', { ascending: false })
          .limit(200)
          .abortSignal(controller.signal)
          .then((r) => r),
        new Promise<never>((_, reject) => {
          timeoutId = setTimeout(() => {
            console.warn(`📋 fetchCallLogs: TIMEOUT po ${timeoutMs / 1000}s (caller=${caller})`);
            controller.abort();
            reject(new Error('QueryTimeout'));
          }, timeoutMs);
        }),
      ]);

      if (error) {
        console.error(`📋 fetchCallLogs: błąd Supabase (${Date.now() - t0}ms, caller=${caller}):`, error.message, error.code);
        throw error;
      }

      const queueLogs = (allLogs || []).map((log: any) => ({
        ...log,
        client: log.clients,
        hasVoiceReport: false,
      })) as CallLogWithClient[];

      const grouped = groupCallLogsByClient(queueLogs);
      console.log(`📋 fetchCallLogs: OK — ${grouped.length} grup, ${queueLogs.length} logów (${Date.now() - t0}ms, caller=${caller})`);
      callLogsLoadedRef.current = true;
      setConnectionError(false);
      setGroupedLogs(grouped);
    } catch (error: any) {
      const isAbort = error?.name === 'AbortError' || error?.message?.includes('aborted') || error?.message === 'QueryTimeout';
      if (isAbort) {
        console.error(`📋 fetchCallLogs: TIMEOUT/ABORT (${Date.now() - t0}ms, caller=${caller})`);
      } else {
        console.error(`📋 fetchCallLogs: BŁĄD (${Date.now() - t0}ms, caller=${caller}):`, error?.message ?? error);
      }
    } finally {
      checkpoints.forEach(clearTimeout);
      clearTimeout(timeoutId);
      controller.abort();
      isFetchingRef.current = false;
      console.log(`📋 fetchCallLogs: END — isFetchingRef reset (${Date.now() - t0}ms, caller=${caller})`);
      if (showLoading) setLoading(false);
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
        (payload) => {
          // Debounce: if multiple DB events fire in quick succession (e.g. during a scan),
          // wait 500ms after the last event before fetching. Never show loading spinner.
          console.log(`📋 realtime: zdarzenie DB (${payload.eventType}) → debounce 500ms`);
          if (realtimeDebounceRef.current) {
            clearTimeout(realtimeDebounceRef.current);
          }
          realtimeDebounceRef.current = setTimeout(() => {
            fetchCallLogs(false, 'realtime');
          }, 500);
        }
      )
      .subscribe((status, err) => {
        if (err) {
          console.error('📋 realtime: błąd subskrypcji —', status, err.message);
        } else {
          console.log('📋 realtime: status kanału —', status);
        }
      });

    return () => {
      console.log('📋 realtime: odpinanie kanału');
      supabase.removeChannel(channel);
    };
  };

  const onRefresh = async () => {
    setRefreshing(true);
    setSyncStatus('Synchronizacja połączeń...');

    try {
      console.log('🔄 Manual refresh - scanning call log...');
      await callLogScanner.scanMissedCalls();
      await fetchCallLogs(false, 'onRefresh');
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
    setSyncStatus('Pełne skanowanie (2 dni)...');

    try {
      await callLogScanner.fullRescan();
      await fetchCallLogs(false, 'fullRescan');
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
              // Reset concurrency guard so the forced fetch after delete can run
              isFetchingRef.current = false;
              fetchCallLogs(true, 'clearQueue');
            } catch (error) {
              console.error('Error clearing queue:', error);
              setLoading(false);
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

    // Time of last call with relative date label
    const lastCallTime = formatCallDate(item.lastCallTime);

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
              {connectionError ? (
                <>
                  <MaterialIcons name="wifi-off" size={64} color={colors.textTertiary} />
                  <Text style={styles.emptyText}>Brak połączenia</Text>
                  <Text style={styles.emptySubtext}>
                    Nie udało się połączyć z serwerem. Sprawdź internet i odśwież.
                  </Text>
                </>
              ) : (
                <>
                  <MaterialIcons name="phone-missed" size={64} color={colors.textTertiary} />
                  <Text style={styles.emptyText}>Brak połączeń</Text>
                  <Text style={styles.emptySubtext}>
                    Nieodebrane połączenia pojawią się tutaj
                  </Text>
                </>
              )}
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
});

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

