package com.ekotak.teamtalk.presentation.meetings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ekotak.teamtalk.data.remote.dto.MeetingDto
import com.ekotak.teamtalk.data.remote.dto.MeetingVoiceSummaryDto
import com.ekotak.teamtalk.presentation.crm.formatDateTime
import com.ekotak.teamtalk.presentation.theme.Orange600
import com.ekotak.teamtalk.presentation.theme.Red600
import kotlinx.coroutines.delay

/** Krótsze nagranie i tak nie da podsumowania (serwer odrzuca transkrypcję < 40 znaków). */
private const val MIN_SEC = 15

/**
 * D9 (2026-09-28): spotkanie bez nagrania → prowadzący MUSI nagrać podsumowanie
 * głosem. Okno bez „Pomiń" i bez pisania ręcznie; gdy wymagane, nie znika po
 * „Wstecz" ani po kliknięciu obok. Nagranie idzie na serwer jako `kind=summary`
 * i dalej jest traktowane jak transkrypcja spotkania: podsumowanie AI →
 * propozycje zadań → akceptacja. [onClose] tylko przy otwarciu z przycisku,
 * zanim stało się wymagane (czekamy jeszcze na nagranie z telefonu).
 */
@Composable
fun MeetingVoiceSummaryDialog(
    m: MeetingDto,
    need: MeetingVoiceSummaryDto,
    voice: MeetingDetailViewModel.VoiceDraft,
    busy: Boolean,
    elapsedMs: () -> Long,
    onRecord: () -> Unit,
    onStop: () -> Unit,
    onSend: () -> Unit,
    onRetry: () -> Unit,
    onReschedule: () -> Unit,
    onDelete: () -> Unit,
    onClose: (() -> Unit)?,
) {
    Dialog(
        onDismissRequest = { if (!voice.recording && !voice.sending) onClose?.invoke() },
        properties = DialogProperties(
            dismissOnBackPress = onClose != null,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(max = 640.dp),
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Nagraj podsumowanie spotkania", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(m.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                NoticeStrip(
                    when (need.reason) {
                        "not_started" -> "Spotkanie miało się odbyć ${formatDateTime(m.startAt) ?: ""}, ale nie zostało włączone, więc nie ma nagrania."
                        "no_recording" -> "Nagranie spotkania nie dotarło na serwer."
                        else -> m.transcriptionError ?: "Nie udało się spisać nagrania spotkania."
                    },
                    if (need.reason == "failed") Red600 else Orange600,
                )
                Text(
                    "Opowiedz w 1–3 minuty, co ustaliliście: decyzje, kto ma co zrobić i do kiedy. " +
                        "Z tego nagrania powstaną podsumowanie i propozycje zadań — tak jak z nagrania spotkania. " +
                        "Ręczne pisanie podsumowania jest wyłączone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (m.agenda.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Przejdź po punktach agendy:", style = MaterialTheme.typography.labelMedium)
                        m.agenda.forEachIndexed { i, a -> Text("${i + 1}. ${a.text}", style = MaterialTheme.typography.bodySmall) }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        when {
                            voice.recording -> {
                                var ms by remember { mutableLongStateOf(0L) }
                                LaunchedEffect(Unit) { while (true) { ms = elapsedMs(); delay(500) } }
                                val sec = (ms / 1000).toInt()
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = CircleShape, color = Red600, modifier = Modifier.size(10.dp)) {}
                                    Text("  Nagrywam…", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(formatElapsed(sec.toLong()), fontSize = 36.sp, fontWeight = FontWeight.Bold)
                                Button(
                                    onClick = onStop,
                                    enabled = sec >= MIN_SEC,
                                    colors = ButtonDefaults.buttonColors(containerColor = Red600),
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(if (sec < MIN_SEC) "■  Zakończ (jeszcze ${MIN_SEC - sec} s)" else "■  Zakończ nagrywanie") }
                            }
                            voice.file != null -> {
                                Text("Nagrane: ${formatElapsed(voice.durationSec.toLong())}", fontWeight = FontWeight.SemiBold)
                                if (voice.sending) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Text("  Wysyłam…", style = MaterialTheme.typography.bodySmall)
                                    }
                                } else {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        OutlinedButton(onClick = onRecord, modifier = Modifier.weight(1f)) { Text("Od nowa") }
                                        Button(onClick = onSend, modifier = Modifier.weight(1f)) { Text("Wyślij") }
                                    }
                                }
                            }
                            else -> Button(
                                onClick = onRecord,
                                colors = ButtonDefaults.buttonColors(containerColor = Red600),
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("●  Nagraj podsumowanie") }
                        }
                    }
                }

                voice.error?.let { NoticeStrip(it, Red600) }

                val idle = !voice.recording && voice.file == null
                if (idle && (need.reason == "not_started" || need.canRetry || onClose != null)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        if (need.reason == "not_started") {
                            TextButton(onClick = onReschedule, enabled = !busy) { Text("Nie odbyło się — przełóż termin") }
                            TextButton(onClick = onDelete, enabled = !busy) { Text("Nie odbyło się — usuń spotkanie") }
                        }
                        if (need.canRetry) {
                            TextButton(onClick = onRetry, enabled = !busy) { Text("Ponów transkrypcję nagrania") }
                        }
                        onClose?.let { TextButton(onClick = it) { Text("Poczekam na nagranie") } }
                    }
                }
            }
        }
    }
}
