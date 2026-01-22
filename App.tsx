/**
 * TeamTalk - Main App Component
 *
 * Features:
 * - Bottom Tab Navigation
 * - CallLog Scanner integration
 * - Notification handling
 * - Realtime sync
 */

import React, { useEffect, useRef } from 'react';
import { StatusBar } from 'expo-status-bar';
import { NavigationContainer } from '@react-navigation/native';
import * as Notifications from 'expo-notifications';
import { AppState, AppStateStatus } from 'react-native';
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
  const appState = useRef(AppState.currentState);

  useEffect(() => {
    // Initialize app
    initializeApp();

    // Setup AppState listener - skanuj przy powrocie z tła
    const subscription = AppState.addEventListener('change', handleAppStateChange);

    return () => {
      subscription.remove();
    };
  }, []);

  const handleAppStateChange = (nextAppState: AppStateStatus) => {
    // Gdy aplikacja wraca z tła na pierwszy plan - skanuj połączenia
    if (appState.current.match(/inactive|background/) && nextAppState === 'active') {
      console.log('📱 App returned to foreground - scanning call log...');
      callLogScanner.scanMissedCalls();
    }
    appState.current = nextAppState;
  };

  const initializeApp = async () => {
    try {
      // Request permissions
      const hasPermissions = await callLogScanner.requestPermissions();

      if (hasPermissions) {
        console.log('✅ Permissions granted');

        // Start periodic call log scanning (every 1 minute)
        callLogScanner.startPeriodicScanning(1);
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
