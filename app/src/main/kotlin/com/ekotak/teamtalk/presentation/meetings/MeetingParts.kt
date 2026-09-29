package com.ekotak.teamtalk.presentation.meetings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ekotak.teamtalk.presentation.crm.formatDate
import com.ekotak.teamtalk.presentation.crm.formatDateTime
import com.ekotak.teamtalk.presentation.crm.parseIsoMillis
import com.ekotak.teamtalk.presentation.theme.Green600
import com.ekotak.teamtalk.presentation.theme.Orange600
import com.ekotak.teamtalk.presentation.theme.Red600
import java.util.Calendar

/** Barwy rodzajów — te same, co w panelu (`web/src/lib/meetings.ts`). */
fun meetingTypeColor(type: String): Color = when (type) {
    "board" -> Color(0xFFF59E0B)
    "employee" -> Color(0xFF38BDF8)
    "training" -> Color(0xFFA78BFA)
    "contractor" -> Color(0xFF34D399)
    "recruit_office" -> Color(0xFFF472B6)
    "recruit_installer" -> Color(0xFFFB923C)
    else -> Color(0xFF8AA0B6)
}

fun meetingTypeIcon(type: String): ImageVector = when (type) {
    "board" -> Icons.Filled.Groups
    "employee" -> Icons.Filled.Person
    "contractor" -> Icons.Filled.BusinessCenter
    "recruit_office" -> Icons.Filled.PersonSearch
    "recruit_installer" -> Icons.Filled.Engineering
    else -> Icons.Filled.School
}

fun meetingTypeHint(type: String): String = when (type) {
    "board" -> "Decyzje zarządu. Zakłada i prowadzi tylko zarząd."
    "employee" -> "Rozmowa z pracownikiem — poufna, widzą ją tylko uczestnicy."
    "contractor" -> "Z kimś spoza firmy — architektem, dostawcą. Kontrahent z kartoteki."
    "recruit_office" -> "Rozmowa z kandydatem do biura. Tylko zarząd; podsumowanie z oceną kandydata."
    "recruit_installer" -> "Rozmowa z kandydatem na montażystę. Tylko zarząd; podsumowanie z oceną kandydata."
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
        when (c.category) { "kontrahent" -> "Kontrahenci"; "inne" -> "Inne"; "kandydat" -> "Kandydaci"; else -> null },
    ).filter { it.isNotBlank() }.joinToString(" · ")

// ── v2: wielodniowe (D14) ─────────────────────────────────────────────────────

/** „1 dzień", „2 dni", „14 dni" — w polskim od 2 wzwyż zawsze „dni". */
fun daysLabel(n: Int): String = if (n == 1) "1 dzień" else "$n dni"

/** Minuty od północy → „9:00". */
fun formatClock(minuteOfDay: Int): String {
    val m = ((minuteOfDay % 1440) + 1440) % 1440
    return "%d:%02d".format(m / 60, m % 60)
}

fun minuteOfDay(ms: Long): Int = Calendar.getInstance().apply { timeInMillis = ms }.let {
    it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
}

/** „9:00–16:00" dla dnia zaczynającego się o [startMs] i trwającego [durationMin]. */
fun dayRangeLabel(startMs: Long, durationMin: Int): String {
    val from = minuteOfDay(startMs)
    return "${formatClock(from)}–${formatClock(from + durationMin)}"
}

/**
 * Termin do nagłówka karty i listy: jednodniowe „28.09.2026, 9:00 · 60 min",
 * wielodniowe „28.09.2026 · 2 dni · 9:00–16:00".
 */
fun meetingWhenLabel(startAt: String, durationMin: Int, dayCount: Int): String {
    if (dayCount <= 1) return "${formatDateTime(startAt) ?: ""} · $durationMin min"
    val ms = parseIsoMillis(startAt) ?: return "${daysLabel(dayCount)} · $durationMin min/dzień"
    return "${formatDate(startAt) ?: ""} · ${daysLabel(dayCount)} · ${dayRangeLabel(ms, durationMin)}"
}

// ── v2: ocena spotkania (D16) ────────────────────────────────────────────────

/** Progi 1:1 z panelem: czerwony < 50, bursztynowy 50–74, zielony ≥ 75. */
fun scoreColor(score: Int): Color = when {
    score < 50 -> Red600
    score < 75 -> Orange600
    else -> Green600
}

/** Mały znaczek z wynikiem — lista spotkań. */
@Composable
fun ScoreBadge(score: Int) {
    val c = scoreColor(score)
    Surface(
        shape = RoundedCornerShape(50),
        color = c.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, c.copy(alpha = 0.7f)),
    ) {
        Text(
            "$score",
            color = c,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

/** Półokrągły wskaźnik 0–100: szare tło łuku, kolorowy wycinek, liczba w środku. */
@Composable
fun ScoreGauge(score: Int, modifier: Modifier = Modifier) {
    val value = score.coerceIn(0, 100)
    val c = scoreColor(value)
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.width(180.dp).height(104.dp), contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.fillMaxWidth().height(104.dp)) {
            val stroke = 16.dp.toPx()
            val d = minOf(size.width, size.height * 2) - stroke
            val topLeft = Offset((size.width - d) / 2, stroke / 2)
            val arc = Size(d, d)
            drawArc(track, 180f, 180f, useCenter = false, topLeft = topLeft, size = arc, style = Stroke(stroke, cap = StrokeCap.Round))
            if (value > 0) {
                drawArc(c, 180f, 180f * value / 100f, useCenter = false, topLeft = topLeft, size = arc, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$value", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = c)
            Text("/ 100", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Karta „Ocena spotkania" — review i approved; widzą ją wszyscy, którzy widzą spotkanie (D17). */
@Composable
fun ScoreCard(score: Int, reason: String?, digressions: List<String>) {
    var open by remember { mutableStateOf(false) }
    MeetingCard("Ocena spotkania") {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ScoreGauge(score) }
        reason?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        val n = digressions.size
        val c = if (n == 0) Green600 else Orange600
        Surface(
            shape = RoundedCornerShape(50),
            color = c.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, c.copy(alpha = 0.6f)),
            modifier = Modifier.clickable(enabled = n > 0) { open = !open },
        ) {
            Text(
                "Dygresje: $n" + if (n > 0) (if (open) "  ▾" else "  ▸") else "",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
        if (open) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                digressions.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
