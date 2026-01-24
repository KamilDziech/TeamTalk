-- Migration: Create profiles table for user identity
-- This table stores user display names linked to auth.users

-- Create profiles table
CREATE TABLE IF NOT EXISTS profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    display_name TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Enable RLS
ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;

-- RLS Policies
-- Users can read all profiles (needed to display names in UI)
CREATE POLICY "Profiles are viewable by everyone"
    ON profiles FOR SELECT
    USING (true);

-- Users can update their own profile
CREATE POLICY "Users can update own profile"
    ON profiles FOR UPDATE
    USING (auth.uid() = id);

-- Users can insert their own profile
CREATE POLICY "Users can insert own profile"
    ON profiles FOR INSERT
    WITH CHECK (auth.uid() = id);

-- Create index for faster lookups
CREATE INDEX IF NOT EXISTS idx_profiles_id ON profiles(id);

-- Function to automatically create profile on user signup
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.profiles (id, display_name)
    VALUES (NEW.id, COALESCE(NEW.raw_user_meta_data->>'display_name', 'Użytkownik'));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Trigger to call function on user creation
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- Update call_logs to reference profiles for reservation_by
-- Add foreign key constraint (optional, for data integrity)
-- Note: This assumes reservation_by stores user UUID, not mock ID
COMMENT ON COLUMN call_logs.reservation_by IS 'UUID of user who reserved this call (references profiles.id)';

-- Update voice_reports to track who created them
ALTER TABLE voice_reports ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES profiles(id);
COMMENT ON COLUMN voice_reports.created_by IS 'UUID of user who created this voice report';
