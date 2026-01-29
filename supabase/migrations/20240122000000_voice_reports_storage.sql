-- Migration: Voice Reports Storage Bucket and Policies
-- Creates storage bucket for voice reports and configures RLS policies
-- Note: App uses anon key, so policies must allow anon role

-- Create the voice-reports bucket if it doesn't exist
INSERT INTO storage.buckets (id, name, public)
VALUES ('voice-reports', 'voice-reports', true)
ON CONFLICT (id) DO NOTHING;

-- Policy: Allow anon users to upload files (app uses anon key)
CREATE POLICY "Allow anon uploads"
ON storage.objects
FOR INSERT
TO anon
WITH CHECK (bucket_id = 'voice-reports');

-- Policy: Allow anon users to update files
CREATE POLICY "Allow anon updates"
ON storage.objects
FOR UPDATE
TO anon
USING (bucket_id = 'voice-reports');

-- Policy: Allow public read access (for audio playback)
CREATE POLICY "Allow public read"
ON storage.objects
FOR SELECT
TO public
USING (bucket_id = 'voice-reports');

-- Policy: Allow anon users to delete files
CREATE POLICY "Allow anon deletes"
ON storage.objects
FOR DELETE
TO anon
USING (bucket_id = 'voice-reports');

-- Also ensure voice_reports table exists (if not created yet)
CREATE TABLE IF NOT EXISTS voice_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    call_log_id UUID REFERENCES call_logs(id) ON DELETE CASCADE,
    audio_url TEXT,
    transcription TEXT,
    ai_summary TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- RLS for voice_reports table
ALTER TABLE voice_reports ENABLE ROW LEVEL SECURITY;

-- Allow all operations for authenticated users (dev policy)
CREATE POLICY "Allow all for authenticated users"
ON voice_reports
FOR ALL
TO authenticated
USING (true)
WITH CHECK (true);

-- Allow anon read for development
CREATE POLICY "Allow anon read"
ON voice_reports
FOR SELECT
TO anon
USING (true);

-- Allow anon insert for development (app uses anon key)
CREATE POLICY "Allow anon insert"
ON voice_reports
FOR INSERT
TO anon
WITH CHECK (true);
