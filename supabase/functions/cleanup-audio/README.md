# Audio Cleanup Edge Function

Automatycznie usuwa pliki audio starsze niż 30 dni z Storage bucket `voice-reports`.

## Funkcjonalność

- 🗑️ Usuwa pliki audio starsze niż 30 dni
- 📝 Zachowuje transkrypcje i notatki w bazie (tylko audio jest usuwane)
- 🔄 Automatyczne uruchamianie przez cron job
- 📊 Loguje szczegóły operacji

## Wdrożenie

### 1. Deploy funkcji do Supabase

```bash
npx supabase functions deploy cleanup-audio
```

### 2. Ustaw zmienne środowiskowe

Funkcja automatycznie używa zmiennych z Supabase:
- `SUPABASE_URL` - automatycznie ustawione
- `SUPABASE_SERVICE_ROLE_KEY` - automatycznie ustawione

### 3. Skonfiguruj Cron Job w Supabase Dashboard

1. Wejdź do Supabase Dashboard → Database → Cron Jobs
2. Kliknij "Create a new cron job"
3. Ustaw:
   - **Name**: `cleanup-audio-daily`
   - **Schedule**: `0 3 * * *` (codziennie o 3:00 AM)
   - **Command**:
   ```sql
   SELECT
     net.http_post(
       url:='https://YOUR_PROJECT_ID.supabase.co/functions/v1/cleanup-audio',
       headers:='{"Content-Type": "application/json", "Authorization": "Bearer YOUR_SERVICE_ROLE_KEY"}'::jsonb
     ) as request_id;
   ```

### 4. Testowanie

Testuj funkcję ręcznie:

```bash
# Test lokalnie
npx supabase functions invoke cleanup-audio

# Test w produkcji
curl -X POST \
  https://YOUR_PROJECT_ID.supabase.co/functions/v1/cleanup-audio \
  -H "Authorization: Bearer YOUR_SERVICE_ROLE_KEY"
```

## Jak to działa

1. Funkcja wywołuje `get_old_voice_reports_for_cleanup(30)` aby znaleźć voice_reports starsze niż 30 dni
2. Dla każdego rekordu:
   - Usuwa plik z Storage bucket `voice-reports`
   - Wywołuje `mark_audio_cleaned(report_id)` aby ustawić `audio_url = NULL`
3. Zwraca podsumowanie: ile plików usunięto, ile błędów

## Wymagania

- Migracja `20240210000000_audio_cleanup_function.sql` musi być wykonana (dodaje funkcje RPC)
- Bucket `voice-reports` musi istnieć w Supabase Storage

## Bezpieczeństwo

- Używa `service_role_key` do autoryzacji (admin permissions)
- Tylko usuwa audio - zachowuje wszystkie inne dane (transcription, ai_summary, etc.)
- Dane klientów zachowane przez 3 lata, tylko audio usuwane po 30 dniach
