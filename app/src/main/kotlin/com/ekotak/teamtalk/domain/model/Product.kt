package com.ekotak.teamtalk.domain.model

/**
 * Model magazynu na telefonie — odpowiednik `web/src/app/app/inventory`.
 *
 * Trzy reguły przeniesione z panelu bez zmian, bo na nich stoi cały moduł:
 *  • `stock` to towar FIZYCZNIE na półce — zamówienie ani rezerwacja go nie ruszają,
 *  • wolne = stan − zarezerwowane (ujemne znaczy: obiecaliśmy więcej, niż mamy),
 *  • poziom stanu liczy się z progów `min` / `target` tej samej metodą co
 *    `stockLevel.ts`, żeby czerwień na telefonie znaczyła to samo co na monitorze.
 */

/** Dwa światy towaru: zapas magazynowy vs pozycja zamawiana pod konkretny deal. */
enum class StockType(val api: String) {
    STOCK("stock"),
    CLIENT("client");

    companion object {
        fun from(value: String?): StockType = entries.firstOrNull { it.api == value } ?: STOCK
    }
}

/**
 * Poziom stanu względem progów pozycji — 1:1 z `stockLevel.ts`.
 * `NOGOAL` = pozycja pod klienta albo z polityką „pod zamówienie": nie ma celu,
 * więc nie może być ani poniżej minimum, ani przekroczona.
 */
enum class StockLevel { LOW, OK, OVER, NOGOAL }

/** Polityka zapasu z panelu (tylko „Materiały ogólne"). */
enum class StockPolicy(val api: String, val label: String) {
    STEADY("steady", "Stały zapas"),
    SMALL("small", "Mały zapas"),
    ONORDER("onorder", "Pod klienta lub awaryjnie"),
    REPLACE("replace", "Zamiennik"),
    HIDDEN("hidden", "Ukryte");

    companion object {
        fun from(value: String?): StockPolicy? = entries.firstOrNull { it.api == value }
    }
}

/**
 * Typy instalacji — etykiety i kolejność wprost ze słownika panelu
 * (`installations.ts`). Podkategorie „Materiałów ogólnych" mają [parent],
 * więc filtr „Materiały ogólne" łapie też je.
 */
enum class InstallationType(val api: String, val label: String, val parent: String? = null) {
    HEAT_PUMP("heat_pump", "Pompy ciepła"),
    PV("pv", "Fotowoltaika"),
    UNDERFLOOR("underfloor", "Ogrzewanie podłogowe"),
    PLUMBING("plumbing", "Wod-kan"),
    RECUPERATION("recuperation", "Rekuperacja"),
    AC("ac", "Klimatyzacja"),
    GENERAL("general", "Materiały ogólne"),
    GENERAL_HYDRAULIKA("general_hydraulika", "Hydrauliczne", "general"),
    GENERAL_ELEKTRYKA("general_elektryka", "Elektryczne", "general"),
    GENERAL_BUDOWLANE("general_budowlane", "Budowlane", "general"),
    GENERAL_CHLODNICZE("general_chlodnicze", "Chłodnicze", "general");

    companion object {
        fun from(value: String?): InstallationType? = entries.firstOrNull { it.api == value }

        /** Etykieta do wiersza; nieznany kod pokazujemy surowo, zamiast go gubić. */
        fun labelOf(value: String?): String? =
            value?.let { from(it)?.label ?: it }
    }
}

/**
 * Pozycja magazynu w kształcie potrzebnym telefonowi. [reserved], [inTransit]
 * i [toOrder] doklejamy z rezerwacji i zapotrzebowania — w kartotece ich nie ma,
 * a bez nich cztery liczby z karty pozycji nie mają skąd się wziąć.
 */
data class Product(
    val id: String,
    val name: String,
    val code: String?,
    val installation: String?,
    val stockType: StockType,
    val dealId: String?,
    val stock: Double,
    val min: Double,
    val target: Double,
    val price: Double?,
    val producer: String?,
    val distributor: String?,
    val distributors: List<String>,
    val distributorPrices: List<PurchasePoint>,
    val packaging: String?,
    val notes: String?,
    val storageZone: String?,
    val storageShelf: String?,
    val stockPolicy: StockPolicy?,
    val imageUrl: String?,
    val leadTimeDays: Int?,
    /** Suma aktywnych rezerwacji tej kartoteki (znacznik 🔒 przy liczniku). */
    val reserved: Double = 0.0,
    /** Sztuki zamówione u dystrybutora, jeszcze niedostarczone (`ordered`). */
    val inTransit: Double = 0.0,
    /** Sztuki w koszyku zakupowym, jeszcze niezamówione (`to_order`). */
    val toOrder: Double = 0.0,
) {
    /** Ujemne = niedobór pod podpisane umowy. */
    val free: Double get() = stock - reserved

    /** Pozycja poza skalą min/maks: pod klienta albo „pod zamówienie". */
    val isNoGoal: Boolean
        get() = stockType == StockType.CLIENT || stockPolicy == StockPolicy.ONORDER

    val isHidden: Boolean get() = stockPolicy == StockPolicy.HIDDEN

    /**
     * Kubełek pozycji. Brak towaru sprawdzamy PRZED nadmiarem — przy dziwnej
     * konfiguracji (target < min) ważniejszy jest brak niż przekroczenie.
     * `target = 0` znaczy „poziom nieustawiony", więc nie ma czego przekroczyć.
     */
    val level: StockLevel
        get() = when {
            isNoGoal -> StockLevel.NOGOAL
            stock < min -> StockLevel.LOW
            target > 0 && stock > target -> StockLevel.OVER
            else -> StockLevel.OK
        }

    /** „Kontener 2 · A35"; sam obiekt, gdy półki nie wpisano; null = brak lokalizacji. */
    val locationLabel: String?
        get() = when {
            storageZone != null && storageShelf != null -> "$storageZone · $storageShelf"
            storageZone != null -> storageZone
            storageShelf != null -> storageShelf
            else -> null
        }

    val installationLabel: String? get() = InstallationType.labelOf(installation)

    /** Ile trzeba dokupić, żeby pokryć rezerwacje (po odjęciu tego, co jedzie). */
    val toBuy: Double get() = maxOf(0.0, reserved - stock - inTransit)
}

/** Punkt zakupu pozycji: nazwa dystrybutora + cena netto (null = ceny nie znamy). */
data class PurchasePoint(val name: String, val price: Double?)

/**
 * Rezerwacja materiału pod klienta wraz z pokryciem policzonym przez API.
 * [missing] to wprost odpowiedź na „kto zostanie bez materiału".
 */
data class StockReservation(
    val id: String,
    val dealId: String,
    val productId: String?,
    val itemName: String,
    val itemCode: String?,
    val clientLabel: String,
    val quantity: Double,
    val unit: String,
    val status: String,
    val source: String,
    /** Data montażu (ISO) — po niej ustawia się kolejka przydziału towaru. */
    val neededBy: String?,
    val note: String?,
    val covered: Double,
    val missing: Double,
    val productName: String?,
    /**
     * Zmiana stanu linii („wydane" / „zwolnione") czeka w kolejce — magazynier
     * kliknął ją bez zasięgu. Ustawia to wyłącznie karta deala; ekran Magazynu
     * czyta rezerwacje bez kolejki i zostawia tu `false`.
     */
    val pending: Boolean = false,
) {
    val isActive: Boolean get() = status == "active"

    /** Z umowy czy dołożone ręcznie przez magazyn. */
    val fromContract: Boolean get() = source == "contract"
}
