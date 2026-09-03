package com.ekotak.teamtalk.domain.search

import com.ekotak.teamtalk.domain.model.Client
import java.text.Normalizer

/**
 * Od tego podobieństwa nazw pytamy „czy chodzi o kogoś z kartoteki?" zamiast
 * od razu proponować nowy kontakt. 0.8 przepuszcza literówkę i przekręcone
 * nazwisko z dyktowania („Kowalsky", „Janusz" zamiast „Jan"), a odcina osoby
 * po prostu niepodobne.
 */
const val NAME_MATCH_THRESHOLD = 0.8

/** Krótsze hasła są zbyt niejednoznaczne, żeby cokolwiek po nich podpowiadać. */
private const val MIN_SUGGESTION_LENGTH = 3

/**
 * Porównanie odporne na ogonki — rozpoznawanie mowy pisze „Słoneczne", a
 * z klawiatury równie często przyjdzie „sloneczne". NFD rozkłada ą, ć, ę, ń, ó,
 * ś, ź i ż, ale „ł" jest osobną literą i trzeba je podmienić ręcznie.
 */
fun String.foldPolish(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace("\\p{Mn}".toRegex(), "")
        .lowercase()
        .replace('ł', 'l')

/** Rozdzielacze słów w haśle — spacje, interpunkcja i ozdobniki numerów telefonu. */
private val SEPARATORS = "[\\s,.;:/()+_-]+".toRegex()

private fun String.searchTokens(): List<String> =
    foldPolish().split(SEPARATORS).filter { it.isNotBlank() }

/**
 * Czy któreś z pól pasuje do hasła. Hasło idzie na słowa i KAŻDE z nich musi
 * się znaleźć w którymś polu — bez tego „Jan Kowalski" nie trafia w nikogo,
 * bo żadna pojedyncza kolumna nie zawiera imienia razem z nazwiskiem.
 * Puste hasło pasuje do wszystkiego.
 */
fun matchesAllTokens(query: String, vararg fields: String?): Boolean {
    val tokens = query.searchTokens()
    if (tokens.isEmpty()) return true
    val haystacks = fields.mapNotNull { it?.takeIf(String::isNotBlank)?.foldPolish() }
    return tokens.all { token -> haystacks.any { it.contains(token) } }
}

/** Dopasowanie pojedynczego pola — nazwa projektu, osoba w zespole. */
fun String.matchesQuery(query: String): Boolean = matchesAllTokens(query, this)

/** Pola karty klienta, po których szuka kartoteka i kreator zadania. */
fun Client.matchesQuery(query: String): Boolean = matchesAllTokens(
    query,
    firstName,
    lastName,
    phone,
    phone2,
    email,
    email2,
    address,
    postalCode,
    city,
    street,
    geoCity,
)

/**
 * Podobieństwo dwóch nazw w skali 0–1 (odległość Levenshteina po zdjęciu
 * ogonków). Liczymy też na słowach ustawionych alfabetycznie, bo „Kowalski
 * Jan" to ta sama osoba co „Jan Kowalski", a sama odległość znakowa dałaby
 * tu ledwie 0.3.
 */
fun nameSimilarity(first: String, second: String): Double {
    val left = first.searchTokens()
    val right = second.searchTokens()
    if (left.isEmpty() || right.isEmpty()) return 0.0
    return maxOf(
        ratio(left.joinToString(" "), right.joinToString(" ")),
        ratio(left.sorted().joinToString(" "), right.sorted().joinToString(" ")),
    )
}

/** Jak bardzo wpisane hasło przypomina osobę z kartoteki. */
fun Client.nameSimilarityTo(probe: String): Double = nameSimilarity(probe, displayName)

/**
 * Klienci „prawie tacy" jak wpisane hasła — do pytania, czy nie chodzi
 * przypadkiem o kogoś, kto już jest w kartotece. [exclude] to wpisy pokazane
 * już jako trafienia wyszukiwarki; nie ma sensu pytać o to, co widać obok.
 */
fun List<Client>.similarTo(
    probes: List<String>,
    exclude: Set<String> = emptySet(),
    limit: Int = 3,
): List<Client> {
    val usable = probes.filter { it.trim().foldPolish().length >= MIN_SUGGESTION_LENGTH }
    if (usable.isEmpty()) return emptyList()
    return asSequence()
        .filter { it.id !in exclude }
        .map { client -> client to usable.maxOf { client.nameSimilarityTo(it) } }
        .filter { (_, score) -> score >= NAME_MATCH_THRESHOLD }
        .sortedByDescending { (_, score) -> score }
        .take(limit)
        .map { (client, _) -> client }
        .toList()
}

private fun ratio(first: String, second: String): Double {
    val longest = maxOf(first.length, second.length)
    if (longest == 0) return 1.0
    return 1.0 - levenshtein(first, second).toDouble() / longest
}

/** Odległość edycyjna na dwóch wierszach macierzy — kartoteka bywa duża. */
private fun levenshtein(first: String, second: String): Int {
    var previous = IntArray(second.length + 1) { it }
    var current = IntArray(second.length + 1)
    for (i in 1..first.length) {
        current[0] = i
        for (j in 1..second.length) {
            val substitution = previous[j - 1] + if (first[i - 1] == second[j - 1]) 0 else 1
            current[j] = minOf(current[j - 1] + 1, previous[j] + 1, substitution)
        }
        val swap = previous
        previous = current
        current = swap
    }
    return previous[second.length]
}
