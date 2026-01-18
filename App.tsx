/**
 * TeamTalk - Main App Component
 *
 * Features:
 * - Bottom Tab Navigation
 * - CallLog Scanner integration
 * - Notification handling
 * - Realtime sync
 */

import React, { useEffect } from 'react';
import { StatusBar } from 'expo-status-bar';
import { NavigationContainer } from '@react-navigation/native';
import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';
import { AppNavigator } from './src/navigation/AppNavigator';
import { callLogScanner } from './src/services/CallLogScanner';

// Configure notifications
Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowAlert: true,
    shouldPlaySound: true,
    shouldSetBadge: true,
  }),
});

export default function App() {
  useEffect(() => {
    // Initialize app
    initializeApp();
  }, []);

  const initializeApp = async () => {
    try {
      // Request permissions
      const hasPermissions = await callLogScanner.requestPermissions();

      if (hasPermissions) {
        console.log('✅ Permissions granted');

        // Start periodic call log scanning (every 5 minutes)
        callLogScanner.startPeriodicScanning(5);
      } else {
        console.warn('⚠️ Permissions not granted - call monitoring disabled');
      }

      // Setup notification tap handler
      setupNotificationHandler();
    } catch (error) {
      console.error('Error initializing app:', error);
    }
  };

  const setupNotificationHandler = () => {
    // Handle notification tap
    Notifications.addNotificationResponseReceivedListener((response) => {
      const data = response.notification.request.content.data;

      if (data.type === 'missed_call' && data.clientId) {
        // TODO: Navigate to CallLogs tab and highlight the specific call
        console.log('Notification tapped for client:', data.clientId);
      }
    });
  };

  return (
    <NavigationContainer>
      <AppNavigator />
      <StatusBar style="light" />
    </NavigationContainer>
  );
}
