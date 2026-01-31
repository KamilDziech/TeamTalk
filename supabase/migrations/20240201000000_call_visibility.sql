-- ============================================================================
-- CALL VISIBILITY SYSTEM
-- Adds visibility control for call logs to support private/public queue system
-- ============================================================================

-- Add visibility column to call_logs
-- 'public' = visible to all team members (calls from known clients)
-- 'private' = visible only to original receiver (calls from unknown numbers)
ALTER TABLE public.call_logs
ADD COLUMN IF NOT EXISTS visibility VARCHAR(10) NOT NULL DEFAULT 'public'
CHECK (visibility IN ('private', 'public'));

-- Add original_receiver_id column
-- Stores the ID of the employee who first received/registered this call
-- Used for private visibility filtering
ALTER TABLE public.call_logs
ADD COLUMN IF NOT EXISTS original_receiver_id UUID REFERENCES auth.users(id);

-- Add caller_phone column for unknown callers (not in clients table)
-- When client_id is NULL, this stores the phone number for grouping/matching
ALTER TABLE public.call_logs
ADD COLUMN IF NOT EXISTS caller_phone VARCHAR(20);

-- Index for fast visibility filtering in queue queries
CREATE INDEX IF NOT EXISTS idx_call_logs_visibility ON public.call_logs(visibility);

-- Index for filtering by original receiver (private calls)
CREATE INDEX IF NOT EXISTS idx_call_logs_original_receiver
ON public.call_logs(original_receiver_id)
WHERE original_receiver_id IS NOT NULL;

-- Index for caller_phone lookups (for unknown number matching)
CREATE INDEX IF NOT EXISTS idx_call_logs_caller_phone
ON public.call_logs(caller_phone)
WHERE caller_phone IS NOT NULL;

-- Composite index for common queue query pattern
CREATE INDEX IF NOT EXISTS idx_call_logs_visibility_receiver
ON public.call_logs(visibility, original_receiver_id, status);

-- ============================================================================
-- MIGRATE EXISTING DATA
-- Set visibility based on whether client_id exists
-- ============================================================================

-- Existing calls with client_id are from known clients -> public
UPDATE public.call_logs
SET visibility = 'public',
    original_receiver_id = employee_id
WHERE client_id IS NOT NULL
  AND visibility IS NULL;

-- ============================================================================
-- FUNCTION: Auto-publicize when unknown number calls multiple employees
-- ============================================================================

CREATE OR REPLACE FUNCTION public.check_and_publicize_unknown_caller()
RETURNS TRIGGER AS $$
DECLARE
    existing_receiver UUID;
    caller_number VARCHAR(20);
BEGIN
    -- Only process private calls from unknown callers
    IF NEW.visibility = 'private' AND NEW.caller_phone IS NOT NULL THEN
        caller_number := NEW.caller_phone;

        -- Check if this phone number has called a DIFFERENT employee before
        SELECT original_receiver_id INTO existing_receiver
        FROM public.call_logs
        WHERE caller_phone = caller_number
          AND visibility = 'private'
          AND original_receiver_id != NEW.original_receiver_id
        LIMIT 1;

        -- If found, publicize ALL calls from this number
        IF existing_receiver IS NOT NULL THEN
            UPDATE public.call_logs
            SET visibility = 'public'
            WHERE caller_phone = caller_number;

            -- Also set this new call as public
            NEW.visibility := 'public';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to run before inserting new call logs
DROP TRIGGER IF EXISTS trigger_check_publicize_caller ON public.call_logs;
CREATE TRIGGER trigger_check_publicize_caller
    BEFORE INSERT ON public.call_logs
    FOR EACH ROW
    EXECUTE FUNCTION public.check_and_publicize_unknown_caller();

-- ============================================================================
-- COMMENTS
-- ============================================================================

COMMENT ON COLUMN public.call_logs.visibility IS 'Call visibility: public (team-wide) or private (only original receiver)';
COMMENT ON COLUMN public.call_logs.original_receiver_id IS 'Employee who first received/registered this call';
COMMENT ON COLUMN public.call_logs.caller_phone IS 'Phone number for unknown callers (when client_id is NULL)';
COMMENT ON FUNCTION public.check_and_publicize_unknown_caller IS 'Auto-publicizes calls when unknown number contacts multiple employees';
