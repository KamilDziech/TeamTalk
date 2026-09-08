package com.ekotak.teamtalk.presentation.task

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.Task
import com.ekotak.teamtalk.domain.model.TaskMember
import com.ekotak.teamtalk.domain.model.TaskPriority
import com.ekotak.teamtalk.domain.model.TaskSource
import com.ekotak.teamtalk.domain.model.TaskStatus
import com.ekotak.teamtalk.presentation.theme.EkotakGreen
import com.ekotak.teamtalk.presentation.theme.Orange600
import com.ekotak.teamtalk.presentation.theme.Red600
import com.ekotak.teamtalk.presentation.theme.SyncBlue

/*
 * Wiersz zadania i jego części — wspólne dla modułu „Zadania" i dla zakładki
 * „Zadania" karty deala. W panelu to jedna tablica w dwóch trybach, więc na
 * telefonie zadanie też musi wyglądać tak samo w obu miejscach: inaczej ta sama
 * rzecz uczyłaby się dwa razy, a poprawka wchodziła tylko w jedno z nich.
 */

/** Nagłówek sekcji (etapu lejka) z licznikiem zadań pod nim. */
@Composable
fun TaskSectionHeader(
    label: String,
    count: Int,
    modifier: Modifier = Modifier,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

/**
 * Wiersz zadania. Kółko po lewej zamyka i otwiera zadanie jednym dotknięciem —
 * to najczęstsza czynność w terenie i dlatego ma największy cel dotykowy.
 * Dotknięcie reszty wiersza wchodzi w kartę zadania (opis, dyskusja).
 *
 * [dragging] podnosi wiersz na czas przenoszenia (zakładka karty deala pozwala
 * układać kolejność po długim przytrzymaniu), a [modifier] wnosi wtedy zarówno
 * przesunięcie, jak i sam gest — trzyma je ekran, bo tylko on wie, co z czym
 * sąsiaduje.
 */
@Composable
fun TaskRow(
    task: Task,
    assignee: TaskMember?,
    pending: Boolean,
    queued: Boolean,
    onToggleDone: () -> Unit,
    onTogglePriority: () -> Unit,
    onOpen: () -> Unit = {},
    showSource: Boolean = true,
    dragging: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val done = task.status == TaskStatus.DONE
    val high = task.priority == TaskPriority.HIGH

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (dragging) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (dragging) 2.dp else 1.dp,
            color = if (dragging) EkotakGreen else MaterialTheme.colorScheme.outline,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DoneToggle(done = done, pending = pending, onClick = onToggleDone)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpen)
                    .padding(start = 4.dp),
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (done) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                TaskMeta(task = task, done = done, queued = queued, showSource = showSource)
            }

            if (assignee != null) {
                Spacer(Modifier.width(6.dp))
                TaskAvatar(initials = assignee.initials)
            }

            IconButton(onClick = onTogglePriority, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (high) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (high) "Zdejmij priorytet" else "Wysoki priorytet",
                    tint = if (high) Orange600 else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun DoneToggle(done: Boolean, pending: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(40.dp).clickable(enabled = !pending, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            pending -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            done -> Box(
                modifier = Modifier.size(22.dp).background(EkotakGreen, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Oznacz jako aktywne",
                    tint = Color.Black,
                    modifier = Modifier.size(15.dp),
                )
            }
            else -> Box(
                modifier = Modifier
                    .size(22.dp)
                    .border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
            ) {
                // Puste kółko — opis dla czytnika ekranu niesie sam przycisk.
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Oznacz jako wykonane",
                    tint = Color.Transparent,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

/**
 * Druga linia wiersza: źródło, termin, SLA i znacznik komentarzy.
 *
 * `FlowRow`, a nie `Row`: przy nazwie klienta, zaległym terminie i SLA naraz
 * zwykły wiersz ściskał ostatni znacznik do pionowej kolumny liter („SL/A/−2/
 * dni"). Tutaj nadmiar przenosi się do drugiej linii.
 *
 * [showSource] gasi znacznik klienta w zakładce karty deala — tam wszystkie
 * zadania należą do jednego klienta i powtarzanie jego nazwiska w każdym
 * wierszu zabierałoby miejsce terminowi.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskMeta(
    task: Task,
    done: Boolean,
    queued: Boolean = false,
    showSource: Boolean = true,
) {
    val due = dueLabel(task.dueAt)
    val overdue = !done && isOverdue(task.dueAt)
    val sla = slaState(task.createdAt, task.slaHours, done)

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        task.source?.takeIf { showSource }?.let { source ->
            MetaItem(
                icon = when (source) {
                    is TaskSource.Deal -> Icons.Default.Person
                    is TaskSource.Project -> Icons.Default.Folder
                },
                text = source.label ?: when (source) {
                    is TaskSource.Deal -> "Klient"
                    is TaskSource.Project -> "Projekt"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (due != null) {
            Text(
                text = due,
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    done -> MaterialTheme.colorScheme.onSurfaceVariant
                    overdue -> Red600
                    isDueToday(task.dueAt) -> Orange600
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        if (sla != null) {
            Text(
                text = sla.text,
                style = MaterialTheme.typography.labelSmall,
                color = when (sla.level) {
                    SlaLevel.OVER -> Red600
                    SlaLevel.WARN -> Orange600
                    SlaLevel.OK -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        if (task.commentCount > 0) {
            MetaItem(
                icon = Icons.Default.ChatBubbleOutline,
                text = "${task.commentCount}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Zmiana zrobiona bez zasięgu. Wiersz pokazuje ją tak, jakby weszła —
        // bo z punktu widzenia człowieka weszła — ale znacznik mówi wprost, że
        // serwer jeszcze o niej nie wie.
        if (queued) {
            MetaItem(
                icon = Icons.Default.CloudUpload,
                text = "czeka na wysyłkę",
                color = SyncBlue,
            )
        }
    }
}

/**
 * Ikona ze swoim podpisem. Para siedzi we własnym wierszu z ciaśniejszym
 * odstępem, bo `spacedBy` wiersza nadrzędnego rozdziela grupy, a nie ikonę od
 * jej tekstu. Wcześniej ściągałem podpis ujemnym paddingiem — Compose odrzuca
 * ujemne wartości wyjątkiem i lista zadań wywalała aplikację przy każdym
 * wierszu ze źródłem albo komentarzem.
 */
@Composable
private fun MetaItem(icon: ImageVector, text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TaskAvatar(initials: String) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
