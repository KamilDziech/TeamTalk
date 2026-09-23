package com.ekotak.teamtalk.presentation.installations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.model.ProtocolAnswer
import com.ekotak.teamtalk.domain.model.ProtocolKind
import com.ekotak.teamtalk.domain.model.ProtocolPhoto
import com.ekotak.teamtalk.domain.model.ProtocolQuestion
import com.ekotak.teamtalk.domain.model.answered
import com.ekotak.teamtalk.domain.model.groups
import com.ekotak.teamtalk.domain.model.photosOk
import com.ekotak.teamtalk.presentation.components.AppTopBar
import com.ekotak.teamtalk.presentation.crm.SectionCard
import com.ekotak.teamtalk.presentation.crm.SectionGap
import com.ekotak.teamtalk.presentation.crm.SectionTitle
import com.ekotak.teamtalk.presentation.crm.rememberFilePickers
import com.ekotak.teamtalk.presentation.theme.OkGreen
import com.ekotak.teamtalk.presentation.theme.Red600
import com.ekotak.teamtalk.presentation.theme.SyncBlue

/**
 * PROTOKÓŁ ODBIORU — pytania z ZAKRESU zlecenia, nie z jednego szablonu.
 *
 * Pytania przychodzą z węzłów katalogu objętych montażem (zakładka
 * „📋 Protokół"), więc podłogówka pyta o próbę ciśnieniową, a pompa ciepła
 * o tabliczkę znamionową i rozruch. Czerwona ramka i podpis pod pytaniem mówią
 * wprost, czego brakuje — zamiast komunikatu „uzupełnij formularz" na końcu,
 * kiedy klient już stoi z długopisem.
 */
@Composable
fun JobProtocolScreen(
    onNavigateBack: () -> Unit,
    viewModel: JobViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Aparat oddaje kadr do PYTANIA, przy którym ekipa go zrobiła — dlatego
    // wybierak pamięta ostatnio dotknięte pytanie.
    val pendingQuestion = remember { arrayOfNulls<String>(1) }
    val pickers = rememberFilePickers { files ->
        val questionId = pendingQuestion[0] ?: return@rememberFilePickers
        files.forEach { viewModel.addPhoto(questionId, it.bytes, it.name) }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = "Protokół", onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLoading) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        val protocol = state.protocol
        val (done, total) = state.protocolProgress
        val readOnly = protocol.closed

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SectionCard {
                    SectionTitle(
                        text = "Protokół odbioru",
                        accent = if (readOnly) "zamknięty" else "$done / $total",
                    )
                    SectionGap()
                    Text(
                        text = listOfNotNull(
                            protocol.client.takeIf { it.isNotBlank() },
                            protocol.scope.joinToString(" · ").takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (protocol.pending) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Zapisany w telefonie — wyślemy, gdy wróci zasięg.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SyncBlue,
                        )
                    }
                }
            }

            if (protocol.items.isEmpty()) {
                item {
                    Text(
                        text = "Węzły zakresu nie mają pytań protokołu — uzupełnia się je " +
                            "w karcie węzła katalogu. Zostaje odbiór i podpis.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            protocol.groups().forEach { (group, questions) ->
                item(key = "g-$group") {
                    Text(
                        text = group.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(questions.size, key = { "q-${questions[it].id}" }) { index ->
                    val question = questions[index]
                    QuestionCard(
                        question = question,
                        answer = protocol.answers[question.id] ?: ProtocolAnswer(),
                        readOnly = readOnly,
                        onAnswer = { patch -> viewModel.setAnswer(question.id, patch) },
                        onPhoto = {
                            pendingQuestion[0] = question.id
                            pickers.takePhoto()
                        },
                        onRemovePhoto = { viewModel.removeLastPhoto(question.id) },
                    )
                }
            }

            // ── Zamknięcie ──
            item {
                SectionCard {
                    SectionTitle(text = "Zamknięcie")
                    SectionGap()
                    OutlinedTextField(
                        value = protocol.issues,
                        onValueChange = viewModel::setIssues,
                        enabled = !readOnly,
                        label = { Text("Uwagi / usterki") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = protocol.accepted,
                            enabled = !readOnly,
                            onCheckedChange = viewModel::setAccepted,
                            colors = CheckboxDefaults.colors(checkedColor = OkGreen),
                        )
                        Text("Odbiór bez zastrzeżeń", style = MaterialTheme.typography.bodyMedium)
                    }
                    OutlinedTextField(
                        value = protocol.clientName,
                        onValueChange = viewModel::setClientName,
                        enabled = !readOnly,
                        label = { Text("Imię i nazwisko odbierającego") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (readOnly) {
                        Text(
                            text = "Protokół zamknięty — podpis i odpowiedzi są już dokumentem.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OkGreen,
                        )
                    } else {
                        SignaturePad(
                            value = protocol.signature,
                            onChange = viewModel::setSignature,
                        )
                    }
                }
            }

            if (!readOnly) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.protocolMissing.isNotEmpty()) {
                            Text(
                                text = "Do zamknięcia brakuje: " +
                                    state.protocolMissing.joinToString(", ") { it.label } + ".",
                                style = MaterialTheme.typography.bodySmall,
                                color = Red600,
                            )
                        }
                        Button(
                            onClick = { viewModel.saveProtocol(close = true) },
                            enabled = !state.isSaving,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Zamknij protokół i wyślij") }
                        OutlinedButton(
                            onClick = { viewModel.saveProtocol(close = false) },
                            enabled = !state.isSaving,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Zapisz i dokończ później") }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** Jedno pytanie: odpowiedź wg rodzaju, uwaga i zdjęcia. */
@Composable
private fun QuestionCard(
    question: ProtocolQuestion,
    answer: ProtocolAnswer,
    readOnly: Boolean,
    onAnswer: ((ProtocolAnswer) -> ProtocolAnswer) -> Unit,
    onPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
) {
    val missingAnswer = question.required && !question.answered(answer)
    val missingPhoto = !question.photosOk(answer)

    SectionCard {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = question.label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = when {
                    missingAnswer || missingPhoto -> if (missingPhoto) "zdjęcie" else "wymagane"
                    question.required -> "✓"
                    else -> ""
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (missingAnswer || missingPhoto) Red600 else OkGreen,
            )
        }
        if (question.hint.isNotBlank()) {
            Text(
                text = question.hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))

        when (question.kind) {
            ProtocolKind.CHECK -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = answer.checked == true,
                    enabled = !readOnly,
                    onClick = { onAnswer { it.copy(checked = true) } },
                    label = { Text("Wykonane") },
                )
                FilterChip(
                    selected = answer.checked == false,
                    enabled = !readOnly,
                    onClick = { onAnswer { it.copy(checked = false) } },
                    label = { Text("Nie dotyczy") },
                )
            }

            ProtocolKind.NUMBER -> OutlinedTextField(
                value = answer.number?.let { formatAnswerNumber(it) } ?: "",
                onValueChange = { raw ->
                    val parsed = raw.replace(",", ".").trim().toDoubleOrNull()
                    onAnswer { it.copy(number = if (raw.isBlank()) null else parsed) }
                },
                enabled = !readOnly,
                label = { Text(question.unit.ifBlank { "wartość" }) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            ProtocolKind.TEXT -> OutlinedTextField(
                value = answer.text,
                onValueChange = { v -> onAnswer { it.copy(text = v) } },
                enabled = !readOnly,
                label = { Text("Odpowiedź") },
                modifier = Modifier.fillMaxWidth(),
            )

            ProtocolKind.CHOICE -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                question.options.forEach { option ->
                    FilterChip(
                        selected = answer.text == option,
                        enabled = !readOnly,
                        onClick = { onAnswer { it.copy(text = option) } },
                        label = { Text(option) },
                    )
                }
            }

            ProtocolKind.PHOTO -> Unit
        }

        if (question.photo != ProtocolPhoto.NONE) {
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                answer.photoIds.forEach { _ ->
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) { Text("📷", style = MaterialTheme.typography.bodyMedium) }
                }
                if (!readOnly) {
                    OutlinedButton(onClick = onPhoto) {
                        Text(if (answer.photoIds.isEmpty()) "Zrób zdjęcie" else "Kolejne")
                    }
                    if (answer.photoIds.isNotEmpty()) {
                        TextButton(onClick = onRemovePhoto) { Text("− zdjęcie") }
                    }
                }
            }
            if (missingPhoto) {
                Text(
                    text = "Brakuje zdjęć: ${answer.photoIds.size} z ${question.photosNeeded}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Red600,
                )
            }
        }

        if (!readOnly) {
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = answer.note,
                onValueChange = { v -> onAnswer { it.copy(note = v) } },
                label = { Text("Uwaga (opcjonalnie)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (answer.note.isNotBlank()) {
            Text(
                text = "Uwaga: ${answer.note}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** „6" zamiast „6.0" — na budowie nikt nie wpisuje zer po przecinku. */
private fun formatAnswerNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
