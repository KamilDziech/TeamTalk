Pracujesz w repozytorium board360 (git@github.com:ekotak/board360.git) — monorepo z backendem NestJS w katalogu `api/` (architektura heksagonalna: warstwy domain / application (+ports) / infrastructure / presentation; Prisma 5; PostgreSQL; Argon2; Zod) oraz frontendem Next.js w `web/`. Realizujemy podpięcie mobilnej aplikacji Android „TeamTalk” dla serwisantów.

ZASADY (przestrzegaj bezwzględnie):
1. Najpierw ustal gałąź: `git checkout dev && git pull` a potem `git checkout -B feature/mobile-teamtalk`. Całą pracę rób na tej gałęzi. Zrób commit na końcu, ale NIE merguj do dev/main i NIE deployuj na produkcję.
2. Zanim cokolwiek napiszesz, PRZECZYTAJ pliki wymienione niżej i naśladuj istniejące konwencje (nazwy, styl, sposób wstrzykiwania zależności przez porty i `useFactory`). Nie zmieniaj formatu tokenu ani sekretu.
3. Na koniec uruchom w katalogu `api/`: `npm run build`, `npm run typecheck` (jeśli jest) oraz `npm test`. Napraw błędy, które wprowadziłeś. Potem streść PO POLSKU, prostym językiem (odbiorca nie jest programistą), co zrobiłeś i czy build/testy przeszły.

ZADANIE — dodaj logowanie dla aplikacji mobilnej, które zwraca podpisany token sesji.

Tło: sesja w board360 to podpisany token HMAC w cookie `b360_session`. Obecnie token PODPISUJE warstwa web (`web/src/lib/auth.ts` → funkcja `createSessionToken`), a backend API tylko go WERYFIKUJE (`api/src/modules/iam/infrastructure/security/hmac-session-token.ts`). Endpoint `POST /api/auth/login` sprawdza tylko hasło i NIE wystawia tokenu. Aplikacja mobilna nie przechodzi przez web, więc potrzebuje endpointu, który po zalogowaniu sam podpisze i zwróci token.

Najpierw przeczytaj, żeby poznać dokładny format i konwencje:
- `web/src/lib/auth.ts` (jak web podpisuje token — `createSessionToken`, pola payloadu, `SESSION_SECRET`)
- `web/src/lib/session.ts` (`SESSION_COOKIE`, `SESSION_MAX_AGE`)
- `api/src/modules/iam/infrastructure/security/hmac-session-token.ts` (weryfikacja po stronie API)
- `api/src/modules/iam/application/ports/session-token.port.ts` (interfejs `SessionPayload`, `SessionTokenVerifier`)
- `api/src/modules/iam/application/authenticate-user.usecase.ts` (weryfikacja poświadczeń + audyt)
- `api/src/modules/iam/application/resolve-session.usecase.ts` i `api/src/modules/iam/domain/effective-permissions.ts` (jak liczone są uprawnienia)
- `api/src/modules/iam/presentation/auth.controller.ts`, `.../guards/login-throttler.guard.ts`, `.../dto/login.dto.ts`, `.../zod-validation.pipe.ts`
- `api/src/modules/iam/iam.module.ts` (rejestracja providerów)

Implementacja:
1. W `api/src/modules/iam/application/ports/session-token.port.ts` dodaj port wystawiania tokenu obok istniejącego weryfikatora:
   - `export const SESSION_TOKEN_ISSUER = Symbol('SessionTokenIssuer');`
   - `export interface SessionTokenIssuer { issue(payload: SessionPayload): string; }`
2. W `hmac-session-token.ts` zaimplementuj `SessionTokenIssuer.issue()` używając istniejącej prywatnej metody `sign()`:
   `payload = base64url(JSON.stringify({sub,userId,organizationId,role,exp}))`, wynik = `` `${payload}.${sign(payload)}` ``.
   WYNIK MUSI być identyczny formatowo z `createSessionToken` z `web/src/lib/auth.ts`, żeby `SessionAuthGuard` go zaakceptował. Ta sama klasa `HmacSessionToken` może implementować oba interfejsy.
3. Dodaj use-case `api/src/modules/iam/application/issue-mobile-session.usecase.ts`: woła istniejący `AuthenticateUser` (żeby zachować Argon2, audyt logowań i rate-limiting jak dziś), a po sukcesie wystawia token z `exp = clock.nowSeconds() + MOBILE_SESSION_TTL`. Dodaj `MOBILE_SESSION_TTL` czytany z env (`process.env.MOBILE_SESSION_TTL`) z domyślną wartością 30 dni (`60*60*24*30`) — mobilka nie ma przeglądarkowego re-loginu, więc krótkie 8h z weba nie wystarcza.
4. W `auth.controller.ts` dodaj `POST /api/auth/mobile-login`:
   - zabezpiecz `LoginThrottlerGuard` (jak istniejący `login`), walidacja `ZodValidationPipe(loginSchema)` (body `{ email, password }`),
   - w razie złych poświadczeń zwróć 401 (`UnauthorizedException`), tak jak `login`,
   - odpowiedź:
     ```json
     {
       "token": "<token b360_session>",
       "expiresAt": 1730000000,
       "user": { "userId": "...", "organizationId": "...", "email": "...", "role": "serwisant", "permissions": ["..."], "clientVisibility": "all" }
     }
     ```
     Pole `user` zbuduj tak samo jak `GET /api/me` zwraca `AuthContext` (reużyj `ResolveSession`/`effectivePermissions`).
5. Zarejestruj nowy provider w `iam.module.ts` (`{ provide: SESSION_TOKEN_ISSUER, useClass: HmacSessionToken }`) oraz `IssueMobileSession` (wzoruj wiązanie `useFactory`/`inject` na istniejących providerach w tym module). Wyeksportuj z modułu to, co potrzebne.
6. Dodaj testy jednostkowe: token z `issue()` przechodzi `verify()` tej samej klasy; endpoint zwraca 401 dla błędnych danych; rate-limiting nadal działa.

Kryteria odbioru: `npm run build` i `npm test` w `api/` przechodzą; nowy endpoint `POST /api/auth/mobile-login` zwraca token, który akceptuje istniejący `SessionAuthGuard`. Nie zmieniłeś zachowania dotychczasowego `POST /api/auth/login`.

OPCJONALNIE (jeśli łatwe i bezpieczne): w `api/src/modules/iam/presentation/guards/session-auth.guard.ts` dodaj odczyt tokenu także z nagłówka `Authorization: Bearer <token>` (gdy brak cookie `b360_session`). To ułatwia życie aplikacji mobilnej. Jeśli to zrobisz, wyraźnie napisz o tym w podsumowaniu.

Na koniec w podsumowaniu po polsku napisz: (a) czy build i testy przeszły, (b) czy zrobiłeś wariant opcjonalny z nagłówkiem Bearer, (c) jaką nazwę i domyślną wartość ma `MOBILE_SESSION_TTL`, (d) że zmiany są tylko na gałęzi `feature/mobile-teamtalk`.
