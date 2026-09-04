package com.ekotak.teamtalk.presentation.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.presentation.components.PersonScope
import com.ekotak.teamtalk.presentation.components.PersonTree

/** Progi promienia „od lokalizacji" — ten sam zestaw co w panelu. */
private val RADII = listOf(0, 5, 10, 15, 30, 50, 75, 100)

/**
 * Filtry mapy w arkuszu. Pasek panelu (słowo · lokalizacja · promień w jednej
 * linii) nie mieści się na szerokość telefonu, więc słowo kluczowe zostaje na
 * ekranie, a reszta — opiekun/serwisant, rodzaj instalacji, lokalizacja
 * i promień — chowa się tutaj. Zakres filtrów jest ten sam co w panelu, plus
 * „moja lokalizacja" jako środek promienia.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MapFilterSheet(
    state: MapViewModel.UiState,
    onOwnerMode: (OwnerMode) -> Unit,
    onPerson: (PersonScope) -> Unit,
    onInstall: (String?) -> Unit,
    onLocationQuery: (String) -> Unit,
    onSelectPlace: (com.ekotak.teamtalk.domain.model.PlaceSuggestion) -> Unit,
    onMyLocation: () -> Unit,
    onClearLocation: () -> Unit,
    onRadius: (Int) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Filtry", style = MaterialTheme.typography.titleMedium)

            if (state.view.isClientView) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel("Opiekun")
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        OwnerMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = state.ownerMode == mode,
                                onClick = { onOwnerMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, OwnerMode.entries.size),
                            ) {
                                Text(mode.label, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel(if (state.view.isServiceView) "Serwisant" else "Osoba")
                // Drzewo działów zamiast płaskich chipów — ten sam komponent co
                // w Zadaniach i Kalendarzu. Liczniki przy nazwiskach i przy
                // działach liczą PUNKTY, nie ludzi. „Moje" tu nie ma: mapa nie
                // zna zalogowanego użytkownika, a filtr po sobie robi się
                // wybraniem siebie z listy.
                PersonTree(
                    members = state.people,
                    selected = state.person,
                    onSelect = onPerson,
                    mineLabel = null,
                    allLabel = allPeopleLabel(state),
                    counts = state.peopleCounts,
                )
            }

            if (state.installs.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel("Rodzaj instalacji")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = state.installFilter == null,
                            onClick = { onInstall(null) },
                            label = { Text("Wszystkie instalacje") },
                        )
                        state.installs.forEach { (name, count) ->
                            FilterChip(
                                selected = state.installFilter == name,
                                onClick = { onInstall(if (state.installFilter == name) null else name) },
                                label = { Text("$name ($count)") },
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("Lokalizacja")
                OutlinedTextField(
                    value = state.locationQuery,
                    onValueChange = onLocationQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Miejscowość…") },
                    trailingIcon = {
                        if (state.locationQuery.isNotEmpty() || state.center != null) {
                            IconButton(onClick = onClearLocation) {
                                Icon(Icons.Default.Clear, contentDescription = "Wyczyść lokalizację")
                            }
                        }
                    },
                )
                OutlinedButton(onClick = onMyLocation, modifier = Modifier.fillMaxWidth()) {
                    if (state.isLocating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.Default.MyLocation,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Text("Moja lokalizacja", modifier = Modifier.padding(start = 8.dp))
                }
                state.suggestions.forEach { place ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectPlace(place) }
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(10.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(place.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("Promień")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RADII.forEach { km ->
                        FilterChip(
                            selected = state.radiusKm == km,
                            onClick = { onRadius(km) },
                            // Promień bez środka nic nie filtruje — mówimy o tym
                            // wyłączeniem, zamiast po cichu nie robić nic.
                            enabled = km == 0 || state.center != null,
                            label = { Text(if (km == 0) "bez" else "+$km km") },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(onClick = onClearAll, modifier = Modifier.weight(1f)) {
                    Text("Wyczyść")
                }
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Pokaż ${state.baseCount}")
                }
            }
        }
    }
}

/** „Wszyscy opiekunowie (128)" / „Wszyscy serwisanci (23)" — jak w panelu. */
private fun allPeopleLabel(state: MapViewModel.UiState): String = when {
    state.view.isServiceView -> "Wszyscy serwisanci"
    state.ownerMode == OwnerMode.STAGE -> "Wszyscy opiekunowie etapowi"
    else -> "Wszyscy opiekunowie"
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
