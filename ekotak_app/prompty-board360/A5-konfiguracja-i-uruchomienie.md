Pracujesz w repozytorium board360 (git@github.com:ekotak/board360.git), monorepo na serwerze VPS w `/opt/ekotak/app`: backend NestJS w `api/`, frontend Next.js w `web/`, Caddy jako reverse proxy (`/api/*` → api :3001, reszta → web :3000). Docker działa w trybie ROOTLESS pod użytkownikiem `ekotak`. Domeny: produkcja `https://ekotak.app`, wersja testowa `https://dev.185.193.112.175.sslip.io`. Realizujemy podpięcie mobilnej aplikacji „TeamTalk” — kroki A1–A4 (logowanie mobilne, modele telefonii, moduł telephony, uprawnienia) powinny być już zrobione na gałęzi `feature/mobile-teamtalk`.

ZASADY (przestrzegaj bezwzględnie):
1. Gałąź: `git checkout feature/mobile-teamtalk`. NIE deployuj na produkcję (`ekotak.app`) i NIE dotykaj bazy produkcyjnej. Uruchamiamy TYLKO wersję testową (dev).
2. Zanim cokolwiek uruchomisz, PRZECZYTAJ: `dev.sh`, `docs/` (szczególnie plik o przepływie dev, np. `docs/10-przeplyw-dev.md`), `docker-compose.yml`, `docker-compose.dev.yml`, `Caddyfile`, `api/docker-entrypoint.sh`, `.env.example`. Zrozum, jak w tym projekcie uruchamia się środowisko DEV i jak stosuje migracje — potem działaj zgodnie z tym, co znajdziesz (jeśli moje instrukcje rozminą się z realiami repo, trzymaj się repo i napisz o różnicy).
3. Na koniec streść PO POLSKU, prostym językiem (odbiorca NIE jest programistą): co ustawiłeś, jak uruchomić/przetestować wersję testową i co dokładnie sprawdzić.

ZADANIE — dokończ konfigurację i bezpiecznie uruchom całość na wersji testowej (dev).

1. Konfiguracja środowiska:
   - Upewnij się, że `SESSION_SECRET` jest ustawiony w `.env` (służy do podpisu/weryfikacji tokenu — NIE zmieniaj jego wartości).
   - Dodaj `MOBILE_SESSION_TTL` do `.env` oraz `.env.example` (czas życia sesji mobilnej; domyślnie 30 dni = `2592000` sekund), jeśli krok A1 go wprowadził. Nie ujawniaj w podsumowaniu żadnych sekretów.
2. Dostęp dla klienta natywnego (aplikacja mobilna uderza w `https://ekotak.app/api/*` i `https://dev.185.193.112.175.sslip.io/api/*`):
   - Sprawdź konfigurację CORS i globalnych nagłówków w `api/src/main.ts` oraz reguły w `Caddyfile`. Aplikacja natywna często NIE wysyła nagłówka `Origin` — upewnij się, że to nie blokuje żądań do `POST /api/auth/mobile-login`, `/api/call-logs`, `/api/voice-reports`, `/api/devices`.
   - Jeśli w projekcie jest ochrona CSRF/`SameSite` powiązana z logowaniem przez przeglądarkę — NIE wymagaj jej dla ścieżek używanych przez mobilkę (mobilka uwierzytelnia się tokenem w nagłówku, nie ciasteczkiem przeglądarki). Zmiany rób minimalnie i tylko dla tych ścieżek.
3. Uruchomienie wersji TESTOWEJ (dev), bez ruszania produkcji:
   - Zastosuj migrację bazy z kroku A2 na środowisku DEV (nie prod). Ustal właściwą komendę z `dev.sh`/`docs`/compose — pamiętaj, że baza to kontener `board360-db` (rootless docker pod userem `ekotak`), więc migrację najpewniej uruchamia się przez `docker compose ... exec`, a nie z hosta.
   - Uruchom podgląd dev tak, jak przewiduje projekt (prawdopodobnie `./dev.sh preview`), pod adresem `https://dev.185.193.112.175.sslip.io`. Produkcja ma pozostać nietknięta.
   - Jeśli w którymkolwiek miejscu masz wątpliwość, czy operacja trafi w środowisko DEV a nie PRODUKCYJNE — NIE wykonuj jej. Zamiast tego wypisz dokładną, gotową komendę i wyjaśnij ją po polsku, żeby człowiek mógł ją świadomie uruchomić.
4. Szybki test dymny (jeśli środowisko dev działa): wykonaj `curl` na `https://dev.185.193.112.175.sslip.io/api/auth/mobile-login` z testowym kontem serwisanta (jeśli takie istnieje) i sprawdź, że zwraca `token` oraz `user`. Nie loguj sekretów/haseł w podsumowaniu.
5. Commit na gałęzi `feature/mobile-teamtalk`. Jeśli w repo używa się Pull Requestów — utwórz PR z gałęzi `feature/mobile-teamtalk` do `dev` z opisem zmian (A1–A5), ale NIE merguj samodzielnie.

DO POTWIERDZENIA (wypisz jako pytania w podsumowaniu — to decyzje dla Kamila/autora mobilki):
- Stary backend mobilki miał endpoint `/api/functions` — sprawdź, czy w board360 jest coś analogicznego lub czy trzeba to dobudować (jeśli nie wiadomo, po co był, zapytaj).
- Czy notatka po połączeniu ma być widoczna także w panelu web (na karcie klienta / w `activity_logs`), czy wystarczy zapis w tabeli `voice_reports`? Jeśli ma być w panelu — to osobne, kolejne zadanie po stronie web/API.

Kryteria odbioru: wersja testowa dev działa (albo masz jasną, gotową komendę do jej uruchomienia); produkcja nietknięta; endpoint logowania mobilnego odpowiada na dev; zmiany scommitowane na `feature/mobile-teamtalk` (opcjonalnie PR do `dev`).

W podsumowaniu po polsku napisz PROSTYMI słowami: (a) czy wersja testowa jest uruchomiona i pod jakim adresem, (b) jak sprawdzić, że działa, (c) listę pytań/decyzji do podjęcia, (d) że produkcja nie została ruszona.
