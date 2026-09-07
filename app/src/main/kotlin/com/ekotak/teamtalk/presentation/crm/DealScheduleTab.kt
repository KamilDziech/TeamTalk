package com.ekotak.teamtalk.presentation.crm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.Project
import com.ekotak.teamtalk.presentation.theme.SyncBlue

/**
 * Zakładka „Harmonogram" karty deala — mobilny `DealProjectPanel` (w panelu
 * chodzi pod kluczem `projekt`). Zakres 1:1 z web: projekty rozwojowe przypięte
 * do tego deala, ich postęp i pole zakładania nowego. Kamienie milowe, Gantt
 * i planowanie zadań zostają w module Projekty — tu jest wejście do nich
 * z karty klienta, tak samo jak w panelu.
 *
 * Lista czyta się z cache Room, więc otwiera się bez zasięgu, a projekt
 * założony w terenie widać od razu, z podpisem, że czeka na wysyłkę.
 */
@Composable
fun DealScheduleTab(
    state: DealDetailViewModel.UiState,
    onOpenProject: (String) -> Unit,
    viewModel: DealDetailViewModel,
) {
    val schedule = state.schedule

    SectionCard {
        SectionTitle(
            text = "Harmonogram",
            accent = schedule.projects.size.takeIf { it > 0 }?.toString(),
        )
        SectionGap()
        Text(
            text = "Projekt rozwojowy powiązany z tym dealem (np. montaż, wdrożenie). " +
                "Zadania planujesz w module Projekty; po aktywacji trafiają do „Zadań”.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (schedule.pendingCount > 0 || schedule.offline || schedule.error != null) {
            SectionGap()
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (schedule.pendingCount > 0) {
                    StatusLine(
                        icon = Icons.Default.Schedule,
                        text = pendingProjectsLine(schedule.pendingCount),
                        color = SyncBlue,
                    )
                }
                if (schedule.offline) {
                    StatusLine(
                        icon = Icons.Default.CloudOff,
                        text = "Lista z telefonu — bez zasięgu nie widać projektów " +
                            "założonych w międzyczasie w panelu.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                schedule.error?.let { error ->
                    StatusLine(
                        icon = Icons.Default.CloudOff,
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = { viewModel.loadSchedule(force = true) }) {
                        Text("Spróbuj ponownie")
                    }
                }
            }
        }
    }
    SectionGap()

    when {
        schedule.isLoading && !schedule.loaded -> Box(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        schedule.projects.isEmpty() -> SectionCard {
            Text(
                text = "Brak powiązanego projektu.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            schedule.projects.forEach { project ->
                ProjectRow(
                    project = project,
                    onClick = {
                        // Projekt z kolejki nie ma jeszcze karty na serwerze —
                        // wejście w niego pokazałoby pusty ekran, więc zamiast
                        // tego mówimy, na co czeka.
                        if (project.localOnly) {
                            viewModel.showMessage(
                                "Projekt czeka na wysyłkę — karta otworzy się, gdy wróci zasięg.",
                            )
                        } else {
                            onOpenProject(project.id)
                        }
                    },
                )
            }
        }
    }

    SectionGap()
    NewProjectCard(state = state, viewModel = viewModel)
}

/**
 * Kafel projektu w kształcie znanym z modułu Projekty (pasek koloru, plakietka
 * etapu, postęp) — ten sam projekt ma wyglądać tak samo niezależnie od tego,
 * czy wchodzi się w niego z karty deala, czy z pulpitu.
 */
@Composable
private fun ProjectRow(project: Project, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column {
            // Pasek koloru zamiast okładki: grafik z MinIO nie ciągniemy na
            // telefon, a kolor i tak niesie rozpoznanie projektu.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(projectColor(project.color)),
            )
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    when {
                        project.localOnly -> Pill("czeka na wysyłkę", SyncBlue)
                        // Odpowiednik „· archiwum" panelu. Zarchiwizowany projekt
                        // zostaje na liście deala — jego historia jest częścią
                        // historii deala.
                        project.archived -> Pill(
                            text = "archiwum",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        else -> Pill(
                            text = project.stage.label,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                if (project.taskCount > 0) {
                    LinearProgressIndicator(
                        progress = { project.progressPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                    )
                }
                Text(
                    text = if (project.taskCount > 0) {
                        "${project.doneCount}/${project.taskCount} zadań · ${project.progressPercent}%"
                    } else {
                        "bez zadań"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Pole zakładania projektu. Board360 przyjmuje projekt zakładany wprost tylko
 * z `projects.manage` (bez niego wolno zgłosić wyłącznie pomysł do Poczekalni),
 * więc zamiast pola kończącego się odmową serwera pokazujemy wyjaśnienie.
 */
@Composable
private fun NewProjectCard(
    state: DealDetailViewModel.UiState,
    viewModel: DealDetailViewModel,
) {
    val schedule = state.schedule

    SectionCard {
        SectionTitle("Nowy projekt")
        SectionGap()

        if (!state.canManageProjects) {
            Text(
                text = "Zakładanie projektu wymaga uprawnienia do modułu Projekty — " +
                    "poproś koordynatora albo załóż projekt w panelu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        OutlinedTextField(
            value = schedule.newName,
            onValueChange = viewModel::onNewProjectNameChange,
            label = { Text("Nazwa nowego projektu…") },
            singleLine = true,
            enabled = !schedule.busy,
            modifier = Modifier.fillMaxWidth(),
        )
        SectionGap()
        Button(
            onClick = viewModel::createDealProject,
            enabled = !schedule.busy && schedule.newName.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Projekt")
        }
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun StatusLine(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

/** Kolor okładki projektu — ten sam fallback co w module Projekty. */
private fun projectColor(raw: String?): Color {
    val hex = raw?.removePrefix("#")?.takeIf { it.length == 6 } ?: "44d62c"
    return runCatching { Color(("ff$hex").toLong(16)) }.getOrDefault(Color(0xFF44D62C))
}

/** Odmiana przez przypadki razem z orzeczeniem — inaczej wychodzi „2 projekty czeka". */
private fun pendingProjectsLine(count: Int): String = when {
    count == 1 -> "1 projekt czeka na wysyłkę — poleci sam, gdy wróci zasięg."
    count % 10 in 2..4 && count % 100 !in 12..14 ->
        "$count projekty czekają na wysyłkę — polecą same, gdy wróci zasięg."

    else -> "$count projektów czeka na wysyłkę — polecą same, gdy wróci zasięg."
}
