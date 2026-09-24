package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.CrewSchedule
import com.ekotak.teamtalk.domain.model.PersonMove
import com.ekotak.teamtalk.domain.model.MyDays
import com.ekotak.teamtalk.domain.model.ScheduleBlockInput
import com.ekotak.teamtalk.domain.model.StagePatch
import java.time.LocalDate

/**
 * Harmonogram ekip (kafelek „Harmonogram").
 *
 * Odczyt i zmiany etapów działają bez zasięgu: oś z ostatniego pobrania leży
 * w pamięci telefonu, a zmiana terminu, ekipy czy składu wpada do WSPÓLNEJ
 * kolejki montaży (`montaz_mutations`, ten sam `PATCH /installations/{id}`,
 * co zakładka Montaż karty deala). Publikacja tygodnia, przełącznik publikacji
 * i „Zaplanuj" wymagają zasięgu — to decyzje, których skutek liczy serwer.
 */
interface CrewScheduleRepository {

    suspend fun load(from: LocalDate, to: LocalDate): CrewScheduleSnapshot

    suspend fun patchStage(dealId: String, id: String, patch: StagePatch): ScheduleSaveResult

    /**
     * Przeniesienie osoby do innej ekipy (etap albo wybrane dni). Bez zasięgu
     * czeka w kolejce montaży i od razu rysuje się na osi.
     */
    suspend fun movePerson(dealId: String, move: PersonMove): ScheduleSaveResult

    suspend fun publishWeek(weekStart: LocalDate): ScheduleCallResult<PublishOutcome>

    suspend fun setPublishEnabled(enabled: Boolean): ScheduleCallResult<Unit>

    /**
     * Kolejność ekip na osi (przytrzymanie nazwy i przeciągnięcie). Wspólna dla
     * firmy, jak w panelu. Bez zasięgu czeka w telefonie i idzie przy
     * najbliższym wczytaniu osi — do tego czasu oś rysuje się w nowym układzie.
     */
    suspend fun saveCrewOrder(crewIds: List<String>): ScheduleSaveResult

    /** „Zaplanuj" — zwraca liczbę założonych etapów (0 = deal ma już montaż). */
    suspend fun planDeal(dealId: String): ScheduleCallResult<Int>

    // Dni nieaktywne i blokady (decyzje usera 2026-09-24) — tylko w zasięgu:
    // skutek (przesunięte końce montaży) liczy serwer.

    /** „W ten dzień pracujemy" przy ekipie — włącz albo zdejmij. */
    suspend fun setCrewWorkday(crewId: String, day: LocalDate, working: Boolean): ScheduleCallResult<Unit>

    /** Nowa blokada (`id == null`) albo zmiana istniejącej. */
    suspend fun saveBlock(id: String?, input: ScheduleBlockInput): ScheduleCallResult<Unit>

    suspend fun deleteBlock(id: String): ScheduleCallResult<Unit>

    /** Cofnięcie usunięcia, zanim poszło do ekip. */
    suspend fun restoreBlock(id: String): ScheduleCallResult<Unit>

    /**
     * Dni wolne i pracujące montera (moduł „Montaże") — wersja opublikowana.
     * Bez zasięgu ostatnia kopia z telefonu; `null` = nigdy jej nie było.
     */
    suspend fun myDays(from: LocalDate, to: LocalDate): MyDays?
}

data class CrewScheduleSnapshot(
    /** `null` = nie ma ani odpowiedzi serwera, ani kopii tego okna w telefonie. */
    val schedule: CrewSchedule?,
    /** Oś z pamięci telefonu, bo serwer był nieosiągalny. */
    val fromCache: Boolean,
    /** 403 — moduł jest dla koordynatora, zarządu i admina. */
    val forbidden: Boolean,
    val error: String?,
    /** Ile zmian etapów czeka w kolejce na wysłanie. */
    val pendingCount: Int,
)

data class PublishOutcome(val published: Int, val notified: Int, val calendar: Int = 0)

sealed interface ScheduleSaveResult {
    /** Serwer przyjął zmianę — oś trzeba przeładować, bo ostrzeżenia się przeliczyły. */
    data object Sent : ScheduleSaveResult

    /** Bez zasięgu — zmiana czeka w kolejce i pójdzie sama. */
    data object Queued : ScheduleSaveResult

    data class Failed(val message: String) : ScheduleSaveResult
}

sealed interface ScheduleCallResult<out T> {
    data class Ok<T>(val value: T) : ScheduleCallResult<T>
    data object Offline : ScheduleCallResult<Nothing>
    data class Failed(val message: String) : ScheduleCallResult<Nothing>
}
