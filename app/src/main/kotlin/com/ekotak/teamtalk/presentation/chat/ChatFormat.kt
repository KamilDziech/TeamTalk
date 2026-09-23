package com.ekotak.teamtalk.presentation.chat

import androidx.compose.ui.graphics.Color
import com.ekotak.teamtalk.presentation.crm.parseIsoMillis
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private val DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("pl"))
private val SHORT_DATE = DateTimeFormatter.ofPattern("dd.MM.yy")
private val WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale("pl"))

private fun localDate(iso: String): LocalDate? =
    parseIsoMillis(iso)?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }

/** Godzina przy dymku. */
fun chatTime(iso: String): String {
    val millis = parseIsoMillis(iso) ?: return ""
    return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(TIME)
}

/** Znacznik w liście rozmów: godzina dziś, „wczoraj", dzień tygodnia, potem data. */
fun chatListTime(iso: String): String {
    val date = localDate(iso) ?: return ""
    val days = LocalDate.now().toEpochDay() - date.toEpochDay()
    return when {
        days == 0L -> chatTime(iso)
        days == 1L -> "wczoraj"
        days < 7L -> date.format(WEEKDAY)
        else -> date.format(SHORT_DATE)
    }
}

/** Plakietka rozdzielająca dni w rozmowie. */
fun chatDayChip(iso: String): String {
    val date = localDate(iso) ?: return ""
    val days = LocalDate.now().toEpochDay() - date.toEpochDay()
    return when (days) {
        0L -> "DZISIAJ"
        1L -> "WCZORAJ"
        else -> date.format(DAY).uppercase(Locale("pl"))
    }
}

/** Czy między wiadomościami zmienił się dzień — wtedy leci plakietka daty. */
fun chatDifferentDay(previous: String, current: String): Boolean =
    localDate(previous) != localDate(current)

fun chatDuration(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

/**
 * Stały kolor kółka z inicjałami, liczony z id — te same inicjały zawsze w tym
 * samym kolorze, więc oko rozpoznaje rozmowę zanim przeczyta nazwę.
 */
fun chatAvatarColor(seed: String): Color {
    var hash = 0
    for (char in seed) hash = (hash * 31 + char.code) % 360
    return Color.hsv(hash.toFloat(), 0.45f, 0.45f)
}
