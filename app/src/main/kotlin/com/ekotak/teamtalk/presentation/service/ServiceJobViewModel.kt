package com.ekotak.teamtalk.presentation.service

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.domain.model.Edit
import com.ekotak.teamtalk.domain.model.ServiceClient
import com.ekotak.teamtalk.domain.model.ServiceJob
import com.ekotak.teamtalk.domain.model.ServiceJobPatch
import com.ekotak.teamtalk.domain.model.ServiceJobPriority
import com.ekotak.teamtalk.domain.model.ServiceJobStatus
import com.ekotak.teamtalk.domain.model.Technician
import com.ekotak.teamtalk.domain.repository.ServiceRepository
import com.ekotak.teamtalk.presentation.crm.crmErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Karta zlecenia serwisowego — odpowiednik szuflady `ServiceJobCard.tsx`.
 *
 * Zlecenie bierzemy z migawki repozytorium (ta sama, z której żyje lista), więc
 * wejście z listy nie odpytuje API. Wejście z powiadomienia trafia tu bez
 * migawki — wtedy dociągamy komplet (`refresh`), bo karta musi znać jeszcze
 * klientów i serwisantów, nie samo zlecenie.
 */
@HiltViewModel
class ServiceJobViewModel @Inject constructor(
    private val repository: ServiceRepository,
    private val sessionPreferences: SessionPreferences,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val jobId: String = checkNotNull(savedStateHandle["jobId"])

    data class UiState(
        val isLoading: Boolean = true,
        val job: ServiceJob? = null,
        val client: ServiceClient? = null,
        val clients: List<ServiceClient> = emptyList(),
        val technicians: List<Technician> = emptyList(),
        val pending: Boolean = false,
        val message: String? = null,
        val error: String? = null,
        val currentUserId: String? = null,
        val now: Long = System.currentTimeMillis(),
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(currentUserId = sessionPreferences.session.first()?.userId) }
        }
        viewModelScope.launch {
            repository.observe().collect { snap ->
                val job = snap.jobs.firstOrNull { it.id == jobId }
                _uiState.update {
                    it.copy(
                        isLoading = job == null && snap.syncedAt == null,
                        job = job,
                        client = job?.clientId?.let { id -> snap.clients[id] },
                        clients = snap.clients.values.sortedBy { c -> c.label.lowercase() },
                        technicians = snap.technicians,
                        error = if (job == null && snap.syncedAt != null) {
                            "Nie znaleziono zlecenia — mogło zostać usunięte."
                        } else {
                            null
                        },
                    )
                }
            }
        }
        // Wejście z powiadomienia: migawki jeszcze nie ma, trzeba ją pobrać.
        viewModelScope.launch {
            if (repository.observe().first().syncedAt == null) {
                runCatching { repository.refresh() }
                    .onFailure { e ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = crmErrorMessage(e, "Nie udało się wczytać zlecenia"),
                            )
                        }
                    }
            }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    fun patch(patch: ServiceJobPatch, okMessage: String) {
        if (patch.isEmpty) return
        run(okMessage) { repository.updateJob(jobId, patch) }
    }

    fun setClient(clientId: String) = patch(ServiceJobPatch(clientId = Edit(clientId)), "Przypisano klienta.")

    fun setStatus(status: ServiceJobStatus) =
        patch(ServiceJobPatch(status = Edit(status)), "Zmieniono status.")

    fun setTechnician(id: String?) =
        patch(ServiceJobPatch(technicianId = Edit(id)), "Przypisano serwisanta.")

    fun setScheduledAt(iso: String?) =
        patch(ServiceJobPatch(scheduledAt = Edit(iso)), "Zapisano termin.")

    fun setSlaHours(hours: Int?) = patch(ServiceJobPatch(slaHours = Edit(hours)), "Zapisano SLA.")

    fun setNote(note: String) {
        val job = _uiState.value.job ?: return
        val next = note.trim()
        if (next == jobTitle(job)) return
        patch(ServiceJobPatch(note = Edit(next.ifBlank { null })), "Zapisano opis usterki.")
    }

    fun togglePriority() {
        val job = _uiState.value.job ?: return
        val high = job.priority == ServiceJobPriority.HIGH
        patch(
            ServiceJobPatch(
                priority = Edit(if (high) ServiceJobPriority.NORMAL else ServiceJobPriority.HIGH),
            ),
            if (high) "Zdjęto priorytet." else "Oznaczono jako pilne.",
        )
    }

    /** Przełącznik „wykonane" — z `new` idzie dwoma krokami (maszyna statusów). */
    fun toggleDone() {
        val job = _uiState.value.job ?: return
        if (job.status == ServiceJobStatus.DONE) {
            patch(ServiceJobPatch(status = Edit(ServiceJobStatus.IN_PROGRESS)), "Zlecenie znów w toku.")
        } else {
            run("Zlecenie wykonane.") { repository.completeJob(job.id, job.status) }
        }
    }

    private fun run(okMessage: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(pending = true) }
            runCatching { block() }
                .onSuccess { _uiState.update { s -> s.copy(message = okMessage) } }
                .onFailure { e ->
                    _uiState.update { s ->
                        s.copy(message = crmErrorMessage(e, "Nie udało się zapisać zmiany"))
                    }
                }
            _uiState.update { it.copy(pending = false, now = System.currentTimeMillis()) }
        }
    }
}
