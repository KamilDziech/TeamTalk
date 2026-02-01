/**
 * AddClientScreen
 *
 * Clean screen for adding new clients to the database.
 * Features:
 * - Import from device contacts
 * - Manual form with phone, name, address, notes
 * - Polish phone number validation (+48)
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
  Modal,
  FlatList,
  SafeAreaView,
  StatusBar,
} from 'react-native';
import * as Contacts from 'expo-contacts';
import { supabase } from '@/api/supabaseClient';
import { useTheme } from '@/contexts/ThemeContext';
import { spacing, radius, typography } from '@/styles/theme';

export const AddClientScreen: React.FC<{ onClientAdded?: () => void }> = ({
  onClientAdded,
}) => {
  const { colors, isDark } = useTheme();
  const styles = createStyles(colors);
  const [phone, setPhone] = useState('');
  const [name, setName] = useState('');
  const [address, setAddress] = useState('');
  const [notes, setNotes] = useState('');
  const [loading, setLoading] = useState(false);

  // Contact picker state
  const [contactsLoading, setContactsLoading] = useState(false);
  const [phoneNumbersModal, setPhoneNumbersModal] = useState(false);
  const [availableNumbers, setAvailableNumbers] = useState<{ number: string; label: string }[]>([]);
  const [selectedContactName, setSelectedContactName] = useState('');

  const validatePhoneNumber = (phoneNumber: string): boolean => {
    const phoneRegex = /^\+48\d{9}$/;
    const cleanPhone = phoneNumber.replace(/[\s\-\(\)]/g, '');
    return phoneRegex.test(cleanPhone);
  };

  const normalizePhoneNumber = (phoneNumber: string): string => {
    let cleaned = phoneNumber.replace(/[\s\-\(\)]/g, '');

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

  // Open contact picker
  const handlePickContact = async () => {
    setContactsLoading(true);

    try {
      const { status } = await Contacts.requestPermissionsAsync();

      if (status !== 'granted') {
        Alert.alert(
          'Brak uprawnień',
          'Aby wybrać kontakt, musisz zezwolić na dostęp do kontaktów w ustawieniach telefonu.'
        );
        setContactsLoading(false);
        return;
      }

      const contact = await Contacts.presentContactPickerAsync();

      if (!contact) {
        setContactsLoading(false);
        return;
      }

      const fullContact = await Contacts.getContactByIdAsync(contact.id, [
        Contacts.Fields.Name,
        Contacts.Fields.FirstName,
        Contacts.Fields.LastName,
        Contacts.Fields.PhoneNumbers,
        Contacts.Fields.Addresses,
      ]);

      if (!fullContact) {
        Alert.alert('Błąd', 'Nie udało się pobrać danych kontaktu.');
        setContactsLoading(false);
        return;
      }

      const contactName = fullContact.name ||
        [fullContact.firstName, fullContact.lastName].filter(Boolean).join(' ') ||
        'Nieznany';

      const phoneNumbers = fullContact.phoneNumbers || [];

      if (phoneNumbers.length === 0) {
        Alert.alert('Brak numeru', 'Ten kontakt nie ma zapisanego numeru telefonu.');
        setContactsLoading(false);
        return;
      }

      if (phoneNumbers.length === 1) {
        fillFormWithContact(contactName, phoneNumbers[0].number || '', fullContact.addresses);
      } else {
        setSelectedContactName(contactName);
        setAvailableNumbers(
          phoneNumbers.map((pn) => ({
            number: pn.number || '',
            label: pn.label || 'Telefon',
          }))
        );
        setPhoneNumbersModal(true);
      }
    } catch (error) {
      console.error('Error picking contact:', error);
      Alert.alert('Błąd', 'Nie udało się wybrać kontaktu.');
    } finally {
      setContactsLoading(false);
    }
  };

  // Fill form with contact data
  const fillFormWithContact = (
    contactName: string,
    phoneNumber: string,
    addresses?: Contacts.Address[]
  ) => {
    setName(contactName);
    setPhone(phoneNumber);

    if (addresses && addresses.length > 0) {
      const addr = addresses[0];
      const addressParts = [
        addr.street,
        addr.postalCode,
        addr.city,
      ].filter(Boolean);
      if (addressParts.length > 0) {
        setAddress(addressParts.join(', '));
      }
    }

    setPhoneNumbersModal(false);
  };

  const handleSelectPhoneNumber = (phoneNumber: string) => {
    fillFormWithContact(selectedContactName, phoneNumber);
  };

  const handleSubmit = async () => {
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
      const { data: existing } = await supabase
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

      const { error } = await supabase
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
        `Klient ${name} został dodany do bazy.`,
        [
          {
            text: 'OK',
            onPress: () => {
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
    <SafeAreaView style={styles.safeArea}>
      <StatusBar barStyle="dark-content" backgroundColor={colors.background} />

      {/* Header removed - title is shown in navigation bar */}

      <KeyboardAvoidingView
        style={styles.container}
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      >
        <ScrollView contentContainerStyle={styles.scrollContent}>
          {/* Import from Contacts - Primary Button */}
          <TouchableOpacity
            style={[styles.contactsButton, contactsLoading && styles.contactsButtonDisabled]}
            onPress={handlePickContact}
            disabled={contactsLoading}
          >
            {contactsLoading ? (
              <ActivityIndicator color={colors.textInverse} size="small" />
            ) : (
              <>
                <Text style={styles.contactsButtonIcon}>📇</Text>
                <Text style={styles.contactsButtonText}>Wybierz z kontaktów</Text>
              </>
            )}
          </TouchableOpacity>

          <Text style={styles.orDivider}>— lub wypełnij ręcznie —</Text>

          {/* Form */}
          <View style={styles.form}>
            {/* Phone Number */}
            <View style={styles.fieldContainer}>
              <Text style={styles.label}>
                Numer telefonu <Text style={styles.required}>*</Text>
              </Text>
              <TextInput
                style={styles.input}
                placeholder="+48123456789"
                placeholderTextColor={colors.textTertiary}
                value={phone}
                onChangeText={setPhone}
                keyboardType="phone-pad"
                autoCapitalize="none"
                maxLength={20}
              />
            </View>

            {/* Name */}
            <View style={styles.fieldContainer}>
              <Text style={styles.label}>
                Nazwa klienta <Text style={styles.required}>*</Text>
              </Text>
              <TextInput
                style={styles.input}
                placeholder="Jan Kowalski"
                placeholderTextColor={colors.textTertiary}
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
              <Text style={styles.label}>Notatki (opcjonalnie)</Text>
              <TextInput
                style={[styles.input, styles.textArea]}
                placeholder="Dodatkowe informacje..."
                placeholderTextColor={colors.textTertiary}
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
                <ActivityIndicator color={colors.textInverse} />
              ) : (
                <Text style={styles.submitButtonText}>Dodaj klienta</Text>
              )}
            </TouchableOpacity>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>

      {/* Phone number selection modal */}
      <Modal
        visible={phoneNumbersModal}
        transparent
        animationType="slide"
        onRequestClose={() => setPhoneNumbersModal(false)}
      >
        <View style={styles.modalOverlay}>
          <View style={styles.modalContent}>
            <Text style={styles.modalTitle}>Wybierz numer telefonu</Text>
            <Text style={styles.modalSubtitle}>
              Kontakt "{selectedContactName}" ma kilka numerów:
            </Text>

            <FlatList
              data={availableNumbers}
              keyExtractor={(item, index) => `${item.number}-${index}`}
              renderItem={({ item }) => (
                <TouchableOpacity
                  style={styles.phoneNumberItem}
                  onPress={() => handleSelectPhoneNumber(item.number)}
                >
                  <Text style={styles.phoneNumberLabel}>{item.label}</Text>
                  <Text style={styles.phoneNumberValue}>{item.number}</Text>
                </TouchableOpacity>
              )}
              style={styles.phoneNumbersList}
            />

            <TouchableOpacity
              style={styles.modalCancelButton}
              onPress={() => setPhoneNumbersModal(false)}
            >
              <Text style={styles.modalCancelText}>Anuluj</Text>
            </TouchableOpacity>
          </View>
        </View>
      </Modal>
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
    container: {
      flex: 1,
    },
    scrollContent: {
      padding: spacing.lg,
      paddingBottom: spacing.xxxl,
    },

    // Contacts button
    contactsButton: {
      backgroundColor: colors.success,
      paddingVertical: spacing.md,
      paddingHorizontal: spacing.lg,
      borderRadius: radius.lg,
      flexDirection: 'row',
      alignItems: 'center',
      justifyContent: 'center',
      marginBottom: spacing.md,
    },
    contactsButtonDisabled: {
      opacity: 0.7,
    },
    contactsButtonIcon: {
      fontSize: 20,
      marginRight: spacing.sm,
    },
    contactsButtonText: {
      color: colors.textInverse,
      fontSize: typography.base,
      fontWeight: typography.semibold,
    },

    orDivider: {
      textAlign: 'center',
      color: colors.textTertiary,
      fontSize: typography.sm,
      marginVertical: spacing.md,
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
    required: {
      color: colors.error,
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

    // Modal
    modalOverlay: {
      flex: 1,
      backgroundColor: 'rgba(0, 0, 0, 0.5)',
      justifyContent: 'flex-end',
    },
    modalContent: {
      backgroundColor: colors.surface,
      borderTopLeftRadius: radius.xl,
      borderTopRightRadius: radius.xl,
      padding: spacing.lg,
      maxHeight: '60%',
    },
    modalTitle: {
      fontSize: typography.lg,
      fontWeight: typography.bold,
      color: colors.textPrimary,
      marginBottom: spacing.xs,
    },
    modalSubtitle: {
      fontSize: typography.sm,
      color: colors.textSecondary,
      marginBottom: spacing.md,
    },
    phoneNumbersList: {
      maxHeight: 200,
    },
    phoneNumberItem: {
      paddingVertical: spacing.md,
      paddingHorizontal: spacing.md,
      backgroundColor: colors.background,
      borderRadius: radius.md,
      marginBottom: spacing.sm,
    },
    phoneNumberLabel: {
      fontSize: typography.xs,
      color: colors.textTertiary,
      textTransform: 'uppercase',
    },
    phoneNumberValue: {
      fontSize: typography.lg,
      color: colors.textPrimary,
      fontWeight: typography.medium,
      marginTop: 2,
    },
    modalCancelButton: {
      paddingVertical: spacing.md,
      alignItems: 'center',
      marginTop: spacing.md,
    },
    modalCancelText: {
      fontSize: typography.base,
      color: colors.textSecondary,
      fontWeight: typography.medium,
    },
  });
