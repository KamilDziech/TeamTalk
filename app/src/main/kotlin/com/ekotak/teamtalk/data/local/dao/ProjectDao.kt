package com.ekotak.teamtalk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ekotak.teamtalk.data.local.entity.ProjectEntity
import com.ekotak.teamtalk.data.local.entity.ProjectMilestoneEntity
import com.ekotak.teamtalk.data.local.entity.ProjectMutationEntity
import com.ekotak.teamtalk.data.local.entity.ProjectTaskEntity
import kotlinx.coroutines.flow.Flow

/** Cache modułu Projekty: projekty, kamienie i zadania z karty projektu. */
@Dao
interface ProjectDao {

    @Query("SELECT * FROM projects ORDER BY name")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observeProject(id: String): Flow<ProjectEntity?>

    /**
     * Projekty jednego deala — zakładka „Harmonogram" karty.
     *
     * Kolejność jak w `listByDeal` board360: najpierw aktywne, potem archiwum
     * (`status` rośnie alfabetycznie). Drugim kluczem jest nazwa, a nie data
     * zmiany jak na serwerze — cache jej nie trzyma, a lista projektów deala
     * jest krótka, więc kolejność alfabetyczna czyta się lepiej niż losowa.
     */
    @Query("SELECT * FROM projects WHERE dealId = :dealId ORDER BY status, name")
    suspend fun getDealProjects(dealId: String): List<ProjectEntity>

    @Query("SELECT * FROM project_milestones WHERE projectId = :projectId ORDER BY position")
    fun observeMilestones(projectId: String): Flow<List<ProjectMilestoneEntity>>

    @Query("SELECT * FROM project_tasks WHERE projectId = :projectId")
    fun observeTasks(projectId: String): Flow<List<ProjectTaskEntity>>

    @Query("SELECT * FROM project_tasks WHERE id = :id")
    suspend fun getTask(id: String): ProjectTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: ProjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProjects(projects: List<ProjectEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMilestones(milestones: List<ProjectMilestoneEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: ProjectTaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTasks(tasks: List<ProjectTaskEntity>)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProject(id: String)

    /**
     * Podmiana listy z serwera. Pomysły zgłoszone bez zasięgu (`localOnly`)
     * przeżywają odświeżenie — serwer o nich jeszcze nie wie, a skasowanie ich
     * tutaj byłoby utratą jedynej kopii.
     */
    @Query("DELETE FROM projects WHERE localOnly = 0")
    suspend fun deleteSyncedProjects()

    /** Jak wyżej, ale tylko projekty jednego deala — reszta cache'u zostaje. */
    @Query("DELETE FROM projects WHERE dealId = :dealId AND localOnly = 0")
    suspend fun deleteSyncedDealProjects(dealId: String)

    @Query("DELETE FROM project_milestones WHERE projectId = :projectId")
    suspend fun deleteMilestones(projectId: String)

    @Query("DELETE FROM project_tasks WHERE projectId = :projectId")
    suspend fun deleteTasks(projectId: String)

    @Transaction
    suspend fun replaceProjects(projects: List<ProjectEntity>) {
        deleteSyncedProjects()
        upsertProjects(projects)
    }

    /**
     * Podmiana listy projektów jednego deala. Projekt założony bez zasięgu
     * (`localOnly`) zostaje — serwer o nim jeszcze nie wie, a to jedyna kopia.
     */
    @Transaction
    suspend fun replaceDealProjects(dealId: String, projects: List<ProjectEntity>) {
        deleteSyncedDealProjects(dealId)
        upsertProjects(projects)
    }

    @Transaction
    suspend fun replaceDetail(
        project: ProjectEntity,
        milestones: List<ProjectMilestoneEntity>,
        tasks: List<ProjectTaskEntity>,
    ) {
        upsertProject(project)
        deleteMilestones(project.id)
        deleteTasks(project.id)
        upsertMilestones(milestones)
        upsertTasks(tasks)
    }
}

/** Kolejka zmian zrobionych bez zasięgu (domknięcia zadań, pomysły). */
@Dao
interface ProjectMutationDao {

    @Query("SELECT * FROM project_mutations ORDER BY createdAt")
    suspend fun getAll(): List<ProjectMutationEntity>

    @Query("SELECT COUNT(*) FROM project_mutations")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mutation: ProjectMutationEntity)

    @Query("DELETE FROM project_mutations WHERE targetId = :targetId AND kind = :kind")
    suspend fun delete(targetId: String, kind: String)
}
