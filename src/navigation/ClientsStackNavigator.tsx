/**
 * ClientsStackNavigator
 *
 * Stack navigator for the Clients tab, enabling navigation to client details/timeline.
 * Supports dynamic theming.
 */

import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { ClientsList } from '@/components/ClientsList';
import { ClientTimelineScreen } from '@/screens/ClientTimelineScreen';
import { useTheme } from '@/contexts/ThemeContext';
import { typography } from '@/styles/theme';
import type { Client } from '@/types';

export type ClientsStackParamList = {
  ClientsList: undefined;
  ClientTimeline: { client: Client };
};

const Stack = createNativeStackNavigator<ClientsStackParamList>();

export const ClientsStackNavigator: React.FC = () => {
  const { colors } = useTheme();

  return (
    <Stack.Navigator
      screenOptions={{
        headerStyle: {
          backgroundColor: colors.surface,
        },
        headerTintColor: colors.textPrimary,
        headerTitleStyle: {
          fontWeight: typography.semibold,
          fontSize: typography.lg,
        },
        headerShadowVisible: false,
      }}
    >
      <Stack.Screen
        name="ClientsList"
        component={ClientsList}
        options={{
          title: 'Klienci',
          headerShown: false,
        }}
      />
      <Stack.Screen
        name="ClientTimeline"
        component={ClientTimelineScreen}
        options={({ route }) => ({
          title: route.params.client.name || 'Historia klienta',
          headerShown: true,
        })}
      />
    </Stack.Navigator>
  );
};
