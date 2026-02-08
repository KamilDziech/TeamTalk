-- Ensure dedup_key column exists and has UNIQUE index
-- This is needed for ON CONFLICT (dedup_key) to work

-- Add dedup_key column if it doesn't exist
ALTER TABLE call_logs ADD COLUMN IF NOT EXISTS dedup_key TEXT;

-- Drop existing index if it exists (to recreate it properly)
DROP INDEX IF EXISTS idx_call_logs_dedup_key;

-- Create unique index on dedup_key (partial index - only non-null values)
CREATE UNIQUE INDEX idx_call_logs_dedup_key
  ON call_logs (dedup_key)
  WHERE dedup_key IS NOT NULL;

-- Add comment
COMMENT ON COLUMN call_logs.dedup_key IS 'Deduplication key: {client_id|caller_phone}_{timestamp/30s}. Prevents duplicate call logs when multiple devices receive the same call.';
