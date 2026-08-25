package com.ekotak.teamtalk.presentation.calllog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.model.CallDirection
import com.ekotak.teamtalk.domain.model.CallLog
import com.ekotak.teamtalk.presentation.components.AppTopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallLogListScreen(
    onNavigateToDetail: (String) -> Unit,
    viewModel: CallLogViewModel = hiltViewModel(),
) {
    val callLogs by viewModel.callLogs.collectAsState()

    Scaffold(
        topBar = { AppTopBar(title = "Nieodebrane") },
    ) { padding ->
        if (callLogs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Brak nieodebranych połączeń",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            ) {
                items(callLogs, key = { it.id }) { callLog ->
                    CallLogCard(callLog = callLog, onClick = { onNavigateToDetail(callLog.id) })
                }
            }
        }
    }
}

@Composable
private fun CallLogCard(callLog: CallLog, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = callLog.client?.displayName?.takeIf { it.isNotBlank() }
                        ?: callLog.phoneNumber.ifBlank { "Nieznany" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = callLog.direction.toPolishLabel(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    callLog.durationSec?.takeIf { it > 0 }?.let { d ->
                        Text(
                            text = "· ${d / 60}:${(d % 60).toString().padStart(2, '0')}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = callLog.startedAt.toShortDateTime(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun CallDirection.toPolishLabel(): String = when (this) {
    CallDirection.INBOUND  -> "Przychodzące"
    CallDirection.OUTBOUND -> "Wychodzące"
    CallDirection.MISSED   -> "Nieodebrane"
}

internal fun String.toShortDateTime(): String = try {
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val formatter = SimpleDateFormat("dd.MM HH:mm", Locale("pl", "PL")).apply {
        timeZone = TimeZone.getDefault()
    }
    formatter.format(parser.parse(this.take(19)) ?: Date())
} catch (_: Exception) { this }
