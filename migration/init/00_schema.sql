-- TeamTalk Local DB Schema
-- Replaces Supabase with standard PostgreSQL
-- Run on a clean database: psql -U teamtalk -d teamtalk -f schema.sql

-- ============================================================================
-- EXTENSIONS
-- ============================================================================
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================================
-- USERS (replaces Supabase auth.users)
-- ============================================================================
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);

-- ============================================================================
-- REFRESH TOKENS
-- ============================================================================
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token TEXT NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);

-- ============================================================================
-- PROFILES
-- ============================================================================
CREATE TABLE IF NOT EXISTS profiles (
    id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    display_name TEXT NOT NULL DEFAULT 'Użytkownik',
    is_admin BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_profiles_is_admin ON profiles(is_admin) WHERE is_admin = true;

-- ============================================================================
-- CLIENTS
-- ============================================================================
CREATE TABLE IF NOT EXISTS clients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(255),
    address TEXT,
    notes TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_clients_phone ON clients(phone);
CREATE INDEX idx_clients_name ON clients(name) WHERE name IS NOT NULL;

-- ============================================================================
-- CALL_LOGS
-- ============================================================================
CREATE TABLE IF NOT EXISTS call_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id UUID REFERENCES clients(id) ON DELETE CASCADE,
    employee_id UUID REFERENCES users(id),
    type VARCHAR(20) NOT NULL CHECK (type IN ('missed', 'completed', 'merged', 'skipped')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('missed', 'reserved', 'completed')),
    timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reservation_by UUID REFERENCES users(id),
    reservation_at TIMESTAMPTZ,
    recipients TEXT[] DEFAULT '{}',
    caller_phone VARCHAR(20),
    dedup_key TEXT,
    merged_into_id UUID REFERENCES call_logs(id) ON DELETE SET NULL,
    phone_account_id TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_call_logs_client_id ON call_logs(client_id);
CREATE INDEX idx_call_logs_status ON call_logs(status);
CREATE INDEX idx_call_logs_employee_id ON call_logs(employee_id) WHERE employee_id IS NOT NULL;
CREATE INDEX idx_call_logs_reservation_by ON call_logs(reservation_by) WHERE reservation_by IS NOT NULL;
CREATE INDEX idx_call_logs_status_timestamp ON call_logs(status, timestamp DESC);
CREATE INDEX idx_call_logs_caller_phone ON call_logs(caller_phone) WHERE caller_phone IS NOT NULL;
CREATE UNIQUE INDEX idx_call_logs_dedup_key ON call_logs(dedup_key) WHERE dedup_key IS NOT NULL;
CREATE INDEX idx_call_logs_phone_account_id ON call_logs(phone_account_id);

-- ============================================================================
-- VOICE_REPORTS
-- ============================================================================
CREATE TABLE IF NOT EXISTS voice_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    call_log_id UUID NOT NULL REFERENCES call_logs(id) ON DELETE CASCADE,
    audio_url TEXT,
    transcription TEXT,
    ai_summary TEXT,
    created_by UUID REFERENCES users(id),
    call_count INTEGER DEFAULT 1,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_voice_reports_call_log_id ON voice_reports(call_log_id);

-- ============================================================================
-- DEVICES
-- ============================================================================
CREATE TABLE IF NOT EXISTS devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_name TEXT NOT NULL,
    push_token TEXT NOT NULL UNIQUE,
    device_info TEXT,
    last_active_at TIMESTAMPTZ DEFAULT NOW(),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_devices_push_token ON devices(push_token);
CREATE INDEX idx_devices_user_name ON devices(user_name);

-- ============================================================================
-- TRIGGERS: updated_at
-- ============================================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_profiles_updated_at
    BEFORE UPDATE ON profiles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_clients_updated_at
    BEFORE UPDATE ON clients
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_call_logs_updated_at
    BEFORE UPDATE ON call_logs
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_voice_reports_updated_at
    BEFORE UPDATE ON voice_reports
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_devices_updated_at
    BEFORE UPDATE ON devices
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================================
-- RPC: append_unique_recipient
-- Atomically appends a recipient ID to call_logs.recipients
-- without duplicates, to avoid race conditions.
-- ============================================================================
CREATE OR REPLACE FUNCTION append_unique_recipient(
    p_call_log_id TEXT,
    p_recipient_id TEXT
)
RETURNS void AS $$
BEGIN
    UPDATE call_logs
    SET recipients = array_append(recipients, p_recipient_id)
    WHERE id = p_call_log_id::UUID
      AND NOT (recipients @> ARRAY[p_recipient_id]);
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- RPC: get_old_voice_reports_for_cleanup
-- ============================================================================
CREATE OR REPLACE FUNCTION get_old_voice_reports_for_cleanup(days_old INTEGER DEFAULT 30)
RETURNS TABLE (id UUID, audio_url TEXT, created_at TIMESTAMPTZ) AS $$
BEGIN
    RETURN QUERY
    SELECT vr.id, vr.audio_url, vr.created_at
    FROM voice_reports vr
    WHERE vr.audio_url IS NOT NULL
      AND vr.created_at < NOW() - (days_old || ' days')::INTERVAL;
END;
$$ LANGUAGE plpgsql;
