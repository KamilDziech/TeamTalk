Pracujesz w repozytorium board360 (git@github.com:ekotak/board360.git) — backend NestJS w `api/`, system uprawnień RBAC w module `iam`. Realizujemy podpięcie mobilnej aplikacji „TeamTalk” dla serwisantów. Role w systemie: `admin, zarzad, koordynator, serwisant, biuro, montaz, stazysta`. Serwisant = użytkownik aplikacji mobilnej.

ZASADY:
1. Gałąź: `git checkout feature/mobile-teamtalk`. Pracuj tylko na niej. NIE merguj i NIE deployuj na produkcję.
2. Przeczytaj najpierw `api/src/modules/iam/domain/permissions.ts` (stała `PERMISSIONS`, mapa `ROLE_PERMISSIONS`, lista `ALL`) i naśladuj styl.
3. Na koniec uruchom w `api/`: `npm run build` i `npm test`; napraw wprowadzone błędy; streść PO POLSKU prostym językiem.

ZADANIE — dodaj uprawnienie do korzystania z telefonii i nadaj je serwisantom.

1. W `api/src/modules/iam/domain/permissions.ts` dodaj do obiektu `PERMISSIONS` nowy wpis:
   `TELEPHONY_USE: 'telephony.use',` (dopisz go w stylu istniejących wpisów, z krótkim komentarzem: „Telefonia mobilna TeamTalk — rejestr połączeń i notatki głosowe serwisanta”).
2. W mapie `ROLE_PERMISSIONS` dodaj `PERMISSIONS.TELEPHONY_USE` do listy uprawnień roli **`serwisant`**. (Role `admin` i `zarzad` mają `ALL`, więc dostaną je automatycznie — nie ruszaj ich list.)
3. Rozważ dodanie `TELEPHONY_USE` także roli `koordynator` — TYLKO jeśli koordynatorzy również mają widzieć/obsługiwać rejestr połączeń. Jeśli nie masz pewności, NIE dodawaj i zaznacz to w podsumowaniu jako pytanie do decyzji biznesowej.
4. Jeśli w projekcie istnieją testy sprawdzające uprawnienia ról (np. `effective-permissions` / `permissions.guard.spec`) — zaktualizuj/uzupełnij je tak, by uwzględniały nowe uprawnienie i nie były czerwone.
5. Sprawdź, że kontrolery modułu `telephony` (jeśli już istnieją z kroku A3) używają `@RequirePermissions(PERMISSIONS.TELEPHONY_USE)`. Jeśli używały literału `'telephony.use'`, podmień na stałą `PERMISSIONS.TELEPHONY_USE`.

Kryteria odbioru: `npm run build` i `npm test` w `api/` przechodzą; serwisant ma efektywne uprawnienie `telephony.use`. Zmiany tylko na gałęzi `feature/mobile-teamtalk`.

W podsumowaniu po polsku napisz: (a) że dodałeś uprawnienie `telephony.use` i nadałeś je roli serwisant, (b) czy dodałeś je też koordynatorowi (jeśli nie — że to pytanie do decyzji), (c) czy testy przeszły.
