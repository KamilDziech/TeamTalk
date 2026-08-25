package com.ekotak.teamtalk.presentation.client

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.ekotak.teamtalk.presentation.components.AppTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.model.Client

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientDetailScreen(
    clientId: String,
    onNavigateBack: () -> Unit,
    onNavigateToTimeline: (String) -> Unit = {},
    viewModel: ClientViewModel = hiltViewModel(),
) {
    val client by viewModel.observeClient(clientId).collectAsState(initial = null)

    Scaffold(
        topBar = {
            AppTopBar(
                title = client?.displayName ?: "Klient",
                onNavigateBack = onNavigateBack,
            ) {
                IconButton(onClick = { onNavigateToTimeline(clientId) }) {
                    Icon(imageVector = Icons.Default.History, contentDescription = "Historia")
                }
            }
        },
    ) { padding ->
        val current = client
        if (current == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        } else {
            ClientDetailContent(client = current, modifier = Modifier.fillMaxSize().padding(padding))
        }
    }
}

@Composable
private fun ClientDetailContent(client: Client, modifier: Modifier = Modifier) {
    Column(modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        DetailSection(title = "Dane kontaktowe") {
            client.primaryPhone?.let { DetailRow("Telefon", it) }
            client.phone2?.takeIf { it.isNotBlank() && it != client.phone }?.let { DetailRow("Telefon 2", it) }
            client.email?.let { DetailRow("E-mail", it) }
            DetailRow("Nazwa", client.displayName)
        }

        val addr = client.address ?: listOfNotNull(client.street, client.city).joinToString(", ").ifBlank { null }
        if (!addr.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            DetailSection(title = "Adres") {
                Text(text = addr, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(modifier = Modifier.height(8.dp))
    content()
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
