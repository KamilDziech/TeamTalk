package com.ekotak.teamtalk.domain.model

/**
 * Polisa ubezpieczeniowa auta — widok kierowcy (odczyt). Źródłem prawdy jest
 * panel (karta pojazdu → „Ubezpieczenie"); tu bez cache, jak cała historia trasy.
 */
data class InsurancePolicy(
    val id: String,
    val insurer: String,
    val policyNumber: String?,
    val coverage: List<String>,
    val validFromMillis: Long,
    /** Ostatni dzień ochrony (północ UTC tego dnia). */
    val validToMillis: Long,
    val agentName: String?,
    val agentContact: String?,
    val notes: String?,
) {
    val coverageLabel: String
        get() = coverage.joinToString(" + ") { COVERAGE_LABEL[it] ?: it }.ifBlank { "polisa" }

    /** Pierwszy numer telefonu z pola kontaktu — do przycisku „Zadzwoń". */
    val phone: String?
        get() = agentContact?.let { PHONE.find(it)?.value?.replace(" ", "")?.replace("-", "") }

    fun isExpired(nowMillis: Long): Boolean = validToMillis + DAY_MS <= nowMillis
    fun isUpcoming(nowMillis: Long): Boolean = validFromMillis > nowMillis

    companion object {
        private const val DAY_MS = 24L * 3_600_000L
        private val PHONE = Regex("""\+?\d[\d \-]{7,}\d""")
        private val COVERAGE_LABEL = mapOf(
            "oc" to "OC",
            "ac" to "AC",
            "nnw" to "NNW",
            "assistance" to "Assistance",
            "szyby" to "Szyby",
            "inne" to "Inne",
        )
    }
}

/**
 * Polisa „na teraz": obowiązująca z najpóźniejszym końcem, a gdy żadna nie
 * obowiązuje — najbliższa przyszła, a na końcu ostatnia wygasła (żeby kierowca
 * zobaczył, że ochrona się skończyła, zamiast pustego miejsca).
 */
fun List<InsurancePolicy>.current(nowMillis: Long): InsurancePolicy? =
    filter { !it.isExpired(nowMillis) && !it.isUpcoming(nowMillis) }.maxByOrNull { it.validToMillis }
        ?: filter { it.isUpcoming(nowMillis) }.minByOrNull { it.validFromMillis }
        ?: maxByOrNull { it.validToMillis }
