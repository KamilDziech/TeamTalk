package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.IdeaDraft
import com.ekotak.teamtalk.domain.model.Project
import com.ekotak.teamtalk.domain.model.ProjectDetail
import kotlinx.coroutines.flow.Flow

/** Wynik opróżniania kolejki — `RETRY` oznacza brak sieci, nie odmowę serwera. */
enum class ProjectSyncResult { DONE, RETRY }

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

    suspend fun syncPendingMutations(): ProjectSyncResult
}
