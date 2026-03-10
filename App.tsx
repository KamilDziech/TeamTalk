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
import { SafeAreaProvider } from 'react-native-safe-area-context';
import * as Notifications from 'expo-notifications';
import { AppState, AppStateStatus, Alert, View, Text, StyleSheet, Platform, Linking, ActivityIndicator } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { AppNavigator } from './src/navigation/AppNavigator';
import { callLogScanner } from './src/services/CallLogScanner';
import { deviceService } from './src/services/DeviceService';
import { AuthProvider, useAuth } from './src/contexts/AuthContext';
import { ThemeProvider, useTheme } from './src/contexts/ThemeContext';
import { AuthScreen } from './src/screens/AuthScreen';
import { supabase } from './src/api/supabaseClient';

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
  const { colors, isDark } = useTheme();
  const appState = useRef(AppState.currentState);
  const wasLoggedInRef = useRef(false);

  useEffect(() => {
    const isLoggedIn = !!session;

    // initializeApp tylko przy przejściu z niezalogowany → zalogowany
    // (nie przy odświeżeniu tokenu JWT co godzinę — TOKEN_REFRESHED też zmienia obiekt sesji)
    if (isLoggedIn && !wasLoggedInRef.current) {
      console.log('🚀 AppContent: initializeApp (pierwsze logowanie)');
      initializeApp();
    } else if (isLoggedIn && wasLoggedInRef.current) {
      console.log('🔁 AppContent: session zmieniona (TOKEN_REFRESHED?) — pomijam initializeApp');
    } else if (!isLoggedIn && wasLoggedInRef.current) {
      console.log('🔒 AppContent: wylogowanie — zatrzymuję periodic scanning');
      callLogScanner.stopPeriodicScanning();
    }
    wasLoggedInRef.current = isLoggedIn;

    // Setup AppState listener
    const subscription = AppState.addEventListener('change', handleAppStateChange);

    return () => {
      subscription.remove();
    };
  }, [session]);

  // Keep Supabase alive: ping co 4 minuty zapobiega cold-startowi (free tier pausuje po 5 min)
  useEffect(() => {
    if (!session) return;

    const ping = async () => {
      try {
        await supabase.from('profiles').select('id').limit(1);
        console.log('🏓 Supabase keep-alive ping OK');
      } catch {
        // Błąd ping jest nieistotny - nie przerywamy działania aplikacji
      }
    };

    const intervalId = setInterval(ping, 4 * 60 * 1000); // co 4 minuty
    return () => clearInterval(intervalId);
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
      // setupNotificationHandler and initializeDeviceService run immediately —
      // they don't trigger system dialogs and don't put app in background.
      setupNotificationHandler();
      await initializeDeviceService();
      checkBatteryOptimization();

      // requestPermissions is deferred by 5s so that CallLogsList has time to load
      // data before the system permission dialog appears. The dialog puts the app in
      // background, which causes Android (Doze/OLAF) to suspend network access and
      // makes the initial REST fetches time out. Data loading takes ~1–2s, so 5s is
      // a safe margin. If permissions are already granted, requestPermissions() returns
      // immediately without showing a dialog — the delay is then harmless.
      setTimeout(async () => {
        try {
          const hasPermissions = await callLogScanner.requestPermissions();
          if (hasPermissions) {
            console.log('✅ Permissions granted — startPeriodicScanning');
            callLogScanner.startPeriodicScanning(1);
          } else {
            console.warn('⚠️ Permissions not granted - call monitoring disabled');
          }
        } catch (error) {
          console.error('Error requesting permissions:', error);
        }
      }, 5000);
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
      <View style={[styles.loadingContainer, { backgroundColor: colors.background }]}>
        <ActivityIndicator size="large" color={colors.primary} />
        <Text style={[styles.loadingText, { color: colors.textSecondary }]}>Ładowanie...</Text>
      </View>
    );
  }

  // Show auth screen if not authenticated
  if (!session) {
    return <AuthScreen onAuthSuccess={() => { }} />;
  }

  // Show main app when authenticated
  return (
    <NavigationContainer>
      <AppNavigator />
      <StatusBar style={isDark ? 'light' : 'dark'} />
    </NavigationContainer>
  );
};

/**
 * Root App Component with SafeAreaProvider and AuthProvider
 */
export default function App() {
  return (
    <SafeAreaProvider>
      <ThemeProvider>
        <AuthProvider>
          <AppContent />
        </AuthProvider>
      </ThemeProvider>
    </SafeAreaProvider>
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
