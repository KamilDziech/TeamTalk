-- Add 'skipped' type to call_logs.type enum
-- This allows marking calls that were completed but user chose to skip adding a note

-- Drop existing check constraint
ALTER TABLE call_logs DROP CONSTRAINT IF EXISTS call_logs_type_check;

-- Add new check constraint with 'skipped' type
ALTER TABLE call_logs ADD CONSTRAINT call_logs_type_check
  CHECK (type IN ('missed', 'completed', 'merged', 'skipped'));

-- Add comment
COMMENT ON COLUMN call_logs.type IS 'Type of call: missed (new), completed (with note), merged (duplicate), skipped (completed without note)';
