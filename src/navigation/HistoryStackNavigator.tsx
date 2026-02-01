/**
 * HistoryStackNavigator
 *
 * Stack navigator for History feature with Master-Detail pattern.
 * Supports dynamic theming.
 */

import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { HistoryScreen } from '@/screens/HistoryScreen';
import { NoteDetailScreen } from '@/screens/NoteDetailScreen';
import { useTheme } from '@/contexts/ThemeContext';
import { typography } from '@/styles/theme';
import type { HistoryItem } from '@/screens/HistoryScreen';

export type HistoryStackParamList = {
    HistoryList: undefined;
    NoteDetail: { item: HistoryItem };
};

const Stack = createNativeStackNavigator<HistoryStackParamList>();

export const HistoryStackNavigator: React.FC = () => {
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
                name="HistoryList"
                component={HistoryScreen}
                options={{
                    title: 'Historia',
                    headerShown: false,
                }}
            />
            <Stack.Screen
                name="NoteDetail"
                component={NoteDetailScreen}
                options={({ route }) => ({
                    title: route.params.item.client?.name || 'Notatka',
                    headerShown: true,
                    headerBackTitle: 'Wróć',
                })}
            />
        </Stack.Navigator>
    );
};
