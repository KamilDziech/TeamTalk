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
  Modal,
  FlatList,
} from 'react-native';
import * as Contacts from 'expo-contacts';
import { supabase } from '@/api/supabaseClient';
import { colors, spacing, radius, typography, shadows, commonStyles } from '@/styles/theme';

interface ClientItem {
  id: string;
  phone: string;
  name: string;
  address?: string;
}

export const AddClientScreen: React.FC<{ onClientAdded?: () => void }> = ({
  onClientAdded,
}) => {
  const [phone, setPhone] = useState('');
  const [name, setName] = useState('');
  const [address, setAddress] = useState('');
  const [notes, setNotes] = useState('');
  const [loading, setLoading] = useState(false);
  const [quickAddLoading, setQuickAddLoading] = useState(false);

  // Stan dla wyboru z kontaktów
  const [contactsLoading, setContactsLoading] = useState(false);
  const [phoneNumbersModal, setPhoneNumbersModal] = useState(false);
  const [availableNumbers, setAvailableNumbers] = useState<{ number: string; label: string }[]>([]);
  const [selectedContactName, setSelectedContactName] = useState('');

  // Stan dla listy klientów
  const [clients, setClients] = useState<ClientItem[]>([]);
  const [clientsLoading, setClientsLoading] = useState(false);
  const [showClientsList, setShowClientsList] = useState(false);

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

  // Otwórz picker kontaktów
  const handlePickContact = async () => {
    setContactsLoading(true);

    try {
      // Poproś o uprawnienia
      const { status } = await Contacts.requestPermissionsAsync();

      if (status !== 'granted') {
        Alert.alert(
          'Brak uprawnień',
          'Aby wybrać kontakt, musisz zezwolić na dostęp do kontaktów w ustawieniach telefonu.'
        );
        setContactsLoading(false);
        return;
      }

      // Otwórz picker kontaktów (natywny UI)
      const contact = await Contacts.presentContactPickerAsync();

      if (!contact) {
        // Użytkownik anulował wybór
        setContactsLoading(false);
        return;
      }

      // Pobierz pełne dane kontaktu
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

      // Ustaw imię i nazwisko
      const contactName = fullContact.name ||
        [fullContact.firstName, fullContact.lastName].filter(Boolean).join(' ') ||
        'Nieznany';

      // Sprawdź numery telefonu
      const phoneNumbers = fullContact.phoneNumbers || [];

      if (phoneNumbers.length === 0) {
        Alert.alert('Brak numeru', 'Ten kontakt nie ma zapisanego numeru telefonu.');
        setContactsLoading(false);
        return;
      }

      // Jeśli jeden numer - wypełnij od razu
      if (phoneNumbers.length === 1) {
        fillFormWithContact(contactName, phoneNumbers[0].number || '', fullContact.addresses);
      } else {
        // Jeśli wiele numerów - pokaż modal wyboru
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

  // Wypełnij formularz danymi z kontaktu
  const fillFormWithContact = (
    contactName: string,
    phoneNumber: string,
    addresses?: Contacts.Address[]
  ) => {
    setName(contactName);
    setPhone(phoneNumber);

    // Opcjonalnie wypełnij adres (pierwszy z listy)
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

  // Obsługa wyboru numeru z modalu
  const handleSelectPhoneNumber = (phoneNumber: string) => {
    fillFormWithContact(selectedContactName, phoneNumber);
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

  // Szybkie dodanie - tylko numer telefonu, nazwa = numer
  const handleQuickAdd = async () => {
    if (!phone.trim()) {
      Alert.alert('Błąd', 'Wpisz numer telefonu');
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

    setQuickAddLoading(true);

    try {
      // Check if client already exists
      const { data: existing } = await supabase
        .from('clients')
        .select('id, name')
        .eq('phone', normalizedPhone)
        .single();

      if (existing) {
        Alert.alert(
          'Klient istnieje',
          `Ten numer jest już przypisany do: ${existing.name}`
        );
        setQuickAddLoading(false);
        return;
      }

      // Create with phone as name
      const { error } = await supabase
        .from('clients')
        .insert({
          phone: normalizedPhone,
          name: `Klient ${normalizedPhone.slice(-4)}`, // np. "Klient 6789"
          address: null,
          notes: 'Dodano przez szybkie dodanie',
        })
        .select()
        .single();

      if (error) throw error;

      Alert.alert(
        'Dodano! ✅',
        `Numer ${normalizedPhone} został dodany jako klient służbowy.\n\nMożesz później uzupełnić dane.`,
        [{ text: 'OK', onPress: () => { setPhone(''); onClientAdded?.(); } }]
      );
    } catch (error) {
      console.error('Error quick adding client:', error);
      Alert.alert('Błąd', 'Nie udało się dodać klienta.');
    } finally {
      setQuickAddLoading(false);
    }
  };

  // Pobierz listę klientów
  const fetchClients = async () => {
    setClientsLoading(true);
    try {
      const { data, error } = await supabase
        .from('clients')
        .select('id, phone, name, address')
        .order('name', { ascending: true });

      if (error) throw error;
      setClients(data || []);
    } catch (error) {
      console.error('Error fetching clients:', error);
      Alert.alert('Błąd', 'Nie udało się pobrać listy klientów.');
    } finally {
      setClientsLoading(false);
    }
  };

  // Usuń klienta z bazy
  const handleDeleteClient = (client: ClientItem) => {
    Alert.alert(
      'Usuń klienta',
      `Czy na pewno chcesz usunąć klienta "${client.name}" (${client.phone})?\n\nTa operacja usunie również wszystkie powiązane połączenia z kolejki.`,
      [
        { text: 'Anuluj', style: 'cancel' },
        {
          text: 'Usuń',
          style: 'destructive',
          onPress: async () => {
            try {
              // Najpierw usuń powiązane call_logs
              await supabase
                .from('call_logs')
                .delete()
                .eq('client_id', client.id);

              // Potem usuń klienta
              const { error } = await supabase
                .from('clients')
                .delete()
                .eq('id', client.id);

              if (error) throw error;

              Alert.alert('Usunięto', `Klient "${client.name}" został usunięty.`);
              fetchClients();
              onClientAdded?.(); // Odśwież inne widoki
            } catch (error) {
              console.error('Error deleting client:', error);
              Alert.alert('Błąd', 'Nie udało się usunąć klienta.');
            }
          },
        },
      ]
    );
  };

  // Usuń wszystkich klientów (na testy)
  const handleDeleteAllClients = () => {
    Alert.alert(
      'Usuń wszystkich klientów',
      'Czy na pewno chcesz usunąć WSZYSTKICH klientów z bazy?\n\nTa operacja jest nieodwracalna i usunie również wszystkie powiązane połączenia.',
      [
        { text: 'Anuluj', style: 'cancel' },
        {
          text: 'Usuń wszystko',
          style: 'destructive',
          onPress: async () => {
            try {
              // Najpierw usuń wszystkie call_logs
              await supabase
                .from('call_logs')
                .delete()
                .gte('id', '00000000-0000-0000-0000-000000000000');

              // Potem usuń wszystkich klientów
              const { error } = await supabase
                .from('clients')
                .delete()
                .gte('id', '00000000-0000-0000-0000-000000000000');

              if (error) throw error;

              Alert.alert('Usunięto', 'Wszyscy klienci zostali usunięci.');
              fetchClients();
              onClientAdded?.();
            } catch (error) {
              console.error('Error deleting all clients:', error);
              Alert.alert('Błąd', 'Nie udało się usunąć klientów.');
            }
          },
        },
      ]
    );
  };

  // Toggle listy klientów
  const handleToggleClientsList = () => {
    if (!showClientsList) {
      fetchClients();
    }
    setShowClientsList(!showClientsList);
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

        {/* Przycisk wyboru z kontaktów */}
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
              <Text style={styles.contactsButtonText}>Wybierz z kontaktów telefonu</Text>
            </>
          )}
        </TouchableOpacity>

        <Text style={styles.orDivider}>— lub wypełnij ręcznie —</Text>

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

            {/* Szybkie dodanie */}
            <TouchableOpacity
              style={[styles.quickAddButton, quickAddLoading && styles.quickAddButtonDisabled]}
              onPress={handleQuickAdd}
              disabled={quickAddLoading || !phone.trim()}
            >
              {quickAddLoading ? (
                <ActivityIndicator color={colors.primary} size="small" />
              ) : (
                <Text style={styles.quickAddButtonText}>
                  ⚡ Szybkie dodanie (tylko numer)
                </Text>
              )}
            </TouchableOpacity>
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
              <ActivityIndicator color={colors.textInverse} />
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

        {/* Sekcja zarządzania klientami */}
        <View style={styles.clientsSection}>
          <TouchableOpacity
            style={styles.toggleClientsButton}
            onPress={handleToggleClientsList}
          >
            <Text style={styles.toggleClientsButtonText}>
              {showClientsList ? '▼' : '▶'} Zarządzaj klientami ({clients.length || '...'})
            </Text>
          </TouchableOpacity>

          {showClientsList && (
            <View style={styles.clientsListContainer}>
              {clientsLoading ? (
                <ActivityIndicator size="small" color={colors.primary} style={styles.clientsLoader} />
              ) : clients.length === 0 ? (
                <Text style={styles.noClientsText}>Brak klientów w bazie</Text>
              ) : (
                <>
                  {clients.map((client) => (
                    <View key={client.id} style={styles.clientItem}>
                      <View style={styles.clientItemInfo}>
                        <Text style={styles.clientItemName}>{client.name}</Text>
                        <Text style={styles.clientItemPhone}>{client.phone}</Text>
                        {client.address && (
                          <Text style={styles.clientItemAddress}>{client.address}</Text>
                        )}
                      </View>
                      <TouchableOpacity
                        style={styles.deleteClientButton}
                        onPress={() => handleDeleteClient(client)}
                      >
                        <Text style={styles.deleteClientButtonText}>🗑️</Text>
                      </TouchableOpacity>
                    </View>
                  ))}

                  {/* Przycisk usuwania wszystkich */}
                  <TouchableOpacity
                    style={styles.deleteAllButton}
                    onPress={handleDeleteAllClients}
                  >
                    <Text style={styles.deleteAllButtonText}>
                      🗑️ Usuń wszystkich klientów (testy)
                    </Text>
                  </TouchableOpacity>
                </>
              )}
            </View>
          )}
        </View>
      </ScrollView>

      {/* Modal wyboru numeru telefonu */}
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
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  container: commonStyles.screen,
  scrollContent: {
    padding: spacing.lg,
  },
  header: {
    marginBottom: spacing.xxl,
  },
  title: {
    ...commonStyles.heading,
    marginBottom: spacing.sm,
  },
  subtitle: {
    fontSize: typography.base,
    color: colors.textSecondary,
  },
  form: {
    ...commonStyles.card,
  },
  fieldContainer: {
    marginBottom: spacing.xl,
  },
  label: {
    ...commonStyles.label,
  },
  required: {
    color: colors.error,
  },
  input: {
    ...commonStyles.input,
  },
  textArea: {
    minHeight: 80,
    textAlignVertical: 'top',
    paddingTop: spacing.md,
  },
  hint: {
    ...commonStyles.caption,
    marginTop: spacing.xs,
  },
  quickAddButton: {
    backgroundColor: colors.primaryLight,
    padding: spacing.md,
    borderRadius: radius.lg,
    alignItems: 'center',
    marginTop: spacing.md,
    borderWidth: 1,
    borderColor: colors.primary,
  },
  quickAddButtonDisabled: {
    opacity: 0.5,
  },
  quickAddButtonText: {
    color: colors.primary,
    fontSize: typography.sm,
    fontWeight: typography.bold,
  },
  submitButton: {
    ...commonStyles.buttonPrimary,
    marginTop: spacing.sm,
  },
  submitButtonDisabled: {
    backgroundColor: colors.textTertiary,
  },
  submitButtonText: {
    ...commonStyles.buttonPrimaryText,
  },
  infoBox: {
    backgroundColor: colors.infoLight,
    borderRadius: radius.lg,
    padding: spacing.lg,
    marginTop: spacing.lg,
    borderLeftWidth: 4,
    borderLeftColor: colors.info,
  },
  infoTitle: {
    fontSize: typography.base,
    fontWeight: typography.bold,
    color: colors.info,
    marginBottom: spacing.sm,
  },
  infoText: {
    fontSize: typography.sm,
    color: colors.textSecondary,
    lineHeight: 20,
  },
  // Style dla wyboru z kontaktów
  contactsButton: {
    backgroundColor: colors.success,
    padding: spacing.lg,
    borderRadius: radius.xl,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.md,
    ...shadows.sm,
  },
  contactsButtonDisabled: {
    opacity: 0.7,
  },
  contactsButtonIcon: {
    fontSize: 24,
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
    marginVertical: spacing.lg,
  },
  // Style dla modalu wyboru numeru
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    justifyContent: 'flex-end',
  },
  modalContent: {
    backgroundColor: colors.white,
    borderTopLeftRadius: radius.xxl,
    borderTopRightRadius: radius.xxl,
    padding: spacing.xl,
    maxHeight: '60%',
  },
  modalTitle: {
    fontSize: typography.xl,
    fontWeight: typography.bold,
    color: colors.textPrimary,
    textAlign: 'center',
    marginBottom: spacing.sm,
  },
  modalSubtitle: {
    fontSize: typography.base,
    color: colors.textSecondary,
    textAlign: 'center',
    marginBottom: spacing.lg,
  },
  phoneNumbersList: {
    maxHeight: 250,
  },
  phoneNumberItem: {
    backgroundColor: colors.background,
    padding: spacing.lg,
    borderRadius: radius.lg,
    marginBottom: spacing.md,
  },
  phoneNumberLabel: {
    fontSize: typography.xs,
    color: colors.textSecondary,
    marginBottom: spacing.xs,
    textTransform: 'uppercase',
  },
  phoneNumberValue: {
    fontSize: typography.lg,
    fontWeight: typography.semibold,
    color: colors.primary,
  },
  modalCancelButton: {
    padding: spacing.lg,
    alignItems: 'center',
    marginTop: spacing.sm,
  },
  modalCancelText: {
    fontSize: typography.base,
    color: colors.textSecondary,
  },
  // Style dla sekcji zarządzania klientami
  clientsSection: {
    marginTop: spacing.xxl,
    marginBottom: spacing.xxxl,
  },
  toggleClientsButton: {
    backgroundColor: colors.surface,
    padding: spacing.lg,
    borderRadius: radius.lg,
    ...shadows.sm,
  },
  toggleClientsButtonText: {
    fontSize: typography.base,
    fontWeight: typography.semibold,
    color: colors.textPrimary,
  },
  clientsListContainer: {
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    marginTop: spacing.sm,
    padding: spacing.md,
    ...shadows.sm,
  },
  clientsLoader: {
    padding: spacing.xl,
  },
  noClientsText: {
    textAlign: 'center',
    color: colors.textTertiary,
    fontSize: typography.base,
    padding: spacing.xl,
  },
  clientItem: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: spacing.md,
    borderBottomWidth: 1,
    borderBottomColor: colors.borderLight,
  },
  clientItemInfo: {
    flex: 1,
  },
  clientItemName: {
    fontSize: typography.base,
    fontWeight: typography.semibold,
    color: colors.textPrimary,
  },
  clientItemPhone: {
    fontSize: typography.sm,
    color: colors.primary,
    marginTop: spacing.xs,
  },
  clientItemAddress: {
    fontSize: typography.xs,
    color: colors.textSecondary,
    marginTop: spacing.xs,
  },
  deleteClientButton: {
    backgroundColor: colors.errorLight,
    padding: spacing.md,
    borderRadius: radius.md,
    marginLeft: spacing.md,
  },
  deleteClientButtonText: {
    fontSize: 18,
  },
  deleteAllButton: {
    backgroundColor: colors.errorLight,
    padding: spacing.lg,
    borderRadius: radius.lg,
    alignItems: 'center',
    marginTop: spacing.lg,
    borderWidth: 1,
    borderColor: colors.error,
    borderStyle: 'dashed',
  },
  deleteAllButtonText: {
    color: colors.error,
    fontSize: typography.sm,
    fontWeight: typography.semibold,
  },
});
