/**
 * EditClientScreen
 *
 * Screen for editing existing client's address and notes.
 * Name is displayed but not editable (fetched from contacts).
 */

import React, { useState } from 'react';
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ScrollView,
  KeyboardAvoidingView,
  Platform,
  Alert,
  ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useTheme } from '@/contexts/ThemeContext';
import { spacing, radius, typography } from '@/styles/theme';
import { supabase } from '@/api/supabaseClient';
import type { Client } from '@/types';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

type ClientsStackParamList = {
  ClientsList: undefined;
  ClientTimeline: { client: Client };
  EditClient: { client: Client; onClientUpdated?: (updatedClient: Client) => void };
};

type Props = NativeStackScreenProps<ClientsStackParamList, 'EditClient'>;

export const EditClientScreen: React.FC<Props> = ({ route, navigation }) => {
  const { client, onClientUpdated } = route.params;
  const { colors } = useTheme();
  const styles = createStyles(colors);

  const [address, setAddress] = useState(client.address || '');
  const [notes, setNotes] = useState(client.notes || '');
  const [loading, setLoading] = useState(false);

  const handleSave = async () => {
    setLoading(true);

    try {
      const { data, error } = await supabase
        .from('clients')
        .update({
          address: address.trim() || null,
          notes: notes.trim() || null,
        })
        .eq('id', client.id)
        .select()
        .single();

      if (error) {
        throw error;
      }

      Alert.alert('Zapisano', 'Dane klienta zostały zaktualizowane.', [
        {
          text: 'OK',
          onPress: () => {
            onClientUpdated?.(data);
            navigation.goBack();
          },
        },
      ]);
    } catch (error) {
      console.error('Error updating client:', error);
      Alert.alert('Błąd', 'Nie udało się zapisać zmian. Spróbuj ponownie.');
    } finally {
      setLoading(false);
    }
  };

  const hasChanges =
    (address.trim() || null) !== (client.address || null) ||
    (notes.trim() || null) !== (client.notes || null);

  return (
    <SafeAreaView style={styles.safeArea} edges={['bottom']}>
      <KeyboardAvoidingView
        style={styles.container}
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      >
        <ScrollView contentContainerStyle={styles.scrollContent}>
          {/* Client Info (read-only) */}
          <View style={styles.infoCard}>
            <View style={styles.infoRow}>
              <Text style={styles.infoLabel}>Nazwa</Text>
              <Text style={styles.infoValue}>{client.name || 'Nieznany'}</Text>
            </View>
            <View style={styles.infoRow}>
              <Text style={styles.infoLabel}>Telefon</Text>
              <Text style={styles.infoValue}>{client.phone}</Text>
            </View>
            <Text style={styles.infoHint}>
              Nazwa pobierana jest z kontaktów w telefonie
            </Text>
          </View>

          {/* Editable Form */}
          <View style={styles.form}>
            {/* Address */}
            <View style={styles.fieldContainer}>
              <Text style={styles.label}>Adres</Text>
              <TextInput
                style={[styles.input, styles.textArea]}
                placeholder="ul. Testowa 1, 00-001 Warszawa"
                placeholderTextColor={colors.textTertiary}
                value={address}
                onChangeText={setAddress}
                multiline
                numberOfLines={2}
                maxLength={500}
              />
            </View>

            {/* Notes */}
            <View style={styles.fieldContainer}>
              <Text style={styles.label}>Opis / Notatki</Text>
              <TextInput
                style={[styles.input, styles.textAreaLarge]}
                placeholder="np. trudny klient, preferuje kontakt SMS..."
                placeholderTextColor={colors.textTertiary}
                value={notes}
                onChangeText={setNotes}
                multiline
                numberOfLines={4}
                maxLength={1000}
              />
            </View>

            {/* Save Button */}
            <TouchableOpacity
              style={[
                styles.submitButton,
                (!hasChanges || loading) && styles.submitButtonDisabled,
              ]}
              onPress={handleSave}
              disabled={!hasChanges || loading}
            >
              {loading ? (
                <ActivityIndicator color={colors.textInverse} />
              ) : (
                <Text style={styles.submitButtonText}>Zapisz zmiany</Text>
              )}
            </TouchableOpacity>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
};

const createStyles = (
  colors: ReturnType<typeof import('@/contexts/ThemeContext').useTheme>['colors']
) =>
  StyleSheet.create({
    safeArea: {
      flex: 1,
      backgroundColor: colors.background,
    },
    container: {
      flex: 1,
    },
    scrollContent: {
      padding: spacing.lg,
      paddingBottom: spacing.xxxl,
    },

    // Info card (read-only section)
    infoCard: {
      backgroundColor: colors.surface,
      borderRadius: radius.lg,
      padding: spacing.lg,
      marginBottom: spacing.lg,
    },
    infoRow: {
      flexDirection: 'row',
      justifyContent: 'space-between',
      alignItems: 'center',
      marginBottom: spacing.sm,
    },
    infoLabel: {
      fontSize: typography.sm,
      color: colors.textSecondary,
    },
    infoValue: {
      fontSize: typography.base,
      fontWeight: typography.semibold,
      color: colors.textPrimary,
    },
    infoHint: {
      fontSize: typography.xs,
      color: colors.textTertiary,
      fontStyle: 'italic',
      marginTop: spacing.sm,
      textAlign: 'center',
    },

    // Form
    form: {
      backgroundColor: colors.surface,
      borderRadius: radius.lg,
      padding: spacing.lg,
    },
    fieldContainer: {
      marginBottom: spacing.lg,
    },
    label: {
      fontSize: typography.sm,
      fontWeight: typography.semibold,
      color: colors.textSecondary,
      marginBottom: spacing.sm,
    },
    input: {
      borderWidth: 1,
      borderColor: colors.border,
      borderRadius: radius.md,
      paddingVertical: spacing.md,
      paddingHorizontal: spacing.md,
      fontSize: typography.base,
      color: colors.textPrimary,
      backgroundColor: colors.background,
    },
    textArea: {
      minHeight: 80,
      textAlignVertical: 'top',
    },
    textAreaLarge: {
      minHeight: 120,
      textAlignVertical: 'top',
    },

    // Submit button
    submitButton: {
      backgroundColor: colors.primary,
      paddingVertical: spacing.md,
      borderRadius: radius.lg,
      alignItems: 'center',
      marginTop: spacing.sm,
    },
    submitButtonDisabled: {
      opacity: 0.6,
    },
    submitButtonText: {
      color: colors.textInverse,
      fontSize: typography.base,
      fontWeight: typography.semibold,
    },
  });
