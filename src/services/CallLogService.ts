/**
 * CallLogService
 *
 * Service responsible for managing call logs including:
 * - Creating missed call records
 * - Reserving calls for callback
 * - Completing calls
 * - Querying call logs by status
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
   * Creates a new missed call log record
   *
   * @param clientId - The ID of the client who called
   * @param phoneNumber - The phone number of the caller
   * @returns The created call log
   * @throws Error if the database operation fails
   */
  async createMissedCall(clientId: string, phoneNumber: string): Promise<CallLog> {
    const { data, error } = await this.supabase
      .from('call_logs')
      .insert({
        client_id: clientId,
        employee_id: null,
        type: 'missed',
        status: 'idle',
        timestamp: new Date().toISOString(),
        reservation_by: null,
        reservation_at: null,
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
   * Updates the call log status to 'calling' and sets the reservation details
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
        status: 'calling',
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
   * @returns Array of call logs with 'idle' status
   * @throws Error if the query fails
   */
  async getMissedCalls(): Promise<CallLog[]> {
    const { data, error } = await this.supabase
      .from('call_logs')
      .select('*')
      .eq('status', 'idle');

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
      .eq('status', 'calling')
      .eq('reservation_by', employeeId);

    if (error) {
      throw new Error('Failed to fetch reserved calls');
    }

    return data || [];
  }
}
