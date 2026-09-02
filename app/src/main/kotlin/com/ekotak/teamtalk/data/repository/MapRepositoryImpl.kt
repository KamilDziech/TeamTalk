package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.local.dao.MapPointDao
import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.mapper.toEntity
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.ClientResponseDto
import com.ekotak.teamtalk.data.remote.dto.ServiceJobResponseDto
import com.ekotak.teamtalk.data.remote.dto.TaskMemberDto
import com.ekotak.teamtalk.data.remote.dto.WarrantyCardDto
import com.ekotak.teamtalk.domain.model.DealStage
import com.ekotak.teamtalk.domain.model.MapBadge
import com.ekotak.teamtalk.domain.model.MapKind
import com.ekotak.teamtalk.domain.model.MapPalette
import com.ekotak.teamtalk.domain.model.MapPoint
import com.ekotak.teamtalk.domain.model.MapSnapshot
import com.ekotak.teamtalk.domain.model.PIPELINE_STAGES
import com.ekotak.teamtalk.domain.model.PlaceSuggestion
import com.ekotak.teamtalk.domain.model.ServiceJobStatus
import com.ekotak.teamtalk.domain.model.ServiceJobType
import com.ekotak.teamtalk.domain.model.WarrantyCardStatus
import com.ekotak.teamtalk.domain.repository.MapRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Składanie punktów mapy — mobilny odpowiednik `web/src/app/app/map/page.tsx`.
 * Panel liczy „badge" (kolor, etykieta, kolejność, litera) po stronie serwera;
 * tutaj robi to warstwa danych, żeby ekran rysował markery i chipy identycznie,
 * niezależnie od źródła punktu.
 *
 * Deale i klienci są wymagani — bez nich nie ma mapy, więc błąd leci wyżej i
 * ekran zostaje przy poprzedniej migawce z cache. Pozostałe źródła są miękkie:
 * brak `/service-jobs` czy `/warranty-cards` (np. cofnięte uprawnienie serwisu)
 * daje po prostu pusty widok „Serwisy"/„Przeglądy", a nie błąd całej mapy.
 */
class MapRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
    private val dao: MapPointDao,
) : MapRepository {

    override fun observeSnapshot(): Flow<MapSnapshot> = dao.observeAll().map { rows ->
        MapSnapshot(
            points = rows.map { it.toDomain() },
            syncedAt = rows.firstOrNull()?.syncedAt,
        )
    }

    override suspend fun suggestPlaces(query: String): List<PlaceSuggestion> {
        if (query.trim().length < 3) return emptyList()
        return runCatching { api.suggestPlaces(query.trim()) }
            .getOrDefault(emptyList())
            .map { PlaceSuggestion(label = it.label, lat = it.lat, lng = it.lng) }
    }

    override suspend fun refresh() = coroutineScope {
        val dealsAsync = async { api.getDeals() }
        val clientsAsync = async { api.getClients() }
        val membersAsync = softAsync { api.getTaskMembers() }
        val jobsAsync = softAsync { api.getServiceJobs() }
        val techniciansAsync = softAsync { api.getTechnicians() }
        val cardsAsync = softAsync { api.getWarrantyCards() }
        val cardGeoAsync = softAsync { api.getWarrantyCardsGeo() }
        val installsAsync = softMapAsync { api.getCurrentInstallations() }
        val categoriesAsync = softAsync { api.getCategories() }

        val deals = dealsAsync.await()
        val clients = clientsAsync.await()
        val clientById = clients.associateBy { it.id }

        // Nazwy kategorii głównych — filtr „rodzaj instalacji" pokazuje je tak
        // samo jak panel (kategoria główna, nie pełna ścieżka technologii).
        val rootNames = categoriesAsync.await()
            .filter { it.parentId == null }
            .associate { it.id to it.name }
        val installsByDeal = installsAsync.await()
        fun installsOf(dealId: String?): List<String> =
            dealId?.let { id -> installsByDeal[id]?.mapNotNull { rootNames[it] } }.orEmpty()

        val people = peopleLabels(membersAsync.await(), techniciansAsync.await().map { it.id to it.email })
        val points = mutableListOf<MapPoint>()

        // ── Klienci (deale) ──────────────────────────────────────────────────
        for (deal in deals) {
            val client = clientById[deal.clientId] ?: continue
            val stage = DealStage.fromWire(deal.stage) ?: DealStage.LEAD
            val finished = stage == DealStage.LOST || stage == DealStage.ZAKONCZONY
            points += MapPoint(
                id = deal.id,
                kind = if (finished) MapKind.FINISHED else MapKind.CURRENT,
                lat = client.geo?.lat,
                lng = client.geo?.lng,
                name = client.fullName(),
                city = client.city ?: client.postalCode,
                address = client.mapAddress(),
                phone = client.phone ?: client.phone2,
                installs = installsOf(deal.id),
                ownerId = deal.ownerId.takeIf { it.isNotBlank() },
                ownerLabel = people[deal.ownerId],
                stageOwnerId = deal.stageOwnerId,
                stageOwnerLabel = deal.stageOwnerId?.let { people[it] },
                technicianId = null,
                technicianLabel = null,
                badge = MapBadge(
                    key = stage.wire,
                    label = stage.label,
                    colorArgb = MapPalette.stageColor(stage),
                    order = STAGE_ORDER.indexOf(stage),
                    letter = stage.label.take(1).uppercase(),
                ),
                dealId = deal.id,
                clientId = client.id,
            )
        }

        // ── Serwisy i przeglądy ze zleceń serwisowych ────────────────────────
        for (job in jobsAsync.await()) {
            // Zlecenie bez klienta (zapis „na szybko") nie ma czego pokazać.
            val client = job.clientId?.let { clientById[it] } ?: continue
            points += job.toPoint(client, people, installsOf(job.dealId))
        }

        // ── Przeglądy gwarancyjne (karty Panasonic) ──────────────────────────
        val cardGeo = cardGeoAsync.await().associateBy { it.id }
        for (card in cardsAsync.await()) {
            val geo = cardGeo[card.id]
            val technicianId = card.technicianId()
            val status = WarrantyCardStatus.fromWire(card.status)
            points += MapPoint(
                id = card.id,
                kind = MapKind.INSPECTION,
                lat = geo?.lat,
                lng = geo?.lng,
                name = card.name,
                city = geo?.city?.takeIf { it.isNotBlank() } ?: card.location,
                address = card.location,
                phone = null,
                installs = emptyList(),
                ownerId = null,
                ownerLabel = null,
                stageOwnerId = null,
                stageOwnerLabel = null,
                technicianId = technicianId,
                technicianLabel = technicianId?.let { people[it] },
                badge = MapBadge(
                    key = status.wire,
                    label = status.label,
                    colorArgb = MapPalette.warrantyStatusColor(status),
                    order = status.ordinal,
                    letter = "P",
                ),
                dealId = null,
                clientId = null,
            )
        }

        val now = System.currentTimeMillis()
        dao.replaceAll(points.map { it.toEntity(now) })
    }

    /** Zlecenie serwisowe jako punkt — awaria po SLA ma własny badge (czerwień). */
    private fun ServiceJobResponseDto.toPoint(
        client: ClientResponseDto,
        people: Map<String, String>,
        installs: List<String>,
    ): MapPoint {
        val jobType = ServiceJobType.fromWire(type)
        val jobStatus = ServiceJobStatus.fromWire(status)
        val breach = slaBreached && jobType == ServiceJobType.AWARIA
        return MapPoint(
            id = id,
            kind = if (jobType == ServiceJobType.AWARIA) MapKind.SERVICE else MapKind.INSPECTION,
            lat = client.geo?.lat,
            lng = client.geo?.lng,
            name = client.fullName(),
            city = client.city ?: client.postalCode,
            address = client.mapAddress(),
            phone = client.phone ?: client.phone2,
            installs = installs,
            ownerId = null,
            ownerLabel = null,
            stageOwnerId = null,
            stageOwnerLabel = null,
            technicianId = technicianId,
            technicianLabel = technicianId?.let { people[it] },
            badge = MapBadge(
                key = if (breach) "sla" else jobStatus.wire,
                label = if (breach) "Po SLA" else jobStatus.label,
                colorArgb = if (breach) MapPalette.SLA else MapPalette.serviceStatusColor(jobStatus),
                // Po SLA przed wszystkimi statusami — to ono ma być pierwszym chipem.
                order = if (breach) -1 else SERVICE_ORDER.indexOf(jobStatus),
                letter = jobType.label.take(1).uppercase(),
            ),
            dealId = dealId,
            clientId = client.id,
        )
    }

    /**
     * Etykiety osób do filtrów: zespół (imię i nazwisko, w razie braku e-mail)
     * plus serwisanci, którzy w zespole nie występują — tak samo jak w panelu.
     */
    private fun peopleLabels(
        members: List<TaskMemberDto>,
        technicians: List<Pair<String, String>>,
    ): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (m in members) {
            val name = listOfNotNull(m.firstName, m.lastName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .trim()
            out[m.id] = name.ifBlank { m.email }
        }
        for ((id, email) in technicians) out.putIfAbsent(id, email)
        return out
    }

    /** Serwisant karty: technik najbliższego niezrealizowanego przeglądu. */
    private fun WarrantyCardDto.technicianId(): String? =
        inspections.firstOrNull { it.technicianId != null && it.computedStatus != "done" }?.technicianId
            ?: inspections.firstOrNull { it.technicianId != null }?.technicianId

    /** Źródło miękkie: brak dostępu albo awaria → pusta lista, nie błąd mapy. */
    private fun <T> CoroutineScope.softAsync(
        block: suspend () -> List<T>,
    ): Deferred<List<T>> = async { runCatching { block() }.getOrDefault(emptyList()) }

    /** Wariant [softAsync] dla źródeł zwracających mapę. */
    private fun <K, V> CoroutineScope.softMapAsync(
        block: suspend () -> Map<K, V>,
    ): Deferred<Map<K, V>> = async { runCatching { block() }.getOrDefault(emptyMap()) }

    private companion object {
        /** Pełna kolejność etapów (lejek + zamknięte) do stabilnego sortu chipów. */
        val STAGE_ORDER: List<DealStage> =
            PIPELINE_STAGES + listOf(DealStage.LOST, DealStage.ZAKONCZONY)

        val SERVICE_ORDER: List<ServiceJobStatus> = listOf(
            ServiceJobStatus.NEW,
            ServiceJobStatus.IN_PROGRESS,
            ServiceJobStatus.DONE,
        )
    }
}

/** Nazwa klienta w kształcie panelu (`fullName`) — pusta zastąpiona telefonem. */
private fun ClientResponseDto.fullName(): String =
    listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ").trim()
        .ifBlank { phone ?: "Klient bez nazwy" }

/** Adres do nawigacji: pełny, a gdy go brak — kod pocztowy z miejscowością. */
private fun ClientResponseDto.mapAddress(): String? =
    address?.takeIf { it.isNotBlank() }
        ?: listOfNotNull(postalCode, city).filter { it.isNotBlank() }
            .joinToString(" ")
            .takeIf { it.isNotBlank() }
