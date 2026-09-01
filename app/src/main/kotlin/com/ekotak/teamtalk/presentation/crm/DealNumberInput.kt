package com.ekotak.teamtalk.presentation.crm

/**
 * Wspólna obsługa pól liczbowych karty deala. Zakładka „Dane" i formularz
 * pozostałych pól przyjmują różne zestawy liczb, ale reguły wpisywania mają te
 * same, więc dzielą te trzy pomocniki zamiast trzymać po własnej kopii.
 */

/**
 * Czy tekst nadaje się do pola liczbowego. Pusty przechodzi (kasowanie treści),
 * „12a" nie — inaczej śmieć zostałby na ekranie, a do draftu poszedłby `null`
 * i użytkownik nie wiedziałby, że wpisana wartość nie zostanie zapisana.
 */
fun String.isNumericInput(): Boolean =
    isBlank() || all { it.isDigit() || it == '.' || it == ',' }

/** Przecinek dziesiętny (polska klawiatura) jest równoprawny z kropką. */
fun String.toDecimalOrNull(): Double? = replace(',', '.').toDoubleOrNull()

/** „12.0" → „12"; moce OZC rzadko mają część ułamkową, a zero razi w polu. */
fun Double.toPlainText(): String =
    if (this == toLong().toDouble()) toLong().toString() else toString()
