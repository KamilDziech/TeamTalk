/**
 * SettingsButton
 *
 * Shared settings button component for navigation headers.
 * Opens a modal with theme selection, push notification toggle, and logout option.
 */

import React, { useState } from 'react';
import {
    View,
    Text,
    TouchableOpacity,
    Alert,
    StyleSheet,
    Modal,
    Switch,
} from 'react-native';
import { MaterialIcons } from '@expo/vector-icons';
import { useAuth } from '@/contexts/AuthContext';
import { useTheme, ThemeMode } from '@/contexts/ThemeContext';
import { usePushNotifications } from '@/hooks/usePushNotifications';
import { spacing, radius, typography } from '@/styles/theme';

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
});
