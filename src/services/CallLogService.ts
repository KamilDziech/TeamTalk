/**
 * CallLogService
 *
 * Service responsible for managing call logs including:
 * - Creating missed call records
 * - Reserving calls for callback
 * - Completing calls
 * - Querying call logs
 *
 * Follows SOLID principles:
 * - Single Responsibility: Only handles call log business logic
 * - Dependency Inversion: Depends on Supabase client abstraction
 */

import type { SupabaseClient } from '@supabase/supabase-js';
import type { CallLog, Database } from '@/types';

export class CallLogService {
  private supabase: SupabaseClient<Database>;

  constructor(supabaseClient: SupabaseClient<Database>) {
    this.supabase = supabaseClient;
  }

  /**
   * Generate deduplication key for call log
   * Format: {client_id|caller_phone}_{timestamp_rounded_to_30s}
   * This prevents duplicate entries when multiple devices receive the same call
   */
  private generateDedupKey(
    clientId: string | null,
    callerPhone: string,
    timestamp: Date
  ): string {
    const identifier = clientId || callerPhone;
    const timestampInSeconds = Math.floor(timestamp.getTime() / 1000);
    const roundedTimestamp = Math.floor(timestampInSeconds / 30);
    return `${identifier}_${roundedTimestamp}`;
  }

  /**
   * Creates a new missed call log record
   * Uses dedup_key to prevent duplicates when multiple devices receive same call
   *
   * @param clientId - The ID of the client who called
   * @param phoneNumber - The phone number of the caller
   * @param recipientId - The ID of the user who missed this call
   * @returns The created call log
   * @throws Error if the database operation fails
   */
  async createMissedCall(clientId: string, phoneNumber: string, recipientId?: string): Promise<CallLog> {
    const recipients = recipientId ? [recipientId] : [];
    const timestamp = new Date();
    const dedupKey = this.generateDedupKey(clientId, phoneNumber, timestamp);

    const { data, error } = await this.supabase
      .from('call_logs')
      .upsert({
        client_id: clientId,
        employee_id: recipientId || null,
        type: 'missed',
        status: 'missed',
        timestamp: timestamp.toISOString(),
        reservation_by: null,
        reservation_at: null,
        recipients: recipients,
        caller_phone: phoneNumber,
        dedup_key: dedupKey,
      }, {
        onConflict: 'dedup_key',
        ignoreDuplicates: true,
      })
      .select()
      .single();

    if (error || !data) {
      throw new Error('Failed to create missed call log');
    }

    return data;
  }

  /**
   * Reserves a call for callback by an employee
   *
   * Updates the call log status to 'reserved' and sets the reservation details
   *
   * @param callLogId - The ID of the call log to reserve
   * @param employeeId - The ID of the employee reserving the call
   * @returns The updated call log
   * @throws Error if the call is already reserved or update fails
   */
  async reserveCall(callLogId: string, employeeId: string): Promise<CallLog | null> {
    const { data, error } = await this.supabase
      .from('call_logs')
      .update({
        status: 'reserved',
        reservation_by: employeeId,
        reservation_at: new Date().toISOString(),
      })
      .eq('id', callLogId)
      .select()
      .single();

    if (error || !data) {
      throw new Error('Failed to reserve call');
    }

    return data;
  }

  /**
   * Marks a call as completed
   *
   * Updates the call log status to 'completed'
   *
   * @param callLogId - The ID of the call log to complete
   * @returns The updated call log
   * @throws Error if the update fails
   */
  async completeCall(callLogId: string): Promise<CallLog | null> {
    const { data, error } = await this.supabase
      .from('call_logs')
      .update({
        type: 'completed',
        status: 'completed',
      })
      .eq('id', callLogId)
      .select()
      .single();

    if (error || !data) {
      throw new Error('Failed to complete call');
    }

    return data;
  }

  /**
   * Retrieves all missed calls that haven't been reserved yet
   *
   * @returns Array of call logs with 'missed' status
   * @throws Error if the query fails
   */
  async getMissedCalls(): Promise<CallLog[]> {
    const { data, error } = await this.supabase
      .from('call_logs')
      .select('*')
      .eq('status', 'missed');

    if (error) {
      throw new Error('Failed to fetch missed calls');
    }

    return data || [];
  }

  /**
   * Retrieves all calls reserved by a specific employee
   *
   * @param employeeId - The ID of the employee
   * @returns Array of call logs reserved by the employee
   * @throws Error if the query fails
   */
  async getReservedCallsByEmployee(employeeId: string): Promise<CallLog[]> {
    const { data, error } = await this.supabase
      .from('call_logs')
      .select('*')
      .eq('status', 'reserved')
      .eq('reservation_by', employeeId);

    if (error) {
      throw new Error('Failed to fetch reserved calls');
    }

    return data || [];
  }

  /**
   * Retrieves all call logs (shared database - all visible to everyone)
   *
   * @returns Array of all call logs
   * @throws Error if the query fails
   */
  async getAllCallLogs(): Promise<CallLog[]> {
    const { data, error } = await this.supabase
      .from('call_logs')
      .select('*')
      .order('timestamp', { ascending: false });

    if (error) {
      throw new Error('Failed to fetch call logs');
    }

    return data || [];
  }

  /**
   * Adds a recipient to an existing call log
   * Used for aggregation when same number calls multiple employees
   *
   * @param callLogId - The ID of the call log
   * @param recipientId - The ID of the recipient to add
   * @returns The updated call log
   * @throws Error if the update fails
   */
  async addRecipient(callLogId: string, recipientId: string): Promise<CallLog | null> {
    // First get current recipients
    const { data: callLog, error: fetchError } = await this.supabase
      .from('call_logs')
      .select('recipients')
      .eq('id', callLogId)
      .single();

    if (fetchError || !callLog) {
      throw new Error('Failed to fetch call log');
    }

    const currentRecipients = callLog.recipients || [];

    // Don't add duplicate recipients
    if (currentRecipients.includes(recipientId)) {
      return null;
    }

    // Add new recipient
    const updatedRecipients = [...currentRecipients, recipientId];

    const { data, error } = await this.supabase
      .from('call_logs')
      .update({ recipients: updatedRecipients })
      .eq('id', callLogId)
      .select()
      .single();

    if (error || !data) {
      throw new Error('Failed to add recipient');
    }

    return data;
  }
}

