# Supabase Self-Hosted - Plan Konfiguracji

> **Status:** Do wdrożenia w przyszłości
> **Obecne środowisko:** Darmowy plan Supabase Cloud (testowanie)

---

## Wymagania wstępne

- [ ] Serwer Linux (Ubuntu 22.04+ zalecany)
- [ ] Docker + Docker Compose
- [ ] Min. 4GB RAM, 20GB dysk
- [ ] Publiczne IP lub Cloudflare Tunnel
- [ ] Domena (płatna lub darmowa DuckDNS)

---

## Etap 1: Przygotowanie serwera

### 1.1 Instalacja Docker

```bash
# Aktualizacja systemu
sudo apt update && sudo apt upgrade -y

# Instalacja Docker
curl -fsSL https://get.docker.com | sh

# Dodanie użytkownika do grupy docker
sudo usermod -aG docker $USER

# Weryfikacja
docker --version
docker compose version
```

### 1.2 Instalacja nginx

```bash
sudo apt install nginx -y
sudo systemctl enable nginx
```

---

## Etap 2: Konfiguracja domeny

### Zalecane rejestratory (PL)

| Rejestrator | Cena .pl | Cena .com |
|-------------|----------|-----------|
| OVH | ~29 zł/rok | ~49 zł/rok |
| home.pl | ~39 zł/rok | ~59 zł/rok |
| nazwa.pl | ~35 zł/rok | ~55 zł/rok |
| Cloudflare | - | ~45 zł/rok |

### 2.1 Rejestracja domeny

1. Zarejestruj domenę, np. `teamtalk.pl` lub `ekotak-api.pl`
2. Skonfiguruj DNS:

```
Typ     Nazwa           Wartość              TTL
A       api             <IP_TWOJEGO_SERWERA> 3600
A       @               <IP_TWOJEGO_SERWERA> 3600
```

Wynik: `api.teamtalk.pl` lub `api.ekotak.pl`

### 2.2 Sprawdzenie propagacji DNS

```bash
# Poczekaj 5-30 minut po zmianie DNS
nslookup api.teamtalk.pl
dig api.teamtalk.pl
```

---

## Etap 3: Instalacja Supabase

### 3.1 Pobranie konfiguracji

```bash
cd /opt
sudo git clone --depth 1 https://github.com/supabase/supabase
cd supabase/docker
sudo cp .env.example .env
```

### 3.2 Generowanie kluczy

```bash
# Zainstaluj narzędzie
npm install -g supabase

# Wygeneruj silne hasło
openssl rand -base64 32

# Wygeneruj JWT secret (min 32 znaki)
openssl rand -base64 64

# Wygeneruj klucze API
# Użyj: https://supabase.com/docs/guides/self-hosting#api-keys
```

### 3.3 Konfiguracja `.env`

```env
############
# Secrets
############
POSTGRES_PASSWORD=<wygenerowane_haslo>
JWT_SECRET=<wygenerowany_jwt_secret_min_32_znaki>
ANON_KEY=<wygenerowany_anon_key>
SERVICE_ROLE_KEY=<wygenerowany_service_role_key>

############
# Database
############
POSTGRES_HOST=db
POSTGRES_DB=postgres
POSTGRES_PORT=5432

############
# API
############
SITE_URL=https://api.teamtalk.pl
API_EXTERNAL_URL=https://api.teamtalk.pl

############
# Studio
############
STUDIO_PORT=3000
SUPABASE_PUBLIC_URL=https://api.teamtalk.pl

############
# Auth
############
ENABLE_EMAIL_SIGNUP=true
ENABLE_EMAIL_AUTOCONFIRM=true
```

### 3.4 Uruchomienie

```bash
cd /opt/supabase/docker
sudo docker compose up -d

# Sprawdzenie statusu
sudo docker compose ps
```

---

## Etap 4: SSL z Let's Encrypt

### 4.1 Instalacja Certbot

```bash
sudo apt install certbot python3-certbot-nginx -y
```

### 4.2 Konfiguracja nginx

```bash
sudo nano /etc/nginx/sites-available/supabase
```

```nginx
server {
    listen 80;
    server_name api.teamtalk.pl;

    location / {
        proxy_pass http://localhost:8000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # WebSocket dla Realtime
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 86400;
    }
}

# Studio (opcjonalnie - dostęp do dashboardu)
server {
    listen 80;
    server_name studio.api.teamtalk.pl;

    location / {
        proxy_pass http://localhost:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

```bash
# Aktywacja
sudo ln -s /etc/nginx/sites-available/supabase /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

### 4.3 Certyfikat SSL

```bash
sudo certbot --nginx -d api.teamtalk.pl
```

---

## Etap 5: Migracja schematu bazy

### 5.1 Eksport z Supabase Cloud

```bash
# Connection string z Dashboard -> Settings -> Database
pg_dump "postgresql://postgres:[PASSWORD]@db.[PROJECT-REF].supabase.co:5432/postgres" \
  --schema-only \
  --no-owner \
  > schema.sql
```

### 5.2 Import do self-hosted

```bash
psql "postgresql://postgres:[LOCAL_PASSWORD]@localhost:5432/postgres" < schema.sql
```

---

## Etap 6: Konfiguracja aplikacji TeamTalk

### 6.1 Nowe zmienne środowiskowe

`.env.production` (self-hosted):
```env
SUPABASE_URL=https://api.teamtalk.pl
SUPABASE_ANON_KEY=<twoj_anon_key_z_self_hosted>
```

### 6.2 Build aplikacji

```bash
APP_ENV=production eas build --platform android
```

---

## Etap 7: Backup (cron)

```bash
sudo nano /opt/supabase/backup.sh
```

```bash
#!/bin/bash
BACKUP_DIR="/opt/supabase/backups"
DATE=$(date +%Y%m%d_%H%M)
mkdir -p $BACKUP_DIR

# Backup bazy
docker exec supabase-db pg_dump -U postgres postgres | gzip > "$BACKUP_DIR/db_$DATE.sql.gz"

# Backup storage (jeśli używasz)
tar -czf "$BACKUP_DIR/storage_$DATE.tar.gz" /opt/supabase/docker/volumes/storage

# Usuń stare (30 dni)
find $BACKUP_DIR -name "*.gz" -mtime +30 -delete

echo "[$DATE] Backup completed"
```

```bash
sudo chmod +x /opt/supabase/backup.sh

# Cron - co niedzielę o 3:00
echo "0 3 * * 0 /opt/supabase/backup.sh >> /var/log/supabase-backup.log 2>&1" | sudo crontab -
```

---

## Porty do otwarcia (firewall)

| Port | Usługa | Dostęp |
|------|--------|--------|
| 80 | HTTP (redirect) | Publiczny |
| 443 | HTTPS (API) | Publiczny |
| 3000 | Studio | Opcjonalnie (LAN only) |
| 5432 | PostgreSQL | Tylko lokalnie! |

```bash
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
```

---

## Monitoring

### Sprawdzenie statusu

```bash
# Wszystkie kontenery
docker compose ps

# Logi
docker compose logs -f

# Logi konkretnej usługi
docker compose logs -f auth
docker compose logs -f rest
```

### Health check

```bash
curl https://api.teamtalk.pl/rest/v1/ -I
```

---

## Troubleshooting

| Problem | Rozwiązanie |
|---------|-------------|
| 502 Bad Gateway | Sprawdź czy Docker działa: `docker compose ps` |
| SSL error | Sprawdź certyfikat: `sudo certbot renew --dry-run` |
| Auth nie działa | Sprawdź JWT_SECRET w .env |
| Realtime nie działa | Sprawdź WebSocket w nginx (Upgrade headers) |

---

## Szacowane koszty

| Element | Koszt |
|---------|-------|
| Serwer domowy | 0 zł (prąd ~20-30 zł/msc) |
| VPS (alternatywa) | 20-50 zł/msc |
| Domena .pl | ~30-40 zł/rok (~3 zł/msc) |
| SSL Let's Encrypt | 0 zł |
| **Razem** | **~3 - 55 zł/msc** |

vs Supabase Pro: ~100 zł/msc

---

## Kiedy migrować?

Rozważ migrację gdy:
- [ ] Aplikacja jest stabilna i przetestowana
- [ ] Masz więcej niż 2-3 użytkowników
- [ ] Zbliżasz się do limitów darmowego planu
- [ ] Potrzebujesz pełnej kontroli nad danymi

---

*Dokument utworzony: 2026-02-11*
