Pracujesz w repozytorium board360 (git@github.com:ekotak/board360.git) — monorepo z backendem NestJS w `api/` (architektura HEKSAGONALNA: każdy moduł ma warstwy `domain/`, `application/` z podkatalogiem `ports/`, `infrastructure/`, `presentation/`; zależności wstrzykiwane przez porty i `useFactory`; Prisma 5; Zod; magazyn plików przez port `FILE_STORAGE` z adapterem S3/MinIO). Realizujemy podpięcie mobilnej aplikacji Android „TeamTalk” dla serwisantów.

WYMAGANIE WSTĘPNE: ten krok zakłada, że wcześniej wykonano krok A2 (modele `CallLog`, `VoiceReport`, `MobileDevice` w `api/prisma/schema.prisma`). Jeśli tych modeli NIE ma — zatrzymaj się i napisz, że najpierw trzeba wykonać prompt A2.

ZASADY (przestrzegaj bezwzględnie):
1. Gałąź: `git checkout feature/mobile-teamtalk`. Pracuj tylko na niej. NIE merguj i NIE deployuj na produkcję.
2. Zanim cokolwiek napiszesz, PRZECZYTAJ wzorcowy moduł i naśladuj go 1:1 pod względem struktury i stylu.
3. Na koniec uruchom w `api/`: `npm run build`, `npm run typecheck` (jeśli jest), `npm test`. Napraw błędy, które wprowadziłeś. Potem streść PO POLSKU, prostym językiem (odbiorca nie jest programistą).

WZORCE DO PRZECZYTANIA NAJPIERW:
- Cały moduł `api/src/modules/whatsapp/` — to najbliższy wzór (kontroler + use-case'y + porty + adaptery Prisma + rejestracja w `whatsapp.module.ts` przez `useFactory`/`inject`, `imports: [IamModule]`).
- `api/src/modules/crm/presentation/clients.controller.ts` — jak używać `@UseGuards(SessionAuthGuard, PermissionsGuard)`, `@RequirePermissions(...)`, `@CurrentUser()`, `ZodValidationPipe`, oraz jak działa `clientVisibility` (`own`/`all`).
- `api/src/shared/storage/file-storage.port.ts` (metoda `put({key, body, contentType})`), `api/src/shared/storage/storage-key.ts` (`assertValidKey`) oraz `api/src/shared/storage/storage.module.ts` (jak wstrzyknąć `FILE_STORAGE`).
- Dowolny kontroler robiący upload pliku multipart, np. `api/src/modules/documents/presentation/documents.controller.ts` — skopiuj z niego wzorzec przyjmowania pliku.
- `api/src/modules/iam/domain/permissions.ts` — użyjesz `PERMISSIONS.TELEPHONY_USE` (dodawane w kroku A4; jeśli jeszcze go nie ma, użyj literału `'telephony.use'` i dopisz TODO, albo dodaj stałą tutaj i pozwól krokowi A4 dołożyć ją do roli).
- Główny `app.module.ts` — tam zarejestrujesz nowy `TelephonyModule`.

ZADANIE — utwórz moduł `api/src/modules/telephony` odwzorowując strukturę modułu `whatsapp`.

Warstwy:
- `domain/` — typy `CallLog`, `VoiceReport`, `CallDirection`, walidacje domenowe (np. `endedAt >= startedAt`, `durationSec >= 0`), klasy błędów.
- `application/ports/` — porty repozytoriów: `CALL_LOG_REPOSITORY`, `VOICE_REPORT_REPOSITORY`; oraz port dopasowania klienta `CLIENT_LOOKUP` (odpowiednik `DEAL_LOOKUP` z whatsapp): po znormalizowanym numerze telefonu znajduje `clientId` w obrębie `organizationId`, szukając po `clients.phone` i `clients.phone2`.
- `application/` — use-case'y: `RecordCallLogs` (przyjmuje paczkę połączeń, idempotentnie po `(userId, phoneNumber, startedAt)`, próbuje ustawić `clientId` przez `CLIENT_LOOKUP`), `ListCallLogs`, `CreateVoiceReport`, `AttachRecording` (zapis pliku przez `FILE_STORAGE`, ustawia `recordingKey`), `ListVoiceReports`, oraz `RegisterDevice` (upsert `MobileDevice`).
- `infrastructure/persistence/` — adaptery Prisma dla powyższych portów.
- `presentation/` — kontrolery z guardami `@UseGuards(SessionAuthGuard, PermissionsGuard)` i `@RequirePermissions(PERMISSIONS.TELEPHONY_USE)`; `organizationId`/`userId` bierz z `@CurrentUser()`; walidacja wejścia przez Zod (`ZodValidationPipe`). Uwzględnij `clientVisibility` przy listowaniu (serwisant ze scope `own` widzi tylko swoje wpisy).

Endpointy (globalny prefix `/api` jest ustawiony w `main.ts`):
| Metoda | Ścieżka | Wejście | Opis |
|---|---|---|---|
| POST | `/api/call-logs` | tablica połączeń (batch) | zapis połączeń, idempotencja po `(userId, phoneNumber, startedAt)`, dopasowanie `clientId` |
| GET  | `/api/call-logs` | `?since=<ISO>&limit=` | lista połączeń (wg `clientVisibility`) |
| POST | `/api/voice-reports` | `{callLogId?, clientId?, text?, durationSec?}` | tworzy notatkę, zwraca `id` |
| POST | `/api/voice-reports/:id/recording` | multipart, pole `file` (audio) | wgrywa nagranie do `FILE_STORAGE`, zapisuje `recordingKey` |
| GET  | `/api/voice-reports` | `?since=&limit=` | lista notatek |
| POST | `/api/devices` | `{deviceId, model?, osVersion?, sim1Label?, sim2Label?, pushToken?}` | upsert urządzenia |

Wymagania szczegółowe:
- Czasy zwracaj i przyjmuj jako ISO-8601 w UTC.
- Klucz pliku nagrania: `voice-reports/<organizationId>/<uuid>.<ext>`, waliduj `assertValidKey`. Dozwolone typy audio: `audio/mp4`, `audio/aac`, `audio/ogg`, `audio/mpeg`. Ustal rozsądny limit rozmiaru pliku i wymuś go w kontrolerze/DTO.
- Zarejestruj `TelephonyModule` w `app.module.ts`. W module `imports: [IamModule, StorageModule]` (lub jak nazywa się moduł storage w projekcie — sprawdź `storage.module.ts`).
- Dodaj testy jednostkowe do najważniejszej logiki (idempotencja zapisu połączeń, dopasowanie klienta po numerze, walidacja typu/rozmiaru pliku).

Kryteria odbioru: `npm run build` i `npm test` w `api/` przechodzą; wszystkie endpointy wymagają zalogowania (guard) i uprawnienia `telephony.use`. Zmiany tylko na gałęzi `feature/mobile-teamtalk`.

W podsumowaniu po polsku napisz: (a) jakie endpointy powstały, (b) czy build i testy przeszły, (c) czy uprawnienie `PERMISSIONS.TELEPHONY_USE` już istniało czy trzeba wykonać krok A4, (d) jaki ustawiłeś limit rozmiaru nagrania.
