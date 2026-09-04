package com.ekotak.teamtalk.presentation.service

import com.ekotak.teamtalk.domain.model.MAX_WARRANTY_INSPECTIONS
import com.ekotak.teamtalk.domain.model.ServiceClient
import com.ekotak.teamtalk.domain.model.ServiceJob
import com.ekotak.teamtalk.domain.model.ServiceJobStatus
import com.ekotak.teamtalk.domain.model.WarrantyCard
import com.ekotak.teamtalk.domain.model.WarrantyInspectionStatus
import com.ekotak.teamtalk.presentation.crm.parseIsoMillis
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Reguły wierszy modułu Serwis przeniesione 1:1 z panelu
 * (`web/src/app/app/service/service-jobs.ts` i `warranty.ts`). Trzymane osobno
 * od ekranów, bo używa ich lista, kalendarz i obie karty — a przy tym to czysta
 * logika, którą da się przetestować bez Compose.
 */

// ── Zlecenia serwisowe ───────────────────────────────────────────────────────

/** Opis usterki = pierwsza linia notatki; bez notatki — etykieta zastępcza. */
fun jobTitle(job: ServiceJob): String {
    val first = job.note.orEmpty().lineSequence().firstOrNull()?.trim().orEmpty()
    return first.ifBlank { "Zgłoszenie serwisowe" }
}

/** Etykieta klienta zlecenia albo `null`, gdy klienta jeszcze nie wskazano. */
fun clientNameOf(job: ServiceJob, clients: Map<String, ServiceClient>): String? =
    job.clientId?.let { clients[it]?.label ?: "—" }

/**
 * Nazwa wiersza: „opis usterki — Klient (Miasto)”. Bez klienta zostaje sam opis
 * — braki sygnalizuje czerwień wiersza.
 */
fun jobRowLabel(job: ServiceJob, clientName: String?, city: String?): String {
    if (clientName == null) return jobTitle(job)
    val who = if (!city.isNullOrBlank()) "$clientName ($city)" else clientName
    return "${jobTitle(job)} — $who"
}

/**
 * Czego brakuje w zleceniu. Kolejność = kolejność pól w karcie zlecenia,
 * bo z tej listy powstaje podpowiedź „Do uzupełnienia: …”.
 */
fun missingFields(job: ServiceJob): List<String> = buildList {
    if (job.note.isNullOrBlank()) add("opis usterki")
    if (job.clientId == null) add("klient")
    if (job.technicianId == null) add("serwisant")
    if (job.scheduledAt == null) add("termin")
}

/** Czy zlecenie jest niedouzupełnione (czerwona czcionka na liście). */
fun isIncomplete(job: ServiceJob): Boolean = missingFields(job).isNotEmpty()

/** Podpowiedź „Do uzupełnienia: …”; pusty string, gdy komplet. */
fun missingHint(job: ServiceJob): String {
    val miss = missingFields(job)
    return if (miss.isEmpty()) "" else "Do uzupełnienia: ${miss.joinToString(", ")}."
}

/** Okna SLA do wyboru w karcie zlecenia (jak `SLA_CHOICES` w panelu). */
val SLA_CHOICES: List<Pair<Int, String>> = listOf(
    24 to "24 h",
    168 to "7 dni",
    720 to "30 dni",
)

/** Etykieta opcji „bez wskazania” — zależna od typu zlecenia. */
fun slaDefaultLabel(job: ServiceJob): String =
    if (job.type == com.ekotak.teamtalk.domain.model.ServiceJobType.AWARIA) {
        "Domyślne (24 h)"
    } else {
        "Bez SLA"
    }

/** Ton prawej komórki wiersza: chip alarmowy, ostrzegawczy albo zwykły tekst. */
enum class MetaTone { BREACH, SOON, PLAIN }

data class RowMeta(val tone: MetaTone, val label: String)

/** Ile godzin zostało do końca okna SLA (ujemne = po terminie); null gdy brak. */
private fun hoursLeft(job: ServiceJob, now: Long): Double? {
    val due = parseIsoMillis(job.slaDueAt) ?: return null
    return (due - now) / 3_600_000.0
}

/**
 * Prawa strona wiersza: chip SLA (awaria ma okno 24 h) albo zwykły termin.
 * Kolejność warunków jak w panelu — flaga z API wygrywa z lokalnym liczeniem.
 */
fun rowMeta(job: ServiceJob, now: Long = System.currentTimeMillis()): RowMeta {
    if (job.status == ServiceJobStatus.DONE) {
        return RowMeta(MetaTone.PLAIN, formatDay(job.scheduledAt).ifBlank { "wykonane" })
    }
    val left = hoursLeft(job, now)
    if (job.slaBreached || (left != null && left <= 0)) return RowMeta(MetaTone.BREACH, "po SLA")
    if (left != null) {
        val h = Math.floor(left).toInt()
        val label = if (h >= 1) "SLA $h h" else "SLA ${maxOf(1, Math.round(left * 60).toInt())} min"
        return RowMeta(if (h < 6) MetaTone.SOON else MetaTone.PLAIN, label)
    }
    return RowMeta(MetaTone.PLAIN, formatDay(job.scheduledAt).ifBlank { "bez terminu" })
}

/** Data porządkująca wiersz: koniec SLA, a gdy go brak — termin wizyty. */
private fun jobSortDate(job: ServiceJob): Long? =
    parseIsoMillis(job.slaDueAt) ?: parseIsoMillis(job.scheduledAt)

/** Otwarte najpierw (rosnąco — najpilniejsze na górze), wykonane na końcu. */
val jobComparator: Comparator<ServiceJob> = Comparator { a, b ->
    val aDone = a.status == ServiceJobStatus.DONE
    val bDone = b.status == ServiceJobStatus.DONE
    if (aDone != bDone) return@Comparator if (aDone) 1 else -1
    val da = jobSortDate(a)
    val db = jobSortDate(b)
    when {
        da == null && db == null -> 0
        da == null -> 1
        db == null -> -1
        aDone -> db.compareTo(da)
        else -> da.compareTo(db)
    }
}

// ── Karty gwarancyjne ────────────────────────────────────────────────────────

/**
 * „Sygnalizacja świetlna” pojedynczego przeglądu (decyzja z panelu, 2026-08-10):
 *  - [GREEN]  — wykonany,
 *  - [FUTURE] — zaplanowany w przyszłości LUB bez terminu (samo obramowanie),
 *  - [RED]    — po terminie, do odrobienia (termin minął ≤ 12 miesięcy temu),
 *  - [GRAY]   — przepadł (termin minął > 12 miesięcy temu).
 */
enum class WarrantyLight { GREEN, RED, FUTURE, GRAY }

/** Próg „przepadnięcia”: po ilu ms po terminie czerwony przechodzi w szary. */
const val WARRANTY_LAPSE_MS = 365L * 24 * 60 * 60 * 1000

fun warrantyLight(
    status: WarrantyInspectionStatus,
    plannedAt: String?,
    now: Long = System.currentTimeMillis(),
): WarrantyLight {
    if (status == WarrantyInspectionStatus.DONE) return WarrantyLight.GREEN
    if (status == WarrantyInspectionStatus.OVERDUE) {
        val planned = parseIsoMillis(plannedAt)
        if (planned != null) {
            return if (now - planned > WARRANTY_LAPSE_MS) WarrantyLight.GRAY else WarrantyLight.RED
        }
    }
    return WarrantyLight.FUTURE
}

/** Krótka etykieta stanu przeglądu wg światła (z rozróżnieniem szarego). */
fun warrantyLightLabel(light: WarrantyLight, status: WarrantyInspectionStatus): String = when (light) {
    WarrantyLight.GREEN -> "Wykonany"
    WarrantyLight.RED -> "Po terminie"
    WarrantyLight.FUTURE -> "Zaplanowany"
    WarrantyLight.GRAY ->
        if (status == WarrantyInspectionStatus.UNSCHEDULED) "Bez terminu" else "Przepadł"
}

/**
 * Wiersz listy dla całego urządzenia: pasek 5 świateł (stałe pozycje 1..5, brak
 * wpisu = puste kółko) plus najpilniejszy stan instalacji.
 * Priorytet stanu: po terminie → przepadł → zakończony → w toku.
 */
data class WarrantyRowView(
    val card: WarrantyCard,
    /** Długość 5: indeks i = przegląd #(i+1). */
    val lights: List<WarrantyLight>,
    val doneCount: Int,
    val urgency: WarrantyLight,
    val label: String,
    val sortDate: Long?,
    val closed: Boolean,
)

fun warrantyRowView(card: WarrantyCard, now: Long = System.currentTimeMillis()): WarrantyRowView {
    val byOrdinal = card.inspections.associateBy { it.ordinal }
    val lights = (1..MAX_WARRANTY_INSPECTIONS).map { ord ->
        byOrdinal[ord]?.let { warrantyLight(it.computedStatus, it.plannedAt, now) }
            ?: WarrantyLight.FUTURE
    }
    val doneCount = lights.count { it == WarrantyLight.GREEN }
    val urgency: WarrantyLight
    val label: String
    when {
        WarrantyLight.RED in lights -> {
            urgency = WarrantyLight.RED
            label = "Po terminie"
        }
        WarrantyLight.GRAY in lights -> {
            urgency = WarrantyLight.GRAY
            label = "Przepadł"
        }
        doneCount == MAX_WARRANTY_INSPECTIONS -> {
            urgency = WarrantyLight.GREEN
            label = "Zakończony"
        }
        else -> {
            urgency = WarrantyLight.FUTURE
            label = if (doneCount > 0) "W toku" else "Zaplanowany"
        }
    }
    return WarrantyRowView(
        card = card,
        lights = lights,
        doneCount = doneCount,
        urgency = urgency,
        label = label,
        sortDate = parseIsoMillis(card.nextPlannedAt),
        closed = doneCount == MAX_WARRANTY_INSPECTIONS,
    )
}

/** Pojedynczy przegląd „wyjęty” z karty — pozycja kalendarza. */
data class WarrantyListItem(
    /** `${cardId}:${ordinal}` — stabilny klucz listy. */
    val key: String,
    val card: WarrantyCard,
    val ordinal: Int,
    val plannedAt: String?,
    val doneAt: String?,
    /** Data wiodąca: wykonania (jeśli jest) lub planowana. */
    val date: String?,
    val status: WarrantyInspectionStatus,
    val light: WarrantyLight,
    val suspect: Boolean,
)

/** Rozkłada karty na pojedyncze przeglądy (po jednej pozycji na wpis inspekcji). */
fun flattenWarranty(
    cards: List<WarrantyCard>,
    now: Long = System.currentTimeMillis(),
): List<WarrantyListItem> = cards.flatMap { card ->
    card.inspections.map { i ->
        WarrantyListItem(
            key = "${card.id}:${i.ordinal}",
            card = card,
            ordinal = i.ordinal,
            plannedAt = i.plannedAt,
            doneAt = i.doneAt,
            date = i.doneAt ?: i.plannedAt,
            status = i.computedStatus,
            light = warrantyLight(i.computedStatus, i.plannedAt, now),
            suspect = i.suspect,
        )
    }
}

/** Czy karta pasuje do wyszukiwarki (nazwa, adres, modele, numery seryjne). */
fun warrantyMatchesQuery(card: WarrantyCard, query: String): Boolean {
    if (query.isBlank()) return true
    val hay = listOfNotNull(
        card.name,
        card.location,
        card.outdoorModel,
        card.outdoorSerial,
        card.indoorModel,
        card.indoorSerial,
    ).joinToString(" ").lowercase()
    return hay.contains(query.trim().lowercase())
}

// ── Wspólne wiersze listy „Przegląd” ─────────────────────────────────────────

/**
 * Wiersz scalonej listy dziedziny Przegląd: zwykłe zlecenie albo urządzenie
 * gwarancyjne. Gwarancja to JEDEN wiersz na kartę (pasek 5 przeglądów), nie
 * jeden na przegląd — tak samo jak w panelu.
 */
sealed interface ServiceRow {
    val date: Long?
    val closed: Boolean

    data class Job(
        val job: ServiceJob,
        override val date: Long?,
        override val closed: Boolean,
    ) : ServiceRow

    data class Warranty(
        val view: WarrantyRowView,
        override val date: Long?,
        override val closed: Boolean,
    ) : ServiceRow
}

/**
 * Otwarte przed zamkniętymi; otwarte rosnąco po dacie (zaległe na górze),
 * zamknięte malejąco (najświeższe); brak daty na końcu grupy.
 */
val serviceRowComparator: Comparator<ServiceRow> = Comparator { a, b ->
    if (a.closed != b.closed) return@Comparator if (a.closed) 1 else -1
    val da = a.date
    val db = b.date
    when {
        da == null && db == null -> 0
        da == null -> 1
        db == null -> -1
        a.closed -> db.compareTo(da)
        else -> da.compareTo(db)
    }
}

// ── Daty ─────────────────────────────────────────────────────────────────────

private val dayFormat = ThreadLocal.withInitial {
    SimpleDateFormat("dd.MM.yyyy", Locale("pl", "PL"))
}
private val dayTimeFormat = ThreadLocal.withInitial {
    SimpleDateFormat("dd.MM, HH:mm", Locale("pl", "PL"))
}
private val isoUtcFormat = ThreadLocal.withInitial {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}

/** ISO → „18.09.2026”; pusty string, gdy brak daty. */
fun formatDay(iso: String?): String =
    parseIsoMillis(iso)?.let { dayFormat.get()!!.format(Date(it)) }.orEmpty()

/** ISO → „18.09, 14:00” — okno SLA liczy się z dokładnością do godziny. */
fun formatDayTime(iso: String?): String =
    parseIsoMillis(iso)?.let { dayTimeFormat.get()!!.format(Date(it)) }.orEmpty()

/** Cena przeglądu w PLN albo „—” (0 zł = przegląd darmowy, nie brak ceny). */
fun formatPrice(value: Int?): String = if (value == null) "—" else "$value zł"

/** Data lub „—” — pola karty gwarancyjnej. */
fun formatDayOrDash(iso: String?): String = formatDay(iso).ifBlank { "—" }

/**
 * Godzina, na którą zapisujemy termin wybrany jako sama data (start dnia pracy).
 * Bez doklejenia godziny „2026-09-18” poszłoby jako północ UTC i po przeliczeniu
 * na strefę mogłoby wypaść dzień wcześniej — tak samo broni się przed tym panel.
 */
const val SERVICE_DAY_HOUR = 9

/** Millis z wybieraka dat (północ UTC) → ISO lokalnej godziny 9:00 tego dnia. */
fun dayMillisToIso(millis: Long): String {
    val utc = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = millis
    }
    val local = java.util.Calendar.getInstance().apply {
        clear()
        set(
            utc.get(java.util.Calendar.YEAR),
            utc.get(java.util.Calendar.MONTH),
            utc.get(java.util.Calendar.DAY_OF_MONTH),
            SERVICE_DAY_HOUR,
            0,
            0,
        )
    }
    return isoUtcFormat.get()!!.format(Date(local.timeInMillis))
}

/** Millis (czas lokalny) → ISO 8601 w UTC — zapis daty wykonania przeglądu. */
fun millisToIso(millis: Long): String = isoUtcFormat.get()!!.format(Date(millis))
