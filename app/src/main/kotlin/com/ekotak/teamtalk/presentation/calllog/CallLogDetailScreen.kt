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
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.ekotak.teamtalk.presentation.components.AppTopBar
import com.ekotak.teamtalk.presentation.theme.ButtonShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.model.CallLog
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
    onNavigateToPostCallNote: (phone: String) -> Unit = {},
    viewModel: CallLogViewModel = hiltViewModel(),
) {
    val callLog by remember(callLogId, viewModel) {
        viewModel.observeCallLog(callLogId)
    }.collectAsState(initial = null)

    Scaffold(
        topBar = { AppTopBar(title = "Szczegóły połączenia", onNavigateBack = onNavigateBack) },
    ) { padding ->
        val current = callLog
        if (current == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        } else {
            CallLogDetailContent(
                callLog = current,
                onCall = { phone -> viewModel.makeCall(phone) },
                onNavigateToPostCallNote = onNavigateToPostCallNote,
                onNavigateToVoiceReport = onNavigateToVoiceReport,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

@Composable
private fun CallLogDetailContent(
    callLog: CallLog,
    onCall: (String) -> Unit,
    onNavigateToPostCallNote: (phone: String) -> Unit,
    onNavigateToVoiceReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = callLog.direction.toPolishLabel(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = callLog.startedAt.toFullDateTime(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        DetailRow("Numer telefonu", callLog.phoneNumber.ifBlank { "—" })
        callLog.client?.let { c ->
            DetailRow("Klient", c.displayName)
            c.address?.let { DetailRow("Adres", it) }
        }
        callLog.durationSec?.takeIf { it > 0 }?.let {
            DetailRow("Czas trwania", "${it / 60}:${(it % 60).toString().padStart(2, '0')}")
        }
        callLog.simSlot?.let { DetailRow("SIM", it.toString()) }

        Spacer(modifier = Modifier.height(24.dp))

        val phone = callLog.phoneNumber.ifBlank { null }
        if (phone != null) {
            Button(onClick = { onCall(phone) }, shape = ButtonShape, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Zadzwoń: $phone")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onNavigateToPostCallNote(phone) },
                shape = ButtonShape,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Dodaj notatkę") }
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onNavigateToVoiceReport, shape = ButtonShape, modifier = Modifier.fillMaxWidth()) {
            Text("Notatki")
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
    }
}

private fun String.toFullDateTime(): String = try {
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("pl", "PL")).apply {
        timeZone = TimeZone.getDefault()
    }
    formatter.format(parser.parse(this.take(19)) ?: Date())
} catch (_: Exception) { this }
