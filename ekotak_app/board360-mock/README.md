# board360-mock

Testowy backend zgodny z **kontraktem board360**, do testów end-to-end aplikacji mobilnej TeamTalk (Android) bez czekania na wdrożenie prawdziwego board360. Jednocześnie stanowi **żywą referencję kontraktu** dla autora board360 (odpowiada promptom A1–A5).

Pokrywa **wszystkie endpointy, których używa `TeamTalkApi.kt`** — logowanie, kartotekę, CRM (lejek, karta deala, kontakty towarzyszące, instalacje), leadownię, telefonię i zadania.

- Dane trzymane **w pamięci** (seed przy starcie) — resetują się po restarcie.
- Auth: token HMAC `b360_session` w tym samym formacie co board360 (cookie lub `Authorization: Bearer`).
- Nagrania zapisywane na dysku w `uploads/` (w Dockerze na wolumenie `uploads`).

---

## Uruchomienie w Dockerze (zalecane)

```bash
docker compose up --build -d
```

Serwer nasłuchuje na `http://<host>:3001`. Podgląd logów i zatrzymanie:

```bash
docker compose logs -f mock
```

```bash
docker compose down
```

Konfiguracja przez `.env` obok `docker-compose.yml` (wzór: `.env.example`) — bez tego pliku wszystko ma sensowne wartości domyślne. Najczęściej zmieniane:

| Zmienna | Domyślnie | Do czego |
|---------|-----------|----------|
| `MOCK_PORT` | `3001` | port na hoście (w kontenerze zawsze 3001) |
| `SESSION_SECRET` | `dev-insecure-secret-change-me` | podpis tokenu; zmiana unieważnia sesje w telefonach |
| `MOBILE_SESSION_TTL` | `2592000` (30 dni) | czas życia sesji mobilnej |
| `STAGE_GATES` | `1` | blokady walidacyjne przejść etapu (422 + `missing[]`); `0` przepuszcza wszystko |
| `MOCK_TRANSCRIPT` | `1` | udawana transkrypcja po wgraniu nagrania |

### Tryb dev (hot-reload)

Kod z hosta zamiast tego z obrazu, restart procesu po każdym zapisie pliku:

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up
```

`node_modules` zostaje wtedy z obrazu, więc **nie trzeba mieć Node ani npm na maszynie**.

## Uruchomienie bez Dockera

Wymaga Node 18+ (globalny `fetch` i `node --watch`).

```bash
npm install
```

```bash
PORT=3001 SESSION_SECRET=dev-secret npm start
```

`npm run dev` uruchamia to samo z automatycznym restartem po zmianie pliku.

---

## Konta testowe

| E-mail | Hasło | Rola | Co potrafi |
|--------|-------|------|------------|
| `serwisant@ekotak.pl` | `test1234` | serwisant | podgląd CRM, telefonia, zadania (**bez** `deal.manage`) |
| `koordynator@ekotak.pl` | `test1234` | koordynator | jw. + edycja deali i kartoteki |
| `admin@ekotak.pl` | `admin1234` | admin | wszystko, w tym anonimizacja RODO (`settings.company`) |

Trzy konta zamiast jednego, bo tylko tak da się na telefonie sprawdzić, że przyciski zapisu znikają bez uprawnienia.

## Co jest w seedzie

- **10 klientów** — z geokodowaniem i dojazdem z obu baz, jeden bez adresu, kontrahent, afiliant i wpis „inne" (zakładki kategorii), oraz **para duplikatów „Marek Nowak"** do przetestowania scalania.
- **8 deali** — po jednym w kluczowych etapach lejka, jeden **zaległy** (filtr „Zaległe"), jeden **stracony**, jeden klient z dwoma dealami.
- **3 zgłoszenia z leadowni** (kanały `targi` / `www` / `tel`) — pozostałe deale celowo bez zgłoszenia, żeby dało się zobaczyć komunikat „deal spoza leadowni".
- **Katalog technologii** (5 kategorii głównych + podkategorie), wartości ofert, „deale wspólne", historia zmian, kolejka nieodebranych połączeń i notatka z transkrypcją.

---

## Endpointy

### Auth (A1)
- `POST /api/auth/mobile-login` `{email,password}` → `{token, expiresAt, user}`
- `GET /api/me` → AuthContext

### Kartoteka klientów
- `GET /api/clients?q=` — szuka po nazwisku, e-mailu, adresie, mieście i telefonie
- `GET /api/clients/:id`
- `POST /api/clients` (`deal.manage`) — wymaga imienia i nazwiska
- `PATCH /api/clients/:id` (`deal.manage`) — tylko przesłane pola; `null` czyści wartość. Zmiana adresu uruchamia **re-geokodowanie i przeliczenie dojazdu** (udawane, ale deterministyczne)
- `POST /api/clients/:id/merge` `{sourceIds}` (`deal.manage`) — scalenie duplikatów, nieodwracalne
- `POST /api/clients/:id/erase` (`settings.company`) — anonimizacja RODO, rekord zostaje
- `POST /api/clients/:id/assistant` `{messages}` → `{text, configured:false, commsCount, dealCount}`

### CRM — lejek
- `GET /api/deals?stage=&overdue=true` — bez klienta i historii (mobilka dokleja z kartoteki)
- `GET /api/deals/:id` — deal + `client` + `activities`
- `POST /api/deals/:id/stage` `{stage, lostReason?, lostReasonCategory?, note?}` (`deal.manage`)
- `PATCH /api/deals/:id` (`deal.manage`) — pola jak w `buildDealPatch`; `buildingData`/`ozcData` podmieniane w całości
- `GET|POST /api/deals/:id/contacts`, `PATCH /api/deals/:id/contacts/primary`, `DELETE /api/deals/:id/contacts/:clientId`
- `POST /api/deals/:id/assistant`
- `GET /api/deals/:id/installations` → `{current, stages[]}` z **dziedziczeniem** wyboru z wcześniejszych etapów

### Dane pochodne lejka
- `GET /api/deals/installations/current` → `{dealId: [idKategoriiGłównej]}`
- `GET /api/deals/contacts` → `[{dealId, clientId}]`
- `GET /api/offers/deal-values` → `{dealId: kwotaBrutto}`
- `GET /api/categories` → katalog technologii

### Leadownia
- `GET /api/intake/deal/:dealId/lead` — deal spoza leadowni dostaje **200 z pustym ciałem** (nie 404 — mobilka na tym polega)
- `PATCH /api/intake/deal/:dealId/lead/note` `{note}` (`deal.manage`)

### Telefonia (A2/A3)
- `POST /api/call-logs` (obiekt lub tablica) — idempotencja po `(userId, phoneNumber, startedAt)`
- `GET /api/call-logs?since=&limit=`
- `POST /api/voice-reports` `{callLogId?,clientId?,text?,durationSec?}`
- `POST /api/voice-reports/:id/recording` (multipart, pole `file`) → ustawia `recordingKey` i udawaną transkrypcję
- `GET /api/voice-reports?since=&limit=`
- `POST /api/devices` `{deviceId, model?, ...}` — upsert

### Zadania
- `GET /api/tasks/members` — z `functions[]` i `additionalRoles[]` (po nich kreator filtruje osoby pod kafelkami zespołów)
- `GET /api/tasks?status=&assignee=me`, `POST /api/tasks`
- `GET /api/tasks/:id` — jedno zadanie (karta otwierana z powiadomienia albo z dyskusji)
- `PATCH /api/tasks/:id` — pojedyncze pola (`status`, `dueAt`, `assigneeId`, `priority`, `section`, `estimatedMinutes`, `slaHours`), `DELETE /api/tasks/:id` → 204
- `GET /api/deals/:id/tasks`, `POST /api/deals/:id/tasks` — tak zadanie wiąże się z klientem (`Task` nie ma `clientId`)
- `GET /api/projects?status=active&templates=0`, `POST /api/projects/:id/tasks` (wymaga `projects.manage`)

Rekord zadania ma pełny kształt z board360 — `section`, `slaHours`, `estimatedMinutes`, `commentCount`,
`createdBy` oraz doklejane przy odczycie `dealName` / `projectName`. Sekcja zadania zakładanego pod dealem
wyprowadza się z etapu deala, o ile nie podano jej wprost.

### Komentarze i Komunikator wewnętrzny
- `GET /api/tasks/:id/comments`, `POST /api/tasks/:id/comments` `{body, mentions[]}` — `mentions[]` to
  TOKENY wywołań (`user:<id>`, `role:<rola>`, `watchers`, `all`), nie nazwiska; w tekście komentarza
  zostaje „@Imię Nazwisko". `DELETE /api/task-comments/:id` → 204
- `GET /api/discussions` — skrzynka: dyskusje, w których użytkownik bierze udział (wywołany LUB komentował)
- `GET /api/discussions/unread-count` → `{count}` (plakietka, powiadomienia)
- `GET /api/discussions/:taskId`, `POST /api/discussions/:taskId/read|unread`
- `POST /api/discussions/:taskId/comments` — odpowiedź ze skrzynki; ląduje jako komentarz pod zadaniem

Dyskusja **jest** wątkiem komentarzy jednego zadania — nie ma osobnej rozmowy obok. `title` liczy backend:
`Nazwisko · kod deala` dla zadań pod dealem, nazwa projektu dla projektowych, tytuł zadania dla luźnych
(ustalenia z 2026-09-01, `ekotak-app/docs/tasks/wywolanie-w-komentarzu.md`). Deal ma przez to pole `code`.

Seed zakłada trzy komentarze, w tym dwa wywołania — po zalogowaniu `serwisant@ekotak.pl` ma jedno
nieprzeczytane wywołanie od koordynatora.

**Czego jeszcze nie ma:** załączników zadań — wchodzą z etapem E5, patrz `design/mockups/modul-zadania.html`.
Nie ma też wątków deal-level z panelu (`/api/discussions/deal/:id`): mobilka ich nie woła.

### Kalendarz — prywatna zajętość (szare pola „Zajęte”)

Pracownik podpina w **panelu** sekretny adres iCal swojego prywatnego kalendarza, a board360
wyciąga z niego WYŁĄCZNIE godziny. Zespół widzi szare pola „Zajęte” — bez tytułów, opisów
i miejsc, bo tych rzeczy backend w ogóle nie zapisuje.

- `GET /api/calendar/private-link` → `{link, canOverrideBusy}` — moje podpięcie (link tylko jako maska)
- `PUT /api/calendar/private-link` `{url}` → podpina; **400** przy adresie bez `https`, bez `.ics` albo wewnętrznym
- `POST /api/calendar/private-link/refresh` → ręczne odświeżenie
- `DELETE /api/calendar/private-link` → **204**; kasuje link i całą zajętość
- `GET /api/calendar/events/private-busy?from&to[&userIds]` → `[{userId, startAt, endAt}]`
- `GET /api/calendar/events/freebusy` **dokłada** prywatną zajętość do firmowej

**Twarda kolizja 409.** `POST`/`PATCH` wydarzenia z `assigneeId`, którego prywatna zajętość zachodzi
na termin, wraca kodem **409** z `code: "private_busy"`, `userId`, `occurrences` i `slots[]`.
Samo `allowConflict=true` NIE wystarcza — przebija tylko rola z uprawnieniem `calendar.override_busy`
(w atrapie: admin, zarząd, koordynator, biuro; **nie** serwisant i nie montaż). Na tym da się na
telefonie sprawdzić obie ścieżki: monter dostaje ścianę, koordynator przycisk „mimo to”.

**Czym atrapa różni się od board360:** nie pobiera niczego z sieci. `PUT` sprawdza kształt adresu,
a zajętość GENERUJE syntetycznie (dni robocze, 12:00–13:00 i 17:30–19:00, −3…+21 dni), żeby dało się
testować bez konta Google i bez internetu. Seed daje takie bloki **serwisantowi** — po zalogowaniu
jako koordynator od razu widać cudze szare pola i można wywołać kolizję.

### Zdrowie
- `GET /api/health` → `{ok, service, time, counts}` (używane też przez `HEALTHCHECK` w obrazie)

---

## Blokady walidacyjne (422 + `missing[]`)

Przejście etapu może zostać odrzucone kodem 422 z ciałem `{message, missing:[...]}` — aplikacja pokazuje to jako „Uzupełnij w panelu: …". Zestaw blokad w mocku jest **przybliżeniem** board360 (dobranym tak, żeby dało się je przećwiczyć na telefonie):

| Wejście w etap | Wymaga |
|----------------|--------|
| Kwalifikacja | telefon klienta, e-mail klienta (chyba że `elderlyContactException`), zgoda RODO |
| Audyt | termin i miejsce spotkania wstępnego |
| Oferta | termin audytu, powierzchnia budynku, moc budynku (OZC) |
| Sprzedane | potwierdzenie OZC przez audytora, dane do faktury |
| Przed montażem | opiekun etapu |

Przejście na „Stracone" zawsze wymaga `lostReasonCategory`, a zestaw kategorii zależy od etapu (z etapu „Lead" obowiązuje zestaw leadowy). Wyłączenie wszystkich blokad: `STAGE_GATES=0`.

Maszyna przejść jest kopią `ALLOWED_TRANSITIONS` z aplikacji (`domain/model/Deal.kt`) — mock nie odrzuci przycisku, który appka pokazuje.

---

## Szybki test

```bash
TOKEN=$(curl -s localhost:3001/api/auth/mobile-login -H 'Content-Type: application/json' -d '{"email":"koordynator@ekotak.pl","password":"test1234"}' | node -pe 'JSON.parse(require("fs").readFileSync(0)).token')
```

```bash
curl -s localhost:3001/api/me -H "Authorization: Bearer $TOKEN"
```

```bash
curl -s localhost:3001/api/deals -H "Authorization: Bearer $TOKEN"
```

## Konfiguracja aplikacji Android

- `local.properties`: `API_BASE_URL=http://100.78.184.117:3001` (przez Tailscale) **lub**
- telefon przez USB + `adb reverse tcp:3001 tcp:3001` i `API_BASE_URL=http://localhost:3001`.

## Układ plików

```
server.js              wiring aplikacji Express, healthcheck, obsługa błędów
src/config.js          konfiguracja z ENV
src/crypto.js          token sesji (HMAC) i hasła (scrypt)
src/rbac.js            role i uprawnienia
src/deal-rules.js      maszyna stanów lejka + blokady walidacyjne
src/store.js           baza w pamięci i helpery
src/seed.js            dane startowe
src/middleware.js      requireAuth / requirePermission / 422
src/routes/            auth, clients, deals, intake, catalog, telephony, tasks
```
