# Kontekst Projektu: System Zarządzania Zgłoszeniami Instalacyjnymi (CRM AI)

## Cel projektu
Stworzenie lekkiej aplikacji mobilnej (React Native + Supabase) dla firmy instalacyjnej (pompy ciepła, fotowoltaika), która wyeliminuje chaos informacyjny między wspólnikami i pracownikami.

## Główni użytkownicy
- Właściciel, wspólnik, serwisanci (wszyscy używają telefonów z Androidem).

## Kluczowe Funkcjonalności (MVP)
1. **Wspólny Rejestr Nieodebranych:**
   - Wykrywanie nieodebranych połączeń na telefonach pracowników (Android Background Service).
   - Synchronizacja w czasie rzeczywistym z bazą Supabase.
   - Widok listy nieodebranych z funkcją "rezerwacji" (oznaczenie, że dany pracownik właśnie oddzwania).

2. **Raporty Głosowe po rozmowie:**
   - Wykrywanie zakończenia rozmowy (przychodzącej i wychodzącej).
   - Wyświetlanie powiadomienia (Push/Overlay) z prośbą o nagranie notatki.
   - Nagrywanie audio i wysyłka do serwera (OpenAI Whisper + Claude API).

3. **Automatyczna Kartoteka Klienta:**
   - Rozpoznawanie numeru telefonu i przypisywanie notatki do historii danego klienta.
   - Streszczanie rozmów przez AI (wyciąganie konkretów: kto, co, na kiedy).

## Architektura Techniczna
- **Frontend:** React Native (Expo) - skupienie na Androidzie.
- **Backend/Baza:** Supabase (Auth, Database, Edge Functions, Realtime).
- **AI:** OpenAI Whisper (Transkrypcja) + Claude API (Analiza/Streszczenie).
- **Automatyzacja:** Android Foreground Service do monitorowania stanu telefonu (TelephonyManager).

## Zasady implementacji
- **Offline-first:** Jeśli brak internetu, notatki audio muszą być kolejkowane lokalnie i wysyłane po odzyskaniu połączenia.
- **Prostota:** Interfejs musi być obsługiwalny jedną ręką, przyciski duże, widok czytelny w pełnym słońcu.
- **Prywatność:** System przetwarza tylko metadane połączeń służbowych i nagrane notatki głosowe pracowników (nie nagrywamy samej rozmowy telefonicznej bezpośrednio).

## Wyzwania do rozwiązania
- Stabilność serwisu działającego w tle na Androidzie (Battery Optimization).
- Szybka synchronizacja stanów "rezerwacji" połączenia między wieloma urządzeniami.

## Standardy Kodowania i Jakość
- **Język:** TypeScript ze ścisłym typowaniem (strict mode).
- **Architektura:** Clean Architecture z podziałem na warstwy: `services`, `hooks`, `components`, `api`.
- **Wzorce:** SOLID, ze szczególnym uwzględnieniem Single Responsibility Principle.
- **Workflow:** TDD (Test-Driven Development). Dla każdej kluczowej funkcjonalności logicznej najpierw napisz testy w Jest, a potem implementację.
- **Zasada DRY:** Unikaj powtarzania logiki powiadomień i zapytań do bazy.
- **Obsługa błędów:** Każde zapytanie do API musi mieć obsłużony tryb offline i retry policy (ponowienie próby).
- **Język kodu:** Cały kod źródłowy (nazwy zmiennych, funkcji, klas, tabel w bazie danych) musi być pisany wyłącznie w języku angielskim. Wyjątek stanowi warstwa interfejsu użytkownika (UI), która musi być w języku polskim. Dokumentacja technicza i komentarze w kodzie w języku polskim.

## Repozytorium i Git
- **Remote URL:** git@github.com:KamilDziech/TeamTalk.git
- **Zasady commitowania:** - Twórz atomowe commity (jedna funkcja = jeden commit).
    - Używaj opisowych wiadomości commitów w języku polskim. Nazwa commita po angielsku.
    - Po zakończeniu każdej fazy z `roadmap.md` wykonaj `git push`.