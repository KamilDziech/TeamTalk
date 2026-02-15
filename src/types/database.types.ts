/**
 * Database types for Supabase tables
 */

export type CallLogType = 'missed' | 'completed' | 'merged' | 'skipped';
export type CallLogStatus = 'missed' | 'reserved' | 'completed';

export interface Client {
  id: string;
  phone: string;
  name: string | null;
  address: string | null;
  notes: string | null;
  created_at: string;
  updated_at: string;
}

export interface CallLog {
  id: string;
  client_id: string | null;
  employee_id: string | null;
  type: CallLogType;
  status: CallLogStatus;
  timestamp: string;
  reservation_by: string | null;
  reservation_at: string | null;
  recipients: string[];  // Array of user IDs who received this call
  caller_phone: string | null;
  merged_into_id: string | null;  // ID of the main call this was merged into
  created_at: string;
  updated_at: string;
}

export interface VoiceReport {
  id: string;
  call_log_id: string;
  audio_url: string | null;
  transcription: string | null;
  ai_summary: string | null;
  created_by: string | null;
  call_count: number;  // How many calls were grouped together for this note
  created_at: string;
  updated_at: string;
}

export interface Profile {
  id: string;
  display_name: string;
  created_at: string;
  updated_at: string;
}

export interface Device {
  id: string;
  user_name: string;
  push_token: string;
  device_info: string | null;
  last_active_at: string;
  created_at: string;
  updated_at: string;
}

export interface Database {
  public: {
    Tables: {
      clients: {
        Row: Client;
        Insert: Omit<Client, 'id' | 'created_at' | 'updated_at'>;
        Update: Partial<Omit<Client, 'id' | 'created_at' | 'updated_at'>>;
      };
      call_logs: {
        Row: CallLog;
        Insert: Omit<CallLog, 'id' | 'created_at' | 'updated_at'>;
        Update: Partial<Omit<CallLog, 'id' | 'created_at' | 'updated_at'>>;
      };
      voice_reports: {
        Row: VoiceReport;
        Insert: Omit<VoiceReport, 'id' | 'created_at' | 'updated_at'>;
        Update: Partial<Omit<VoiceReport, 'id' | 'created_at' | 'updated_at'>>;
      };
      devices: {
        Row: Device;
        Insert: Omit<Device, 'id' | 'created_at' | 'updated_at' | 'last_active_at'>;
        Update: Partial<Omit<Device, 'id' | 'created_at' | 'updated_at'>>;
      };
      profiles: {
        Row: Profile;
        Insert: Omit<Profile, 'created_at' | 'updated_at'>;
        Update: Partial<Omit<Profile, 'id' | 'created_at' | 'updated_at'>>;
      };
    };
  };
}
