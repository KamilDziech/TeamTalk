package com.ekotak.teamtalk.presentation.task

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.model.TaskMember
import com.ekotak.teamtalk.domain.model.TaskPriority
import com.ekotak.teamtalk.presentation.components.AppTopBar
import com.ekotak.teamtalk.presentation.theme.Green600
import com.ekotak.teamtalk.presentation.theme.Red600
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskScreen(
    onNavigateBack: () -> Unit,
    viewModel: CreateTaskViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }

    // Sukces: pokaż zielony banner na górze, po chwili wróć.
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            delay(1400)
            onNavigateBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = { AppTopBar(title = "Nowe zadanie", onNavigateBack = onNavigateBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Tytuł ──────────────────────────────────────────────────────────
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text("Tytuł zadania *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = state.error != null && state.title.isBlank(),
            )

            Spacer(Modifier.height(12.dp))

            // ── Opis ───────────────────────────────────────────────────────────
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("Opis") },
                placeholder = { Text("Szczegóły ustaleń z rozmowy") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 8,
            )

            Spacer(Modifier.height(16.dp))

            // ── Przypisany pracownik ────────────────────────────────────────────
            Text(
                "Przypisz do",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            AssigneeDropdown(
                members = state.members,
                selectedId = state.assigneeId,
                isLoading = state.isLoadingMembers,
                onSelect = viewModel::onAssigneeChange,
            )

            Spacer(Modifier.height(16.dp))

            // ── Priorytet ───────────────────────────────────────────────────────
            Text(
                "Priorytet",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PriorityChip("Niski", state.priority == TaskPriority.LOW) {
                    viewModel.onPriorityChange(TaskPriority.LOW)
                }
                PriorityChip("Normalny", state.priority == TaskPriority.NORMAL) {
                    viewModel.onPriorityChange(TaskPriority.NORMAL)
                }
                PriorityChip("Wysoki", state.priority == TaskPriority.HIGH) {
                    viewModel.onPriorityChange(TaskPriority.HIGH)
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Termin ──────────────────────────────────────────────────────────
            Text(
                "Termin",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(state.dueAtMillis?.let(::formatDate) ?: "Bez terminu")
            }

            Spacer(Modifier.height(24.dp))

            // ── Zapis ───────────────────────────────────────────────────────────
            Button(
                onClick = viewModel::createTask,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !state.isSaving && state.title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Green600),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Utwórz zadanie", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !state.isSaving,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Anuluj")
            }
        }
    }

        // ── Banner potwierdzenia (na górze ekranu) ──────────────────────────
        ResultBanner(
            visible = state.isSaved,
            success = true,
            message = "Zadanie zostało utworzone",
            modifier = Modifier.align(Alignment.TopCenter),
        )
        ResultBanner(
            visible = !state.isSaved && state.error != null,
            success = false,
            message = state.error ?: "Nie udało się utworzyć zadania",
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.dueAtMillis,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onDueAtChange(datePickerState.selectedDateMillis)
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.onDueAtChange(null)
                    showDatePicker = false
                }) { Text("Wyczyść") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/** Banner potwierdzenia zsuwający się od góry — zielony (sukces) / czerwony (błąd). */
@Composable
private fun ResultBanner(
    visible: Boolean,
    success: Boolean,
    message: String,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            color = if (success) Green600 else Red600,
            shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = message,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssigneeDropdown(
    members: List<TaskMember>,
    selectedId: String?,
    isLoading: Boolean,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = when {
        isLoading -> "Ładowanie…"
        selectedId == null -> "Bez przypisania"
        else -> members.firstOrNull { it.id == selectedId }?.displayName ?: "Bez przypisania"
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (!isLoading) expanded = it },
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Bez przypisania") },
                onClick = { onSelect(null); expanded = false },
            )
            members.forEach { member ->
                DropdownMenuItem(
                    text = { Text(member.displayName) },
                    onClick = { onSelect(member.id); expanded = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriorityChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

private fun formatDate(millis: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale("pl")).format(Date(millis))
