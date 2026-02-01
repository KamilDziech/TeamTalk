/**
 * usePushNotifications Hook
 *
 * Manages push notification state and permissions.
 * Provides toggle functionality for enabling/disabling notifications.
 */

import { useState, useEffect, useCallback } from 'react';
import * as Notifications from 'expo-notifications';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Platform } from 'react-native';

const NOTIFICATIONS_ENABLED_KEY = '@push_notifications_enabled';

interface UsePushNotificationsReturn {
    notificationsEnabled: boolean;
    toggleNotifications: () => Promise<boolean | null>;
    requestPermissions: () => Promise<boolean>;
}

export const usePushNotifications = (): UsePushNotificationsReturn => {
    const [notificationsEnabled, setNotificationsEnabled] = useState(true);

    // Load saved state on mount
    useEffect(() => {
        loadNotificationState();
    }, []);

    const loadNotificationState = async () => {
        try {
            const stored = await AsyncStorage.getItem(NOTIFICATIONS_ENABLED_KEY);
            if (stored !== null) {
                setNotificationsEnabled(stored === 'true');
            }
        } catch (error) {
            console.error('Error loading notification state:', error);
        }
    };

    const saveNotificationState = async (enabled: boolean) => {
        try {
            await AsyncStorage.setItem(NOTIFICATIONS_ENABLED_KEY, enabled.toString());
        } catch (error) {
            console.error('Error saving notification state:', error);
        }
    };

    const requestPermissions = useCallback(async (): Promise<boolean> => {
        try {
            const { status: existingStatus } = await Notifications.getPermissionsAsync();
            let finalStatus = existingStatus;

            if (existingStatus !== 'granted') {
                const { status } = await Notifications.requestPermissionsAsync();
                finalStatus = status;
            }

            if (finalStatus !== 'granted') {
                console.log('Push notification permissions not granted');
                return false;
            }

            // Configure for Android
            if (Platform.OS === 'android') {
                await Notifications.setNotificationChannelAsync('default', {
                    name: 'default',
                    importance: Notifications.AndroidImportance.MAX,
                    vibrationPattern: [0, 250, 250, 250],
                    lightColor: '#007AFF',
                });
            }

            return true;
        } catch (error) {
            console.error('Error requesting notification permissions:', error);
            return false;
        }
    }, []);

    const toggleNotifications = useCallback(async (): Promise<boolean | null> => {
        try {
            const newState = !notificationsEnabled;

            if (newState) {
                // Enabling - request permissions if needed
                const granted = await requestPermissions();
                if (!granted) {
                    return null; // Permissions denied
                }
            }

            setNotificationsEnabled(newState);
            await saveNotificationState(newState);

            return newState;
        } catch (error) {
            console.error('Error toggling notifications:', error);
            return null;
        }
    }, [notificationsEnabled, requestPermissions]);

    return {
        notificationsEnabled,
        toggleNotifications,
        requestPermissions,
    };
};
