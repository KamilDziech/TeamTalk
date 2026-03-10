#!/bin/bash

# Skrypt do serwowania najnowszego APK z kodem QR
# Wymaga: python3, qrencode

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Znajdź najnowszy plik APK
APK_FILE=$(ls -t *.apk 2>/dev/null | head -1)

if [ -z "$APK_FILE" ]; then
    echo "Nie znaleziono pliku .apk w katalogu $SCRIPT_DIR"
    exit 1
fi

echo "Znaleziono APK: $APK_FILE"
echo "Rozmiar: $(du -h "$APK_FILE" | cut -f1)"

# Znajdź wolny port (8080-8099)
find_free_port() {
    for port in $(seq 8080 8099); do
        if ! ss -tuln | grep -q ":$port "; then
            echo $port
            return
        fi
    done
    # Jeśli wszystkie zajęte, użyj losowego
    echo $((RANDOM % 1000 + 9000))
}

PORT=$(find_free_port)

# Pobierz lokalne IP
LOCAL_IP=$(hostname -I | awk '{print $1}')

if [ -z "$LOCAL_IP" ]; then
    LOCAL_IP="localhost"
fi

URL="http://${LOCAL_IP}:${PORT}/${APK_FILE}"

echo ""
echo "=========================================="
echo "Serwer uruchomiony na porcie: $PORT"
echo "URL do pobrania: $URL"
echo "=========================================="
echo ""

# Sprawdź czy qrencode jest dostępne
if command -v qrencode &> /dev/null; then
    QR_FILE="${SCRIPT_DIR}/apk-qrcode.png"
    qrencode -o "$QR_FILE" -s 10 "$URL"
    echo "Kod QR zapisany do: $QR_FILE"
    echo ""
    # Wyświetl też w terminalu
    echo "Kod QR (zeskanuj telefonem):"
    echo ""
    qrencode -t ANSIUTF8 "$URL"
    echo ""
else
    echo "Zainstaluj qrencode aby wygenerować kod QR:"
    echo "  sudo apt install qrencode"
    echo ""
    echo "Lub użyj tego URL: $URL"
fi

echo "Naciśnij Ctrl+C aby zatrzymać serwer..."
echo ""

# Uruchom serwer HTTP
python3 -m http.server $PORT
