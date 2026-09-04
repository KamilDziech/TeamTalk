package com.ekotak.teamtalk.presentation.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.domain.model.Edit
import com.ekotak.teamtalk.domain.model.ServiceClient
import com.ekotak.teamtalk.domain.model.ServiceDomain
import com.ekotak.teamtalk.domain.model.ServiceJob
import com.ekotak.teamtalk.domain.model.ServiceJobDraft
import com.ekotak.teamtalk.domain.model.ServiceJobPatch
import com.ekotak.teamtalk.domain.model.ServiceJobPriority
import com.ekotak.teamtalk.domain.model.ServiceJobStatus
import com.ekotak.teamtalk.domain.model.ServiceJobType
import com.ekotak.teamtalk.domain.model.ServiceView
import com.ekotak.teamtalk.domain.model.Technician
import com.ekotak.teamtalk.domain.model.WarrantyCard
import com.ekotak.teamtalk.domain.model.WarrantyCardDraft
import com.ekotak.teamtalk.domain.model.WarrantyCardPatch
import com.ekotak.teamtalk.domain.model.WarrantyCardStatus
import com.ekotak.teamtalk.domain.model.WarrantyInspectionUpsert
import com.ekotak.teamtalk.domain.repository.ServiceRepository
import com.ekotak.teamtalk.domain.repository.ServiceSnapshot
import com.ekotak.teamtalk.presentation.crm.crmErrorMessage
import com.ekotak.teamtalk.presentation.crm.parseIsoMillis
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Klucz grupy „bez klienta” w pasku chipów (żaden identyfikator go nie zajmie). */
const val NO_CLIENT_KEY = " bez-klienta"

/**
 * Moduł Serwis — mobilny odpowiednik `ServiceView.tsx`. Dziedzinę (Przegląd /
 * Serwis) ustala kafelek pulpitu przez [openDomain] i już się nie zmienia; w jej
 * obrębie trzyma trzy widoki (Lista / Kalendarz / Mapa) i wszystkie
 * filtry panelu; dane przychodzą jedną migawką z repozytorium, a filtrowanie
 * i sortowanie robimy lokalnie — przełączanie chipów bez okrążenia po sieci.
 */
@HiltViewModel
class ServiceViewModel @Inject constructor(
    private val repository: ServiceRepository,
    private val sessionPreferences: SessionPreferences,
) : ViewModel() {

    /** Grupa klientów w pasku chipów dziedziny Serwis (odpowiednik lewej kolumny). */
    data class ClientGroup(val id: String, val label: String, val count: Int)

    data class UiState(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val error: String? = null,
        val message: String? = null,
        val domain: ServiceDomain = ServiceDomain.PRZEGLAD,
        val view: ServiceView = ServiceView.LISTA,
        val query: String = "",
        /** Filtry źródeł dziedziny Przegląd — jak chipy „Zwykłe / Gwarancyjne”. */
        val showRegular: Boolean = true,
        val showWarranty: Boolean = true,
        val warrantyStatus: WarrantyCardStatus? = null,
        /** Przypięty klient w dziedzinie Serwis; `null` = wszyscy. */
        val clientPin: String? = null,
        val rows: List<ServiceRow> = emptyList(),
        val jobs: List<ServiceJob> = emptyList(),
        val clientGroups: List<ClientGroup> = emptyList(),
        val clients: Map<String, ServiceClient> = emptyMap(),
        val technicians: List<Technician> = emptyList(),
        val warrantyAvailable: Boolean = true,
        /** Zlecenia z trwającym zapisem — wiersz nie reaguje na kolejne dotknięcia. */
        val pendingIds: Set<String> = emptySet(),
        val currentUserId: String? = null,
        /** „Teraz” odświeżane co minutę — chip SLA musi odliczać. */
        val now: Long = System.currentTimeMillis(),
        val syncedAt: Long? = null,
    ) {
        val isPrzeglad: Boolean get() = domain == ServiceDomain.PRZEGLAD

        /** Liczba filtrów odbiegających od domyślnych — kropka przy ikonie filtra. */
        val activeFilterCount: Int
            get() = listOf(
                !showRegular,
                !showWarranty,
                warrantyStatus != null,
                clientPin != null,
            ).count { it }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var snapshot = ServiceSnapshot()

    init {
        observe()
        load(initial = true)
    }

    private fun observe() {
        viewModelScope.launch {
            val userId = sessionPreferences.session.first()?.userId
            _uiState.update { it.copy(currentUserId = userId) }
        }
        viewModelScope.launch {
            repository.observe().collect { snap ->
                snapshot = snap
                _uiState.update {
                    it.copy(
                        clients = snap.clients,
                        technicians = snap.technicians,
                        warrantyAvailable = snap.warrantyAvailable,
                        syncedAt = snap.syncedAt,
                    )
                }
                recompute()
            }
        }
    }

    private fun load(initial: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = initial, isRefreshing = !initial, error = null) }
            try {
                repository.refresh()
                _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = crmErrorMessage(e, "Nie udało się pobrać zleceń serwisowych"),
                    )
                }
            }
        }
    }

    fun refresh() = load(initial = false)

    /**
     * Dziedzina ustalona wejściem: kafelek „Przeglądy” otwiera Przegląd,
     * kafelek „Serwis” — Serwis. Ekran nie przełącza jej w locie, więc to
     * jedyne miejsce, które ją zmienia.
     */
    fun openDomain(domain: ServiceDomain) {
        if (_uiState.value.domain == domain) return
        // Przypięcie klienta należy do dziedziny Serwis — w Przeglądzie
        // zostawiłoby listę odfiltrowaną bez widocznej przyczyny.
        _uiState.update { it.copy(domain = domain, view = ServiceView.LISTA, clientPin = null) }
        recompute()
    }

    fun setView(view: ServiceView) {
        _uiState.update { it.copy(view = view) }
    }

    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
        recompute()
    }

    fun toggleRegular() {
        _uiState.update { it.copy(showRegular = !it.showRegular) }
        recompute()
    }

    fun toggleWarranty() {
        _uiState.update { it.copy(showWarranty = !it.showWarranty) }
        recompute()
    }

    fun setWarrantyStatus(status: WarrantyCardStatus?) {
        _uiState.update { it.copy(warrantyStatus = status) }
        recompute()
    }

    fun setClientPin(id: String?) {
        _uiState.update { it.copy(clientPin = if (it.clientPin == id) null else id) }
        recompute()
    }

    fun tick() {
        _uiState.update { it.copy(now = System.currentTimeMillis()) }
        recompute()
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    // ── Składanie widoku ─────────────────────────────────────────────────────

    private fun recompute() {
        val s = _uiState.value
        val q = s.query.trim().lowercase()
        val domainJobs = snapshot.jobs.filter { it.type in s.domain.types }

        if (s.isPrzeglad) {
            val cards = snapshot.cards.filter { card ->
                (s.warrantyStatus == null || card.status == s.warrantyStatus) &&
                    warrantyMatchesQuery(card, q)
            }
            val rows = buildList {
                if (s.showRegular) {
                    for (job in domainJobs) {
                        if (q.isNotEmpty() && !jobMatchesQuery(job, q)) continue
                        add(
                            ServiceRow.Job(
                                job = job,
                                date = parseIsoMillis(job.scheduledAt),
                                closed = job.status == ServiceJobStatus.DONE,
                            ),
                        )
                    }
                }
                if (s.showWarranty) {
                    for (card in cards) {
                        val view = warrantyRowView(card, s.now)
                        add(ServiceRow.Warranty(view, view.sortDate, view.closed))
                    }
                }
            }.sortedWith(serviceRowComparator)
            _uiState.update { it.copy(rows = rows, jobs = domainJobs, clientGroups = emptyList()) }
            return
        }

        // Dziedzina Serwis: wiersze awarii + pasek grup klientów z licznikami.
        val searched = domainJobs.filter { q.isEmpty() || jobMatchesQuery(it, q) }
        val counts = LinkedHashMap<String, Int>()
        for (job in searched) {
            val key = job.clientId ?: NO_CLIENT_KEY
            counts[key] = (counts[key] ?: 0) + 1
        }
        val groups = counts.entries
            .map { (id, count) ->
                ClientGroup(
                    id = id,
                    label = if (id == NO_CLIENT_KEY) "Bez klienta" else snapshot.clients[id]?.label ?: "—",
                    count = count,
                )
            }
            // „Bez klienta” zawsze na końcu, reszta wg liczby zgłoszeń.
            .sortedWith(
                compareBy<ClientGroup> { it.id == NO_CLIENT_KEY }
                    .thenByDescending { it.count }
                    .thenBy { it.label.lowercase() },
            )
        val visible = searched
            .filter { s.clientPin == null || (it.clientId ?: NO_CLIENT_KEY) == s.clientPin }
            .sortedWith(jobComparator)
        _uiState.update {
            it.copy(
                jobs = visible,
                clientGroups = groups,
                rows = visible.map { job ->
                    ServiceRow.Job(job, parseIsoMillis(job.scheduledAt), job.status == ServiceJobStatus.DONE)
                },
            )
        }
    }

    /** Szukanie po opisie usterki, kliencie i miejscowości — jak w panelu. */
    private fun jobMatchesQuery(job: ServiceJob, query: String): Boolean {
        val client = job.clientId?.let { snapshot.clients[it] }
        val hay = listOfNotNull(job.note, client?.label, client?.city)
            .joinToString(" ")
            .lowercase()
        return hay.contains(query)
    }

    // ── Zapis ────────────────────────────────────────────────────────────────

    fun createJob(draft: ServiceJobDraft, onDone: (String?) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.createJob(draft) }
                .onSuccess { job ->
                    flash("Dodano zgłoszenie.")
                    recompute()
                    onDone(job.id)
                }
                .onFailure {
                    flash(crmErrorMessage(it, "Nie udało się zapisać zgłoszenia"))
                    onDone(null)
                }
        }
    }

    fun patchJob(id: String, patch: ServiceJobPatch, okMessage: String) {
        if (patch.isEmpty) return
        runJobAction(id, okMessage) { repository.updateJob(id, patch) }
    }

    fun setJobDone(job: ServiceJob) {
        if (job.status == ServiceJobStatus.DONE) {
            patchJob(job.id, ServiceJobPatch(status = Edit(ServiceJobStatus.IN_PROGRESS)), "Zlecenie znów w toku.")
        } else {
            runJobAction(job.id, "Zlecenie wykonane.") { repository.completeJob(job.id, job.status) }
        }
    }

    fun toggleJobPriority(job: ServiceJob) {
        val high = job.priority == ServiceJobPriority.HIGH
        patchJob(
            job.id,
            ServiceJobPatch(priority = Edit(if (high) ServiceJobPriority.NORMAL else ServiceJobPriority.HIGH)),
            if (high) "Zdjęto priorytet." else "Oznaczono jako pilne.",
        )
    }

    private fun runJobAction(id: String, okMessage: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(pendingIds = it.pendingIds + id) }
            runCatching { block() }
                .onSuccess { flash(okMessage) }
                .onFailure { flash(crmErrorMessage(it, "Nie udało się zapisać zmiany")) }
            _uiState.update { it.copy(pendingIds = it.pendingIds - id) }
            recompute()
        }
    }

    fun createCard(draft: WarrantyCardDraft, onDone: (WarrantyCard?) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.createCard(draft) }
                .onSuccess {
                    flash("Utworzono kartę gwarancyjną.")
                    recompute()
                    onDone(it)
                }
                .onFailure {
                    flash(crmErrorMessage(it, "Nie udało się utworzyć karty"))
                    onDone(null)
                }
        }
    }

    private fun flash(text: String) = _uiState.update { it.copy(message = text) }
}
