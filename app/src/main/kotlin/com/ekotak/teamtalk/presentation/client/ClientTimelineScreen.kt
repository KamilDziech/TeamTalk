package com.ekotak.teamtalk.presentation.client

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.ekotak.teamtalk.presentation.components.AppTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.model.VoiceReport
import com.ekotak.teamtalk.presentation.calllog.toPolishLabel
import com.ekotak.teamtalk.presentation.theme.ButtonShape
import com.ekotak.teamtalk.presentation.calllog.toShortDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientTimelineScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCallDetail: (String) -> Unit,
    onCreateTask: (phone: String, name: String?) -> Unit = { _, _ -> },
    viewModel: ClientTimelineViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val client = uiState.client
    val taskPhone = client?.primaryPhone ?: viewModel.phone
    val taskName = client?.displayName

    Scaffold(
        topBar = { AppTopBar(title = client?.displayName ?: "Historia klienta", onNavigateBack = onNavigateBack) },
    ) { padding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            uiState.entries.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Brak historii połączeń",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(16.dp),
            ) {
                item {
                    OutlinedButton(
                        onClick = { onCreateTask(taskPhone, taskName) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = ButtonShape,
                    ) {
                        Icon(Icons.Default.AddTask, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Utwórz zadanie")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (client != null) {
                    item {
                        ClientHeaderCard(
                            phone = client.primaryPhone ?: "",
                            name = client.displayName,
                            address = client.address,
                            onCall = { client.primaryPhone?.let { viewModel.makeCall(it) } },
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${uiState.entries.size} połączeń",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                items(uiState.entries, key = { it.callLog.id }) { entry ->
                    TimelineEntryCard(
                        entry = entry,
                        onOpenDetail = { onNavigateToCallDetail(entry.callLog.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ClientHeaderCard(
    phone: String,
    name: String?,
    address: String?,
    onCall: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (!name.isNullOrBlank()) {
                    Text(text = name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                if (phone.isNotBlank()) {
                    Text(
                        text = phone,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!address.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (phone.isNotBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onCall, shape = ButtonShape) {
                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Zadzwoń")
                }
            }
        }
    }
}

@Composable
private fun TimelineEntryCard(
    entry: TimelineEntry,
    onOpenDetail: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val callLog = entry.callLog

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = callLog.direction.toPolishLabel(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = callLog.startedAt.toShortDateTime(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (entry.reports.isNotEmpty()) {
                    IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Zwiń" else "Rozwiń",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (entry.reports.isNotEmpty() && !expanded) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${entry.reports.size} ${if (entry.reports.size == 1) "notatka" else "notatki"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else if (entry.reports.isEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Brak notatki",
                    style = MaterialTheme.typography.labelSmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    entry.reports.forEachIndexed { index, report ->
                        ReportRow(report = report)
                        if (index < entry.reports.lastIndex) HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onOpenDetail) {
                    Text("Szczegóły →", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ReportRow(report: VoiceReport) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = report.createdAt.toShortDateTime(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (report.recordingKey != null) {
                Text(
                    text = "Nagranie",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        val content = report.text ?: report.transcript
        if (!content.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
