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

## 2. Zarządzanie Klientami

### TEST 2.1: Dodanie klienta ręcznie
| Status | Krok |
|--------|------|
| ⏳ | 1. Przejdź do zakładki "Klienci" (👥) |
| ⏳ | 2. Kliknij "+" (Dodaj klienta) |
| ⏳ | 3. Wypełnij: Telefon (np. +48123456789), Imię, Adres, Notatki |
| ⏳ | 4. Kliknij "Zapisz" |
| ⏳ | **Oczekiwany rezultat:** Klient pojawia się na liście klientów |

### TEST 2.2: Dodanie klienta z kontaktów telefonu
| Status | Krok |
|--------|------|
| ⏳ | 1. Na ekranie dodawania klienta kliknij "📇 Wybierz z kontaktów telefonu" |
| ⏳ | 2. Wybierz kontakt z listy |
| ⏳ | 3. Jeśli kontakt ma kilka numerów - wybierz odpowiedni |
| ⏳ | 4. Kliknij "Zapisz" |
| ⏳ | **Oczekiwany rezultat:** Dane kontaktu (imię, numer, adres) są automatycznie wypełnione i klient zapisany |

### TEST 2.3: Walidacja numeru telefonu
| Status | Krok |
|--------|------|
| ⏳ | 1. Spróbuj dodać klienta z niepoprawnym numerem (np. "abc", "12345") |
| ⏳ | **Oczekiwany rezultat:** Wyświetla się komunikat błędu o niepoprawnym formacie numeru |

### TEST 2.4: Edycja klienta
| Status | Krok |
|--------|------|
| ⏳ | 1. Na liście klientów wybierz klienta |
| ⏳ | 2. Zmień dane (np. imię lub adres) |
| ⏳ | 3. Zapisz zmiany |
| ⏳ | **Oczekiwany rezultat:** Zmiany są widoczne na liście klientów |

---

## 3. Wykrywanie Nieodebranych Połączeń

### TEST 3.1: Nieodebrane od klienta z bazy
| Status | Krok |
|--------|------|
| ⏳ | 1. Upewnij się, że numer **📞C** jest dodany jako klient w TeamTalk |
| ⏳ | 2. Z **📞C** zadzwoń do **📱A** i rozłącz przed odebraniem |
| ⏳ | 3. Na **📱A** poczekaj max. 1 minutę lub użyj Pull-to-Refresh na ekranie "Kolejka" |
| ⏳ | **Oczekiwany rezultat:** Nieodebrane połączenie pojawia się w zakładce "Kolejka" |

### TEST 3.2: Nieodebrane od numeru spoza bazy (ignorowanie)
| Status | Krok |
|--------|------|
| ⏳ | 1. Upewnij się, że numer **📞C** NIE jest dodany jako klient |
| ⏳ | 2. Z **📞C** zadzwoń do **📱A** i rozłącz przed odebraniem |
| ⏳ | 3. Odśwież listę w "Kolejka" |
| ⏳ | **Oczekiwany rezultat:** Połączenie NIE pojawia się w kolejce (prywatność) |

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

### TEST 5.2: Oznaczenie jako "WYKONANE"
| Status | Krok |
|--------|------|
| ⏳ | 1. Na **📱A** przy zarezerwowanym połączeniu kliknij "WYKONANE" |
| ⏳ | 2. Sprawdź zakładkę "Notatka" |
| ⏳ | **Oczekiwany rezultat:** Połączenie znika z "Kolejka" i pojawia się w "Notatka" z alertem "🔴 WYMAGA NOTATKI" |

---

## 6. Notatki Głosowe i Transkrypcja AI

### TEST 6.1: Nagrywanie notatki głosowej
| Status | Krok |
|--------|------|
| ⏳ | 1. W zakładce "Notatka" kliknij na połączenie wymagające notatki |
| ⏳ | 2. Kliknij przycisk nagrywania (🎤) |
| ⏳ | 3. Nagraj krótką wiadomość głosową (5-10 sekund) |
| ⏳ | 4. Kliknij stop |
| ⏳ | 5. Odsłuchaj podgląd nagrania |
| ⏳ | 6. Kliknij "Zapisz" |
| ⏳ | **Oczekiwany rezultat:** Nagranie zostaje wysłane, pojawia się transkrypcja AI |

### TEST 6.2: Transkrypcja po polsku
| Status | Krok |
|--------|------|
| ⏳ | 1. Nagraj notatkę głosową po polsku |
| ⏳ | 2. Sprawdź wygenerowaną transkrypcję |
| ⏳ | **Oczekiwany rezultat:** Transkrypcja poprawnie oddaje polskie słowa |

### TEST 6.3: Zniknięcie alertu po dodaniu notatki
| Status | Krok |
|--------|------|
| ⏳ | 1. Po zapisaniu notatki sprawdź zakładkę "Notatka" |
| ⏳ | **Oczekiwany rezultat:** Połączenie zniknęło z listy "WYMAGA NOTATKI" |

---

## 7. Historia Rozmów

### TEST 7.1: Wyświetlanie historii
| Status | Krok |
|--------|------|
| ⏳ | 1. Przejdź do zakładki "Historia" (📋) |
| ⏳ | **Oczekiwany rezultat:** Lista rozmów z notatkami, posortowana od najnowszych |

### TEST 7.2: Odtwarzanie nagrania
| Status | Krok |
|--------|------|
| ⏳ | 1. W "Historia" wybierz rozmowę z nagraną notatką |
| ⏳ | 2. Kliknij przycisk "▶ Odtwórz" |
| ⏳ | **Oczekiwany rezultat:** Nagranie odtwarza się poprawnie |

### TEST 7.3: Wyszukiwanie w historii
| Status | Krok |
|--------|------|
| ⏳ | 1. W pasku wyszukiwania wpisz nazwisko klienta lub słowo z notatki |
| ⏳ | **Oczekiwany rezultat:** Lista filtruje się do pasujących wyników |

### TEST 7.4: Szczegóły notatki
| Status | Krok |
|--------|------|
| ⏳ | 1. Kliknij na rozmowę w historii |
| ⏳ | **Oczekiwany rezultat:** Otwiera się ekran z pełną transkrypcją i danymi klienta |

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

## 12. Timeline Klienta

### TEST 12.1: Przejście do historii klienta
| Status | Krok |
|--------|------|
| ⏳ | 1. W zakładce "Klienci" wybierz klienta z kilkoma rozmowami |
| ⏳ | 2. Sprawdź ekran szczegółów klienta |
| ⏳ | **Oczekiwany rezultat:** Widoczna lista wszystkich rozmów i notatek z tym klientem |

### TEST 12.2: Statystyki klienta
| Status | Krok |
|--------|------|
| ⏳ | 1. Na ekranie szczegółów klienta sprawdź statystyki |
| ⏳ | **Oczekiwany rezultat:** Widoczne: liczba połączeń, liczba notatek, liczba nieodebranych |

---

## Podsumowanie Testów

| Sekcja | Liczba testów | Zaliczone | Niezaliczone |
|--------|---------------|-----------|--------------|
| 1. Autoryzacja | 4 | | |
| 2. Zarządzanie Klientami | 4 | | |
| 3. Wykrywanie Połączeń | 3 | | |
| 4. Synchronizacja Realtime | 4 | | |
| 5. Workflow Obsługi | 2 | | |
| 6. Notatki Głosowe | 3 | | |
| 7. Historia | 4 | | |
| 8. Powiadomienia Zespołowe | 1 | | |
| 9. SLA i Accordion | 3 | | |
| 10. Dual SIM | 3 | | |
| 11. Ustawienia | 2 | | |
| 12. Timeline Klienta | 2 | | |
| **RAZEM** | **35** | | |

---

## Uwagi z testów

> _Miejsce na notatki o znalezionych błędach lub problemach_

1. 
2. 
3. 

---

**Data wykonania testów:** _______________  
**Tester:** _______________  
**Wersja aplikacji:** _______________
