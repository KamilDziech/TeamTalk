package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.CrewSchedule
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

data class PublishOutcome(val published: Int, val notified: Int)

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
