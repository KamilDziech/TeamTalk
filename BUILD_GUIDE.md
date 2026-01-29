# TeamTalk - Przewodnik Budowania Development APK

## ⚠️ Dlaczego potrzebujemy development build?

**Expo Go NIE OBSŁUGUJE:**
- `react-native-call-log` - wymaga natywnego kodu Android
- Niestandardowych uprawnień (READ_CALL_LOG)
- Config Plugins z niestandardowymi modułami

**Development Build pozwala:**
- ✅ Używać natywnych modułów (CallLog)
- ✅ Testować rzeczywiste wykrywanie połączeń
- ✅ Działać jak Expo Go, ale z pełnym dostępem do natywnego kodu

---

## 🚀 Metoda 1: Lokalny Build (Zalecane dla deweloperów)

### Wymagania:
- Node.js 20+ ✅ (już zainstalowane)
- Android Studio + Android SDK
- JDK 17+
- Urządzenie Android lub emulator

### Kroki:

#### 1. Zainstaluj Android Studio
```bash
# Pobierz ze strony: https://developer.android.com/studio
# Po instalacji otwórz SDK Manager i zainstaluj:
# - Android SDK Platform 34
# - Android SDK Build-Tools 34.0.0
# - Android SDK Command-line Tools
```

#### 2. Ustaw zmienne środowiskowe
```bash
# Dodaj do ~/.bashrc lub ~/.zshrc:
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools
export PATH=$PATH:$ANDROID_HOME/tools
export PATH=$PATH:$ANDROID_HOME/tools/bin
export PATH=$PATH:$ANDROID_HOME/emulator

# Przeładuj shell:
source ~/.bashrc
```

#### 3. Prebuild projektu Expo
```bash
cd /home/kamil/projects/ekotak/TeamTalk

# Generuje foldery android/ i ios/ z konfiguracją
npx expo prebuild --platform android
```

#### 4. Zbuduj development APK
```bash
# Opcja A: Uruchom na podłączonym urządzeniu/emulatorze
npx expo run:android

# Opcja B: Zbuduj samodzielny APK
cd android
./gradlew assembleDebug

# APK będzie w: android/app/build/outputs/apk/debug/app-debug.apk
```

#### 5. Zainstaluj na telefonie
```bash
# Jeśli telefon podłączony przez USB:
adb install android/app/build/outputs/apk/debug/app-debug.apk

# Lub skopiuj plik app-debug.apk na telefon i zainstaluj ręcznie
```

---

## ☁️ Metoda 2: EAS Build (Łatwiejsza, wymaga konta)

### Wymagania:
- Konto Expo (darmowe: https://expo.dev)
- EAS CLI

### Kroki:

#### 1. Zainstaluj EAS CLI
```bash
npm install -g eas-cli
```

#### 2. Zaloguj się
```bash
eas login
```

#### 3. Skonfiguruj projekt
```bash
eas build:configure
```

#### 4. Zbuduj development build
```bash
# Build w chmurze (zajmie ~5-10 minut)
eas build --platform android --profile development

# Po zakończeniu pobierze link do APK
# Pobierz APK na telefon i zainstaluj
```

#### 5. Uruchom development server
```bash
# Po zainstalowaniu APK, uruchom:
npx expo start --dev-client

# Zeskanuj QR code w zainstalowanej aplikacji
```

---

## 📱 Testowanie na urządzeniu

### Po zainstalowaniu development build:

1. **Pierwsze uruchomienie:**
   - Aplikacja poprosi o uprawnienia
   - **WAŻNE:** Przyznaj READ_CALL_LOG i POST_NOTIFICATIONS

2. **Dodaj testowego klienta:**
   - Przejdź do zakładki "Dodaj"
   - Wprowadź swój numer telefonu (lub telefonu przyjaciela)
   - Kliknij "Dodaj klienta"

3. **Testuj wykrywanie nieodebranych:**
   - Zadzwoń na telefon z aplikacją z dodanego numeru
   - NIE odbieraj połączenia
   - Poczekaj ~30 sekund (czas na skanowanie CallLog)
   - Powinna pojawić się powiadomienie: "🔴 Nieodebrane od: [Twoja Nazwa]"

4. **Sprawdź kolejkę:**
   - Przejdź do zakładki "Kolejka"
   - Powinieneś zobaczyć nieodebrane połączenie
   - Kliknij "Rezerwuję" aby zarezerwować
   - Status zmieni się na 🟡 Żółty

5. **Testuj Realtime:**
   - Jeśli masz dwa telefony z aplikacją
   - Zarezerwuj połączenie na jednym
   - Na drugim powinno automatycznie zaktualizować status

---

## 🐛 Troubleshooting

### Problem: `react-native-call-log not found`
```bash
# Usuń i przebuduj:
cd android
./gradlew clean
cd ..
npx expo prebuild --clean
npx expo run:android
```

### Problem: Uprawnienia nie działają
```bash
# Sprawdź AndroidManifest.xml:
cat android/app/src/main/AndroidManifest.xml | grep permission

# Powinny być:
# - android.permission.READ_CALL_LOG
# - android.permission.POST_NOTIFICATIONS
```

### Problem: CallLog jest pusty
```bash
# Sprawdź czy aplikacja ma uprawnienia w ustawieniach telefonu:
# Settings > Apps > TeamTalk > Permissions
# Upewnij się że "Call logs" i "Notifications" są włączone
```

---

## ✅ Checklist przed testem produkcyjnym

- [ ] Development APK zbudowany i zainstalowany
- [ ] Uprawnienia przyznane (READ_CALL_LOG, POST_NOTIFICATIONS)
- [ ] Testowy klient dodany do bazy
- [ ] Nieodebrane połączenie wykryte i powiadomienie wysłane
- [ ] Realtime synchronizacja działa między urządzeniami
- [ ] Alert "WYMAGA NOTATKI" pojawia się dla połączeń bez voice_report

---

## 📞 Gotowe do testowania Fazy 2!

Po wykonaniu powyższych kroków aplikacja powinna:
1. ✅ Wykrywać nieodebrane połączenia od znanych klientów
2. ✅ Wysyłać powiadomienia lokalne
3. ✅ Wyświetlać kolejkę nieodebranych w aplikacji
4. ✅ Synchronizować statusy w czasie rzeczywistym
5. ✅ Pokazywać alert "WYMAGA NOTATKI" dla połączeń bez notatek

**Kryterium sukcesu Fazy 2 spełnione!** 🎉
