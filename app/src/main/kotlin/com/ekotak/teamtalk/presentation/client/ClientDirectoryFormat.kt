package com.ekotak.teamtalk.presentation.client

import androidx.compose.ui.graphics.Color
import com.ekotak.teamtalk.domain.model.DealStage
import java.text.NumberFormat
import java.util.Locale

/**
 * Oznaczenia i etykiety kartoteki — odpowiednik `catalog-badges.ts` i etykiet
 * z `ClientsView` w panelu. Skróty instalacji trzymamy 1:1 z webem, żeby ta sama
 * litera na kafelku znaczyła to samo na telefonie i na tablicy Kanban.
 */
data class InstallBadge(val label: String, val background: Color, val foreground: Color)

private val ROOT_BADGES: Map<String, InstallBadge> = mapOf(
    "ogrzewanie" to InstallBadge("O", Color(0xFFE24B4A), Color.White),
    "ogrzewanie podłogowe" to InstallBadge("OP", Color(0xFFE8833A), Color.White),
    "klimatyzacja" to InstallBadge("K", Color(0xFF5CC0E8), Color(0xFF08243A)),
    "fotowoltaika" to InstallBadge("PV", Color(0xFFF2C517), Color(0xFF3A2F00)),
    "magazyn energii" to InstallBadge("Me", Color(0xFFCF8A00), Color(0xFF3A2F00)),
    "magazyn" to InstallBadge("Me", Color(0xFFCF8A00), Color(0xFF3A2F00)),
    "rekuperacja" to InstallBadge("R", Color(0xFF2E9E5B), Color.White),
    "wod-kan" to InstallBadge("WK", Color(0xFF2A78D6), Color.White),
)

/** Badge instalacji; nieznana kategoria dostaje dwie litery na neutralnym tle. */
fun installBadge(name: String): InstallBadge =
    ROOT_BADGES[name.trim().lowercase()] ?: InstallBadge(
        label = name.trim().take(2).uppercase().ifBlank { "?" },
        background = Color(0xFF5F5E5A),
        foreground = Color.White,
    )

/** Etykieta głównego etapu: etap lejka albo „Zakończone" / „Zarchiwizowane". */
fun mainStageLabel(stage: DealStage): String = when (stage) {
    DealStage.ZAKONCZONY -> "Zakończone"
    DealStage.LOST -> "Zarchiwizowane"
    else -> stage.label
}

/** Etapy w filtrze kartoteki: lejek + dwa stany domknięte (jak w panelu). */
val DIRECTORY_STAGES: List<DealStage> =
    com.ekotak.teamtalk.domain.model.PIPELINE_STAGES + listOf(DealStage.ZAKONCZONY, DealStage.LOST)

private val plnFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale("pl", "PL")).apply {
    maximumFractionDigits = 0
}

/** Kwota szansy: „64 200 zł". Zero i brak wartości pokazujemy jako kreskę. */
fun formatPln(value: Double): String =
    if (value <= 0.0) "—" else plnFormat.format(value)

/** Dojazd: „24 km / 31 min". */
fun formatTravelLeg(km: Double, min: Double): String =
    "${km.toInt()} km / ${min.toInt()} min"
