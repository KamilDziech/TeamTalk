package com.ekotak.teamtalk.presentation.installations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.model.MaterialStatus
import com.ekotak.teamtalk.domain.model.MontazMaterial
import com.ekotak.teamtalk.domain.model.packKey
import com.ekotak.teamtalk.domain.montaz.MontazTool
import com.ekotak.teamtalk.presentation.components.AppTopBar
import com.ekotak.teamtalk.presentation.crm.formatQty
import com.ekotak.teamtalk.presentation.theme.OkGreen
import com.ekotak.teamtalk.presentation.theme.Orange600
import com.ekotak.teamtalk.presentation.theme.Red600
import com.ekotak.teamtalk.presentation.theme.SyncBlue

/**
 * PAKOWANIE W MAGAZYNIE — dwie listy, dwa różne ptaszki.
 *
 * Materiał jest rezerwacją magazynową: ptaszek mówi „leży na aucie", a osobne
 * WYDANIE zdejmuje towar ze stanu i podpisuje się pod nim imieniem. Narzędzia
 * stanu nie mają (wracają wieczorem), więc ptaszek jest tam listą kontrolną
 * przy klapie bagażnika — i zostaje w telefonie.
 */
@Composable
fun JobPackingScreen(
    onNavigateBack: () -> Unit,
    viewModel: JobViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = "Pakowanie", onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            TabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("Materiał ${state.materialsPacked}/${state.materials.size}") },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("Narzędzia ${state.toolsPacked}/${state.tools.size}") },
                )
            }
            Spacer(Modifier.height(8.dp))

            if (tab == 0) {
                MaterialList(state = state, viewModel = viewModel, modifier = Modifier.weight(1f))
                if (state.toIssue.isNotEmpty()) {
                    Button(
                        onClick = viewModel::issuePacked,
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    ) { Text("Wydaj spakowane (${state.toIssue.size} poz.)") }
                }
            } else {
                ToolList(state = state, viewModel = viewModel, modifier = Modifier.weight(1f))
                if (state.toolsRequiredLeft > 0) {
                    Text(
                        text = "Zostało ${state.toolsRequiredLeft} wymaganych pozycji. " +
                            "Braku nie blokujemy — ale ekipa ma o nim wiedzieć.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Orange600,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }

            // Zgłoszenie braku stoi pod OBIEMA listami: koordynator dostaje jedno
            // zadanie na cały wyjazd — sprzęt i materiał razem — zamiast telefonu
            // o 6:40 i dwóch osobnych zgłoszeń.
            if (state.shortageCount > 0 || state.shortageReported) {
                OutlinedButton(
                    onClick = viewModel::reportShortages,
                    enabled = !state.isSaving && !state.shortageReported,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Text(
                        if (state.shortageReported) {
                            "Brak zgłoszony koordynatorowi"
                        } else {
                            "Zgłoś brak koordynatorowi (${state.shortageCount})"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MaterialList(
    state: JobViewModel.UiState,
    viewModel: JobViewModel,
    modifier: Modifier = Modifier,
) {
    if (state.materialsLoaded && state.materials.isEmpty()) {
        Text(
            text = "Magazyn nie ma nic odłożonego pod ten montaż. Rezerwacja powstaje sama " +
                "przy podpisaniu umowy — jeśli jej nie ma, zadzwoń do biura.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(top = 12.dp),
        )
        return
    }

    LazyColumn(modifier = modifier) {
        if (state.shortages.isNotEmpty()) {
            item {
                Text(
                    text = "Brakuje na stanie: " +
                        state.shortages.joinToString(", ") {
                            "${it.itemName} (−${formatQty(it.missing)} ${it.unit})"
                        } + ". Wydać można mimo braku.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Red600,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
        items(state.materials.size, key = { state.materials[it].id }) { index ->
            val material = state.materials[index]
            PackRow(
                title = material.itemName,
                subtitle = buildString {
                    append(formatQty(material.quantity))
                    append(" ")
                    append(material.unit)
                    if (material.missing > 0 && material.status == MaterialStatus.ACTIVE) {
                        append(" · brakuje ${formatQty(material.missing)}")
                    }
                    material.note?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                },
                subtitleColor = if (material.missing > 0) Red600 else null,
                checked = material.packKey in state.packed,
                required = false,
                flag = material.flagLabel(),
                flagColor = material.flagColor(),
                onToggle = { viewModel.togglePacked(material.packKey) },
            )
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

private fun MontazMaterial.flagLabel(): String? = when {
    pending -> "wydanie czeka"
    status == MaterialStatus.DONE -> "wydane"
    else -> null
}

private fun MontazMaterial.flagColor() = when {
    pending -> SyncBlue
    status == MaterialStatus.DONE -> OkGreen
    else -> null
}

@Composable
private fun ToolList(
    state: JobViewModel.UiState,
    viewModel: JobViewModel,
    modifier: Modifier = Modifier,
) {
    val groups = state.job?.toolGroups.orEmpty()
    if (groups.isEmpty()) {
        Text(
            text = "Katalog nie ma listy sprzętu dla tego zakresu — uzupełnia się ją w karcie " +
                "węzła Technologii, zakładka „🧰 Narzędzia”.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(top = 12.dp),
        )
        return
    }

    LazyColumn(modifier = modifier) {
        state.job?.toolNotes?.forEach { note ->
            item(key = "note-$note") {
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = Orange600,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
        groups.forEach { group ->
            item(key = "g-${group.group}") {
                Text(
                    text = group.group.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                )
            }
            items(group.items.size, key = { "t-${group.group}-${group.items[it].key}" }) { index ->
                val tool: MontazTool = group.items[index]
                PackRow(
                    title = tool.name,
                    subtitle = listOfNotNull(
                        tool.qty?.let { "${formatQty(it)} ${tool.unit}" },
                        tool.owner.takeIf { it.isNotBlank() },
                        if (tool.beacon) "◉ beacon" else null,
                        if (tool.required) null else "przydatne",
                        tool.note.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    subtitleColor = null,
                    checked = tool.key in state.packed,
                    required = tool.required,
                    flag = null,
                    flagColor = null,
                    onToggle = { viewModel.togglePacked(tool.key) },
                )
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

/** Wiersz z ptaszkiem — wspólny dla materiału i sprzętu. */
@Composable
private fun PackRow(
    title: String,
    subtitle: String,
    subtitleColor: androidx.compose.ui.graphics.Color?,
    checked: Boolean,
    required: Boolean,
    flag: String?,
    flagColor: androidx.compose.ui.graphics.Color?,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = OkGreen),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (required) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (flag != null) {
            Text(
                text = flag,
                style = MaterialTheme.typography.labelSmall,
                color = flagColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
