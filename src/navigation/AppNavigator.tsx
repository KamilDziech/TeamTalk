/**
 * AppNavigator
 *
 * Main navigation structure with bottom tabs
 */

import React from 'react';
import { Text } from 'react-native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { CallLogsList } from '@/components/CallLogsList';
import { ClientsStackNavigator } from '@/navigation/ClientsStackNavigator';
import { AddClientScreen } from '@/screens/AddClientScreen';
import { AddNoteScreen } from '@/screens/AddNoteScreen';
import { HistoryScreen } from '@/screens/HistoryScreen';

const Tab = createBottomTabNavigator();

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
