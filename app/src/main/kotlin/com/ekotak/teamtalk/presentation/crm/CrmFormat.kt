package com.ekotak.teamtalk.presentation.crm

import androidx.compose.ui.graphics.Color
import com.ekotak.teamtalk.domain.model.DealActivity
import com.ekotak.teamtalk.domain.model.DealStage
import com.ekotak.teamtalk.domain.model.FunnelGroup
import com.ekotak.teamtalk.presentation.theme.EkotakGreen
import com.ekotak.teamtalk.presentation.theme.OkGreen
import com.ekotak.teamtalk.presentation.theme.Orange600
import com.ekotak.teamtalk.presentation.theme.Red600
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Formatowanie i kolory kart lejka. Daty z board360 przychodzą jako ISO 8601 w
 * UTC — parser jest ten sam co w `RelativeTime`, ale tu potrzebujemy też
 * kalendarzowych dat i liczby dni, więc trzymamy własny komplet pomocników.
 */

private val isoParser = ThreadLocal.withInitial {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}

private val dayFormat = ThreadLocal.withInitial {
    SimpleDateFormat("dd.MM.yyyy", Locale("pl", "PL")).apply { timeZone = TimeZone.getDefault() }
}

private val dayTimeFormat = ThreadLocal.withInitial {
    SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale("pl", "PL")).apply {
        timeZone = TimeZone.getDefault()
    }
}

/** ISO 8601 → millis, albo `null` gdy pole puste lub w nieznanym formacie. */
fun parseIsoMillis(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    return try {
        isoParser.get()!!.parse(iso)?.time
    } catch (_: Exception) {
        null
    }
}

/** ISO 8601 → „31.08.2026" (czas lokalny). Puste pole daje `null`. */
fun formatDate(iso: String?): String? = parseIsoMillis(iso)?.let { dayFormat.get()!!.format(Date(it)) }

/** ISO 8601 → „31.08.2026, 14:30" (czas lokalny). */
fun formatDateTime(iso: String?): String? =
    parseIsoMillis(iso)?.let { dayTimeFormat.get()!!.format(Date(it)) }

/** Millis → „31.08.2026, 14:30" (czas lokalny) — dla pól formularza. */
fun formatMillisDateTime(millis: Long): String = dayTimeFormat.get()!!.format(Date(millis))

/** Liczba pełnych dni od podanej daty do teraz (ujemne = data w przyszłości). */
fun daysSince(iso: String?): Long? = parseIsoMillis(iso)?.let {
    TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - it)
}

/** Etykieta licznika czasu w etapie: „dziś", „3 dni", „2 tyg.". */
fun stageAgeLabel(stageEnteredAt: String?): String? {
    val days = daysSince(stageEnteredAt) ?: return null
    if (days < 0) return null
    return when {
        days == 0L -> "dziś"
        days == 1L -> "1 dzień"
        days < 14L -> "$days dni"
        days < 60L -> "${days / 7} tyg."
        else -> "${days / 30} mies."
    }
}

/** Czy termin następnego kontaktu już minął (kryterium `overdue` z API). */
fun isOverdue(nextContactAt: String?): Boolean {
    val ms = parseIsoMillis(nextContactAt) ?: return false
    return ms < System.currentTimeMillis()
}

/**
 * Kolor etapu. board360 nie definiuje kolorów per etap (kolumny Kanbana są
 * neutralne), więc kolorujemy fazami lejka, sięgając po barwy kafelków pulpitu:
 * BOW = „Klienci", Sprzedaż = „CRM", faza montażowa = „Montaże".
 */
fun stageColor(stage: DealStage): Color = when (stage) {
    DealStage.LOST -> Red600
    DealStage.ZAKONCZONY -> OkGreen
    DealStage.ON_HOLD -> Orange600
    else -> when (funnelGroupOf(stage)) {
        FunnelGroup.BOW -> Color(0xFF38BDF8)
        FunnelGroup.SPRZEDAZ -> EkotakGreen
        FunnelGroup.MONTAZ -> Color(0xFF4F8CFF)
        FunnelGroup.PO_MONTAZU -> OkGreen
        null -> Orange600
    }
}

/** Faza lejka, do której należy etap (`null` dla `lost`/`zakonczony`). */
fun funnelGroupOf(stage: DealStage): FunnelGroup? =
    FunnelGroup.entries.firstOrNull { stage in it.stages }

/**
 * Opis wpisu historii — odpowiednik `describeActivity` z panelu (DealDrawer).
 * Nieznane akcje pokazujemy surowo: lepiej techniczna nazwa niż pusty wiersz.
 */
fun activityLabel(activity: DealActivity): String = when (activity.action) {
    "stage_change" -> buildString {
        append("Etap: ")
        append(activity.fromStage?.label ?: "?")
        append(" → ")
        append(activity.toStage?.label ?: "?")
        activity.lostReason?.let { append(" · powód: ").append(it) }
        activity.note?.let { append(" · ").append(it) }
    }
    "meeting_change" -> "Spotkanie: zaktualizowano"
    "lead_intake" -> "Przyjęcie leada"
    "contract_signed" -> "Podpisanie umowy"
    "split" -> "Podział na instalacje"
    "merge" -> "Scalenie kart"
    else -> activity.action
}
