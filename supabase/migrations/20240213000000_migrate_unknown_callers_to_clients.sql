-- Migration: Migrate existing unknown callers to clients table
-- This handles call_logs that existed before auto-client-creation was implemented

-- Step 1: Create clients for all unique phone numbers that don't have a client yet
INSERT INTO clients (phone, name, address, notes, created_at, updated_at)
SELECT DISTINCT
  caller_phone,
  NULL as name,
  NULL as address,
  NULL as notes,
  NOW() as created_at,
  NOW() as updated_at
FROM call_logs
WHERE client_id IS NULL
  AND caller_phone IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM clients WHERE clients.phone = call_logs.caller_phone
  );

-- Step 2: Update call_logs to link to the newly created clients
UPDATE call_logs
SET client_id = clients.id
FROM clients
WHERE call_logs.client_id IS NULL
  AND call_logs.caller_phone IS NOT NULL
  AND clients.phone = call_logs.caller_phone;

-- Verify results
SELECT
  COUNT(*) as remaining_unknown_calls
FROM call_logs
WHERE client_id IS NULL
  AND caller_phone IS NOT NULL;
