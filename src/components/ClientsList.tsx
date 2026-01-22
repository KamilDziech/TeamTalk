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

type NavigationProp = NativeStackNavigationProp<ClientsStackParamList, 'ClientsList'>;

export const ClientsList: React.FC = () => {
  const navigation = useNavigation<NavigationProp>();
  const { clients, loading, error, refetch } = useClients();

  if (loading) {
    return (
      <View style={styles.centerContainer}>
        <ActivityIndicator size="large" color="#007AFF" />
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
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  centerContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
    backgroundColor: '#f5f5f5',
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 16,
    backgroundColor: '#fff',
    borderBottomWidth: 1,
    borderBottomColor: '#e0e0e0',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#333',
  },
  refreshButton: {
    fontSize: 16,
    color: '#007AFF',
  },
  loadingText: {
    marginTop: 12,
    fontSize: 16,
    color: '#666',
  },
  errorText: {
    fontSize: 16,
    color: '#FF3B30',
    marginBottom: 16,
    textAlign: 'center',
  },
  emptyText: {
    fontSize: 18,
    fontWeight: '600',
    color: '#666',
    marginBottom: 8,
  },
  emptySubtext: {
    fontSize: 14,
    color: '#999',
    textAlign: 'center',
    marginBottom: 16,
  },
  retryButton: {
    backgroundColor: '#007AFF',
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: 8,
  },
  retryButtonText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
  },
  listContent: {
    padding: 16,
  },
  clientCard: {
    backgroundColor: '#fff',
    padding: 16,
    borderRadius: 12,
    marginBottom: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
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
    color: '#007AFF',
    fontWeight: '300',
  },
  clientName: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 4,
  },
  clientPhone: {
    fontSize: 16,
    color: '#007AFF',
    marginBottom: 8,
  },
  clientAddress: {
    fontSize: 14,
    color: '#666',
    marginBottom: 4,
  },
  clientNotes: {
    fontSize: 14,
    color: '#999',
    fontStyle: 'italic',
    marginBottom: 8,
  },
  clientDate: {
    fontSize: 12,
    color: '#999',
    marginTop: 4,
  },
});
