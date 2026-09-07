package com.ekotak.teamtalk.presentation.email

import androidx.compose.ui.graphics.Color
import com.ekotak.teamtalk.domain.model.EmailFolder
import com.ekotak.teamtalk.presentation.crm.parseIsoMillis
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Formatowanie wierszy poczty. Osobny plik, bo te reguły są specyficzne dla
 * skrzynki i nie mają czego szukać we wspólnym `CrmFormat`.
 */

private val plLocale = Locale("pl", "PL")
private val timeFormat = ThreadLocal.withInitial { SimpleDateFormat("HH:mm", plLocale) }
private val dayMonthFormat = ThreadLocal.withInitial { SimpleDateFormat("d MMM", plLocale) }
private val fullFormat = ThreadLocal.withInitial { SimpleDateFormat("dd.MM.yyyy", plLocale) }
private val messageFormat =
    ThreadLocal.withInitial { SimpleDateFormat("d MMM, HH:mm", plLocale) }

/**
 * Data na wierszu listy, jak w Gmailu: dziś sama godzina, w tym roku dzień
 * i miesiąc, starsze — pełna data. Wiersz ma ~40 dp szerokości na datę, więc
 * pełny format wszędzie zjadłby temat wiadomości.
 */
fun formatThreadDate(iso: String?): String {
    val millis = parseIsoMillis(iso) ?: return "—"
    val date = Date(millis)
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { time = date }
    val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val sameDay = sameYear && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    return when {
        sameDay -> timeFormat.get()!!.format(date)
        sameYear -> dayMonthFormat.get()!!.format(date)
        else -> fullFormat.get()!!.format(date)
    }
}

/** Data nad wiadomością w otwartym wątku — zawsze z godziną. */
fun formatMessageDate(iso: String?): String {
    val millis = parseIsoMillis(iso) ?: return "—"
    return messageFormat.get()!!.format(Date(millis))
}

/** Inicjały do kółka awatara: „Ewa Kowalska" → „EK", „biuro@x.pl" → „BI". */
fun emailInitials(name: String?, address: String): String {
    val source = name?.takeIf { it.isNotBlank() } ?: address
    val parts = source.trim().split(Regex("\\s+"))
    val initials = if (parts.size > 1) {
        "${parts[0].firstOrNull() ?: ' '}${parts[1].firstOrNull() ?: ' '}"
    } else {
        source.take(2)
    }
    return initials.trim().uppercase(plLocale).ifEmpty { "?" }
}

/**
 * Kolor kółka awatara — stały dla adresu, żeby ten sam klient wyglądał tak samo
 * przy każdym wejściu. Paleta jest przyciemniona pod biały tekst inicjałów.
 */
fun avatarColor(address: String): Color {
    val palette = listOf(
        Color(0xFF2FA84F), Color(0xFF2563EB), Color(0xFFB4680A),
        Color(0xFFC5343A), Color(0xFF7C3AED), Color(0xFF0E7490),
    )
    val index = (address.lowercase(plLocale).hashCode().toUInt() % palette.size.toUInt()).toInt()
    return palette[index]
}

/** Rozmiar załącznika w jednostkach, które człowiek czyta bez liczenia zer. */
fun formatFileSize(bytes: Long): String = when {
    bytes <= 0 -> "—"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(plLocale, "%.0f kB", bytes / 1024.0)
    else -> String.format(plLocale, "%.1f MB", bytes / (1024.0 * 1024.0))
}

/** Ikona folderu w szufladzie — te same znaki co w sidebarze panelu. */
fun folderIcon(folder: EmailFolder): String = when (folder) {
    EmailFolder.INBOX -> "📥"
    EmailFolder.SENT -> "➤"
    EmailFolder.DRAFTS -> "📝"
    EmailFolder.ARCHIVE -> "🗄"
    EmailFolder.SPAM -> "⚠"
    EmailFolder.TRASH -> "🗑"
}

/** Temat odpowiedzi / przekazania bez piętrzenia przedrostków „Re: Re: Re:". */
fun replySubject(subject: String): String =
    if (subject.startsWith("Re:", ignoreCase = true)) subject else "Re: $subject"

fun forwardSubject(subject: String): String =
    if (subject.startsWith("Fwd:", ignoreCase = true)) subject else "Fwd: $subject"

/** Kolor etykiety z panelu („#f0b429") na kolor Compose; wadliwy = szary. */
fun labelColor(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrElse { Color(0xFF9FB0C3) }
