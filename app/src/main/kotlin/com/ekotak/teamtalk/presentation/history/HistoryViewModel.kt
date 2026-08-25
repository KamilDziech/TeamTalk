package com.ekotak.teamtalk.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.data.contacts.ContactNameResolver
import com.ekotak.teamtalk.domain.model.CallLog
import com.ekotak.teamtalk.domain.model.CallLogFilter
import com.ekotak.teamtalk.domain.model.Client
import com.ekotak.teamtalk.domain.model.VoiceReport
import com.ekotak.teamtalk.domain.repository.VoiceReportRepository
import com.ekotak.teamtalk.domain.usecase.calllog.GetCallLogsUseCase
import com.ekotak.teamtalk.domain.usecase.client.GetClientsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ClientHistoryEntry(
    val clientId: String?,
    val phone: String?,
    val displayName: String,
    val callCount: Int,
    val noteCount: Int,
    val lastCallTimestamp: String,
    val notePreview: String?,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val getCallLogsUseCase: GetCallLogsUseCase,
    private val getClientsUseCase: GetClientsUseCase,
    private val voiceReportRepository: VoiceReportRepository,
    private val contactNameResolver: ContactNameResolver,
) : ViewModel() {

    data class UiState(
        val entries: List<ClientHistoryEntry> = emptyList(),
        val isLoading: Boolean = true,
    )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val uiState: StateFlow<UiState> = combine(
        getCallLogsUseCase(CallLogFilter()),
        voiceReportRepository.getVoiceReports(),
        getClientsUseCase(),
        _searchQuery,
    ) { logs, reports, clients, query ->
        val keyOf: (CallLog) -> String = { c ->
            c.clientId?.ifBlank { null } ?: c.phoneNumber.ifBlank { null } ?: "unknown_${c.id}"
        }

        // Notatki wiążemy z kontaktem po znormalizowanym NUMERZE (odporne na to,
        // że logi z urządzenia nie mają board360 clientId). Numer notatki
        // odtwarzamy z połączenia (callLogId → numer) albo z klienta (clientId → numer).
        val callIdToPhone = HashMap<String, String>()
        val clientIdToPhone = HashMap<String, String>()
        // Most clientId → numer z listy klientów board360 (najpewniejsze źródło).
        for (cl in clients) {
            val pk = normalizePhone(cl.primaryPhone ?: cl.phone)
            if (pk.isNotBlank()) clientIdToPhone[cl.id] = pk
        }
        for (c in logs) {
            val pk = normalizePhone(c.phoneNumber)
            if (pk.isBlank()) continue
            callIdToPhone[c.id] = pk
            (c.client?.id ?: c.clientId)?.takeIf { it.isNotBlank() }?.let { clientIdToPhone.putIfAbsent(it, pk) }
        }
        val notesByPhone = HashMap<String, MutableList<VoiceReport>>()
        for (r in reports) {
            val pk = r.callLogId?.let { callIdToPhone[it] }
                ?: r.clientId?.let { clientIdToPhone[it] }
                ?: continue
            notesByPhone.getOrPut(pk) { ArrayList() }.add(r)
        }

        val entries = ArrayList<ClientHistoryEntry>()
        for ((_, calls) in logs.groupBy(keyOf)) {
            val mostRecent = calls.maxByOrNull { it.startedAt }!!
            val client = mostRecent.client ?: calls.firstOrNull { it.client != null }?.client
            val phone = mostRecent.phoneNumber.ifBlank { null }
                ?: calls.firstOrNull { it.phoneNumber.isNotBlank() }?.phoneNumber
            val clientId = client?.id
                ?: calls.firstOrNull { !it.clientId.isNullOrBlank() }?.clientId
            val clientName = client?.displayName?.takeIf { it.isNotBlank() }
            // Brak klienta board360 → spróbuj nazwy z kontaktów telefonu.
            val contactName = if (clientName == null) contactNameResolver.resolve(phone) else null
            val notes = notesByPhone[normalizePhone(phone)].orEmpty()
            val lastNote = notes.maxByOrNull { it.createdAt }
            val notePreview = (lastNote?.text ?: lastNote?.transcript)?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { if (it.length > 160) it.take(160).trimEnd() + "…" else it }
            entries.add(
                ClientHistoryEntry(
                    clientId = clientId,
                    phone = phone,
                    displayName = clientName ?: contactName ?: phone ?: "Nieznany",
                    callCount = calls.size,
                    noteCount = notes.size,
                    lastCallTimestamp = mostRecent.startedAt,
                    notePreview = notePreview,
                )
            )
        }

        val result = entries
            .filter { entry ->
                if (query.isBlank()) true
                else entry.displayName.contains(query, ignoreCase = true) ||
                     entry.phone?.contains(query, ignoreCase = true) == true ||
                     entry.notePreview?.contains(query, ignoreCase = true) == true
            }
            .sortedByDescending { it.lastCallTimestamp }
        UiState(entries = result, isLoading = false)
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun onSearchQueryChange(query: String) { _searchQuery.value = query }

    /** Ostatnie 9 cyfr numeru — wspólny klucz dopasowania (bez kierunkowego). */
    private fun normalizePhone(raw: String?): String {
        val digits = raw?.replace(Regex("[^\\d]"), "").orEmpty()
        return if (digits.length >= 9) digits.takeLast(9) else digits
    }
}
