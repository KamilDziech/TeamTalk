-- Migration: Devices table for push notifications
-- Stores push tokens for each device to enable team notifications

-- Create devices table
CREATE TABLE IF NOT EXISTS devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_name TEXT NOT NULL,           -- Display name for the user/device
    push_token TEXT NOT NULL UNIQUE,   -- Expo push token
    device_info TEXT,                  -- Optional device info (model, OS, etc.)
    last_active_at TIMESTAMPTZ DEFAULT NOW(),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Create index for faster lookups
CREATE INDEX IF NOT EXISTS idx_devices_push_token ON devices(push_token);
CREATE INDEX IF NOT EXISTS idx_devices_user_name ON devices(user_name);

-- Enable RLS
ALTER TABLE devices ENABLE ROW LEVEL SECURITY;

-- Allow anon to insert (register device)
CREATE POLICY "devices_anon_insert" ON devices
FOR INSERT TO anon WITH CHECK (true);

-- Allow anon to update (refresh token)
CREATE POLICY "devices_anon_update" ON devices
FOR UPDATE TO anon USING (true);

-- Allow anon to read (for sending notifications)
CREATE POLICY "devices_anon_select" ON devices
FOR SELECT TO anon USING (true);

-- Trigger to update updated_at
CREATE OR REPLACE FUNCTION update_devices_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER devices_updated_at_trigger
    BEFORE UPDATE ON devices
    FOR EACH ROW
    EXECUTE FUNCTION update_devices_updated_at();
