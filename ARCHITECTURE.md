# TeamTalk Android — Architektura systemu

## Decyzja projektowa

Istniejący backend TypeScript (Express + PostgreSQL) zostaje jako warstwa serwerowa.
Android łączy się z nim przez Retrofit, Room pełni rolę cache offline.

---

## Diagram systemu

```
┌─────────────────────────────────────────────────────┐
│              SERWER FIRMY (Linux VPS)                │
│                                                     │
│  ┌──────────────────────┐   ┌───────────────────┐  │
│  │  TypeScript API      │   │  PostgreSQL 13+   │  │
│  │  Node.js / Express   │──▶│  teamtalk DB      │  │
│  │  port 3000           │   │  port 5433        │  │
│  └──────────────────────┘   └───────────────────┘  │
│            ▲                                         │
│            │ HTTPS / JWT Bearer                      │
└────────────│────────────────────────────────────────┘
             │
    ┌────────┴────────┐
    │  Android App    │
    │  (Retrofit)     │
    │  (Room cache)   │
    └─────────────────┘
```

---

## Ocena backendu TypeScript

### Stan przed poprawkami (Moduł 0)

| # | Problem | Ryzyko | Status |
|---|---------|--------|--------|
| 1 | Refresh tokeny nie zapisywane do DB (`refresh_tokens` tabela nieużywana) | Brak możliwości logout/revoke | ✅ Naprawione |
| 2 | `CORS: origin: '*'` | Bezpieczeństwo produkcja | ✅ Naprawione |
| 3 | Brak rate limiting na `/api/auth` | Podatność na brute-force | ✅ Naprawione |
| 4 | Storage: lokalny filesystem | Skalowalność (wiele instancji) | Zostawione na później |

---

## Architektura Android — MVVM + Clean Architecture

Trzy warstwy zgodnie z CLAUDE.md:

```
presentation/ (Jetpack Compose + ViewModels)
      ↓ wywołuje
domain/ (Use Cases, modele biznesowe, interfejsy repozytoriów)
      ↓ implementowane przez
data/ (Retrofit remote + Room local cache + repository implementations)
```

### Struktura pakietów

```
com.ekotak.teamtalk/
│
├── TeamTalkApp.kt                     @HiltAndroidApp
├── MainActivity.kt
│
├── data/
│   ├── local/
│   │   ├── database/
│   │   │   └── TeamTalkDatabase.kt    Room DB, version 1
│   │   ├── entity/                    6 @Entity: UserEntity, ClientEntity,
│   │   │                              CallLogEntity, VoiceReportEntity,
│   │   │                              ProfileEntity, DeviceEntity
│   │   └── dao/                       6 @Dao interfejsów
│   │
│   ├── remote/
│   │   ├── api/
│   │   │   └── TeamTalkApi.kt         Retrofit interface, wszystkie endpointy
│   │   ├── dto/                       Request/Response DTO
│   │   │   ├── AuthDto.kt
│   │   │   ├── ClientDto.kt
│   │   │   ├── CallLogDto.kt
│   │   │   ├── VoiceReportDto.kt
│   │   │   ├── ProfileDto.kt
│   │   │   └── DeviceDto.kt
│   │   └── interceptor/
│   │       ├── AuthInterceptor.kt     dodaje "Bearer <token>"
│   │       └── TokenAuthenticator.kt  auto-refresh na HTTP 401
│   │
│   ├── repository/                    network-first + Room cache
│   │   ├── AuthRepositoryImpl.kt
│   │   ├── ClientRepositoryImpl.kt
│   │   ├── CallLogRepositoryImpl.kt
│   │   ├── VoiceReportRepositoryImpl.kt
│   │   ├── ProfileRepositoryImpl.kt
│   │   └── DeviceRepositoryImpl.kt
│   │
│   └── di/
│       ├── DatabaseModule.kt
│       ├── NetworkModule.kt
│       └── RepositoryModule.kt
│
├── domain/
│   ├── model/
│   │   ├── User.kt
│   │   ├── Client.kt
│   │   ├── CallLog.kt
│   │   ├── VoiceReport.kt
│   │   ├── Profile.kt
│   │   ├── Device.kt
│   │   └── Session.kt
│   ├── repository/                    6 interfejsów
│   └── usecase/
│       ├── auth/     LoginUseCase, RegisterUseCase, LogoutUseCase, GetCurrentUserUseCase
│       ├── client/   GetClientsUseCase, CreateClientUseCase, UpdateClientUseCase, DeleteClientUseCase
│       ├── calllog/  GetCallLogsUseCase, CreateCallLogUseCase, UpdateCallLogUseCase,
│       │             BulkUpdateCallLogsUseCase, AppendRecipientUseCase
│       ├── voicereport/ GetVoiceReportsUseCase, CreateVoiceReportUseCase,
│       │                UploadAudioUseCase, TranscribeAudioUseCase
│       └── profile/  GetProfilesUseCase, UpdateProfileUseCase, UpsertDeviceUseCase
│
└── presentation/
    ├── navigation/
    │   └── TeamTalkNavGraph.kt        Compose Navigation + BottomNav
    ├── auth/
    │   ├── LoginScreen.kt
    │   ├── RegisterScreen.kt
    │   └── AuthViewModel.kt
    ├── calllogs/
    │   ├── CallLogListScreen.kt       filtry: status/typ/data, pull-to-refresh
    │   ├── CallLogDetailScreen.kt     zmiana statusu, rezerwacja, voice report
    │   └── CallLogViewModel.kt
    ├── client/
    │   ├── ClientListScreen.kt        kartoteka: kategorie, filtry, duplikaty
    │   ├── ClientDetailScreen.kt      karta: Dane / Deale / Historia / Asystent
    │   ├── ClientFormScreen.kt        nowy wpis i edycja danych
    │   ├── ClientMergeScreen.kt       scalanie duplikatów
    │   ├── ClientTimelineScreen.kt    historia połączeń (także jako zakładka)
    │   └── ClientListViewModel.kt + ClientDetailViewModel.kt
    │       + ClientFormViewModel.kt + ClientMergeViewModel.kt
    ├── voicereports/
    │   ├── VoiceReportScreen.kt       nagrywanie m4a + transkrypcja Whisper
    │   └── VoiceReportViewModel.kt
    └── profile/
        ├── ProfileScreen.kt           mój profil + wylogowanie
        └── ProfileViewModel.kt
```

---

## Stack technologiczny (Android)

| Komponent | Biblioteka | Wersja |
|-----------|-----------|--------|
| UI | Jetpack Compose BOM | 2024.09.00 |
| DI | Hilt | 2.51.1 |
| DB (cache) | Room | 2.6.1 |
| HTTP | Retrofit 2 + OkHttp 4 | 2.11.0 / 4.12.0 |
| JSON | Kotlin Serialization | 1.6.3 |
| Async | Coroutines + Flow | 1.8.1 |
| Nawigacja | Navigation Compose | 2.7.7 |
| Tokeny | DataStore Preferences | 1.1.1 |
| Min SDK | Android 8.0 (API 26) | — |
| Target SDK | Android 14 (API 34) | — |

---

## Kluczowe decyzje

### Cache strategy
- `GET`: API-first → zapisz do Room → emit przez Flow; brak sieci → Room fallback
- `POST/PUT/DELETE`: API-first → aktualizuj Room po sukcesie

### Auth flow
- JWT (access 1h + refresh 30d) w `DataStore` (szyfrowany)
- `TokenAuthenticator` w OkHttp: na 401 → refresh → ponów request
- Błąd refresh → `SharedFlow` emituje `LogoutEvent` → powrót do Login

### Audio
- Nagrywanie: `MediaRecorder` → `.m4a` w `cacheDir`
- Upload: `POST /api/storage/voice-reports` (MultipartBody)
- Transkrypcja: `POST /api/functions/transcribe-audio`
- Odtwarzanie: `MediaPlayer` z URL serwera

### Nawigacja
- `BottomNavigationBar`: **CallLogi** (główny) | **Klienci** | **Profil**

---

## Plan implementacji

| # | Moduł | Zawartość |
|---|-------|-----------|
| 0 | **Backend fix** ✅ | Refresh token persistence, rate limiting, CORS |
| 1 | Projekt Android | build.gradle.kts, libs.versions.toml, Hilt App, MainActivity |
| 2 | Domain | Modele + interfejsy repozytoriów |
| 3 | Remote | TeamTalkApi (Retrofit), DTOs, AuthInterceptor, TokenAuthenticator |
| 4 | Local | Room entities, DAOs, Database |
| 5 | DI | NetworkModule, DatabaseModule, RepositoryModule |
| 6 | Repositories | Wszystkie 6 implementacji |
| 7 | Use Cases | Pełna logika biznesowa |
| 8 | Auth UI | Login + Register + ViewModel |
| 9 | CallLogs UI | Lista + Detail + ViewModel |
| 10 | Clients UI | Lista + Detail + ViewModel |
| 11 | VoiceReport UI | Nagrywanie + transkrypcja + ViewModel |
| 12 | Profile UI | Profil + wylogowanie |
| 13 | Nawigacja | NavGraph + BottomBar + Splash |
