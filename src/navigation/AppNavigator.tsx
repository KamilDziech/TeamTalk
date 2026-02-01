/**
 * AppNavigator
 *
 * Main navigation structure with bottom tabs.
 * Settings gear icon is only available in CallLogsStackNavigator.
 * Supports dynamic theming (light/dark mode).
 */

import React from 'react';
import { Text } from 'react-native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { CallLogsStackNavigator } from '@/navigation/CallLogsStackNavigator';
import { ClientsStackNavigator } from '@/navigation/ClientsStackNavigator';
import { HistoryStackNavigator } from '@/navigation/HistoryStackNavigator';
import { AddClientScreen } from '@/screens/AddClientScreen';
import { AddNoteScreen } from '@/screens/AddNoteScreen';
import { useTheme } from '@/contexts/ThemeContext';
import { typography, shadows } from '@/styles/theme';

const Tab = createBottomTabNavigator();

export const AppNavigator: React.FC = () => {
  const { colors, isDark } = useTheme();

  console.log('🧭 AppNavigator: Rendering Tab.Navigator, isDark:', isDark);

  return (
    <Tab.Navigator
      screenOptions={{
        tabBarActiveTintColor: colors.primary,
        tabBarInactiveTintColor: colors.textSecondary,
        tabBarStyle: {
          backgroundColor: isDark ? colors.surface : colors.white,
          borderTopWidth: 1,
          borderTopColor: colors.border,
          height: 80,
          paddingBottom: 24,
          paddingTop: 8,
        },
        tabBarLabelStyle: {
          fontSize: typography.xs,
          fontWeight: typography.medium,
        },
        headerStyle: {
          backgroundColor: isDark ? colors.surface : colors.white,
          ...shadows.sm,
        },
        headerTintColor: colors.textPrimary,
        headerTitleStyle: {
          fontWeight: typography.semibold,
          fontSize: typography.lg,
          color: colors.textPrimary,
        },
        headerShadowVisible: false,
      }}
    >
      <Tab.Screen
        name="CallLogs"
        component={CallLogsStackNavigator}
        options={{
          title: 'Kolejka Połączeń',
          tabBarLabel: 'Kolejka',
          headerShown: false,
          tabBarIcon: ({ color }) => <Text style={{ fontSize: 24, color }}>📞</Text>,
        }}
      />
      <Tab.Screen
        name="Clients"
        component={ClientsStackNavigator}
        options={{
          title: 'Klienci',
          tabBarLabel: 'Klienci',
          headerShown: false,
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
        component={HistoryStackNavigator}
        options={{
          title: 'Historia Rozmów',
          tabBarLabel: 'Historia',
          headerShown: false,
          tabBarIcon: ({ color }) => <Text style={{ fontSize: 24, color }}>📋</Text>,
        }}
      />
    </Tab.Navigator>
  );
};
