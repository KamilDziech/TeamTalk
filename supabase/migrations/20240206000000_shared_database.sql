-- ============================================================================
-- SHARED DATABASE MIGRATION
-- Removes visibility system, adds recipients array for shared call queue
-- All calls are now visible to all authenticated users (shared queue)
-- ============================================================================

-- ============================================================================
-- DROP OLD RLS POLICIES (depend on visibility column)
-- ============================================================================

-- Drop call_logs policies that reference visibility
DROP POLICY IF EXISTS "Users can view public call logs" ON public.call_logs;
DROP POLICY IF EXISTS "Users can view own private call logs" ON public.call_logs;
DROP POLICY IF EXISTS "Users can update accessible call logs" ON public.call_logs;
DROP POLICY IF EXISTS "Users can delete own call logs" ON public.call_logs;
DROP POLICY IF EXISTS "Users can insert call logs" ON public.call_logs;

-- Drop voice_reports policies that reference call_logs.visibility
DROP POLICY IF EXISTS "Users can view voice reports for accessible calls" ON public.voice_reports;
DROP POLICY IF EXISTS "Users can insert voice reports" ON public.voice_reports;
DROP POLICY IF EXISTS "Users can update own voice reports" ON public.voice_reports;
DROP POLICY IF EXISTS "Users can delete own voice reports" ON public.voice_reports;

-- ============================================================================
-- DROP TRIGGERS AND FUNCTIONS
-- ============================================================================

-- Drop visibility trigger and function (no longer needed)
DROP TRIGGER IF EXISTS trigger_check_publicize_caller ON public.call_logs;
DROP FUNCTION IF EXISTS public.check_and_publicize_unknown_caller();

-- ============================================================================
-- ADD NEW COLUMN
-- ============================================================================

-- Add recipients array column (stores user IDs who received this call)
ALTER TABLE public.call_logs
ADD COLUMN IF NOT EXISTS recipients TEXT[] DEFAULT '{}';

-- ============================================================================
-- MIGRATE DATA
-- Move original_receiver_id to recipients array before dropping
-- ============================================================================

UPDATE public.call_logs
SET recipients = ARRAY[original_receiver_id::TEXT]
WHERE original_receiver_id IS NOT NULL
  AND (recipients IS NULL OR recipients = '{}');

-- ============================================================================
-- DROP OLD COLUMNS AND INDEXES
-- ============================================================================

-- Drop indexes first
DROP INDEX IF EXISTS idx_call_logs_visibility;
DROP INDEX IF EXISTS idx_call_logs_visibility_receiver;
DROP INDEX IF EXISTS idx_call_logs_original_receiver;

-- Drop columns (now safe after policies removed)
ALTER TABLE public.call_logs DROP COLUMN IF EXISTS visibility;
ALTER TABLE public.call_logs DROP COLUMN IF EXISTS original_receiver_id;

-- ============================================================================
-- NEW INDEXES
-- ============================================================================

-- GIN index for efficient array queries (contains, overlap)
CREATE INDEX IF NOT EXISTS idx_call_logs_recipients
ON public.call_logs USING GIN(recipients);

-- ============================================================================
-- NEW RLS POLICIES (Shared Database Model)
-- All authenticated users can see all call logs
-- ============================================================================

-- Ensure RLS is enabled
ALTER TABLE public.call_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.voice_reports ENABLE ROW LEVEL SECURITY;

-- Drop new policies first (in case of re-run)
DROP POLICY IF EXISTS "Authenticated users can view all call logs" ON public.call_logs;
DROP POLICY IF EXISTS "Authenticated users can insert call logs" ON public.call_logs;
DROP POLICY IF EXISTS "Authenticated users can update call logs" ON public.call_logs;
DROP POLICY IF EXISTS "Authenticated users can delete call logs" ON public.call_logs;
DROP POLICY IF EXISTS "Authenticated users can view voice reports" ON public.voice_reports;
DROP POLICY IF EXISTS "Authenticated users can insert voice reports" ON public.voice_reports;
DROP POLICY IF EXISTS "Authenticated users can update voice reports" ON public.voice_reports;
DROP POLICY IF EXISTS "Authenticated users can delete voice reports" ON public.voice_reports;

-- call_logs: All authenticated users can view all calls (shared queue)
CREATE POLICY "Authenticated users can view all call logs"
ON public.call_logs FOR SELECT
TO authenticated
USING (true);

-- call_logs: All authenticated users can insert calls
CREATE POLICY "Authenticated users can insert call logs"
ON public.call_logs FOR INSERT
TO authenticated
WITH CHECK (true);

-- call_logs: All authenticated users can update calls (for reservations)
CREATE POLICY "Authenticated users can update call logs"
ON public.call_logs FOR UPDATE
TO authenticated
USING (true)
WITH CHECK (true);

-- call_logs: All authenticated users can delete calls
CREATE POLICY "Authenticated users can delete call logs"
ON public.call_logs FOR DELETE
TO authenticated
USING (true);

-- voice_reports: All authenticated users can view all reports (shared)
CREATE POLICY "Authenticated users can view voice reports"
ON public.voice_reports FOR SELECT
TO authenticated
USING (true);

-- voice_reports: All authenticated users can insert reports
CREATE POLICY "Authenticated users can insert voice reports"
ON public.voice_reports FOR INSERT
TO authenticated
WITH CHECK (true);

-- voice_reports: All authenticated users can update reports
CREATE POLICY "Authenticated users can update voice reports"
ON public.voice_reports FOR UPDATE
TO authenticated
USING (true)
WITH CHECK (true);

-- voice_reports: All authenticated users can delete reports
CREATE POLICY "Authenticated users can delete voice reports"
ON public.voice_reports FOR DELETE
TO authenticated
USING (true);

-- ============================================================================
-- COMMENTS
-- ============================================================================

COMMENT ON COLUMN public.call_logs.recipients IS
  'Array of user IDs who received/missed this call. Used for "Do:" labels in UI.';
