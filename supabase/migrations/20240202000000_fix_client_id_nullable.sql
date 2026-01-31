-- ============================================================================
-- FIX: Make client_id nullable for unknown callers
-- Required for private visibility system (calls from unknown numbers)
-- ============================================================================

-- Remove NOT NULL constraint from client_id
ALTER TABLE public.call_logs
ALTER COLUMN client_id DROP NOT NULL;

-- Add comment explaining the change
COMMENT ON COLUMN public.call_logs.client_id IS 'Client ID - NULL for unknown callers (private calls)';
