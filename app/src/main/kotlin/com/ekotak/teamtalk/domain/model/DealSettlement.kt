package com.ekotak.teamtalk.domain.model

/**
 * Zatwierdzone (zamrożone) rozliczenie jednej instalacji deala —
 * `deal_settlements` w board360.
 *
 * Zatwierdzenie ZAMRAŻA wynik, żeby późniejsza zmiana punktacji w Warunkach
 * finansowych albo poprawka w audycie nie ruszały już rozliczonych deali.
 * Dlatego migawka niesie własną sumę punktów i własne rozbicie — czyta się ją
 * bez liczenia czegokolwiek od nowa.
 */
data class DealSettlement(
    val categoryId: String,
    val totalPoints: Double,
    /** Rozbicie z chwili zatwierdzenia jako surowy JSON (kontrakt z panelem). */
    val breakdownJson: String,
    /** Kto zatwierdził (`TeamMember.id`); `null` = zapis sprzed podpisywania autorem. */
    val approvedById: String?,
    /** ISO-8601 z API — formatowanie zostawiamy warstwie prezentacji. */
    val approvedAt: String,
    /**
     * Decyzja zapisana bez zasięgu i czekająca w kolejce. Zakładka pokazuje po
     * tym „czeka na wysyłkę": zatwierdzenie ma być widoczne od razu, ale nikt
     * nie może uznać, że panel już je widzi.
     */
    val pending: Boolean = false,
)
