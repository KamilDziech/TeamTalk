/**
 * SettingsButton
 *
 * Shared settings button component for navigation headers.
 * Opens a modal with push notification toggle and logout option.
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
import { usePushNotifications } from '@/hooks/usePushNotifications';
import { colors, spacing, radius, typography } from '@/styles/theme';

export const SettingsButton: React.FC = () => {
    const { signOut, profile } = useAuth();
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

    return (
        <>
            {/* Gear Icon */}
            <TouchableOpacity
                onPress={() => setModalVisible(true)}
                style={styles.settingsButton}
            >
                <MaterialIcons name="settings" size={24} color={colors.textSecondary} />
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
                    <View style={styles.modalContent}>
                        {/* Header */}
                        <View style={styles.modalHeader}>
                            <Text style={styles.modalTitle}>Ustawienia</Text>
                            {profile && (
                                <Text style={styles.modalSubtitle}>{profile.display_name}</Text>
                            )}
                        </View>

                        {/* Push Notifications Toggle */}
                        <View style={styles.settingsRow}>
                            <View style={styles.settingsRowLeft}>
                                <MaterialIcons name="notifications" size={22} color={colors.textPrimary} />
                                <Text style={styles.settingsRowText}>Powiadomienia Push</Text>
                            </View>
                            <Switch
                                value={notificationsEnabled}
                                onValueChange={handleToggleNotifications}
                                trackColor={{ false: colors.borderLight, true: colors.primaryLight }}
                                thumbColor={notificationsEnabled ? colors.primary : colors.textTertiary}
                            />
                        </View>

                        {/* Separator */}
                        <View style={styles.separator} />

                        {/* Logout Button */}
                        <TouchableOpacity style={styles.logoutRow} onPress={handleLogout}>
                            <MaterialIcons name="logout" size={22} color={colors.error} />
                            <Text style={styles.logoutText}>Wyloguj</Text>
                        </TouchableOpacity>

                        {/* Cancel */}
                        <TouchableOpacity
                            style={styles.cancelButton}
                            onPress={() => setModalVisible(false)}
                        >
                            <Text style={styles.cancelText}>Anuluj</Text>
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
        backgroundColor: 'rgba(0, 0, 0, 0.4)',
        justifyContent: 'center',
        alignItems: 'center',
        padding: spacing.lg,
    },
    modalContent: {
        backgroundColor: colors.surface,
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
        color: colors.textPrimary,
    },
    modalSubtitle: {
        fontSize: typography.sm,
        color: colors.textTertiary,
        marginTop: spacing.xs,
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
        color: colors.textPrimary,
        marginLeft: spacing.md,
    },

    // Separator
    separator: {
        height: 1,
        backgroundColor: colors.borderLight,
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
        color: colors.error,
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
        color: colors.textSecondary,
    },
});
