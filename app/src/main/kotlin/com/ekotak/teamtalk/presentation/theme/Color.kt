package com.ekotak.teamtalk.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * Paleta zgodna z aplikacją wzorcową board360 (ekotak.app) i księgą znaku
 * EKOTAK v1.2023: zieleń neonowa Pantone 802 C (#44d62c) + czerń (#080808).
 * System jest "dark-first" — motyw ciemny (granat → czerń) jest tożsamością
 * marki, motyw jasny neutralizuje tło zachowując akcent zieleni.
 */

// ── Marka ────────────────────────────────────────────────────────────
val EkotakGreen        = Color(0xFF44D62C) // --accent
val EkotakGreenDark    = Color(0xFF2FA84F) // ciemniejszy odcień pod biały tekst
val EkotakBlack        = Color(0xFF080808) // --accent-contrast (tekst na zieleni)

// ── Ciemny motyw (domyślny, wg board360) ─────────────────────────────
val NavyBg             = Color(0xFF0B1220) // --bg-2 (tło)
val NavySurface        = Color(0xFF12161F) // --surface-2 (karty/pola)
val NavyPanel          = Color(0xFF1A212E) // --panel (nieprzezroczysty odpowiednik)
val FgDark             = Color(0xFFE6EDF3) // --fg
val MutedDark          = Color(0xFF9FB0C3) // --muted
val BorderDark         = Color(0xFF2A3441) // --border (~rgba white .08)

// ── Jasny motyw (wg board360) ────────────────────────────────────────
val LightBg            = Color(0xFFEEF3F9) // --bg-2
val LightSurface       = Color(0xFFFFFFFF) // --surface-2
val LightPanel         = Color(0xFFDBE6F2) // --bg-1
val FgLight            = Color(0xFF0F1720) // --fg
val MutedLight         = Color(0xFF5A6B7C) // --muted
val BorderLight        = Color(0xFFC3CBD4) // --border (~rgba black .12)

// ── Kolory semantyczne (spójne z modułami board360) ──────────────────
// Nazwy zachowane dla zgodności z ekranami, wartości = paleta wzorcowa.
val Green600  = EkotakGreenDark      // akcja sukcesu (czytelny z białym tekstem)
val Red600    = Color(0xFFE5484D)    // danger/error (board360 #e5484d)
val Orange600 = Color(0xFFF59E0B)    // warning/pending (board360 #f59e0b)
val OkGreen   = Color(0xFF7EE787)    // --ok (status/tekst)
val SyncBlue  = Color(0xFF5B8DEF)    // info — zmiana czekająca na wysyłkę (kolejka offline)
