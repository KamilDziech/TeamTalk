# TeamTalk - Stack Technologiczny

## Przegląd

TeamTalk to mobilna aplikacja CRM z AI dla zespołu instalatorskiego. Aplikacja służy do zarządzania połączeniami, klientami i notatkami głosowymi z automatyczną transkrypcją i streszczaniem.

---

## Frontend (Aplikacja Mobilna)

### Framework
| Technologia | Wersja | Opis |
|-------------|--------|------|
| **React Native** | 0.81.5 | Framework do budowy natywnych aplikacji mobilnych |
| **Expo** | SDK 54 | Platforma ułatwiająca rozwój React Native |
| **TypeScript** | 5.3.3 | Typowany JavaScript dla lepszej jakości kodu |

### Nawigacja
| Biblioteka | Wersja | Zastosowanie |
|------------|--------|--------------|
| `@react-navigation/native` | 7.1.28 | Główny system nawigacji |
| `@react-navigation/native-stack` | 7.10.1 | Nawigacja stosowa (stack) |
| `@react-navigation/bottom-tabs` | 7.10.0 | Dolne menu zakładek |

### UI/UX
| Biblioteka | Zastosowanie |
|------------|--------------|
| `@expo/vector-icons` (MaterialIcons) | Ikony Material Design |
| `expo-linear-gradient` | Gradienty |
| `react-native-safe-area-context` | Bezpieczne obszary ekranu |
| `react-native-screens` | Natywne ekrany dla wydajności |

### Przechowywanie Lokalne
| Biblioteka | Zastosowanie |
|------------|--------------|
| `@react-native-async-storage/async-storage` | Lokalne przechowywanie danych (sesja, ustawienia) |

---

## Backend (Supabase)

### Baza Danych
| Technologia | Opis |
|-------------|------|
| **PostgreSQL** | Relacyjna baza danych (hostowana przez Supabase) |
| **Row Level Security (RLS)** | Polityki bezpieczeństwa na poziomie wierszy |
| **Realtime** | Subskrypcje zmian w czasie rzeczywistym |

### Tabele
```
clients          - Dane klientów (telefon, imię, adres, notatki)
call_logs        - Historia połączeń (status, recipients, timestamp)
voice_reports    - Notatki głosowe (audio_url, transcription)
profiles         - Profile użytkowników (display_name)
devices          - Tokeny push notifications
ignored_numbers  - Numery zablokowane/prywatne
```

### Autentykacja
| Funkcja | Opis |
|---------|------|
| Supabase Auth | Email/hasło |
| Session Persistence | AsyncStorage jako storage provider |
| Auto-login | Automatyczne odświeżanie sesji |

### Storage
| Bucket | Zastosowanie |
|--------|--------------|
| `voice-reports` | Pliki audio notatek głosowych |

### SDK
| Biblioteka | Wersja |
|------------|--------|
| `@supabase/supabase-js` | 2.39.3 |

---

## Integracje AI

### Transkrypcja Audio
| Serwis | Model | Zastosowanie |
|--------|-------|--------------|
| **OpenAI Whisper** | whisper-1 | Transkrypcja mowy na tekst (polski) |

---

## Natywne Moduły (Android)

### Uprawnienia
```xml
READ_PHONE_STATE      - Stan telefonu
READ_CALL_LOG         - Historia połączeń
POST_NOTIFICATIONS    - Powiadomienia push
RECORD_AUDIO          - Nagrywanie audio
READ_CONTACTS         - Odczyt kontaktów
RECEIVE_BOOT_COMPLETED - Uruchomienie po restarcie
VIBRATE               - Wibracje
```

### Moduły Natywne
| Moduł | Zastosowanie |
|-------|--------------|
| `react-native-call-log` | Odczyt systemowej historii połączeń |
| `expo-contacts` | Import kontaktów z telefonu |
| `expo-av` | Nagrywanie i odtwarzanie audio |
| `expo-notifications` | Powiadomienia lokalne i push |
| `expo-file-system` | Operacje na plikach |

---

## Architektura Aplikacji

### Wzorce
| Wzorzec | Zastosowanie |
|---------|--------------|
| **SOLID** | Zasady projektowania serwisów |
| **Master-Detail** | Lista połączeń → Szczegóły |
| **Context API** | Zarządzanie stanem (Auth, Theme) |
| **Service Layer** | Logika biznesowa oddzielona od UI |

### Struktura Katalogów
```
src/
├── api/           # Konfiguracja Supabase
├── components/    # Komponenty React Native
├── contexts/      # Context API (AuthContext, ThemeContext)
├── hooks/         # Custom hooks (usePushNotifications)
├── navigation/    # React Navigation (Stack, Tabs)
├── screens/       # Ekrany aplikacji
├── services/      # Logika biznesowa
│   ├── CallLogService.ts      # CRUD dla call_logs
│   ├── CallLogScanner.ts      # Skanowanie połączeń
│   ├── VoiceReportService.ts  # Notatki głosowe + AI
│   ├── ContactLookupService.ts # Wyszukiwanie kontaktów
│   └── SimDetectionService.ts # Dual SIM
├── styles/        # Theme i wspólne style
└── types/         # TypeScript types
```

### Serwisy
| Serwis | Odpowiedzialność |
|--------|------------------|
| `CallLogService` | Operacje CRUD na call_logs |
| `CallLogScanner` | Skanowanie systemowego CallLog |
| `VoiceReportService` | Upload audio, transkrypcja, streszczanie |
| `ContactLookupService` | Wyszukiwanie nazw w kontaktach |
| `SimDetectionService` | Wykrywanie i filtrowanie Dual SIM |
| `DeviceService` | Rejestracja tokenów push |

---

## Testowanie

### Framework
| Narzędzie | Wersja | Zastosowanie |
|-----------|--------|--------------|
| **Jest** | 29.7.0 | Test runner |
| **jest-expo** | 54.0.0 | Preset dla Expo |
| `@testing-library/react-native` | 12.4.3 | Testowanie komponentów |

### Pokrycie Testów
- `CallLogService` - 17+ testów (TDD)
- `AuthContext` - 6 testów

### Uruchomienie
```bash
npm test              # Uruchom testy
npm run test:watch    # Tryb watch
npm test -- --coverage # Z pokryciem
```

---

## Build i Deploy

### EAS Build (Expo Application Services)
| Profil | Typ | Zastosowanie |
|--------|-----|--------------|
| `development` | APK (debug) | Development z dev-client |
| `preview` | APK | Testy wewnętrzne |
| `production` | AAB | Publikacja w Google Play |

### Komendy
```bash
npx eas build --platform android --profile preview    # APK do testów
npx eas build --platform android --profile production # AAB do sklepu
```

### Sekrety (EAS Secrets)
```bash
SUPABASE_URL       # URL projektu Supabase
SUPABASE_ANON_KEY  # Klucz publiczny Supabase
OPENAI_API_KEY     # Klucz API OpenAI (Whisper)
CLAUDE_API_KEY     # Klucz API Anthropic (Claude)
```

---

## Bezpieczeństwo

### Autentykacja
- Supabase Auth z email/hasło
- Sesja przechowywana w AsyncStorage
- Auto-refresh tokenów

### Autoryzacja (RLS)
- Wszyscy zalogowani użytkownicy widzą wszystkie połączenia (shared database)
- Polityki RLS na tabelach `call_logs`, `voice_reports`

### Dane Wrażliwe
- Klucze API przechowywane w EAS Secrets (nie w kodzie)
- Lokalnie w pliku `.env` (nie commitowany)

---

## Wymagania Systemowe

### Android
- Minimalny SDK: 21 (Android 5.0)
- Docelowy SDK: 34 (Android 14)

### Development
- Node.js 18+
- npm lub yarn
- Expo CLI
- EAS CLI
- Konto Expo (expo.dev)

---

## Monitoring i Debugging

### Logi
- `console.log` z emoji dla łatwej identyfikacji:
  - `📋` - Call logs
  - `📞` - Połączenia
  - `🔄` - Synchronizacja
  - `✅` - Sukces
  - `❌` - Błąd

### Realtime
- Supabase Realtime dla synchronizacji między urządzeniami
- Automatyczne odświeżanie listy po zmianach w bazie

---

## Wersjonowanie

| Komponent | Wersja |
|-----------|--------|
| Aplikacja | 1.0.0 |
| Expo SDK | 54 |
| React Native | 0.81.5 |
| React | 19.1.0 |

---

## Zasoby i Limity (Supabase)

### Plan Free Tier (aktualny)

| Zasób | Limit | Opis |
|-------|-------|------|
| **Baza danych** | 500 MB | Tabele PostgreSQL |
| **Storage** | 1 GB | Pliki audio (voice-reports) |
| **Bandwidth** | 2 GB/miesiąc | Transfer danych |
| **Edge Functions** | 500K wywołań/miesiąc | Serverless functions |
| **Realtime** | 200 połączeń jednocześnie | WebSocket connections |

### Szacunkowe zużycie pamięci

#### Pliki Audio (Notatki Głosowe)
| Parametr | Wartość |
|----------|---------|
| Format | M4A (AAC) |
| Jakość | HIGH_QUALITY (expo-av) |
| Rozmiar | ~1-2 MB/minutę |
| Średnia notatka | 1-3 minuty = **2-6 MB** |

#### Kalkulacja dla 20 notatek dziennie

| Okres | Zużycie Storage | Pozostało (z 1 GB) |
|-------|-----------------|-------------------|
| 1 dzień | ~40-80 MB | ~920-960 MB |
| 1 tydzień | ~280-560 MB | ~440-720 MB |
| 2 tygodnie | ~560 MB - 1.1 GB | ⚠️ Limit! |
| 1 miesiąc | ~1.2-2.4 GB | ❌ Przekroczony |

**Wniosek:** Na planie Free Tier starczy miejsca na **~2-3 tygodnie** przy 20 notatkach dziennie.

#### Baza danych (tekstowe dane)
| Tabela | Szacunkowy rozmiar/rekord |
|--------|---------------------------|
| `call_logs` | ~500 bajtów |
| `voice_reports` | ~2-5 KB (z transkrypcją i summary) |
| `clients` | ~300 bajtów |
| `profiles` | ~200 bajtów |

Przy 20 notatkach dziennie: ~100 KB/dzień = **~3 MB/miesiąc** (baza tekstowa)

**Baza danych nie jest problemem** - 500 MB starczy na lata.

---

### Rozwiązania na większą skalę

#### Opcja 1: Supabase Pro ($25/miesiąc)
| Zasób | Limit |
|-------|-------|
| Database | 8 GB |
| Storage | 100 GB |
| Bandwidth | 250 GB/miesiąc |

**Starczy na:** ~2500 notatek (4+ miesiące przy 20/dzień)

#### Opcja 2: Automatyczne czyszczenie starych nagrań
```sql
-- Usuń nagrania audio starsze niż 30 dni (zachowaj transkrypcje)
UPDATE voice_reports
SET audio_url = NULL
WHERE created_at < NOW() - INTERVAL '30 days';

-- Usuń pliki z Storage (wymaga Edge Function lub skryptu)
```

#### Opcja 3: Kompresja audio
Zmiana jakości nagrywania z `HIGH_QUALITY` na `LOW_QUALITY`:
- Rozmiar: ~0.5 MB/minutę (zamiast 1-2 MB)
- Jakość: Wystarczająca dla transkrypcji mowy

#### Opcja 4: Zewnętrzny storage (S3, Cloudflare R2)
- Cloudflare R2: 10 GB free, potem $0.015/GB
- AWS S3: ~$0.023/GB

---

### Koszty API (zewnętrzne)

#### OpenAI Whisper (transkrypcja)
| Model | Koszt |
|-------|-------|
| whisper-1 | $0.006/minuta |

Przy 20 notatkach × 2 min = 40 min/dzień = **~$0.24/dzień** = **~$7.20/miesiąc**

---

### Podsumowanie kosztów miesięcznych

| Składnik | Plan Free | Plan Pro |
|----------|-----------|----------|
| Supabase | $0 | $25 |
| OpenAI Whisper | ~$7 | ~$7 |
| **RAZEM** | **~$7/miesiąc** | **~$32/miesiąc** |

**Uwaga:** Na planie Free musisz regularnie czyścić stare nagrania audio lub przejść na Pro po 2-3 tygodniach intensywnego użytkowania.

---

### Gdzie przechowywane są dane?

| Dane | Lokalizacja | Retencja |
|------|-------------|----------|
| Pliki audio (.m4a) | Supabase Storage (bucket: `voice-reports`) | Do wyczerpania limitu |
| Transkrypcje | PostgreSQL (kolumna `voice_reports.transcription`) | Bez limitu |
| Historia połączeń | PostgreSQL (tabela `call_logs`) | Bez limitu |
| Dane klientów | PostgreSQL (tabela `clients`) | Bez limitu |

**Tip:** Nawet po usunięciu plików audio, transkrypcje pozostają w bazie danych i zajmują minimalną ilość miejsca.

---

*Ostatnia aktualizacja: 2026-02-05*
