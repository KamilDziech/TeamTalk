-- Performance indexes for call_logs table
-- Optimizes reservation queries and client status lookups

-- Index for reservation operations (filtering by status and updating)
CREATE INDEX IF NOT EXISTS idx_call_logs_reservation_composite
  ON call_logs (status, reservation_by, reservation_at)
  WHERE status IN ('missed', 'reserved');

-- Index for client-based status queries
CREATE INDEX IF NOT EXISTS idx_call_logs_client_status
  ON call_logs (client_id, status, timestamp DESC)
  WHERE client_id IS NOT NULL;

-- Index for phone-based queries (unknown callers)
CREATE INDEX IF NOT EXISTS idx_call_logs_phone_status
  ON call_logs (caller_phone, status, timestamp DESC)
  WHERE caller_phone IS NOT NULL;

-- Comment explaining the indexes
COMMENT ON INDEX idx_call_logs_reservation_composite IS 'Speeds up reservation button operations';
COMMENT ON INDEX idx_call_logs_client_status IS 'Speeds up client-based call lookups';
COMMENT ON INDEX idx_call_logs_phone_status IS 'Speeds up phone-based call lookups for unknown callers';

-- RPC function for atomic array_append (avoids race conditions)
CREATE OR REPLACE FUNCTION add_recipient_to_call(call_id UUID, recipient_id UUID)
RETURNS VOID AS $$
BEGIN
  UPDATE call_logs
  SET recipients = CASE
    WHEN recipients IS NULL THEN ARRAY[recipient_id]
    WHEN recipient_id = ANY(recipients) THEN recipients
    ELSE array_append(recipients, recipient_id)
  END
  WHERE id = call_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
