package com.ekotak.teamtalk.domain.leave

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Dni robocze i polskie dni ustawowo wolne — lustro
 * `api/src/modules/hr/domain/working-days.ts` i `holidays-pl.ts` z board360.
 *
 * Po co drugi raz to samo po stronie telefonu: człowiek musi zobaczyć „5 dni
 * roboczych" ZANIM wyśle wniosek, a bez zasięgu nie ma kogo o to zapytać.
 * Liczba, którą pokazujemy, i liczba, którą zapisze serwer, muszą się zgadzać
 * co do dnia — inaczej wniosek złożony w busie wróciłby z inną liczbą dni niż
 * ta, na którą człowiek się zgodził.
 *
 * Serwer liczy w UTC, my w dniach kalendarzowych ([LocalDate]) — to ta sama
 * arytmetyka, bo obie strony normalizują datę do doby, nie do chwili.
 */

/** Niedziela wielkanocna — algorytm Meeusa/Jonesa/Butchera (kalendarz gregoriański). */
fun easterSunday(year: Int): LocalDate {
    val a = year % 19
    val b = year / 100
    val c = year % 100
    val d = b / 4
    val e = b % 4
    val f = (b + 8) / 25
    val g = (b - f + 1) / 3
    val h = (19 * a + b - d - g + 15) % 30
    val i = c / 4
    val k = c % 4
    val l = (32 + 2 * e + 2 * i - h - k) % 7
    val m = (a + 11 * h + 22 * l) / 451
    val month = (h + l - 7 * m + 114) / 31 // 3 = marzec, 4 = kwiecień
    val day = ((h + l - 7 * m + 114) % 31) + 1
    return LocalDate.of(year, month, day)
}

private val holidayCache = HashMap<Int, Set<LocalDate>>()

/** Dni ustawowo wolne w danym roku. Memoizowane — kalendarz roku pyta o to często. */
@Synchronized
fun polishHolidays(year: Int): Set<LocalDate> = holidayCache.getOrPut(year) {
    val days = mutableSetOf(
        LocalDate.of(year, 1, 1),   // Nowy Rok
        LocalDate.of(year, 1, 6),   // Trzech Króli
        LocalDate.of(year, 5, 1),   // Święto Pracy
        LocalDate.of(year, 5, 3),   // Święto Konstytucji 3 Maja
        LocalDate.of(year, 8, 15),  // Wniebowzięcie NMP
        LocalDate.of(year, 11, 1),  // Wszystkich Świętych
        LocalDate.of(year, 11, 11), // Święto Niepodległości
        LocalDate.of(year, 12, 25), // Boże Narodzenie
        LocalDate.of(year, 12, 26), // drugi dzień świąt
    )
    val easter = easterSunday(year)
    days += easter                    // Wielkanoc
    days += easter.plusDays(1)        // Poniedziałek Wielkanocny
    days += easter.plusDays(49)       // Zielone Świątki
    days += easter.plusDays(60)       // Boże Ciało
    days
}

fun isPolishHoliday(day: LocalDate): Boolean = day in polishHolidays(day.year)

fun isWeekend(day: LocalDate): Boolean =
    day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY

/** Czy w tym dniu się pracuje — po tym kalendarz przygasza pola i liczy wnioski. */
fun isWorkingDay(day: LocalDate): Boolean = !isWeekend(day) && !isPolishHoliday(day)

/**
 * Dni robocze w przedziale [start, end] włącznie. Zwraca 0, gdy koniec wypada
 * przed początkiem albo gdy zakres to same weekendy i święta — takiego wniosku
 * API nie przyjmie (409), więc ekran musi to pokazać wcześniej.
 */
fun countWorkingDays(start: LocalDate, end: LocalDate): Int {
    if (end.isBefore(start)) return 0
    var count = 0
    var day = start
    while (!day.isAfter(end)) {
        if (isWorkingDay(day)) count++
        day = day.plusDays(1)
    }
    return count
}
