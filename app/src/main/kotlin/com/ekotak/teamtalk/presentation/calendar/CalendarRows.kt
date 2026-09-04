package com.ekotak.teamtalk.presentation.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.CalendarEvent
import com.ekotak.teamtalk.domain.model.CalendarOverlay
import com.ekotak.teamtalk.domain.model.RsvpStatus
import com.ekotak.teamtalk.presentation.theme.Red600

/**
 * Wiersze list kalendarza: wydarzenie i nakładka operacyjna. Ten sam kształt
 * pod siatką miesiąca i w agendzie — dzięki temu przełączenie widoku nie zmienia
 * sposobu czytania dnia.
 */

@Composable
fun EventRow(
    event: CalendarEvent,
    currentUserId: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = eventColor(event)
    val response = event.responseOf(currentUserId)
    val start = event.startMillis()
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Pasek koloru kalendarza — lewa krawędź wiersza, jak w panelu.
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(color),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = if (event.allDay) "cały\ndzień" else start?.let { formatHour(it) }.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(38.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = eventSubtitle(event)
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (event.pendingSync) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = "Czeka na wysyłkę",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp),
                    )
                } else if (response != null && response != RsvpStatus.NEEDS_ACTION) {
                    RsvpBadge(response)
                }
            }
        }
    }
}

/** Podtytuł wiersza: miejsce, liczba uczestników, znacznik serii — jak tooltip panelu. */
private fun eventSubtitle(event: CalendarEvent): String = listOfNotNull(
    event.location?.takeIf { it.isNotBlank() },
    event.attendees.size.takeIf { it > 0 }?.let { "$it ${peopleWord(it)}" },
    if (event.isRecurring) "🔁 cykl" else null,
    if (event.pendingSync) "czeka na wysyłkę" else null,
).joinToString(" · ")

private fun peopleWord(count: Int): String = when {
    count == 1 -> "osoba"
    count % 10 in 2..4 && count % 100 !in 12..14 -> "osoby"
    else -> "osób"
}

@Composable
fun RsvpBadge(response: RsvpStatus) {
    val color = rsvpColor(response)
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
    ) {
        Text(
            text = when (response) {
                RsvpStatus.ACCEPTED -> "idę"
                RsvpStatus.DECLINED -> "nie idę"
                RsvpStatus.TENTATIVE -> "może"
                RsvpStatus.NEEDS_ACTION -> "?"
            },
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
        )
    }
}

@Composable
fun rsvpColor(response: RsvpStatus): Color = when (response) {
    RsvpStatus.ACCEPTED -> Color(0xFF2FA84F)
    RsvpStatus.DECLINED -> Red600
    RsvpStatus.TENTATIVE -> Color(0xFFB4680A)
    RsvpStatus.NEEDS_ACTION -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * Nakładka operacyjna — podgląd rekordu z innego modułu. Wiersz jest przygaszony
 * i ma przerywaną obwódkę, żeby nie brać go za wydarzenie, którego nie da się
 * tu edytować.
 */
@Composable
fun OverlayRow(
    overlay: CalendarOverlay,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val color = parseHexColor(overlay.color) ?: parseHexColor(overlay.source.color) ?: Color.Gray
    val content: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .background(color, RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = overlay.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = overlay.source.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!overlay.allDay) {
                Text(
                    text = formatHour(overlay.startAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            color = Color.Transparent,
            border = border,
            modifier = modifier.fillMaxWidth(),
        ) { content() }
    } else {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.Transparent,
            border = border,
            modifier = modifier.fillMaxWidth(),
        ) { content() }
    }
}

/** Nagłówek dnia nad listą — data i licznik pozycji. */
@Composable
fun DayHeader(text: String, count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Text(
            text = text.replaceFirstChar { it.titlecase(PL) },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "· $count poz.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Wiersz „Zajęte" — prywatna zajętość kolegi z zespołu (podpięty w panelu
 * kalendarz iCal). Świadomie wygląda inaczej niż wydarzenie: bez koloru
 * kalendarza, bez tytułu i bez kliknięcia, bo nie ma czego otworzyć — z
 * prywatnego kalendarza znamy WYŁĄCZNIE godziny.
 */
@Composable
fun BusyRow(label: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(2.dp),
                    ),
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = "Zajęte",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
