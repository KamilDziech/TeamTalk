# TeamTalk

System CRM AI dla zespołu instalatorskiego - aplikacja mobilna do zarządzania połączeniami, klientami i notatkami głosowymi z AI.

## Funkcje

- **Wspólna Kolejka** - Wszystkie nieodebrane połączenia widoczne dla całego zespołu
- **Etykiety Adresatów** - Informacja "Do: Kamil, Marcin" pokazująca kto odbierał
- **System rezerwacji** - Rezerwowanie połączeń do oddzwonienia (Realtime sync między urządzeniami)
- **Notatki głosowe** - Nagrywanie i transkrypcja (Whisper)
- **Historia rozmów** - Pełna historia z możliwością odsłuchania i przeszukiwania
- **Dual SIM** - Automatyczna detekcja kart SIM, synchronizacja tylko połączeń z karty służbowej

## Wymagania

- Node.js 18+
- npm lub yarn
- Expo CLI (`npm install -g expo-cli`)
- EAS CLI (`npm install -g eas-cli`)
- Konto Expo (expo.dev)

## Instalacja

```bash
# Klonuj repozytorium
git clone <repo-url>
cd TeamTalk

# Zainstaluj zależności
npm install

# Skopiuj plik środowiskowy
cp .env.example .env
# Uzupełnij klucze API w .env
```

## Konfiguracja zmiennych środowiskowych

Utwórz plik `.env` w katalogu głównym:

```env
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-anon-key
OPENAI_API_KEY=sk-your-openai-key
```

## Uruchomienie (Development)

```bash
# Uruchom serwer deweloperski
npm start

# Lub z Expo Go
npx expo start
```

## Budowanie aplikacji (EAS Build)

### Jednorazowa konfiguracja

```bash
# Zaloguj się do Expo
npx eas login

# Skonfiguruj sekrety w EAS (wykonaj raz)
npx eas secret:create --name SUPABASE_URL --value "https://xxx.supabase.co" --scope project
npx eas secret:create --name SUPABASE_ANON_KEY --value "xxx" --scope project
npx eas secret:create --name OPENAI_API_KEY --value "sk-xxx" --scope project
```

### Budowanie APK (do testów)

```bash
# APK do testów wewnętrznych (preview)
npx eas build --platform android --profile preview

# Po zakończeniu pobierz APK z linku w terminalu lub z expo.dev
```

### Budowanie AAB (do Google Play)

```bash
# Bundle do publikacji w Google Play
npx eas build --platform android --profile production
```

### Profile budowania

| Profil | Typ | Użycie |
|--------|-----|--------|
| `development` | APK (debug) | Rozwój z dev-client |
| `preview` | APK | Testy wewnętrzne, wysyłka do wspólników |
| `production` | AAB | Publikacja w Google Play |

## Struktura projektu

```
TeamTalk/
├── src/
│   ├── api/           # Konfiguracja Supabase
│   ├── components/    # Komponenty React Native
│   ├── contexts/      # Context API (Auth)
│   ├── hooks/         # Custom hooks
│   ├── navigation/    # React Navigation
│   ├── screens/       # Ekrany aplikacji
│   ├── services/      # Logika biznesowa
│   ├── styles/        # Theme i style
│   └── types/         # TypeScript types
├── supabase/
│   └── migrations/    # Migracje SQL
├── assets/            # Ikony i splash screen
├── app.config.js      # Konfiguracja Expo
├── eas.json           # Konfiguracja EAS Build
└── package.json
```

## Baza danych (Supabase)

### Uruchamianie migracji

Wklej SQL z plików w `supabase/migrations/` do SQL Editor w panelu Supabase:
https://supabase.com/dashboard/project/YOUR_PROJECT_ID/sql

### Tabele

- `clients` - Dane klientów
- `call_logs` - Historia połączeń (z kolumną `recipients` - lista adresatów)
- `voice_reports` - Notatki głosowe z transkrypcją
- `profiles` - Profile użytkowników (imiona wyświetlane w etykietach "Do:")
- `devices` - Tokeny push notifications
- `ignored_numbers` - Numery zablokowane/prywatne

## Testowanie

```bash
# Uruchom testy
npm test

# Testy z coverage
npm test -- --coverage

# Testy w trybie watch
npm run test:watch
```

## Aktualizacja wersji

1. Zaktualizuj `version` w `app.config.js`
2. Zaktualizuj `versionCode` w `android` section (inkrementuj o 1)
3. Zbuduj nową wersję: `npx eas build --platform android --profile preview`

## Rozwiązywanie problemów

### Build nie widzi zmiennych środowiskowych
Upewnij się, że sekrety są ustawione w EAS:
```bash
npx eas secret:list
```

### Aplikacja crashuje przy starcie
Sprawdź logi:
```bash
npx expo start --clear
```

### Problemy z uprawnieniami na Androidzie
Aplikacja wymaga uprawnień:
- READ_CALL_LOG - Dostęp do historii połączeń
- READ_CONTACTS - Import kontaktów
- RECORD_AUDIO - Nagrywanie notatek głosowych
- POST_NOTIFICATIONS - Powiadomienia push

## Licencja

Proprietary - Ekotak
