package com.ekotak.teamtalk.presentation.client

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.model.DuplicateGroup
import com.ekotak.teamtalk.presentation.components.AppTopBar
import com.ekotak.teamtalk.presentation.theme.ButtonShape

/**
 * Scalanie duplikatów — mobilny odpowiednik okna „Scal ten sam kontakt".
 * Wybór rekordu docelowego jest jawny (radio), a scalenie potwierdzamy
 * dialogiem: operacji nie da się cofnąć, więc jedno przypadkowe tapnięcie
 * nie może przenieść cudzych deali.
 */
@Composable
fun ClientMergeScreen(
    onNavigateBack: () -> Unit,
    viewModel: ClientMergeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmGroup by remember { mutableStateOf<DuplicateGroup?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Scal duplikaty" + if (state.groups.isNotEmpty()) " (${state.groups.size})" else "",
                onNavigateBack = onNavigateBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "hint") {
                Text(
                    text = "Wykryte duplikaty: te same imię i nazwisko, telefon lub e-mail. " +
                        "Zaznacz rekord, który zostanie zachowany — pozostałe zostaną w niego " +
                        "scalone (deale, zlecenia i wiadomości przejdą do zachowanego). " +
                        "Operacji nie można cofnąć.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.groups.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "Nie znaleziono duplikatów w kartotece.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            items(state.groups, key = { it.clients.first().client.id }) { group ->
                MergeGroupCard(
                    group = group,
                    targetId = state.targetByGroup[group.clients.first().client.id],
                    isMerging = state.isMerging,
                    onSelect = { id -> viewModel.selectTarget(group, id) },
                    onMerge = { confirmGroup = group },
                )
            }
        }
    }

    confirmGroup?.let { group ->
        val targetId = state.targetByGroup[group.clients.first().client.id]
        val targetName = group.clients.firstOrNull { it.client.id == targetId }?.client?.displayName
        AlertDialog(
            onDismissRequest = { confirmGroup = null },
            title = { Text("Scalić kontakty?") },
            text = {
                Text(
                    "${group.clients.size} rekordy zostaną scalone w „${targetName.orEmpty()}”. " +
                        "Tej operacji nie można cofnąć.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmGroup = null
                        viewModel.merge(group)
                    },
                ) { Text("Scal") }
            },
            dismissButton = {
                TextButton(onClick = { confirmGroup = null }) { Text("Anuluj") }
            },
        )
    }
}

@Composable
private fun MergeGroupCard(
    group: DuplicateGroup,
    targetId: String?,
    isMerging: Boolean,
    onSelect: (String) -> Unit,
    onMerge: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            group.clients.forEach { entry ->
                val client = entry.client
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = targetId == client.id,
                            onClick = { onSelect(client.id) },
                        )
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = targetId == client.id,
                        onClick = { onSelect(client.id) },
                    )
                    Spacer(Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = client.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = listOfNotNull(client.phone, client.email)
                                .joinToString(" · ")
                                .ifBlank { "—" } + " · deale: ${entry.deals.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onMerge,
                enabled = !isMerging && targetId != null,
                shape = ButtonShape,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isMerging) "Scalam…" else "Scal ${group.clients.size} kontakty")
            }
        }
    }
}
