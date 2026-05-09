package com.ekotak.teamtalk.presentation.calllog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.ekotak.teamtalk.domain.model.isOverSla
import com.ekotak.teamtalk.domain.model.waitingMinutes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.model.CallLog
import com.ekotak.teamtalk.domain.model.CallStatus
import com.ekotak.teamtalk.presentation.theme.Green600
import com.ekotak.teamtalk.presentation.theme.Orange600
import com.ekotak.teamtalk.presentation.theme.Red600
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallLogDetailScreen(
    callLogId: String,
    onNavigateBack: () -> Unit,
    onNavigateToVoiceReport: () -> Unit = {},
    viewModel: CallLogViewModel = hiltViewModel(),
) {
    val callLog by viewModel.observeCallLog(callLogId).collectAsState(initial = null)
    val actionError by viewModel.actionError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(actionError) {
        actionError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Szczegóły zgłoszenia") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Wróć",
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (callLog == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            CallLogDetailContent(
                callLog = callLog!!,
                onReserve = { viewModel.reserveCallLog(callLogId) },
                onComplete = { viewModel.completeCallLog(callLogId) },
                onReopen = { viewModel.reopenCallLog(callLogId) },
                onAddRecipient = { viewModel.addCurrentUserAsRecipient(callLogId) },
                onCall = { phone -> viewModel.makeCall(phone) },
                onNavigateToVoiceReport = onNavigateToVoiceReport,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        }
    }
}

@Composable
private fun CallLogDetailContent(
    callLog: CallLog,
    onReserve: () -> Unit,
    onComplete: () -> Unit,
    onReopen: () -> Unit,
    onAddRecipient: () -> Unit,
    onCall: (String) -> Unit,
    onNavigateToVoiceReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // Status header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusChip(status = callLog.status)
            Text(
                text = callLog.timestamp.toFullDateTime(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (callLog.status == CallStatus.MISSED) {
            val minutes = callLog.waitingMinutes()
            val overSla = callLog.isOverSla()
            if (minutes > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                val waitText = if (minutes >= 60) {
                    val h = minutes / 60; val m = minutes % 60
                    if (m == 0L) "Czeka ${h}h" else "Czeka ${h}h ${m}min"
                } else "Czeka ${minutes}min"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = if (overSla) Red600 else Orange600,
                        modifier = Modifier
                            .size(16.dp),
                    )
                    Text(
                        text = if (overSla) "$waitText — PRZEKROCZONO SLA" else waitText,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (overSla) Red600 else Orange600,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // Caller info
        DetailSection(title = "Dzwoniący") {
            DetailRow("Numer telefonu", callLog.callerPhone ?: "—")
            if (callLog.client != null) {
                DetailRow("Klient", callLog.client.name ?: callLog.client.phone)
                if (callLog.client.address != null) {
                    DetailRow("Adres", callLog.client.address)
                }
                if (callLog.client.notes != null) {
                    DetailRow("Notatki", callLog.client.notes)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Call details
        DetailSection(title = "Szczegóły") {
            DetailRow("Typ", callLog.type.name.lowercase().replaceFirstChar { it.uppercase() })
            if (callLog.reservationBy != null) {
                DetailRow("Zarezerwowane przez", callLog.reservationBy)
            }
            if (callLog.reservationAt != null) {
                DetailRow("Czas rezerwacji", callLog.reservationAt.toFullDateTime())
            }
            if (callLog.recipients.isNotEmpty()) {
                DetailRow("Odbiorcy", "${callLog.recipients.size} os.")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(24.dp))

        // Action buttons
        Text(
            text = "Akcje",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(12.dp))

        when (callLog.status) {
            CallStatus.MISSED -> {
                Button(
                    onClick = onReserve,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Orange600),
                ) {
                    Text("Zarezerwuj")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onComplete,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Green600),
                ) {
                    Text("Zakończ")
                }
            }
            CallStatus.RESERVED -> {
                Button(
                    onClick = onComplete,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Green600),
                ) {
                    Text("Zakończ")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onReopen,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cofnij rezerwację", color = Red600)
                }
            }
            CallStatus.COMPLETED -> {
                OutlinedButton(
                    onClick = onReopen,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Otwórz ponownie", color = Orange600)
                }
            }
        }

        val phone = callLog.callerPhone ?: callLog.client?.phone
        if (phone != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onCall(phone) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Zadzwoń: $phone")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onAddRecipient,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Dodaj siebie jako odbiorcę")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onNavigateToVoiceReport,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Notatki głosowe")
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun DetailSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(modifier = Modifier.height(8.dp))
    content()
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.6f),
        )
    }
}

private fun String.toFullDateTime(): String = try {
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("pl", "PL")).apply {
        timeZone = TimeZone.getDefault()
    }
    formatter.format(parser.parse(this) ?: Date())
} catch (_: Exception) { this }
