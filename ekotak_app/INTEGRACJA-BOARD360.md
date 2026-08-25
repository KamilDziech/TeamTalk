# Integracja TeamTalk (Android) ↔ board360 (VPS)

Dokument dzieli pracę na dwie strony:
- **CZĘŚĆ A — SERWER (board360)** — do wykonania przez autora aplikacji webowej. Zawiera gotowe prompty/zadania.
- **CZĘŚĆ B — APLIKACJA (TeamTalk Android)** — do wykonania po naszej stronie.

Stary backend Express (`100.78.184.117:5433`) jest wycofywany — board360 na VPS go zastępuje. EKOTAK = jedna organizacja (jeden `organizationId`).

---

## Kontekst techniczny (ustalony z analizy serwera — read only)

- Monorepo: `git@github.com:ekotak/board360.git`, katalog na VPS `/opt/ekotak/app`, branch **dev**.
- Backend `api/` = **NestJS 10**, architektura **heksagonalna** (moduł = `domain` / `application` (+`ports`) / `infrastructure` / `presentation`), **Prisma 5**, **Argon2**, **Zod**, storage przez port `FILE_STORAGE` (adapter S3/MinIO).
- Web `web/` = Next.js (BFF). Caddy: `/api/*` → NestJS (:3001), reszta → Next.js (:3000). Domeny: prod **https://ekotak.app**, dev **https://dev.185.193.112.175.sslip.io**.
- **Auth**: sesja = stateless token HMAC w cookie `b360_session`:
  `token = base64url(JSON{sub,userId,organizationId,role,exp}) + "." + HMAC_SHA256(SESSION_SECRET, payload)`.
  Token **podpisuje web** (`web/src/lib/auth.ts` → `createSessionToken`), a **API tylko go weryfikuje** (`api/src/modules/iam/infrastructure/security/hmac-session-token.ts`). `SESSION_SECRET` współdzielony przez `.env`. TTL sesji web = 8h (`web/src/lib/session.ts`, `SESSION_MAX_AGE`).
- `POST /api/auth/login` sprawdza tylko hasło i **nie ustawia cookie** — zwraca `{sub,userId,organizationId,role,permissions}`.
- `SessionAuthGuard` czyta token z **cookie `b360_session`** (`iam/presentation/guards/session-auth.guard.ts`), `ResolveSession` dobiera aktualne uprawnienia z bazy.
- RBAC: `PERMISSIONS` w `api/src/modules/iam/domain/permissions.ts`; role: `admin, zarzad, koordynator, serwisant, biuro, montaz, stazysta`. Serwisant = użytkownik TeamTalk.
- Baza (`board360`, user `app`): brak jakichkolwiek tabel telefonii (call/voice/device). Tabela `clients` ma `phone`, `phone2`, `email`, geo. `users` + rozszerzenie `technicians`.
- Migracje: entrypoint api robi `npx prisma migrate deploy` przy starcie. Dev bez rebuildu: `./dev.sh preview`.

---

# CZĘŚĆ A — SERWER (board360). Instrukcje dla autora webówki

Cel: udostępnić aplikacji mobilnej **(1)** logowanie zwracające token sesji, **(2)** moduł telefonii (rejestr połączeń + notatki/nagrania głosowe), **(3)** uprawnienie RBAC dla serwisanta. Klienci i profil już istnieją — mobilka użyje `GET /api/clients` i `GET /api/me` bez zmian.

**Zasada bezpieczeństwa pracy:** wszystko najpierw na branchu (np. `feature/mobile-teamtalk`) i na środowisku **dev** (`./dev.sh preview`, URL `https://dev.185.193.112.175.sslip.io`), migracje na dev DB, testy, dopiero potem merge do `dev`/prod i deploy.

---

### Zadanie A1 — Endpoint logowania mobilnego (zwraca token sesji)

**Problem:** API weryfikuje token, ale go nie wystawia (robi to web). Mobilka nie przechodzi przez web, więc potrzebuje endpointu, który po weryfikacji hasła **podpisze i zwróci** token w formacie zgodnym z `b360_session`.

**Prompt do wykonania:**

> W module `api/src/modules/iam` dodaj wystawianie tokenu sesji dla klientów mobilnych, w pełni zgodne z istniejącym formatem cookie `b360_session`.
>
> 1. Dodaj port `SessionTokenIssuer` w `application/ports/session-token.port.ts` obok istniejącego `SessionTokenVerifier`:
>    ```ts
>    export const SESSION_TOKEN_ISSUER = Symbol('SessionTokenIssuer');
>    export interface SessionTokenIssuer {
>      issue(payload: SessionPayload): string; // ten sam format co web/src/lib/auth.ts
>    }
>    ```
> 2. W `infrastructure/security/hmac-session-token.ts` zaimplementuj `SessionTokenIssuer.issue()` używając istniejącej prywatnej `sign()` — payload = `base64url(JSON.stringify({sub,userId,organizationId,role,exp}))`, token = `payload + '.' + sign(payload)`. **Nie zmieniaj** formatu ani sekretu (`SESSION_SECRET`). To musi produkować dokładnie to samo co `createSessionToken` w `web/src/lib/auth.ts`.
> 3. Dodaj use-case `application/issue-mobile-session.usecase.ts`, który: woła istniejący `AuthenticateUser` (Argon2 + audyt + rate-limiting jak dziś), a po sukcesie wystawia token z `exp = clock.nowSeconds() + MOBILE_SESSION_TTL`. Dodaj `MOBILE_SESSION_TTL` (np. 30 dni = `60*60*24*30`) do konfiguracji/env, bo web ma tylko 8h a mobilka nie ma przeglądarkowego re-loginu.
> 4. W `presentation/auth.controller.ts` dodaj `POST /api/auth/mobile-login` (reużyj `LoginThrottlerGuard` i `ZodValidationPipe(loginSchema)`), zwracający:
>    ```json
>    { "token": "<b360_session>", "expiresAt": 1730000000,
>      "user": { "userId": "...", "organizationId": "...", "email": "...", "role": "serwisant", "permissions": ["..."], "clientVisibility": "all" } }
>    ```
>    (`user` zbuduj tak jak `GET /api/me` zwraca `AuthContext` — reużyj `ResolveSession`/`effectivePermissions`.)
> 5. Zarejestruj nowy provider w `iam.module.ts` (`{ provide: SESSION_TOKEN_ISSUER, useClass: HmacSessionToken }`) i wyeksportuj co trzeba.
> 6. Testy jednostkowe: token z `issue()` przechodzi `verify()`; endpoint zwraca 401 dla złych poświadczeń; rate-limiting działa.

**Efekt dla mobilki:** mobilka wyśle ten `token` w każdym żądaniu jako nagłówek `Cookie: b360_session=<token>` — `SessionAuthGuard` działa bez żadnych zmian.

**Opcjonalne ułatwienie (A1b):** dodać w `SessionAuthGuard` odczyt tokenu także z `Authorization: Bearer <token>` (jeśli brak cookie), żeby mobilka nie musiała udawać cookie. Zmiana 3-linijkowa, nieinwazyjna. Jeśli zrobicie — dajcie znać, po stronie mobilki użyjemy Bearer.

---

### Zadanie A2 — Model danych telefonii (Prisma)

**Prompt do wykonania:**

> W `api/prisma/schema.prisma` dodaj modele telefonii mobilnej. Trzymaj konwencje: `id String @id @default(uuid())`, `organizationId` na każdej encji głównej (multi-tenant), `@@map` snake_case, `@@index([organizationId])`. Relacja do `Client`/`User` po `organizationId + id`, `clientId` nullable (dopasowanie po numerze bywa nietrafione).
>
> ```prisma
> enum CallDirection { inbound outbound missed }
>
> /// Rejestr połączeń telefonicznych serwisanta (aplikacja mobilna TeamTalk).
> model CallLog {
>   id             String        @id @default(uuid())
>   organizationId String
>   userId         String        // serwisant (User.id)
>   clientId       String?       // dopasowany klient (po numerze), nullable
>   phoneNumber    String        // numer drugiej strony (E.164 jeśli się da)
>   direction      CallDirection
>   simSlot        Int?          // dual-SIM: 0/1 lub null
>   startedAt      DateTime
>   endedAt        DateTime?
>   durationSec    Int?
>   createdAt      DateTime      @default(now())
>
>   @@index([organizationId])
>   @@index([userId])
>   @@index([clientId])
>   @@index([organizationId, startedAt])
>   @@map("call_logs")
> }
>
> /// Notatka głosowa/tekstowa po połączeniu (raport serwisanta). Nagranie w storage (MinIO) pod recordingKey.
> model VoiceReport {
>   id             String   @id @default(uuid())
>   organizationId String
>   userId         String
>   callLogId      String?  // powiązanie z połączeniem
>   clientId       String?
>   text           String?  // notatka tekstowa
>   transcript     String?  // opcjonalna transkrypcja
>   recordingKey   String?  // klucz w FILE_STORAGE, np. "voice-reports/<org>/<uuid>.m4a"
>   durationSec    Int?
>   createdAt      DateTime @default(now())
>   updatedAt      DateTime @updatedAt
>
>   @@index([organizationId])
>   @@index([userId])
>   @@index([callLogId])
>   @@map("voice_reports")
> }
>
> /// Rejestracja urządzenia mobilnego serwisanta (dual-SIM, push). Opcjonalne — jeśli potrzebne powiadomienia/dedup.
> model MobileDevice {
>   id             String   @id @default(uuid())
>   organizationId String
>   userId         String
>   deviceId       String   // stabilny identyfikator urządzenia
>   model          String?
>   osVersion      String?
>   sim1Label      String?
>   sim2Label      String?
>   pushToken      String?
>   lastSeenAt     DateTime @default(now())
>   createdAt      DateTime @default(now())
>
>   @@unique([organizationId, userId, deviceId])
>   @@index([organizationId])
>   @@map("mobile_devices")
> }
> ```
> Następnie: `npx prisma migrate dev --name mobile_teamtalk` na środowisku dev (NIE na prodzie). Na prodzie migracja pójdzie automatycznie przez entrypoint (`prisma migrate deploy`) przy deployu.

---

### Zadanie A3 — Moduł `telephony` (NestJS, heksagonalny)

**Prompt do wykonania:**

> Utwórz `api/src/modules/telephony` wzorując się 1:1 na module `whatsapp` (ta sama struktura, DI przez `useFactory` + porty, `imports: [IamModule]` dla guardów RBAC, `imports: [StorageModule]` dla nagrań). Zarejestruj `TelephonyModule` w głównym `app.module.ts` obok pozostałych modułów.
>
> **Warstwy:**
> - `domain/` — typy `CallLog`, `VoiceReport`, `CallDirection`, walidacje domenowe (np. `endedAt >= startedAt`), błędy.
> - `application/ports/` — `CALL_LOG_REPOSITORY`, `VOICE_REPORT_REPOSITORY`; use-case'y: `RecordCallLog`, `ListCallLogs`, `CreateVoiceReport`, `AttachRecording`, `ListVoiceReports`. Dopasowanie klienta po numerze przez port `CLIENT_LOOKUP` (jak `DEAL_LOOKUP` w whatsapp): normalizuj numer i szukaj po `clients.phone`/`phone2` w obrębie `organizationId`.
> - `infrastructure/persistence/` — adaptery Prisma. Nagrania przez wstrzyknięty `FILE_STORAGE` (port `shared/storage/file-storage.port.ts`, metoda `put({key, body, contentType})`; klucz waliduj `assertValidKey`, wzór `voice-reports/<organizationId>/<uuid>.<ext>`).
> - `presentation/` — kontrolery z `@UseGuards(SessionAuthGuard, PermissionsGuard)` i `@RequirePermissions(PERMISSIONS.TELEPHONY_USE)`; `organizationId`/`userId` z `@CurrentUser()`; walidacja wejścia Zod (`ZodValidationPipe`).
>
> **Endpointy (prefix globalny `/api`):**
> | Metoda | Ścieżka | Body / Query | Opis |
> |---|---|---|---|
> | `POST` | `/api/call-logs` | tablica CallLog (batch sync) | zapisuje połączenia; idempotencja po `(userId, phoneNumber, startedAt)`; próbuje dopasować `clientId` |
> | `GET`  | `/api/call-logs` | `?since=<ISO>&limit=` | lista połączeń serwisanta (`clientVisibility`: `own` → tylko swoje) |
> | `POST` | `/api/voice-reports` | `{callLogId?, clientId?, text?, durationSec?}` | tworzy notatkę, zwraca `id` |
> | `POST` | `/api/voice-reports/:id/recording` | multipart `file` (audio) | upload nagrania → `FILE_STORAGE`, zapis `recordingKey` (wzoruj upload/multipart na `documents.controller.ts`) |
> | `GET`  | `/api/voice-reports` | `?since=&limit=` | lista notatek |
> | `POST` | `/api/devices` | `{deviceId, model?, osVersion?, sim1Label?, sim2Label?, pushToken?}` | upsert `MobileDevice` (opcjonalne) |
>
> Zwracaj czasy jako ISO-8601 UTC. Limit rozmiaru nagrania i dozwolone typy audio (`audio/mp4`, `audio/aac`, `audio/ogg`) egzekwuj w DTO/kontrolerze.

---

### Zadanie A4 — Uprawnienie RBAC dla telefonii

**Prompt do wykonania:**

> W `api/src/modules/iam/domain/permissions.ts`:
> 1. Dodaj do `PERMISSIONS`: `TELEPHONY_USE: 'telephony.use'`.
> 2. Dodaj `PERMISSIONS.TELEPHONY_USE` do listy uprawnień roli **`serwisant`** w `ROLE_PERMISSIONS` (admin/zarzad mają `ALL`, więc dostają automatycznie). Rozważ też `koordynator`, jeśli koordynatorzy mają widzieć rejestr połączeń.
> 3. Kontrolery modułu `telephony` chronij `@RequirePermissions(PERMISSIONS.TELEPHONY_USE)`.

---

### Zadanie A5 — Konfiguracja / deploy

**Prompt do wykonania:**

> 1. Upewnij się, że `SESSION_SECRET` jest ustawiony w `.env` (jest — służy do weryfikacji). `MOBILE_SESSION_TTL` dodaj do `.env`/`.env.example` z sensownym defaultem (30 dni).
> 2. CORS/hosty: mobilka uderza w `https://ekotak.app/api/*`. Sprawdź, czy globalny CORS/nagłówki w `main.ts` nie blokują klienta bez `Origin` (aplikacja natywna nie wysyła Origin). Jeśli logika sesji zależy od `SameSite`/CSRF pod web — dla ścieżek `auth/mobile-login`, `call-logs`, `voice-reports`, `devices` NIE wymagaj cookie CSRF (mobilka uwierzytelnia się tokenem w nagłówku).
> 3. Deploy: merge do `dev`, `git pull` na VPS w `/opt/ekotak/app`, przebudowa obrazu api (`docker compose build api && docker compose up -d api`) — migracje pójdą automatycznie przez entrypoint.
> 4. Bootstrap kont serwisantów: konta zakładane normalnie w panelu (rola `serwisant`); mobilka tylko loguje istniejące konta.

**Do potwierdzenia z autorem:**
- Co robił stary endpoint `/api/functions` (mobilka go wołała) — czy potrzebny odpowiednik po stronie board360?
- Czy notatka po połączeniu ma trafiać także do `activity_logs` / karty klienta w panelu (żeby biuro to widziało w webie), czy tylko do `voice_reports`?

---

# CZĘŚĆ B — APLIKACJA (TeamTalk Android). Nasza robota

Zmiany po stronie `C:\EKOTAK\teamtalk-kotlin` (Kotlin/Compose/Hilt/Retrofit). Stack i architektura bez zmian — podmieniamy tylko warstwę sieci/auth i mapowanie modeli.

### B1 — Konfiguracja bazowego URL
- `API_BASE_URL` → `https://ekotak.app` (BuildConfig; wariant dev: `https://dev.185.193.112.175.sslip.io`). Usunąć stary `100.78.184.117:3000`.

### B2 — Nowy model auth (sesja board360 zamiast JWT)
- Logowanie: `POST /api/auth/login`... → **nie**, użyć nowego `POST /api/auth/mobile-login` (Zadanie A1). Body `{email, password}` → odpowiedź `{token, expiresAt, user}`.
- Token trzymać w `DataStore`/`EncryptedSharedPreferences`. Interceptor OkHttp dodaje do każdego żądania nagłówek:
  `Cookie: b360_session=<token>` (albo `Authorization: Bearer <token>` jeśli autor zrobi A1b).
- **Usunąć** dotychczasowy `TokenAuthenticator` z refresh-flow (board360 nie ma refresh tokenów). Na `401`:
  - jeśli mamy zapisane poświadczenia → ciche ponowne `mobile-login`;
  - inaczej → wylogowanie i ekran logowania.
- TTL tokenu ustala serwer (`MOBILE_SESSION_TTL`, prop. 30 dni). Zapisywać `expiresAt` i odświeżać token proaktywnie przed wygaśnięciem.

### B3 — Mapowanie modeli
- **Profil** (`/api/me` lub `user` z mobile-login) → `AuthContext { userId, organizationId, email, role, permissions[], clientVisibility }`. Zastępuje stary `/api/profiles`.
- **Klienci** (`GET /api/clients?q=`) → pola board360: `id, firstName, lastName, email, email2, phone, phone2, address, postalCode, city, street, geoLat, geoLng, type, category`. Zaktualizować DTO/encje Room i mapowanie (stary model klienta mógł mieć inne pola).
- **Rejestr połączeń** → `POST /api/call-logs` (batch sync z urządzenia) + `GET /api/call-logs`. Zmapować lokalny model połączenia (kierunek, numer, SIM slot dual-SIM, start/stop, czas trwania) na kontrakt z Zadania A3.
- **Notatki/raporty głosowe** → `POST /api/voice-reports` (+ `POST /api/voice-reports/:id/recording` multipart dla nagrania). Zastępuje stary `/api/voice-reports` i `/api/storage`.
- **Urządzenie/dual-SIM** (opcjonalnie) → `POST /api/devices` przy starcie.

### B4 — Ekran notatki po połączeniu (istniejący) 
- Podłączyć zapis do `voice-reports` (najpierw utwórz raport → dostajesz `id` → wgraj nagranie). Powiązać z `callLogId` utworzonym przy sync połączenia.

### B5 — Offline / sync
- Room jako kolejka: połączenia i raporty zapisywane lokalnie, wysyłka batchem gdy jest sieć (endpoint call-logs idempotentny po `userId+phoneNumber+startedAt`).

### B6 — Testy e2e
- Zalogować konto serwisanta, pobrać klientów, wykonać połączenie testowe → sync do `/api/call-logs`, dodać notatkę + nagranie → sprawdzić w panelu web/bazie na **dev**.

---

## Kolejność wdrożenia (zależności)
1. **A1** (mobile-login) — odblokowuje logowanie mobilki. → potem **B2**.
2. **A2 + A3 + A4** (telefonia + RBAC) — odblokowuje sync połączeń/notatek. → potem **B3/B4/B5**.
3. **A5** (deploy dev) → testy **B6** na dev → merge/prod.
4. Klienci/profil (`/api/clients`, `/api/me`) działają od razu — B3 w części klienckiej można robić równolegle.

## Ryzyka / uwagi
- **Format tokenu musi być identyczny** jak w `web/src/lib/auth.ts` — inaczej `SessionAuthGuard` odrzuci (Zadanie A1 pkt 2).
- Krótkie TTL sesji web (8h) nie nadaje się dla mobilki → osobny `MOBILE_SESSION_TTL`.
- `clientVisibility` serwisanta (`own`/`all`) wpływa na to, co widzi w `/api/clients` i `/api/call-logs` — ustalić z biurem.
- Dopasowanie połączeń do klientów po numerze bywa nietrafne (różne formaty) → `clientId` nullable, normalizacja numerów po obu stronach.
