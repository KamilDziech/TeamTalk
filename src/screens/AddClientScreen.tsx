/**
 * AddClientScreen
 *
 * Screen for adding new clients to the database
 * Features:
 * - Form with phone, name, address, notes
 * - Polish phone number validation (+48)
 * - Direct integration with Supabase
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
import { supabase } from '@/api/supabaseClient';

export const AddClientScreen: React.FC<{ onClientAdded?: () => void }> = ({
  onClientAdded,
}) => {
  const [phone, setPhone] = useState('');
  const [name, setName] = useState('');
  const [address, setAddress] = useState('');
  const [notes, setNotes] = useState('');
  const [loading, setLoading] = useState(false);

  const validatePhoneNumber = (phoneNumber: string): boolean => {
    // Polish phone number format: +48XXXXXXXXX (9 digits after +48)
    const phoneRegex = /^\+48\d{9}$/;
    const cleanPhone = phoneNumber.replace(/[\s\-\(\)]/g, '');
    return phoneRegex.test(cleanPhone);
  };

  const normalizePhoneNumber = (phoneNumber: string): string => {
    let cleaned = phoneNumber.replace(/[\s\-\(\)]/g, '');

    // Add +48 if not present
    if (!cleaned.startsWith('+48')) {
      if (cleaned.startsWith('48')) {
        cleaned = '+' + cleaned;
      } else if (cleaned.startsWith('0')) {
        cleaned = '+48' + cleaned.substring(1);
      } else {
        cleaned = '+48' + cleaned;
      }
    }

    return cleaned;
  };

  const handleSubmit = async () => {
    // Validation
    if (!phone.trim()) {
      Alert.alert('Błąd', 'Numer telefonu jest wymagany');
      return;
    }

    if (!name.trim()) {
      Alert.alert('Błąd', 'Nazwa klienta jest wymagana');
      return;
    }

    const normalizedPhone = normalizePhoneNumber(phone);

    if (!validatePhoneNumber(normalizedPhone)) {
      Alert.alert(
        'Błąd',
        'Nieprawidłowy numer telefonu. Użyj formatu: +48XXXXXXXXX lub 0XXXXXXXXX'
      );
      return;
    }

    setLoading(true);

    try {
      // Check if client already exists
      const { data: existing, error: checkError } = await supabase
        .from('clients')
        .select('id, name')
        .eq('phone', normalizedPhone)
        .single();

      if (existing) {
        Alert.alert(
          'Klient istnieje',
          `Ten numer telefonu jest już przypisany do: ${existing.name}`
        );
        setLoading(false);
        return;
      }

      // Create new client
      const { data, error } = await supabase
        .from('clients')
        .insert({
          phone: normalizedPhone,
          name: name.trim(),
          address: address.trim() || null,
          notes: notes.trim() || null,
        })
        .select()
        .single();

      if (error) {
        throw error;
      }

      Alert.alert(
        'Sukces! ✅',
        `Klient ${name} został dodany do bazy.\n\nOd teraz system będzie monitorował połączenia z tego numeru.`,
        [
          {
            text: 'OK',
            onPress: () => {
              // Clear form
              setPhone('');
              setName('');
              setAddress('');
              setNotes('');
              onClientAdded?.();
            },
          },
        ]
      );
    } catch (error) {
      console.error('Error adding client:', error);
      Alert.alert('Błąd', 'Nie udało się dodać klienta. Spróbuj ponownie.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
    >
      <ScrollView contentContainerStyle={styles.scrollContent}>
        <View style={styles.header}>
          <Text style={styles.title}>➕ Dodaj klienta</Text>
          <Text style={styles.subtitle}>
            Dodaj numer do bazy, aby monitorować połączenia
          </Text>
        </View>

        <View style={styles.form}>
          {/* Phone Number */}
          <View style={styles.fieldContainer}>
            <Text style={styles.label}>
              Numer telefonu <Text style={styles.required}>*</Text>
            </Text>
            <TextInput
              style={styles.input}
              placeholder="+48123456789 lub 0123456789"
              value={phone}
              onChangeText={setPhone}
              keyboardType="phone-pad"
              autoCapitalize="none"
              maxLength={20}
            />
            <Text style={styles.hint}>
              Format: +48 i 9 cyfr lub 0 i 9 cyfr
            </Text>
          </View>

          {/* Name */}
          <View style={styles.fieldContainer}>
            <Text style={styles.label}>
              Nazwa klienta <Text style={styles.required}>*</Text>
            </Text>
            <TextInput
              style={styles.input}
              placeholder="Jan Kowalski"
              value={name}
              onChangeText={setName}
              autoCapitalize="words"
              maxLength={255}
            />
          </View>

          {/* Address */}
          <View style={styles.fieldContainer}>
            <Text style={styles.label}>Adres (opcjonalnie)</Text>
            <TextInput
              style={[styles.input, styles.textArea]}
              placeholder="ul. Testowa 1, 00-001 Warszawa"
              value={address}
              onChangeText={setAddress}
              multiline
              numberOfLines={2}
              maxLength={500}
            />
          </View>

          {/* Notes */}
          <View style={styles.fieldContainer}>
            <Text style={styles.label}>Notatki (opcjonalnie)</Text>
            <TextInput
              style={[styles.input, styles.textArea]}
              placeholder="Dodatkowe informacje o kliencie..."
              value={notes}
              onChangeText={setNotes}
              multiline
              numberOfLines={3}
              maxLength={1000}
            />
          </View>

          {/* Submit Button */}
          <TouchableOpacity
            style={[styles.submitButton, loading && styles.submitButtonDisabled]}
            onPress={handleSubmit}
            disabled={loading}
          >
            {loading ? (
              <ActivityIndicator color="#fff" />
            ) : (
              <Text style={styles.submitButtonText}>Dodaj klienta</Text>
            )}
          </TouchableOpacity>
        </View>

        <View style={styles.infoBox}>
          <Text style={styles.infoTitle}>ℹ️ Prywatność</Text>
          <Text style={styles.infoText}>
            System będzie monitorował nieodebrane połączenia TYLKO od numerów
            dodanych do bazy. Wszystkie inne numery są ignorowane.
          </Text>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  scrollContent: {
    padding: 16,
  },
  header: {
    marginBottom: 24,
  },
  title: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#333',
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 16,
    color: '#666',
  },
  form: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  fieldContainer: {
    marginBottom: 20,
  },
  label: {
    fontSize: 16,
    fontWeight: '600',
    color: '#333',
    marginBottom: 8,
  },
  required: {
    color: '#FF3B30',
  },
  input: {
    borderWidth: 1,
    borderColor: '#ddd',
    borderRadius: 8,
    padding: 12,
    fontSize: 16,
    backgroundColor: '#fff',
  },
  textArea: {
    minHeight: 80,
    textAlignVertical: 'top',
  },
  hint: {
    fontSize: 12,
    color: '#999',
    marginTop: 4,
  },
  submitButton: {
    backgroundColor: '#007AFF',
    padding: 16,
    borderRadius: 8,
    alignItems: 'center',
    marginTop: 8,
  },
  submitButtonDisabled: {
    backgroundColor: '#999',
  },
  submitButtonText: {
    color: '#fff',
    fontSize: 18,
    fontWeight: 'bold',
  },
  infoBox: {
    backgroundColor: '#E3F2FD',
    borderRadius: 8,
    padding: 16,
    marginTop: 16,
    borderLeftWidth: 4,
    borderLeftColor: '#2196F3',
  },
  infoTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#1976D2',
    marginBottom: 8,
  },
  infoText: {
    fontSize: 14,
    color: '#555',
    lineHeight: 20,
  },
});
