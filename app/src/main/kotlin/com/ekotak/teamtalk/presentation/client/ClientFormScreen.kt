package com.ekotak.teamtalk.presentation.client

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientFormScreen(
    clientId: String?,
    onNavigateBack: () -> Unit,
    viewModel: ClientViewModel = hiltViewModel(),
) {
    val isEditMode = clientId != null
    val form by viewModel.formState.collectAsState()
    val actionError by viewModel.actionError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(clientId) {
        viewModel.resetForm()
        if (clientId != null) {
            viewModel.loadClientForEdit(clientId)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.navigateBack.collect { onNavigateBack() }
    }

    LaunchedEffect(actionError) {
        actionError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "Edytuj klienta" else "Nowy klient") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = form.phone,
                onValueChange = viewModel::onPhoneChange,
                label = { Text("Numer telefonu *") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = form.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Nazwa / Imię i nazwisko") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = form.address,
                onValueChange = viewModel::onAddressChange,
                label = { Text("Adres") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = form.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text("Notatki") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                minLines = 3,
                maxLines = 6,
            )

            AnimatedVisibility(visible = form.errorMessage != null) {
                Text(
                    text = form.errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    focusManager.clearFocus()
                    if (isEditMode) viewModel.updateClient(clientId!!)
                    else viewModel.createClient()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = !form.isLoading,
            ) {
                if (form.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(if (isEditMode) "Zapisz zmiany" else "Utwórz klienta")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
