package com.ekotak.teamtalk.presentation.calllog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.TranscriptionStatus
import com.ekotak.teamtalk.domain.model.VoiceReport

/**
 * Treść notatki z nagrania rozmowy: stan transkrypcji, streszczenie, ustalenia,
 * następny krok i rozwijana pełna transkrypcja. Notatka bez nagrania pokazuje
 * sam tekst — tak jak dotąd.
 *
 * [compact] — wersja do list (karta klienta, historia): streszczenie ucięte,
 * transkrypcja tylko na żądanie.
 */
@Composable
fun CallRecordingNote(
    report: VoiceReport,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val hasRecording = report.recordingKey != null
    Column(modifier = modifier) {
        if (hasRecording) TranscriptionStatusLine(report)

        report.summary?.takeIf { it.isNotBlank() }?.let {
            Label("Podsumowanie")
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                maxLines = if (compact) 6 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Tekst człowieka (dyktowany po rozmowie). Stara notatka bez nagrania
        // miewała samą transkrypcję — wtedy ona jest treścią.
        val body = report.text?.takeIf { it.isNotBlank() }
            ?: report.transcript?.takeIf { !hasRecording && it.isNotBlank() }
        body?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                maxLines = if (compact) 5 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
            )
        }

        report.agreements?.takeIf { it.isNotBlank() }?.let {
            Label("Ustalenia")
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
        report.nextStep?.takeIf { it.isNotBlank() }?.let {
            Label("Następny krok")
            Text(it, style = MaterialTheme.typography.bodySmall)
        }

        val transcript = report.transcript?.takeIf { hasRecording && it.isNotBlank() }
        if (transcript != null) {
            // Bez streszczenia (krótka rozmowa, brak modelu) transkrypcja jest
            // jedyną treścią — na pełnym ekranie od razu rozwinięta.
            var expanded by rememberSaveable(report.id) {
                mutableStateOf(!compact && report.summary.isNullOrBlank())
            }
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
            ) {
                Text(
                    text = if (expanded) "Zwiń transkrypcję" else "Pełna transkrypcja",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Text(
                    text = transcript,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TranscriptionStatusLine(report: VoiceReport) {
    when (report.transcriptionStatus) {
        TranscriptionStatus.PENDING, TranscriptionStatus.PROCESSING -> Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (report.transcriptionStatus == TranscriptionStatus.PROCESSING) {
                    "Trwa transkrypcja nagrania…"
                } else {
                    "Nagranie czeka na transkrypcję"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TranscriptionStatus.FAILED -> Text(
            text = "Transkrypcja nieudana" + (report.transcriptionError?.let { ": $it" } ?: ""),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
        TranscriptionStatus.DONE -> if (report.transcript.isNullOrBlank()) {
            Text(
                text = "Na nagraniu nie rozpoznano mowy.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        null -> Unit
    }
}

@Composable
private fun Label(text: String) {
    Spacer(Modifier.height(6.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}
