-- ============================================================================
-- FIX RLS POLICIES - Remove references to deleted 'visibility' column
-- This fixes the issue where call_logs couldn't be inserted due to
-- broken RLS policies referencing non-existent columns
-- ============================================================================

-- ============================================================================
-- STEP 1: Drop all existing policies on call_logs
-- ============================================================================

DROP POLICY IF EXISTS "Users can view public call logs" ON public.call_logs;
DROP POLICY IF EXISTS "Authenticated users can insert call logs" ON public.call_logs;
DROP POLICY IF EXISTS "Users can update accessible call logs" ON public.call_logs;
DROP POLICY IF EXISTS "Users can delete own call logs" ON public.call_logs;
DROP POLICY IF EXISTS "Authenticated users can view all call logs" ON public.call_logs;
DROP POLICY IF EXISTS "Authenticated users can update call logs" ON public.call_logs;
DROP POLICY IF EXISTS "Authenticated users can delete call logs" ON public.call_logs;

-- ============================================================================
-- STEP 2: Drop all existing policies on voice_reports
-- ============================================================================

DROP POLICY IF EXISTS "Users can view voice reports for accessible calls" ON public.voice_reports;
DROP POLICY IF EXISTS "Authenticated users can insert voice reports" ON public.voice_reports;
DROP POLICY IF EXISTS "Users can update own voice reports" ON public.voice_reports;
DROP POLICY IF EXISTS "Users can delete own voice reports" ON public.voice_reports;
DROP POLICY IF EXISTS "Authenticated users can view voice reports" ON public.voice_reports;
DROP POLICY IF EXISTS "Authenticated users can update voice reports" ON public.voice_reports;
DROP POLICY IF EXISTS "Authenticated users can delete voice reports" ON public.voice_reports;

-- ============================================================================
-- STEP 3: Create correct policies (SHARED DATABASE MODEL)
-- All authenticated users can access all call logs and voice reports
-- ============================================================================

-- call_logs: SELECT policy
CREATE POLICY "Authenticated users can view all call logs"
ON public.call_logs FOR SELECT
TO authenticated
USING (true);

-- call_logs: INSERT policy
CREATE POLICY "Authenticated users can insert call logs"
ON public.call_logs FOR INSERT
TO authenticated
WITH CHECK (true);

-- call_logs: UPDATE policy
CREATE POLICY "Authenticated users can update call logs"
ON public.call_logs FOR UPDATE
TO authenticated
USING (true)
WITH CHECK (true);

-- call_logs: DELETE policy
CREATE POLICY "Authenticated users can delete call logs"
ON public.call_logs FOR DELETE
TO authenticated
USING (true);

-- voice_reports: SELECT policy
CREATE POLICY "Authenticated users can view voice reports"
ON public.voice_reports FOR SELECT
TO authenticated
USING (true);

-- voice_reports: INSERT policy
CREATE POLICY "Authenticated users can insert voice reports"
ON public.voice_reports FOR INSERT
TO authenticated
WITH CHECK (true);

-- voice_reports: UPDATE policy
CREATE POLICY "Authenticated users can update voice reports"
ON public.voice_reports FOR UPDATE
TO authenticated
USING (true)
WITH CHECK (true);

-- voice_reports: DELETE policy
CREATE POLICY "Authenticated users can delete voice reports"
ON public.voice_reports FOR DELETE
TO authenticated
USING (true);

-- ============================================================================
-- STEP 4: Verify RLS is enabled
-- ============================================================================

ALTER TABLE public.call_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.voice_reports ENABLE ROW LEVEL SECURITY;

-- ============================================================================
-- COMMENTS
-- ============================================================================

COMMENT ON TABLE public.call_logs IS
  'Shared call queue - all authenticated users can view and manage all calls';
COMMENT ON TABLE public.voice_reports IS
  'Voice reports for calls - all authenticated users can view and manage';
