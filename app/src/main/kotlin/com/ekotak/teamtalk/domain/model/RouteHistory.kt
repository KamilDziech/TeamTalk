package com.ekotak.teamtalk.domain.model

/**
 * Historia trasy pojazdu — to, o co pyta człowiek patrzący na mapę: o której
 * wyjechał, gdzie i jak długo stał, gdzie się rozpędził.
 *
 * Wszystkie reguły („co jest postojem", „gdzie zaczyna się kurs", „jak grupują
 * się przekroczenia") liczy SERWER i telefon ich nie powtarza — inaczej ten sam
 * dzień miałby inną liczbę postojów w panelu niż w aplikacji, a to jest rzecz,
 * na którą ktoś się powołuje w rozmowie z kierowcą.
 *
 * Czasy trzymamy w milisekundach epoch (UTC); na ekran idą przez formatowanie
 * w strefie telefonu, tak samo jak wszędzie w CRM-ie.
 */
data class RouteHistory(
    val fromMillis: Long,
    val toMillis: Long,
    /** Próg prędkości obowiązujący to auto (własny albo domyślny organizacji). */
    val speedLimitKmh: Int,
    val points: List<RoutePoint>,
    val trips: List<RouteTrip>,
    val stops: List<RouteStop>,
    val speeding: List<SpeedingRun>,
    val harsh: List<HarshEvent>,
    val summary: RouteSummary,
) {
    val hasTrack: Boolean get() = points.size > 1
}

data class RoutePoint(
    val atMillis: Long,
    val lat: Double,
    val lng: Double,
    val speedKmh: Double?,
    val ignition: Boolean?,
    /** Odcinek DO tego punktu to luka w sygnale — rysowany przerywaną linią. */
    val gap: Boolean,
)

data class RouteStop(
    val fromMillis: Long,
    val toMillis: Long,
    val minutes: Int,
    val lat: Double,
    val lng: Double,
    /** Silnik pracował przez większość postoju — postój, który kosztuje paliwo. */
    val idling: Boolean,
    /** Postój trwa: auto stoi tam nadal. */
    val open: Boolean,
)

data class RouteTrip(
    val index: Int,
    val departedMillis: Long,
    val arrivedMillis: Long,
    val minutes: Int,
    val distanceKm: Double,
    val fromLat: Double,
    val fromLng: Double,
    val toLat: Double,
    val toLng: Double,
    val maxSpeed: Double?,
    val avgSpeed: Double?,
    val speedingCount: Int,
    val open: Boolean,
    /** Dysponent auta w chwili wyruszenia; null = auto z puli. */
    val driverLabel: String?,
    val startOdometerKm: Double?,
    val endOdometerKm: Double?,
)

data class SpeedingRun(
    val fromMillis: Long,
    val toMillis: Long,
    val seconds: Int,
    val maxSpeed: Double,
    val limit: Double,
    val lat: Double,
    val lng: Double,
    val distanceKm: Double,
    val tripIndex: Int?,
)

/** Gwałtowne zdarzenie prowadzenia — z urządzenia albo oszacowane z odczytów. */
data class HarshEvent(
    val atMillis: Long,
    val lat: Double,
    val lng: Double,
    val type: HarshType,
    val fromDevice: Boolean,
    val fromSpeed: Double?,
    val toSpeed: Double?,
    val accelMs2: Double?,
)

enum class HarshType(val label: String) {
    ACCEL("Gwałtowne przyspieszenie"),
    BRAKE("Gwałtowne hamowanie"),
    UNKNOWN("Zdarzenie z urządzenia");

    companion object {
        fun fromWire(value: String?): HarshType = when (value) {
            "accel" -> ACCEL
            "brake" -> BRAKE
            else -> UNKNOWN
        }
    }
}

data class DrivingStyle(
    val score: Int,
    val grade: String,
    val speedingPenalty: Int,
    val harshPenalty: Int,
    val idlePenalty: Int,
    val harshEvents: Int,
    val harshFromDevice: Int,
)

data class RouteSummary(
    val distanceKm: Double,
    val drivingMinutes: Int,
    val stopMinutes: Int,
    val idleMinutes: Int,
    val maxSpeed: Double?,
    val firstDepartureMillis: Long?,
    val lastArrivalMillis: Long?,
    val trips: Int,
    val stops: Int,
    val speedingRuns: Int,
    val speedingMinutes: Int,
    val longestStopMinutes: Int,
    /** Przebieg z licznika auta; null = urządzenie go nie nadaje albo się cofnął. */
    val odometerKm: Double?,
    /** Szacunkowy koszt paliwa spalonego na jałowym [zł]. */
    val idleCostPln: Double,
    val style: DrivingStyle,
)

/**
 * Kolor odcinka śladu wg prędkości — te same wartości co w panelu
 * (`RouteHistoryPanel.tsx`), żeby ta sama trasa wyglądała tak samo na obu
 * ekranach. Ponad progiem zawsze czerwień, niezależnie od bezwzględnej wartości.
 */
object RoutePalette {
    const val OVER_LIMIT = 0xFFCF222EL
    const val GAP = 0xFF8B949EL
    const val IDLE = 0xFFBC4C00L
    const val PARKED = 0xFF57606AL
    const val SLOW = 0xFF1A7F37L
    const val MEDIUM = 0xFF9A6700L
    const val FAST = 0xFFBC4C00L

    fun segment(speedKmh: Double?, limitKmh: Int): Long {
        if (speedKmh != null && limitKmh > 0 && speedKmh > limitKmh) return OVER_LIMIT
        val s = speedKmh ?: 0.0
        return when {
            s <= 40 -> SLOW
            s <= 70 -> MEDIUM
            else -> FAST
        }
    }
}

/** Wpis osi czasu — kurs albo postój, ułożone jednym ciągiem chronologicznie. */
sealed interface RouteTimelineItem {
    val atMillis: Long

    data class Trip(val trip: RouteTrip) : RouteTimelineItem {
        override val atMillis: Long get() = trip.departedMillis
    }

    data class Stop(val stop: RouteStop, val index: Int) : RouteTimelineItem {
        override val atMillis: Long get() = stop.fromMillis
    }
}

/** Kursy i postoje w jednym ciągu — tak, jak czyta się dzień: z góry na dół. */
fun RouteHistory.timeline(): List<RouteTimelineItem> =
    (trips.map { RouteTimelineItem.Trip(it) } +
        stops.mapIndexed { i, s -> RouteTimelineItem.Stop(s, i) })
        .sortedBy { it.atMillis }

/**
 * Kondycja lokalizatora — odpowiedź na „czy temu, co widzę na mapie, wolno
 * ufać". Opisy problemów (`issues`) układa serwer, żeby były identyczne w
 * panelu i w telefonie; telefon dokłada wyłącznie kolor i kolejność.
 */
data class TrackerHealth(
    val assetId: String,
    val assetName: String,
    val registration: String?,
    val hasTracker: Boolean,
    val status: TrackerStatus,
    val lastContactMillis: Long?,
    val silentMinutes: Int?,
    val readings: Int,
    val coveragePercent: Int,
    val longestGapMinutes: Int,
    val voltageV: Double?,
    val minVoltageV: Double?,
    val gsmSignal: Double?,
    val satellites: Double?,
    val faultCodes: String?,
    val issues: List<String>,
)

enum class TrackerStatus(val wire: String, val label: String, val colorArgb: Long) {
    OK("ok", "Nadaje", 0xFF1A7F37),
    SILENT("cisza", "Cisza", 0xFFBC4C00),
    POWER("zasilanie", "Zasilanie", 0xFFCF222E),
    FAULT("usterka", "Usterka", 0xFF9A6700),
    NO_TRACKER("bez_lokalizatora", "Bez lokalizatora", 0xFF57606A);

    companion object {
        fun fromWire(value: String?): TrackerStatus =
            entries.firstOrNull { it.wire == value } ?: OK
    }
}
