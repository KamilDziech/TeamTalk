/**
 * ClientsList Component
 *
 * Displays a list of clients from Supabase with navigation to client timeline.
 * Refactored: SafeArea fix, pull-to-refresh, consistent styling.
 */

import React, { useState, useCallback } from 'react';
import {
  View,
  Text,
  FlatList,
  StyleSheet,
  ActivityIndicator,
  TouchableOpacity,
  RefreshControl,
  SafeAreaView,
  StatusBar,
  Platform,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useClients } from '@/hooks/useClients';
import { useTheme } from '@/contexts/ThemeContext';
import type { Client } from '@/types';
import type { ClientsStackParamList } from '@/navigation/ClientsStackNavigator';
import { spacing, radius, typography } from '@/styles/theme';

type NavigationProp = NativeStackNavigationProp<ClientsStackParamList, 'ClientsList'>;

export const ClientsList: React.FC = () => {
  const navigation = useNavigation<NavigationProp>();
  const { colors, isDark } = useTheme();
  const styles = createStyles(colors);
  const { clients, loading, error, refetch } = useClients();
  const [refreshing, setRefreshing] = useState(false);

  // Pull-to-refresh handler
  const handleRefresh = useCallback(async () => {
    setRefreshing(true);
    await refetch();
    setRefreshing(false);
  }, [refetch]);

  const handleClientPress = (client: Client) => {
    navigation.navigate('ClientTimeline', { client });
  };

  // Screen Header
  const ScreenHeader = () => (
    <View style={styles.headerContainer}>
      <Text style={styles.headerTitle}>Klienci</Text>
      <Text style={styles.headerCount}>{clients.length}</Text>
    </View>
  );

  // Loading state
  if (loading && !refreshing) {
    return (
      <SafeAreaView style={styles.safeArea}>
        <StatusBar barStyle={isDark ? 'light-content' : 'dark-content'} backgroundColor={colors.background} />
        <ScreenHeader />
        <View style={styles.centerContainer}>
          <ActivityIndicator size="large" color={colors.primary} />
          <Text style={styles.loadingText}>Ładowanie klientów...</Text>
        </View>
      </SafeAreaView>
    );
  }

  // Error state
  if (error) {
    return (
      <SafeAreaView style={styles.safeArea}>
        <StatusBar barStyle={isDark ? 'light-content' : 'dark-content'} backgroundColor={colors.background} />
        <ScreenHeader />
        <View style={styles.centerContainer}>
          <Text style={styles.errorText}>Błąd: {error}</Text>
          <TouchableOpacity style={styles.retryButton} onPress={refetch}>
            <Text style={styles.retryButtonText}>Spróbuj ponownie</Text>
          </TouchableOpacity>
        </View>
      </SafeAreaView>
    );
  }

  const renderClient = ({ item }: { item: Client }) => (
    <TouchableOpacity
      style={styles.row}
      onPress={() => handleClientPress(item)}
      activeOpacity={0.7}
    >
      {/* Left: Avatar placeholder */}
      <View style={styles.avatarContainer}>
        <Text style={styles.avatarText}>
          {(item.name || item.phone || '?').charAt(0).toUpperCase()}
        </Text>
      </View>

      {/* Center: Name + Phone */}
      <View style={styles.rowCenter}>
        <Text style={styles.clientName} numberOfLines={1}>
          {item.name || 'Brak nazwy'}
        </Text>
        <Text style={styles.clientPhone} numberOfLines={1}>
          {item.phone}
        </Text>
        {item.address && (
          <Text style={styles.clientAddress} numberOfLines={1}>
            📍 {item.address}
          </Text>
        )}
      </View>

      {/* Right: Chevron */}
      <Text style={styles.chevron}>›</Text>
    </TouchableOpacity>
  );

  // Separator
  const ItemSeparator = () => <View style={styles.separator} />;

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle={isDark ? 'light-content' : 'dark-content'} backgroundColor={colors.background} />
      <ScreenHeader />
      <FlatList
        data={clients}
        renderItem={renderClient}
        keyExtractor={(item) => item.id}
        ItemSeparatorComponent={ItemSeparator}
        contentContainerStyle={styles.listContent}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={handleRefresh}
            tintColor={colors.primary}
            colors={[colors.primary]}
          />
        }
        ListEmptyComponent={
          <View style={styles.emptyContainer}>
            <Text style={styles.emptyIcon}>👥</Text>
            <Text style={styles.emptyTitle}>Brak klientów</Text>
            <Text style={styles.emptyText}>
              Przeciągnij w dół, aby odświeżyć lub dodaj klienta w zakładce "Dodaj".
            </Text>
          </View>
        }
      />
    </SafeAreaView>
  );
};

// Dynamic styles generator
const createStyles = (colors: ReturnType<typeof import('@/contexts/ThemeContext').useTheme>['colors']) =>
  StyleSheet.create({
    safeArea: {
      flex: 1,
      backgroundColor: colors.background,
      paddingTop: Platform.OS === 'android' ? StatusBar.currentHeight : 0,
    },
    headerContainer: {
      flexDirection: 'row',
      alignItems: 'center',
      backgroundColor: colors.surface,
      paddingHorizontal: spacing.lg,
      paddingVertical: spacing.lg,
      borderBottomWidth: 1,
      borderBottomColor: colors.border,
    },
    headerTitle: {
      fontSize: typography.xxl,
      fontWeight: typography.bold,
      color: colors.textPrimary,
    },
    headerCount: {
      fontSize: typography.base,
      fontWeight: typography.medium,
      color: colors.textTertiary,
      marginLeft: spacing.sm,
      backgroundColor: colors.borderLight,
      paddingHorizontal: spacing.sm,
      paddingVertical: 2,
      borderRadius: radius.sm,
    },
    centerContainer: {
      flex: 1,
      justifyContent: 'center',
      alignItems: 'center',
      padding: spacing.xl,
    },
    loadingText: {
      marginTop: spacing.md,
      fontSize: typography.base,
      color: colors.textSecondary,
    },
    errorText: {
      fontSize: typography.base,
      color: colors.error,
      marginBottom: spacing.md,
      textAlign: 'center',
    },
    retryButton: {
      backgroundColor: colors.primary,
      paddingHorizontal: spacing.xxl,
      paddingVertical: spacing.md,
      borderRadius: radius.md,
    },
    retryButtonText: {
      color: colors.textInverse,
      fontSize: typography.base,
      fontWeight: typography.semibold,
    },

    // List
    listContent: {
      paddingBottom: spacing.xl,
    },

    // Row
    row: {
      flexDirection: 'row',
      alignItems: 'center',
      backgroundColor: colors.surface,
      paddingVertical: spacing.md,
      paddingHorizontal: spacing.lg,
    },
    avatarContainer: {
      width: 44,
      height: 44,
      borderRadius: 22,
      backgroundColor: colors.primaryLight,
      justifyContent: 'center',
      alignItems: 'center',
      marginRight: spacing.md,
    },
    avatarText: {
      fontSize: typography.lg,
      fontWeight: typography.bold,
      color: colors.primary,
    },
    rowCenter: {
      flex: 1,
    },
    clientName: {
      fontSize: typography.lg,
      fontWeight: typography.semibold,
      color: colors.textPrimary,
      marginBottom: 2,
    },
    clientPhone: {
      fontSize: typography.sm,
      color: colors.primary,
    },
    clientAddress: {
      fontSize: typography.xs,
      color: colors.textTertiary,
      marginTop: 2,
    },
    chevron: {
      fontSize: 24,
      color: colors.textTertiary,
      fontWeight: '300',
    },
    separator: {
      height: 1,
      backgroundColor: colors.borderLight,
      marginLeft: 72,
    },

    // Empty state
    emptyContainer: {
      flex: 1,
      justifyContent: 'center',
      alignItems: 'center',
      paddingVertical: 60,
      paddingHorizontal: 40,
    },
    emptyIcon: {
      fontSize: 48,
      marginBottom: spacing.md,
    },
    emptyTitle: {
      fontSize: typography.lg,
      fontWeight: typography.semibold,
      color: colors.textPrimary,
      marginBottom: spacing.sm,
    },
    emptyText: {
      fontSize: typography.sm,
      color: colors.textTertiary,
      textAlign: 'center',
      lineHeight: 20,
    },
  });
