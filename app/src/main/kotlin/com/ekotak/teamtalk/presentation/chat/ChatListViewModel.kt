package com.ekotak.teamtalk.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.domain.model.ChatKind
import com.ekotak.teamtalk.domain.model.ChatPerson
import com.ekotak.teamtalk.domain.model.ChatSearchHit
import com.ekotak.teamtalk.domain.model.ChatThread
import com.ekotak.teamtalk.domain.repository.ChatRepository
import com.ekotak.teamtalk.presentation.crm.crmErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Zakładki skrzynki — ten sam podział, co filtry w panelu. */
enum class ChatFilter(val label: String) {
    ALL("Wszystkie"),
    UNREAD("Nieprzeczytane"),
    GROUPS("Grupy"),
    TASKS("Zadania"),
}

/**
 * Skrzynka Komunikatora na telefonie.
 *
 * Czyta przez repozytorium, więc bez zasięgu dostaje ostatni odczyt z Rooma
 * razem z tym, co czeka w kolejce — lista nigdy nie jest pusta „bo nie ma sieci".
 */
@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chat: ChatRepository,
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val error: String? = null,
        val threads: List<ChatThread> = emptyList(),
        val filter: ChatFilter = ChatFilter.ALL,
        val archived: Boolean = false,
        val query: String = "",
        val hits: List<ChatSearchHit>? = null,
        val people: List<ChatPerson> = emptyList(),
        val newChatOpen: Boolean = false,
        val pendingCount: Int = 0,
    ) {
        val unreadTotal: Int get() = threads.filterNot { it.muted }.sumOf { it.unreadCount }

        val visible: List<ChatThread>
            get() = threads.filter {
                when (filter) {
                    ChatFilter.ALL -> true
                    ChatFilter.UNREAD -> it.unreadCount > 0
                    ChatFilter.GROUPS -> it.kind == ChatKind.GROUP || it.kind == ChatKind.CHANNEL
                    ChatFilter.TASKS -> it.kind == ChatKind.TASK
                }
            }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        load(initial = true)
    }

    fun refresh() = load(initial = false)

    private fun load(initial: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = initial, isRefreshing = !initial, error = null) }
            try {
                val archived = _uiState.value.archived
                val threads = chat.listThreads(archived)
                // Meldunek „doszło" — z niego bierze się drugi ptaszek u nadawcy.
                chat.markDelivered()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        threads = threads,
                        pendingCount = chat.pendingCount(),
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = crmErrorMessage(e, "Nie udało się pobrać rozmów"),
                    )
                }
            }
        }
    }

    fun setFilter(filter: ChatFilter) = _uiState.update { it.copy(filter = filter) }

    fun toggleArchived() {
        _uiState.update { it.copy(archived = !it.archived, threads = emptyList()) }
        load(initial = true)
    }

    /** Szukanie z odroczeniem — bez tego każda litera biłaby po API. */
    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _uiState.update { it.copy(hits = null) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            val hits = runCatching { chat.search(query) }.getOrDefault(emptyList())
            _uiState.update { it.copy(hits = hits) }
        }
    }

    fun clearQuery() {
        searchJob?.cancel()
        _uiState.update { it.copy(query = "", hits = null) }
    }

    fun showStarred() {
        viewModelScope.launch {
            val hits = runCatching { chat.starred() }.getOrDefault(emptyList())
            _uiState.update { it.copy(hits = hits, query = "") }
        }
    }

    // ── Porządek listy ───────────────────────────────────────────────────────

    fun togglePinned(thread: ChatThread) = act { chat.setPinned(thread.id, !thread.pinned) }

    fun toggleMuted(thread: ChatThread) = act { chat.setMuted(thread.id, !thread.muted) }

    fun toggleArchived(thread: ChatThread) = act { chat.setArchived(thread.id, !thread.archived) }

    fun markUnread(thread: ChatThread) = act { chat.markUnread(thread.id) }

    fun leave(thread: ChatThread) = act { chat.leave(thread.id) }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                load(initial = false)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = crmErrorMessage(e, "Nie udało się zapisać")) }
            }
        }
    }

    // ── Nowa rozmowa ─────────────────────────────────────────────────────────

    fun openNewChat() {
        _uiState.update { it.copy(newChatOpen = true) }
        viewModelScope.launch {
            val people = runCatching { chat.people() }.getOrDefault(emptyList())
            _uiState.update { it.copy(people = people) }
        }
    }

    fun closeNewChat() = _uiState.update { it.copy(newChatOpen = false) }

    /** Zakłada (albo odnajduje) rozmowę i oddaje jej id do nawigacji. */
    fun startDirect(person: ChatPerson, onOpen: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val id = chat.openDirect(person.id)
                _uiState.update { it.copy(newChatOpen = false) }
                onOpen(id)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        newChatOpen = false,
                        error = crmErrorMessage(e, "Nie udało się otworzyć rozmowy"),
                    )
                }
            }
        }
    }

    fun startGroup(
        channel: Boolean,
        title: String,
        memberIds: List<String>,
        onOpen: (String) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val id = chat.createGroup(channel, title, memberIds)
                _uiState.update { it.copy(newChatOpen = false) }
                onOpen(id)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        newChatOpen = false,
                        error = crmErrorMessage(e, "Nie udało się założyć grupy"),
                    )
                }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}
