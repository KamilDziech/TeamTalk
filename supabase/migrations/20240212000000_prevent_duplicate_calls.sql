-- Prevent duplicate call logs
-- Uses dedup_key = {client_id|caller_phone}_{timestamp/30s}

-- Add dedup_key column
ALTER TABLE call_logs ADD COLUMN IF NOT EXISTS dedup_key TEXT;

-- Generate dedup_key for existing records
UPDATE call_logs
SET dedup_key = CONCAT(
  COALESCE(client_id::text, caller_phone),
  '_',
  FLOOR(EXTRACT(EPOCH FROM timestamp) / 30)::text
)
WHERE dedup_key IS NULL;

-- Create unique index on dedup_key (allows null for backwards compatibility)
CREATE UNIQUE INDEX IF NOT EXISTS idx_call_logs_dedup_key
  ON call_logs (dedup_key)
  WHERE dedup_key IS NOT NULL;

-- Comment
COMMENT ON COLUMN call_logs.dedup_key IS 'Deduplication key: {client_id|caller_phone}_{timestamp/30s}';
