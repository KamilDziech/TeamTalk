/**
 * ClientsList Component
 *
 * Displays a list of clients from Supabase with navigation to client timeline.
 */

import React from 'react';
import { View, Text, FlatList, StyleSheet, ActivityIndicator, TouchableOpacity } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useClients } from '@/hooks/useClients';
import type { Client } from '@/types';
import type { ClientsStackParamList } from '@/navigation/ClientsStackNavigator';
import { colors, spacing, radius, typography, shadows, commonStyles } from '@/styles/theme';

type NavigationProp = NativeStackNavigationProp<ClientsStackParamList, 'ClientsList'>;

export const ClientsList: React.FC = () => {
  const navigation = useNavigation<NavigationProp>();
  const { clients, loading, error, refetch } = useClients();

  if (loading) {
    return (
      <View style={styles.centerContainer}>
        <ActivityIndicator size="large" color={colors.primary} />
        <Text style={styles.loadingText}>Ładowanie klientów...</Text>
      </View>
    );
  }

  if (error) {
    return (
      <View style={styles.centerContainer}>
        <Text style={styles.errorText}>Błąd: {error}</Text>
        <TouchableOpacity style={styles.retryButton} onPress={refetch}>
          <Text style={styles.retryButtonText}>Spróbuj ponownie</Text>
        </TouchableOpacity>
      </View>
    );
  }

  if (clients.length === 0) {
    return (
      <View style={styles.centerContainer}>
        <Text style={styles.emptyText}>Brak klientów w bazie</Text>
        <Text style={styles.emptySubtext}>
          Dodaj testowego klienta w panelu Supabase
        </Text>
        <TouchableOpacity style={styles.retryButton} onPress={refetch}>
          <Text style={styles.retryButtonText}>Odśwież</Text>
        </TouchableOpacity>
      </View>
    );
  }

  const handleClientPress = (client: Client) => {
    navigation.navigate('ClientTimeline', { client });
  };

  const renderClient = ({ item }: { item: Client }) => (
    <TouchableOpacity
      style={styles.clientCard}
      onPress={() => handleClientPress(item)}
      activeOpacity={0.7}
    >
      <View style={styles.clientCardHeader}>
        <View style={styles.clientCardInfo}>
          <Text style={styles.clientName}>{item.name || 'Brak nazwy'}</Text>
          <Text style={styles.clientPhone}>{item.phone}</Text>
        </View>
        <Text style={styles.chevron}>›</Text>
      </View>
      {item.address && (
        <Text style={styles.clientAddress}>📍 {item.address}</Text>
      )}
      {item.notes && (
        <Text style={styles.clientNotes}>{item.notes}</Text>
      )}
      <Text style={styles.clientDate}>
        Utworzono: {new Date(item.created_at).toLocaleDateString('pl-PL')}
      </Text>
    </TouchableOpacity>
  );

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>Lista klientów ({clients.length})</Text>
        <TouchableOpacity onPress={refetch}>
          <Text style={styles.refreshButton}>🔄 Odśwież</Text>
        </TouchableOpacity>
      </View>
      <FlatList
        data={clients}
        renderItem={renderClient}
        keyExtractor={(item) => item.id}
        contentContainerStyle={styles.listContent}
      />
    </View>
  );
};

const styles = StyleSheet.create({
  container: commonStyles.screen,
  centerContainer: {
    ...commonStyles.centered,
    backgroundColor: colors.background,
  },
  header: {
    ...commonStyles.rowBetween,
    padding: spacing.lg,
    backgroundColor: colors.white,
    borderBottomWidth: 1,
    borderBottomColor: colors.border,
  },
  title: {
    ...commonStyles.heading,
  },
  refreshButton: {
    fontSize: typography.base,
    color: colors.primary,
    fontWeight: typography.medium,
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
  emptyText: {
    ...commonStyles.emptyStateTitle,
  },
  emptySubtext: {
    ...commonStyles.emptyStateText,
    marginBottom: spacing.lg,
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
  listContent: {
    padding: spacing.lg,
  },
  clientCard: {
    ...commonStyles.card,
    marginBottom: spacing.md,
  },
  clientCardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  clientCardInfo: {
    flex: 1,
  },
  chevron: {
    fontSize: 24,
    color: colors.primaryLight,
    fontWeight: '300',
  },
  clientName: {
    fontSize: typography.lg,
    fontWeight: typography.bold,
    color: colors.textPrimary,
    marginBottom: 4,
  },
  clientPhone: {
    fontSize: typography.sm,
    color: colors.primary,
    marginBottom: spacing.sm,
  },
  clientAddress: {
    fontSize: typography.sm,
    color: colors.textSecondary,
    marginBottom: 4,
  },
  clientNotes: {
    fontSize: typography.sm,
    color: colors.textTertiary,
    fontStyle: 'italic',
    marginBottom: spacing.sm,
  },
  clientDate: {
    fontSize: typography.xs,
    color: colors.textTertiary,
    marginTop: 4,
  },
});
