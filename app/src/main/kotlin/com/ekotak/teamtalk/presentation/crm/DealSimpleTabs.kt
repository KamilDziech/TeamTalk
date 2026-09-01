package com.ekotak.teamtalk.presentation.crm

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.DealActivity
import com.ekotak.teamtalk.domain.model.DealDetail
import com.ekotak.teamtalk.domain.model.TaskMember

/**
 * Dwie zakładki karty, które z natury są tylko do odczytu: „Historia"
 * (append-only log zmian) i „Podsumowanie" (zebrane wartości deala).
 */

/**
 * Log aktywności deala. API zwraca wpisy malejąco po dacie, więc pokazujemy je
 * w tej samej kolejności co panel — najnowsza zmiana na górze.
 */
@Composable
fun DealHistoryTab(activities: List<DealActivity>, members: List<TaskMember>) {
    if (activities.isEmpty()) {
        SectionCard {
            SectionTitle("Historia")
            SectionGap()
            Text(
                text = "Brak zapisów w historii tego deala.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val byId = members.associateBy { it.id }
    SectionCard {
        SectionTitle("Historia · ${activities.size}")
        SectionGap()
        activities.forEachIndexed { index, activity ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = activityLabel(activity),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = listOfNotNull(
                        byId[activity.userId]?.displayName,
                        formatDateTime(activity.createdAt),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Zebrane wartości karty — to, co handlowiec chce zobaczyć jednym rzutem oka
 * przed rozmową. Odpowiednik `DealSummaryPanel` panelu, na razie bez ofert
 * i zakresu instalacji: obie te sekcje wymagają endpointów z kolejnych etapów.
 */
@Composable
fun DealSummaryTab(detail: DealDetail, members: List<TaskMember>) {
    val deal = detail.deal
    val byId = members.associateBy { it.id }

    SectionCard {
        SectionTitle("Klient")
        SectionGap()
        InfoRow("Nazwa", detail.client?.displayName)
        InfoRow("Telefon", detail.client?.primaryPhone)
        InfoRow("E-mail", detail.client?.email)
        InfoRow("Adres", detail.client?.address)
        InfoRow(
            label = "Kontakty towarzyszące",
            value = detail.companions.joinToString(", ") { it.displayName }.ifBlank { null },
        )
    }
    SectionGap()

    SectionCard {
        SectionTitle("Proces")
        SectionGap()
        InfoRow("Etap", deal.stage.label)
        InfoRow("W etapie od", formatDate(deal.stageEnteredAt))
        InfoRow("Czas w etapie", stageAgeLabel(deal.stageEnteredAt))
        InfoRow("Opiekun", byId[deal.ownerId]?.displayName ?: deal.ownerId.takeIf { it.isNotBlank() })
        InfoRow("Opiekun etapu", deal.stageOwnerId?.let { byId[it]?.displayName ?: it })
        InfoRow("Następny kontakt", formatDate(deal.nextContactAt))
        InfoRow("Zgłoszenie", formatDateTime(deal.createdAt))
    }
    SectionGap()

    SectionCard {
        SectionTitle("Zakres")
        SectionGap()
        InfoRow("Projekt", deal.projectName)
        InfoRow("Segment", deal.segment.label)
        InfoRow("Budynek", deal.buildingKind.label)
        InfoRow("Powierzchnia", deal.buildingData?.areaM2?.let { "${it.toInt()} m²" })
        InfoRow("OZC budynek", deal.ozcData?.buildingKw?.let { "${it.toPlainText()} kW" })
        InfoRow("Trudność", deal.difficulty?.label)
        InfoRow("Źródło", deal.source)
    }
}
