package com.ekotak.teamtalk.presentation.training

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.model.QuestionKind
import com.ekotak.teamtalk.presentation.components.AppTopBar
import com.ekotak.teamtalk.presentation.crm.formatDate
import com.ekotak.teamtalk.presentation.theme.EkotakGreen
import com.ekotak.teamtalk.presentation.theme.Orange600
import com.ekotak.teamtalk.presentation.theme.Red600
import java.io.File

/**
 * Ekran lekcji: film → test → wynik. Odpowiednik `/szkolenie/[id]` z panelu,
 * z dwiema różnicami ustalonymi 2026-09-06: bramka na obejrzenie filmu i limit
 * czasu liczony per pytanie (20 s), a nie 60 s na cały test.
 */
@Composable
fun LessonScreen(
    onNavigateBack: () -> Unit,
    viewModel: LessonViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = state.data?.lesson?.title ?: "Szkolenie",
                onNavigateBack = onNavigateBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.forbidden -> Notice(
                    title = "Szkolenie niedostępne",
                    body = "To szkolenie nie jest Ci przypisane albo nie istnieje. " +
                        "Jeśli powinieneś je odbyć, poproś opiekuna o przypisanie.",
                    actionLabel = "Wróć",
                    onAction = onNavigateBack,
                )

                state.data == null -> Notice(
                    title = "Nie udało się otworzyć lekcji",
                    body = state.error ?: "Spróbuj ponownie za chwilę.",
                    actionLabel = "Ponów",
                    onAction = viewModel::load,
                )

                else -> when (state.phase) {
                    LessonPhase.VIDEO -> VideoStep(state, viewModel)
                    LessonPhase.QUIZ -> QuizStep(state, viewModel)
                    LessonPhase.RESULT -> ResultStep(
                        state = state,
                        onRetry = viewModel::retry,
                        onBack = onNavigateBack,
                        onCertificate = {
                            viewModel.openCertificate(context.cacheDir) { file ->
                                context.openPdf(file)
                            }
                        },
                    )
                }
            }
        }
    }
}

// ── Krok 1: film ─────────────────────────────────────────────────────────────

@Composable
private fun VideoStep(state: LessonViewModel.UiState, viewModel: LessonViewModel) {
    val lesson = state.data?.lesson ?: return
    val context = LocalContext.current
    var showTranscript by remember { mutableStateOf(false) }
    val embeddable = remember(lesson.videoUrl) { canEmbed(lesson) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (embeddable) {
            LessonVideo(
                lesson = lesson,
                onProgress = viewModel::onWatchProgress,
                onEnded = viewModel::onVideoEnded,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp)),
            )
        } else {
            // Link spoza YouTube/Loom — osadzenie nie ma jak zadziałać, więc
            // oddajemy film przeglądarce zamiast pokazywać czarny prostokąt.
            Card {
                Text("Filmu nie da się osadzić w aplikacji.", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { context.openLink(lesson.videoUrl) }) {
                    Text("Otwórz film w przeglądarce")
                }
            }
        }

        ChipRow(
            listOfNotNull(
                "${state.questions.size} pytań".takeIf { state.questions.isNotEmpty() },
                "próg ${lesson.passThreshold}%",
                lesson.validityMonths?.let { "ważne $it mies." },
            ),
        )

        if (state.questions.isEmpty()) {
            Card {
                Text(
                    "Ta lekcja nie ma pytań — wystarczy obejrzeć film.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            Card {
                Label("Krok 1 — film")
                if (state.trackable) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        LinearProgressIndicator(
                            progress = { state.watched },
                            modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                        )
                        Text(
                            "${(state.watched * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "Test odblokuje się po obejrzeniu filmu. Przewijanie do przodu " +
                            "nie liczy się jako obejrzane.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!state.trackable || (state.watched < 0.95f && state.canConfirmManually)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = state.canConfirmManually && !state.manualWatched) {
                                viewModel.confirmWatchedManually()
                            },
                    ) {
                        Checkbox(
                            checked = state.manualWatched,
                            enabled = state.canConfirmManually && !state.manualWatched,
                            onCheckedChange = { viewModel.confirmWatchedManually() },
                        )
                        Text(
                            if (state.canConfirmManually) {
                                "Obejrzałem cały film"
                            } else {
                                "Potwierdzenie odblokuje się za chwilę"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            Button(
                onClick = viewModel::startQuiz,
                enabled = state.videoDone,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Rozpocznij test — ${state.questions.size} pyt., " +
                        formatClock(state.totalSeconds),
                )
            }
            Text(
                "Po starcie zegar tyka dla całego testu ($SECONDS_PER_QUESTION s na pytanie). " +
                    "Po jego upływie odpowiedzi wyślą się automatycznie.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val transcript = lesson.description?.takeIf { it.isNotBlank() }
        if (transcript != null) {
            TextButton(onClick = { showTranscript = !showTranscript }) {
                Text(if (showTranscript) "Ukryj transkrypcję" else "Pokaż transkrypcję filmu")
            }
            if (showTranscript) {
                Card { Text(transcript, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

// ── Krok 2: test ─────────────────────────────────────────────────────────────

@Composable
private fun QuizStep(state: LessonViewModel.UiState, viewModel: LessonViewModel) {
    val question = state.current ?: return
    val selected = state.answers[question.id].orEmpty()
    val low = state.remainingSeconds <= 15

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Pytanie ${state.questionIndex + 1} z ${state.questions.size} · " +
                    "próg ${state.data?.lesson?.passThreshold ?: 0}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (low) Red600.copy(alpha = 0.15f) else Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (low) Red600 else MaterialTheme.colorScheme.outline,
                ),
            ) {
                Text(
                    formatClock(state.remainingSeconds),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (low) Red600 else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }

        LinearProgressIndicator(
            progress = { state.answeredCount.toFloat() / state.questions.size },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
        )

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(question.prompt, style = MaterialTheme.typography.titleMedium)
            Text(
                if (question.kind == QuestionKind.MULTI) "wielokrotny wybór" else "jedna odpowiedź",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            question.options.forEachIndexed { index, option ->
                val on = index in selected
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = 1.dp,
                            color = if (on) EkotakGreen else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .background(
                            if (on) EkotakGreen.copy(alpha = 0.12f) else Color.Transparent,
                        )
                        .clickable { viewModel.select(question.id, index, question.kind) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    if (question.kind == QuestionKind.MULTI) {
                        Checkbox(
                            checked = on,
                            onCheckedChange = { viewModel.select(question.id, index, question.kind) },
                        )
                    } else {
                        RadioButton(
                            selected = on,
                            onClick = { viewModel.select(question.id, index, question.kind) },
                        )
                    }
                    Text(
                        option,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = viewModel::previousQuestion,
                enabled = state.questionIndex > 0,
                modifier = Modifier.weight(1f),
            ) { Text("Wstecz") }

            if (state.questionIndex < state.questions.lastIndex) {
                Button(
                    onClick = viewModel::nextQuestion,
                    modifier = Modifier.weight(1f),
                ) { Text("Dalej") }
            } else {
                Button(
                    onClick = { viewModel.submit() },
                    enabled = !state.submitting,
                    modifier = Modifier.weight(1f),
                ) { Text(if (state.submitting) "Sprawdzam…" else "Wyślij") }
            }
        }
        if (!state.allAnswered) {
            Text(
                "Odpowiedziano na ${state.answeredCount} z ${state.questions.size}. " +
                    "Brakujące pytania wyślą się jako puste.",
                style = MaterialTheme.typography.bodySmall,
                color = Orange600,
            )
        }
    }
}

// ── Krok 3: wynik ────────────────────────────────────────────────────────────

@Composable
private fun ResultStep(
    state: LessonViewModel.UiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onCertificate: () -> Unit,
) {
    val result = state.result ?: return
    val accent = if (result.passed) EkotakGreen else Red600

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = accent.copy(alpha = 0.12f),
            border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "${result.score}%",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                Text(
                    if (result.passed) {
                        "Zaliczone — ${result.correctCount} z ${result.total} poprawnych. " +
                            "Wpis trafił do Twojej kartoteki HR."
                    } else {
                        "Za mało — ${result.correctCount} z ${result.total} poprawnych. " +
                            "Wymagane ${result.passThreshold}%."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        InfoRow("Próg zaliczenia", "${result.passThreshold}%")
        InfoRow("Poprawne odpowiedzi", "${result.correctCount} / ${result.total}")
        state.data?.lesson?.validityMonths?.let { InfoRow("Ważność", "$it mies. od dziś") }

        Spacer(Modifier.height(4.dp))

        if (result.passed) {
            Button(onClick = onCertificate, modifier = Modifier.fillMaxWidth()) {
                Text("Pobierz certyfikat (PDF)")
            }
            Text(
                "Certyfikat otwiera się w systemowej przeglądarce PDF.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Wróć do moich szkoleń")
            }
        } else {
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text("Spróbuj ponownie")
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Wróć do moich szkoleń")
            }
        }
    }
}

// ── Drobiazgi wspólne ────────────────────────────────────────────────────────

@Composable
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ChipRow(items: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { text ->
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun InfoRow(key: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            key,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Notice(title: String, body: String, actionLabel: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onAction) { Text(actionLabel) }
    }
}

/** „5:00" — limit i pozostały czas czyta się na telefonie jako zegar. */
fun formatClock(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

/** Data ważności w wierszu listy; puste pole daje „—". */
fun formatDayOrDash(iso: String?): String = formatDate(iso) ?: "—"

/**
 * Certyfikat oddany systemowi. `FLAG_GRANT_READ_URI_PERMISSION` jest tu
 * obowiązkowe — bez niego czytnik PDF dostanie `content://`, do którego nie ma
 * prawa. Tak samo działają załączniki zadań.
 */
fun android.content.Context.openPdf(file: File) {
    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Bez czytnika PDF nie ma co robić — plik został w pamięci podręcznej.
    }
}

private fun android.content.Context.openLink(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Telefon bez przeglądarki — nic sensownego nie da się tu zrobić.
    }
}

