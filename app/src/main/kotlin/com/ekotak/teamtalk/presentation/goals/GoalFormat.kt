package com.ekotak.teamtalk.presentation.goals

import androidx.compose.ui.graphics.Color
import com.ekotak.teamtalk.domain.model.GoalStatus
import com.ekotak.teamtalk.domain.model.GoalUnit
import com.ekotak.teamtalk.presentation.theme.EkotakGreen
import com.ekotak.teamtalk.presentation.theme.Orange600
import com.ekotak.teamtalk.presentation.theme.Red600
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Formatowanie modułu Cele — dokładnie to samo, co `web/src/lib/goals.ts`:
 * kwoty bez groszy, procenty z jednym miejscem, sztuki i punkty całkowite.
 * Jedna liczba ma wyglądać tak samo na telefonie i w panelu.
 */

private val plFormat: NumberFormat = NumberFormat.getIntegerInstance(Locale("pl", "PL"))

fun goalValueText(value: Double, unit: GoalUnit): String = when (unit) {
    GoalUnit.PLN -> "${plFormat.format(value.roundToLong())} zł"
    GoalUnit.PCT -> "${(Math.round(value * 10) / 10.0).toString().removeSuffix(".0")}%"
    GoalUnit.PKT -> "${plFormat.format(value.roundToLong())} pkt"
    GoalUnit.SZT -> "${plFormat.format(value.roundToLong())} szt."
}

/** Kolor statusu — zieleń marki dla „na kursie", pomarańcz i czerwień dla reszty. */
fun goalStatusColor(status: GoalStatus): Color = when (status) {
    GoalStatus.DONE, GoalStatus.OK -> EkotakGreen
    GoalStatus.WARN -> Orange600
    GoalStatus.BAD -> Red600
}

private val ROMAN = listOf("I", "II", "III", "IV")
private val MONTHS = listOf(
    "styczeń", "luty", "marzec", "kwiecień", "maj", "czerwiec",
    "lipiec", "sierpień", "wrzesień", "październik", "listopad", "grudzień",
)

/** Etykieta okresu po ludzku: `2026-Q3` → „III kw. 2026". */
fun periodLabel(key: String): String {
    Regex("""^(\d{4})-Q([1-4])$""").find(key)?.let { m ->
        return "${ROMAN[m.groupValues[2].toInt() - 1]} kw. ${m.groupValues[1]}"
    }
    Regex("""^(\d{4})-(\d{2})$""").find(key)?.let { m ->
        return "${MONTHS[m.groupValues[2].toInt() - 1]} ${m.groupValues[1]}"
    }
    if (Regex("""^\d{4}$""").matches(key)) return "rok $key"
    return "okres własny"
}

val DEPARTMENT_LABELS: Map<String, String> = mapOf(
    "biuro" to "Biuro",
    "serwis" to "Serwis",
    "montaz" to "Montaż",
    "pozostali" to "Pozostali",
)
