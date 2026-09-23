package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.local.dao.GoalDao
import com.ekotak.teamtalk.data.local.entity.GoalCatalogEntity
import com.ekotak.teamtalk.data.local.entity.GoalMutationEntity
import com.ekotak.teamtalk.data.local.entity.GoalMutationEntity.Companion.FIELD_CHECKIN
import com.ekotak.teamtalk.data.local.entity.GoalMutationEntity.Companion.FIELD_CLOSE
import com.ekotak.teamtalk.data.local.entity.GoalMutationEntity.Companion.FIELD_CREATE
import com.ekotak.teamtalk.data.local.entity.GoalMutationEntity.Companion.FIELD_DELETE
import com.ekotak.teamtalk.data.local.entity.GoalMutationEntity.Companion.FIELD_PATCH
import com.ekotak.teamtalk.data.local.entity.GoalMutationEntity.Companion.LOCAL_ID_PREFIX
import com.ekotak.teamtalk.data.local.entity.GoalTrendEntity
import com.ekotak.teamtalk.data.local.entity.GoalViewEntity
import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.mapper.toDto
import com.ekotak.teamtalk.data.mapper.toLocalDto
import com.ekotak.teamtalk.data.mapper.withDraft
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.CompanyGoalsDto
import com.ekotak.teamtalk.data.remote.dto.GoalCatalogDto
import com.ekotak.teamtalk.data.remote.dto.GoalCheckinDto
import com.ekotak.teamtalk.data.remote.dto.GoalDto
import com.ekotak.teamtalk.data.remote.dto.GoalTrendDto
import com.ekotak.teamtalk.data.remote.dto.GoalWriteDto
import com.ekotak.teamtalk.data.remote.dto.PersonalGoalsDto
import com.ekotak.teamtalk.data.remote.dto.TeamGoalsDto
import com.ekotak.teamtalk.data.sync.GoalSyncScheduler
import com.ekotak.teamtalk.domain.model.CompanyGoals
import com.ekotak.teamtalk.domain.model.Goal
import com.ekotak.teamtalk.domain.model.GoalCatalog
import com.ekotak.teamtalk.domain.model.GoalDraft
import com.ekotak.teamtalk.domain.model.GoalScope
import com.ekotak.teamtalk.domain.model.GoalTrend
import com.ekotak.teamtalk.domain.model.PersonalGoals
import com.ekotak.teamtalk.domain.model.TeamGoals
import com.ekotak.teamtalk.domain.repository.GoalRepository
import com.ekotak.teamtalk.domain.repository.GoalSyncRejection
import com.ekotak.teamtalk.domain.repository.GoalSyncResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Moduł Cele — mobilny odpowiednik `/app/goals` z panelu.
 *
 * Źródłem prawdy dla ekranu jest Room: cele otwierają się w busie bez zasięgu,
 * a sieć tylko dolewa świeże liczby. Zapis idzie wprost do API, a gdy sieci
 * nie ma — do kolejki i od razu do migawki, żeby człowiek zobaczył swój cel
 * (pełny offline, decyzja 2026-09-23).
 *
 * Migawkę trzymamy jako SUROWY JSON odpowiedzi: realizację, tempo i status
 * liczy wyłącznie serwer. Telefon dokłada do niej tylko to, co sam zrobił
 * i co czeka w kolejce — nigdy nie przelicza cudzych liczb po swojemu.
 */
@Singleton
class GoalRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
    private val dao: GoalDao,
    private val scheduler: GoalSyncScheduler,
) : GoalRepository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ── Klucze migawek ────────────────────────────────────────────────────────
    // Zakładka + jej parametry, więc przełączanie okresu i działu działa bez
    // sieci na wszystkim, co już raz było otwarte.

    private fun personalKey(period: String, userId: String?) =
        "personal:$period:${userId ?: "me"}"

    private fun teamKey(period: String, team: String) = "team:$period:$team"

    private fun companyKey(period: String) = "company:$period"

    // ── Odczyt ────────────────────────────────────────────────────────────────

    override fun observePersonal(period: String, userId: String?): Flow<PersonalGoals?> =
        combine(
            dao.observeView(personalKey(period, userId)),
            dao.observePendingIds(),
        ) { view, pending ->
            view?.let {
                runCatching {
                    json.decodeFromString(PersonalGoalsDto.serializer(), it.payload)
                        .toDomain(pending.toSet())
                }.getOrNull()
            }
        }

    override fun observeTeam(period: String, team: String): Flow<TeamGoals?> =
        combine(
            dao.observeView(teamKey(period, team)),
            dao.observePendingIds(),
        ) { view, pending ->
            view?.let {
                runCatching {
                    json.decodeFromString(TeamGoalsDto.serializer(), it.payload)
                        .toDomain(pending.toSet())
                }.getOrNull()
            }
        }

    override fun observeCompany(period: String): Flow<CompanyGoals?> =
        combine(
            dao.observeView(companyKey(period)),
            dao.observePendingIds(),
        ) { view, pending ->
            view?.let {
                runCatching {
                    json.decodeFromString(CompanyGoalsDto.serializer(), it.payload)
                        .toDomain(pending.toSet())
                }.getOrNull()
            }
        }

    override fun observeTrend(goalId: String): Flow<GoalTrend?> =
        dao.observeTrend(goalId).map { row ->
            row?.let {
                runCatching {
                    json.decodeFromString(GoalTrendDto.serializer(), it.payload).toDomain()
                }.getOrNull()
            }
        }

    override fun observeCatalog(): Flow<GoalCatalog?> =
        dao.observeCatalog().map { row ->
            row?.let {
                runCatching {
                    json.decodeFromString(GoalCatalogDto.serializer(), it.payload).toDomain()
                }.getOrNull()
            }
        }

    override fun observeSyncedAt(key: String): Flow<Long?> =
        dao.observeView(key).map { it?.syncedAt }

    override fun observePendingIds(): Flow<Set<String>> =
        dao.observePendingIds().map { it.toSet() }

    // ── Odświeżanie ───────────────────────────────────────────────────────────

    override suspend fun refreshPersonal(period: String, userId: String?) {
        // Najpierw kolejka: odpowiedź serwera cofnęłaby na ekranie cel,
        // o którym on jeszcze nie wie.
        runCatching { syncPendingMutations() }
        val dto = api.getPersonalGoals(period, userId)
        saveView(personalKey(period, userId), json.encodeToString(PersonalGoalsDto.serializer(), dto))
    }

    override suspend fun refreshTeam(period: String, team: String) {
        runCatching { syncPendingMutations() }
        val dto = api.getTeamGoals(period, team)
        saveView(teamKey(period, team), json.encodeToString(TeamGoalsDto.serializer(), dto))
    }

    override suspend fun refreshCompany(period: String) {
        runCatching { syncPendingMutations() }
        val dto = api.getCompanyGoals(period)
        saveView(companyKey(period), json.encodeToString(CompanyGoalsDto.serializer(), dto))
    }

    override suspend fun refreshTrend(goalId: String) {
        // Cel, którego serwer jeszcze nie zna, nie ma przebiegu — nie ma po co pytać.
        if (goalId.startsWith(LOCAL_ID_PREFIX)) return
        val dto = api.getGoalTrend(goalId)
        dao.upsertTrend(
            GoalTrendEntity(
                goalId = goalId,
                payload = json.encodeToString(GoalTrendDto.serializer(), dto),
                syncedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun refreshCatalog() {
        val dto = api.getGoalCatalog()
        dao.upsertCatalog(
            GoalCatalogEntity(
                payload = json.encodeToString(GoalCatalogDto.serializer(), dto),
                syncedAt = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun saveView(key: String, payload: String) {
        dao.upsertView(GoalViewEntity(key, payload, System.currentTimeMillis()))
    }

    // ── Zapis ─────────────────────────────────────────────────────────────────

    override suspend fun create(draft: GoalDraft): Goal {
        val body = draft.toDto()
        return try {
            val created = api.createGoal(body)
            upsertGoalInViews(created)
            created.toDomain()
        } catch (e: IOException) {
            // Cel zakłada się w rozmowie z człowiekiem — przy kawie, w aucie,
            // na budowie. Brak zasięgu nie może kasować tej decyzji.
            val localId = LOCAL_ID_PREFIX + UUID.randomUUID()
            val metric = cachedMetric(draft.metric)
            val local = draft.toLocalDto(
                localId = localId,
                metricLabel = metric?.label ?: draft.metric,
                unit = metric?.unit ?: "szt",
                source = metric?.source ?: "",
            )
            upsertGoalInViews(local)
            enqueue(localId, FIELD_CREATE, json.encodeToString(GoalWriteDto.serializer(), body))
            local.toDomain(pending = true)
        }
    }

    override suspend fun update(goalId: String, draft: GoalDraft) {
        // Cel, którego serwer jeszcze nie zna, poprawiamy w kolejce — poleci
        // już zmieniony, zamiast lecieć dwa razy.
        val body = draft.toDto()
        if (goalId.startsWith(LOCAL_ID_PREFIX)) {
            patchViews(goalId) { it.withDraft(draft) }
            enqueue(goalId, FIELD_CREATE, json.encodeToString(GoalWriteDto.serializer(), body))
            return
        }
        // Przy łatce zakres i adresat się nie zmieniają — serwer ich nie przyjmuje.
        val patch = body.copy(scope = null, ownerUserId = null, teamKey = null)
        try {
            val updated = api.updateGoal(goalId, patch)
            upsertGoalInViews(updated)
            dao.deleteMutation(goalId, FIELD_PATCH)
        } catch (e: IOException) {
            patchViews(goalId) { it.withDraft(draft) }
            enqueue(goalId, FIELD_PATCH, json.encodeToString(GoalWriteDto.serializer(), patch))
        }
    }

    override suspend fun delete(goalId: String) {
        if (goalId.startsWith(LOCAL_ID_PREFIX)) {
            // Cel istniał tylko w telefonie — znika razem z kolejką.
            dao.deleteMutations(goalId)
            removeGoalFromViews(goalId)
            return
        }
        try {
            api.deleteGoal(goalId)
            dao.deleteMutations(goalId)
            removeGoalFromViews(goalId)
        } catch (e: IOException) {
            removeGoalFromViews(goalId)
            // Skasowanie unieważnia wszystko, co na ten cel czekało.
            dao.deleteMutations(goalId)
            enqueue(goalId, FIELD_DELETE, "{}")
        }
    }

    override suspend fun checkin(goalId: String, value: Double, note: String) {
        val body = GoalCheckinDto(value = value, note = note)
        val payload = json.encodeToString(GoalCheckinDto.serializer(), body)
        if (goalId.startsWith(LOCAL_ID_PREFIX)) {
            patchViews(goalId) { it.copy(value = value) }
            enqueue(goalId, FIELD_CHECKIN, payload)
            return
        }
        try {
            api.checkinGoal(goalId, body)
            // Wartość i procent przelicza serwer — bierzemy je z odświeżenia,
            // a do tego czasu pokazujemy wpisaną liczbę.
            patchViews(goalId) { it.copy(value = value) }
            dao.deleteMutation(goalId, FIELD_CHECKIN)
        } catch (e: IOException) {
            patchViews(goalId) { it.copy(value = value) }
            enqueue(goalId, FIELD_CHECKIN, payload)
        }
    }

    override suspend fun close(goalId: String) {
        if (goalId.startsWith(LOCAL_ID_PREFIX)) {
            // Najpierw musi powstać na serwerze — zamknięcie poczeka w kolejce
            // i pójdzie zaraz po utworzeniu.
            patchViews(goalId) { it.copy(goalStatusRaw = "closed") }
            enqueue(goalId, FIELD_CLOSE, "{}")
            return
        }
        try {
            val closed = api.closeGoal(goalId)
            upsertGoalInViews(closed)
            dao.deleteMutation(goalId, FIELD_CLOSE)
        } catch (e: IOException) {
            patchViews(goalId) { it.copy(goalStatusRaw = "closed") }
            enqueue(goalId, FIELD_CLOSE, "{}")
        }
    }

    private suspend fun enqueue(goalId: String, field: String, payload: String) {
        dao.upsertMutation(
            GoalMutationEntity(
                goalId = goalId,
                field = field,
                payload = payload,
                createdAt = System.currentTimeMillis(),
            ),
        )
        scheduler.scheduleSync()
    }

    // ── Kolejka ───────────────────────────────────────────────────────────────

    override suspend fun syncPendingMutations(): GoalSyncResult {
        var sent = 0
        val rejected = mutableListOf<GoalSyncRejection>()
        var incomplete = false

        for (mutation in dao.getMutations()) {
            try {
                when (mutation.field) {
                    FIELD_CREATE -> {
                        val body = json.decodeFromString(GoalWriteDto.serializer(), mutation.payload)
                        val created = api.createGoal(body)
                        // Reszta wierszy tego celu (check-in, zamknięcie) czekała
                        // pod identyfikatorem lokalnym — przepinamy je na serwerowy,
                        // żeby poszły zaraz po utworzeniu, a nie przepadły.
                        dao.deleteMutation(mutation.goalId, FIELD_CREATE)
                        dao.remapMutations(mutation.goalId, created.id)
                        removeGoalFromViews(mutation.goalId)
                        upsertGoalInViews(created)
                    }
                    FIELD_PATCH -> {
                        val body = json.decodeFromString(GoalWriteDto.serializer(), mutation.payload)
                        val updated = api.updateGoal(mutation.goalId, body)
                        dao.deleteMutation(mutation.goalId, FIELD_PATCH)
                        upsertGoalInViews(updated)
                    }
                    FIELD_CHECKIN -> {
                        val body = json.decodeFromString(GoalCheckinDto.serializer(), mutation.payload)
                        api.checkinGoal(mutation.goalId, body)
                        dao.deleteMutation(mutation.goalId, FIELD_CHECKIN)
                    }
                    FIELD_CLOSE -> {
                        val closed = api.closeGoal(mutation.goalId)
                        dao.deleteMutation(mutation.goalId, FIELD_CLOSE)
                        upsertGoalInViews(closed)
                    }
                    FIELD_DELETE -> {
                        api.deleteGoal(mutation.goalId)
                        dao.deleteMutations(mutation.goalId)
                        removeGoalFromViews(mutation.goalId)
                    }
                    else -> dao.deleteMutation(mutation.goalId, mutation.field)
                }
                sent++
            } catch (e: IOException) {
                // Sieć znowu zawiodła — reszta kolejki poczeka na kolejny przebieg.
                incomplete = true
                break
            } catch (e: HttpException) {
                // Serwer odmówił: cel skasowano, okres już zamknięty albo pytający
                // stracił prawo zapisu. Wiersz wyrzucamy (ponawianie nic nie da),
                // ale mówimy o tym wprost — człowiek widział zapis jako zrobiony.
                rejected += GoalSyncRejection(
                    label = goalLabel(mutation.goalId) ?: mutation.goalId,
                    reason = httpReason(e),
                )
                dao.deleteMutation(mutation.goalId, mutation.field)
            }
        }

        if (sent > 0) {
            // Migawki zawierają liczby sprzed wysyłki — ekran dociągnie świeże.
            dao.clearTrends()
        }
        return GoalSyncResult(sent = sent, rejected = rejected, incomplete = incomplete)
    }

    private fun httpReason(e: HttpException): String? =
        when (e.code()) {
            403 -> "Brak uprawnień do zmiany tego celu."
            404 -> "Cel już nie istnieje."
            422 -> "Serwer odrzucił dane celu."
            else -> "Serwer odpowiedział ${e.code()}."
        }

    // ── Migawki: wstawianie i łatanie celu bez pytania serwera ───────────────

    /** Etykieta celu z cache — do treści powiadomienia o odmowie. */
    private suspend fun goalLabel(goalId: String): String? {
        for (view in dao.getAllViews()) {
            val found = decodeItems(view.payload).firstOrNull { it.id == goalId }
            if (found != null) return found.name
        }
        return null
    }

    /** Etykieta, jednostka i źródło miernika — do karty celu zapisanego offline. */
    private data class MetricLabel(val label: String, val unit: String, val source: String)

    /**
     * Opis miernika bez pytania sieci: najpierw katalog z cache (pobiera go
     * ekran przy wejściu w moduł), a gdyby go nie było — dowolny cel o tym
     * samym mierniku, który już leży w migawkach. Gdy nie ma ani jednego,
     * karta pokaże sam kod miernika i odświeżenie ją poprawi.
     */
    private suspend fun cachedMetric(code: String): MetricLabel? {
        dao.getCatalog()?.let { row ->
            val catalog = runCatching {
                json.decodeFromString(GoalCatalogDto.serializer(), row.payload)
            }.getOrNull()
            catalog?.metrics?.firstOrNull { it.code == code }?.let {
                return MetricLabel(it.label, it.unit, it.source)
            }
        }
        for (view in dao.getAllViews()) {
            val found = decodeItems(view.payload).firstOrNull { it.metric == code }
            if (found != null) return MetricLabel(found.metricLabel, found.unit, found.source)
        }
        return null
    }

    /** Wstawia albo podmienia cel we WSZYSTKICH migawkach, gdzie się mieści. */
    private suspend fun upsertGoalInViews(goal: GoalDto) {
        for (view in dao.getAllViews()) {
            val updated = rewrite(view.payload) { items ->
                val without = items.filterNot { it.id == goal.id }
                if (fitsView(view.key, goal)) without + goal else without
            } ?: continue
            dao.upsertView(view.copy(payload = updated))
        }
    }

    private suspend fun patchViews(goalId: String, transform: (GoalDto) -> GoalDto) {
        for (view in dao.getAllViews()) {
            val updated = rewrite(view.payload) { items ->
                items.map { if (it.id == goalId) transform(it) else it }
            } ?: continue
            dao.upsertView(view.copy(payload = updated))
        }
    }

    private suspend fun removeGoalFromViews(goalId: String) {
        for (view in dao.getAllViews()) {
            val updated = rewrite(view.payload) { items -> items.filterNot { it.id == goalId } }
                ?: continue
            dao.upsertView(view.copy(payload = updated))
        }
    }

    /**
     * Czy cel należy do tej zakładki. Klucz niesie komplet parametrów widoku
     * (`personal:2026-Q3:<userId>`), więc wystarczy porównać go z celem —
     * bez tego cel firmowy wskoczyłby do zakładki „Osobiste".
     */
    private fun fitsView(key: String, goal: GoalDto): Boolean {
        val parts = key.split(":")
        val scope = parts.getOrNull(0) ?: return false
        val period = parts.getOrNull(1) ?: return false
        if (goal.periodKey != period) return false
        return when (scope) {
            "personal" -> goal.scope == GoalScope.PERSONAL.wire
            "team" -> goal.scope == GoalScope.TEAM.wire && goal.teamKey == parts.getOrNull(2)
            "company" -> goal.scope == GoalScope.COMPANY.wire
            else -> false
        }
    }

    private fun decodeItems(payload: String): List<GoalDto> =
        runCatching {
            json.decodeFromString(PersonalGoalsDto.serializer(), payload).items
        }.getOrNull()
            ?: runCatching {
                json.decodeFromString(TeamGoalsDto.serializer(), payload).items
            }.getOrNull()
            ?: runCatching {
                json.decodeFromString(CompanyGoalsDto.serializer(), payload).items
            }.getOrNull()
            ?: emptyList()

    /**
     * Przepisuje listę celów w migawce, nie znając jej typu. Trzy próby, bo
     * każda zakładka ma własny kształt odpowiedzi, a klucz mówi tylko, która
     * to zakładka — kolejność prób jest bezpieczna, bo pola się nie pokrywają.
     */
    private fun rewrite(payload: String, transform: (List<GoalDto>) -> List<GoalDto>): String? {
        runCatching {
            val dto = json.decodeFromString(PersonalGoalsDto.serializer(), payload)
            if (dto.person != null || dto.managed.isNotEmpty()) {
                return json.encodeToString(
                    PersonalGoalsDto.serializer(),
                    dto.copy(items = transform(dto.items)),
                )
            }
        }
        runCatching {
            val dto = json.decodeFromString(TeamGoalsDto.serializer(), payload)
            if (dto.teamKey.isNotBlank()) {
                return json.encodeToString(
                    TeamGoalsDto.serializer(),
                    dto.copy(items = transform(dto.items)),
                )
            }
        }
        runCatching {
            val dto = json.decodeFromString(CompanyGoalsDto.serializer(), payload)
            return json.encodeToString(
                CompanyGoalsDto.serializer(),
                dto.copy(items = transform(dto.items)),
            )
        }
        return null
    }
}
