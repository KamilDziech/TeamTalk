package com.ekotak.teamtalk.presentation.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Rejestr modułów pulpitu — odwzorowanie ekranu startowego board360
 * (web/src/app/app/Dashboard.tsx + lib/modules.ts). Etykiety, opisy, kolory
 * akcentu i ikony są przeniesione 1:1, żeby aplikacja mobilna i panel czytały
 * się jak jeden produkt.
 *
 * Świadomie pominięte na mobile (ustalone z zamawiającym): Raporty, Zasoby,
 * Marketing i Faktury KSeF — to moduły „biurkowe", bez sensownego zastosowania
 * w terenie. Kolejność pozostałych = DEFAULT_ORDER z pulpitu board360.
 */
data class HomeModule(
    /** Klucz zgodny z rejestrem modułów board360 (MODULES.key). */
    val key: String,
    val label: String,
    val desc: String,
    /** Kolor akcentu kafelka (META[key].color w board360). */
    val color: Color,
    val icon: ImageVector,
)

// ── Pomocniki: SVG board360 → ImageVector ────────────────────────────────────
// Ikony w panelu to kreska 1.7 na siatce 24 (fill: none). Odtwarzamy je jako
// ścieżki wektorowe — kolor nadaje `tint` przy rysowaniu (Icon), stąd czarny
// SolidColor jako wartość zastępcza.

/** `<rect x y width height rx>` z SVG jako dane ścieżki. */
private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float): String =
    "M${x + r},$y H${x + w - r} A$r,$r 0 0 1 ${x + w},${y + r} " +
        "V${y + h - r} A$r,$r 0 0 1 ${x + w - r},${y + h} " +
        "H${x + r} A$r,$r 0 0 1 $x,${y + h - r} " +
        "V${y + r} A$r,$r 0 0 1 ${x + r},$y Z"

/** `<circle cx cy r>` z SVG jako dane ścieżki. */
private fun circle(cx: Float, cy: Float, r: Float): String =
    "M${cx - r},$cy a$r,$r 0 1 0 ${2 * r},0 a$r,$r 0 1 0 ${-2 * r},0"

private fun moduleIcon(vararg paths: String): ImageVector =
    ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        paths.forEach { data ->
            addPath(
                pathData = addPathNodes(data),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.7f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

/** Moduły ekranu startowego, w kolejności z pulpitu board360. */
val HOME_MODULES: List<HomeModule> = listOf(
    HomeModule(
        key = "assistant",
        label = "Asystent",
        desc = "Czat AI oparty na wiedzy firmy — pytania i akcje",
        color = Color(0xFFA371F7),
        icon = moduleIcon(
            rect(3f, 4f, 18f, 13f, 2.5f),
            "M8.5 17v3.2L12.8 17",
            "M12 7.2l1 2.6 2.6 1-2.6 1-1 2.6-1-2.6-2.6-1 2.6-1z",
        ),
    ),
    HomeModule(
        key = "crm",
        label = "CRM",
        desc = "Leady, deale, oferty — główny moduł sprzedaży",
        color = Color(0xFF44D62C),
        icon = moduleIcon("M3.5 5h17l-6.6 7.6V19l-3.8 1.8v-8.2z"),
    ),
    HomeModule(
        key = "clients",
        label = "Klienci",
        desc = "Kartoteka klientów — dane i historia instalacji",
        color = Color(0xFF38BDF8),
        icon = moduleIcon(
            circle(9f, 8f, 3.2f),
            "M3 19.5a6 6 0 0 1 12 0",
            "M16.5 5.6a3.2 3.2 0 0 1 0 4.8",
            "M18 14.4a6 6 0 0 1 3 5.1",
        ),
    ),
    HomeModule(
        key = "map",
        label = "Mapa",
        desc = "Deale i klienci na mapie regionu",
        color = Color(0xFFF778BA),
        icon = moduleIcon(
            "M9 4.5 3.5 6.7v12.8L9 17.3l6 2.2 5.5-2.2V4.5L15 6.7z",
            "M9 4.5v12.8M15 6.7v12.8",
        ),
    ),
    HomeModule(
        key = "communication",
        label = "Komunikacja",
        desc = "WhatsApp, e-mail i notatki w jednym miejscu",
        color = Color(0xFF22D3EE),
        icon = moduleIcon(
            "M15.5 13.5a1.8 1.8 0 0 1-1.8 1.8H8l-3.4 3v-3a1.8 1.8 0 0 1-1.3-1.8v-7a1.8 1.8 0 0 1 1.8-1.8h8.6a1.8 1.8 0 0 1 1.8 1.8z",
            "M18.9 8.5h.4A1.8 1.8 0 0 1 21 10.3v7a1.8 1.8 0 0 1-1.3 1.8v3l-2.6-2.3",
        ),
    ),
    HomeModule(
        key = "installations",
        label = "Montaże",
        desc = "Planowanie i realizacja instalacji",
        color = Color(0xFF4F8CFF),
        icon = moduleIcon(
            rect(3f, 5f, 18f, 11f, 1.5f),
            "M9 5v11M15 5v11M3 10.5h18",
            "M12 16v4M8.5 20h7",
        ),
    ),
    HomeModule(
        key = "service",
        label = "Serwis",
        desc = "Zgłoszenia i przeglądy serwisowe",
        color = Color(0xFFFF7B54),
        icon = moduleIcon(
            "M15.4 4.4a4.5 4.5 0 0 0-5.9 5.5L4 15.4a2 2 0 0 0 2.8 2.8l5.5-5.5a4.5 4.5 0 0 0 5.5-5.9l-2.7 2.7-2.4-.6-.6-2.4z",
        ),
    ),
    HomeModule(
        key = "inventory",
        label = "Magazyn",
        desc = "Stany magazynowe i ruchy materiałów",
        color = Color(0xFFEAB308),
        icon = moduleIcon(
            "M20.5 15.6V8.4a1.5 1.5 0 0 0-.8-1.3l-6.9-3.8a1.5 1.5 0 0 0-1.6 0L4.3 7.1a1.5 1.5 0 0 0-.8 1.3v7.2a1.5 1.5 0 0 0 .8 1.3l6.9 3.8a1.5 1.5 0 0 0 1.6 0l6.9-3.8a1.5 1.5 0 0 0 .8-1.3z",
            "M3.7 7.6 12 12.2l8.3-4.6M12 12.2V21",
        ),
    ),
    HomeModule(
        key = "tasks",
        label = "Zadania",
        desc = "Twoje zadania i przypomnienia",
        color = Color(0xFFC084FC),
        icon = moduleIcon(
            "M9 4.5H6.5a1.5 1.5 0 0 0-1.5 1.5v13a1.5 1.5 0 0 0 1.5 1.5h11a1.5 1.5 0 0 0 1.5-1.5V6a1.5 1.5 0 0 0-1.5-1.5H15",
            rect(9f, 2.8f, 6f, 3.4f, 1f),
            "M8.6 12l1.7 1.7 3.4-3.4",
            "M15.5 16.5h-7",
        ),
    ),
    HomeModule(
        key = "calendar",
        label = "Kalendarz",
        desc = "Terminy, wizyty i wydarzenia zespołu",
        color = Color(0xFFFB7185),
        icon = moduleIcon(
            rect(3.5f, 5f, 17f, 15.5f, 1.8f),
            "M3.5 9.5h17M8 3.5v3M16 3.5v3",
            rect(7f, 12.5f, 3f, 3f, 0.6f),
        ),
    ),
)

/** Moduł po kluczu — dla ekranu-zaślepki otwieranego z kafelka. */
fun homeModule(key: String?): HomeModule? = HOME_MODULES.firstOrNull { it.key == key }
