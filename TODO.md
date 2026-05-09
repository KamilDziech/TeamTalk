# TeamTalk Android — Lista funkcjonalności

Legenda: ✅ Zaimplementowane | 🚧 Częściowo | ❌ Brak

---

## 1. Autoryzacja i zarządzanie sesją

- ✅ Rejestracja (email + hasło + imię wyświetlane)
- ✅ Logowanie z walidacją formularza i komunikatami błędów po polsku
- ✅ Podgląd/ukrywanie hasła
- ✅ Automatyczne przywracanie sesji po zamknięciu aplikacji (DataStore)
- ✅ Automatyczne odświeżanie tokenów JWT (TokenAuthenticator na HTTP 401)
- ✅ Wylogowanie z potwierdzeniem

---

## 2. Kolejka połączeń nieodebranych

- 🚧 Automatyczne skanowanie systemowego dziennika połączeń Androida — WorkManager co 15 min (oryginał: co 1 min — WorkManager nie pozwala częściej niż 15 min)
- ✅ Wspólna kolejka dla całego zespołu
- ✅ Grupowanie wielokrotnych połączeń od tego samego numeru (badge ×N)
- ✅ Wskaźnik SLA — ostrzeżenie gdy połączenie czeka ponad 1 godzinę
- ❌ Alert gdy ten sam klient dzwonił do kilku pracowników
- ✅ Rezerwacja połączenia (nieodebrane → zarezerwowane)
- ✅ Widoczność kto zarezerwował połączenie
- ✅ Pull-to-refresh
- ✅ Automatyczne odświeżenie po powrocie z tła (lifecycle observer)

---

## 3. Szczegóły połączenia

- ✅ Historia wszystkich połączeń od danego klienta (ClientTimelineScreen)
- ✅ Bezpośrednie oddzwonienie (Intent do dialera)
- ✅ Oznaczenie jako wykonane (completed)
- ✅ Anulowanie rezerwacji (zwrot do kolejki)
- ✅ Informacja o czasie oczekiwania i SLA

---

## 4. Notatki po rozmowie

- 🚧 Workflow nieodebrane → zarezerwowane → wykonane → notatka (ekrany są, brak wymuszenia kolejności)
- ✅ Notatka głosowa — nagrywanie audio z timerem, możliwość ponowienia
- ✅ Automatyczna transkrypcja przez OpenAI Whisper (klucz po stronie serwera)
- ✅ Edycja tekstu transkrypcji przed zapisem
- ✅ Notatka tekstowa — alternatywa dla głosowej
- ✅ Opcja pominięcia notatki ("Pomiń")

---

## 5. Historia rozmów

- ✅ Dedykowany ekran historii zakończonych połączeń z notatkami (zakładka Historia)
- ✅ Wyszukiwarka (filtrowanie po nazwie, numerze, treści notatki)
- ✅ Relatywny czas ("X min temu", "Wczoraj")
- ✅ Kliknięcie → szczegóły połączenia (CallLogDetailScreen)

---

## 6. Baza klientów (CRM)

- ✅ Lista klientów z wyszukiwarką
- ✅ Dodawanie klienta ręcznie
- ✅ Import klienta z kontaktów urządzenia
- ✅ Normalizacja i walidacja numeru telefonu (min. 7 cyfr)
- ✅ Edycja klienta (adres, notatki)
- ✅ Usunięcie klienta z potwierdzeniem
- ✅ Timeline klienta — historia wszystkich połączeń i notatek (ClientTimelineScreen)
- ✅ Liczba połączeń dla każdego klienta (badge na liście)

---

## 7. Ustawienia

- ✅ Motyw — jasny / ciemny / systemowy
- ❌ Dual SIM — detekcja kart SIM, wskazanie karty służbowej, filtrowanie połączeń prywatnych
- ❌ Powiadomienia push — włączanie/wyłączanie, rejestracja urządzenia
- ✅ Kliknięcie powiadomienia → przejście do szczegółów połączenia (PendingIntent)
- ✅ Informacja o wersji aplikacji

---

## 8. Integracja z backendem

- ✅ JWT authentication (access token + refresh token)
- ✅ Synchronizacja: połączenia, klienci, notatki, profile, urządzenia
- ✅ Deduplikacja połączeń po kluczu (numer + timestamp / 5s)
- ❌ Row Level Security po stronie backendu (do weryfikacji na serwerze)

---

## 9. Nawigacja

4 zakładki zgodnie z oryginałem.

| Karta | Oryginał | Kotlin |
|-------|----------|--------|
| Kolejka/Zgłoszenia | ✅ | ✅ |
| Historia | ✅ | ✅ |
| Klienci | ✅ | ✅ |
| Ustawienia | ✅ | ✅ |

---

## Pozostałe (poza scopem MVP)

- ❌ Dual SIM — wymaga testowania sprzętowego
- ❌ Push notifications z serwera — wymaga pracy po stronie backendu
- ❌ Row Level Security — weryfikacja po stronie backendu
- ❌ Alert gdy klient dzwonił do kilku pracowników — złożona logika grupowania
