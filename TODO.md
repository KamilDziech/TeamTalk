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

- ✅ Kreator po rozmowie: rozmówca → streszczenie → zadanie (3 plansze)
- ✅ Plansza 1: potwierdzenie klienta z kartoteki / założenie kontaktu / pominięcie z potwierdzeniem
- ✅ Plansza 3: „Tak" prowadzi w skrócony kreator zadania (zespół → osoba → priorytet → termin)
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

## 6. Kartoteka klientów (karta „Klienci" z board360)

Pełny odpowiednik karty „Klienci" z panelu. Klienci mają cache Room (lista
działa offline), dane lejka — główny etap, instalacje, deale wspólne — lecą
z `GET /api/deals`, `/api/deals/installations/current`, `/api/offers/deal-values`,
`/api/deals/contacts` i `/api/categories` przy wejściu i przy odświeżeniu.

- ✅ Lista z wyszukiwarką (imię, telefon, e-mail, adres, miejscowość)
- ✅ Zakładki kategorii: Klienci / Kontrahenci / Afilianci z licznikami
- ✅ Filtr głównego etapu (najświeższa szansa) — arkusz z kolorami etapów
- ✅ Filtr instalacji + „Wyczyść" (widoczne tylko przy aktywnym filtrze)
- ✅ Karta na liście: nazwisko, telefon i miejscowość, chip etapu, badge
  instalacji (PV/O/K…), liczba deali, tag „deal wspólny: X", licznik połączeń
- ✅ Dzwonienie wprost z listy, pull-to-refresh, osobne komunikaty dla pustej
  kartoteki, pustych filtrów i błędu
- ✅ Karta klienta w czterech zakładkach: Dane / Deale / Historia / Asystent
- ✅ Dane: telefony, e-maile, adres, status walidacji adresu (geo), dojazd
  z baz Kobiernice i Gliwice, kategoria i typ, instalacje, deale wspólne
- ✅ Szybkie akcje karty: Zadzwoń, SMS, Nawiguj (geo lub adres), Zadanie
- ✅ Deale: etap + wartość brutto → przejście do karty deala
- ✅ Historia: dotychczasowy timeline połączeń i notatek, wchłonięty do karty
- ✅ Asystent klienta (`POST /clients/:id/assistant`) — Q&A po notatkach i
  komunikacji ze wszystkich deali, z podpowiedziami i informacją o podstawie
- ✅ Dodanie wpisu (`deal.manage`) — etykieta FAB wg aktywnej zakładki
- ✅ Edycja danych (`PATCH /clients/:id`) — wysyłane tylko zmienione pola;
  zmiana adresu uruchamia serwerowo re-geokodowanie i przeliczenie dojazdu
- ✅ Scalanie duplikatów (te same nazwisko / telefon / e-mail) z wyborem
  rekordu docelowego i potwierdzeniem
- ✅ Anonimizacja RODO (`settings.company`) w menu karty, z potwierdzeniem
- ❌ Import klienta z kontaktów urządzenia (był w wersji sprzed board360)
- ❌ Podgląd i edycja części adresu (kod / miejscowość / ulica) — panel je
  rozbija sam przy geokodowaniu

---

## 6a. CRM — lejek sprzedaży (deale)

Moduł „CRM" z pulpitu. Dane wprost z board360 (`GET /api/deals`, `GET /api/deals/:id`),
bez cache Room — etap deala zmienia się często i po stronie panelu.

- ✅ Lejek jako lista pogrupowana etapami (mobilny odpowiednik tablicy Kanban)
- ✅ Etykiety etapów 1:1 ze `STAGE_LABEL` panelu (Lead … Po montażu)
- ✅ Filtry faz lejka: BOW / Sprzedaż / Etap montażowy / Po montażu
- ✅ Filtry „Zaległe" (minął `nextContactAt`) i „Moje" (deale zalogowanego)
- ✅ Wyszukiwarka po kliencie, mieście, źródle i opisie
- ✅ Karta na liście: klient + miasto, chip etapu, „X dni w etapie", badge zaległości
- ✅ Dzwonienie wprost z listy (klient doklejany z kartoteki po `clientId`)
- ✅ Pull-to-refresh, osobne komunikaty dla pustego lejka, pustych filtrów i błędu
- ✅ Karta deala: dane, LEAD (zgłoszenie + instalacje), dane budynku, OZC,
  spotkanie/audyt, historia zmian
- ✅ Zmiana etapu wg maszyny stanów board360 (tylko przejścia w przód + `lost`)
- ✅ Wymagany powód przy „Stracone" (zestaw kategorii zależny od etapu)
- ✅ Blokady walidacyjne z API (422 + `missing[]`) pokazywane po polsku
- ✅ Termin następnego kontaktu — skróty jutro / 3 dni / tydzień / 2 tygodnie
- ✅ Akcje zapisu widoczne tylko z uprawnieniem `deal.manage` (świeże z `GET /api/me`)
- ❌ Cofanie etapu po głównej ścieżce (API dopuszcza — korekta zostaje w panelu)
- ❌ Tworzenie deala, oferta, materiały, rozliczenie, pliki

### Zakładka „LEAD" karty deala

Odpowiednik zakładki `lead` z `DealDrawer` panelu. Dane z trzech źródeł, każde
z osobną obsługą błędu: `GET /api/intake/deal/:id/lead` (zgłoszenie z leadowni
cennikinstalacji.pl), `GET /api/deals/:id/installations` (migawka instalacji
etapu `lead`, już z dziedziczeniem) i `GET /api/categories` (nazwy węzłów).
Dociągane dopiero przy wejściu w zakładkę — reszta karty ich nie potrzebuje.

- ✅ Baner auto-kwalifikacji (`qualReview`) — powód i data zgłoszenia do decyzji,
  z ostrzeżeniem o automatycznym odrzuceniu po upływie okna
- ✅ Spotkanie wstępne: miejsce, termin, czas trwania, prowadzący, link +
  skrót „Umów"/„Zmień" do formularza pozostałych pól (`deal.manage`)
- ✅ Instalacje wybrane na etapie LEAD jako ścieżki katalogu
  („Ogrzewanie › Pompa ciepła"); nieznane id pokazywane surowo
- ✅ Zgłoszenie z leadowni: kanał (targi / strona www / telefon), źródło, dane
  podane przez klienta, zainteresowanie, budżet, zgoda, kto przyjął, data
- ✅ Notatka z rozmowy (kanał `tel`) / uwagi klienta — edycja z telefonu
  (`PATCH /api/intake/deal/:id/lead/note`, wymaga `deal.manage`)
- ✅ Dane budynku z kreatora /targi (kształt, konstrukcja, powierzchnia, osoby,
  kondygnacje, etap budowy, okna, piwnica, garaż)
- ✅ Deal spoza leadowni: zamiast pustej zakładki komunikat, gdzie szukać danych
- ❌ Edycja drzewa instalacji (drill-down do marek) — zostaje w panelu
- ❌ Artykuł wiedzy dla wybranej instalacji (`KnowledgeArticlePanel`) — wymaga
  ekranów katalogu, których mobile jeszcze nie ma
- ❌ Ikonografika budynku i ręczna korekta danych budynku ze zgłoszenia
  (`PATCH …/lead/building`) — te same wartości stoją niżej wypisane

### Edycja karty (ekran `deal/{id}/edit`)

Pełen zakres pól przyjmowanych przez `PATCH /api/deals/:id`. Zapis idzie jednym
żądaniem i wyłącznie z polami, które się zmieniły — równoległe zmiany w panelu
nie są nadpisywane. Puste pole = jawny `null` = wyczyszczenie wartości.

- ✅ Dane podstawowe: źródło, nazwa projektu, opis, kod rabatowy
- ✅ Segment, rodzaj budynku, trudność, buyer persona (z opcją „brak")
- ✅ Zgoda RODO, wyjątek „osoba starsza"
- ✅ Dane budynku: osoby, m², kondygnacje, rodzaj, konstrukcja, etap, okna, piwnica, garaż
- ✅ OZC: moc budynku, moc CWU, link do cieplo.app, potwierdzenie audytora
- ✅ Spotkanie: miejsce, termin (data + godzina), czas trwania, link, osoba wykonująca
- ✅ Audyt: miejsce, adres, termin, opiekun
- ✅ Opiekun deala i opiekun etapu (lista z `GET /api/tasks/members`)
- ✅ Dane do faktury: przełącznik „jak instalacji" + odbiorca / firma / NIP / adres
- ✅ Folder Drive
- ✅ Termin następnego kontaktu — pełny wybór daty i godziny + wyczyszczenie
- ✅ „Zapisz" aktywne tylko przy realnych zmianach; wyjście z niezapisanymi pyta o potwierdzenie
- ❌ `buildingPhoto` — zdjęcie budynku ustawia `POST /:id/building-photo` albo
  referencja `doc:<id>` z audytu; ręcznie wpisany URL rozjechałby się z panelem
- ❌ Selektory opiekunów bez uprawnienia `tasks.view` (brak listy zespołu — sekcja ukryta)

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

## 10. Pulpit (ekran startowy)

Kafelki modułów przeniesione z pulpitu board360 — etykiety, opisy, kolory i
ikony 1:1. Pominięte na mobile: Raporty, Zasoby, Marketing, Faktury KSeF.

- ✅ Pulpit jako ekran startowy po zalogowaniu + zakładka „Pulpit" w dolnym pasku
- ✅ Kafelki: Asystent, CRM, Klienci, Mapa, Komunikacja, Montaże, Serwis, Magazyn, Zadania, Kalendarz
- ✅ Kafelek „Klienci" → istniejąca kartoteka (ClientListScreen)
- ✅ Kafelek „CRM" → lejek sprzedaży (DealListScreen, §6a)
- ✅ Kafelek „Zadania" → lista zadań zespołu (TaskListScreen, §11a)
- 🚧 Pozostałe kafelki → ekran-zaślepka „wersja mobilna w przygotowaniu"
- ❌ Zmiana kolejności kafelków przeciąganiem (jest w board360, brak na mobile)

---

## 11a. Moduł Zadania (kafelek „Zadania")

Mobilny odpowiednik tablicy „Zadania" z board360. Zakres i makieta:
`design/mockups/modul-zadania.html`. Etapy: E1 lista → E2 karta zadania →
E3 kolejka offline → E4 przypomnienia → E5 komentarze i załączniki.

- ✅ Lista zadań z cache Room (`tasks`, baza w wersji 5) — widoczna bez zasięgu
- ✅ Filtr roli: Do wykonania / Zlecone / Wszystkie (wykonawca vs zlecający)
- ✅ Filtr osoby: Moje, Wszyscy, grupy Biuro / Montażyści / Pozostali, konkretna
  osoba, Nieprzypisane — grupy liczone jak w panelu (rola dodatkowa = główna)
- ✅ Filtry statusu, priorytetu, terminu (Dziś / Zaległe) i źródła (Klient / Projekt)
  w arkuszu od dołu + sortowanie (termin, priorytet, najnowsze, nazwa)
- ✅ Sekcje jako nagłówki grup (9 etapów lejka + „Bez sekcji"), z przełącznikiem
  na płaską listę — do rozstrzygnięcia z zamawiającym, co zostaje domyślnie
- ✅ Znacznik SLA (24 h / 7 dni / 30 dni) liczony od utworzenia, z ostrzeżeniem
  na ostatnich 25 % okna i alarmem po terminie
- ✅ Odhaczenie zadania i wysoki priorytet wprost z wiersza (`PATCH /api/tasks/:id`)
- ✅ Wyszukiwarka (tytuł, opis, osoba, źródło), pull-to-refresh, FAB w kreator
- ❌ Karta zadania z edycją pól (E2) — wymaga `GET /api/tasks/:id` w board360
- ❌ Kolejka zmian offline (E3) — dziś zapis wymaga sieci, awaria idzie w snackbar
- ❌ Przypomnienia i licznik nieprzeczytanych (E4)
- ❌ Komentarze i załączniki (E5) — brak też w atrapie board360-mock

---

## Pozostałe (poza scopem MVP)

- ❌ Dual SIM — wymaga testowania sprzętowego
- ❌ Push notifications z serwera — wymaga pracy po stronie backendu
- ❌ Row Level Security — weryfikacja po stronie backendu
- ❌ Alert gdy klient dzwonił do kilku pracowników — złożona logika grupowania
