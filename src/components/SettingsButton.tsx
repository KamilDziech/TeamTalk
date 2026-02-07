/**
 * SettingsButton
 *
 * Shared settings button component for navigation headers.
 * Opens a modal with theme selection, Dual SIM configuration,
 * push notification toggle, and logout option.
 */

import React, { useState, useEffect } from 'react';
import {
    View,
    Text,
    TouchableOpacity,
    Alert,
    StyleSheet,
    Modal,
    Switch,
    ActivityIndicator,
    ScrollView,
    Platform,
} from 'react-native';
import { MaterialIcons } from '@expo/vector-icons';
import { useAuth } from '@/contexts/AuthContext';
import { useTheme, ThemeMode } from '@/contexts/ThemeContext';
import { usePushNotifications } from '@/hooks/usePushNotifications';
import { spacing, radius, typography } from '@/styles/theme';
import { simDetectionService } from '@/services/SimDetectionService';

// Theme option interface
interface ThemeOption {
    mode: ThemeMode;
    label: string;
    icon: string;
}

const THEME_OPTIONS: ThemeOption[] = [
    { mode: 'system', label: 'Systemowy', icon: '🖥️' },
    { mode: 'light', label: 'Jasny', icon: '☀️' },
    { mode: 'dark', label: 'Ciemny', icon: '🌙' },
];

export const SettingsButton: React.FC = () => {
    const { signOut, profile } = useAuth();
    const { colors, mode, setMode, isDark } = useTheme();
    const { notificationsEnabled, toggleNotifications } = usePushNotifications();
    const [modalVisible, setModalVisible] = useState(false);

    // Dual SIM state
    const [detectedSims, setDetectedSims] = useState<{ id: string; displayName: string }[]>([]);
    const [selectedSimId, setSelectedSimId] = useState<string | null>(null);
    const [isLoadingSims, setIsLoadingSims] = useState(false);
    const [showDualSimSection, setShowDualSimSection] = useState(false);

    // Load SIM info when modal opens
    useEffect(() => {
        if (modalVisible && Platform.OS === 'android') {
            loadSimInfo();
        }
    }, [modalVisible]);

    const loadSimInfo = async () => {
        setIsLoadingSims(true);
        try {
            const sims = await simDetectionService.getDetectedSims();
            const businessSimId = await simDetectionService.getBusinessSimId();
            setDetectedSims(sims);
            setSelectedSimId(businessSimId);
            setShowDualSimSection(sims.length > 1);
        } catch (error) {
            console.error('Error loading SIM info:', error);
        } finally {
            setIsLoadingSims(false);
        }
    };

    const handleSimSelect = async (simId: string) => {
        await simDetectionService.setBusinessSimId(simId);
        setSelectedSimId(simId);
    };

    const handleResetSimSelection = async () => {
        Alert.alert(
            'Resetuj wybór SIM',
            'Czy na pewno chcesz zresetować wybór karty służbowej? Przy następnej synchronizacji zostaniesz poproszony o ponowny wybór.',
            [
                { text: 'Anuluj', style: 'cancel' },
                {
                    text: 'Resetuj',
                    style: 'destructive',
                    onPress: async () => {
                        await simDetectionService.resetSimSelection();
                        setSelectedSimId(null);
                        Alert.alert('Gotowe', 'Wybór karty SIM został zresetowany.');
                    },
                },
            ]
        );
    };

    const handleLogout = () => {
        setModalVisible(false);
        Alert.alert(
            'Wylogowanie',
            `Czy na pewno chcesz się wylogować${profile ? `, ${profile.display_name}` : ''}?`,
            [
                { text: 'Anuluj', style: 'cancel' },
                {
                    text: 'Wyloguj',
                    style: 'destructive',
                    onPress: async () => {
                        try {
                            await signOut();
                        } catch (error) {
                            Alert.alert('Błąd', 'Nie udało się wylogować. Spróbuj ponownie.');
                        }
                    },
                },
            ]
        );
    };

    const handleToggleNotifications = async () => {
        const newState = await toggleNotifications();
        if (newState !== null) {
            console.log('📱 Push notifications:', newState ? 'enabled' : 'disabled');
        }
    };

    const handleThemeChange = (newMode: ThemeMode) => {
        setMode(newMode);
    };

    return (
        <>
            {/* Gear Icon */}
            <TouchableOpacity
                onPress={() => setModalVisible(true)}
                style={styles.settingsButton}
            >
                <MaterialIcons
                    name="settings"
                    size={24}
                    color={isDark ? colors.textSecondary : colors.textSecondary}
                />
            </TouchableOpacity>

            {/* Settings Modal */}
            <Modal
                visible={modalVisible}
                transparent
                animationType="fade"
                onRequestClose={() => setModalVisible(false)}
            >
                <TouchableOpacity
                    style={styles.modalOverlay}
                    activeOpacity={1}
                    onPress={() => setModalVisible(false)}
                >
                    <View style={[styles.modalContent, { backgroundColor: colors.surface }]}>
                        {/* Header */}
                        <View style={styles.modalHeader}>
                            <Text style={[styles.modalTitle, { color: colors.textPrimary }]}>
                                Ustawienia
                            </Text>
                            {profile && (
                                <Text style={[styles.modalSubtitle, { color: colors.textTertiary }]}>
                                    {profile.display_name}
                                </Text>
                            )}
                        </View>

                        {/* Theme Section */}
                        <View style={styles.sectionContainer}>
                            <Text style={[styles.sectionTitle, { color: colors.textSecondary }]}>
                                Motyw Aplikacji
                            </Text>
                            <View style={styles.themeOptionsContainer}>
                                {THEME_OPTIONS.map((option) => (
                                    <TouchableOpacity
                                        key={option.mode}
                                        style={[
                                            styles.themeOption,
                                            {
                                                backgroundColor: mode === option.mode
                                                    ? colors.primaryLight
                                                    : colors.background,
                                                borderColor: mode === option.mode
                                                    ? colors.primary
                                                    : colors.border,
                                            },
                                        ]}
                                        onPress={() => handleThemeChange(option.mode)}
                                    >
                                        <Text style={styles.themeIcon}>{option.icon}</Text>
                                        <Text
                                            style={[
                                                styles.themeLabel,
                                                {
                                                    color: mode === option.mode
                                                        ? colors.primary
                                                        : colors.textPrimary,
                                                    fontWeight: mode === option.mode ? '600' : '400',
                                                },
                                            ]}
                                        >
                                            {option.label}
                                        </Text>
                                    </TouchableOpacity>
                                ))}
                            </View>
                        </View>

                        {/* Dual SIM Section - only show if multiple SIMs detected */}
                        {showDualSimSection && (
                            <>
                                {/* Separator */}
                                <View style={[styles.separator, { backgroundColor: colors.border }]} />

                                <View style={styles.sectionContainer}>
                                    <View style={styles.settingsRowLeft}>
                                        <MaterialIcons
                                            name="sim-card"
                                            size={22}
                                            color={colors.textPrimary}
                                        />
                                        <Text style={[styles.sectionTitle, { color: colors.textSecondary, marginLeft: spacing.sm, marginBottom: 0 }]}>
                                            Konfiguracja Dual SIM
                                        </Text>
                                    </View>
                                    <Text style={[styles.simSubtitle, { color: colors.textTertiary }]}>
                                        Wybierz kartę służbową:
                                    </Text>

                                    {isLoadingSims ? (
                                        <ActivityIndicator size="small" color={colors.primary} />
                                    ) : (
                                        <View style={styles.simOptionsContainer}>
                                            {detectedSims.map((sim, index) => (
                                                <TouchableOpacity
                                                    key={sim.id}
                                                    style={[
                                                        styles.simOption,
                                                        {
                                                            backgroundColor: selectedSimId === sim.id
                                                                ? colors.primaryLight
                                                                : colors.background,
                                                            borderColor: selectedSimId === sim.id
                                                                ? colors.primary
                                                                : colors.border,
                                                        },
                                                    ]}
                                                    onPress={() => handleSimSelect(sim.id)}
                                                >
                                                    <MaterialIcons
                                                        name={selectedSimId === sim.id ? 'radio-button-checked' : 'radio-button-unchecked'}
                                                        size={20}
                                                        color={selectedSimId === sim.id ? colors.primary : colors.textTertiary}
                                                    />
                                                    <View style={styles.simOptionTextContainer}>
                                                        <Text
                                                            style={[
                                                                styles.simOptionLabel,
                                                                {
                                                                    color: selectedSimId === sim.id
                                                                        ? colors.primary
                                                                        : colors.textPrimary,
                                                                },
                                                            ]}
                                                        >
                                                            SIM {index + 1}
                                                        </Text>
                                                        <Text
                                                            style={[
                                                                styles.simOptionId,
                                                                { color: colors.textTertiary },
                                                            ]}
                                                            numberOfLines={1}
                                                        >
                                                            ID: {simDetectionService.shortenId(sim.id)}
                                                        </Text>
                                                    </View>
                                                    {selectedSimId === sim.id && (
                                                        <Text style={[styles.simBadge, { backgroundColor: colors.success, color: '#fff' }]}>
                                                            Służbowa
                                                        </Text>
                                                    )}
                                                </TouchableOpacity>
                                            ))}
                                        </View>
                                    )}

                                    {/* Reset SIM selection */}
                                    <TouchableOpacity
                                        style={styles.resetSimButton}
                                        onPress={handleResetSimSelection}
                                    >
                                        <MaterialIcons name="refresh" size={16} color={colors.textSecondary} />
                                        <Text style={[styles.resetSimText, { color: colors.textSecondary }]}>
                                            Resetuj wybór SIM
                                        </Text>
                                    </TouchableOpacity>
                                </View>
                            </>
                        )}

                        {/* Separator */}
                        <View style={[styles.separator, { backgroundColor: colors.border }]} />

                        {/* Push Notifications Toggle */}
                        <View style={styles.settingsRow}>
                            <View style={styles.settingsRowLeft}>
                                <MaterialIcons
                                    name="notifications"
                                    size={22}
                                    color={colors.textPrimary}
                                />
                                <Text style={[styles.settingsRowText, { color: colors.textPrimary }]}>
                                    Powiadomienia Push
                                </Text>
                            </View>
                            <Switch
                                value={notificationsEnabled}
                                onValueChange={handleToggleNotifications}
                                trackColor={{ false: colors.border, true: colors.primaryLight }}
                                thumbColor={notificationsEnabled ? colors.primary : colors.textTertiary}
                            />
                        </View>

                        {/* Separator */}
                        <View style={[styles.separator, { backgroundColor: colors.border }]} />

                        {/* Logout Button */}
                        <TouchableOpacity style={styles.logoutRow} onPress={handleLogout}>
                            <MaterialIcons name="logout" size={22} color={colors.error} />
                            <Text style={[styles.logoutText, { color: colors.error }]}>
                                Wyloguj
                            </Text>
                        </TouchableOpacity>

                        {/* Cancel */}
                        <TouchableOpacity
                            style={styles.cancelButton}
                            onPress={() => setModalVisible(false)}
                        >
                            <Text style={[styles.cancelText, { color: colors.textSecondary }]}>
                                Anuluj
                            </Text>
                        </TouchableOpacity>
                    </View>
                </TouchableOpacity>
            </Modal>
        </>
    );
};

const styles = StyleSheet.create({
    // Settings button (gear icon)
    settingsButton: {
        marginRight: spacing.lg,
        padding: spacing.sm,
    },

    // Modal
    modalOverlay: {
        flex: 1,
        backgroundColor: 'rgba(0, 0, 0, 0.5)',
        justifyContent: 'center',
        alignItems: 'center',
        padding: spacing.lg,
    },
    modalContent: {
        borderRadius: radius.xl,
        width: '100%',
        maxWidth: 320,
        padding: spacing.lg,
    },
    modalHeader: {
        marginBottom: spacing.lg,
        alignItems: 'center',
    },
    modalTitle: {
        fontSize: typography.lg,
        fontWeight: typography.bold,
    },
    modalSubtitle: {
        fontSize: typography.sm,
        marginTop: spacing.xs,
    },

    // Section
    sectionContainer: {
        marginBottom: spacing.md,
    },
    sectionTitle: {
        fontSize: typography.sm,
        fontWeight: typography.medium,
        marginBottom: spacing.sm,
    },
    themeOptionsContainer: {
        flexDirection: 'row',
        justifyContent: 'space-between',
    },
    themeOption: {
        flex: 1,
        alignItems: 'center',
        paddingVertical: spacing.md,
        paddingHorizontal: spacing.sm,
        borderRadius: radius.md,
        borderWidth: 2,
        marginHorizontal: spacing.xs,
    },
    themeIcon: {
        fontSize: 20,
        marginBottom: spacing.xs,
    },
    themeLabel: {
        fontSize: typography.xs,
    },

    // Settings row
    settingsRow: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
        paddingVertical: spacing.md,
    },
    settingsRowLeft: {
        flexDirection: 'row',
        alignItems: 'center',
    },
    settingsRowText: {
        fontSize: typography.base,
        marginLeft: spacing.md,
    },

    // Separator
    separator: {
        height: 1,
        marginVertical: spacing.sm,
    },

    // Logout row
    logoutRow: {
        flexDirection: 'row',
        alignItems: 'center',
        paddingVertical: spacing.md,
    },
    logoutText: {
        fontSize: typography.base,
        marginLeft: spacing.md,
        fontWeight: typography.medium,
    },

    // Cancel button
    cancelButton: {
        alignItems: 'center',
        paddingVertical: spacing.md,
        marginTop: spacing.sm,
    },
    cancelText: {
        fontSize: typography.base,
    },

    // Dual SIM styles
    simSubtitle: {
        fontSize: typography.sm,
        marginTop: spacing.xs,
        marginBottom: spacing.sm,
    },
    simOptionsContainer: {
        marginTop: spacing.xs,
    },
    simOption: {
        flexDirection: 'row',
        alignItems: 'center',
        paddingVertical: spacing.md,
        paddingHorizontal: spacing.md,
        borderRadius: radius.md,
        borderWidth: 2,
        marginBottom: spacing.sm,
    },
    simOptionTextContainer: {
        flex: 1,
        marginLeft: spacing.sm,
    },
    simOptionLabel: {
        fontSize: typography.base,
        fontWeight: typography.medium,
    },
    simOptionId: {
        fontSize: typography.xs,
        marginTop: 2,
    },
    simBadge: {
        fontSize: typography.xs,
        paddingHorizontal: spacing.sm,
        paddingVertical: 2,
        borderRadius: radius.sm,
        overflow: 'hidden',
        fontWeight: typography.medium,
    },
    resetSimButton: {
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'center',
        marginTop: spacing.xs,
        paddingVertical: spacing.sm,
    },
    resetSimText: {
        fontSize: typography.sm,
        marginLeft: spacing.xs,
    },
});
