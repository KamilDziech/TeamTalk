/**
 * HistoryStackNavigator
 *
 * Stack navigator for History feature with Master-Detail pattern.
 * - HistoryList: Minimalist rows of completed calls with notes
 * - NoteDetail: Full note content view
 */

import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { HistoryScreen } from '@/screens/HistoryScreen';
import { NoteDetailScreen } from '@/screens/NoteDetailScreen';
import { colors, typography } from '@/styles/theme';
import type { HistoryItem } from '@/screens/HistoryScreen';

export type HistoryStackParamList = {
    HistoryList: undefined;
    NoteDetail: { item: HistoryItem };
};

const Stack = createNativeStackNavigator<HistoryStackParamList>();

export const HistoryStackNavigator: React.FC = () => {
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
