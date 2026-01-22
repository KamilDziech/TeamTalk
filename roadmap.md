# Roadmapa Projektu: TeamTalk - System CRM AI

## Faza 1: Fundament i Baza Danych ✅ UKOŃCZONA
- [x] Konfiguracja projektu React Native (Expo SDK 54) z Supabase.
- [x] Stworzenie struktury bazy danych w Supabase:
    - Tabela `clients` (id, phone, name, address, notes).
    - Tabela `call_logs` (id, client_id, employee_id, type: 'missed'/'completed', status: 'missed'/'reserved'/'completed', timestamp, reservation_by).
    - Tabela `voice_reports` (id, call_log_id, audio_url, transcription, ai_summary).
- [x] Włączenie Supabase Realtime dla tabeli `call_logs`.
- [x] CallLogService z testami TDD (12/12 ✓).
- **Kryterium sukcesu:** ✅ Możliwość ręcznego dodania klienta w panelu Supabase i wyświetlenia go w surowym widoku aplikacji.

## Faza 2: Logika Połączeń i Prywatność ✅ UKOŃCZONA
**Zasada główna:** Prywatność przede wszystkim - monitoruj TYLKO znanych klientów z bazy.

### 2.1 CallLog Scanner ✅
- [x] Implementacja funkcji skanującej systemowy CallLog (READ_CALL_LOG).
- [x] Filtrowanie: wykrywaj nieodebrane TYLKO od numerów z tabeli `clients`.
- [x] Ignorowanie: numery spoza bazy `clients` są pomijane (prywatność).
- [x] Automatyczne dodawanie rekordów do `call_logs` dla nieodebranych od znanych klientów.
- [x] **Mechanizm synchronizacji (zoptymalizowany):**
    - Automatyczne skanowanie co 1 minutę (background interval).
    - Skanowanie natychmiast przy starcie aplikacji (App Bootstrap).
    - Skanowanie przy powrocie z tła (AppState change to 'active').
    - Pull-to-Refresh na liście połączeń - ręczne odświeżanie.
    - Feedback dla użytkownika: spinner + komunikat "Synchronizacja połączeń...".

### 2.2 Zarządzanie Klientami ✅
- [x] Ekran "Dodaj numer do bazy klientów" (formularz: telefon, nazwa, adres, notatki).
- [x] Funkcja szybkiego dodawania numeru jako "klient służbowy" (przycisk ⚡).
- [x] Walidacja numerów telefonów (format polski +48).
- [x] **Integracja z kontaktami telefonu (expo-contacts):**
    - Przycisk "📇 Wybierz z kontaktów telefonu".
    - Automatyczne wypełnianie: imię, nazwisko, numer telefonu, adres.
    - Modal wyboru numeru jeśli kontakt ma kilka numerów.
    - Uprawnienie READ_CONTACTS.

### 2.3 System Powiadomień ✅
- [x] Konfiguracja expo-notifications.
- [x] Powiadomienie po wykryciu nieodebranego: "🔴 Nieodebrane od: [Nazwa Klienta]. Kliknij, aby zarezerwować."
- [x] Kliknięcie powiadomienia otwiera aplikację.
- [ ] Kliknięcie powiadomienia oznacza połączenie jako zarezerwowane (TODO).
- [x] Konfiguracja uprawnień Android (READ_CALL_LOG, POST_NOTIFICATIONS).

### 2.4 Ręczne Notatki ✅
- [x] Zakładka "Notatka" (🎤) z listą połączeń wymagających notatki.
- [x] Lista ostatnich połączeń bez voice_report do wyboru.
- [x] Alert "WYMAGA NOTATKI": połączenia bez voice_report oznaczone czerwonym komunikatem.
- [x] Przycisk "Dodaj notatkę" na karcie połączenia w kolejce.

### 2.5 Grupowanie Nieodebranych ✅
- [x] Grupowanie połączeń po kliencie (jeden klient = jedna karta).
- [x] Licznik prób: "🔔 Klient dzwonił X razy!".
- [x] Łączna liczba prób: "📊 Łącznie prób: X".
- [x] Priorytetyzacja: nieobsłużone (missed) wyświetlane na górze.
- [ ] Identyfikacja pracowników (wymaga systemu auth - przesunięte do Fazy 5).

**Kryterium sukcesu:** ✅
1. ✅ Aplikacja wykrywa nieodebrane TYLKO od klientów z bazy.
2. ✅ Powiadomienie pojawia się po wykryciu nieodebranego od znanego klienta.
3. ✅ Można dodać nowy numer do bazy klientów z poziomu aplikacji (+ szybkie dodanie).
4. ✅ Lista połączeń wyświetla alert "WYMAGA NOTATKI" dla rekordów bez voice_report.
5. ✅ Połączenia od tego samego klienta są grupowane z licznikiem prób.

## Faza 3: Kolejka Nieodebranych i Rezerwacje ✅ UKOŃCZONA
- [x] Ekran główny "Kolejka Kontaktów" z listą nieodebranych połączeń.
- [x] Karta połączenia: nazwa klienta, telefon, ile razy próbował dzwonić.
- [x] **Ulepszony Workflow (Kolejka → Notatka):**

### 3.1 Przepływ Statusów
```
missed (Do obsłużenia)
    ↓ klik [REZERWUJ]
reserved (Zarezerwowane przez Ciebie)
    ├── klik [ZADZWOŃ] → uruchamia dialer systemowy
    ├── klik [WYKONANE] → status: completed, znika z Kolejki → pojawia się w Notatce
    └── klik [UWOLNIJ] → status: missed, karta wraca do stanu pierwotnego
```

### 3.2 UI Karty Połączenia
- **Status: missed** → Żółty przycisk [REZERWUJ]
- **Status: reserved** → Trzy przyciski:
    - [ZADZWOŃ] (niebieski) - uruchamia dialer systemowy z numerem klienta
    - [WYKONANE] (zielony) - oznacza rozmowę jako przeprowadzoną
    - [UWOLNIJ] (szary/czerwony, mniejszy) - zwalnia rezerwację dla wspólnika
- **Status: completed** → Karta znika z Kolejki, pojawia się w zakładce Notatka

### 3.3 Akcje
- [x] **Rezerwuj:** `status='reserved'`, `reservation_by=currentUserId`
- [x] **Zadzwoń:** `Linking.openURL('tel:+48XXXXXXXXX')` - otwiera dialer
- [x] **Wykonane:** `status='completed'`, rekord przenosi się do zakładki Notatka
- [x] **Uwolnij:** `status='missed'`, `reservation_by=null` - karta wraca do puli

### 3.4 Synchronizacja
- [x] Supabase Realtime: rezerwacje i uwolnienia widoczne natychmiast na obu telefonach.
- [x] Grupowe rezerwowanie (wszystkie nieobsłużone od klienta jednym kliknięciem).

### 3.5 Wizualizacja Stanów
- 🔴 Czerwony: do obsłużenia (status: 'missed')
- 🟡 Żółty: zarezerwowane (status: 'reserved')
- 🟢 Zielony: załatwione (status: 'completed')

**Kryterium sukcesu:** ✅ Workflow: missed ↔ reserved → completed. Realtime sync między urządzeniami.

---

## Faza 3.5: Zakładka Notatka ✅ UKOŃCZONA

### Filtrowanie
- [x] Wyświetla TYLKO połączenia o statusie `completed` bez `voice_report` i bez `ai_summary`.

### UI
- [x] Po kliknięciu [WYKONANE] w Kolejce, rekord natychmiast pojawia się tutaj.
- [x] Czerwony wskaźnik "🔴 WYMAGA NOTATKI" na każdej karcie.
- [x] Kliknięcie karty otwiera ekran nagrywania audio (Faza 4).

**Kryterium sukcesu:** ✅ Połączenia completed bez notatki są widoczne z czerwonym alertem.

---

## Faza 4: Moduł Notatek Głosowych i AI ✅ UKOŃCZONA

### 4.1 Nagrywanie Audio ✅
- [x] Instalacja expo-av dla nagrywania audio.
- [x] VoiceRecordingScreen z UI do nagrywania.
- [x] Przycisk nagrywania (start/stop) z timerem.
- [x] Podgląd nagrania przed zapisem.
- [x] Uprawnienia RECORD_AUDIO (Android).

### 4.2 Upload i Storage ✅
- [x] VoiceReportService do obsługi audio.
- [x] Upload audio do Supabase Storage (bucket: voice-reports).
- [x] Generowanie unikalnych nazw plików.

### 4.3 Transkrypcja ✅
- [x] Integracja z OpenAI Whisper API.
- [x] Automatyczna transkrypcja po uploade.
- [x] Obsługa języka polskiego.

### 4.4 Streszczenie AI ✅
- [x] Integracja z Claude API (model: claude-3-haiku).
- [x] Generowanie streszczenia z transkrypcji.
- [x] Format: temat rozmowy, ustalenia, zadania do wykonania.

### 4.5 Tryb Offline ✅
- [x] Kolejkowanie nieudanych uploadów w AsyncStorage.
- [x] Metoda processPendingUploads() do ponowienia.
- [ ] Automatyczny retry po odzyskaniu sieci (TODO).

### 4.6 Integracja z UI ✅
- [x] Modal nagrywania otwiera się z zakładki Notatka.
- [x] Po zapisaniu notatki lista się odświeża.
- [x] Połączenie znika z listy "WYMAGA NOTATKI".

### Konfiguracja wymagana:
```bash
# Dodaj do pliku .env:
OPENAI_API_KEY=sk-xxx
CLAUDE_API_KEY=sk-ant-xxx
```

### Supabase Storage:
```sql
-- Utwórz bucket w Supabase Dashboard:
-- Storage → New bucket → "voice-reports" (public)
```

**Kryterium sukcesu:** ✅ Użytkownik nagrywa notatkę, aplikacja transkrybuje i streszcza audio, alert "WYMAGA NOTATKI" znika.

---

## Faza 4.7: Zakładka Historia ✅ UKOŃCZONA

### Nawigacja
- [x] Piąta ikona w dolnym menu (📋 Historia).
- [x] Nowy ekran HistoryScreen.

### Lista Rozmów
- [x] Wyświetlanie połączeń `completed` posiadających `voice_reports`.
- [x] Sortowanie od najnowszych.
- [x] Karta rozmowy: nazwa klienta, data/godzina, kto obsłużył.

### Prezentacja Notatek
- [x] Streszczenie AI w formie czytelnej listy punktowej.
- [x] Przycisk "▶ Odtwórz" - odsłuchanie nagrania z Supabase Storage.
- [x] Przycisk "Pełna notatka" - rozwijanie pełnej transkrypcji.

### Wyszukiwarka
- [x] Pasek wyszukiwania na górze ekranu.
- [x] Filtrowanie po nazwisku klienta lub słowach kluczowych w streszczeniu.

**Kryterium sukcesu:** ✅ Historia rozmów z możliwością odsłuchania nagrań i przeczytania streszczeń AI.

---

## Faza 5: Powiadomienia Zespołowe i Finalizacja
- [ ] Powiadomienie push do zespołu po dodaniu nowej notatki przez AI.
- [ ] Widok historii notatek dla każdego klienta (timeline).
- [ ] Testy między dwoma telefonami (różni pracownicy).
- [ ] Optymalizacja: Battery optimization handling, background sync.
- **Kryterium sukcesu:** Wspólnik otrzymuje powiadomienie "Jan Kowalski dodał notatkę do rozmowy z [Klient]" natychmiast po przetworzeniu przez AI. System działa stabilnie przez 24h bez crashy.

---

## Definicja MVP (Cel końcowy)
System uznajemy za gotowy, gdy:
1. **Prywatność:** Aplikacja monitoruje TYLKO numerów z bazy `clients`, ignoruje resztę.
2. **Nieodebrane:** Nieodebrane od znanych klientów są wykrywane i widoczne dla całego zespołu.
3. **Rezerwacje:** Można zarezerwować oddzwonienie, unikając dublowania pracy (Realtime sync).
4. **Notatki:** Po rozmowie można ręcznie dodać notatkę głosową, która jest transkrybowana i streszczana przez AI.
5. **Alerty:** Połączenia bez notatek są oznaczone "WYMAGA NOTATKI" do czasu uzupełnienia.
6. **Standardy:** Kod w języku angielskim, interfejs w języku polskim, zmiany w repozytorium Git.
