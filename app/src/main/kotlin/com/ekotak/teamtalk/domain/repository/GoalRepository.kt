package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.CompanyGoals
import com.ekotak.teamtalk.domain.model.Goal
import com.ekotak.teamtalk.domain.model.GoalCatalog
import com.ekotak.teamtalk.domain.model.GoalDraft
import com.ekotak.teamtalk.domain.model.GoalTrend
import com.ekotak.teamtalk.domain.model.PersonalGoals
import com.ekotak.teamtalk.domain.model.TeamGoals
import kotlinx.coroutines.flow.Flow

/**
 * Moduł Cele — pełny offline z kolejką (decyzja 2026-09-23), jak w Zadaniach,
 * Serwisie i Urlopie. Każdy ekran czyta migawkę z Room, więc bez zasięgu widać
 * ostatnio pobrane liczby z podpisem „dane z <godzina>", a zapisy czekają
 * w kolejce na powrót sieci.
 *
 * Realizacji telefon NIE liczy — to samo API, co panel; tu jest tylko cache.
 */
interface GoalRepository {

    /** Ostatnia migawka zakładki „Osobiste" dla okresu i (opcjonalnie) osoby. */
    fun observePersonal(period: String, userId: String?): Flow<PersonalGoals?>

    fun observeTeam(period: String, team: String): Flow<TeamGoals?>

    fun observeCompany(period: String): Flow<CompanyGoals?>

    /** Przebieg celu do wykresu — dociągany osobno, dla celu wiodącego. */
    fun observeTrend(goalId: String): Flow<GoalTrend?>

    /** Katalog mierników i działów — kreator działa z niego także bez sieci. */
    fun observeCatalog(): Flow<GoalCatalog?>

    /** Kiedy ostatnio udało się pobrać ten widok (`null` = nigdy). */
    fun observeSyncedAt(key: String): Flow<Long?>

    /** Cele z zapisem czekającym w kolejce — plakietka „W kolejce" na karcie. */
    fun observePendingIds(): Flow<Set<String>>

    /**
     * Dociąga zakładkę z serwera. Najpierw opróżnia kolejkę — inaczej
     * odpowiedź cofnęłaby na ekranie cel, o którym serwer jeszcze nie wie.
     */
    suspend fun refreshPersonal(period: String, userId: String?)

    suspend fun refreshTeam(period: String, team: String)

    suspend fun refreshCompany(period: String)

    suspend fun refreshTrend(goalId: String)

    suspend fun refreshCatalog()

    /** Nowy cel. Bez zasięgu dostaje id lokalne i ląduje w kolejce. */
    suspend fun create(draft: GoalDraft): Goal

    /** Łatka celu — zmiana nazwy, wartości, okresu albo progu ostrzeżenia. */
    suspend fun update(goalId: String, draft: GoalDraft)

    suspend fun delete(goalId: String)

    /** Wpis ręczny do celu `manual` — stan na dany dzień, nie przyrost. */
    suspend fun checkin(goalId: String, value: Double, note: String)

    /** Zamknięcie okresu — migawka wyniku i propozycja punktów. */
    suspend fun close(goalId: String)

    /**
     * Opróżnia kolejkę. Woła ją robotnik po powrocie sieci i każde odświeżenie
     * ekranu — człowiek zwykle wchodzi w moduł szybciej, niż system zdąży
     * obudzić `WorkManagera`.
     */
    suspend fun syncPendingMutations(): GoalSyncResult
}

/**
 * Wynik opróżniania kolejki.
 *
 * [rejected] to zapisy, których serwer NIE przyjął — zwykle dlatego, że cel
 * w międzyczasie skasowano albo pytający stracił prawo do jego zmiany. Takiej
 * odmowy nie wolno przemilczeć: człowiek widział zapis na ekranie jako zrobiony.
 */
data class GoalSyncResult(
    val sent: Int = 0,
    val rejected: List<GoalSyncRejection> = emptyList(),
    /** Coś zostało w kolejce (brak sieci) — robotnik ma spróbować ponownie. */
    val incomplete: Boolean = false,
)

data class GoalSyncRejection(
    /** Nazwa celu albo jego id — do treści powiadomienia. */
    val label: String,
    val reason: String?,
)
