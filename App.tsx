/**
 * TeamTalk - Main App Component
 *
 * Features:
 * - Bottom Tab Navigation
 * - CallLog Scanner integration
 * - Notification handling
 * - Realtime sync
 */

import React, { useEffect, useRef, useState } from 'react';
import { StatusBar } from 'expo-status-bar';
import { NavigationContainer } from '@react-navigation/native';
import * as Notifications from 'expo-notifications';
import { AppState, AppStateStatus, Alert, TextInput, Modal, View, Text, TouchableOpacity, StyleSheet, Platform, Linking } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { AppNavigator } from './src/navigation/AppNavigator';
import { callLogScanner } from './src/services/CallLogScanner';
import { deviceService } from './src/services/DeviceService';

const BATTERY_OPTIMIZATION_SHOWN_KEY = 'battery_optimization_shown';

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
  const [showNameModal, setShowNameModal] = useState(false);
  const [userName, setUserName] = useState('');

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

      // Initialize device service for push notifications
      await initializeDeviceService();

      // Check battery optimization settings
      checkBatteryOptimization();
    } catch (error) {
      console.error('Error initializing app:', error);
    }
  };

  const initializeDeviceService = async () => {
    try {
      // Check if user name is already set
      const existingName = await deviceService.getUserName();

      if (existingName) {
        // Initialize with existing name
        await deviceService.initialize(existingName);
      } else {
        // Show modal to get user name
        setShowNameModal(true);
      }
    } catch (error) {
      console.error('Error initializing device service:', error);
    }
  };

  const handleNameSubmit = async () => {
    if (userName.trim()) {
      setShowNameModal(false);
      await deviceService.initialize(userName.trim());
    }
  };

  const checkBatteryOptimization = async () => {
    // Only check on Android
    if (Platform.OS !== 'android') return;

    try {
      // Check if we've already shown this prompt
      const shown = await AsyncStorage.getItem(BATTERY_OPTIMIZATION_SHOWN_KEY);
      if (shown) return;

      // Mark as shown
      await AsyncStorage.setItem(BATTERY_OPTIMIZATION_SHOWN_KEY, 'true');

      // Show alert after a delay
      setTimeout(() => {
        Alert.alert(
          'Optymalizacja baterii',
          'Dla niezawodnego działania powiadomień i synchronizacji w tle, zalecamy wyłączenie optymalizacji baterii dla TeamTalk.\n\nUstawienia → Aplikacje → TeamTalk → Bateria → Nieograniczone',
          [
            { text: 'Przypomnij później', style: 'cancel' },
            {
              text: 'Otwórz ustawienia',
              onPress: () => {
                // Try to open battery settings
                Linking.openSettings();
              },
            },
          ]
        );
      }, 3000);
    } catch (error) {
      console.error('Error checking battery optimization:', error);
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

      {/* User Name Modal */}
      <Modal
        visible={showNameModal}
        transparent
        animationType="fade"
      >
        <View style={styles.modalOverlay}>
          <View style={styles.modalContent}>
            <Text style={styles.modalTitle}>Witaj w TeamTalk!</Text>
            <Text style={styles.modalSubtitle}>
              Podaj swoje imię, aby inni widzieli kto dodaje notatki.
            </Text>
            <TextInput
              style={styles.nameInput}
              placeholder="Twoje imię..."
              placeholderTextColor="#999"
              value={userName}
              onChangeText={setUserName}
              autoFocus
              autoCapitalize="words"
            />
            <TouchableOpacity
              style={[styles.submitButton, !userName.trim() && styles.submitButtonDisabled]}
              onPress={handleNameSubmit}
              disabled={!userName.trim()}
            >
              <Text style={styles.submitButtonText}>Rozpocznij</Text>
            </TouchableOpacity>
          </View>
        </View>
      </Modal>
    </NavigationContainer>
  );
}

const styles = StyleSheet.create({
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  modalContent: {
    backgroundColor: '#fff',
    borderRadius: 16,
    padding: 24,
    width: '100%',
    maxWidth: 340,
    alignItems: 'center',
  },
  modalTitle: {
    fontSize: 24,
    fontWeight: '700',
    color: '#333',
    marginBottom: 8,
  },
  modalSubtitle: {
    fontSize: 14,
    color: '#666',
    textAlign: 'center',
    marginBottom: 20,
    lineHeight: 20,
  },
  nameInput: {
    width: '100%',
    height: 50,
    borderWidth: 1,
    borderColor: '#ddd',
    borderRadius: 10,
    paddingHorizontal: 16,
    fontSize: 16,
    color: '#333',
    marginBottom: 16,
  },
  submitButton: {
    width: '100%',
    backgroundColor: '#007AFF',
    paddingVertical: 14,
    borderRadius: 10,
    alignItems: 'center',
  },
  submitButtonDisabled: {
    backgroundColor: '#ccc',
  },
  submitButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
});
