# Roadmapa Projektu: TeamTalk - System CRM AI

## Faza 1: Fundament i Baza Danych
- [ ] Konfiguracja projektu React Native (Expo) z Supabase.
- [ ] Stworzenie struktury bazy danych w Supabase:
    - Tabela `clients` (id, phone, name, address, notes).
    - Tabela `call_logs` (id, client_id, employee_id, type: 'missed'/'completed', timestamp, reservation_by).
    - Tabela `voice_reports` (id, call_log_id, audio_url, transcription, ai_summary).
- [ ] Włączenie Supabase Realtime dla tabeli `call_logs`.
- **Kryterium sukcesu:** Możliwość ręcznego dodania klienta w panelu Supabase i wyświetlenia go w surowym widoku aplikacji.

## Faza 2: Monitoring Połączeń
- [ ] Implementacja Native Module / Expo Config Plugin do obsługi `PhoneStateListener`.
- [ ] Stworzenie Foreground Service, który działa w tle i:
    - Wykrywa połączenie nieodebrane -> wysyła rekord do `call_logs`.
    - Wykrywa zakończenie rozmowy -> wyzwala powiadomienie lokalne.
- [ ] Konfiguracja uprawnień Android (READ_PHONE_STATE, READ_CALL_LOG, POST_NOTIFICATIONS).
- **Kryterium sukcesu:** Po symulacji nieodebranego połączenia w bazie danych pojawia się nowy rekord, a po zakończeniu rozmowy telefon wyświetla powiadomienie systemowe.

## Faza 3: Rejestr Nieodebranych i Rezerwacje
- [ ] Stworzenie ekranu głównego "Kolejka Kontaktów".
- [ ] Implementacja przycisku "Rezerwuję" (Update rekordu w Supabase Realtime).
- [ ] Wizualizacja stanów: Czerwony (nikt nie dzwonił), Żółty (ktoś właśnie oddzwania), Zielony (załatwione).
- **Kryterium sukcesu:** Kliknięcie "Rezerwuję" na jednym urządzeniu natychmiast zmienia status (kolor) tego połączenia na wszystkich innych zalogowanych urządzeniach.

## Faza 4: Moduł Głosowy i AI
- [ ] Stworzenie ekranu nagrywania notatki (Audio Recorder).
- [ ] Integracja z OpenAI Whisper API (transkrypcja pliku audio).
- [ ] Integracja z Claude API (streszczanie tekstu i wyciąganie zadań).
- [ ] Obsługa trybu Offline: kolejkowanie nagrań w pamięci telefonu (AsyncStorage) i wysyłka po wykryciu sieci.
- **Kryterium sukcesu:** Nagrana notatka głosowa pojawia się w aplikacji jako sformatowany tekst (streszczenie AI) przypisany do właściwego klienta.

## Faza 5: Powiadomienia Push i Testy
- [ ] Konfiguracja Firebase Cloud Messaging (FCM).
- [ ] Funkcja wysyłania powiadomienia do wspólnika po dodaniu nowej notatki przez AI.
- [ ] Testy "na sucho" między dwoma telefonami.
- **Kryterium sukcesu:** Wspólnik otrzymuje powiadomienie o treści notatki natychmiast po tym, jak AI skończy przetwarzać Twoje nagranie.

---

## Definicja MVP (Cel końcowy)
System uznajemy za gotowy, gdy:
1. Nieodebrane połączenie u jednego pracownika jest widoczne dla wszystkich.
2. Można zarezerwować oddzwonienie, unikając dublowania pracy.
3. Po rozmowie notatka głosowa jest automatycznie przetwarzana na tekst i synchronizowana w chmurze.
4. Cały kod jest w języku angielskim, interfejs w języku polskim, a zmiany są wypchnięte na repozytorium git.