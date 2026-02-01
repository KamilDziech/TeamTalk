/**
 * CallLogsStackNavigator
 *
 * Stack navigator for the Call Queue tab, enabling navigation to call details.
 * Settings button is only available from this tab.
 */

import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { CallLogsList } from '@/components/CallLogsList';
import { CallDetailsScreen } from '@/screens/CallDetailsScreen';
import { SettingsButton } from '@/components/SettingsButton';
import type { GroupedCallLog } from '@/components/CallLogsList';
import { colors, typography } from '@/styles/theme';

export type CallLogsStackParamList = {
    CallLogsList: undefined;
    CallDetails: { group: GroupedCallLog };
};

const Stack = createNativeStackNavigator<CallLogsStackParamList>();

export const CallLogsStackNavigator: React.FC = () => {
    return (
        <Stack.Navigator
            screenOptions={{
                headerStyle: {
                    backgroundColor: colors.white,
                },
                headerTintColor: colors.textPrimary,
                headerTitleStyle: {
                    fontWeight: typography.semibold,
                    fontSize: typography.lg,
                },
                headerShadowVisible: false,
                headerRight: () => <SettingsButton />,
            }}
        >
            <Stack.Screen
                name="CallLogsList"
                component={CallLogsList}
                options={{
                    title: 'Kolejka Połączeń',
                    headerShown: true,
                }}
            />
            <Stack.Screen
                name="CallDetails"
                component={CallDetailsScreen}
                options={({ route }) => ({
                    title: route.params.group.client?.phone || route.params.group.callerPhone || 'Szczegóły',
                    headerShown: true,
                    headerBackTitle: 'Wróć',
                })}
            />
        </Stack.Navigator>
    );
};
