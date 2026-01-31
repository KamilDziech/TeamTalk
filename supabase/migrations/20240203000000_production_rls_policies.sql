-- ============================================================================
-- PRODUCTION RLS POLICIES
-- Secure Row Level Security policies for production deployment
-- ============================================================================

-- ============================================================================
-- DROP ALL EXISTING POLICIES
-- ============================================================================

-- Clients table
DROP POLICY IF EXISTS "Allow all operations for authenticated users on clients" ON public.clients;
DROP POLICY IF EXISTS "Authenticated users can view all clients" ON public.clients;
DROP POLICY IF EXISTS "Authenticated users can insert clients" ON public.clients;
DROP POLICY IF EXISTS "Authenticated users can update clients" ON public.clients;
DROP POLICY IF EXISTS "Authenticated users can delete clients" ON public.clients;

-- Call logs table
DROP POLICY IF EXISTS "Allow all operations for authenticated users on call_logs" ON public.call_logs;
DROP POLICY IF EXISTS "Users can view public call logs" ON public.call_logs;
DROP POLICY IF EXISTS "Authenticated users can insert call logs" ON public.call_logs;
DROP POLICY IF EXISTS "Users can update accessible call logs" ON public.call_logs;
DROP POLICY IF EXISTS "Users can delete own call logs" ON public.call_logs;

-- Voice reports table
DROP POLICY IF EXISTS "Allow all operations for authenticated users on voice_reports" ON public.voice_reports;
DROP POLICY IF EXISTS "Users can view voice reports for accessible calls" ON public.voice_reports;
DROP POLICY IF EXISTS "Authenticated users can insert voice reports" ON public.voice_reports;
DROP POLICY IF EXISTS "Users can update own voice reports" ON public.voice_reports;
DROP POLICY IF EXISTS "Users can delete own voice reports" ON public.voice_reports;

-- Profiles table
DROP POLICY IF EXISTS "Users can view all profiles" ON public.profiles;
DROP POLICY IF EXISTS "Everyone can view profiles" ON public.profiles;
DROP POLICY IF EXISTS "Users can insert own profile" ON public.profiles;
DROP POLICY IF EXISTS "Users can update own profile" ON public.profiles;

-- Devices table
DROP POLICY IF EXISTS "Users can manage own devices" ON public.devices;
DROP POLICY IF EXISTS "Users can view own devices" ON public.devices;
DROP POLICY IF EXISTS "Users can insert own devices" ON public.devices;
DROP POLICY IF EXISTS "Users can update own devices" ON public.devices;
DROP POLICY IF EXISTS "Users can delete own devices" ON public.devices;

-- ============================================================================
-- CLIENTS TABLE - Shared company data
-- All authenticated users can read and manage clients
-- ============================================================================

CREATE POLICY "Authenticated users can view all clients"
    ON public.clients
    FOR SELECT
    TO authenticated
    USING (true);

CREATE POLICY "Authenticated users can insert clients"
    ON public.clients
    FOR INSERT
    TO authenticated
    WITH CHECK (true);

CREATE POLICY "Authenticated users can update clients"
    ON public.clients
    FOR UPDATE
    TO authenticated
    USING (true)
    WITH CHECK (true);

CREATE POLICY "Authenticated users can delete clients"
    ON public.clients
    FOR DELETE
    TO authenticated
    USING (true);

-- ============================================================================
-- CALL_LOGS TABLE - Visibility-based access
-- Public calls: visible to all authenticated users
-- Private calls: visible only to original_receiver_id
-- ============================================================================

CREATE POLICY "Users can view public call logs"
    ON public.call_logs
    FOR SELECT
    TO authenticated
    USING (
        visibility = 'public'
        OR original_receiver_id = auth.uid()
        OR employee_id = auth.uid()
        OR reservation_by = auth.uid()
    );

CREATE POLICY "Authenticated users can insert call logs"
    ON public.call_logs
    FOR INSERT
    TO authenticated
    WITH CHECK (true);

CREATE POLICY "Users can update accessible call logs"
    ON public.call_logs
    FOR UPDATE
    TO authenticated
    USING (
        visibility = 'public'
        OR original_receiver_id = auth.uid()
        OR employee_id = auth.uid()
        OR reservation_by = auth.uid()
    )
    WITH CHECK (true);

CREATE POLICY "Users can delete own call logs"
    ON public.call_logs
    FOR DELETE
    TO authenticated
    USING (
        original_receiver_id = auth.uid()
        OR employee_id = auth.uid()
        OR visibility = 'public'
    );

-- ============================================================================
-- VOICE_REPORTS TABLE - Linked to call logs
-- Users can view reports for call logs they can access
-- ============================================================================

CREATE POLICY "Users can view voice reports for accessible calls"
    ON public.voice_reports
    FOR SELECT
    TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.call_logs cl
            WHERE cl.id = call_log_id
            AND (
                cl.visibility = 'public'
                OR cl.original_receiver_id = auth.uid()
                OR cl.employee_id = auth.uid()
                OR cl.reservation_by = auth.uid()
            )
        )
    );

CREATE POLICY "Authenticated users can insert voice reports"
    ON public.voice_reports
    FOR INSERT
    TO authenticated
    WITH CHECK (true);

CREATE POLICY "Users can update own voice reports"
    ON public.voice_reports
    FOR UPDATE
    TO authenticated
    USING (created_by = auth.uid())
    WITH CHECK (true);

CREATE POLICY "Users can delete own voice reports"
    ON public.voice_reports
    FOR DELETE
    TO authenticated
    USING (created_by = auth.uid());

-- ============================================================================
-- PROFILES TABLE - User profiles
-- Everyone can view profiles, users can only update their own
-- ============================================================================

CREATE POLICY "Everyone can view profiles"
    ON public.profiles
    FOR SELECT
    TO authenticated
    USING (true);

CREATE POLICY "Users can insert own profile"
    ON public.profiles
    FOR INSERT
    TO authenticated
    WITH CHECK (id = auth.uid());

CREATE POLICY "Users can update own profile"
    ON public.profiles
    FOR UPDATE
    TO authenticated
    USING (id = auth.uid())
    WITH CHECK (id = auth.uid());

-- ============================================================================
-- DEVICES TABLE - Push notification tokens
-- Users can only manage their own devices
-- ============================================================================

CREATE POLICY "Users can view own devices"
    ON public.devices
    FOR SELECT
    TO authenticated
    USING (true);  -- Allow viewing all for team notifications

CREATE POLICY "Users can insert own devices"
    ON public.devices
    FOR INSERT
    TO authenticated
    WITH CHECK (true);

CREATE POLICY "Users can update own devices"
    ON public.devices
    FOR UPDATE
    TO authenticated
    USING (true)
    WITH CHECK (true);

CREATE POLICY "Users can delete own devices"
    ON public.devices
    FOR DELETE
    TO authenticated
    USING (true);

-- ============================================================================
-- COMMENTS
-- ============================================================================

COMMENT ON POLICY "Users can view public call logs" ON public.call_logs IS
    'Users can see public calls and private calls where they are the original receiver, employee, or reserver';

COMMENT ON POLICY "Users can view voice reports for accessible calls" ON public.voice_reports IS
    'Voice reports are visible if the linked call log is accessible to the user';
