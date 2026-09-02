package com.ekotak.teamtalk.domain.model

/**
 * Rodzaj punktu = źródło danych, a zarazem widok mapy, do którego należy.
 * „Flota" nie ma punktów (brak realnego GPS pojazdów), więc nie jest rodzajem
 * punktu tylko widokiem — patrz `MapView` w warstwie prezentacji.
 */
enum class MapKind(val wire: String, val label: String) {
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
) {
    val hasGeo: Boolean get() = lat != null && lng != null
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
