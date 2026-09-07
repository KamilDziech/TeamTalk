package com.ekotak.teamtalk.domain.model

/**
 * SLOTY RZUTÓW „PROJEKT DOMU" — mobilny odpowiednik `floor-plan-slots.ts`.
 *
 * Slot dopięty jest do NAZWY pliku dyskretnym prefiksem `[[<klucz>]] ` — bez
 * żadnej kolumny w backendzie. Parsujemy go przy wyświetlaniu i czyścimy nazwę
 * pokazywaną człowiekowi. Konwencja musi zgadzać się z panelem co do znaku:
 * audyt OP szuka rzutów kondygnacji dokładnie po tym prefiksie, więc plik
 * wgrany z telefonu ma się pojawić w audycie zrobionym w web i odwrotnie.
 */

private val SLOT_RE = Regex("^\\[\\[([a-z0-9]+)]]\\s*")

/** Klucz slotu z nazwy pliku (`[[parter]] rzut.jpg` → `parter`). */
fun slotOfName(name: String): String? = SLOT_RE.find(name)?.groupValues?.get(1)

/** Nazwa pliku bez prefiksu slotu. */
fun stripSlot(name: String): String = name.replace(SLOT_RE, "")

/** Nazwa pliku z dopiętym slotem — jedyny sposób, w jaki slot powstaje. */
fun withSlot(slotKey: String, name: String): String = "[[$slotKey]] ${stripSlot(name)}"

private val SLOT_LABEL = mapOf(
    "piwnica" to "Rzut piwnica",
    "parter" to "Rzut parter",
    "pietro" to "Rzut piętro",
    "poddasze" to "Rzut poddasze",
    "garaz" to "Rzut garaż",
    "przekroj" to "Przekrój",
)

/** Etykieta slotu; pośrednie piętra przy >3 kondygnacjach → „Rzut N. piętro". */
fun slotLabel(key: String): String {
    SLOT_LABEL[key]?.let { return it }
    Regex("^pietro(\\d+)$").find(key)?.let { return "Rzut ${it.groupValues[1]}. piętro" }
    return key
}

/** Maks. liczba plików w slocie: „Przekrój" = 3, każdy rzut kondygnacji = 1. */
fun slotLimit(key: String): Int = if (key == "przekroj") 3 else 1

/** Slot „Przekrój" nie jest rzutem kondygnacji — nie da się go przygotować. */
const val SLOT_SECTION = "przekroj"

/** Pojedynczy slot rzutu do pokazania w sekcji „Projekt domu". */
data class PlanSlot(val key: String, val label: String)

/**
 * Reguła slotów wg danych budynku (mirror zakładki „Dane"):
 *  • piwnica zaznaczona → „Rzut piwnica",
 *  • 1 kondygnacja (parterowy) → tylko „Rzut parter",
 *  • 2 kondygnacje → „Rzut parter" + „Rzut poddasze",
 *  • 3 kondygnacje → parter + piętro + poddasze,
 *  • >3 → parter + kolejne piętra numerowane + poddasze,
 *  • garaż zaznaczony → „Rzut garaż",
 *  • zawsze „Przekrój" na końcu.
 *
 * Piwnica/garaż biorą się z danych budynku, a gdy tam ich nie ustawiono —
 * ze zgłoszenia z leadowni.
 *
 * Sloty, które mają już wgrane pliki, są DOŁĄCZANE nawet gdy wypadły z reguły:
 * zmiana liczby kondygnacji w „Danych" nie może schować rzutu, który ktoś
 * wgrał wcześniej.
 */
fun projektSlots(
    building: DealBuildingData?,
    leadBasement: Boolean?,
    leadGarage: Boolean?,
    documents: List<DealDocument>,
): List<PlanSlot> {
    val floors = building?.floors
    val hasBasement = building?.heatedBasement ?: leadBasement ?: false
    val hasGarage = building?.heatedGarage ?: leadGarage ?: false

    val keys = ArrayList<String>()
    if (hasBasement) keys += "piwnica"
    when {
        // Brak danych o kondygnacjach → bezpieczny domyślny zestaw, jak w panelu.
        floors == null -> keys += listOf("parter", "pietro", "poddasze")
        floors <= 1 -> keys += "parter"
        floors == 2 -> keys += listOf("parter", "poddasze")
        else -> {
            keys += "parter"
            val mid = floors - 2 // piętra pośrednie między parterem a poddaszem
            if (mid == 1) keys += "pietro" else for (i in 1..mid) keys += "pietro$i"
            keys += "poddasze"
        }
    }
    if (hasGarage) keys += "garaz"
    keys += SLOT_SECTION

    // Nie gub plików: sloty z już wgranymi rzutami dołącz przed „Przekrojem".
    val used = documents.mapNotNull { it.slot }.toSet()
    for (key in used) if (key !in keys) keys.add(keys.size - 1, key)

    return keys.map { PlanSlot(it, slotLabel(it)) }
}
