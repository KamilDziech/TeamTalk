package com.ekotak.teamtalk.presentation.client

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.domain.model.CallLogFilter
import com.ekotak.teamtalk.domain.model.Client
import com.ekotak.teamtalk.domain.usecase.calllog.GetCallLogsUseCase
import com.ekotak.teamtalk.domain.usecase.client.CreateClientUseCase
import com.ekotak.teamtalk.domain.usecase.client.DeleteClientUseCase
import com.ekotak.teamtalk.domain.usecase.client.GetClientByIdUseCase
import com.ekotak.teamtalk.domain.usecase.client.GetClientsUseCase
import com.ekotak.teamtalk.domain.usecase.client.UpdateClientUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ClientViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getClientsUseCase: GetClientsUseCase,
    private val getClientByIdUseCase: GetClientByIdUseCase,
    private val createClientUseCase: CreateClientUseCase,
    private val updateClientUseCase: UpdateClientUseCase,
    private val deleteClientUseCase: DeleteClientUseCase,
    private val getCallLogsUseCase: GetCallLogsUseCase,
) : ViewModel() {

    val groupId: String? = savedStateHandle["groupId"]
    private val initialPhone: String? = savedStateHandle["phone"]
    private val initialName: String? = savedStateHandle["name"]

    data class RecentCaller(val phone: String, val name: String?, val formattedDate: String)

    data class ListUiState(
        val clients: List<Client> = emptyList(),
        val searchQuery: String = "",
        val callCountMap: Map<String, Int> = emptyMap(),
    )

    data class FormUiState(
        val phone: String = "",
        val name: String = "",
        val address: String = "",
        val notes: String = "",
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val initialGroupId: String? = null,
    )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val allClientsFlow: Flow<List<Client>> = getClientsUseCase(groupId = groupId)

    val listState: StateFlow<ListUiState> = combine(
        allClientsFlow,
        _searchQuery,
        getCallLogsUseCase(CallLogFilter()),
    ) { clients, query, callLogs ->
        val callCountMap = callLogs
            .filter { it.clientId != null }
            .groupBy { it.clientId!! }
            .mapValues { it.value.size }
        val filtered = if (query.isBlank()) clients
        else clients.filter {
            it.phone.contains(query, ignoreCase = true) ||
            it.name?.contains(query, ignoreCase = true) == true ||
            it.address?.contains(query, ignoreCase = true) == true
        }
        ListUiState(clients = filtered, searchQuery = query, callCountMap = callCountMap)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListUiState())

    val recentCallersState: StateFlow<List<RecentCaller>> =
        getCallLogsUseCase(CallLogFilter(limit = 50))
            .map { logs ->
                logs
                    .filter { it.callerPhone != null }
                    .distinctBy { it.callerPhone }
                    .take(30)
                    .map { log ->
                        RecentCaller(
                            phone = log.callerPhone!!,
                            name = log.client?.name,
                            formattedDate = formatTimestamp(log.timestamp),
                        )
                    }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _formState = MutableStateFlow(
        FormUiState(
            phone = initialPhone ?: "",
            name = initialName ?: "",
            initialGroupId = groupId,
        )
    )
    val formState: StateFlow<FormUiState> = _formState.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private val _navigateBack = Channel<Unit>(Channel.BUFFERED)
    val navigateBack: Flow<Unit> = _navigateBack.receiveAsFlow()

    fun onSearchQueryChange(query: String) { _searchQuery.value = query }
    fun clearActionError() { _actionError.value = null }

    fun onPhoneChange(v: String) = _formState.update { it.copy(phone = v, errorMessage = null) }
    fun onNameChange(v: String) = _formState.update { it.copy(name = v) }
    fun onAddressChange(v: String) = _formState.update { it.copy(address = v) }
    fun onNotesChange(v: String) = _formState.update { it.copy(notes = v) }

    fun observeClient(id: String): Flow<Client?> =
        allClientsFlow.map { list -> list.find { it.id == id } }

    fun resetForm() { _formState.value = FormUiState() }

    fun loadClientForEdit(id: String) {
        viewModelScope.launch {
            try {
                val client = getClientByIdUseCase(id)
                _formState.update {
                    it.copy(
                        phone = client.phone,
                        name = client.name ?: "",
                        address = client.address ?: "",
                        notes = client.notes ?: "",
                    )
                }
            } catch (e: Exception) {
                _actionError.value = e.message ?: "Nie można załadować klienta"
            }
        }
    }

    fun createClient() {
        val form = _formState.value
        if (!form.phone.isValidPhone()) {
            _formState.update { it.copy(errorMessage = "Podaj prawidłowy numer telefonu (min. 7 cyfr)") }
            return
        }
        viewModelScope.launch {
            _formState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                createClientUseCase(
                    phone = form.phone,
                    name = form.name.takeIf { it.isNotBlank() },
                    address = form.address.takeIf { it.isNotBlank() },
                    notes = form.notes.takeIf { it.isNotBlank() },
                    groupId = form.initialGroupId ?: groupId,
                )
                _formState.value = FormUiState()
                _navigateBack.send(Unit)
            } catch (e: Exception) {
                _formState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Błąd tworzenia klienta") }
            }
        }
    }

    fun initNewClientForm(phone: String? = null, name: String? = null, initialGroupId: String? = null) {
        _formState.value = FormUiState(
            phone = phone ?: "",
            name = name ?: "",
            initialGroupId = initialGroupId ?: groupId,
        )
    }

    fun updateClient(id: String) {
        val form = _formState.value
        viewModelScope.launch {
            _formState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                updateClientUseCase(
                    id = id,
                    phone = form.phone.takeIf { it.isNotBlank() },
                    name = form.name.takeIf { it.isNotBlank() },
                    address = form.address.takeIf { it.isNotBlank() },
                    notes = form.notes.takeIf { it.isNotBlank() },
                )
                _formState.update { it.copy(isLoading = false) }
                _navigateBack.send(Unit)
            } catch (e: Exception) {
                _formState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Błąd aktualizacji klienta") }
            }
        }
    }

    private fun String.isValidPhone(): Boolean = filter { it.isDigit() }.length >= 7

    private fun formatTimestamp(ts: String): String = try {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val out = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
        out.format(fmt.parse(ts) ?: Date())
    } catch (_: Exception) { "" }

    fun deleteClient(id: String) {
        viewModelScope.launch {
            try {
                deleteClientUseCase(id)
                _navigateBack.send(Unit)
            } catch (e: Exception) {
                _actionError.value = e.message ?: "Błąd usuwania klienta"
            }
        }
    }
}
