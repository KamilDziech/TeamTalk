# TeamTalk - Instrukcja Wdrożenia

## 1. Wdrożenie Migracji SQL

Wykonaj wszystkie migracje w Supabase Dashboard → SQL Editor:

```bash
# W kolejności:
1. supabase/migrations/20240115000000_initial_schema.sql
2. supabase/migrations/20240118000000_dev_rls_policies.sql
3. supabase/migrations/20240120000000_workflow_status_update.sql
4. supabase/migrations/20240122000000_voice_reports_storage.sql
5. supabase/migrations/20240122100000_devices_table.sql
6. supabase/migrations/20240125000000_profiles_table.sql
7. supabase/migrations/20240201000000_call_visibility.sql
8. supabase/migrations/20240202000000_fix_client_id_nullable.sql
9. supabase/migrations/20240203000000_production_rls_policies.sql
10. supabase/migrations/20240206000000_shared_database.sql
11. supabase/migrations/20240210000000_audio_cleanup_function.sql ⭐
12. supabase/migrations/20240211000000_performance_indexes.sql
13. supabase/migrations/20240212000000_prevent_duplicate_calls.sql
14. supabase/migrations/20240213000000_migrate_unknown_callers_to_clients.sql ⭐
```

## 2. Konfiguracja Storage Bucket

Utwórz bucket w Supabase Dashboard → Storage:

- **Name**: `voice-reports`
- **Public**: Yes (lub skonfiguruj policies dla autoryzowanych użytkowników)
- **File size limit**: 10 MB

## 3. Deploy Edge Function (Punkt 1 TODO.MD)

Automatyczne czyszczenie audio po 30 dniach:

```bash
cd /home/kamil/projects/ekotak/TeamTalk
npx supabase functions deploy cleanup-audio
```

### Konfiguracja Cron Job

W Supabase Dashboard → Database → Cron Jobs:

1. Create new cron job
2. Name: `cleanup-audio-daily`
3. Schedule: `0 3 * * *` (daily at 3:00 AM)
4. SQL Command:
```sql
SELECT
  net.http_post(
    url:='https://gurlzgvxhogbzvbtcqbp.supabase.co/functions/v1/cleanup-audio',
    headers:='{"Content-Type": "application/json", "Authorization": "Bearer YOUR_SERVICE_ROLE_KEY"}'::jsonb
  ) as request_id;
```

**Ważne**: Zamień `YOUR_SERVICE_ROLE_KEY` na prawdziwy klucz z Settings → API

## 4. Zmienne Środowiskowe

Ustaw w Supabase Dashboard → Settings → API:

```env
SUPABASE_URL=https://gurlzgvxhogbzvbtcqbp.supabase.co
SUPABASE_ANON_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

Dla lokalnego developmentu (.env):
```env
SUPABASE_URL=https://gurlzgvxhogbzvbtcqbp.supabase.co
SUPABASE_ANON_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
OPENAI_API_KEY=sk-proj-...
CLAUDE_API_KEY=sk-ant-api03-...
```

## 5. Build Production APK

```bash
# Development build (with dev tools)
eas build --profile development --platform android

# Production build (release)
eas build --profile production --platform android
```

## 6. Testowanie Funkcji Audio Cleanup

Testuj ręcznie:

```bash
# Test lokalne
npx supabase functions invoke cleanup-audio

# Test produkcyjne
curl -X POST \
  https://gurlzgvxhogbzvbtcqbp.supabase.co/functions/v1/cleanup-audio \
  -H "Authorization: Bearer SERVICE_ROLE_KEY"
```

## 7. Monitorowanie

### Logi Edge Function
Supabase Dashboard → Edge Functions → cleanup-audio → Logs

### Sprawdzenie działania
```sql
-- Ile voice_reports ma audio starsze niż 30 dni
SELECT COUNT(*)
FROM voice_reports
WHERE audio_url IS NOT NULL
  AND created_at < NOW() - INTERVAL '30 days';

-- Ile zostało wyczyszczonych
SELECT COUNT(*)
FROM voice_reports
WHERE audio_url IS NULL
  AND created_at < NOW() - INTERVAL '30 days';
```

## Troubleshooting

### Funkcja nie działa
1. Sprawdź czy migracja `20240210000000_audio_cleanup_function.sql` została wykonana
2. Sprawdź logi Edge Function w Dashboard
3. Zweryfikuj czy bucket `voice-reports` istnieje
4. Sprawdź czy cron job jest aktywny

### Duplikaty połączeń
1. Sprawdź czy migracja `20240212000000_prevent_duplicate_calls.sql` została wykonana
2. Uruchom:
```sql
SELECT dedup_key, COUNT(*)
FROM call_logs
WHERE dedup_key IS NOT NULL
GROUP BY dedup_key
HAVING COUNT(*) > 1;
```

## Bezpieczeństwo

- ✅ RLS policies włączone dla wszystkich tabel
- ✅ Service role key używany tylko server-side (Edge Functions)
- ✅ Anon key dla client-side (aplikacja mobilna)
- ✅ Audio usuwane po 30 dniach, dane zachowane przez 3 lata
