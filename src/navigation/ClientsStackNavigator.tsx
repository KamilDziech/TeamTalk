/**
 * ClientsStackNavigator
 *
 * Stack navigator for the Clients tab, enabling navigation to client details/timeline.
 */

import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { ClientsList } from '@/components/ClientsList';
import { ClientTimelineScreen } from '@/screens/ClientTimelineScreen';
import type { Client } from '@/types';

export type ClientsStackParamList = {
  ClientsList: undefined;
  ClientTimeline: { client: Client };
};

const Stack = createNativeStackNavigator<ClientsStackParamList>();

export const ClientsStackNavigator: React.FC = () => {
  return (
    <Stack.Navigator
      screenOptions={{
        headerStyle: {
          backgroundColor: '#007AFF',
        },
        headerTintColor: '#fff',
        headerTitleStyle: {
          fontWeight: 'bold',
        },
      }}
    >
      <Stack.Screen
        name="ClientsList"
        component={ClientsList}
        options={{
          title: 'Klienci',
          headerShown: false, // Tab navigator already shows header
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
