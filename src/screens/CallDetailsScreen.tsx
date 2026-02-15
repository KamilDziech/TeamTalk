/**
 * CallDetailsScreen
 *
 * Detail screen showing call history accordion and action buttons.
 * Flat Design: No cards, no shadows, clean typography.
 */

import React, { useState, useEffect, useCallback } from 'react';
import {
    View,
    Text,
    StyleSheet,
    TouchableOpacity,
    ScrollView,
    Alert,
    Linking,
    LayoutAnimation,
    UIManager,
    Platform,
    RefreshControl,
} from 'react-native';
import { useNavigation, useRoute, RouteProp } from '@react-navigation/native';
import { MaterialIcons } from '@expo/vector-icons';
import { supabase } from '@/api/supabaseClient';
import { contactLookupService } from '@/services/ContactLookupService';
import { useAuth } from '@/contexts/AuthContext';
import { useTheme } from '@/contexts/ThemeContext';
import { spacing, radius, typography } from '@/styles/theme';
import type { GroupedCallLog } from '@/components/CallLogsList';
import type { CallLogsStackParamList } from '@/navigation/CallLogsStackNavigator';
import type { Profile, CallLogStatus } from '@/types';

// Enable LayoutAnimation for Android
if (Platform.OS === 'android' && UIManager.setLayoutAnimationEnabledExperimental) {
    UIManager.setLayoutAnimationEnabledExperimental(true);
}

// SLA threshold in milliseconds (1 hour)
const SLA_THRESHOLD_MS = 60 * 60 * 1000;

/**
 * Format time elapsed since a given timestamp
 */
const formatTimeElapsed = (timestamp: string): string => {
    const now = new Date().getTime();
    const then = new Date(timestamp).getTime();
    const diffMs = now - then;

    const minutes = Math.floor(diffMs / (1000 * 60));
    const hours = Math.floor(minutes / 60);
    const remainingMinutes = minutes % 60;

    if (hours > 0) {
        return `${hours}h ${remainingMinutes}m temu`;
    }
    return `${minutes}m temu`;
};

/**
 * Check if the oldest active call exceeds SLA threshold
 */
const checkSlaExceeded = (group: GroupedCallLog): { exceeded: boolean; waitTime: string } => {
    const activeCalls = group.allCalls.filter((c) => c.status === 'missed' || c.status === 'reserved');
    if (activeCalls.length === 0) {
        return { exceeded: false, waitTime: '' };
    }

    const sortedCalls = [...activeCalls].sort(
        (a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime()
    );
    const oldestCall = sortedCalls[0];

    const now = new Date().getTime();
    const oldestTime = new Date(oldestCall.timestamp).getTime();
    const diffMs = now - oldestTime;

    if (diffMs > SLA_THRESHOLD_MS) {
        const minutes = Math.floor(diffMs / (1000 * 60));
        const hours = Math.floor(minutes / 60);
        const remainingMinutes = minutes % 60;
        return {
            exceeded: true,
            waitTime: `${hours}h ${remainingMinutes}m`,
        };
    }

    return { exceeded: false, waitTime: '' };
};

type CallDetailsRouteProp = RouteProp<CallLogsStackParamList, 'CallDetails'>;

export const CallDetailsScreen: React.FC = () => {
    const route = useRoute<CallDetailsRouteProp>();
    const navigation = useNavigation();
    const { user } = useAuth();
    const { colors } = useTheme();
    const styles = createStyles(colors);
    const [group, setGroup] = useState<GroupedCallLog>(route.params.group);
    const [profiles, setProfiles] = useState<Map<string, Profile>>(new Map());
    const [isHistoryExpanded, setIsHistoryExpanded] = useState(true);
    const [refreshing, setRefreshing] = useState(false);

    // Fetch profiles for display names
    useEffect(() => {
        fetchProfiles();
    }, []);

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

    // Refresh data from Supabase
    const refreshData = useCallback(async () => {
        setRefreshing(true);
        try {
            const callerPhone = group.callerPhone || group.allCalls[0]?.caller_phone;
            const clientId = group.clientId;

            let query = supabase
                .from('call_logs')
                .select(`*, clients (*)`)
                .in('status', ['missed', 'reserved'])
                .order('timestamp', { ascending: false });

            if (clientId) {
                query = query.eq('client_id', clientId);
            } else if (callerPhone) {
                query = query.eq('caller_phone', callerPhone);
            }

            const { data: logs, error } = await query;

            if (error) throw error;

            if (logs && logs.length > 0) {
                // Fetch all voice reports in one query instead of N queries
                const callLogIds = logs.map((log: any) => log.id);
                const { data: reports } = await supabase
                    .from('voice_reports')
                    .select('call_log_id')
                    .in('call_log_id', callLogIds);

                const reportMap = new Set(reports?.map((r) => r.call_log_id) || []);

                const logsWithReports = logs.map((log: any) => ({
                    ...log,
                    client: log.clients,
                    hasVoiceReport: reportMap.has(log.id),
                }));

                const sortedLogs = logsWithReports.sort(
                    (a: any, b: any) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()
                );

                const priorityOrder: Record<CallLogStatus, number> = { missed: 0, reserved: 1, completed: 2 };
                const latestCall = sortedLogs.reduce((prev: any, curr: any) => {
                    if (priorityOrder[curr.status as CallLogStatus] < priorityOrder[prev.status as CallLogStatus]) return curr;
                    return prev;
                }, sortedLogs[0]);

                const missedCalls = sortedLogs.filter((c: any) => c.status === 'missed');

                setGroup({
                    ...group,
                    latestCall: latestCall,
                    allCalls: sortedLogs,
                    callCount: missedCalls.length > 0 ? missedCalls.length : sortedLogs.length,
                    lastCallTime: sortedLogs[0].timestamp,
                });
            } else {
                navigation.goBack();
            }
        } catch (error) {
            console.error('Error refreshing data:', error);
        } finally {
            setRefreshing(false);
        }
    }, [group, navigation]);

    // Toggle history accordion
    const toggleHistory = () => {
        LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
        setIsHistoryExpanded(!isHistoryExpanded);
    };

    // Reserve all missed calls
    const handleReserve = async () => {
        if (!user) {
            Alert.alert('Błąd', 'Musisz być zalogowany, aby rezerwować połączenia.');
            return;
        }

        const missedCalls = group.allCalls.filter((c) => c.status === 'missed');
        if (missedCalls.length === 0) return;

        const reservationTime = new Date().toISOString();

        // OPTIMISTIC UPDATE - Natychmiast zaktualizuj UI
        const updatedCalls = group.allCalls.map((call) => {
            if (call.status === 'missed') {
                return {
                    ...call,
                    status: 'reserved' as CallLogStatus,
                    reservation_by: user.id,
                    reservation_at: reservationTime,
                };
            }
            return call;
        });

        setGroup({
            ...group,
            latestCall: {
                ...group.latestCall,
                status: 'reserved' as CallLogStatus,
                reservation_by: user.id,
                reservation_at: reservationTime,
            },
            allCalls: updatedCalls,
        });

        // Update database in parallel (background)
        try {
            const updatePromises = missedCalls.map((call) =>
                supabase
                    .from('call_logs')
                    .update({
                        status: 'reserved' as CallLogStatus,
                        reservation_by: user.id,
                        reservation_at: reservationTime,
                    })
                    .eq('id', call.id)
            );

            await Promise.all(updatePromises);

            // Refresh data after updates complete (to sync any changes from other users)
            await refreshData();
        } catch (error) {
            console.error('Error reserving calls:', error);
            // Rollback optimistic update on error
            await refreshData();
        }
    };

    // Call the client
    const handleCall = () => {
        const phoneNumber = group.client?.phone || group.callerPhone;
        if (phoneNumber) {
            const formattedNumber = phoneNumber.startsWith('+') ? phoneNumber : `+48${phoneNumber}`;
            Linking.openURL(`tel:${formattedNumber}`);
        }
    };

    // Release reservation
    const handleRelease = async () => {
        const reservedCalls = group.allCalls.filter((c) => c.status === 'reserved');
        if (reservedCalls.length === 0) return;

        // OPTIMISTIC UPDATE
        const updatedCalls = group.allCalls.map((call) => {
            if (call.status === 'reserved') {
                return {
                    ...call,
                    status: 'missed' as CallLogStatus,
                    reservation_by: null,
                    reservation_at: null,
                };
            }
            return call;
        });

        setGroup({
            ...group,
            latestCall: {
                ...group.latestCall,
                status: 'missed' as CallLogStatus,
                reservation_by: null,
                reservation_at: null,
            },
            allCalls: updatedCalls,
        });

        // Update database in parallel
        try {
            const updatePromises = reservedCalls.map((call) =>
                supabase
                    .from('call_logs')
                    .update({
                        status: 'missed' as CallLogStatus,
                        reservation_by: null,
                        reservation_at: null,
                    })
                    .eq('id', call.id)
            );

            await Promise.all(updatePromises);
            await refreshData();
        } catch (error) {
            console.error('Error releasing calls:', error);
            await refreshData();
        }
    };

    // Mark as complete
    const handleComplete = async () => {
        const reservedCalls = group.allCalls.filter((c) => c.status === 'reserved');
        if (reservedCalls.length === 0) return;

        // Update database first
        try {
            const updatePromises = reservedCalls.map((call) =>
                supabase
                    .from('call_logs')
                    .update({
                        type: 'completed',
                        status: 'completed' as CallLogStatus,
                    })
                    .eq('id', call.id)
            );

            await Promise.all(updatePromises);

            // First go back to clear this screen from the stack
            navigation.goBack();

            // Then navigate to AddNote tab
            const rootNavigation = navigation.getParent();
            if (rootNavigation) {
                rootNavigation.navigate('AddNote');
            }
        } catch (error) {
            console.error('Error completing calls:', error);
            Alert.alert('Błąd', 'Nie udało się oznaczyć jako wykonane. Spróbuj ponownie.');
        }
    };

    // Calculate statuses
    const hasMissedCalls = group.allCalls.some((c) => c.status === 'missed');
    const hasReservedCalls = group.allCalls.some((c) => c.status === 'reserved');
    const missedCount = group.allCalls.filter((c) => c.status === 'missed').length;
    const slaStatus = checkSlaExceeded(group);
    const activeCalls = group.allCalls.filter((c) => c.status === 'missed' || c.status === 'reserved');

    // Check if current user is the owner of the reservation
    const isOwner = group.latestCall.status === 'reserved' &&
                    group.latestCall.reservation_by === user?.id;

    // Display values - Priority: 1. Device contacts, 2. CRM client, 3. Phone number
    const phoneNumber = group.client?.phone || group.callerPhone || null;
    const deviceContactName = contactLookupService.lookupContactName(phoneNumber);
    const crmClientName = group.client?.name || null;

    const hasContactName = !!(deviceContactName || crmClientName);
    const displayPrimary = deviceContactName || crmClientName || phoneNumber || 'Nieznany numer';
    const displaySecondary = hasContactName ? phoneNumber : null;
    const handlerName = group.latestCall.reservation_by && group.latestCall.status === 'reserved'
        ? getDisplayName(group.latestCall.reservation_by)
        : null;

    // Recipients label: "Do: Kamil, Marcin" - shows who received the call
    const recipientNames = group.recipients
        .map((id) => getDisplayName(id))
        .filter(Boolean)
        .join(', ');

    return (
        <ScrollView
            style={styles.container}
            contentContainerStyle={styles.content}
            refreshControl={
                <RefreshControl refreshing={refreshing} onRefresh={refreshData} />
            }
        >
            {/* Clean Header - No Card */}
            <View style={styles.header}>
                <View style={styles.titleRow}>
                    <Text style={styles.primaryTitle}>{displayPrimary}</Text>
                    {group.isMultiAgent && (
                        <MaterialIcons name="warning" size={20} color="#F59E0B" style={styles.multiAgentHeaderIcon} />
                    )}
                </View>

                {/* Secondary info (phone if name is shown) */}
                {displaySecondary && (
                    <Text style={styles.secondaryInfo}>{displaySecondary}</Text>
                )}

                {/* Recipients label - shows who received the call */}
                {recipientNames && (
                    <Text style={styles.recipientsLabel}>Do: {recipientNames}</Text>
                )}

                {/* Multi-agent Alert - Amber text inline */}
                {group.isMultiAgent && (
                    <View style={styles.multiAgentInline}>
                        <MaterialIcons name="warning" size={14} color="#F59E0B" />
                        <Text style={styles.multiAgentText}>Kontaktował się z innymi agentami</Text>
                    </View>
                )}

                {/* SLA Alert - Red text inline */}
                {slaStatus.exceeded && (
                    <View style={styles.slaInline}>
                        <MaterialIcons name="warning" size={16} color={colors.error} />
                        <Text style={styles.slaText}>Czeka: {slaStatus.waitTime}</Text>
                    </View>
                )}

                {handlerName && (
                    <Text style={styles.handlerName}>Obsługuje: {handlerName}</Text>
                )}
                {group.client?.address && (
                    <Text style={styles.address}>📍 {group.client.address}</Text>
                )}
            </View>

            {/* Separator */}
            <View style={styles.separator} />

            {/* History Section - Flat, No Card */}
            {activeCalls.length > 0 && (
                <View style={styles.historySection}>
                    <TouchableOpacity
                        style={styles.historyHeader}
                        onPress={toggleHistory}
                        activeOpacity={0.7}
                    >
                        <View style={styles.historyHeaderLeft}>
                            <MaterialIcons name="history" size={20} color={colors.textSecondary} />
                            <Text style={styles.historyHeaderText}>
                                Historia: {activeCalls.length} {activeCalls.length === 1 ? 'próba' : 'prób'}
                            </Text>
                        </View>
                        <MaterialIcons
                            name={isHistoryExpanded ? 'expand-less' : 'expand-more'}
                            size={24}
                            color={colors.textTertiary}
                        />
                    </TouchableOpacity>

                    {isHistoryExpanded && (
                        <View style={styles.historyList}>
                            {activeCalls.map((call, index) => {
                                const time = new Date(call.timestamp).toLocaleTimeString('pl-PL', {
                                    hour: '2-digit',
                                    minute: '2-digit',
                                });
                                const date = new Date(call.timestamp).toLocaleDateString('pl-PL', {
                                    day: '2-digit',
                                    month: '2-digit',
                                });
                                const elapsed = formatTimeElapsed(call.timestamp);
                                const agent = call.status === 'reserved' && call.reservation_by
                                    ? getDisplayName(call.reservation_by)
                                    : call.employee_id
                                        ? getDisplayName(call.employee_id)
                                        : null;

                                const statusColor = call.status === 'missed' ? colors.error : colors.primary;
                                const isLast = index === activeCalls.length - 1;

                                return (
                                    <View key={call.id} style={[styles.historyEntry, !isLast && styles.historyEntryBorder]}>
                                        <View style={styles.historyEntryLeft}>
                                            <View style={[styles.statusDot, { backgroundColor: statusColor }]} />
                                            <View>
                                                <Text style={styles.historyTime}>{date} {time}</Text>
                                                <Text style={styles.historyElapsed}>{elapsed}</Text>
                                            </View>
                                        </View>
                                        {agent && (
                                            <Text style={styles.historyAgent}>{agent}</Text>
                                        )}
                                    </View>
                                );
                            })}
                        </View>
                    )}
                </View>
            )}

            {/* Separator */}
            <View style={styles.separator} />

            {/* Action Buttons - Flat, Vertical Layout */}
            <View style={styles.actionsSection}>
                {/* Status: missed -> Przycisk [REZERWUJ] */}
                {hasMissedCalls && (
                    <TouchableOpacity
                        style={[styles.button, styles.reserveButton]}
                        onPress={handleReserve}
                    >
                        <MaterialIcons name="bookmark-add" size={20} color={colors.textInverse} />
                        <Text style={styles.buttonText}>
                            Rezerwuj {missedCount > 1 ? `(${missedCount})` : ''}
                        </Text>
                    </TouchableOpacity>
                )}

                {/* Status: reserved -> Show buttons ONLY if current user is the owner */}
                {hasReservedCalls && !hasMissedCalls && (
                    <>
                        {isOwner ? (
                            <>
                                {/* Owner can call and complete */}
                                <TouchableOpacity
                                    style={[styles.button, styles.callButton]}
                                    onPress={handleCall}
                                >
                                    <MaterialIcons name="call" size={20} color={colors.textInverse} />
                                    <Text style={styles.buttonText}>Zadzwoń</Text>
                                </TouchableOpacity>

                                <TouchableOpacity
                                    style={[styles.button, styles.completeButton]}
                                    onPress={handleComplete}
                                >
                                    <MaterialIcons name="check" size={20} color={colors.textInverse} />
                                    <Text style={styles.buttonText}>Wykonane</Text>
                                </TouchableOpacity>

                                <TouchableOpacity
                                    style={styles.buttonOutlined}
                                    onPress={handleRelease}
                                >
                                    <MaterialIcons name="lock-open" size={20} color={colors.textSecondary} />
                                    <Text style={styles.buttonOutlinedText}>Uwolnij</Text>
                                </TouchableOpacity>
                            </>
                        ) : (
                            <>
                                {/* Non-owner sees read-only message */}
                                <View style={styles.reservedByOtherContainer}>
                                    <MaterialIcons name="lock" size={24} color={colors.primary} />
                                    <Text style={styles.reservedByOtherText}>
                                        {handlerName || 'Inny użytkownik'} obsługuje to połączenie
                                    </Text>
                                </View>

                                {/* Non-owner can release if needed (emergency unlock) */}
                                <TouchableOpacity
                                    style={styles.buttonOutlined}
                                    onPress={handleRelease}
                                >
                                    <MaterialIcons name="lock-open" size={20} color={colors.textSecondary} />
                                    <Text style={styles.buttonOutlinedText}>Uwolnij (awaryjnie)</Text>
                                </TouchableOpacity>
                            </>
                        )}
                    </>
                )}
            </View>

            {/* Last call info */}
            <View style={styles.infoSection}>
                <Text style={styles.infoText}>
                    Ostatnio: {new Date(group.lastCallTime).toLocaleString('pl-PL')}
                </Text>
            </View>
        </ScrollView>
    );
};

// Dynamic styles generator
const createStyles = (colors: ReturnType<typeof import('@/contexts/ThemeContext').useTheme>['colors']) =>
    StyleSheet.create({
        container: {
            flex: 1,
            backgroundColor: colors.surface,
        },
        content: {
            paddingHorizontal: spacing.lg,
            paddingTop: spacing.lg,
            paddingBottom: spacing.xxxl,
        },

        // Header
        header: {
            paddingVertical: spacing.md,
        },
        titleRow: {
            flexDirection: 'row',
            alignItems: 'center',
        },
        primaryTitle: {
            fontSize: 28,
            fontWeight: typography.bold,
            color: colors.textPrimary,
            letterSpacing: 0.5,
        },
        multiAgentHeaderIcon: {
            marginLeft: spacing.sm,
        },
        secondaryInfo: {
            fontSize: typography.base,
            color: colors.textSecondary,
            marginTop: spacing.xs,
        },
        multiAgentInline: {
            flexDirection: 'row',
            alignItems: 'center',
            marginTop: spacing.xs,
        },
        multiAgentText: {
            color: '#F59E0B',
            fontSize: typography.sm,
            fontWeight: typography.medium,
            marginLeft: spacing.xs,
        },
        slaInline: {
            flexDirection: 'row',
            alignItems: 'center',
            marginTop: spacing.xs,
        },
        slaText: {
            color: colors.error,
            fontSize: typography.sm,
            fontWeight: typography.semibold,
            marginLeft: spacing.xs,
        },
        recipientsLabel: {
            fontSize: typography.sm,
            color: colors.info,
            marginTop: spacing.xs,
            fontStyle: 'italic',
        },
        handlerName: {
            fontSize: typography.sm,
            color: colors.primary,
            marginTop: spacing.xs,
        },
        address: {
            fontSize: typography.sm,
            color: colors.textTertiary,
            marginTop: spacing.sm,
        },

        // Separator
        separator: {
            height: 1,
            backgroundColor: colors.borderLight,
            marginVertical: spacing.lg,
        },

        // History Section
        historySection: {
            marginBottom: spacing.sm,
            backgroundColor: colors.background,
            borderRadius: radius.lg,
            paddingHorizontal: spacing.md,
        },
        historyHeader: {
            flexDirection: 'row',
            justifyContent: 'space-between',
            alignItems: 'center',
            paddingVertical: spacing.sm,
        },
        historyHeaderLeft: {
            flexDirection: 'row',
            alignItems: 'center',
        },
        historyHeaderText: {
            fontSize: typography.base,
            color: colors.textSecondary,
            fontWeight: typography.medium,
            marginLeft: spacing.sm,
        },
        historyList: {
            marginTop: spacing.sm,
        },
        historyEntry: {
            flexDirection: 'row',
            justifyContent: 'space-between',
            alignItems: 'center',
            paddingVertical: spacing.md,
        },
        historyEntryBorder: {
            borderBottomWidth: 1,
            borderBottomColor: colors.borderLight,
        },
        historyEntryLeft: {
            flexDirection: 'row',
            alignItems: 'center',
        },
        statusDot: {
            width: 10,
            height: 10,
            borderRadius: 5,
            marginRight: spacing.md,
        },
        historyTime: {
            fontSize: typography.sm,
            color: colors.textPrimary,
            fontWeight: typography.medium,
        },
        historyElapsed: {
            fontSize: typography.xs,
            color: colors.textTertiary,
        },
        historyAgent: {
            fontSize: typography.sm,
            color: colors.primary,
        },

        // Actions Section
        actionsSection: {
            marginBottom: spacing.lg,
        },
        reservedByOtherContainer: {
            flexDirection: 'row',
            alignItems: 'center',
            justifyContent: 'center',
            backgroundColor: colors.background,
            paddingVertical: spacing.lg,
            paddingHorizontal: spacing.md,
            borderRadius: radius.lg,
            marginBottom: spacing.md,
            borderWidth: 1,
            borderColor: colors.border,
        },
        reservedByOtherText: {
            fontSize: typography.base,
            color: colors.textPrimary,
            fontWeight: typography.medium,
            marginLeft: spacing.sm,
            textAlign: 'center',
        },
        button: {
            flexDirection: 'row',
            alignItems: 'center',
            justifyContent: 'center',
            paddingVertical: spacing.md,
            borderRadius: radius.lg,
            marginBottom: spacing.sm,
        },
        buttonText: {
            color: colors.textInverse,
            fontSize: typography.base,
            fontWeight: typography.semibold,
            marginLeft: spacing.sm,
        },
        reserveButton: {
            backgroundColor: colors.warning,
        },
        callButton: {
            backgroundColor: colors.primary,
        },
        completeButton: {
            backgroundColor: colors.success,
        },
        buttonOutlined: {
            flexDirection: 'row',
            alignItems: 'center',
            justifyContent: 'center',
            paddingVertical: spacing.md,
            borderRadius: radius.lg,
            borderWidth: 1,
            borderColor: colors.border,
            backgroundColor: 'transparent',
            marginBottom: spacing.sm,
        },
        buttonOutlinedText: {
            color: colors.textSecondary,
            fontSize: typography.base,
            fontWeight: typography.semibold,
            marginLeft: spacing.sm,
        },

        // Info Section
        infoSection: {
            paddingVertical: spacing.md,
            alignItems: 'center',
        },
        infoText: {
            fontSize: typography.sm,
            color: colors.textTertiary,
        },
    });
