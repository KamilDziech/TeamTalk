package com.ekotak.teamtalk.presentation.crm

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ekotak.teamtalk.domain.model.Task
import com.ekotak.teamtalk.domain.model.TaskSection
import com.ekotak.teamtalk.domain.model.TaskStatus
import com.ekotak.teamtalk.presentation.task.TaskRow
import com.ekotak.teamtalk.presentation.task.TaskSectionHeader
import com.ekotak.teamtalk.presentation.theme.SyncBlue
import kotlin.math.roundToInt

/**
 * Zakładka „Zadania" karty deala — mobilny odpowiednik `TasksBoard` w trybie
 * `sections`. Zadania należą wyłącznie do tego deala, a lewa kolumna panelu
 * (sekcje = etapy lejka) jest u nas nagłówkami w jednej liście.
 *
 * Wiersz, odhaczenie i znaczniki są dokładnie te same co w module „Zadania"
 * (`TaskRow`) — panel ma tam jedną tablicę w dwóch trybach i telefon też ma
 * jedno zadanie wyglądające wszędzie tak samo. Karta zadania (opis, komentarze,
 * załączniki) otwiera się ekranem modułu, więc niczego nie dublujemy.
 *
 * Ustalenia 2026-09-08:
 *  - widać WSZYSTKIE sekcje, także puste — nagłówki niosą cały proces i każda
 *    ma „+", którym zakłada się zadanie od razu w niej;
 *  - wejście pokazuje wszystkie zadania deala (nie tylko własne), bo w karcie
 *    klienta liczy się to, co się na nim dzieje;
 *  - pasek filtrów jest krótki: „Moje", „Wykonane" i szukajka;
 *  - kolejność układa się przeciąganiem po długim przytrzymaniu, w obrębie
 *    sekcji, i ląduje w tej samej preferencji co kolejność z panelu.
 */
@Composable
fun DealTasksTab(
    state: DealDetailViewModel.UiState,
    onOpenTask: (String) -> Unit,
    onCreateTask: (TaskSection?) -> Unit,
    viewModel: DealDetailViewModel,
) {
    val tasks = state.tasks

    SectionCard {
        SectionTitle(
            text = "Zadania",
            accent = tasks.totalCount.takeIf { it > 0 }?.toString(),
        )
        SectionGap()
        Text(
            text = "Zadania tego klienta, pogrupowane etapami lejka. Sekcję nowego " +
                "zadania podpowiada etap deala; zmienia się ją w karcie zadania.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (tasks.offline || tasks.error != null) {
            SectionGap()
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (tasks.offline) {
                    TaskStatusLine(
                        icon = Icons.Default.CloudOff,
                        text = "Brak zasięgu — lista z pamięci telefonu.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                tasks.error?.let { error ->
                    TaskStatusLine(
                        icon = Icons.Default.CloudOff,
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = { viewModel.loadTasks(force = true) }) {
                        Text("Spróbuj ponownie")
                    }
                }
            }
        }

        val queuedHere = tasks.sections.sumOf { group ->
            group.items.count { it.id in tasks.queuedIds }
        }
        if (queuedHere > 0) {
            SectionGap()
            TaskStatusLine(
                icon = Icons.Default.Schedule,
                text = pendingTasksLine(queuedHere),
                color = SyncBlue,
            )
        }

        SectionGap()
        OutlinedTextField(
            value = tasks.query,
            onValueChange = viewModel::onTaskQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Szukaj: zadanie, opis, osoba…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
        )

        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = tasks.mineOnly,
                onClick = viewModel::onTaskMineOnlyToggle,
                label = { Text("Moje") },
            )
            FilterChip(
                selected = tasks.showDone,
                onClick = viewModel::onTaskShowDoneToggle,
                label = { Text("Wykonane") },
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${tasks.visibleCount} z ${tasks.totalCount}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (tasks.isLoading && !tasks.loaded) {
            SectionGap()
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
    }

    if (tasks.loaded && tasks.totalCount == 0) {
        SectionGap()
        SectionCard {
            EmptyDealTasks(canManage = state.canManageTasks, onCreate = { onCreateTask(null) })
        }
        return
    }

    // Wszystko odfiltrowane: dziewięć pustych nagłówków niczego by nie powiedziało,
    // a sugerowałoby, że deal nie ma zadań. Mówimy wprost, co je schowało.
    if (tasks.loaded && tasks.visibleCount == 0) {
        SectionGap()
        SectionCard {
            Text(
                text = if (tasks.query.isNotBlank()) {
                    "Brak zadań dla hasła „${tasks.query}”."
                } else {
                    "Brak zadań pasujących do filtrów."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    tasks.sections.forEach { group ->
        SectionGap()
        TaskSectionGroup(
            group = group,
            state = state,
            onOpenTask = onOpenTask,
            onCreateTask = onCreateTask,
            viewModel = viewModel,
        )
    }
}

/**
 * Jedna sekcja: nagłówek z licznikiem i „+", pod nim wiersze. Przeciąganie po
 * długim przytrzymaniu przestawia zadania w obrębie TEJ sekcji — sekcja to etap
 * lejka, więc wyrzucenie zadania do sąsiedniej znaczyłoby zmianę etapu, a nie
 * kolejności. Wykonanych nie przestawiamy: stoją na dole, jak w panelu.
 */
@Composable
private fun TaskSectionGroup(
    group: DealDetailViewModel.TaskGroup,
    state: DealDetailViewModel.UiState,
    onOpenTask: (String) -> Unit,
    onCreateTask: (TaskSection?) -> Unit,
    viewModel: DealDetailViewModel,
) {
    val tasks = state.tasks
    val spacing = 8.dp
    val spacingPx = with(LocalDensity.current) { spacing.toPx() }

    // Lista w wersji „na teraz": gest trwa dłużej niż jedna rekompozycja, więc
    // czyta ją przez `rememberUpdatedState` zamiast trzymać kopię sprzed ruchu.
    val items by rememberUpdatedState(group.items)
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableIntStateOf(0) }

    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
        TaskSectionHeader(label = group.label, count = group.items.size) {
            if (state.canManageTasks) {
                IconButton(
                    onClick = { onCreateTask(group.section) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Nowe zadanie w sekcji ${group.label}",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        if (group.items.isEmpty()) {
            Text(
                text = "Brak zadań w tej sekcji.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        group.items.forEach { task ->
            key(task.id) {
                val dragging = dragId == task.id
                val canDrag = state.canManageTasks && !task.isDone
                TaskRow(
                    task = task,
                    assignee = task.assigneeId?.let { id -> state.members.firstOrNull { it.id == id } },
                    pending = task.id in tasks.busyIds,
                    queued = task.id in tasks.queuedIds,
                    onToggleDone = { viewModel.onTaskToggleDone(task) },
                    onTogglePriority = { viewModel.onTaskTogglePriority(task) },
                    onOpen = { onOpenTask(task.id) },
                    // W zakładce deala wszystkie zadania są tego samego klienta,
                    // więc nazwisko w każdym wierszu tylko zabierałoby miejsce.
                    showSource = false,
                    dragging = dragging,
                    modifier = Modifier
                        .onSizeChanged { rowHeight = it.height }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                        .then(
                            if (!canDrag) {
                                Modifier
                            } else {
                                Modifier.pointerInput(task.id, group.section) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            dragId = task.id
                                            dragOffset = 0f
                                        },
                                        onDragEnd = {
                                            dragId = null
                                            dragOffset = 0f
                                            viewModel.commitTaskOrder()
                                        },
                                        onDragCancel = {
                                            dragId = null
                                            dragOffset = 0f
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount.y
                                            val step = rowHeight + spacingPx
                                            if (step <= 0f) return@detectDragGesturesAfterLongPress
                                            val from = items.indexOfFirst { it.id == task.id }
                                            if (from < 0) return@detectDragGesturesAfterLongPress
                                            val shift = (dragOffset / step).roundToInt()
                                            if (shift == 0) return@detectDragGesturesAfterLongPress
                                            // Wykonane stoją na dole i nie biorą
                                            // udziału w układaniu — stąd granica.
                                            val last = items.indexOfLast { !it.isDone }
                                            val to = (from + shift).coerceIn(0, maxOf(last, 0))
                                            if (to == from) return@detectDragGesturesAfterLongPress
                                            viewModel.onTaskMove(group.section, from, to)
                                            // Wiersz przeskoczył o tyle, ile
                                                // przesunął się palec — resztę
                                            // przesunięcia niesiemy dalej.
                                            dragOffset -= (to - from) * step
                                        },
                                    )
                                }
                            },
                        ),
                )
            }
        }
    }
}

/** Pusta zakładka: nic nie odfiltrowaliśmy, po prostu deal nie ma jeszcze zadań. */
@Composable
private fun EmptyDealTasks(canManage: Boolean, onCreate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Assignment,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Ten deal nie ma jeszcze zadań.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        if (canManage) {
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onCreate) { Text("Nowe zadanie") }
        }
    }
}

@Composable
private fun TaskStatusLine(
    icon: ImageVector,
    text: String,
    color: Color,
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

/** Odmiana przez przypadki razem z orzeczeniem — inaczej wychodzi „2 zadania czeka". */
private fun pendingTasksLine(count: Int): String = when {
    count == 1 -> "1 zadanie czeka na wysyłkę — poleci samo, gdy wróci zasięg."
    count % 10 in 2..4 && count % 100 !in 12..14 ->
        "$count zadania czekają na wysyłkę — polecą same, gdy wróci zasięg."

    else -> "$count zadań czeka na wysyłkę — polecą same, gdy wróci zasięg."
}

/** Skrót czytelniejszy niż `status == TaskStatus.DONE` w środku gestu. */
private val Task.isDone: Boolean
    get() = status == TaskStatus.DONE
