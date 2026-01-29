-- Development RLS Policies
-- Allows anonymous (unauthenticated) access for testing Phase 1
-- TODO: Remove these policies in production!

-- Allow anonymous read access to clients table
CREATE POLICY "Allow anonymous read access to clients"
    ON public.clients
    FOR SELECT
    TO anon
    USING (true);

-- Allow anonymous insert access to clients table (for testing)
CREATE POLICY "Allow anonymous insert access to clients"
    ON public.clients
    FOR INSERT
    TO anon
    WITH CHECK (true);

-- Allow anonymous read access to call_logs table
CREATE POLICY "Allow anonymous read access to call_logs"
    ON public.call_logs
    FOR SELECT
    TO anon
    USING (true);

-- Allow anonymous read access to voice_reports table
CREATE POLICY "Allow anonymous read access to voice_reports"
    ON public.voice_reports
    FOR SELECT
    TO anon
    USING (true);
