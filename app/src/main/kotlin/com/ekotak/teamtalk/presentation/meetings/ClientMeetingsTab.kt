package com.ekotak.teamtalk.presentation.meetings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.data.remote.dto.MeetingListItemDto
import com.ekotak.teamtalk.domain.repository.MeetingRepository
import com.ekotak.teamtalk.presentation.crm.crmErrorMessage
import com.ekotak.teamtalk.presentation.crm.formatDateTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Spotkania z kontrahentem do zakładki „Spotkania” karty kartoteki (1:1 z panelem, online). */
@HiltViewModel
class ClientMeetingsViewModel @Inject constructor(
    private val repository: MeetingRepository,
) : ViewModel() {
    data class State(
        val isLoading: Boolean = true,
        val items: List<MeetingListItemDto> = emptyList(),
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var loadedFor: String? = null

    fun load(clientId: String) {
        if (loadedFor == clientId) return
        loadedFor = clientId
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.listForClient(clientId) }
                .onSuccess { list -> _state.update { State(isLoading = false, items = list) } }
                .onFailure { e ->
                    loadedFor = null
                    _state.update {
                        it.copy(isLoading = false, error = crmErrorMessage(e, "Nie udało się wczytać spotkań (potrzebny internet)"))
                    }
                }
        }
    }
}

@Composable
fun ClientMeetingsTab(
    clientId: String,
    onOpenMeeting: (String) -> Unit,
    onCreateMeeting: () -> Unit,
    viewModel: ClientMeetingsViewModel = hiltViewModel(),
) {
    val s by viewModel.state.collectAsState()
    LaunchedEffect(clientId) { viewModel.load(clientId) }

    if (s.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        s.error?.let { msg ->
            item(key = "error") { Text(msg, color = MaterialTheme.colorScheme.error) }
        }
        if (s.error == null && s.items.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Brak spotkań z tym kontrahentem, które możesz zobaczyć.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(s.items, key = { it.id }) { m ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth().clickable { onOpenMeeting(m.id) },
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TypeBadge(m.type, 36.dp)
                    Column(Modifier.weight(1f)) {
                        Text(m.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${formatDateTime(m.startAt) ?: ""} · prowadzi ${m.host.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    StatusPill(m.status)
                }
            }
        }
        item(key = "create") {
            OutlinedButton(onClick = onCreateMeeting) { Text("+ Zaplanuj spotkanie") }
        }
    }
}
