/**
 * AppNavigator
 *
 * Main navigation structure with bottom tabs
 * Includes logout functionality in header
 */

import React from 'react';
import { Text, TouchableOpacity, Alert, StyleSheet } from 'react-native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { CallLogsList } from '@/components/CallLogsList';
import { ClientsStackNavigator } from '@/navigation/ClientsStackNavigator';
import { AddClientScreen } from '@/screens/AddClientScreen';
import { AddNoteScreen } from '@/screens/AddNoteScreen';
import { HistoryScreen } from '@/screens/HistoryScreen';
import { useAuth } from '@/contexts/AuthContext';

const Tab = createBottomTabNavigator();

const LogoutButton: React.FC = () => {
  const { signOut, profile } = useAuth();

  const handleLogout = () => {
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

  return (
    <TouchableOpacity onPress={handleLogout} style={styles.logoutButton}>
      <Text style={styles.logoutButtonText}>Wyloguj</Text>
    </TouchableOpacity>
  );
};

export const AppNavigator: React.FC = () => {
  return (
    <Tab.Navigator
      screenOptions={{
        tabBarActiveTintColor: '#007AFF',
        tabBarInactiveTintColor: '#666',
        tabBarStyle: {
          backgroundColor: '#fff',
          borderTopWidth: 1,
          borderTopColor: '#e0e0e0',
        },
        headerStyle: {
          backgroundColor: '#007AFF',
        },
        headerTintColor: '#fff',
        headerTitleStyle: {
          fontWeight: 'bold',
        },
        headerRight: () => <LogoutButton />,
      }}
    >
      <Tab.Screen
        name="CallLogs"
        component={CallLogsList}
        options={{
          title: 'Kolejka Połączeń',
          tabBarLabel: 'Kolejka',
          tabBarIcon: ({ color }) => <Text style={{ fontSize: 24, color }}>📞</Text>,
        }}
      />
      <Tab.Screen
        name="Clients"
        component={ClientsStackNavigator}
        options={{
          title: 'Klienci',
          tabBarLabel: 'Klienci',
          headerShown: false, // Stack navigator handles its own header
          tabBarIcon: ({ color }) => <Text style={{ fontSize: 24, color }}>👥</Text>,
        }}
      />
      <Tab.Screen
        name="AddNote"
        component={AddNoteScreen}
        options={{
          title: 'Dodaj Notatkę',
          tabBarLabel: 'Notatka',
          tabBarIcon: ({ color }) => <Text style={{ fontSize: 24, color }}>🎤</Text>,
        }}
      />
      <Tab.Screen
        name="AddClient"
        component={AddClientScreen}
        options={{
          title: 'Dodaj Klienta',
          tabBarLabel: 'Dodaj',
          tabBarIcon: ({ color }) => <Text style={{ fontSize: 24, color }}>➕</Text>,
        }}
      />
      <Tab.Screen
        name="History"
        component={HistoryScreen}
        options={{
          title: 'Historia Rozmów',
          tabBarLabel: 'Historia',
          tabBarIcon: ({ color }) => <Text style={{ fontSize: 24, color }}>📋</Text>,
        }}
      />
    </Tab.Navigator>
  );
};

const styles = StyleSheet.create({
  logoutButton: {
    marginRight: 16,
    paddingVertical: 6,
    paddingHorizontal: 12,
    backgroundColor: 'rgba(255,255,255,0.2)',
    borderRadius: 6,
  },
  logoutButtonText: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '600',
  },
});
