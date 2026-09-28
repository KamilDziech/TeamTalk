package com.ekotak.teamtalk.presentation.meetings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.presentation.theme.Orange600
import com.ekotak.teamtalk.presentation.theme.Red600

/** Barwy rodzajów — te same, co w panelu (`web/src/lib/meetings.ts`). */
fun meetingTypeColor(type: String): Color = when (type) {
    "board" -> Color(0xFFF59E0B)
    "employee" -> Color(0xFF38BDF8)
    "training" -> Color(0xFFA78BFA)
    "contractor" -> Color(0xFF34D399)
    else -> Color(0xFF8AA0B6)
}

fun meetingTypeIcon(type: String): ImageVector = when (type) {
    "board" -> Icons.Filled.Groups
    "employee" -> Icons.Filled.Person
    "contractor" -> Icons.Filled.BusinessCenter
    else -> Icons.Filled.School
}

fun meetingTypeHint(type: String): String = when (type) {
    "board" -> "Decyzje zarządu. Zakłada i prowadzi tylko zarząd."
    "employee" -> "Rozmowa z pracownikiem — poufna, widzą ją tylko uczestnicy."
    "contractor" -> "Z kimś spoza firmy — architektem, dostawcą. Kontrahent z kartoteki."
    else -> "Szkolenie zespołu — podsumowanie omówionych tematów."
}

/** Pseudo-status pigułki na liście: D9, prowadzący musi nagrać podsumowanie głosem. */
const val VOICE_SUMMARY_PILL = "voice_summary"

fun meetingStatusLabel(status: String): String = when (status) {
    VOICE_SUMMARY_PILL -> "Nagraj podsumowanie"
    "scheduled" -> "Zaplanowane"
    "live" -> "Trwa"
    "paused" -> "Pauza"
    "processing" -> "Przetwarzanie"
    "review" -> "Do akceptacji"
    "approved" -> "Zamknięte"
    "failed" -> "Błąd nagrania"
    else -> status
}

fun rsvpLabel(r: String?): String = when (r) {
    "accepted" -> "Będzie"
    "declined" -> "Nie będzie"
    "tentative" -> "Może"
    "needs_action" -> "Bez odpowiedzi"
    else -> ""
}

fun formatElapsed(sec: Long): String {
    val s = sec.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val r = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, r) else "%02d:%02d".format(m, r)
}

@Composable
fun TypeBadge(type: String, size: Dp = 40.dp) {
    val c = meetingTypeColor(type)
    Surface(
        shape = RoundedCornerShape(11.dp),
        color = c.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, c.copy(alpha = 0.32f)),
        modifier = Modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(meetingTypeIcon(type), contentDescription = null, tint = c, modifier = Modifier.size(size * 0.55f))
        }
    }
}

@Composable
fun StatusPill(status: String) {
    val (bg, fg) = when (status) {
        "live" -> Red600 to Color.White
        "review", VOICE_SUMMARY_PILL -> Orange600 to Color(0xFF1A1203)
        "approved" -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurface
    }
    Surface(shape = RoundedCornerShape(50), color = bg) {
        Text(
            meetingStatusLabel(status),
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun MeetingCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
fun NoticeStrip(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
    }
}

/** Druga linijka kontrahenta: osoba · rola · telefon · grupa kartoteki. */
fun contractorLine(c: com.ekotak.teamtalk.data.remote.dto.MeetingContractorDto): String =
    listOfNotNull(
        c.person,
        c.businessRole,
        c.phone,
        when (c.category) { "kontrahent" -> "Kontrahenci"; "inne" -> "Inne"; else -> null },
    ).filter { it.isNotBlank() }.joinToString(" · ")
