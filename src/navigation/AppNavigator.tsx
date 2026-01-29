/**
 * AppNavigator
 *
 * Main navigation structure with bottom tabs
 * Includes logout functionality in header
 */

import React from 'react';
import { Text, TouchableOpacity, Alert, StyleSheet, View } from 'react-native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { CallLogsList } from '@/components/CallLogsList';
import { ClientsStackNavigator } from '@/navigation/ClientsStackNavigator';
import { AddClientScreen } from '@/screens/AddClientScreen';
import { AddNoteScreen } from '@/screens/AddNoteScreen';
import { HistoryScreen } from '@/screens/HistoryScreen';
import { useAuth } from '@/contexts/AuthContext';
import { colors, spacing, radius, typography, shadows } from '@/styles/theme';

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
        tabBarActiveTintColor: colors.primary,
        tabBarInactiveTintColor: colors.textSecondary,
        tabBarStyle: {
          backgroundColor: colors.white,
          borderTopWidth: 1,
          borderTopColor: colors.border,
          height: 60,
          paddingBottom: 8,
          paddingTop: 8,
        },
        tabBarLabelStyle: {
          fontSize: typography.xs,
          fontWeight: typography.medium,
        },
        headerStyle: {
          backgroundColor: colors.white,
          ...shadows.sm,
        },
        headerTintColor: colors.textPrimary,
        headerTitleStyle: {
          fontWeight: typography.semibold,
          fontSize: typography.lg,
          color: colors.textPrimary,
        },
        headerShadowVisible: false,
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
    marginRight: spacing.lg,
    paddingVertical: spacing.sm,
    paddingHorizontal: spacing.md,
    backgroundColor: colors.background,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
  },
  logoutButtonText: {
    color: colors.textSecondary,
    fontSize: typography.sm,
    fontWeight: typography.medium,
  },
});
