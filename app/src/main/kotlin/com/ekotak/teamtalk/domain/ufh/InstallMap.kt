package com.ekotak.teamtalk.domain.ufh

import com.ekotak.teamtalk.domain.model.InstallationType

/**
 * Nazwa węzła katalogu → typ instalacji Magazynu. Port `install-map.ts`.
 *
 * Drzewo katalogu nazywa węzły PO SWOJEMU („Powietrzne pompy ciepła"), a Magazyn
 * ma jeden typ na całą rodzinę („Pompy ciepła"), dlatego obok dopasowania po
 * etykiecie idą wzorce celujące w rdzeń nazwy. Dopasowanie dokładne ma
 * pierwszeństwo.
 */
private val ALIASES: List<Pair<InstallationType, Regex>> = listOf(
    InstallationType.HEAT_PUMP to Regex("pomp[ay] ciepła"),
    InstallationType.UNDERFLOOR to Regex("ogrzewanie podłogowe"),
    InstallationType.PV to Regex("fotowoltaik"),
    InstallationType.AC to Regex("klimatyzacj"),
    InstallationType.RECUPERATION to Regex("rekuperacj"),
    InstallationType.PLUMBING to Regex("wod-kan"),
)

fun installationForCategory(name: String): InstallationType? {
    val n = name.trim().lowercase()
    val exact = InstallationType.entries.firstOrNull { it.label.lowercase() == n }
    if (exact != null) return exact
    return ALIASES.firstOrNull { (_, re) -> re.containsMatchIn(n) }?.first
}

/**
 * Czy dla tego węzła mamy w ogóle rozpisaną formułę ceny. Dziś wyłącznie
 * ogrzewanie podłogowe — przy innych instalacjach oferta pokazuje zakres bez
 * kwot, zamiast zmyślać ceny.
 */
fun hasPriceFormula(categoryName: String): Boolean =
    installationForCategory(categoryName) == InstallationType.UNDERFLOOR
