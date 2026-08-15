# Plan wdrożenia: Publiczny dostęp do backendu TeamTalk

**Data:** 2026-05-24  
**Cel:** Udostępnienie backendu TeamTalk przez internet (HTTPS) bez VPN, z maksymalnym bezpieczeństwem.

---

## Architektura docelowa

```
Telefon użytkownika
       ↓ HTTPS TLS 1.3 (certificate pinning)
  Cloudflare Edge
  ├── DDoS protection
  ├── WAF (Web Application Firewall)
  └── SSL/TLS termination
       ↓ szyfrowany tunel (cloudflared)
  Serwer 100.78.184.117
  └── cloudflared demon
       ↓ HTTP localhost
  Node.js :3000 (Express + JWT)
       ↓
  PostgreSQL :5433
```

---

## Koszty

| Składnik          | Koszt         |
|-------------------|---------------|
| Cloudflare Tunnel | bezpłatny     |
| SSL/TLS cert      | bezpłatny     |
| DDoS protection   | bezpłatny     |
| Domena .pl        | ~50–80 zł/rok |
| **Łącznie**       | **~70 zł/rok** |

---

## Etap 1 — Rejestracja domeny i Cloudflare

### 1.1 Zarejestruj domenę

Rekomendowane rejestratury z tanią domeną `.pl`:
- **nazwa.pl** — https://www.nazwa.pl
- **domeny.pl** — https://www.domeny.pl
- **OVH** — https://www.ovhcloud.com/pl/domains/

Kup domenę, np. `ekotak.pl` lub `teamtalk.app` (~50–80 zł/rok).

### 1.2 Przenieś DNS do Cloudflare (bezpłatne)

1. Utwórz konto na https://dash.cloudflare.com
2. Kliknij **Add a Site** → wpisz domenę
3. Wybierz plan **Free**
4. Cloudflare wykryje istniejące rekordy DNS i wyświetli je — zatwierdź
5. Przejdź do rejestratury i zmień serwery nazw (nameservers) na te podane przez Cloudflare, np.:
   ```
   aria.ns.cloudflare.com
   bob.ns.cloudflare.com
   ```
6. Propagacja DNS: do 24h (zwykle kilka minut)

### 1.3 Ustaw SSL/TLS na Full (strict)

W panelu Cloudflare: **SSL/TLS → Overview → Full (strict)**

---

## Etap 2 — Cloudflare Tunnel na serwerze

### 2.1 Zaloguj się na serwer przez SSH

```bash
ssh kamil@100.78.184.117
```

### 2.2 Zainstaluj cloudflared

```bash
curl -L --output cloudflared.deb \
  https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64.deb
sudo dpkg -i cloudflared.deb
cloudflared --version
```

### 2.3 Zaloguj cloudflared do Cloudflare

```bash
cloudflared tunnel login
```

Otworzy się link w przeglądarce — zaloguj się i wybierz domenę.

### 2.4 Utwórz tunel

```bash
cloudflared tunnel create teamtalk
# Zapisz UUID tunelu który pojawi się w wyniku, np: a1b2c3d4-...
```

### 2.5 Utwórz plik konfiguracyjny tunelu

```bash
mkdir -p ~/.cloudflared
nano ~/.cloudflared/config.yml
```

Zawartość pliku (podmień UUID):
```yaml
tunnel: a1b2c3d4-xxxx-xxxx-xxxx-xxxxxxxxxxxx
credentials-file: /home/kamil/.cloudflared/a1b2c3d4-xxxx-xxxx-xxxx-xxxxxxxxxxxx.json

ingress:
  - hostname: api.ekotak.pl
    service: http://localhost:3000
  - service: http_status:404
```

### 2.6 Dodaj rekord DNS

```bash
cloudflared tunnel route dns teamtalk api.ekotak.pl
```

Sprawdź w panelu Cloudflare (DNS → Records) — powinien pojawić się rekord CNAME dla `api`.

### 2.7 Uruchom jako usługa systemd (automatyczny start)

```bash
sudo cloudflared service install
sudo systemctl enable cloudflared
sudo systemctl start cloudflared
sudo systemctl status cloudflared
```

### 2.8 Test połączenia

```bash
curl https://api.ekotak.pl/api/health
# Oczekiwany wynik: {"status":"ok"} lub podobny
```

---

## Etap 3 — Zabezpieczenie serwera (firewall)

Wpuść połączenia do portu 3000 **tylko z adresów IP Cloudflare** — blokuje próby ominięcia Cloudflare.

```bash
# Pobierz aktualne zakresy IP Cloudflare
curl -s https://www.cloudflare.com/ips-v4 | while read ip; do
  sudo ufw allow from $ip to any port 3000
done

# Zablokuj port 3000 dla reszty świata
sudo ufw deny 3000

# Upewnij się że SSH (22) jest otwarte
sudo ufw allow 22
sudo ufw enable
sudo ufw status
```

---

## Etap 4 — Aktualizacja backendu (TypeScript)

### 4.1 Zaktualizuj .env na serwerze

```bash
cd ~/projects/ekotak/teamtalk-localdb/server
nano .env
```

Zmień/dodaj:
```env
ALLOWED_ORIGINS=https://api.ekotak.pl
TRUSTED_PROXIES=cloudflare
```

### 4.2 Dodaj Helmet.js (jeśli nie ma)

```bash
npm install helmet
```

W `server.ts` / `app.ts`:
```typescript
import helmet from 'helmet';
app.use(helmet());
```

### 4.3 Zaufaj nagłówkowi CF-Connecting-IP (prawdziwy IP klienta)

W Express:
```typescript
app.set('trust proxy', true);
// Teraz req.ip zwraca prawdziwy IP użytkownika z CF-Connecting-IP
```

### 4.4 Restart backendu

```bash
pm2 restart all
pm2 save
```

---

## Etap 5 — Zmiany w aplikacji Android

### 5.1 Build Flavors — dev i prod

W `app/build.gradle.kts` dodaj flavory (nie konfiguracja runtime — bezpieczniejsze):

```kotlin
flavorDimensions += "env"
productFlavors {
    create("dev") {
        dimension = "env"
        buildConfigField("String", "API_BASE_URL", "\"http://100.78.184.117:3000\"")
        applicationIdSuffix = ".dev"
        versionNameSuffix = "-dev"
    }
    create("prod") {
        dimension = "env"
        buildConfigField("String", "API_BASE_URL", "\"https://api.ekotak.pl\"")
    }
}
```

Teraz masz warianty:
- `devDebug` — do developmentu (Tailscale IP)
- `prodRelease` — dla użytkowników (HTTPS publiczny)

### 5.2 Network Security Config — zakaz HTTP w produkcji

Utwórz `app/src/main/res/xml/network_security_config.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
    <!-- Dev: pozwól HTTP dla Tailscale IP -->
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">100.78.184.117</domain>
    </domain-config>
</network-security-config>
```

W `AndroidManifest.xml`:
```xml
<application
    android:networkSecurityConfig="@xml/network_security_config"
    ...>
```

### 5.3 Certificate Pinning — ochrona przed atakiem MITM

#### Krok A: Pobierz pin certyfikatu Cloudflare

```bash
openssl s_client -connect api.ekotak.pl:443 -servername api.ekotak.pl 2>/dev/null \
  | openssl x509 -pubkey -noout \
  | openssl rsa -pubin -outform der 2>/dev/null \
  | openssl dgst -sha256 -binary \
  | openssl enc -base64
```

Zapisz wynik (np. `AbCdEfGh...=`) — to jest Twój **pin 1**.

Cloudflare rotuje certyfikaty, więc pobierz też **backup pin** z certyfikatu pośredniego:
```bash
openssl s_client -connect api.ekotak.pl:443 -showcerts 2>/dev/null \
  | awk '/BEGIN CERTIFICATE/,/END CERTIFICATE/' \
  | openssl x509 -pubkey -noout \
  | openssl rsa -pubin -outform der 2>/dev/null \
  | openssl dgst -sha256 -binary \
  | openssl enc -base64
```

#### Krok B: Dodaj piny do OkHttpClient

W `NetworkModule.kt`:
```kotlin
.certificatePinner(
    CertificatePinner.Builder()
        .add("api.ekotak.pl", "sha256/PIN_1_TUTAJ=")
        .add("api.ekotak.pl", "sha256/PIN_BACKUP_TUTAJ=")
        .build()
)
```

**Uwaga:** Certificate pinning aktywuj tylko dla flavorów `prod`. W `dev` pomijasz pinner.

### 5.4 Aktualizacja local.properties

```properties
# dev (Tailscale) — używane przez devDebug
API_BASE_URL=http://100.78.184.117:3000
```

W `build.gradle.kts` `prod` flavor ma URL wpisany na stałe, więc `local.properties` dotyczy tylko `dev`.

---

## Etap 6 — Weryfikacja końcowa

### Checklist przed pierwszą dystrybucją

- [ ] `https://api.ekotak.pl` odpowiada (curl/przeglądarka)
- [ ] Certyfikat SSL ważny i wydany przez Cloudflare
- [ ] Backend zwraca odpowiedź na `/api/health` lub `/api/auth/login`
- [ ] Aplikacja `prodRelease` łączy się z serwerem
- [ ] Aplikacja `prodRelease` **nie łączy się** gdy cert pinning jest zły (test: zmień pin na błędny)
- [ ] `devDebug` nadal działa przez Tailscale
- [ ] pm2 i cloudflared uruchamiają się po restarcie serwera (`pm2 save`, `systemctl enable cloudflared`)

---

## Diagram wariantów budowania

```
              Android App
                  │
        ┌─────────┴─────────┐
      devDebug          prodRelease
        │                   │
  100.78.184.117:3000  https://api.ekotak.pl
  (Tailscale, HTTP)    (Cloudflare, HTTPS)
  bez cert pinning     + certificate pinning
        │                   │
   Ty (developer)      Użytkownicy
```

---

## Polecenia szybkiego dostępu (po wdrożeniu)

```bash
# Status tunelu
sudo systemctl status cloudflared

# Logi tunelu
journalctl -u cloudflared -f

# Restart tunelu
sudo systemctl restart cloudflared

# Status backendu
pm2 status

# Logi backendu
pm2 logs teamtalk-server
```
