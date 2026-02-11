/**
 * ClientsList Component
 *
 * Displays a list of clients from Supabase with navigation to client timeline.
 * Refactored: SafeArea fix, pull-to-refresh, consistent styling.
 */

import React, { useState, useCallback, useEffect, useMemo } from 'react';
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
  TextInput,
} from 'react-native';
import { useNavigation, useFocusEffect } from '@react-navigation/native';
import { MaterialIcons } from '@expo/vector-icons';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useClients } from '@/hooks/useClients';
import { contactLookupService } from '@/services/ContactLookupService';
import { useTheme } from '@/contexts/ThemeContext';
import type { Client } from '@/types';
import type { ClientsStackParamList } from '@/navigation/ClientsStackNavigator';
import { spacing, radius, typography } from '@/styles/theme';

type NavigationProp = NativeStackNavigationProp<ClientsStackParamList, 'ClientsList'>;

export const ClientsList: React.FC = () => {
  console.log('👥 ClientsList: Component rendering');
  const navigation = useNavigation<NavigationProp>();
  const { colors, isDark } = useTheme();
  const styles = createStyles(colors);
  const { clients, loading, error, refetch } = useClients();
  const [refreshing, setRefreshing] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  // Filter clients based on search query
  const filteredClients = useMemo(() => {
    if (!searchQuery.trim()) {
      return clients;
    }
    const query = searchQuery.toLowerCase();
    return clients.filter((client) => {
      const deviceContactName = contactLookupService.lookupContactName(client.phone) || '';
      const clientName = client.name?.toLowerCase() || '';
      const phone = client.phone?.toLowerCase() || '';
      const address = client.address?.toLowerCase() || '';
      return (
        deviceContactName.toLowerCase().includes(query) ||
        clientName.includes(query) ||
        phone.includes(query) ||
        address.includes(query)
      );
    });
  }, [searchQuery, clients]);

  // Load device contacts for caller ID
  useEffect(() => {
    contactLookupService.loadDeviceContacts();
  }, []);

  // Pull-to-refresh handler
  const handleRefresh = useCallback(async () => {
    console.log('👥 ClientsList: Pull to refresh');
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
      <Text style={styles.headerTitle}>Historia</Text>
      <Text style={styles.headerCount}>{filteredClients.length}</Text>
    </View>
  );

  // Search Bar
  const SearchBar = () => (
    <View style={styles.searchContainer}>
      <MaterialIcons name="search" size={20} color={colors.textTertiary} />
      <TextInput
        style={styles.searchInput}
        placeholder="Szukaj po nazwie lub numerze..."
        placeholderTextColor={colors.textTertiary}
        value={searchQuery}
        onChangeText={setSearchQuery}
        autoCorrect={false}
        autoCapitalize="none"
      />
      {searchQuery.length > 0 && (
        <TouchableOpacity onPress={() => setSearchQuery('')}>
          <MaterialIcons name="close" size={20} color={colors.textTertiary} />
        </TouchableOpacity>
      )}
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

  const renderClient = ({ item }: { item: Client }) => {
    // Priority: 1. Device contacts, 2. CRM client name, 3. Phone number
    const deviceContactName = contactLookupService.lookupContactName(item.phone);
    const crmClientName = item.name;
    const displayName = deviceContactName || crmClientName || item.phone || 'Nieznany';

    return (
      <TouchableOpacity
        style={styles.row}
        onPress={() => handleClientPress(item)}
        activeOpacity={0.7}
      >
        {/* Left: Avatar placeholder */}
        <View style={styles.avatarContainer}>
          <Text style={styles.avatarText}>
            {displayName.charAt(0).toUpperCase()}
          </Text>
        </View>

        {/* Center: Name + Phone */}
        <View style={styles.rowCenter}>
          <Text style={styles.clientName} numberOfLines={1}>
            {displayName}
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
  };

  // Separator
  const ItemSeparator = () => <View style={styles.separator} />;

  return (
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle={isDark ? 'light-content' : 'dark-content'} backgroundColor={colors.background} />
      <ScreenHeader />
      <SearchBar />
      <FlatList
        data={filteredClients}
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
    searchContainer: {
      flexDirection: 'row',
      alignItems: 'center',
      backgroundColor: colors.surface,
      marginHorizontal: spacing.lg,
      marginTop: spacing.md,
      marginBottom: spacing.sm,
      paddingHorizontal: spacing.md,
      paddingVertical: spacing.sm,
      borderRadius: radius.lg,
      borderWidth: 1,
      borderColor: colors.border,
    },
    searchInput: {
      flex: 1,
      marginLeft: spacing.sm,
      fontSize: typography.base,
      color: colors.textPrimary,
      paddingVertical: spacing.xs,
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
