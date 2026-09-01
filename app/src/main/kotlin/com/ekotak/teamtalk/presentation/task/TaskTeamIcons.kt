package com.ekotak.teamtalk.presentation.task

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.TaskTeam

/**
 * Ikony kafelków „jaki zespół". Kreska 1.7 na siatce 24, jak ikony modułów
 * pulpitu — ten sam zabieg co w `HomeModules.kt`, bo `material-icons-extended`
 * nie jest zależnością aplikacji, a rysunki i tak mają być spójne z board360.
 */
private fun teamIcon(vararg paths: String): ImageVector =
    ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        paths.forEach { data ->
            addPath(
                pathData = addPathNodes(data),
                stroke = SolidColor(Color.Black), // kolor nadaje `tint` przy rysowaniu
                strokeLineWidth = 1.7f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

private val Moje = teamIcon(
    "M15.4,8 a3.4,3.4 0 1 0 -6.8,0 a3.4,3.4 0 1 0 6.8,0",
    "M5 20a7 7 0 0 1 14 0",
)
private val Serwis = teamIcon(
    "M15.4 4.4a4.5 4.5 0 0 0-5.9 5.5L4 15.4a2 2 0 0 0 2.8 2.8l5.5-5.5a4.5 4.5 0 0 0 5.5-5.9l-2.7 2.7-2.4-.6-.6-2.4z",
)
private val Inzynier = teamIcon(
    "M12 3 5 6v5.5c0 4.3 2.9 7.7 7 9.5 4.1-1.8 7-5.2 7-9.5V6z",
    "m9.2 12 2 2 3.6-3.8",
)
private val Koordynator = teamIcon(
    "M8.2,6 a2.2,2.2 0 1 0 -4.4,0 a2.2,2.2 0 1 0 4.4,0",
    "M20.2,18 a2.2,2.2 0 1 0 -4.4,0 a2.2,2.2 0 1 0 4.4,0",
    "M8.2 6H14a3.5 3.5 0 0 1 0 7h-4a3.5 3.5 0 0 0 0 7h5.8",
)
private val Zaopatrzenie = teamIcon(
    "M20.5 15.6V8.4a1.5 1.5 0 0 0-.8-1.3l-6.9-3.8a1.5 1.5 0 0 0-1.6 0L4.3 7.1a1.5 1.5 0 0 0-.8 1.3v7.2a1.5 1.5 0 0 0 .8 1.3l6.9 3.8a1.5 1.5 0 0 0 1.6 0l6.9-3.8a1.5 1.5 0 0 0 .8-1.3z",
    "M3.7 7.6 12 12.2l8.3-4.6M12 12.2V21",
)
private val Ksiegowosc = teamIcon(
    "M6 3h12v18l-2-1.4-2 1.4-2-1.4-2 1.4-2-1.4L6 21z",
    "M9 8h6M9 12h6M9 16h3",
)
private val Bow = teamIcon(
    "M4 13v-1a8 8 0 0 1 16 0v1",
    "M4 13h2.5v5H5.5A1.5 1.5 0 0 1 4 16.5zM20 13h-2.5v5h1A1.5 1.5 0 0 0 20 16.5z",
    "M17.5 18v.5a2.5 2.5 0 0 1-2.5 2.5h-2",
)
private val Montaz = teamIcon(
    "m14.5 5.5 4 4M17 3l4 4-2.5 2.5-4-4z",
    "m14 9-9 9a2.1 2.1 0 0 0 3 3l9-9",
)
private val Technolog = teamIcon(
    "M9.5 3v6L4.8 17.6A2 2 0 0 0 6.6 20.6h10.8a2 2 0 0 0 1.8-3L14.5 9V3z",
    "M8.5 3h7M7.4 14.5h9.2",
)
private val Marketing = teamIcon(
    "M4 10v4a1.5 1.5 0 0 0 1.5 1.5H8l8 4.5V5L8 9.5H5.5A1.5 1.5 0 0 0 4 11z",
    "M18.5 9.5a3.5 3.5 0 0 1 0 5",
)
private val Dotacje = teamIcon(
    "M5.5,6.5 a6.5,2.8 0 1 0 13,0 a6.5,2.8 0 1 0 -13,0",
    "M5.5 6.5v5c0 1.5 2.9 2.8 6.5 2.8s6.5-1.3 6.5-2.8v-5",
    "M5.5 11.5v5c0 1.5 2.9 2.8 6.5 2.8s6.5-1.3 6.5-2.8v-5",
)
private val Projekt = teamIcon(
    "m3.5 15.5 12-12 5 5-12 12z",
    "m7 12 2 2M10 9l2 2M13 6l2 2",
)

/** Ikona kafelka. */
val TaskTeam.icon: ImageVector
    get() = when (this) {
        TaskTeam.MOJE -> Moje
        TaskTeam.SERWIS -> Serwis
        TaskTeam.INZYNIER -> Inzynier
        TaskTeam.KOORDYNATOR -> Koordynator
        TaskTeam.ZAOPATRZENIE -> Zaopatrzenie
        TaskTeam.KSIEGOWOSC -> Ksiegowosc
        TaskTeam.BOW -> Bow
        TaskTeam.MONTAZ -> Montaz
        TaskTeam.TECHNOLOG -> Technolog
        TaskTeam.MARKETING -> Marketing
        TaskTeam.DOTACJE -> Dotacje
        TaskTeam.PROJEKT -> Projekt
    }
