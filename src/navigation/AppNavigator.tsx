/**
 * AppNavigator
 *
 * Main navigation structure with bottom tabs.
 * Settings gear icon is only available in CallLogsStackNavigator.
 * Supports dynamic theming (light/dark mode).
 */

import React from 'react';
import { Text, Platform } from 'react-native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { CallLogsStackNavigator } from '@/navigation/CallLogsStackNavigator';
import { ClientsStackNavigator } from '@/navigation/ClientsStackNavigator';
import { AddNoteScreen } from '@/screens/AddNoteScreen';
import { SettingsScreen } from '@/screens/SettingsScreen';
import { useTheme } from '@/contexts/ThemeContext';
import { typography, shadows } from '@/styles/theme';

const Tab = createBottomTabNavigator();

export const AppNavigator: React.FC = () => {
  const { colors, isDark } = useTheme();
  const insets = useSafeAreaInsets();

  console.log('🧭 AppNavigator: Rendering Tab.Navigator, isDark:', isDark);

  // Calculate tab bar height with safe area insets
  // Base content height (56) + top padding (8) + bottom inset for system bars
  const tabBarHeight = 56 + 8 + Math.max(insets.bottom, 8);

  return (
    <Tab.Navigator
      screenOptions={{
        tabBarActiveTintColor: colors.primary,
        tabBarInactiveTintColor: colors.textSecondary,
        tabBarStyle: {
          backgroundColor: isDark ? colors.surface : colors.white,
          borderTopWidth: 1,
          borderTopColor: colors.border,
          height: tabBarHeight,
          paddingBottom: Math.max(insets.bottom, 8),
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
          title: 'Historia',
          tabBarLabel: 'Historia',
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
        name="Settings"
        component={SettingsScreen}
        options={{
          title: 'Ustawienia',
          tabBarLabel: 'Ustawienia',
          headerShown: false,
          tabBarIcon: ({ color }) => <Text style={{ fontSize: 24, color }}>⚙️</Text>,
        }}
      />
    </Tab.Navigator>
  );
};
