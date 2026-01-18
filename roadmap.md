# Roadmapa Projektu: TeamTalk - System CRM AI

## Faza 1: Fundament i Baza Danych ✅ UKOŃCZONA
- [x] Konfiguracja projektu React Native (Expo SDK 54) z Supabase.
- [x] Stworzenie struktury bazy danych w Supabase:
    - Tabela `clients` (id, phone, name, address, notes).
    - Tabela `call_logs` (id, client_id, employee_id, type: 'missed'/'completed', timestamp, reservation_by).
    - Tabela `voice_reports` (id, call_log_id, audio_url, transcription, ai_summary).
- [x] Włączenie Supabase Realtime dla tabeli `call_logs`.
- [x] CallLogService z testami TDD (12/12 ✓).
- **Kryterium sukcesu:** ✅ Możliwość ręcznego dodania klienta w panelu Supabase i wyświetlenia go w surowym widoku aplikacji.

## Faza 2: Logika Połączeń i Prywatność (STRATEGIA HYBRYDOWA)
**Zasada główna:** Prywatność przede wszystkim - monitoruj TYLKO znanych klientów z bazy.

### 2.1 CallLog Scanner (Zamiast ciągłego monitoringu)
- [ ] Implementacja funkcji skanującej systemowy CallLog (READ_CALL_LOG).
- [ ] Filtrowanie: wykrywaj nieodebrane TYLKO od numerów z tabeli `clients`.
- [ ] Ignorowanie: numery spoza bazy `clients` są pomijane (prywatność).
- [ ] Automatyczne dodawanie rekordów do `call_logs` dla nieodebranych od znanych klientów.

### 2.2 Zarządzanie Klientami
- [ ] Ekran "Dodaj numer do bazy klientów" (formularz: telefon, nazwa, adres, notatki).
- [ ] Funkcja szybkiego dodawania numeru jako "klient służbowy".
- [ ] Walidacja numerów telefonów (format polski +48).

### 2.3 System Powiadomień
- [ ] Konfiguracja expo-notifications.
- [ ] Powiadomienie po wykryciu nieodebranego: "🔴 Nieodebrane od: [Nazwa Klienta]. Kliknij, aby zarezerwować."
- [ ] Kliknięcie powiadomienia otwiera aplikację i oznacza połączenie jako zarezerwowane.
- [ ] Konfiguracja uprawnień Android (READ_CALL_LOG, POST_NOTIFICATIONS).

### 2.4 Ręczne Notatki (Brak auto-detect końca rozmowy)
- [ ] Duży przycisk na ekranie głównym: "Dodaj notatkę do ostatniej rozmowy".
- [ ] Lista ostatnich połączeń z CallLog (do wyboru właściwej rozmowy).
- [ ] Alert "BRAK NOTATKI": połączenia bez voice_report oznaczone jaskrawoczerwonym komunikatem.

### 2.5 Logika Wspólna (Grupowanie nieodebranych)
- [ ] Detekcja: ten sam klient dzwonił do wielu pracowników i nikt nie odebrał.
- [ ] Złączenie rekordów w jeden wpis z listą pracowników, do których klient próbował dodzwonić.
- [ ] Alert: "Klient [Nazwa] dobijał się do: [Pracownik 1], [Pracownik 2], [Pracownik 3]".

**Kryterium sukcesu:**
1. Aplikacja wykrywa nieodebrane TYLKO od klientów z bazy.
2. Powiadomienie pojawia się po wykryciu nieodebranego od znanego klienta.
3. Można dodać nowy numer do bazy klientów z poziomu aplikacji.
4. Lista połączeń wyświetla alert "WYMAGA NOTATKI" dla rekordów bez voice_report.

## Faza 3: Kolejka Nieodebranych i Rezerwacje
- [ ] Ekran główny "Kolejka Kontaktów" z listą nieodebranych połączeń.
- [ ] Karta połączenia: nazwa klienta, telefon, ile razy próbował dzwonić, do kogo dzwonił.
- [ ] Przycisk "Rezerwuję" (zmienia status na 'calling', ustawia reservation_by).
- [ ] Wizualizacja stanów:
    - 🔴 Czerwony: nikt nie zarezerwował (status: 'idle')
    - 🟡 Żółty: ktoś oddzwania (status: 'calling')
    - 🟢 Zielony: załatwione z notatką (status: 'completed' + voice_report)
    - ⚠️ Pomarańczowy: załatwione BEZ notatki (status: 'completed', brak voice_report) - "WYMAGA NOTATKI"
- [ ] Synchronizacja Realtime: zmiana statusu widoczna natychmiast na wszystkich urządzeniach.
- **Kryterium sukcesu:** Kliknięcie "Rezerwuję" na jednym urządzeniu natychmiast zmienia kolor karty na żółty na wszystkich innych urządzeniach. Alert "WYMAGA NOTATKI" pojawia się dla połączeń bez voice_report.

## Faza 4: Moduł Notatek Głosowych i AI
- [ ] Ekran "Dodaj notatkę" z nagrywaniem audio (expo-av).
- [ ] Lista ostatnich połączeń (z CallLog + call_logs) do wyboru właściwej rozmowy.
- [ ] Upload audio do Supabase Storage.
- [ ] Integracja z OpenAI Whisper API (transkrypcja).
- [ ] Integracja z Claude API (streszczanie i wyciąganie zadań).
- [ ] Obsługa trybu Offline: kolejkowanie w AsyncStorage, sync po odzyskaniu sieci.
- [ ] Po dodaniu notatki: automatyczna zmiana statusu call_log na 'completed', usunięcie alertu "BRAK NOTATKI".
- **Kryterium sukcesu:** Użytkownik nagrywa notatkę, wybiera połączenie z listy, aplikacja transkrybuje i streszcza audio, notatka pojawia się w kartotece klienta, alert "WYMAGA NOTATKI" znika.

## Faza 5: Powiadomienia Zespołowe i Finalizacja
- [ ] Powiadomienie push do zespołu po dodaniu nowej notatki przez AI.
- [ ] Widok historii notatek dla każdego klienta (timeline).
- [ ] Dashboard: statystyki (ile nieodebranych, ile zarezerwowanych, ile bez notatek).
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