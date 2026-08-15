package com.ekotak.teamtalk.presentation.client

import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import com.ekotak.teamtalk.presentation.components.AppTopBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.model.Client
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class BottomSheetMode { NONE, SELECT_METHOD, FROM_HISTORY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientsInGroupScreen(
    groupId: String,
    groupName: String,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (clientId: String) -> Unit,
    onNavigateToNewForm: (phone: String?, name: String?) -> Unit,
    viewModel: ClientViewModel = hiltViewModel(),
) {
    val state by viewModel.listState.collectAsState()
    val recentCallers by viewModel.recentCallersState.collectAsState()
    val actionError by viewModel.actionError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var sheetMode by remember { mutableStateOf(BottomSheetMode.NONE) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(actionError) {
        actionError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionError()
        }
    }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                var name: String? = null
                var phone: String? = null
                withContext(Dispatchers.IO) {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                            val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                            name = cursor.getString(nameIdx)
                            val contactId = cursor.getString(idIdx)
                            context.contentResolver.query(
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                null,
                                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                                arrayOf(contactId),
                                null,
                            )?.use { phoneCursor ->
                                if (phoneCursor.moveToFirst()) {
                                    val phoneIdx = phoneCursor.getColumnIndex(
                                        ContactsContract.CommonDataKinds.Phone.NUMBER
                                    )
                                    phone = phoneCursor.getString(phoneIdx)
                                }
                            }
                        }
                    }
                }
                val cleanPhone = phone?.replace(Regex("\\s"), "") ?: return@launch
                onNavigateToNewForm(cleanPhone, name)
            }
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = groupName, onNavigateBack = onNavigateBack) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { sheetMode = BottomSheetMode.SELECT_METHOD },
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Dodaj klienta",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.clients.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Brak klientów w tej grupie",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                items(state.clients, key = { it.id }) { client ->
                    GroupClientCard(
                        client = client,
                        callCount = state.callCountMap[client.id] ?: 0,
                        onClick = { onNavigateToDetail(client.id) },
                    )
                }
            }
        }
    }

    if (sheetMode != BottomSheetMode.NONE) {
        ModalBottomSheet(
            onDismissRequest = { sheetMode = BottomSheetMode.NONE },
            sheetState = sheetState,
        ) {
            when (sheetMode) {
                BottomSheetMode.SELECT_METHOD -> SelectMethodSheet(
                    onFromHistory = { sheetMode = BottomSheetMode.FROM_HISTORY },
                    onFromContacts = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            sheetMode = BottomSheetMode.NONE
                            contactPickerLauncher.launch(null)
                        }
                    },
                    onNewForm = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            sheetMode = BottomSheetMode.NONE
                            onNavigateToNewForm(null, null)
                        }
                    },
                )
                BottomSheetMode.FROM_HISTORY -> FromHistorySheet(
                    recentCallers = recentCallers,
                    onSelected = { phone, name ->
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            sheetMode = BottomSheetMode.NONE
                            onNavigateToNewForm(phone, name)
                        }
                    },
                )
                BottomSheetMode.NONE -> {}
            }
        }
    }
}

@Composable
private fun SelectMethodSheet(
    onFromHistory: () -> Unit,
    onFromContacts: () -> Unit,
    onNewForm: () -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 32.dp)) {
        Text(
            text = "Dodaj klienta",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Z historii połączeń") },
            supportingContent = { Text("Wybierz numer z listy połączeń") },
            leadingContent = {
                Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            modifier = Modifier.clickable(onClick = onFromHistory),
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Z kontaktów") },
            supportingContent = { Text("Importuj z książki telefonicznej") },
            leadingContent = {
                Icon(Icons.Default.Contacts, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            modifier = Modifier.clickable(onClick = onFromContacts),
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Nowy klient") },
            supportingContent = { Text("Wypełnij formularz ręcznie") },
            leadingContent = {
                Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            modifier = Modifier.clickable(onClick = onNewForm),
        )
    }
}

@Composable
private fun FromHistorySheet(
    recentCallers: List<ClientViewModel.RecentCaller>,
    onSelected: (phone: String, name: String?) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)) {
        Text(
            text = "Historia połączeń",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        HorizontalDivider()
        if (recentCallers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Brak historii połączeń",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.height(360.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(recentCallers, key = { it.phone }) { caller ->
                    ListItem(
                        headlineContent = { Text(caller.name ?: caller.phone) },
                        supportingContent = if (caller.name != null) {
                            { Text(caller.phone) }
                        } else null,
                        leadingContent = {
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        trailingContent = {
                            Text(
                                text = caller.formattedDate,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier.clickable { onSelected(caller.phone, caller.name) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun GroupClientCard(client: Client, callCount: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = client.name ?: client.phone,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (client.name != null) {
                    Text(
                        text = client.phone,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (callCount > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "$callCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
