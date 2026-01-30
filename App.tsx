/**
 * TeamTalk - Main App Component
 *
 * Features:
 * - User authentication with session persistence
 * - Bottom Tab Navigation
 * - CallLog Scanner integration
 * - Notification handling (local notifications work in Expo Go, push requires dev build)
 * - Realtime sync
 */

import React, { useEffect, useRef, useState } from 'react';
import { StatusBar } from 'expo-status-bar';
import { NavigationContainer } from '@react-navigation/native';
import * as Notifications from 'expo-notifications';
import { AppState, AppStateStatus, Alert, View, Text, StyleSheet, Platform, Linking, ActivityIndicator } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { AppNavigator } from './src/navigation/AppNavigator';
import { callLogScanner } from './src/services/CallLogScanner';
import { deviceService } from './src/services/DeviceService';
import { AuthProvider, useAuth } from './src/contexts/AuthContext';
import { AuthScreen } from './src/screens/AuthScreen';

const BATTERY_OPTIMIZATION_SHOWN_KEY = 'battery_optimization_shown';

// Configure notifications
Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowAlert: true,
    shouldPlaySound: true,
    shouldSetBadge: true,
    shouldShowBanner: true,
    shouldShowList: true,
  }),
});

/**
 * Main App Content - handles authenticated app flow
 */
const AppContent: React.FC = () => {
  const { session, profile, loading } = useAuth();
  const appState = useRef(AppState.currentState);

  useEffect(() => {
    if (session) {
      // Initialize app when authenticated
      initializeApp();
    }

    // Setup AppState listener
    const subscription = AppState.addEventListener('change', handleAppStateChange);

    return () => {
      subscription.remove();
    };
  }, [session]);

  const handleAppStateChange = (nextAppState: AppStateStatus) => {
    if (appState.current.match(/inactive|background/) && nextAppState === 'active') {
      console.log('📱 App returned to foreground - scanning call log...');
      callLogScanner.scanMissedCalls();
    }
    appState.current = nextAppState;
  };

  const initializeApp = async () => {
    try {
      const hasPermissions = await callLogScanner.requestPermissions();

      if (hasPermissions) {
        console.log('✅ Permissions granted');
        callLogScanner.startPeriodicScanning(1);
      } else {
        console.warn('⚠️ Permissions not granted - call monitoring disabled');
      }

      setupNotificationHandler();
      await initializeDeviceService();
      checkBatteryOptimization();
    } catch (error) {
      console.error('Error initializing app:', error);
    }
  };

  const initializeDeviceService = async () => {
    try {
      // Use display name from profile if available
      const displayName = profile?.display_name || 'Użytkownik';
      await deviceService.initialize(displayName);
    } catch (error) {
      console.error('Error initializing device service:', error);
    }
  };

  const checkBatteryOptimization = async () => {
    if (Platform.OS !== 'android') return;

    try {
      const shown = await AsyncStorage.getItem(BATTERY_OPTIMIZATION_SHOWN_KEY);
      if (shown) return;

      await AsyncStorage.setItem(BATTERY_OPTIMIZATION_SHOWN_KEY, 'true');

      setTimeout(() => {
        Alert.alert(
          'Optymalizacja baterii',
          'Dla niezawodnego działania powiadomień i synchronizacji w tle, zalecamy wyłączenie optymalizacji baterii dla TeamTalk.\n\nUstawienia → Aplikacje → TeamTalk → Bateria → Nieograniczone',
          [
            { text: 'Przypomnij później', style: 'cancel' },
            {
              text: 'Otwórz ustawienia',
              onPress: () => Linking.openSettings(),
            },
          ]
        );
      }, 3000);
    } catch (error) {
      console.error('Error checking battery optimization:', error);
    }
  };

  const setupNotificationHandler = () => {
    Notifications.addNotificationResponseReceivedListener((response) => {
      const data = response.notification.request.content.data;

      if (data.type === 'missed_call' && data.clientId) {
        console.log('Notification tapped for client:', data.clientId);
      }
    });
  };

  // Show loading screen while checking auth state
  if (loading) {
    console.log('🔄 AppContent: Still loading...');
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color="#007AFF" />
        <Text style={styles.loadingText}>Ładowanie...</Text>
      </View>
    );
  }

  // Show auth screen if not authenticated
  if (!session) {
    return <AuthScreen onAuthSuccess={() => {}} />;
  }

  // Show main app when authenticated
  return (
    <NavigationContainer>
      <AppNavigator />
      <StatusBar style="light" />
    </NavigationContainer>
  );
};

/**
 * Root App Component with AuthProvider
 */
export default function App() {
  return (
    <AuthProvider>
      <AppContent />
    </AuthProvider>
  );
}

const styles = StyleSheet.create({
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#f5f5f5',
  },
  loadingText: {
    marginTop: 12,
    fontSize: 16,
    color: '#666',
  },
});
