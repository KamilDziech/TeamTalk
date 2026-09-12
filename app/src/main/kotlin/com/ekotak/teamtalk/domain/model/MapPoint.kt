package com.ekotak.teamtalk.domain.model

/**
 * Rodzaj punktu = źródło danych, a zarazem widok mapy, do którego należy.
 * „Flota" to ostatnie pozycje lokalizatorów GPS z modułu Zasoby — inne źródło
 * niż deale i zlecenia, ale punkt jak każdy inny: te same filtry, te same
 * klastry, ta sama lista „bez lokalizacji".
 */
enum class MapKind(val wire: String, val label: String) {
    FLEET("fleet", "Flota"),
    CURRENT("current", "Klienci bieżący"),
    FINISHED("finished", "Klienci zakończeni"),
    SERVICE("service", "Serwisy"),
    INSPECTION("inspection", "Przeglądy");

    companion object {
        fun fromWire(value: String?): MapKind =
            entries.firstOrNull { it.wire == value } ?: CURRENT
    }
}

/**
 * Prezentacja punktu policzona przy składaniu migawki: kolor, etykieta,
 * kolejność w legendzie i litera w pinie. Panel liczy to samo serwerowo
 * (`web/src/app/app/map/page.tsx`), dzięki czemu ekran rysuje markery i chipy
 * identycznie, niezależnie od źródła (deal / zlecenie / karta gwarancyjna).
 * Litera jest po to, żeby punkty dało się rozróżnić bez koloru (daltonizm).
 */
data class MapBadge(
    val key: String,
    val label: String,
    /** ARGB — paleta z `mapColors.ts`, patrz [MapPalette]. */
    val colorArgb: Long,
    val order: Int,
    val letter: String,
)

/**
 * Ujednolicony punkt mapy: deal (klient), zlecenie serwisowe albo karta
 * gwarancyjna. Punkt bez współrzędnych (`lat`/`lng` = null) nie trafia na mapę,
 * tylko na listę „bez lokalizacji" — adres czeka na walidację w kartotece.
 */
data class MapPoint(
    val id: String,
    val kind: MapKind,
    val lat: Double?,
    val lng: Double?,
    val name: String,
    val city: String?,
    /** Do akcji „Nawiguj" (adres czytelny dla map) i „Zadzwoń". */
    val address: String?,
    val phone: String?,
    /** Nazwy instalacji/technologii (kategorie główne) — szukane i filtrowane. */
    val installs: List<String>,
    val ownerId: String?,
    val ownerLabel: String?,
    val stageOwnerId: String?,
    val stageOwnerLabel: String?,
    val technicianId: String?,
    val technicianLabel: String?,
    val badge: MapBadge,
    /** Karta deala do otwarcia z dymka; null = punkt bez deala (np. karta gwarancyjna). */
    val dealId: String?,
    val clientId: String?,
    /** Wypełnione wyłącznie dla [MapKind.FLEET] — surowe dane z lokalizatora. */
    val fleet: FleetInfo? = null,
) {
    val hasGeo: Boolean get() = lat != null && lng != null
}

/**
 * Stan pojazdu na mapie Floty — 1:1 z `FLEET_STATUS_*` w `mapColors.ts`.
 * Kolejność steruje chipami (legenda + filtr) i jest ułożona wg tego, co
 * planujący dzień chce zobaczyć najpierw: co jedzie, co stoi na biegu jałowym,
 * co zaparkowane, co milczy.
 *
 * „Brak sygnału" świadomie NIE jest czerwony — auto poza zasięgiem to
 * codzienność, nie awaria. Czerwień zostaje w tym module na przekroczone SLA.
 */
enum class FleetStatus(val wire: String, val label: String, val colorArgb: Long) {
    MOVING("moving", "W ruchu", 0xFF44D62C),
    IDLING("idling", "Postój z zapłonem", 0xFFFFA657),
    PARKED("parked", "Zaparkowany", 0xFF4AA3FF),
    STALE("stale", "Brak sygnału", 0xFF8B949E),
}

/**
 * Po ilu minutach ciszy uznajemy pozycję za nieaktualną.
 *
 * Tracker w ruchu odzywa się co kilkadziesiąt sekund, na postoju co kilka minut.
 * Pół godziny to więc już nie „rzadziej nadaje", tylko martwa strefa, wyłączone
 * zasilanie albo wyjęte urządzenie — i pin, któremu nie wolno ufać przy
 * planowaniu dojazdu. Punkt zostaje na mapie (ostatnie znane miejsce bywa dobrą
 * wskazówką), ale dostaje szary status zamiast udawać bieżącą pozycję.
 */
const val FLEET_STALE_AFTER_MIN = 30

/** Prędkość poniżej tego progu to szum GPS na postoju, nie jazda. */
const val FLEET_MOVING_KMH = 3.0

/**
 * Dane z lokalizatora trzymane SUROWO, a nie jako gotowa etykieta.
 *
 * Panel liczy status raz, przy renderze strony — telefon ogląda tę samą migawkę
 * także godzinę później, bez zasięgu, więc „W ruchu" sprzed dwóch godzin byłoby
 * zwykłym kłamstwem. Stąd [statusAt]: wiek liczymy zawsze od zegara urządzenia
 * do TERAZ, a nie od momentu pobrania.
 */
data class FleetInfo(
    /** Znacznik czasu URZĄDZENIA (epoch ms); null = tracker nic jeszcze nie nadał. */
    val occurredAt: Long?,
    val speedKmh: Double?,
    val ignition: Boolean?,
    /** Czy w karcie auta jest IMEI — rozstrzyga „tracker milczy" vs „nie ma trackera". */
    val hasTracker: Boolean,
) {
    fun statusAt(now: Long): FleetStatus {
        val at = occurredAt ?: return FleetStatus.STALE
        if (ageMinutesAt(now, at) > FLEET_STALE_AFTER_MIN) return FleetStatus.STALE
        if ((speedKmh ?: 0.0) > FLEET_MOVING_KMH) return FleetStatus.MOVING
        // Zapłon rozstrzyga „stoi na biegu jałowym" vs „zaparkowane". Gdy DIN1 nie
        // jest podpięty, `ignition` jest null i auto liczymy jako zaparkowane —
        // lepiej nie twierdzić, że silnik pracuje, skoro nie ma skąd tego wiedzieć.
        if (ignition == true) return FleetStatus.IDLING
        return FleetStatus.PARKED
    }

    /** Wiek pozycji w minutach; null = brak jakiejkolwiek pozycji. */
    fun ageMinutesAt(now: Long): Long? = occurredAt?.let { ageMinutesAt(now, it) }

    private fun ageMinutesAt(now: Long, at: Long): Long =
        ((now - at).coerceAtLeast(0L) + 30_000L) / 60_000L
}

/**
 * Badge punktu floty — kolor i litera wprost ze statusu, jak w panelu.
 *
 * Auto, do którego karty nikt nie wpisał IMEI, dostaje własną etykietę zamiast
 * „Brak sygnału": pierwsze załatwia się wpisaniem numeru, drugie sprawdzeniem
 * bezpiecznika albo zasięgu, a z mapy widać tylko tyle, ile ona powie.
 */
fun fleetBadge(info: FleetInfo, now: Long): MapBadge {
    if (info.occurredAt == null && !info.hasTracker) {
        return MapBadge(
            key = "no_tracker",
            label = "Bez lokalizatora",
            colorArgb = 0xFF6E7681,
            order = FleetStatus.entries.size,
            letter = "B",
        )
    }
    val status = info.statusAt(now)
    return MapBadge(
        key = status.wire,
        label = status.label,
        colorArgb = status.colorArgb,
        order = status.ordinal,
        letter = status.label.take(1).uppercase(),
    )
}

/**
 * Migawka mapy: wszystkie punkty wszystkich widoków plus moment pobrania.
 * Widoki są filtrem po `kind`, nie osobnym zapytaniem — panel też pobiera
 * komplet raz i przełącza widoki lokalnie.
 */
data class MapSnapshot(
    val points: List<MapPoint> = emptyList(),
    /** `System.currentTimeMillis()` ostatniego udanego pobrania; null = nigdy. */
    val syncedAt: Long? = null,
)

/** Miejscowość z geokodera — środek filtra promienia. */
data class PlaceSuggestion(
    val label: String,
    val lat: Double,
    val lng: Double,
)

/**
 * Odległość po wielkim okręgu (haversine) w kilometrach. Ten sam wzór co
 * w panelu — filtr promienia musi dawać po obu stronach ten sam wynik.
 */
fun haversineKm(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(bLat - aLat)
    val dLng = Math.toRadians(bLng - aLng)
    val s = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(Math.toRadians(aLat)) * Math.cos(Math.toRadians(bLat)) *
        Math.sin(dLng / 2) * Math.sin(dLng / 2)
    return 2 * r * Math.asin(Math.sqrt(s))
}
