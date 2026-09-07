package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.local.dao.ContractDao
import com.ekotak.teamtalk.data.local.entity.ContractFillingEntity
import com.ekotak.teamtalk.data.local.entity.ContractMutationEntity
import com.ekotak.teamtalk.data.local.entity.ContractMutationEntity.Companion.KIND_APPROVE
import com.ekotak.teamtalk.data.local.entity.ContractMutationEntity.Companion.KIND_CANCEL
import com.ekotak.teamtalk.data.local.entity.ContractMutationEntity.Companion.KIND_CHANGE
import com.ekotak.teamtalk.data.local.entity.ContractMutationEntity.Companion.KIND_CREATE
import com.ekotak.teamtalk.data.local.entity.ContractMutationEntity.Companion.KIND_ORDER
import com.ekotak.teamtalk.data.local.entity.ContractMutationEntity.Companion.KIND_REJECT
import com.ekotak.teamtalk.data.local.entity.ContractMutationEntity.Companion.KIND_RESEND
import com.ekotak.teamtalk.data.local.entity.ContractMutationEntity.Companion.LOCAL_ID_PREFIX
import com.ekotak.teamtalk.data.local.entity.ContractPreviewEntity
import com.ekotak.teamtalk.data.local.entity.DealContractEntity
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.data.mapper.toChangeRequest
import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.mapper.toDto
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.ContractChangeRequest
import com.ekotak.teamtalk.data.remote.dto.ContractDto
import com.ekotak.teamtalk.data.remote.dto.ContractFillingDto
import com.ekotak.teamtalk.data.remote.dto.ContractRejectRequest
import com.ekotak.teamtalk.data.sync.ContractSyncScheduler
import com.ekotak.teamtalk.domain.model.ContractFilling
import com.ekotak.teamtalk.domain.model.ContractKind
import com.ekotak.teamtalk.domain.model.ContractPending
import com.ekotak.teamtalk.domain.model.ContractPreview
import com.ekotak.teamtalk.domain.model.ContractStatus
import com.ekotak.teamtalk.domain.model.DealContract
import com.ekotak.teamtalk.domain.repository.ContractMaterialLine
import com.ekotak.teamtalk.domain.repository.ContractOrderRebuild
import com.ekotak.teamtalk.domain.repository.ContractRepository
import com.ekotak.teamtalk.domain.repository.ContractSaveResult
import com.ekotak.teamtalk.domain.repository.ContractSyncResult
import com.ekotak.teamtalk.domain.repository.ContractsSnapshot
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zakładka „Umowa" — lista, treść i podgląd z cache Room, zapisy przez kolejkę.
 *
 * Kolejki NIE wpisujemy w cache jako faktu (tak samo jak w „Zamówieniu"
 * i „Rozliczeniu"): cache trzyma to, co powiedział serwer, a niewysłane decyzje
 * nakładamy na niego przy odczycie ([overlay]). Dzięki temu odpowiedź serwera
 * nigdy nie kasuje decyzji, o której serwer jeszcze nie wie.
 *
 * Odmowa serwera (403 bez `deal.manage`, 409 przy zmianie, której ktoś już
 * dokonał) to nie brak sieci — taki wpis wypada z kolejki z komunikatem,
 * zamiast wracać w kółko po tej samej odmowie.
 */
@Singleton
class ContractRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
    private val dao: ContractDao,
    private val syncScheduler: ContractSyncScheduler,
    private val sessionPreferences: SessionPreferences,
    private val json: Json,
) : ContractRepository {

    override suspend fun getContracts(dealId: String): ContractsSnapshot {
        var fromCache = false
        var error: String? = null
        try {
            val fresh = api.getDealContracts(dealId)
            dao.replaceContracts(dealId, fresh.map { it.toEntity(dealId) })
        } catch (_: IOException) {
            // Bez zasięgu zostaje ostatnie pobranie: umowę ogląda się u klienta,
            // a pusta lista wyglądałaby, jakby deal nie miał żadnej.
            fromCache = true
        } catch (e: Exception) {
            error = readErrorMessage(e)
        }

        val cached = dao.getContracts(dealId).mapNotNull { it.toDomain() }
        val queue = dao.getMutationsForDeal(dealId)
        return ContractsSnapshot(
            contracts = overlay(cached, queue),
            fromCache = fromCache,
            error = error,
        )
    }

    override suspend fun getFilling(dealId: String, contractId: String): ContractFilling? {
        // Umowa wystawiona bez zasięgu nie ma jeszcze migawki na serwerze —
        // jej treść leży w kolejce i to ona jest tu jedynym źródłem.
        if (contractId.startsWith(LOCAL_ID_PREFIX)) {
            val payload = dao.getMutationPayload(contractId, KIND_CREATE) ?: return null
            return decode<ContractFillingDto>(payload)?.toDomain()
        }
        try {
            val res = api.getContractFilling(dealId, contractId)
            dao.upsertFilling(
                ContractFillingEntity(
                    contractId = contractId,
                    dealId = dealId,
                    numer = res.numer,
                    wersja = res.wersja,
                    payload = json.encodeToString(ContractFillingDto.serializer(), res.wypelnienie),
                    syncedAt = System.currentTimeMillis(),
                ),
            )
            return res.wypelnienie.toDomain()
        } catch (_: Exception) {
            // Brak sieci albo odmowa — wracamy do ostatniej kopii. Formularz
            // zmiany bez treści umowy kazałby pisać dokument od zera.
            val cached = dao.getFilling(contractId) ?: return null
            return decode<ContractFillingDto>(cached.payload)?.toDomain()
        }
    }

    override suspend fun getPreview(dealId: String, contractId: String): ContractPreview? {
        // Dokumentu, który nie poszedł jeszcze na serwer, nie ma czego pokazać:
        // treść umowy składa panel przy generowaniu.
        if (contractId.startsWith(LOCAL_ID_PREFIX)) return null
        try {
            val res = api.getContractPreview(dealId, contractId)
            dao.upsertPreview(
                ContractPreviewEntity(
                    contractId = contractId,
                    numer = res.numer,
                    podpisana = res.podpisana,
                    html = res.html,
                    syncedAt = System.currentTimeMillis(),
                ),
            )
            return ContractPreview(res.numer, res.podpisana, res.html)
        } catch (_: Exception) {
            val cached = dao.getPreview(contractId) ?: return null
            return ContractPreview(cached.numer, cached.podpisana, cached.html, fromCache = true)
        }
    }

    override suspend fun downloadPdf(dealId: String, contractId: String, target: File) {
        api.downloadContractPdf(dealId, contractId).byteStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
    }

    /**
     * Materiał do migawki umowy bierzemy z AKTYWNEJ rezerwacji deala i tylko
     * z linii policzonych z audytu (`source = contract`). Wpisy dołożone ręcznie
     * przez magazyn zostają w magazynie: podpis klienta przelicza linie umowy,
     * a ręczne dokładki zachowuje — wciągnięcie ich do umowy zdublowałoby je.
     */
    override suspend fun materialsFromReservation(dealId: String): List<ContractMaterialLine> = try {
        api.getInventoryReservations(dealId = dealId)
            .filter { it.status == "active" && it.source == "contract" }
            .map {
                ContractMaterialLine(
                    productId = it.productId,
                    kod = it.itemCode,
                    nazwa = it.itemName,
                    ilosc = it.quantity,
                    jm = it.unit,
                    klucz = it.sourceKey,
                    uwaga = it.note,
                )
            }
    } catch (_: Exception) {
        // Brak magazynu (uprawnienie, sieć) nie może zablokować wystawienia
        // umowy — zakładka mówi wtedy wprost, że migawki materiału nie ma.
        emptyList()
    }

    override suspend fun generate(dealId: String, filling: ContractFilling): ContractSaveResult {
        val body = filling.toDto()
        return try {
            val res = api.createDealContract(dealId, body)
            ContractSaveResult(
                queued = false,
                sciezkaPodpisu = res.sciezkaPodpisu,
                numer = res.numer,
                id = res.id,
            )
        } catch (_: IOException) {
            val localId = LOCAL_ID_PREFIX + UUID.randomUUID()
            enqueue(dealId, localId, KIND_CREATE, encode(body))
            ContractSaveResult(queued = true, id = localId)
        }
    }

    override suspend fun requestChange(
        dealId: String,
        contractId: String,
        filling: ContractFilling,
        powod: String,
        rodzaj: ContractKind,
    ): ContractSaveResult {
        val body = filling.toChangeRequest(powod, rodzaj)
        return try {
            val res = api.requestContractChange(dealId, contractId, body)
            ContractSaveResult(
                queued = false,
                sciezkaPodpisu = res.sciezkaPodpisu,
                numer = res.numer,
                id = res.id,
                stan = res.stan,
            )
        } catch (_: IOException) {
            enqueue(dealId, contractId, KIND_CHANGE, encode(body))
            ContractSaveResult(queued = true)
        }
    }

    override suspend fun approveChange(dealId: String, contractId: String): ContractSaveResult = try {
        val res = api.approveContractChange(dealId, contractId)
        ContractSaveResult(queued = false, sciezkaPodpisu = res.sciezkaPodpisu)
    } catch (_: IOException) {
        enqueue(dealId, contractId, KIND_APPROVE, "")
        ContractSaveResult(queued = true)
    }

    override suspend fun rejectChange(
        dealId: String,
        contractId: String,
        powod: String?,
    ): ContractSaveResult {
        val body = ContractRejectRequest(powod?.takeIf { it.isNotBlank() })
        return try {
            api.rejectContractChange(dealId, contractId, body)
            ContractSaveResult(queued = false)
        } catch (_: IOException) {
            enqueue(dealId, contractId, KIND_REJECT, encode(body))
            ContractSaveResult(queued = true)
        }
    }

    override suspend fun resend(dealId: String, contractId: String): ContractSaveResult = try {
        val res = api.resendDealContract(dealId, contractId)
        ContractSaveResult(queued = false, sciezkaPodpisu = res.sciezkaPodpisu)
    } catch (_: IOException) {
        enqueue(dealId, contractId, KIND_RESEND, "")
        ContractSaveResult(queued = true)
    }

    override suspend fun cancel(dealId: String, contractId: String): ContractSaveResult {
        // Umowa, której serwer nigdy nie widział, znika razem z wpisem
        // w kolejce — nie ma czego unieważniać.
        if (contractId.startsWith(LOCAL_ID_PREFIX)) {
            dao.deleteMutationsFor(contractId)
            return ContractSaveResult(queued = false)
        }
        return try {
            api.cancelDealContract(dealId, contractId)
            ContractSaveResult(queued = false)
        } catch (_: IOException) {
            enqueue(dealId, contractId, KIND_CANCEL, "")
            ContractSaveResult(queued = true)
        }
    }

    override suspend fun rebuildOrder(dealId: String, contractId: String): ContractOrderRebuild = try {
        val res = api.rebuildContractOrder(dealId, contractId)
        ContractOrderRebuild(
            queued = false,
            numer = res.numer,
            status = res.status,
            pozycje = res.pozycje,
            zamowienia = res.zamowienia,
        )
    } catch (_: IOException) {
        enqueue(dealId, contractId, KIND_ORDER, "")
        ContractOrderRebuild(queued = true)
    }

    // ── Kolejka ───────────────────────────────────────────────────────────────

    private suspend fun enqueue(dealId: String, targetId: String, kind: String, payload: String) {
        dao.upsertMutation(
            ContractMutationEntity(
                targetId = targetId,
                kind = kind,
                payload = payload,
                dealId = dealId,
                createdAt = System.currentTimeMillis(),
            ),
        )
        // Warunek sieci zdejmuje z nas odpytywanie — system sam obudzi
        // robotnika, gdy telefon wróci w zasięg.
        syncScheduler.scheduleSync()
    }

    /**
     * Opróżnianie kolejki, od najstarszego zapisu. Kolejność jest tu istotna:
     * akceptacja zmiany musi pójść po samej zmianie, a unieważnienie po
     * wystawieniu — dlatego pierwsza porażka sieci kończy CAŁY przebieg,
     * zamiast przeskakiwać do następnego wpisu.
     */
    override suspend fun syncPendingMutations(): ContractSyncResult {
        val queue = dao.getMutations()
        if (queue.isEmpty()) return ContractSyncResult.DONE

        for (row in queue) {
            try {
                when (row.kind) {
                    KIND_CREATE -> {
                        val body = decode<ContractFillingDto>(row.payload)
                            ?: throw IllegalStateException("payload")
                        api.createDealContract(row.dealId, body)
                    }

                    KIND_CHANGE -> {
                        val body = decode<ContractChangeRequest>(row.payload)
                            ?: throw IllegalStateException("payload")
                        api.requestContractChange(row.dealId, row.targetId, body)
                    }

                    KIND_APPROVE -> api.approveContractChange(row.dealId, row.targetId)

                    KIND_REJECT -> api.rejectContractChange(
                        row.dealId,
                        row.targetId,
                        decode<ContractRejectRequest>(row.payload) ?: ContractRejectRequest(),
                    )

                    KIND_RESEND -> api.resendDealContract(row.dealId, row.targetId)
                    KIND_CANCEL -> api.cancelDealContract(row.dealId, row.targetId)
                    KIND_ORDER -> api.rebuildContractOrder(row.dealId, row.targetId)
                    else -> Unit
                }
                dao.deleteMutation(row.targetId, row.kind)
            } catch (_: IOException) {
                // Sieć znowu padła — reszta kolejki poczeka na następny przebieg.
                return ContractSyncResult.RETRY
            } catch (e: Exception) {
                dao.deleteMutation(row.targetId, row.kind)
                sessionPreferences.saveSyncProblem(discardMessage(row.kind, e))
            }
        }
        return ContractSyncResult.DONE
    }

    /**
     * Umowy widziane przez zakładkę: cache serwera z nałożoną kolejką. Zapis
     * czekający na wysyłkę nie zmienia treści karty (numer, PDF i link nadaje
     * serwer) — dokłada do niej ZNACZNIK, po którym zakładka pisze, co
     * konkretnie czeka. Nowa umowa dochodzi jako osobna karta na górze listy.
     */
    private fun overlay(
        cached: List<DealContract>,
        queue: List<ContractMutationEntity>,
    ): List<DealContract> {
        if (queue.isEmpty()) return cached
        val byId = cached.associateByTo(LinkedHashMap()) { it.id }
        val nowe = mutableListOf<DealContract>()

        for (row in queue) {
            if (row.kind == KIND_CREATE) {
                nowe += localContract(row)
                continue
            }
            val znacznik = when (row.kind) {
                KIND_CHANGE -> ContractPending.ZMIANA
                KIND_APPROVE -> ContractPending.AKCEPTACJA
                KIND_REJECT -> ContractPending.ODRZUCENIE
                KIND_RESEND -> ContractPending.LINK
                KIND_CANCEL -> ContractPending.UNIEWAZNIENIE
                KIND_ORDER -> ContractPending.ZAMOWIENIE
                else -> null
            } ?: continue
            byId[row.targetId]?.let { byId[row.targetId] = it.copy(pending = it.pending + znacznik) }
        }
        // Najświeższe na górze — tak samo, jak układa listę serwer.
        return nowe.sortedByDescending { it.utworzona } + byId.values.toList()
    }

    /**
     * Karta umowy wystawionej bez zasięgu. Numeru, linku i PDF-a nie zgadujemy:
     * nadaje je serwer przy wysyłce, a wymyślony numer na karcie deala trafiłby
     * prędzej czy później do rozmowy z klientem.
     */
    private fun localContract(row: ContractMutationEntity): DealContract = DealContract(
        id = row.targetId,
        numer = "",
        status = ContractStatus.DRAFT,
        rodzaj = ContractKind.UMOWA,
        utworzona = Instant.ofEpochMilli(row.createdAt).toString(),
        wyslana = null,
        podpisana = null,
        podpisanaIp = null,
        wysylka = null,
        wyslanaMailem = null,
        parafaZalacznika = null,
        brakParafy = false,
        wygasaLink = null,
        sciezkaPodpisu = null,
        wersja = 1,
        zastepuje = null,
        zastapionaPrzez = null,
        rodzajNastepcy = null,
        nastepcaPodpisany = false,
        zmiana = null,
        czekaNaAkceptacje = false,
        zarzad = false,
        mogeZdecydowac = false,
        mozeZmienic = false,
        pending = setOf(ContractPending.NOWA),
    )

    // ── Pomocniki ─────────────────────────────────────────────────────────────

    private fun encode(body: ContractFillingDto): String =
        json.encodeToString(ContractFillingDto.serializer(), body)

    private fun encode(body: ContractChangeRequest): String =
        json.encodeToString(ContractChangeRequest.serializer(), body)

    private fun encode(body: ContractRejectRequest): String =
        json.encodeToString(ContractRejectRequest.serializer(), body)

    private inline fun <reified T> decode(payload: String): T? =
        runCatching { json.decodeFromString<T>(payload) }.getOrNull()

    private fun DealContractEntity.toDomain(): DealContract? =
        decode<ContractDto>(payload)?.toDomain()

    private fun ContractDto.toEntity(dealId: String) = DealContractEntity(
        id = id,
        dealId = dealId,
        payload = json.encodeToString(ContractDto.serializer(), this),
        createdAt = utworzona,
        syncedAt = System.currentTimeMillis(),
    )

    private fun readErrorMessage(e: Exception): String = when ((e as? HttpException)?.code()) {
        403 -> "Brak dostępu do umów tego deala — potrzebne uprawnienie do CRM."
        404 -> "Deal zniknął z panelu."
        else -> "Nie udało się wczytać umów."
    }

    /**
     * Zapis wypadł z kolejki, bo serwer go odrzucił. Mówimy o tym wprost:
     * przepadła decyzja o dokumencie, który klient ma podpisać.
     */
    private fun discardMessage(kind: String, e: Exception): String {
        val what = when (kind) {
            KIND_CREATE -> "Umowa wystawiona bez zasięgu przepadła"
            KIND_CHANGE -> "Zmiana umowy przepadła"
            KIND_APPROVE -> "Akceptacja zmiany umowy przepadła"
            KIND_REJECT -> "Odrzucenie zmiany umowy przepadło"
            KIND_RESEND -> "Nowy link do podpisu nie powstał"
            KIND_CANCEL -> "Unieważnienie umowy przepadło"
            KIND_ORDER -> "Odtworzenie zamówienia z umowy przepadło"
            else -> "Zapis umowy przepadł"
        }
        return when (val code = (e as? HttpException)?.code()) {
            403 -> "$what — umowami zarządza opiekun deala albo zarząd."
            404 -> "$what — dokument zniknął z panelu."
            409 -> "$what — ktoś zmienił tę umowę w międzyczasie. Sprawdź kartę w panelu."
            422 -> "$what — serwer odrzucił treść. Sprawdź kartę w panelu."
            null -> "$what — nie udało się wysłać."
            else -> "$what — serwer go odrzucił (kod $code)."
        }
    }
}
