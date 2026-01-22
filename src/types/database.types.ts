/**
 * Database types for Supabase tables
 */

export type CallLogType = 'missed' | 'completed';
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
  client_id: string;
  employee_id: string | null;
  type: CallLogType;
  status: CallLogStatus;
  timestamp: string;
  reservation_by: string | null;
  reservation_at: string | null;
  created_at: string;
  updated_at: string;
}

export interface VoiceReport {
  id: string;
  call_log_id: string;
  audio_url: string | null;
  transcription: string | null;
  ai_summary: string | null;
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
    };
  };
}
