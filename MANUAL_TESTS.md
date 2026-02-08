# TeamTalk - Plan Testów Manualnych

> **Zasoby testowe:** 2 telefony z zainstalowaną aplikacją TeamTalk (oznaczane jako **📱A** i **📱B**), 1 dodatkowy telefon do wykonywania połączeń (oznaczany jako **📞C**)

---

## Legenda

| Symbol | Znaczenie |
|--------|-----------|
| ✅ | Test zaliczony |
| ❌ | Test niezaliczony |
| ⏳ | Test nie wykonany |
| 📱A | Telefon 1 z TeamTalk |
| 📱B | Telefon 2 z TeamTalk |
| 📞C | Telefon do dzwonienia |

---

## 1. Autoryzacja i Sesja

### TEST 1.1: Rejestracja nowego użytkownika
| Status | Krok |
|--------|------|
| ⏳ | 1. Na **📱A** otwórz aplikację TeamTalk |
| ⏳ | 2. Kliknij "Zarejestruj się" |
| ⏳ | 3. Wprowadź: Email, Hasło (min. 6 znaków), Imię |
| ⏳ | 4. Kliknij "Zarejestruj" |
| ⏳ | **Oczekiwany rezultat:** Użytkownik zostaje przeniesiony do ekranu "Kolejka" |

### TEST 1.2: Logowanie istniejącym kontem
| Status | Krok |
|--------|------|
| ⏳ | 1. Na **📱B** otwórz aplikację |
| ⏳ | 2. Wprowadź dane logowania (email + hasło) |
| ⏳ | 3. Kliknij "Zaloguj" |
| ⏳ | **Oczekiwany rezultat:** Użytkownik widzi ekran "Kolejka" |

### TEST 1.3: Trwałość sesji (auto-login)
| Status | Krok |
|--------|------|
| ⏳ | 1. Będąc zalogowanym, zamknij aplikację całkowicie (usuń z "ostatnich") |
| ⏳ | 2. Otwórz aplikację ponownie |
| ⏳ | **Oczekiwany rezultat:** Użytkownik jest automatycznie zalogowany i widzi "Kolejka" bez ekranu logowania |

### TEST 1.4: Wylogowanie
| Status | Krok |
|--------|------|
| ⏳ | 1. Przejdź do zakładki "Ustawienia" (⚙️) lub kliknij ikonę ustawień |
| ⏳ | 2. Kliknij "Wyloguj" |
| ⏳ | 3. Potwierdź w dialogu |
| ⏳ | **Oczekiwany rezultat:** Użytkownik wraca do ekranu logowania |

---

## 2. Automatyczne Zarządzanie Klientami

### TEST 2.1: Auto-generowanie klienta przy nieodebranym połączeniu
| Status | Krok |
|--------|------|
| ⏳ | 1. Upewnij się że numer **📞C** NIE istnieje w bazie klientów |
| ⏳ | 2. Z **📞C** wykonaj nieodebrane połączenie do **📱A** |
| ⏳ | 3. Sprawdź zakładkę "Kolejka" |
| ⏳ | **Oczekiwany rezultat:** Połączenie pojawia się w kolejce, klient jest automatycznie tworzony w bazie (bez nazwy, tylko numer telefonu) |

### TEST 2.2: Wyświetlanie nazw z kontaktów telefonu
| Status | Krok |
|--------|------|
| ⏳ | 1. Upewnij się że numer **📞C** jest zapisany w kontaktach telefonu **📱A** z nazwą (np. "Jan Kowalski") |
| ⏳ | 2. Z **📞C** wykonaj nieodebrane połączenie |
| ⏳ | 3. Sprawdź zakładkę "Kolejka" |
| ⏳ | **Oczekiwany rezultat:** W Kolejce wyświetla się nazwa z kontaktów telefonu "Jan Kowalski" (nie "Brak nazwy") |

### TEST 2.3: Edycja danych klienta w timeline
| Status | Krok |
|--------|------|
| ⏳ | 1. Przejdź do zakładki "Historia", wybierz klienta |
| ⏳ | 2. W timeline klienta kliknij "Edytuj" |
| ⏳ | 3. Zmień adres lub dodaj notatki |
| ⏳ | 4. Zapisz zmiany |
| ⏳ | **Oczekiwany rezultat:** Zmiany są widoczne w timeline klienta |

### TEST 2.4: Race condition przy wielokrotnych połączeniach
| Status | Krok |
|--------|------|
| ⏳ | 1. Z **📞C** (nowy numer) zadzwoń kilka razy szybko po sobie do **📱A** i **📱B** |
| ⏳ | 2. Sprawdź logi aplikacji |
| ⏳ | **Oczekiwany rezultat:** Tylko JEDEN klient jest tworzony (bez duplikatów), logi pokazują "⚠️ Client creation race condition detected" |

---

## 3. Wykrywanie Nieodebranych Połączeń

### TEST 3.1: Nieodebrane od klienta z bazy
| Status | Krok |
|--------|------|
| ⏳ | 1. Upewnij się, że numer **📞C** jest dodany jako klient w TeamTalk |
| ⏳ | 2. Z **📞C** zadzwoń do **📱A** i rozłącz przed odebraniem |
| ⏳ | 3. Na **📱A** poczekaj max. 1 minutę lub użyj Pull-to-Refresh na ekranie "Kolejka" |
| ⏳ | **Oczekiwany rezultat:** Nieodebrane połączenie pojawia się w zakładce "Kolejka" |

### TEST 3.2: Nieodebrane od numeru spoza bazy (auto-dodawanie klienta)
| Status | Krok |
|--------|------|
| ⏳ | 1. Upewnij się, że numer **📞C** NIE jest dodany jako klient |
| ⏳ | 2. Z **📞C** zadzwoń do **📱A** i rozłącz przed odebraniem |
| ⏳ | 3. Odśwież listę w "Kolejka" |
| ⏳ | **Oczekiwany rezultat:** Połączenie pojawia się w kolejce, klient jest automatycznie tworzony w bazie (widoczny po dodaniu notatki w zakładce "Historia") |

### TEST 3.3: Powiadomienie push o nieodebranym
| Status | Krok |
|--------|------|
| ⏳ | 1. Na **📱A** zminimalizuj aplikację (tło) |
| ⏳ | 2. Z **📞C** zadzwoń do **📱A** (numer klienta w bazie) i rozłącz |
| ⏳ | 3. Obserwuj powiadomienia na **📱A** |
| ⏳ | **Oczekiwany rezultat:** Pojawia się powiadomienie: "🔴 Nieodebrane od: [Nazwa Klienta]" |

---

## 4. Wspólna Kolejka i Synchronizacja Realtime

### TEST 4.1: Widoczność nieodebranych na obu urządzeniach
| Status | Krok |
|--------|------|
| ⏳ | 1. Zaloguj się na **📱A** i **📱B** (różne konta) |
| ⏳ | 2. Na **📱A** upewnij się, że nieodebrane połączenie jest w "Kolejka" |
| ⏳ | 3. Na **📱B** sprawdź zakładkę "Kolejka" |
| ⏳ | **Oczekiwany rezultat:** To samo połączenie widoczne na obu telefonach |

### TEST 4.2: Etykiety adresatów ("Do: ...")
| Status | Krok |
|--------|------|
| ⏳ | 1. Z **📞C** zadzwoń do **📱A** - nie odbieraj |
| ⏳ | 2. Z **📞C** zadzwoń do **📱B** (ten sam numer klienta) - nie odbieraj |
| ⏳ | 3. Sprawdź kartę połączenia w "Kolejka" |
| ⏳ | **Oczekiwany rezultat:** Etykieta pokazuje "Do: [Imię z 📱A], [Imię z 📱B]" |

### TEST 4.3: Rezerwacja połączenia - synchronizacja
| Status | Krok |
|--------|------|
| ⏳ | 1. Na **📱A** kliknij "REZERWUJ" przy połączeniu w "Kolejka" |
| ⏳ | 2. Natychmiast sprawdź **📱B** |
| ⏳ | **Oczekiwany rezultat:** Na **📱B** karta pokazuje "Obsługuje: [Imię z 📱A]" i status "zarezerwowane" |

### TEST 4.4: Uwolnienie rezerwacji
| Status | Krok |
|--------|------|
| ⏳ | 1. Na **📱A** przy zarezerwowanym połączeniu kliknij "UWOLNIJ" |
| ⏳ | 2. Sprawdź kartę na **📱A** i **📱B** |
| ⏳ | **Oczekiwany rezultat:** Karta wraca do statusu "missed" z przyciskiem "REZERWUJ" na obu urządzeniach |

---

## 5. Workflow Obsługi Połączenia

### TEST 5.1: Przycisk "ZADZWOŃ"
| Status | Krok |
|--------|------|
| ⏳ | 1. Na **📱A** zarezerwuj połączenie |
| ⏳ | 2. Kliknij przycisk "ZADZWOŃ" |
| ⏳ | **Oczekiwany rezultat:** Otwiera się systemowy dialer z numerem klienta |

### TEST 5.2: Oznaczenie jako "WYKONANE" - przejście do zakładki Notatka
| Status | Krok |
|--------|------|
| ⏳ | 1. Na **📱A** przy zarezerwowanym połączeniu kliknij "WYKONANE" |
| ⏳ | 2. Obserwuj co się dzieje |
| ⏳ | **Oczekiwany rezultat:** Aplikacja automatycznie przechodzi do zakładki "Notatka" (🎤) gdzie połączenie pojawia się na liście wymagających notatki |

### TEST 5.3: Wybór typu notatki i pominięcie
| Status | Krok |
|--------|------|
| ⏳ | 1. W zakładce "Notatka" przy połączeniu widoczne są 3 przyciski: "🎤 Nagraj", "✏️ Napisz", "🗑️ Pomiń" |
| ⏳ | 2. Kliknij "🗑️ Pomiń" |
| ⏳ | 3. Potwierdź w dialogu |
| ⏳ | 4. Sprawdź zakładkę "Historia" |
| ⏳ | **Oczekiwany rezultat:** Połączenie znika z "Notatka" i pojawia się w "Historia" jako klient z completed połączeniem (bez notatki) |

---

## 6. Notatki Głosowe i Transkrypcja AI

### TEST 6.1: Nagrywanie notatki głosowej
| Status | Krok |
|--------|------|
| ⏳ | 1. W zakładce "Notatka" wybierz połączenie i kliknij "🎤 Nagraj" |
| ⏳ | 2. Modal notatki głosowej otwiera się |
| ⏳ | 3. Kliknij przycisk nagrywania (duży 🎤 na środku ekranu) |
| ⏳ | 4. Nagraj krótką wiadomość głosową (5-10 sekund) |
| ⏳ | 5. Kliknij stop |
| ⏳ | 6. Odsłuchaj podgląd nagrania |
| ⏳ | 7. Kliknij "Zapisz" |
| ⏳ | **Oczekiwany rezultat:** Nagranie zostaje wysłane, pojawia się transkrypcja AI, połączenie znika z "Notatka" i trafia do "Historia" |

### TEST 6.1a: Zapisanie notatki tekstowej
| Status | Krok |
|--------|------|
| ⏳ | 1. W zakładce "Notatka" wybierz połączenie i kliknij "✏️ Napisz" |
| ⏳ | 2. Wpisz notatkę w pole tekstowe (np. "Klient chce wycenę okien PVC") |
| ⏳ | 3. Kliknij przycisk "💾 Zapisz" |
| ⏳ | **Oczekiwany rezultat:** Notatka zostaje zapisana, połączenie znika z "Notatka" i trafia do "Historia" |

### TEST 6.1b: Anulowanie notatki
| Status | Krok |
|--------|------|
| ⏳ | 1. Otwórz modal notatki (głosowej lub tekstowej) |
| ⏳ | 2. Kliknij "X" (zamknij) lub "Anuluj" |
| ⏳ | **Oczekiwany rezultat:** Modal się zamyka, połączenie pozostaje w zakładce "Notatka" (nie jest pomijane) |

### TEST 6.1c: Nagrywanie notatki ręcznie z zakładki "Notatka"
| Status | Krok |
|--------|------|
| ⏳ | 1. Przejdź do zakładki "Notatka" (🎤) |
| ⏳ | 2. Kliknij na połączenie z listy wymagających notatki |
| ⏳ | 3. Postępuj jak w TEST 6.1 (kroki 2-6) |
| ⏳ | **Oczekiwany rezultat:** Notatka zostaje zapisana i połączenie znika z listy "Wymaga notatki" |

### TEST 6.2: Transkrypcja po polsku
| Status | Krok |
|--------|------|
| ⏳ | 1. Nagraj notatkę głosową po polsku |
| ⏳ | 2. Sprawdź wygenerowaną transkrypcję |
| ⏳ | **Oczekiwany rezultat:** Transkrypcja poprawnie oddaje polskie słowa |

### TEST 6.3: Przeniesienie do historii po dodaniu notatki
| Status | Krok |
|--------|------|
| ⏳ | 1. Po zapisaniu notatki sprawdź zakładkę "Notatka" |
| ⏳ | 2. Sprawdź zakładkę "Historia" |
| ⏳ | **Oczekiwany rezultat:** Połączenie zniknęło z zakładki "Notatka" i pojawia się w "Historia" jako klient z completed połączeniem |

### TEST 6.4: Pominięcie połączenia bez notatki
| Status | Krok |
|--------|------|
| ⏳ | 1. W zakładce "Notatka" przy połączeniu kliknij "🗑️ Pomiń" |
| ⏳ | 2. Potwierdź w dialogu |
| ⏳ | 3. Sprawdź zakładkę "Historia" |
| ⏳ | **Oczekiwany rezultat:** Połączenie znika z "Notatka", klient pojawia się w "Historia" (ma completed połączenie ale bez voice_report) |

---

## 7. Historia Klientów

### TEST 7.1: Wyświetlanie listy klientów w Historii
| Status | Krok |
|--------|------|
| ⏳ | 1. Oznacz kilka połączeń od różnych numerów jako "WYKONANE" i dodaj notatki |
| ⏳ | 2. Przejdź do zakładki "Historia" (📜) |
| ⏳ | **Oczekiwany rezultat:** Lista pokazuje zgrupowanych KLIENTÓW (nie poszczególne połączenia), którzy mają przynajmniej jedno completed połączenie |

### TEST 7.1a: Nazwy z kontaktów telefonu w Historii
| Status | Krok |
|--------|------|
| ⏳ | 1. W zakładce "Historia" sprawdź nazwy klientów |
| ⏳ | 2. Porównaj z kontaktami w telefonie |
| ⏳ | **Oczekiwany rezultat:** Jeśli numer klienta jest w kontaktach telefonu - wyświetla się nazwa z kontaktów (priorytet 1), inaczej nazwa z CRM lub numer telefonu |

### TEST 7.1b: Klient nie pojawia się przed dodaniem notatki
| Status | Krok |
|--------|------|
| ⏳ | 1. Odbierz nieodebrane połączenie (pojawia się w "Kolejka") |
| ⏳ | 2. Zarezerwuj i oznacz jako "WYKONANE" (pojawia się w "Notatka") |
| ⏳ | 3. Sprawdź zakładkę "Historia" |
| ⏳ | **Oczekiwany rezultat:** Klient NIE pojawia się w Historii dopóki nie dodasz notatki lub nie pominiesz (completed bez voice_report) |

### TEST 7.2: Timeline klienta - historia połączeń
| Status | Krok |
|--------|------|
| ⏳ | 1. W "Historia" kliknij na klienta który dzwonił kilka razy |
| ⏳ | 2. Sprawdź ekran szczegółów/timeline |
| ⏳ | **Oczekiwany rezultat:** Otwiera się timeline z listą WSZYSTKICH połączeń tego klienta (z datą, godziną, notatkami) |

### TEST 7.3: Odtwarzanie notatki głosowej z timeline
| Status | Krok |
|--------|------|
| ⏳ | 1. W timeline klienta wybierz połączenie z notatką głosową |
| ⏳ | 2. Kliknij przycisk "▶ Odtwórz" |
| ⏳ | **Oczekiwany rezultat:** Nagranie odtwarza się poprawnie |

### TEST 7.4: Przejście z Historii gdy brak klientów
| Status | Krok |
|--------|------|
| ⏳ | 1. Na czystym koncie (bez completed połączeń) przejdź do "Historia" |
| ⏳ | **Oczekiwany rezultat:** Pusty stan z komunikatem "Brak historii" lub podobnym |

### TEST 7.5: Odświeżanie Historii przy pull-to-refresh
| Status | Krok |
|--------|------|
| ⏳ | 1. W zakładce "Historia" pociągnij w dół (pull-to-refresh) |
| ⏳ | 2. Obserwuj czy lista się odświeża |
| ⏳ | **Oczekiwany rezultat:** Lista klientów odświeża się, nowo completed klienci pojawiają się na liście |

---

## 8. Powiadomienia Zespołowe

### TEST 8.1: Powiadomienie o nowej notatce
| Status | Krok |
|--------|------|
| ⏳ | 1. Na **📱A** dodaj notatkę głosową do połączenia |
| ⏳ | 2. Sprawdź powiadomienia na **📱B** |
| ⏳ | **Oczekiwany rezultat:** **📱B** otrzymuje powiadomienie: "📝 [Imię z 📱A] dodał notatkę do rozmowy z [Klient]" |

---

## 9. SLA Alert i Accordion

### TEST 9.1: Alert SLA (czas oczekiwania > 1h)
| Status | Krok |
|--------|------|
| ⏳ | 1. Znajdź lub utwórz nieodebrane połączenie starsze niż 1 godzina |
| ⏳ | 2. Sprawdź kartę w "Kolejka" |
| ⏳ | **Oczekiwany rezultat:** Czerwony baner z czasem oczekiwania (np. "Czeka: 1h 30m") i ikona ❗ przy nazwie |

### TEST 9.2: Rozwijane próby kontaktu (Accordion)
| Status | Krok |
|--------|------|
| ⏳ | 1. Z **📞C** zadzwoń kilka razy do **📱A** (ten sam klient) |
| ⏳ | 2. Na karcie klient kliknij "Pokaż X prób" |
| ⏳ | **Oczekiwany rezultat:** Lista rozwija się z animacją, pokazując wszystkie próby z godziną i czasem od próby |

### TEST 9.3: Grupowanie nieodebranych od tego samego klienta
| Status | Krok |
|--------|------|
| ⏳ | 1. Z **📞C** zadzwoń 3 razy do **📱A** (nie odbieraj) |
| ⏳ | 2. Sprawdź "Kolejka" |
| ⏳ | **Oczekiwany rezultat:** Jedna karta z licznikiem "🔔 Klient dzwonił 3 razy!" |

---

## 10. Dual SIM

### TEST 10.1: Wykrywanie kart SIM
| Status | Krok |
|--------|------|
| ⏳ | 1. Na telefonie z 2 kartami SIM przejdź do "Ustawienia" |
| ⏳ | 2. Znajdź sekcję "Konfiguracja Dual SIM" |
| ⏳ | **Oczekiwany rezultat:** Widoczna lista wykrytych kart SIM (tylko na telefonie Dual SIM) |

### TEST 10.2: Wybór karty służbowej
| Status | Krok |
|--------|------|
| ⏳ | 1. W ustawieniach wybierz jedną kartę jako "służbową" |
| ⏳ | 2. Odbierz połączenie na PRYWATNEJ karcie SIM (nie służbowej) |
| ⏳ | 3. Sprawdź czy nieodebrane pojawia się w kolejce |
| ⏳ | **Oczekiwany rezultat:** Połączenia z prywatnej karty NIE trafiają do kolejki |

### TEST 10.3: Reset wyboru SIM
| Status | Krok |
|--------|------|
| ⏳ | 1. Kliknij "Resetuj wybór SIM" |
| ⏳ | 2. Potwierdź w dialogu |
| ⏳ | **Oczekiwany rezultat:** Wybór karty służbowej zostaje usunięty |

---

## 11. Ustawienia Aplikacji

### TEST 11.1: Zmiana motywu (jasny/ciemny/systemowy)
| Status | Krok |
|--------|------|
| ⏳ | 1. Przejdź do Ustawień |
| ⏳ | 2. Wybierz "Ciemny" motyw |
| ⏳ | **Oczekiwany rezultat:** Aplikacja zmienia kolory na ciemne |

### TEST 11.2: Przełącznik powiadomień
| Status | Krok |
|--------|------|
| ⏳ | 1. W Ustawieniach znajdź przełącznik powiadomień push |
| ⏳ | 2. Wyłącz powiadomienia |
| ⏳ | **Oczekiwany rezultat:** Przełącznik zmienia stan, powiadomienia zostają wyłączone |

---

## 12. Timeline Klienta (w zakładce Historia)

### TEST 12.1: Przejście do timeline klienta
| Status | Krok |
|--------|------|
| ⏳ | 1. W zakładce "Historia" (📜) wybierz klienta z kilkoma completed połączeniami |
| ⏳ | 2. Sprawdź ekran szczegółów/timeline klienta |
| ⏳ | **Oczekiwany rezultat:** Widoczna lista wszystkich completed rozmów (z datą, godziną) i notatek z tym klientem |

### TEST 12.2: Odtwarzanie różnych typów notatek
| Status | Krok |
|--------|------|
| ⏳ | 1. W timeline klienta znajdź połączenie z notatką głosową |
| ⏳ | 2. Kliknij "▶ Odtwórz" |
| ⏳ | 3. Znajdź połączenie z notatką tekstową |
| ⏳ | **Oczekiwany rezultat:** Notatka głosowa odtwarza się, notatka tekstowa wyświetla transkrypcję/tekst |

---

## Podsumowanie Testów

| Sekcja | Liczba testów | Zaliczone | Niezaliczone |
|--------|---------------|-----------|--------------|
| 1. Autoryzacja | 4 | | |
| 2. Automatyczne Zarządzanie Klientami | 4 | | |
| 3. Wykrywanie Połączeń | 3 | | |
| 4. Synchronizacja Realtime | 4 | | |
| 5. Workflow Obsługi | 3 | | |
| 6. Notatki Głosowe | 7 | | |
| 7. Historia Klientów | 7 | | |
| 8. Powiadomienia Zespołowe | 1 | | |
| 9. SLA i Accordion | 3 | | |
| 10. Dual SIM | 3 | | |
| 11. Ustawienia | 2 | | |
| 12. Timeline Klienta | 2 | | |
| **RAZEM** | **43** | | |

---

## Uwagi z testów

> _Miejsce na notatki o znalezionych błędach lub problemach_

1. 
2. 
3. 

---

## ⚠️ WYMAGANE PRZED TESTAMI

### Migracje bazy danych
**KRYTYCZNE:** Przed rozpoczęciem testów należy zastosować poniższe migracje w bazie Supabase:

#### 1. Naprawa polityk RLS
1. Otwórz: https://supabase.com/dashboard (Twój projekt)
2. Przejdź do **SQL Editor** → **New query**
3. Wklej zawartość pliku: `/supabase/migrations/20260208000000_fix_rls_policies.sql`
4. Kliknij **Run**
5. Zweryfikuj sukces: `Success. No rows returned`

**Dlaczego:** Naprawia błędne polityki RLS które blokowały zapis połączeń do bazy

#### 2. Dodanie UNIQUE constraint dla dedup_key
1. Wklej zawartość: `/supabase/migrations/20260208100000_add_dedup_constraint.sql`
2. Kliknij **Run**

**Dlaczego:** Zapobiega duplikowaniu połączeń gdy wiele urządzeń otrzymuje to samo połączenie

#### 3. Dodanie typu 'skipped' dla call_logs
1. Wklej zawartość: `/supabase/migrations/20260208200000_add_skipped_type.sql`
2. Kliknij **Run**

**Dlaczego:** Umożliwia oznaczanie połączeń jako "pominięte" (completed bez notatki)

### Uprawnienia aplikacji
Aplikacja wymaga następujących uprawnień:
- ✅ **READ_CALL_LOG** - wykrywanie nieodebranych połączeń
- ✅ **READ_CONTACTS** - wyświetlanie nazw z kontaktów telefonu (zamiast numerów)
- ✅ **Notifications** - powiadomienia o nowych połączeniach

**Ważne:** Bez uprawnienia READ_CONTACTS w Kolejce i Historii będą wyświetlane tylko numery telefonu lub "Brak nazwy"

---

**Data wykonania testów:** _______________
**Tester:** _______________
**Wersja aplikacji:** _______________
