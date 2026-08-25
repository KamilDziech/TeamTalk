Pracujesz w repozytorium board360 (git@github.com:ekotak/board360.git) — monorepo z backendem NestJS w `api/` (architektura heksagonalna; Prisma 5; PostgreSQL; multi-tenant przez pole `organizationId` na encjach głównych) i frontendem Next.js w `web/`. Realizujemy podpięcie mobilnej aplikacji Android „TeamTalk” dla serwisantów. Firma EKOTAK to JEDNA organizacja.

ZASADY (przestrzegaj bezwzględnie):
1. Gałąź: `git checkout feature/mobile-teamtalk` (powinna już istnieć z poprzedniego kroku; jeśli nie: `git checkout dev && git pull && git checkout -B feature/mobile-teamtalk`). Pracuj tylko na niej. NIE merguj i NIE deployuj na produkcję.
2. NIGDY nie uruchamiaj migracji na bazie PRODUKCYJNEJ. Migracje tylko na środowisku DEV/lokalnym.
3. Przeczytaj istniejący `api/prisma/schema.prisma` i naśladuj konwencje (`id String @id @default(uuid())`, `organizationId`, `@@map` w snake_case, indeksy `@@index`, znaczniki czasu `createdAt/updatedAt`).
4. Na koniec streść PO POLSKU, prostym językiem (odbiorca nie jest programistą), co zrobiłeś.

ZADANIE — dodaj do bazy danych struktury dla telefonii mobilnej: rejestr połączeń, notatki/nagrania głosowe oraz (opcjonalnie) rejestrację urządzenia.

Tło: aplikacja mobilna serwisantów zapisuje historię połączeń z klientami oraz notatki głosowe po rozmowie. W obecnym schemacie NIE ma żadnych tabel telefonii. Tabela `clients` ma pola `phone`, `phone2` (po nich będziemy próbować dopasować klienta do połączenia). Relacje po `organizationId + id`, a `clientId` ma być opcjonalne (dopasowanie po numerze bywa nietrafione).

Do `api/prisma/schema.prisma` dodaj (dostosowując drobiazgi do stylu istniejących modeli):

```prisma
enum CallDirection { inbound outbound missed }

/// Rejestr połączeń telefonicznych serwisanta (aplikacja mobilna TeamTalk).
model CallLog {
  id             String        @id @default(uuid())
  organizationId String
  userId         String        // serwisant (User.id), który dzwonił/odebrał
  clientId       String?       // dopasowany klient (po numerze), opcjonalny
  phoneNumber    String        // numer drugiej strony (najlepiej E.164)
  direction      CallDirection
  simSlot        Int?          // dual-SIM: 0/1 lub null
  startedAt      DateTime
  endedAt        DateTime?
  durationSec    Int?
  createdAt      DateTime      @default(now())

  @@index([organizationId])
  @@index([userId])
  @@index([clientId])
  @@index([organizationId, startedAt])
  @@map("call_logs")
}

/// Notatka głosowa/tekstowa po połączeniu (raport serwisanta). Plik nagrania trzymany w magazynie plików (MinIO) pod kluczem recordingKey.
model VoiceReport {
  id             String   @id @default(uuid())
  organizationId String
  userId         String
  callLogId      String?  // powiązanie z konkretnym połączeniem
  clientId       String?
  text           String?  // notatka tekstowa
  transcript     String?  // opcjonalna transkrypcja
  recordingKey   String?  // klucz pliku w magazynie, np. "voice-reports/<org>/<uuid>.m4a"
  durationSec    Int?
  createdAt      DateTime @default(now())
  updatedAt      DateTime @updatedAt

  @@index([organizationId])
  @@index([userId])
  @@index([callLogId])
  @@map("voice_reports")
}

/// Rejestracja urządzenia mobilnego serwisanta (dual-SIM, token powiadomień). Opcjonalne.
model MobileDevice {
  id             String   @id @default(uuid())
  organizationId String
  userId         String
  deviceId       String   // stabilny identyfikator urządzenia
  model          String?
  osVersion      String?
  sim1Label      String?
  sim2Label      String?
  pushToken      String?
  lastSeenAt     DateTime @default(now())
  createdAt      DateTime @default(now())

  @@unique([organizationId, userId, deviceId])
  @@index([organizationId])
  @@map("mobile_devices")
}
```

Uwaga: jeśli w projekcie modele `User`/`Client` mają jawnie zdefiniowane relacje (pola relacyjne) do innych tabel, zdecyduj czy dodać relacje formalne — ale prościej i bezpieczniej jest zostawić `userId`/`clientId` jako zwykłe pola z indeksami (tak jak np. `activity_logs` odwołuje się do userId bez FK). Wybierz wariant spójny z resztą schematu i wyjaśnij wybór w podsumowaniu.

Utworzenie migracji:
- Wygeneruj migrację Prisma o nazwie `mobile_teamtalk`.
- Ustal, jak w TYM projekcie stosuje się migracje na DEV — sprawdź `dev.sh`, `docs/` (np. `docs/10-przeplyw-dev.md`), `docker-compose.dev.yml`, `api/docker-entrypoint.sh` (produkcyjny robi `prisma migrate deploy`). Baza działa w kontenerze `board360-db` i może NIE być wystawiona na hoście — więc migrację dev prawdopodobnie trzeba uruchomić przez `docker compose ... exec`/dev-workflow, a nie bezpośrednio z hosta.
- Jeśli potrafisz BEZPIECZNIE zastosować migrację do bazy DEV (nie produkcyjnej) — zrób to i zweryfikuj, że tabele powstały. Jeśli masz jakiekolwiek wątpliwości, czy trafisz w bazę dev a nie prod — NIE uruchamiaj migracji; zamiast tego wypisz dokładną, gotową komendę do uruchomienia na DEV i wyjaśnij ją po polsku.

Kryteria odbioru: `npx prisma validate` przechodzi; wygenerowany plik migracji istnieje w `api/prisma/migrations/`; `npx prisma generate` działa. Zmiany tylko na gałęzi `feature/mobile-teamtalk`.

W podsumowaniu po polsku napisz: (a) jakie tabele dodałeś, (b) czy migracja została ZAstosowana na dev czy tylko przygotowana (a jeśli tylko przygotowana — podaj komendę), (c) czy dodałeś relacje formalne czy zostawiłeś pola z indeksami i dlaczego.
