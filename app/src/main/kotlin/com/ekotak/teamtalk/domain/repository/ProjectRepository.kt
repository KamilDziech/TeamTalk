package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.IdeaDraft
import com.ekotak.teamtalk.domain.model.Project
import com.ekotak.teamtalk.domain.model.ProjectDetail
import kotlinx.coroutines.flow.Flow

/** Wynik opróżniania kolejki — `RETRY` oznacza brak sieci, nie odmowę serwera. */
enum class ProjectSyncResult { DONE, RETRY }

/** Projekty deala po odczycie: treść z cache plus stan łączności. */
data class DealProjectsSnapshot(
    val projects: List<Project>,
    /** Serwer był nieosiągalny — pokazujemy to, co telefon ma u siebie. */
    val offline: Boolean = false,
    /** Odmowa serwera (brak `projects.view`, 5xx) — inna sprawa niż brak zasięgu. */
    val error: String? = null,
)

/** Czy projekt poszedł na serwer, czy czeka w telefonie na zasięg. */
enum class DealProjectSaveResult { SENT, QUEUED }

/**
 * Moduł Projekty. Czytanie zawsze z Room (ekran otwiera się bez zasięgu),
 * zapis wprost do API, a bez łączności — do kolejki i do cache, żeby człowiek
 * zobaczył swoją decyzję od razu.
 */
interface ProjectRepository {

    fun observeProjects(): Flow<List<Project>>

    fun observeDetail(projectId: String): Flow<ProjectDetail?>

    /** Ile zmian czeka w kolejce (pasek „brak sieci"). */
    fun observePendingCount(): Flow<Int>

    /** Odświeżenie listy z serwera. false = nie udało się (zostaje cache). */
    suspend fun refreshProjects(): Boolean

    suspend fun refreshDetail(projectId: String): Boolean

    /**
     * Domknięcie zadania z podaniem czasu. `actualMinutes = null` znaczy
     * „nie podano" — rozliczenie policzy zadanie po estymacie.
     */
    suspend fun closeTask(taskId: String, actualMinutes: Int?): Boolean

    /** Zgłoszenie pomysłu do Poczekalni. */
    suspend fun submitIdea(draft: IdeaDraft): Boolean

    /**
     * Projekty przypięte do deala — zakładka „Harmonogram" karty. Z serwera,
     * gdy się da, a zawsze z cache, więc lista otwiera się bez zasięgu.
     */
    suspend fun getDealProjects(dealId: String): DealProjectsSnapshot

    /**
     * Nowy projekt pod dealem („+ Projekt" panelu). Bez zasięgu ląduje w kolejce
     * i w cache — handlowiec widzi go od razu, zamiast klikać drugi raz.
     *
     * Odmowa serwera (brak `projects.manage`, skasowany deal) leci wyjątkiem:
     * ponawianie jej w kolejce niczego by nie zmieniło.
     */
    suspend fun createDealProject(dealId: String, name: String): DealProjectSaveResult

    suspend fun syncPendingMutations(): ProjectSyncResult
}
