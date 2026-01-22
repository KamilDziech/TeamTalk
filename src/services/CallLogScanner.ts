/**
 * CallLogScanner Service
 *
 * Privacy-first call log scanning:
 * - Scans Android CallLog for missed calls
 * - Filters ONLY numbers from clients table (ignores unknown numbers)
 * - Creates call_logs entries for missed calls from known clients
 * - Triggers notifications for team
 *
 * SOLID Principles:
 * - Single Responsibility: Only handles call log scanning logic
 * - Dependency Inversion: Depends on Supabase client abstraction
 */

import CallLogs from 'react-native-call-log';
import { supabase } from '@/api/supabaseClient';
import type { Client } from '@/types';
import * as Notifications from 'expo-notifications';
import { PermissionsAndroid, Platform } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';

const LAST_SCAN_KEY = 'calllog_last_scan_timestamp';

interface CallLogEntry {
  phoneNumber: string;
  type: string; // 'MISSED', 'INCOMING', 'OUTGOING'
  dateTime: string | number; // Może być timestamp lub sformatowany string
  duration: number;
  name: string | null;
  timestamp?: number; // Niektóre wersje biblioteki zwracają to pole
}

// Mapowanie polskich skrótów miesięcy na numery (0-indexed)
const POLISH_MONTHS: Record<string, number> = {
  'sty': 0, 'lut': 1, 'mar': 2, 'kwi': 3,
  'maj': 4, 'cze': 5, 'lip': 6, 'sie': 7,
  'wrz': 8, 'paź': 9, 'lis': 10, 'gru': 11,
};

export class CallLogScanner {
  // Timestamp ostatniego skanowania (ładowany z AsyncStorage)
  private lastScanTimestamp: number = 0;
  private initialized: boolean = false;

  /**
   * Inicjalizacja - załaduj lastScanTimestamp z AsyncStorage
   */
  private async initialize(): Promise<void> {
    if (this.initialized) return;

    try {
      const saved = await AsyncStorage.getItem(LAST_SCAN_KEY);
      if (saved) {
        this.lastScanTimestamp = parseInt(saved, 10);
        console.log(`📅 Loaded lastScanTimestamp: ${new Date(this.lastScanTimestamp).toLocaleString()}`);
      } else {
        // Pierwsze uruchomienie - skanuj połączenia z ostatnich 24 godzin
        this.lastScanTimestamp = Date.now() - 24 * 60 * 60 * 1000;
        console.log('📅 First run - scanning calls from last 24 hours');
      }
    } catch (error) {
      console.error('Error loading lastScanTimestamp:', error);
      this.lastScanTimestamp = Date.now() - 24 * 60 * 60 * 1000;
    }

    this.initialized = true;
  }

  /**
   * Reset timestamp i wykonaj pełne skanowanie (ostatnie 7 dni)
   * Używaj do testów lub gdy brakuje połączeń
   */
  async fullRescan(): Promise<void> {
    console.log('🔄 Starting full rescan (last 7 days)...');

    // Reset timestamp do 7 dni wstecz
    this.lastScanTimestamp = Date.now() - 7 * 24 * 60 * 60 * 1000;
    this.initialized = true;

    // Wykonaj skanowanie
    await this.scanMissedCalls();

    console.log('✅ Full rescan completed');
  }

  /**
   * Zapisz lastScanTimestamp do AsyncStorage
   */
  private async saveLastScanTimestamp(): Promise<void> {
    try {
      await AsyncStorage.setItem(LAST_SCAN_KEY, this.lastScanTimestamp.toString());
    } catch (error) {
      console.error('Error saving lastScanTimestamp:', error);
    }
  }

  /**
   * Request required permissions for call log access
   * Android 6.0+ wymaga runtime permission request dla dangerous permissions
   */
  async requestPermissions(): Promise<boolean> {
    try {
      // Request notification permissions
      const { status: notificationStatus } = await Notifications.requestPermissionsAsync();

      if (notificationStatus !== 'granted') {
        console.warn('Notification permissions not granted');
      }

      // Request READ_CALL_LOG permission (required for Android 6.0+)
      if (Platform.OS === 'android') {
        const callLogPermission = await PermissionsAndroid.request(
          PermissionsAndroid.PERMISSIONS.READ_CALL_LOG,
          {
            title: 'Dostęp do historii połączeń',
            message: 'TeamTalk potrzebuje dostępu do historii połączeń, aby wykrywać nieodebrane połączenia od klientów.',
            buttonPositive: 'Zezwól',
            buttonNegative: 'Odmów',
          }
        );

        if (callLogPermission !== PermissionsAndroid.RESULTS.GRANTED) {
          console.warn('READ_CALL_LOG permission not granted:', callLogPermission);
          return false;
        }

        console.log('✅ READ_CALL_LOG permission granted');
      }

      return true;
    } catch (error) {
      console.error('Error requesting permissions:', error);
      return false;
    }
  }

  /**
   * Scan call log for missed calls from known clients
   *
   * Privacy: Only processes numbers that exist in clients table
   */
  async scanMissedCalls(): Promise<void> {
    try {
      // Inicjalizacja - załaduj lastScanTimestamp z AsyncStorage
      await this.initialize();

      // Get all clients from database
      const { data: clients, error: clientsError } = await supabase
        .from('clients')
        .select('*');

      if (clientsError || !clients) {
        console.error('Error fetching clients:', clientsError);
        return;
      }

      // Create map of phone numbers for fast lookup
      const clientMap = new Map<string, Client>();
      clients.forEach((client) => {
        // Normalize phone number (remove spaces, dashes, etc.)
        const normalized = this.normalizePhoneNumber(client.phone);
        clientMap.set(normalized, client);
      });

      console.log(`📋 Loaded ${clients.length} clients. Scanning calls since: ${new Date(this.lastScanTimestamp).toLocaleString()}`);

      // Get recent call logs (only since last scan)
      const filter = {
        minTimestamp: this.lastScanTimestamp,
      };

      const callLogs: CallLogEntry[] = await CallLogs.load(-1, filter);

      // Filter for MISSED calls only
      const missedCalls = callLogs.filter((call) => call.type === 'MISSED');

      console.log(`📞 Found ${missedCalls.length} missed calls since last scan`);

      let newCallsCount = 0;

      // Process each missed call
      for (const call of missedCalls) {
        const normalizedNumber = this.normalizePhoneNumber(call.phoneNumber);
        const client = clientMap.get(normalizedNumber);

        // PRIVACY: Ignore calls from unknown numbers
        if (!client) {
          continue;
        }

        // Parsowanie daty - pomiń wpisy z nieprawidłową datą
        const callDate = this.parseCallLogDate(call.dateTime);
        if (!callDate) {
          console.warn(`Cannot parse date for call from ${call.phoneNumber}:`, call.dateTime);
          continue;
        }

        // Sprawdź czy to połączenie jest nowsze niż ostatni skan
        // (dodatkowe zabezpieczenie przed duplikatami)
        if (callDate.getTime() <= this.lastScanTimestamp) {
          console.log(`⏭️ Skipping old call from ${client.name} (${callDate.toLocaleString()})`);
          continue;
        }

        // Sprawdź czy istnieje połączenie od tego klienta w ciągu ostatnich 2 minut
        // (zapobiega duplikatom z różnic w formatowaniu timestamp)
        const twoMinutesAgo = new Date(callDate.getTime() - 2 * 60 * 1000).toISOString();
        const twoMinutesAfter = new Date(callDate.getTime() + 2 * 60 * 1000).toISOString();

        const { data: existingLogs } = await supabase
          .from('call_logs')
          .select('id, timestamp')
          .eq('client_id', client.id)
          .gte('timestamp', twoMinutesAgo)
          .lte('timestamp', twoMinutesAfter);

        // Skip if similar call already exists
        if (existingLogs && existingLogs.length > 0) {
          console.log(`⏭️ Duplicate call from ${client.name} - already in database`);
          continue;
        }

        // Create call log entry
        await this.createMissedCallLog(client, callDate);

        // Send notification
        await this.sendMissedCallNotification(client);

        console.log(`✅ NEW missed call from: ${client.name} (${call.phoneNumber}) at ${callDate.toLocaleString()}`);
        newCallsCount++;
      }

      // Update last scan timestamp do TERAZ (nie do ostatniego połączenia)
      // To zapobiega ponownemu skanowaniu tych samych połączeń
      this.lastScanTimestamp = Date.now();
      await this.saveLastScanTimestamp();

      if (newCallsCount > 0) {
        console.log(`📊 Added ${newCallsCount} new missed calls to database`);
      }
    } catch (error) {
      console.error('Error scanning call log:', error);
    }
  }

  /**
   * Create a call_logs entry for missed call
   */
  private async createMissedCallLog(
    client: Client,
    callDate: Date
  ): Promise<void> {
    try {
      const { error } = await supabase.from('call_logs').insert({
        client_id: client.id,
        employee_id: null, // Will be set when someone reserves it
        type: 'missed',
        status: 'missed',
        timestamp: callDate.toISOString(),
        reservation_by: null,
        reservation_at: null,
      });

      if (error) {
        console.error('Error creating call log:', error);
      } else {
        console.log(`Created call log for missed call from ${client.name}`);
      }
    } catch (error) {
      console.error('Error in createMissedCallLog:', error);
    }
  }

  /**
   * Send local notification about missed call
   */
  private async sendMissedCallNotification(client: Client): Promise<void> {
    try {
      await Notifications.scheduleNotificationAsync({
        content: {
          title: '🔴 Nieodebrane połączenie',
          body: `Od: ${client.name || client.phone}. Kliknij, aby zarezerwować.`,
          data: { clientId: client.id, type: 'missed_call' },
        },
        trigger: null, // Show immediately
      });
    } catch (error) {
      console.error('Error sending notification:', error);
    }
  }

  /**
   * Parse date from CallLog entry
   * Handles both timestamp (number) and Polish formatted string (e.g. "19 sty 2026 17:32:05")
   */
  private parseCallLogDate(dateTime: string | number): Date | null {
    // Jeśli to timestamp (liczba), użyj bezpośrednio
    if (typeof dateTime === 'number') {
      return new Date(dateTime);
    }

    // Próba parsowania standardowego formatu
    const standardDate = new Date(dateTime);
    if (!isNaN(standardDate.getTime())) {
      return standardDate;
    }

    // Parsowanie polskiego formatu: "19 sty 2026 17:32:05"
    const polishMatch = dateTime.match(/(\d{1,2})\s+(\w{3})\s+(\d{4})\s+(\d{2}):(\d{2}):(\d{2})/);
    if (polishMatch) {
      const [, day, monthStr, year, hours, minutes, seconds] = polishMatch;
      const month = POLISH_MONTHS[monthStr.toLowerCase()];

      if (month !== undefined) {
        return new Date(
          parseInt(year),
          month,
          parseInt(day),
          parseInt(hours),
          parseInt(minutes),
          parseInt(seconds)
        );
      }
    }

    console.warn(`Cannot parse date: ${dateTime}`);
    return null;
  }

  /**
   * Normalize phone number for comparison
   * Removes spaces, dashes, parentheses and country prefix (+48, 0048)
   * Returns last 9 digits for Polish numbers
   */
  private normalizePhoneNumber(phone: string): string {
    // Usuń wszystkie znaki oprócz cyfr
    const digitsOnly = phone.replace(/\D/g, '');

    // Dla polskich numerów zwróć ostatnie 9 cyfr
    // (ignoruje prefiks kraju: 48, 0048)
    if (digitsOnly.length >= 9) {
      return digitsOnly.slice(-9);
    }

    return digitsOnly;
  }

  /**
   * Start periodic scanning (call this on app launch)
   */
  startPeriodicScanning(intervalMinutes: number = 5): void {
    // Initial scan
    this.scanMissedCalls();

    // Periodic scanning
    setInterval(() => {
      this.scanMissedCalls();
    }, intervalMinutes * 60 * 1000);
  }
}

// Export singleton instance
export const callLogScanner = new CallLogScanner();
