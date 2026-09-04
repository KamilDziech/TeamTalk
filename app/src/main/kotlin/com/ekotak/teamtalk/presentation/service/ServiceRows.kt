package com.ekotak.teamtalk.presentation.service

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.MAX_WARRANTY_INSPECTIONS
import com.ekotak.teamtalk.domain.model.ServiceJob
import com.ekotak.teamtalk.domain.model.ServiceJobPriority
import com.ekotak.teamtalk.domain.model.ServiceJobStatus
import com.ekotak.teamtalk.domain.model.Technician
import com.ekotak.teamtalk.presentation.theme.EkotakBlack
import com.ekotak.teamtalk.presentation.theme.EkotakGreen
import com.ekotak.teamtalk.presentation.theme.Orange600
import com.ekotak.teamtalk.presentation.theme.Red600
import com.ekotak.teamtalk.presentation.theme.SyncBlue

/**
 * Wiersze list modułu Serwis. Układ jest świadomą kopią wiersza zadania
 * (kółko · nazwa · prawa komórka · gwiazdka) — w panelu zakładka „Serwis"
 * klonuje moduł Zadania i telefon ma się czytać tak samo.
 *
 * Różnice wymuszone domeną:
 *  - kółko: awatar serwisanta / „?" gdy nieprzypisane / pierścień = w toku /
 *    ptaszek = wykonane (Serwis ma trzy statusy, Zadania dwa),
 *  - ptaszek „na skróty" pokazujemy TYLKO przy własnych zleceniach (ustalenie
 *    2026-09-02) — cudze zamyka się z karty, żeby nie odhaczyć ich odruchowo,
 *  - dodatkowa komórka: chip SLA (awaria ma okno 24 h) albo termin.
 */

/** Kolory świateł gwarancji — te same wartości co w panelu (`service.module.css`). */
val WarrantyGreen = EkotakGreen
val WarrantyRed = Color(0xFFF85149)
val WarrantyGray = Color(0xFF888780)

@Composable
fun lightColor(light: WarrantyLight): Color = when (light) {
    WarrantyLight.GREEN -> WarrantyGreen
    WarrantyLight.RED -> WarrantyRed
    WarrantyLight.GRAY -> WarrantyGray
    WarrantyLight.FUTURE -> Color.Transparent
}

/** Wiersz zlecenia serwisowego (awaria, przegląd, konserwacja). */
@Composable
fun ServiceJobRow(
    job: ServiceJob,
    label: String,
    technician: Technician?,
    meta: RowMeta,
    isMine: Boolean,
    pending: Boolean,
    queued: Boolean,
    onOpen: () -> Unit,
    onToggleDone: () -> Unit,
    onTogglePriority: () -> Unit,
) {
    val done = job.status == ServiceJobStatus.DONE
    val incomplete = !done && isIncomplete(job)
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            JobCircle(
                job = job,
                technician = technician,
                isMine = isMine,
                pending = pending,
                onToggleDone = onToggleDone,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        done -> MaterialTheme.colorScheme.onSurfaceVariant
                        incomplete -> Red600
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = subtitleOf(job, technician, queued)
                if (sub != null) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (queued) SyncBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            SlaCell(meta = meta, dimmed = incomplete && job.scheduledAt == null)
            IconButton(onClick = onTogglePriority, enabled = !pending) {
                val high = job.priority == ServiceJobPriority.HIGH
                Icon(
                    imageVector = if (high) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (high) "Priorytet wysoki" else "Priorytet normalny",
                    tint = if (high) Orange600 else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Podpis wiersza: kto robi, czego brakuje albo że zmiana czeka na wysyłkę. */
private fun subtitleOf(job: ServiceJob, technician: Technician?, queued: Boolean): String? = when {
    queued -> "czeka na wysyłkę"
    job.status == ServiceJobStatus.DONE -> "wykonane"
    technician != null -> technician.displayName
    else -> missingHint(job).takeIf { it.isNotEmpty() }
}

/**
 * Kółko wiersza. Ptaszek dostają tylko zlecenia własne i już wykonane (te da się
 * cofnąć) — przy cudzych stoi awatar serwisanta, żeby jedno dotknięcie nie
 * zamknęło komuś zlecenia.
 */
@Composable
private fun JobCircle(
    job: ServiceJob,
    technician: Technician?,
    isMine: Boolean,
    pending: Boolean,
    onToggleDone: () -> Unit,
) {
    val done = job.status == ServiceJobStatus.DONE
    val inProgress = job.status == ServiceJobStatus.IN_PROGRESS
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
        when {
            pending -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            done || isMine -> {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(
                            color = if (done) EkotakGreen else Color.Transparent,
                            shape = CircleShape,
                        )
                        .border(
                            width = if (done) 0.dp else 2.dp,
                            color = if (done) Color.Transparent else MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        )
                        .clickable(onClick = onToggleDone),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Cofnij — zlecenie znów w toku",
                            tint = EkotakBlack,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            else -> TechnicianAvatar(technician = technician, ring = inProgress)
        }
    }
}

/** Awatar serwisanta (inicjały) — pierścień oznacza zlecenie w toku. */
@Composable
fun TechnicianAvatar(technician: Technician?, ring: Boolean, size: Int = 34) {
    val bg = if (technician == null) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(bg, CircleShape)
            .border(
                width = if (ring) 2.dp else 1.dp,
                color = if (ring) SyncBlue else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = technician?.initials ?: "?",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (technician == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
        )
    }
}

/** Prawa komórka: chip SLA (alarm/ostrzeżenie) albo zwykły termin. */
@Composable
fun SlaCell(meta: RowMeta, dimmed: Boolean = false) {
    when (meta.tone) {
        MetaTone.PLAIN -> Text(
            text = meta.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (dimmed) Red600 else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        else -> {
            val color = if (meta.tone == MetaTone.BREACH) Red600 else Orange600
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = color.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, color.copy(alpha = 0.55f)),
            ) {
                Text(
                    text = meta.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/**
 * Wiersz urządzenia gwarancyjnego: pasek pięciu świateł i najpilniejszy stan
 * całej instalacji. Belka po lewej powtarza kolor stanu, żeby zaległa karta
 * była widoczna bez czytania (i bez polegania na samym kolorze — obok stoi
 * etykieta słowna).
 */
@Composable
fun WarrantyRow(view: WarrantyRowView, onOpen: () -> Unit) {
    val urgencyColor = when (view.urgency) {
        WarrantyLight.RED -> WarrantyRed
        WarrantyLight.GRAY -> WarrantyGray
        WarrantyLight.GREEN -> WarrantyGreen
        WarrantyLight.FUTURE -> MaterialTheme.colorScheme.outlineVariant
    }
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(14.dp),
        color = if (view.urgency == WarrantyLight.FUTURE) {
            MaterialTheme.colorScheme.surface
        } else {
            urgencyColor.copy(alpha = 0.09f)
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .size(width = 3.dp, height = 56.dp)
                    .background(urgencyColor),
            )
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = view.card.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(6.dp))
                        WarrantyBadge()
                    }
                    Text(
                        text = warrantySubtitle(view),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                LightStrip(view.lights)
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = view.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Text(
                        text = "${view.doneCount}/$MAX_WARRANTY_INSPECTIONS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun warrantySubtitle(view: WarrantyRowView): String = listOfNotNull(
    view.card.location?.takeIf { it.isNotBlank() },
    view.card.brand.takeIf { it.isNotBlank() },
    formatDay(view.card.commissionedAt).takeIf { it.isNotEmpty() }?.let { "uruch. $it" },
).joinToString(" · ")

@Composable
private fun WarrantyBadge() {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = "gwarancja",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

/** Pasek pięciu świateł: stałe pozycje 1..5, brak wpisu = puste kółko. */
@Composable
fun LightStrip(lights: List<WarrantyLight>, dotSize: Int = 10) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        lights.forEachIndexed { index, light ->
            Box(
                modifier = Modifier
                    .size(dotSize.dp)
                    .background(lightColor(light), CircleShape)
                    .border(
                        width = if (light == WarrantyLight.FUTURE) 1.5.dp else 0.dp,
                        color = if (light == WarrantyLight.FUTURE) {
                            MaterialTheme.colorScheme.outline
                        } else {
                            Color.Transparent
                        },
                        shape = CircleShape,
                    ),
            )
            if (index < lights.lastIndex) Spacer(Modifier.width(0.dp))
        }
    }
}

