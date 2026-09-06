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
- ❌ Tworzenie deala, materiały, rozliczenie, pliki (oferta: patrz zakładka niżej)

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

### Zakładka „Audyt" karty deala

Odpowiednik zakładki `audyt` z `DealDrawer` panelu. Trzy bloki, w kolejności
używania: spotkanie audytowe (termin i dojazd), formularz audytu instalacji
(podstawa oferty) i lista Heizlast. Dane z czterech źródeł, każde z osobną
obsługą błędu: `GET /api/deals/:id/audits`, `GET /api/categories` (szablon
`auditForm` do dziedziczenia), `GET /api/deals/:id/installations` (migawka
etapu `audit`) i `GET /api/deals/:id/contracts` (czy oferta jest zamknięta
podpisem). Dociągane dopiero przy wejściu w zakładkę.

- ✅ Spotkanie audytowe: miejsce (instalacja / biuro / online) i termin zapisują
  się od razu, bez trybu edycji; adres i osoba wykonująca do odczytu
- ✅ Akcja miejsca: „Wyznacz trasę" (intent `geo:`) przy audycie u klienta,
  „Otwórz spotkanie" (link) przy audycie online — jak zielony pasek w panelu
- ✅ Wybór instalacji z migawki etapu „Audyt"; formularz DZIEDZICZONY z katalogu
  (najbliższy przodek z `Category.auditForm`), więc marka pyta o to samo,
  o co pyta technologia nad nią
- ✅ Formularz audytu OP w trzech zwijanych sekcjach: Dane ogólne (system rur,
  sterowanie, napełnienie, chłodzenie przy pompie ciepła), Dane instalacji
  (8 pytań + warunkowe: medium i inhibitor, dokumentacja gwarancji) z licznikiem
  „x / y" na belce, Kondygnacje (nazwa, metraż wg projektu, system, rozdzielacze,
  skrzynka, metraże wg rozstawu rur, komentarz) z sumą i porównaniem z projektem
- ✅ Pierwsze wypełnienie startuje z szablonu katalogu uzupełnionego danymi
  budynku (ilość i nazwy kondygnacji) — tak samo jak panel
- ✅ Rozstaw 5 cm znika przy rurze ⌀18 (nie da się jej tak wygiąć)
- ✅ Lista braków + „Zapisz — brakuje N" na pomarańczowo; niekompletny audyt
  zapisuje się normalnie (uzupełnia się go na raty)
- ✅ Blokada po podpisie umowy: formularz do odczytu, pasek mówi którą umowę
  klient podpisał i czy zmiana jest już w toku
- ✅ Heizlast: lista wpisów (tryb, data, kW, notatka) + nowy w trybie szybkim
  (metraż, wysokość, standard budynku, podgląd kW) albo DIN (wynik zewnętrzny)
- ❌ Rysowanie po rzucie kondygnacji: kropki rozdzielaczy, pomiar metrażu
  z obrysów, kalibracja skali, źródło ciepła, historia zmian na rzucie. To
  rysowanie po planie budynku — palcem na 360 dp nie da się tego zrobić
  uczciwie. Zapisane wartości PRZECHODZĄ przez telefon nietknięte
  (`UfhFloor.planJson`), więc zapis z terenu nie kasuje pracy z panelu
- ❌ Automat zmiany oferty po podpisie (nowa umowa / aneks z policzonym
  rozpisem) — ruch kończy się dokumentem do podpisu, robi się go w panelu
- ❌ Materiał, długości rur, pojemność wodna i punkty montażowe liczone z audytu
  — to widoki wynikowe, nie pytania do audytora
- ✅ Kolejka offline i cache (baza w wersji 13). Audyt robi się w domu w budowie,
  gdzie zasięgu zwykle nie ma, a formularz jest podstawą oferty — utrata
  odpowiedzi wpisanych przy kliencie to drugi dojazd. Szczegóły niżej.

#### Kolejka offline zakładki „Audyt"

Cache (`audits`, `catalog_categories`, `audit_installations`) plus kolejka
(`audit_mutations`), opróżniana przez `AuditSyncWorker` na warunek sieci —
bez odpytywania, system budzi robotnika sam. Odczyt: najpierw sieć, przy jej
braku cache. Zapis: najpierw sieć, przy jej braku kolejka i od razu cache.

- ✅ Formularz audytu instalacji zapisany bez zasięgu ląduje w kolejce; nagłówek
  bloku pokazuje „czeka na wysyłkę", a komunikat mówi wprost „zapisano
  w telefonie — wyślemy, gdy wróci zasięg" (samo „zapisano" audytor przeczytałby
  jako „panel już to ma")
- ✅ Nowy wpis Heizlast bez zasięgu dostaje lokalne id i czeka w kolejce.
  kW szybkiego szacunku liczy SERWER, więc do wysyłki wiersz pokazuje „czeka
  na wysyłkę" zamiast zmyślonego wyniku
- ✅ Jeden wiersz kolejki = CAŁY dokument `formData`, nie pojedyncze pole
  (inaczej niż w Zadaniach): API podmienia go w całości, więc scalanie dwóch
  zapisów dałoby formularz, którego nikt nie wypełnił. Kolejny zapis tego
  samego audytu nadpisuje poprzedni — liczy się ostatnia decyzja audytora
- ✅ Po wysłaniu rekordu założonego offline cache i kolejka przechodzą na id
  nadane przez serwer, a czekający zapis zmienia się z `POST` na `PATCH` —
  bez tego deal dostałby DWA formularze tego samego węzła
- ✅ Odmowa serwera (409 przy podpisanej umowie, 403, 404) porzuca wpis
  i mówi o tym przez skrzynkę `syncProblem` — ponowienie nic by nie zmieniło,
  a to praca człowieka przepadła
- ✅ Migawka instalacji cache'uje też węzły ze WSZYSTKICH etapów, bo z nich
  rozpoznajemy pompę ciepła. Bez tego audyt zapisany offline chowałby pytanie
  o chłodzenie i wysyłał `cooling: false`, kasując odpowiedź daną w panelu
- ⚠️ Bez zasięgu nie wiemy, czy oferta jest zamknięta umową (`/contracts` nie
  odpowiada). Formularz zostaje wtedy OTWARTY: audytor ma zapisać to, co
  zmierzył, a rozjazd i tak wychwyci serwer przy wysyłce (409)
- ❌ Wejście w kartę deala nadal wymaga sieci (`DealRepository` świadomie bez
  cache). Kolejka ratuje pracę zaczętą w zasięgu i ciągniętą dalej bez niego,
  ale po ubiciu aplikacji offline audytor nie dojdzie do zakładki. Pełne
  offline wymaga cache lejka — osobna decyzja, bo to zmiana ustalenia z karty

#### Do sprawdzenia na urządzeniu (kolejka audytu)

1. Tryb samolotowy → wypełnij formularz audytu → „Zapisz". Komunikat ma mówić
   „w telefonie", a nagłówek bloku „czeka na wysyłkę". Zabij aplikację i wejdź
   ponownie — odpowiedzi mają zostać.
2. Wyłącz tryb samolotowy → w ciągu chwili znacznik znika, a panel pokazuje
   zapisany formularz.
3. Bez zasięgu zapisz formularz DWA razy (druga wersja z innym metrażem) —
   na serwer ma pójść JEDEN rekord, z drugą wersją.
4. Bez zasięgu dodaj wpis Heizlast w trybie szybkim → po powrocie sieci wiersz
   ma dostać kW policzone przez serwer.
5. Bez zasięgu zapisz audyt deala z podpisaną umową → po powrocie sieci zapis
   ma przepaść z komunikatem o umowie, a nie krążyć w kolejce.

#### Do sprawdzenia na urządzeniu (round-trip warstwy rzutu)

Projekt nie ma testów jednostkowych, a przejścia `formData` przez telefon nie
da się sprawdzić kompilacją. Atrapa ma na to gotowe dane: deal na etapie „Audyt"
niesie formularz OP z kropką rozdzielacza, obrysem pomieszczenia, skalą,
historią i podpisami. Scenariusz po `gradlew installDebug`:

1. Deal na etapie „Audyt" → zakładka „Audyt" → instalacja „Ogrzewanie
   podlogowe". Formularz ma być wypełniony wartościami z panelu.
2. Zmień JEDNO pole (np. komentarz kondygnacji) i zapisz.
3. `GET /api/deals/:id/audits` — w `formData.floors[0]` muszą nadal być
   `manifoldMarks`, `heatSource`, `manifoldHistory`, `rooms`, `planScale`,
   `planSlot`, `planDocId` oraz podpisy `marksSavedBy` / `areaSavedBy`.
   Zniknięcie któregokolwiek = utrata pracy zrobionej w panelu.
4. `manifolds` ma zostać liczbą całkowitą (`1`, nie `1.0`).

### Zakładka „Oferta" karty deala

Odpowiednik `DealOfferPanel` panelu — trzy widoki tej samej instalacji: „Oferta
dla klienta" (dlaczego to rozwiązanie + zakres w liczbach), „Podsumowanie"
(pozycje z kwotami netto) i „Widok techniczny" (specyfikacja w układzie
konfiguratora ekotak.pl). Instalacje bierzemy kaskadą etapów od „Oferty" w dół
(`angebot` → `audit` → `sold` → `montaz` → `edukacja` → `lead`), audyt
z `GET /api/deals/:id/audits`, blokadę z `GET /api/deals/:id/contracts`.

Rachunek jest PORTEM, nie przybliżeniem: `domain/ufh` powtarza w Kotlinie
`offer-quote.ts`, `ufh-points.ts`, `ufh-pipe-length.ts`, `ufh-zone-split.ts`,
`ufh-area-measure.ts`, `price-catalog.ts`, `price-model.ts` i `offer-scope.ts`.
Telefon liczy z tych samych danych, co przeglądarka w panelu — łącznie
z pomiarem po rzucie i podziałem pomieszczeń na pętle.

- ✅ Metraż i rura z warstwy rzutu (obrysy, wycięcia, łatki zagęszczenia, skala,
  kropki rozdzielaczy) — telefon rzutu nie rysuje, ale go CZYTA, więc liczby
  zgadzają się z panelem co do dziesiątej
- ✅ „Dlaczego zaproponowaliśmy to rozwiązanie" — każda decyzja audytu jako
  zdanie dla klienta; przy „Zaproponuj" system nazwany po imieniu
- ✅ Zakres w liczbach (powierzchnia, rura, obwody, rozdzielacze, szafki)
  z ostrzeżeniami rachunku
- ✅ Podsumowanie z kwotami: ilości z audytu × ceny jednostkowe z formuły ceny
  węzła (Magazyn + materiały domyślne Technologii + marka szafek + stawki
  i koszty z Warunków finansowych + narzut węzła), z rozbiciem materiał /
  robocizna i listą braków cennika
- ✅ Widok techniczny 1:1 z konfiguratorem (parametry główne → kondygnacje →
  parametry instalacji → uzupełnia inżynier → wyliczenia z audytu)
- ✅ Pasek „oferta zamknięta umową" nad każdym stanem zakładki
- ✅ Bez zasięgu: kartoteka Magazynu z cache modułu Magazyn, ustawienia firmowe
  i zestawy cennika z ostatniego pobrania
- ❌ „Modyfikuj ofertę" (nowa umowa / aneks) — zostaje w panelu, jak zmiana
  audytu po podpisie
- ❌ Zamrożone wersje oferty i historia zmian (panel też ich jeszcze nie ma)

#### Do sprawdzenia na urządzeniu (zgodność z panelem)

Rachunek został porównany z panelem poza aplikacją (ten sam audyt policzony
kodem TS i kodem Kotlin — trzy scenariusze, wynik znak w znak), ale warto
potwierdzić go na żywych danych:

1. Deal z wypełnionym audytem OP → zakładka „Oferta" → „Podsumowanie".
2. Ten sam deal w panelu, zakładka „Oferta" → „Podsumowanie".
3. „Razem netto", liczba obwodów i rura razem muszą się zgadzać co do grosza
   i co do dziesiątej metra.

### Zakładka „Zamówienie" karty deala

Odpowiednik zakładki `zamowienia` z `DealDrawer` panelu — te same trzy bloki,
przestawione pod kciuk: zakres (drzewo etapu `sold`, **zwinięte** do nagłówka,
bo na 360 dp wypycha resztę pod krawędź), rezerwacja materiału i zamówienia.
Cztery odczyty, każdy z osobną obsługą błędu, bo każdy stoi na innym
uprawnieniu: `GET /api/deals/:id/orders` (`order.manage` — w board360 to
uprawnienie gate'uje TAKŻE odczyt), `GET /api/deals/:id/offers` (`crm.view`),
`GET /api/inventory/reservations?status=all&dealId=` i
`GET /api/inventory/orders?status=all&dealId=` (`inventory.view`).

- ✅ Zakres jako czysty podgląd: drzewo przycięte do wyboru klienta
  (`pruneToSelected` — bez pustych kategorii i wyszarzonych marek, jak
  `onlyPicked` w panelu); zakres zamówienia zmienia się wyłącznie przez ofertę
- ✅ Karta zamówienia z nazwą instalacji w nagłówku (z umowy powstaje po jednym
  zamówieniu na instalację), znacznikiem „z umowy", stanem liczonym z pozycji
  i licznikiem „n / m odebrane"
- ✅ Dwa ptaszki przy pozycji („zamów." / „odebr.") pod nazwą, nie obok —
  nazwy magazynowe są za długie na jedną linię z dwoma polami wyboru
- ✅ Ręczne zakładanie zamówienia z WYGRANEJ oferty (selektor + przycisk),
  droga awaryjna pod listą — zamówienia zwykle powstają same z umowy
- ✅ Rezerwacja materiału: trzy liczby magazynu (potrzeba / z magazynu /
  brakuje), stan zakupu linii (propozycja → lista zakupowa → zamówione + ETA →
  przyjęte), ostrzeżenie o pozycjach bez kartoteki
- ✅ „ZAMÓW braki (n)" — tylko pozycje, których nie objął żaden zakup; reszta
  ma już propozycję albo dostawę w drodze i drugie kliknięcie kupiłoby podwójnie
- ✅ „Wydane" / „Zwolnij" / „Przywróć" przy linii rezerwacji
- ✅ Brak uprawnienia chowa POJEDYNCZY blok z wyjaśnieniem, a nie całą zakładkę
- ⛔ „Przelicz z audytu" **zostaje w panelu** — zestawienie liczy się z całego
  audytu podłogówki (rozdzielacze, długości rur, chemia); druga implementacja
  tej matematyki na telefonie zamawiałaby zły towar przy pierwszym rozjeździe

#### Kolejka offline zakładki „Zamówienie"

Baza **14 → 15**: `deal_orders`, `deal_offers`, `order_mutations`, kolumna
`inventory_orders.reservationId` i `audit_installations.soldStageCategoryIds`
(drzewo zakresu ma się rysować bez zasięgu). Kolejkę opróżnia `OrderSyncWorker`
z warunkiem sieci — bez odpytywania.

- ✅ Cztery rodzaje zapisu w JEDNEJ kolejce (`order_create`, `order_item`,
  `reservation`, `purchase`) — wspólna kolejność wysyłki, bo zamówienie
  założone offline musi pójść przed zakupami braków, które przy nim powstały
- ✅ Niewysłane zmiany **nakładane przy odczycie**, nie wpisywane w cache:
  odświeżenie Magazynu podmienia rezerwacje hurtem i skasowałoby ptaszek
  postawiony przed chwilą w kotłowni
- ✅ Drugi ptaszek przy tej samej pozycji **dokłada się** do czekającego ciała
  („zamów." i „odebr." to dwa niezależne pola)
- ✅ Zamówienie zakolejkowane offline stoi na liście puste, z podpisem, że
  pozycje dopisze panel — kopiuje je z oferty, więc zgadywanie ich na telefonie
  dałoby listę do odhaczania inną niż ta po synchronizacji
- ✅ Odmowa serwera (403/404/409) kończy wpis i mówi o tym człowiekowi
  (`SessionPreferences.saveSyncProblem`) — ponowienie nic by nie zmieniło

#### Do przeklikania na urządzeniu

1. Deal „Instal Serwis" → zakładka „Zamówienie": zamówienie z umowy, cztery
   pozycje, rezerwacja z pięcioma wariantami wiersza (atrapa ma na to seed).
2. Tryb samolotowy → odhacz „odebr." przy dwóch pozycjach i zwolnij jedną
   rezerwację → wszystko z niebieskim „czeka na wysyłkę"; wróć w zasięg
   i sprawdź `GET /api/deals/:id/orders`.
3. Konto `montaz` (bez `order.manage`): blok magazynu widoczny, blok zamówień
   z wyjaśnieniem. Konto `serwisant`: oba bloki z wyjaśnieniem.
4. Deal „Wojcik — PV + magazyn": selektor pokazuje TYLKO ofertę `won`;
   po utworzeniu zamówienie ma 3 pozycje przepisane z oferty.

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
- ✅ Kafelek „Komunikacja" → Komunikator wewnętrzny (DiscussionListScreen, §11a)
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
- ✅ Karta zadania (E2) — `GET /api/tasks/:id` dopisane w board360 i w atrapie;
  nagłówek ze źródłem, odhaczenie, priorytet, wątek komentarzy oraz edycja pól:
  status, wykonawca, termin, sekcja, potrzebny czas i SLA. Każde pole zapisuje
  się osobno (`PATCH` z jednym kluczem), bez trybu edycji i przycisku „Zapisz" —
  dzięki temu porcja trafia też osobno do kolejki offline
- ✅ Kolejka zmian offline (E3) — zmiana bez zasięgu ląduje w `task_mutations`
  (baza w wersji 6) i od razu w cache, wiersz dostaje znacznik „czeka na wysyłkę",
  a `TaskSyncWorker` wysyła ją, gdy system zobaczy sieć. Jeden wiersz kolejki =
  jedno pole, więc dwie zmiany tego samego zadania nie cofają się nawzajem;
  odmowa serwera (403/404/422) porzuca zmianę i mówi o tym na liście
  ⚠️ nieprzetestowane na telefonie — patrz „Do sprawdzenia na urządzeniu"
- 🚧 Przypomnienia i licznik nieprzeczytanych (E4) — `MentionsWorker` pilnuje
  wywołań (@) co 15 min, `TaskReminderWorker` co 6 h przypomina o zadaniach na
  dziś i zaległych (raz dziennie, liczone z cache, więc działa bez zasięgu);
  brak plakietki z licznikiem na kafelku pulpitu
- 🚧 Komentarze (E5) — wątek w karcie zadania z wywołaniami przez `@`;
  załączniki nadal bez wsparcia (brak też w atrapie board360-mock)
- ✅ Wywołanie osoby przez `@` w komentarzu → dyskusja w Komunikatorze
  (kafelek „Komunikacja" → skrzynka, wiersz podpisany `Nazwisko · kod deala`,
  wejście prowadzi w kartę zadania). Ustalenia:
  `../ekotak-app/docs/tasks/wywolanie-w-komentarzu.md`

### Do sprawdzenia na urządzeniu (kolejka offline)

Projekt nie ma testów jednostkowych, a kolejki nie da się sprawdzić kompilacją.
Scenariusz na telefon po `gradlew installDebug`:

1. Tryb samolotowy → odhacz zadanie → wiersz zostaje odhaczony i pokazuje
   „czeka na wysyłkę"; zabij aplikację i wejdź ponownie — znacznik ma zostać.
2. Wyłącz tryb samolotowy → w ciągu chwili znacznik znika, a panel pokazuje
   zmieniony status (WorkManager budzi robotnika na warunek sieci).
3. Bez zasięgu zmień to samo zadanie dwa razy (status, potem priorytet) —
   po powrocie sieci mają wejść obie zmiany, jednym żądaniem.
4. Bez zasięgu odhacz zadanie, usuń je w panelu, wróć w zasięg — zadanie znika
   z listy, a na dole pojawia się komunikat „Zmiana … przepadła".

---

## 12. Moduł Magazyn (kafelek „Magazyn")

- ✅ Koncepcja i makieta (2026-09-04) — `design/mockups/modul-magazyn.html`:
  cztery zakładki (Stan · Dostawy · Spis · Braki), skaner kodów z etykiet
  drukowanych w panelu, wydanie jako przycisk w karcie pozycji (nie osobna
  karta). Oferty, faktury, raport, archiwum i pełna edycja kartoteki zostają
  w panelu — na telefon wchodzi tylko to, co robi się na stojąco
- ❌ Cały moduł — kafelek `inventory` (`HomeModules.kt`) prowadzi do zaślepki;
  brak DTO, encji Room, repozytorium i tras w `TeamTalkApi.kt`
- ❌ Braki po stronie board360 (warunek wstępny E1–E4): `GET /api/products`
  bez `q`, stronicowania i `updatedSince`; brak `GET /api/products/lookup?code=`
  dla skanera; brak idempotencji na `POST /products/:id/movements` (kolejka
  offline zdubluje przyjęcie po ponowieniu); brak tras magazynu w atrapie
  `board360-mock`

### Do ustalenia (odpowiedzi zamawiającego)

Siedem pytań z sekcji 07 makiety. Pierwsze jest blokujące — dziś żadna rola
używana w TeamTalku nie może zaksięgować ruchu magazynowego.

1. ❓ **Kto może wydawać i przyjmować z telefonu?** `serwisant`, `montaz`
   i `stazysta` mają w `permissions.ts` tylko `inventory.view`;
   `inventory.manage` ma admin, zarząd i koordynator.
   Opcje: nadpisanie per osoba dla magazyniera · nowa rola `magazyn` · monter
   też dostaje `manage` · telefon zostaje czytelnią.
   Rekomendacja: nadpisanie per osoba, monter przy podglądzie
2. ❓ **Czy „Wydane" z rezerwacji ma księgować ruch?** Dziś `PATCH
   /inventory/reservations/:id` ze statusem `done` nie rusza stanu.
   Opcje: jedna akcja robi oba (zmiana także w panelu, trasa
   `/reservations/:id/issue`) · telefon jak panel, dwa kroki.
   Rekomendacja: jedna akcja — inaczej panel i telefon liczą magazyn inaczej
3. ❓ **Czy wydanie musi wskazywać „pod kogo"?**
   Opcje: zawsze deal albo rezerwacja · wolno „na magazyn" z notatką.
   Rekomendacja: wolno „na magazyn", ale notatka obowiązkowa
4. ❓ **Co czyta skaner?** Etykiety z panelu kodują `code ?? id`, opakowania
   mają własne EAN-y, których w kartotekach częściowo nie ma.
   Opcje: tylko nasze QR · QR + EAN z dopisywaniem kodu do kartoteki.
   Rekomendacja: oba, plus pytanie „kod nieznany — przypisać do pozycji?"
5. ❓ **Czy stan ma spadać od razu, przed wysłaniem?**
   Opcje: optymistycznie z licznikiem „czeka" · dopiero po potwierdzeniu.
   Rekomendacja: optymistycznie, ze znacznikiem „2 ruchy czekają"
6. ❓ **Ile kartoteki wolno poprawić z telefonu?**
   Opcje: lokalizacja + uwagi · dodatkowo progi min/maks · pełna edycja.
   Rekomendacja: lokalizacja i uwagi — progi ruszają automatem zakupowym
7. ❓ **Czy zakładka Braki wchodzi w pierwszej wersji?**
   Opcje: cztery zakładki od razu · trzy, Braki na koniec (E5) albo wcale.
   Rekomendacja: zaplanować cztery, wypuścić Braki jako ostatnie

---

## Pozostałe (poza scopem MVP)

- ❌ Dual SIM — wymaga testowania sprzętowego
- ❌ Push notifications z serwera — wymaga pracy po stronie backendu
- ❌ Row Level Security — weryfikacja po stronie backendu
- ❌ Alert gdy klient dzwonił do kilku pracowników — złożona logika grupowania
