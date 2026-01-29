-- TeamTalk Workflow Status Update Migration
-- Changes status values from ('idle', 'calling', 'completed') to ('missed', 'reserved', 'completed')
-- New workflow:
--   'missed'   - Nieodebrane, do obsłużenia (pokazuje przycisk 'Rezerwuj')
--   'reserved' - Zarezerwowane przez pracownika (pokazuje przycisk 'Oznacz jako wykonane')
--   'completed' - Wykonane (pokazuje notatkę i streszczenie AI)

-- ============================================================================
-- STEP 1: Update existing records to new status values
-- ============================================================================
UPDATE public.call_logs SET status = 'missed' WHERE status = 'idle';
UPDATE public.call_logs SET status = 'reserved' WHERE status = 'calling';

-- ============================================================================
-- STEP 2: Drop old constraint and add new one
-- ============================================================================
ALTER TABLE public.call_logs DROP CONSTRAINT IF EXISTS call_logs_status_check;

ALTER TABLE public.call_logs
ADD CONSTRAINT call_logs_status_check
CHECK (status IN ('missed', 'reserved', 'completed'));

-- ============================================================================
-- STEP 3: Update column comment
-- ============================================================================
COMMENT ON COLUMN public.call_logs.status IS 'Call status: missed (not handled), reserved (someone is calling back), completed (finished with note)';
