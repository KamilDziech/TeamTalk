# board360-mock

Lekki testowy backend zgodny z **kontraktem board360**, do testów end-to-end aplikacji mobilnej TeamTalk (Android) bez czekania na wdrożenie prawdziwego board360. Jednocześnie stanowi **żywą referencję kontraktu** dla autora board360 (odpowiada promptom A1–A5).

- Dane trzymane **w pamięci** (seed przy starcie) — resetują się po restarcie.
- Auth: token HMAC `b360_session` w tym samym formacie co board360 (cookie lub `Authorization: Bearer`).
- Nagrania zapisywane na dysku w `uploads/`.

## Uruchomienie
```bash
npm install
PORT=3001 SESSION_SECRET=dev-secret node server.js
```

## Konta testowe
- `serwisant@ekotak.pl` / `test1234` (rola serwisant)
- `admin@ekotak.pl` / `admin1234` (rola admin)

## Endpointy
- `POST /api/auth/mobile-login` `{email,password}` → `{token, expiresAt, user}`
- `GET /api/me` → AuthContext
- `GET /api/clients?q=` → lista klientów (kształt board360)
- `GET /api/clients/:id`
- `POST /api/call-logs` (obiekt lub tablica) → zapis, idempotencja `(userId, phoneNumber, startedAt)`
- `GET /api/call-logs?since=&limit=`
- `POST /api/voice-reports` `{callLogId?,clientId?,text?,durationSec?}` → `{id,...}`
- `POST /api/voice-reports/:id/recording` (multipart `file`) → ustawia `recordingKey`
- `GET /api/voice-reports?since=&limit=`
- `POST /api/devices` `{deviceId, model?, ...}` → upsert
- `GET /api/health`

## Szybki test
```bash
TOKEN=$(curl -s localhost:3001/api/auth/mobile-login -H 'Content-Type: application/json' \
  -d '{"email":"serwisant@ekotak.pl","password":"test1234"}' | node -pe 'JSON.parse(require("fs").readFileSync(0)).token')
curl -s localhost:3001/api/me -H "Cookie: b360_session=$TOKEN"
curl -s localhost:3001/api/clients -H "Cookie: b360_session=$TOKEN"
```

## Konfiguracja aplikacji Android
- `local.properties`: `API_BASE_URL=http://100.78.184.117:3001` (przez Tailscale) **lub**
- telefon przez USB + `adb reverse tcp:3001 tcp:3001` i `API_BASE_URL=http://localhost:3001`.
