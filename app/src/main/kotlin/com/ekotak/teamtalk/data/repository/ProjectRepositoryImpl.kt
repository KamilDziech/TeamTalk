package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.local.dao.ProjectDao
import com.ekotak.teamtalk.data.local.dao.ProjectMutationDao
import com.ekotak.teamtalk.data.local.entity.ProjectEntity
import com.ekotak.teamtalk.data.local.entity.ProjectMutationEntity
import com.ekotak.teamtalk.data.local.entity.ProjectMutationEntity.Companion.KIND_CLOSE_TASK
import com.ekotak.teamtalk.data.local.entity.ProjectMutationEntity.Companion.KIND_CREATE_IDEA
import com.ekotak.teamtalk.data.local.entity.ProjectMutationEntity.Companion.LOCAL_ID_PREFIX
import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.mapper.toEntity
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.IdeaCreateDto
import com.ekotak.teamtalk.data.remote.dto.ProjectMemberDto
import com.ekotak.teamtalk.data.remote.dto.TaskCloseDto
import com.ekotak.teamtalk.data.sync.ProjectSyncScheduler
import com.ekotak.teamtalk.domain.model.IdeaDraft
import com.ekotak.teamtalk.domain.model.Project
import com.ekotak.teamtalk.domain.model.ProjectDetail
import com.ekotak.teamtalk.domain.repository.ProjectRepository
import com.ekotak.teamtalk.domain.repository.ProjectSyncResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Moduł Projekty — mobilny odpowiednik `web/src/app/app/projects`.
 *
 * Źródłem prawdy dla ekranu jest Room: lista i karta otwierają się w aucie bez
 * zasięgu, a sieć tylko dolewa świeże dane. Zapis idzie wprost do API, a gdy
 * sieci nie ma — do kolejki i do cache, żeby człowiek zobaczył swoją decyzję od
 * razu, zamiast klikać drugi raz.
 *
 * Rozróżnienie awarii jest tu istotne, tak samo jak w Serwisie: brak łączności
 * (`IOException`) da się nadrobić później, odmowa serwera (403/404/422) nie —
 * taką zmianę zdejmujemy z kolejki, zamiast wozić ją w kółko.
 */
@Singleton
class ProjectRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
    private val dao: ProjectDao,
    private val mutationDao: ProjectMutationDao,
    private val syncScheduler: ProjectSyncScheduler,
) : ProjectRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val memberSerializer = ListSerializer(ProjectMemberDto.serializer())

    override fun observeProjects(): Flow<List<Project>> =
        dao.observeProjects().map { rows -> rows.map { it.toDomain() } }

    override fun observeDetail(projectId: String): Flow<ProjectDetail?> = combine(
        dao.observeProject(projectId),
        dao.observeMilestones(projectId),
        dao.observeTasks(projectId),
    ) { project, milestones, tasks ->
        project?.let {
            ProjectDetail(
                project = it.toDomain(),
                milestones = milestones.map { m -> m.toDomain() },
                tasks = tasks.map { t -> t.toDomain() },
                members = decodeMembers(it.membersJson),
            )
        }
    }

    override fun observePendingCount(): Flow<Int> = mutationDao.observeCount()

    private fun decodeMembers(raw: String?): List<com.ekotak.teamtalk.domain.model.ProjectMember> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(memberSerializer, raw) }
            .getOrDefault(emptyList())
            .map { it.toDomain() }
    }

    override suspend fun refreshProjects(): Boolean {
        val remote = try {
            // `status = null` — moduł chce też pomysły z Poczekalni i projekty
            // przed decyzją, a nie tylko aktywne jak kreator zadania.
            api.getProjects(status = null)
        } catch (e: Exception) {
            // Brak sieci albo odmowa — zostaje cache. Ekran i tak ma co pokazać.
            return false
        }
        val now = System.currentTimeMillis()
        dao.replaceProjects(remote.map { it.toEntity(now) })
        return true
    }

    override suspend fun refreshDetail(projectId: String): Boolean {
        // Pomysłu, który jeszcze nie doszedł na serwer, nie ma po co pobierać.
        if (projectId.startsWith(LOCAL_ID_PREFIX)) return false
        val detail = try {
            api.getProject(projectId)
        } catch (e: Exception) {
            return false
        }
        val now = System.currentTimeMillis()
        val entity = detail.toEntity(now).copy(
            membersJson = runCatching {
                json.encodeToString(memberSerializer, detail.members)
            }.getOrNull(),
        )
        dao.replaceDetail(
            project = entity,
            milestones = detail.milestones.map { it.toEntity() },
            tasks = detail.tasks.map { it.toEntity(detail.id) },
        )
        return true
    }

    override suspend fun closeTask(taskId: String, actualMinutes: Int?): Boolean {
        val body = TaskCloseDto(actualMinutes = actualMinutes)
        return try {
            val updated = api.closeProjectTask(taskId, body)
            dao.upsertTask(updated.toEntity(updated.projectId ?: ""))
            true
        } catch (e: IOException) {
            // Bez zasięgu: zamykamy zadanie lokalnie i wkładamy do kolejki.
            // Technik w kotłowni ma zobaczyć „zrobione" od razu.
            queueTaskClose(taskId, body)
            true
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun queueTaskClose(taskId: String, body: TaskCloseDto) {
        dao.getTask(taskId)?.let { task ->
            dao.upsertTask(
                task.copy(
                    status = "done",
                    actualMinutes = body.actualMinutes ?: task.actualMinutes,
                ),
            )
        }
        mutationDao.upsert(
            ProjectMutationEntity(
                targetId = taskId,
                kind = KIND_CLOSE_TASK,
                payload = json.encodeToString(TaskCloseDto.serializer(), body),
                createdAt = System.currentTimeMillis(),
            ),
        )
        syncScheduler.scheduleSync()
    }

    override suspend fun submitIdea(draft: IdeaDraft): Boolean {
        val body = IdeaCreateDto(
            name = draft.name,
            description = draft.description,
            department = draft.department,
        )
        return try {
            val created = api.createIdea(body)
            dao.upsertProject(created.toEntity(System.currentTimeMillis()))
            true
        } catch (e: IOException) {
            queueIdea(body)
            true
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun queueIdea(body: IdeaCreateDto) {
        // Lokalny identyfikator do czasu wysłania — pomysł widać na liście od
        // razu, z oznaczeniem, że czeka.
        val localId = LOCAL_ID_PREFIX + UUID.randomUUID()
        dao.upsertProject(
            ProjectEntity(
                id = localId,
                name = body.name,
                description = body.description,
                color = null,
                stage = "idea",
                department = body.department,
                managerEmail = null,
                sponsorEmail = null,
                memberCount = 0,
                taskCount = 0,
                doneCount = 0,
                dueAt = null,
                problemStatement = null,
                metricName = null,
                metricBaseline = null,
                metricTarget = null,
                membersJson = null,
                localOnly = true,
                cachedAt = System.currentTimeMillis(),
            ),
        )
        mutationDao.upsert(
            ProjectMutationEntity(
                targetId = localId,
                kind = KIND_CREATE_IDEA,
                payload = json.encodeToString(IdeaCreateDto.serializer(), body),
                createdAt = System.currentTimeMillis(),
            ),
        )
        syncScheduler.scheduleSync()
    }

    override suspend fun syncPendingMutations(): ProjectSyncResult {
        val queue = mutationDao.getAll()
        if (queue.isEmpty()) return ProjectSyncResult.DONE

        for (entry in queue) {
            when (entry.kind) {
                KIND_CLOSE_TASK -> {
                    val body = runCatching {
                        json.decodeFromString(TaskCloseDto.serializer(), entry.payload)
                    }.getOrNull()
                    if (body == null) {
                        // Nieczytelny wpis nigdy się nie wyśle — kasujemy, żeby
                        // nie blokował reszty kolejki.
                        mutationDao.delete(entry.targetId, entry.kind)
                        continue
                    }
                    try {
                        val updated = api.closeProjectTask(entry.targetId, body)
                        dao.upsertTask(updated.toEntity(updated.projectId ?: ""))
                        mutationDao.delete(entry.targetId, entry.kind)
                    } catch (e: IOException) {
                        return ProjectSyncResult.RETRY
                    } catch (e: Exception) {
                        // Serwer odmówił (np. zadanie skasowane) — tego nie da
                        // się wysłać nigdy. Zdejmujemy z kolejki.
                        mutationDao.delete(entry.targetId, entry.kind)
                    }
                }

                KIND_CREATE_IDEA -> {
                    val body = runCatching {
                        json.decodeFromString(IdeaCreateDto.serializer(), entry.payload)
                    }.getOrNull()
                    if (body == null) {
                        mutationDao.delete(entry.targetId, entry.kind)
                        continue
                    }
                    try {
                        val created = api.createIdea(body)
                        // Lokalny wiersz ustępuje miejsca temu z serwera —
                        // inaczej pomysł widniałby na liście dwa razy.
                        dao.deleteProject(entry.targetId)
                        dao.upsertProject(created.toEntity(System.currentTimeMillis()))
                        mutationDao.delete(entry.targetId, entry.kind)
                    } catch (e: IOException) {
                        return ProjectSyncResult.RETRY
                    } catch (e: Exception) {
                        // Odmowa serwera: zostawiamy wiersz w cache oznaczony
                        // jako lokalny, ale zdejmujemy go z kolejki.
                        mutationDao.delete(entry.targetId, entry.kind)
                    }
                }

                else -> mutationDao.delete(entry.targetId, entry.kind)
            }
        }
        return ProjectSyncResult.DONE
    }
}
