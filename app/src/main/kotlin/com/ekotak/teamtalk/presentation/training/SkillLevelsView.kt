package com.ekotak.teamtalk.presentation.training

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.LevelToken
import com.ekotak.teamtalk.domain.model.MySkillEntry
import com.ekotak.teamtalk.domain.model.SkillLevel
import com.ekotak.teamtalk.domain.model.SkillPart
import com.ekotak.teamtalk.presentation.crm.formatDate
import com.ekotak.teamtalk.domain.model.requirementLabel
import com.ekotak.teamtalk.presentation.theme.EkotakGreen
import com.ekotak.teamtalk.presentation.theme.Red600

/**
 * „Moje poziomy" — port `SkillLevels.tsx` + `LevelGapMark.tsx`. Tabela z panelu
 * ma sześć kolumn i na 318 px nie ma czego zwężać, więc każda domena to karta:
 * nazwa, trzy kropki, cel i jedna linia „do nadgonienia". Braki idą na górę
 * (sortuje je `mineEntries`).
 *
 * ⚠️ Kropka niesie DWIE informacje: **obrys = teoria**, **środek = praktyka**.
 * Czerwień znaczy „wymagane, a nie ma" — dokładnie jak w panelu, żeby dwa
 * widoki tej samej matrycy nie uczyły dwóch różnych alfabetów.
 */
fun skillLevelItems(scope: LazyListScope, state: TrainingViewModel.UiState) {
    scope.item {
        Text(
            if (state.levels.isEmpty()) {
                "Nikt nie ustawił Ci jeszcze wymogów ani nie ocenił poziomów. Pojawią się " +
                    "tutaj, gdy zespół uzupełni je w module Zespół → „Umiejętności”."
            } else if (state.gapCount > 0) {
                "${state.gapCount} ${domainWord(state.gapCount)} do nadgonienia · " +
                    "matrycę prowadzi zarząd w module Zespół"
            } else {
                "Wszystko dowiezione · matrycę prowadzi zarząd w module Zespół"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    scope.items(state.levels, key = { it.domain.id }) { entry ->
        DomainCard(entry)
    }

    if (state.levels.isNotEmpty()) {
        scope.item { Legend() }
    }
}

@Composable
private fun DomainCard(entry: MySkillEntry) {
    val hasGap = entry.missing.isNotEmpty()
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (hasGap) Red600.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.domain.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                LevelDots(entry)
            }

            val goal = requirementLabel(entry.cell.requiredLevel, entry.cell.requiredPart)
            val deadline = formatDate(entry.cell.requiredUntil)
            Text(
                listOfNotNull(
                    "Cel: $goal",
                    deadline?.let { "termin $it" },
                    if (!entry.cell.assessed) "nieoceniona" else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (hasGap) {
                Text(
                    "Do nadgonienia: ${entry.missing.joinToString(", ") { it.label }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Red600,
                )
            }
            entry.cell.note?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Trzy kropki = trzy szczeble skali, w kolejności rosnącej. */
@Composable
private fun LevelDots(entry: MySkillEntry) {
    val required = entry.cell.requiredLevel
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        SkillLevel.entries.forEach { level ->
            val hasTheory = LevelToken(level, SkillPart.TEORIA) in entry.cell.levels
            val hasPractice = LevelToken(level, SkillPart.PRAKTYKA) in entry.cell.levels
            val wanted = required != null && level.ordinal <= required.ordinal
            // Praktyka zakłada teorię; wymóg „sama teoria" praktyki nie żąda.
            val needsTheory = wanted
            val needsPractice = wanted && entry.cell.requiredPart == SkillPart.PRAKTYKA
            LevelDot(
                ring = when {
                    hasTheory -> EkotakGreen
                    needsTheory -> Red600
                    else -> PaleRing
                },
                fill = when {
                    hasPractice -> EkotakGreen
                    needsPractice -> Red600
                    else -> Color.Transparent
                },
            )
        }
    }
}

private val PaleRing = Color(0x669FB0C3)

@Composable
private fun LevelDot(ring: Color, fill: Color) {
    Box(
        modifier = Modifier
            .size(14.dp)
            .border(2.dp, ring, CircleShape)
            .padding(3.dp)
            .background(fill, CircleShape),
    )
}

@Composable
private fun Legend() {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LegendRow(EkotakGreen, Color.Transparent, "obrys = teoria zdana")
            LegendRow(EkotakGreen, EkotakGreen, "środek = praktyka zdana")
            LegendRow(Red600, Red600, "czerwone = wymagane, a brak")
            Text(
                "Kolejność kropek: ${SkillLevel.entries.joinToString(" · ") { it.label }}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LegendRow(ring: Color, fill: Color, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LevelDot(ring, fill)
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** „1 domena", „3 domeny", „5 domen" — liczebnik jak w panelu. */
private fun domainWord(count: Int): String = when {
    count == 1 -> "domena"
    count % 10 in 2..4 && count % 100 !in 12..14 -> "domeny"
    else -> "domen"
}
