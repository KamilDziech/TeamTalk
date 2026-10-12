package com.ekotak.teamtalk.domain.model

/** Postój > 10 min ze zgaszonym silnikiem — z adresem i punktem z systemu w 250 m. */
data class LongStop(
    val fromMillis: Long,
    val toMillis: Long,
    val minutes: Int,
    val lat: Double,
    val lng: Double,
    val open: Boolean,
    /** „Miejscowość, ulica numer"; null = geokoder nic nie znalazł albo nie zdążył. */
    val address: String?,
    /** Najlepsze trafienie: z terminem ±3 dni przed bez terminu, potem bliższe. */
    val match: LongStopMatch?,
    /** Pozostałe punkty w promieniu — „obok: …". */
    val nearby: List<LongStopMatch>,
)

data class LongStopMatch(
    val kind: String,
    val label: String,
    val detail: String?,
    val distanceM: Int,
    /** `montaz` | `audyt` | `serwis` | `przeglad` | `przeglad_gwarancyjny`; null = brak terminu. */
    val visitKind: String?,
    val visitAtMillis: Long?,
    val visitRef: String?,
)
