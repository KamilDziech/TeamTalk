package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.local.dao.TaskDao
import com.ekotak.teamtalk.data.local.dao.TaskMutationDao
import com.ekotak.teamtalk.data.local.entity.TaskEntity
import com.ekotak.teamtalk.data.local.entity.TaskMutationEntity
import com.ekotak.teamtalk.data.local.entity.TaskMutationEntity.Companion.FIELD_CREATE
import com.ekotak.teamtalk.data.local.entity.TaskMutationEntity.Companion.LOCAL_ID_PREFIX
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.data.mapper.applyPatch
import com.ekotak.teamtalk.data.mapper.isoNow
import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.mapper.toEntity
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.AddCommentRequest
import com.ekotak.teamtalk.data.remote.dto.CreateTaskRequest
import com.ekotak.teamtalk.data.remote.dto.PreferenceSetRequest
import com.ekotak.teamtalk.data.remote.dto.QueuedTaskCreate
import com.ekotak.teamtalk.data.remote.dto.buildTaskPatch
import com.ekotak.teamtalk.data.sync.TaskSyncScheduler
import com.ekotak.teamtalk.domain.model.Task
import com.ekotak.teamtalk.domain.model.TaskAttachment
import com.ekotak.teamtalk.domain.model.TaskComment
import com.ekotak.teamtalk.domain.model.TaskLink
import com.ekotak.teamtalk.domain.model.TaskMember
import com.ekotak.teamtalk.domain.model.TaskPatch
import com.ekotak.teamtalk.domain.model.TaskPriority
import com.ekotak.teamtalk.domain.model.TaskProject
import com.ekotak.teamtalk.domain.model.TaskSection
import com.ekotak.teamtalk.domain.model.TaskStatus
import com.ekotak.teamtalk.domain.repository.TaskRepository
import com.ekotak.teamtalk.domain.repository.TaskSyncResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

class TaskRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
    private val taskDao: TaskDao,
    private val mutationDao: TaskMutationDao,
    private val sessionPreferences: SessionPreferences,
    private val syncScheduler: TaskSyncScheduler,
) : TaskRepository {

    /** Do odczytu zakolejkowanych fragmentów ciała żądania. */
    private val json = Json

    /**
     * Czysty strumień z cache — bez dociągania w tle. Pobranie z sieci zleca
     * ekran przez [refreshTasks], dzięki czemu wie, kiedy pokazać kręciołek
     * i kiedy powiedzieć o awarii, zamiast zgadywać po pustej liście.
     */
    override fun observeTasks(): Flow<List<Task>> =
        taskDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    /** Błąd świadomie leci dalej — pull-to-refresh musi umieć pokazać awarię. */
    override suspend fun refreshTasks() {
        taskDao.replaceAll(api.getTasks().map { it.toEntity() })
    }

    override fun observeDealTasks(dealId: String): Flow<List<Task>> =
        taskDao.observeForDeal(dealId).map { rows -> rows.map { it.toDomain() } }

    /**
     * Wycinek jednego deala. Brak sieci nie jest tu awarią — zakładka ma się
     * otworzyć na ostatniej kopii i tylko powiedzieć, że tak jest; odmowa
     * serwera (brak `tasks.view`, deal skasowany) leci dalej wyjątkiem, bo to
     * już nie jest „spróbuj za chwilę".
     */
    override suspend fun refreshDealTasks(dealId: String): Boolean = try {
        taskDao.replaceForDeal(dealId, api.getDealTasks(dealId).map { it.toEntity() })
        true
    } catch (_: IOException) {
        false
    }

    /**
     * Ręczna kolejność zadań. Klucz `tasks.order` jest wspólny z panelem, więc
     * telefon czyta dokładnie to ułożenie, które ktoś poskładał myszą — i pisze
     * do tego samego miejsca. Brak zapisanej wartości i brak zasięgu wyglądają
     * dla listy tak samo: zostaje kolejność domyślna.
     */
    override suspend fun getTasksOrder(): List<String> = try {
        val raw = api.getPreference(TASKS_ORDER_KEY).value
        if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            (json.parseToJsonElement(raw) as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                .orEmpty()
        }
    } catch (_: Exception) {
        emptyList()
    }

    override suspend fun saveTasksOrder(ids: List<String>) {
        api.putPreference(
            TASKS_ORDER_KEY,
            PreferenceSetRequest(value = JsonArray(ids.map { JsonPrimitive(it) }).toString()),
        )
    }

    /**
     * Świeże zadanie z serwera; cache aktualizujemy przy okazji. Zadania
     * założonego bez zasięgu serwer jeszcze nie zna, a i przy zwykłym zadaniu
     * karta ma się otworzyć w tunelu — dlatego oba przypadki schodzą do cache
     * zamiast pokazywać awarię.
     */
    override suspend fun getTask(id: String): Task {
        if (id.startsWith(LOCAL_ID_PREFIX)) {
            return taskDao.getById(id)?.toDomain() ?: error("Brak zadania $id w pamięci telefonu.")
        }
        return try {
            val dto = api.getTask(id)
            taskDao.upsert(dto.toEntity())
            dto.toDomain()
        } catch (e: IOException) {
            taskDao.getById(id)?.toDomain() ?: throw e
        }
    }

    override suspend fun getComments(taskId: String): List<TaskComment> {
        val me = sessionPreferences.session.first()?.userId
        return api.getTaskComments(taskId).map { it.toDomain(me) }
    }

    override suspend fun addComment(
        taskId: String,
        body: String,
        mentions: List<String>,
    ): TaskComment {
        val me = sessionPreferences.session.first()?.userId
        return api.addTaskComment(taskId, AddCommentRequest(body = body, mentions = mentions))
            .toDomain(me)
    }

    /**
     * Zmiana idzie wprost na serwer, a gdy sieci nie ma — do kolejki i do
     * cache, żeby człowiek zobaczył swoją decyzję od razu. Rozróżnienie jest
     * tu istotne: brak łączności (`IOException`) da się nadrobić później,
     * odmowa serwera (`HttpException`: 403, 404, 422) nie — taką zmianę
     * puszczamy dalej jako błąd, zamiast wozić ją w kółko po kolejce.
     */
    override suspend fun updateTask(id: String, patch: TaskPatch): Task {
        val body = buildTaskPatch(patch)
        // Zadania, którego serwer jeszcze nie zna (założone bez zasięgu), nie ma
        // jak łatać po sieci — zmiana dokłada się do jego kolejki i do cache.
        if (id.startsWith(LOCAL_ID_PREFIX)) {
            return enqueue(id, body, patch) ?: error("Brak zadania $id w cache.")
        }
        return try {
            val dto = api.updateTask(id, body)
            // Pola, które właśnie poszły, nie mają po co czekać w kolejce.
            mutationDao.delete(id, body.keys.toList())
            taskDao.upsert(dto.toEntity())
            dto.toDomain()
        } catch (e: IOException) {
            enqueue(id, body, patch) ?: throw e
        }
    }

    /**
     * Kolejkuje zmianę i nakłada ją na cache. Zwraca `null`, gdy zadania nie ma
     * lokalnie — wtedy nie ma czego pokazać ani do czego wrócić, więc niech
     * zawoła o tym pierwotny błąd sieci.
     */
    private suspend fun enqueue(id: String, body: JsonObject, patch: TaskPatch): Task? {
        val cached = taskDao.getById(id) ?: return null
        val now = System.currentTimeMillis()
        mutationDao.upsertAll(
            body.map { (field, value) ->
                TaskMutationEntity(
                    taskId = id,
                    field = field,
                    payload = JsonObject(mapOf(field to value)).toString(),
                    createdAt = now,
                )
            },
        )
        val local = cached.applyPatch(patch)
        taskDao.upsert(local)
        syncScheduler.scheduleSync()
        return local.toDomain()
    }

    override suspend fun deleteTask(id: String) {
        // Zadanie, które nigdy nie poszło na serwer, kasuje się samą kolejką —
        // wysyłanie `DELETE` na lokalne id skończyłoby się czterysta czwórką.
        if (!id.startsWith(LOCAL_ID_PREFIX)) api.deleteTask(id)
        // Kolejka idzie do kosza razem z zadaniem — nie ma już czego łatać.
        mutationDao.deleteForTask(id)
        taskDao.deleteById(id)
    }

    // ── Załączniki ────────────────────────────────────────────────────────────
    // Bez cache: plik na karcie zadania ogląda się rzadko, a trzymanie kopii
    // każdego zdjęcia z montażu w pamięci telefonu kosztowałoby więcej, niż
    // warte jest jego offline'owe pokazanie.

    override suspend fun getAttachments(taskId: String): List<TaskAttachment> =
        api.getTaskAttachments(taskId).map { it.toDomain() }

    override suspend fun uploadAttachment(
        taskId: String,
        name: String,
        contentType: String,
        bytes: ByteArray,
    ): TaskAttachment {
        val part = MultipartBody.Part.createFormData(
            "file",
            name,
            bytes.toRequestBody(contentType.toMediaTypeOrNull()),
        )
        return api.uploadTaskAttachment(taskId, part).toDomain()
    }

    /** Strumień prosto do pliku — zdjęcie z montażu nie musi przechodzić przez RAM. */
    override suspend fun downloadAttachmentTo(id: String, target: File) {
        api.downloadTaskAttachment(id).byteStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
    }

    override suspend fun deleteAttachment(id: String) = api.deleteTaskAttachment(id)

    override fun observePendingTaskIds(): Flow<Set<String>> =
        mutationDao.observePendingTaskIds().map { it.toSet() }

    /**
     * Opróżnianie kolejki. Zmiany jednego zadania scalamy w jedno żądanie —
     * pola i tak są rozłączne, a serwer nie musi oglądać trzech okrążeń.
     * Kolejność ma znaczenie tylko między zadaniami, więc idziemy od najstarszej.
     */
    override suspend fun syncPendingMutations(): TaskSyncResult {
        val pending = mutationDao.getAll()
        if (pending.isEmpty()) return TaskSyncResult.DONE

        var networkFailed = false
        for ((queuedId, entries) in pending.groupBy { it.taskId }) {
            // Tworzenie idzie pierwsze: dopiero po nim reszta kolejki wie, pod
            // jakim identyfikatorem serwer trzyma to zadanie.
            val created = entries.firstOrNull { it.field == FIELD_CREATE }
            var taskId = queuedId
            if (created != null) {
                when (val result = sendQueuedCreate(queuedId, created.payload)) {
                    is CreateOutcome.Sent -> taskId = result.id
                    CreateOutcome.Retry -> {
                        networkFailed = true
                        continue
                    }
                    // Zadania nie da się wysłać nigdy — zdjęte z kolejki razem
                    // z jego łatkami, żeby nie wracało przy każdym przebiegu.
                    CreateOutcome.Dropped -> continue
                }
            }

            val rows = entries.filter { it.field != FIELD_CREATE }
            if (rows.isEmpty()) continue
            val body = buildJsonObject {
                rows.forEach { row ->
                    (json.parseToJsonElement(row.payload) as? JsonObject)
                        ?.forEach { (key, value) -> put(key, value) }
                }
            }
            try {
                val dto = api.updateTask(taskId, body)
                mutationDao.delete(taskId, rows.map { it.field })
                taskDao.upsert(dto.toEntity())
            } catch (_: IOException) {
                // Sieć znowu padła — reszta kolejki poczeka na następny przebieg.
                networkFailed = true
            } catch (e: HttpException) {
                // Serwer odrzucił zmianę: 404 = zadania już nie ma, 403/422 =
                // zmiana nie do przyjęcia. Ponowienie nic nie zmieni, więc
                // porzucamy wpis i zostawiamy prawdę serwera — ale mówimy o tym
                // człowiekowi, bo to jego decyzja przepadła.
                mutationDao.delete(taskId, rows.map { it.field })
                val title = taskDao.getById(taskId)?.title
                if (e.code() == 404) taskDao.deleteById(taskId)
                sessionPreferences.saveSyncProblem(discardMessage(title, e.code()))
            }
        }
        return if (networkFailed) TaskSyncResult.RETRY else TaskSyncResult.DONE
    }

    /** Co się stało z zakolejkowanym tworzeniem — patrz [syncPendingMutations]. */
    private sealed interface CreateOutcome {
        data class Sent(val id: String) : CreateOutcome
        data object Retry : CreateOutcome
        data object Dropped : CreateOutcome
    }

    /**
     * Wysyła zadanie założone bez zasięgu i przepisuje je w cache pod
     * identyfikatorem z serwera. Reszta kolejki tego zadania (odhaczenie,
     * termin zmieniony jeszcze przed wysyłką) dostaje nowy klucz — inaczej
     * poszłaby `PATCH`-em na nieistniejące `local:…`.
     */
    private suspend fun sendQueuedCreate(localId: String, payload: String): CreateOutcome {
        val queued = runCatching {
            json.decodeFromString(QueuedTaskCreate.serializer(), payload)
        }.getOrNull()
        if (queued == null) {
            // Nieczytelnego wpisu nie wyślemy nigdy — kasujemy, żeby nie blokował
            // reszty kolejki; zadanie zostaje w cache jako lokalne.
            mutationDao.deleteForTask(localId)
            return CreateOutcome.Dropped
        }
        val dto = try {
            when {
                queued.dealId != null -> api.createDealTask(queued.dealId, queued.request)
                queued.projectId != null -> api.createProjectTask(queued.projectId, queued.request)
                else -> api.createTask(queued.request)
            }
        } catch (_: IOException) {
            return CreateOutcome.Retry
        } catch (e: HttpException) {
            mutationDao.deleteForTask(localId)
            taskDao.deleteById(localId)
            sessionPreferences.saveSyncProblem(createDiscardMessage(queued.request.title, e.code()))
            return CreateOutcome.Dropped
        }
        taskDao.deleteById(localId)
        taskDao.upsert(dto.toEntity())
        mutationDao.delete(localId, listOf(FIELD_CREATE))
        mutationDao.rekeyTask(localId, dto.id)
        return CreateOutcome.Sent(dto.id)
    }

    private fun createDiscardMessage(title: String, code: Int): String = when (code) {
        403 -> "Zadanie „$title” przepadło — brak uprawnień do jego założenia."
        404 -> "Zadanie „$title” przepadło — deal zniknął z panelu."
        else -> "Zadanie „$title” przepadło — serwer je odrzucił (kod $code)."
    }

    private fun discardMessage(taskTitle: String?, code: Int): String {
        val what = if (taskTitle != null) "zadania „$taskTitle”" else "zadania"
        return when (code) {
            404 -> "Zmiana $what przepadła — zadanie zniknęło z panelu."
            403 -> "Zmiana $what przepadła — brak uprawnień."
            else -> "Zmiana $what przepadła — serwer ją odrzucił (kod $code)."
        }
    }

    override suspend fun getMembers(): List<TaskMember> =
        api.getTaskMembers().map { it.toDomain() }

    override suspend fun getProjects(): List<TaskProject> =
        api.getProjects().map { it.toDomain() }

    override suspend fun createTask(
        title: String,
        description: String?,
        assigneeId: String?,
        dueAt: String?,
        priority: TaskPriority,
        link: TaskLink,
        section: TaskSection?,
    ): Task {
        val request = CreateTaskRequest(
            title = title,
            description = description,
            assigneeId = assigneeId,
            dueAt = dueAt,
            priority = priority.wire,
            section = section?.wire,
        )
        return try {
            // Ciało jest identyczne dla wszystkich trzech ścieżek — różni je adres.
            val dto = when (link) {
                is TaskLink.None -> api.createTask(request)
                is TaskLink.Deal -> api.createDealTask(link.dealId, request)
                is TaskLink.Project -> api.createProjectTask(link.projectId, request)
            }
            // Nowe zadanie ląduje w cache od razu — lista pokaże je bez odświeżania.
            taskDao.upsert(dto.toEntity())
            dto.toDomain()
        } catch (_: IOException) {
            enqueueCreate(request, link).toDomain()
        }
    }

    /**
     * Zadanie spisane bez zasięgu: lokalne id, wiersz w cache i całe `POST`
     * w kolejce. Człowiek widzi swoje zadanie na liście od razu — ze znacznikiem
     * „czeka na wysyłkę", bo serwer jeszcze o nim nie wie (ustalenie 2026-09-08).
     */
    private suspend fun enqueueCreate(request: CreateTaskRequest, link: TaskLink): TaskEntity {
        val localId = LOCAL_ID_PREFIX + UUID.randomUUID()
        val now = System.currentTimeMillis()
        val dealId = (link as? TaskLink.Deal)?.dealId
        val projectId = (link as? TaskLink.Project)?.projectId
        val entity = TaskEntity(
            id = localId,
            title = request.title,
            description = request.description,
            assigneeId = request.assigneeId,
            assigneeEmail = null,
            dueAt = request.dueAt,
            status = TaskStatus.OPEN.wire,
            priority = request.priority ?: TaskPriority.NORMAL.wire,
            section = request.section,
            estimatedMinutes = null,
            slaHours = null,
            commentCount = 0,
            createdBy = sessionPreferences.session.first()?.userId,
            createdAt = isoNow(now),
            updatedAt = null,
            dealId = dealId,
            // Nazwy klienta serwer dokleja przy odczycie; do czasu wysyłki
            // wiersz stoi w zakładce deala, gdzie i tak wiadomo, czyj jest.
            dealName = null,
            projectId = projectId,
            projectName = null,
        )
        taskDao.upsert(entity)
        mutationDao.upsertAll(
            listOf(
                TaskMutationEntity(
                    taskId = localId,
                    field = FIELD_CREATE,
                    payload = json.encodeToString(
                        QueuedTaskCreate.serializer(),
                        QueuedTaskCreate(request, dealId = dealId, projectId = projectId),
                    ),
                    createdAt = now,
                ),
            ),
        )
        syncScheduler.scheduleSync()
        return entity
    }

    private companion object {
        /** Klucz preferencji z ręczną kolejnością zadań — wspólny z panelem. */
        const val TASKS_ORDER_KEY = "tasks.order"
    }
}
