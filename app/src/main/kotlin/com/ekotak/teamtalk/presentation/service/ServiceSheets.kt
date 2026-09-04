package com.ekotak.teamtalk.presentation.service

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.WarrantyCardStatus

/**
 * Arkusze modułu Serwis: filtry (odpowiednik paska filtrów panelu) i wybór,
 * co dodajemy w dziedzinie Przegląd — panel ma tam dwa przyciski obok siebie
 * („+ Nowy przegląd" i „+ Karta gwarancyjna"), a na telefonie jest jeden FAB.
 */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ServiceFilterSheet(
    state: ServiceViewModel.UiState,
    onDismiss: () -> Unit,
    onToggleRegular: () -> Unit,
    onToggleWarranty: () -> Unit,
    onWarrantyStatus: (WarrantyCardStatus?) -> Unit,
    onClientPin: (String?) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = sheetBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Filtry", style = MaterialTheme.typography.titleMedium)

            if (state.isPrzeglad) {
                SectionLabel("Źródło przeglądów")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.showRegular,
                        onClick = onToggleRegular,
                        label = { Text("Zwykłe") },
                    )
                    FilterChip(
                        selected = state.showWarranty,
                        onClick = onToggleWarranty,
                        label = { Text("Gwarancyjne") },
                    )
                }

                SectionLabel("Status karty gwarancyjnej")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.warrantyStatus == null,
                        onClick = { onWarrantyStatus(null) },
                        label = { Text("Wszystkie") },
                    )
                    WarrantyCardStatus.entries.forEach { status ->
                        FilterChip(
                            selected = state.warrantyStatus == status,
                            onClick = { onWarrantyStatus(status) },
                            // Wyłączony filtr źródła zabiera sens filtrowaniu kart —
                            // panel blokuje tę listę dokładnie tak samo.
                            enabled = state.showWarranty,
                            label = { Text(status.label) },
                        )
                    }
                }
            } else {
                SectionLabel("Klient")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.clientPin == null,
                        onClick = { onClientPin(null) },
                        label = { Text("Wszyscy") },
                    )
                    state.clientGroups.forEach { group ->
                        FilterChip(
                            selected = state.clientPin == group.id,
                            onClick = { onClientPin(group.id) },
                            label = {
                                Text(
                                    text = "${group.label} ${group.count}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddChoiceSheet(
    addLabel: String,
    onDismiss: () -> Unit,
    onJob: () -> Unit,
    onCard: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = sheetBottomPadding()),
        ) {
            ListItem(
                headlineContent = { Text(addLabel) },
                supportingContent = { Text("Zlecenie z terminem i serwisantem") },
                leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().clickable(onClick = onJob),
            )
            ListItem(
                headlineContent = { Text("Karta gwarancyjna") },
                supportingContent = { Text("Urządzenie Panasonic z harmonogramem 5 przeglądów") },
                leadingContent = { Icon(Icons.Filled.VerifiedUser, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().clickable(onClick = onCard),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}
