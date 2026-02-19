-- TeamTalk Initial Database Schema
-- Creates tables for clients, call_logs, and voice_reports
-- Includes proper indexes, constraints, and RLS policies

-- Enable UUID extension (for backwards compatibility)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================================
-- CLIENTS TABLE
-- Stores information about clients (customers)
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.clients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(255),
    address TEXT,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index for fast phone number lookups
CREATE INDEX idx_clients_phone ON public.clients(phone);

-- Index for name searches
CREATE INDEX idx_clients_name ON public.clients(name) WHERE name IS NOT NULL;

-- ============================================================================
-- CALL_LOGS TABLE
-- Stores call history including missed calls and completed calls
-- Supports reservation system for callbacks
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.call_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID NOT NULL REFERENCES public.clients(id) ON DELETE CASCADE,
    employee_id UUID,
    type VARCHAR(20) NOT NULL CHECK (type IN ('missed', 'completed')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('idle', 'calling', 'completed')),
    timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reservation_by UUID,
    reservation_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index for fetching calls by client
CREATE INDEX idx_call_logs_client_id ON public.call_logs(client_id);

-- Index for fetching calls by status (critical for missed calls queue)
CREATE INDEX idx_call_logs_status ON public.call_logs(status);

-- Index for fetching calls by employee
CREATE INDEX idx_call_logs_employee_id ON public.call_logs(employee_id) WHERE employee_id IS NOT NULL;

-- Index for reservation lookups
CREATE INDEX idx_call_logs_reservation_by ON public.call_logs(reservation_by) WHERE reservation_by IS NOT NULL;

-- Composite index for common queries
CREATE INDEX idx_call_logs_status_timestamp ON public.call_logs(status, timestamp DESC);

-- ============================================================================
-- VOICE_REPORTS TABLE
-- Stores voice notes, transcriptions, and AI summaries
-- ============================================================================
CREATE TABLE IF NOT EXISTS public.voice_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    call_log_id UUID NOT NULL REFERENCES public.call_logs(id) ON DELETE CASCADE,
    audio_url TEXT,
    transcription TEXT,
    ai_summary TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index for fetching reports by call log
CREATE INDEX idx_voice_reports_call_log_id ON public.voice_reports(call_log_id);

-- ============================================================================
-- TRIGGERS
-- Automatically update updated_at timestamp
-- ============================================================================
CREATE OR REPLACE FUNCTION public.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_clients_updated_at
    BEFORE UPDATE ON public.clients
    FOR EACH ROW
    EXECUTE FUNCTION public.update_updated_at_column();

CREATE TRIGGER update_call_logs_updated_at
    BEFORE UPDATE ON public.call_logs
    FOR EACH ROW
    EXECUTE FUNCTION public.update_updated_at_column();

CREATE TRIGGER update_voice_reports_updated_at
    BEFORE UPDATE ON public.voice_reports
    FOR EACH ROW
    EXECUTE FUNCTION public.update_updated_at_column();

-- ============================================================================
-- ROW LEVEL SECURITY (RLS)
-- Enable RLS for all tables (policies to be added based on auth requirements)
-- ============================================================================
ALTER TABLE public.clients ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.call_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.voice_reports ENABLE ROW LEVEL SECURITY;

-- Temporary policy for development: Allow all operations for authenticated users
-- TODO: Refine policies based on employee roles in production
CREATE POLICY "Allow all operations for authenticated users on clients"
    ON public.clients
    FOR ALL
    TO authenticated
    USING (true)
    WITH CHECK (true);

CREATE POLICY "Allow all operations for authenticated users on call_logs"
    ON public.call_logs
    FOR ALL
    TO authenticated
    USING (true)
    WITH CHECK (true);

CREATE POLICY "Allow all operations for authenticated users on voice_reports"
    ON public.voice_reports
    FOR ALL
    TO authenticated
    USING (true)
    WITH CHECK (true);

-- ============================================================================
-- REALTIME
-- Enable realtime for call_logs table (critical for reservation system)
-- ============================================================================
ALTER PUBLICATION supabase_realtime ADD TABLE public.call_logs;

-- ============================================================================
-- COMMENTS
-- Add table and column descriptions
-- ============================================================================
COMMENT ON TABLE public.clients IS 'Stores customer/client information for the installation company';
COMMENT ON TABLE public.call_logs IS 'Tracks all phone calls including missed calls and their reservation status';
COMMENT ON TABLE public.voice_reports IS 'Stores voice notes recorded after calls with AI transcription and summaries';

COMMENT ON COLUMN public.call_logs.status IS 'Call status: idle (not handled), calling (reserved), completed (finished)';
COMMENT ON COLUMN public.call_logs.reservation_by IS 'Employee ID who reserved this call for callback';
COMMENT ON COLUMN public.call_logs.reservation_at IS 'Timestamp when the call was reserved';
