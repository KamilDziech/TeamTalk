package com.ekotak.teamtalk.presentation.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.TrackerHealth

/**
 * Lokalizatory — arkusz odpowiadający na „czemu tego auta nie widać na mapie".
 *
 * Brak IMEI, cisza, odcięte zasilanie i usterka to cztery różne sprawy, a pin
 * (albo jego brak) wygląda w każdej z nich tak samo. Opisy problemów układa
 * serwer, więc telefon i panel mówią dokładnie to samo — telefon dokłada
 * wyłącznie kolor, kolejność i przejście do historii trasy, bo to ona pokazuje,
 * co auto robiło tuż przed zamilknięciem.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerHealthSheet(
    items: List<TrackerHealth>,
    isLoading: Boolean,
    onOpenRoute: (TrackerHealth) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val doRuchu = items.count { it.status != com.ekotak.teamtalk.domain.model.TrackerStatus.OK }
            Text(
                "Lokalizatory",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                when {
                    isLoading && items.isEmpty() -> "Sprawdzam…"
                    items.isEmpty() -> "Brak pojazdów do oceny."
                    doRuchu == 0 -> "Wszystkie nadają. Ocena z ostatniej doby."
                    else -> "$doRuchu ${if (doRuchu == 1) "pozycja wymaga" else "pozycji wymaga"} uwagi. " +
                        "Ocena z ostatniej doby."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (isLoading && items.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items, key = { it.assetId }) { health ->
                    Surface(
                        onClick = { onOpenRoute(health) },
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.padding(vertical = 6.dp),
                        ) {
                            Surface(
                                color = Color(health.status.colorArgb),
                                shape = CircleShape,
                                modifier = Modifier
                                    .padding(top = 5.dp)
                                    .size(10.dp),
                            ) {}
                            Column {
                                Text(
                                    listOfNotNull(health.assetName, health.registration)
                                        .joinToString(" · "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    subtitleOf(health),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                health.issues.forEach { issue ->
                                    Text(
                                        issue,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(health.status.colorArgb),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun subtitleOf(health: TrackerHealth): String {
    if (!health.hasTracker) return "auto bez lokalizatora"
    val parts = mutableListOf(health.status.label)
    health.silentMinutes?.let { parts += "ostatni odczyt ${formatMinutes(it)} temu" }
    parts += "${health.readings} odczytów · pokrycie doby ${health.coveragePercent}%"
    health.voltageV?.let { parts += "$it V" }
    return parts.joinToString(" · ")
}

private fun formatMinutes(minutes: Int): String {
    if (minutes < 60) return "$minutes min"
    val hours = minutes / 60
    if (hours < 24) return "$hours h"
    val days = hours / 24
    return "$days ${if (days == 1) "dnia" else "dni"}"
}
