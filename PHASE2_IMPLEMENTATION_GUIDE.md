# Faza 2: Monitoring Połączeń - Przewodnik Implementacji

## ⚠️ Ważna informacja

Pełna implementacja natywnego modułu do monitoringu połączeń wymaga:
1. **Zaawansowanej wiedzy z Kotlin/Java** - PhoneStateListener, BroadcastReceiver
2. **Konfiguracji Expo Modules** - expo-modules-core, autolinking
3. **Budowania development build** - `expo run:android` lub EAS Build
4. **Testowania na fizycznym urządzeniu** - emulator ma ograniczone możliwości telefonii

## 📋 Plan Implementacji

### Opcja A: Natywny Moduł (Zaawansowane)

**Wymagane pliki Android (Kotlin):**

```
modules/call-monitoring/android/src/main/java/com/teamtalk/callmonitoring/
├── CallMonitoringModule.kt        # Główny moduł Expo
├── CallMonitoringPackage.kt       # Package definition
├── PhoneStateReceiver.kt          # BroadcastReceiver dla połączeń
├── CallMonitoringService.kt       # Foreground Service
└── CallEventEmitter.kt            # EventEmitter do komunikacji z JS
```

**Kluczowy kod Kotlin:**

```kotlin
// CallMonitoringModule.kt
package com.teamtalk.callmonitoring

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class CallMonitoringModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("CallMonitoring")

    Events("onCallStateChanged", "onMissedCall", "onCallEnded")

    AsyncFunction("startMonitoring") {
      // Start PhoneStateListener
    }

    AsyncFunction("stopMonitoring") {
      // Stop PhoneStateListener
    }

    AsyncFunction("requestPermissions") {
      // Request READ_PHONE_STATE, READ_CALL_LOG
    }
  }
}
```

### Opcja B: React Native Modules (Łatwiejsze)

Użyj gotowych bibliotek:
- `react-native-call-detection` - wykrywanie połączeń
- `react-native-phone-call` - informacje o połączeniach
- `@react-native-community/push-notification-ios` + custom Android - powiadomienia

### Opcja C: Mockowanie + Przyszła Integracja (Zalecane na start)

**Zalecam na początek:**

1. **Mockuj PhoneStateListener** - użyj przycisków do symulacji:
   - "Symuluj nieodebrane"
   - "Symuluj zakończenie rozmowy"

2. **Zbuduj całą logikę biznesową:**
   - Dodawanie call_logs do Supabase
   - Wysyłanie powiadomień
   - Realtime synchronizacja
   - UI kolejki nieodebranych

3. **Później zamień mock na prawdziwy moduł**

## 🎯 Kryterium Sukcesu Fazy 2

> "Po symulacji nieodebranego połączenia w bazie danych pojawia się nowy rekord,
> a po zakończeniu rozmowy telefon wyświetla powiadomienie systemowe"

**Można osiągnąć przez:**
- ✅ Przyciski testowe (mock) - szybko, testuje logikę
- ✅ Natywny moduł - pełna funkcjonalność, wymaga czasu

## 📦 Co już jest gotowe:

✅ CallLogService z TDD (testy przechodzą)
✅ Struktura bazy danych w Supabase
✅ Realtime włączony dla call_logs
✅ Interfejs TypeScript modułu (modules/call-monitoring/index.ts)
✅ expo-dev-client zainstalowany

## 🚀 Następne kroki (wybierz podejście):

### Podejście Mockowe (2-3 godziny):
1. Stwórz UI z przyciskami do symulacji
2. Zaimplementuj logikę dodawania call_logs
3. Dodaj system powiadomień lokalnych
4. Przetestuj Realtime synchronizację
5. **Faza 2 ukończona** ✅

### Podejście Natywne (1-2 dni):
1. Dokończ implementację Kotlin
2. Skonfiguruj expo-modules-autolinking
3. Zbuduj development build (`npx expo run:android`)
4. Przetestuj na fizycznym urządzeniu
5. Debug i poprawki

## 💡 Rekomendacja

**Zacznij od mocków**, zbuduj całą logikę i UI, a natywny moduł dodaj później.
To pozwoli Ci:
- ✅ Szybko kontynuować rozwój
- ✅ Przetestować architekturę
- ✅ Pokazać działającą funkcjonalność
- ✅ Zrozumieć wymagania przed native code

Później można:
1. Znaleźć gotową bibliotekę (react-native-call-detection)
2. Zlecić implementację modułu deweloperowi Android
3. Samodzielnie nauczyć się Kotlin i zaimplementować

---

**Co wybierasz?** Mockowe podejście czy native implementation?
