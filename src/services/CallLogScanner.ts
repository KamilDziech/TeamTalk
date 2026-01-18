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

interface CallLogEntry {
  phoneNumber: string;
  type: string; // 'MISSED', 'INCOMING', 'OUTGOING'
  dateTime: number;
  duration: number;
  name: string | null;
}

export class CallLogScanner {
  private lastScanTimestamp: number = 0;

  /**
   * Request required permissions for call log access
   */
  async requestPermissions(): Promise<boolean> {
    try {
      // Request notification permissions
      const { status } = await Notifications.requestPermissionsAsync();

      // CallLog permissions are requested automatically by react-native-call-log
      // when first accessed (declared in AndroidManifest.xml)

      return status === 'granted';
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

      // Get recent call logs (only since last scan)
      const filter = {
        minTimestamp: this.lastScanTimestamp,
      };

      const callLogs: CallLogEntry[] = await CallLogs.load(-1, filter);

      // Filter for MISSED calls only
      const missedCalls = callLogs.filter((call) => call.type === 'MISSED');

      // Process each missed call
      for (const call of missedCalls) {
        const normalizedNumber = this.normalizePhoneNumber(call.phoneNumber);
        const client = clientMap.get(normalizedNumber);

        // PRIVACY: Ignore calls from unknown numbers
        if (!client) {
          console.log(`Ignoring missed call from unknown number: ${call.phoneNumber}`);
          continue;
        }

        // Check if this call already exists in our database
        const { data: existingLog } = await supabase
          .from('call_logs')
          .select('id')
          .eq('client_id', client.id)
          .eq('timestamp', new Date(call.dateTime).toISOString())
          .single();

        // Skip if already logged
        if (existingLog) {
          continue;
        }

        // Create call log entry
        await this.createMissedCallLog(client, call);

        // Send notification
        await this.sendMissedCallNotification(client);
      }

      // Update last scan timestamp
      if (missedCalls.length > 0) {
        this.lastScanTimestamp = Math.max(...missedCalls.map((c) => c.dateTime));
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
    callLogEntry: CallLogEntry
  ): Promise<void> {
    try {
      const { error } = await supabase.from('call_logs').insert({
        client_id: client.id,
        employee_id: null, // Will be set when someone reserves it
        type: 'missed',
        status: 'idle',
        timestamp: new Date(callLogEntry.dateTime).toISOString(),
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
   * Normalize phone number for comparison
   * Removes spaces, dashes, parentheses
   */
  private normalizePhoneNumber(phone: string): string {
    return phone.replace(/[\s\-\(\)]/g, '');
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
