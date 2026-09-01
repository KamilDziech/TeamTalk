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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.model.ClientCategory
import com.ekotak.teamtalk.domain.model.ClientType
import com.ekotak.teamtalk.presentation.components.AppTopBar
import com.ekotak.teamtalk.presentation.theme.ButtonShape

/**
 * Formularz kartoteki (nowy wpis albo edycja). Świadomie bez pól części adresu:
 * board360 przyjmuje adres jako jedno pole i sam je rozbija przy geokodowaniu,
 * a wpisywanie kodu, miasta i ulicy osobno na telefonie tylko wydłużałoby formę.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientFormScreen(
    onNavigateBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: ClientFormViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.savedMessage) {
        state.savedMessage?.let(onSaved)
    }

    Scaffold(
        topBar = { AppTopBar(title = state.title, onNavigateBack = onNavigateBack) },
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.firstName,
                onValueChange = viewModel::onFirstName,
                label = { Text("Imię *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = state.lastName,
                onValueChange = viewModel::onLastName,
                label = { Text("Nazwisko *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = state.phone,
                onValueChange = viewModel::onPhone,
                label = { Text("Telefon") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next,
                ),
            )
            if (state.isEdit) {
                OutlinedTextField(
                    value = state.phone2,
                    onValueChange = viewModel::onPhone2,
                    label = { Text("Telefon 2") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next,
                    ),
                )
            }
            OutlinedTextField(
                value = state.email,
                onValueChange = viewModel::onEmail,
                label = { Text("E-mail") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
            )
            if (state.isEdit) {
                OutlinedTextField(
                    value = state.email2,
                    onValueChange = viewModel::onEmail2,
                    label = { Text("E-mail 2") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                )
            }
            OutlinedTextField(
                value = state.address,
                onValueChange = viewModel::onAddress,
                label = { Text("Adres instalacji") },
                supportingText = {
                    Text("Zmiana adresu uruchamia ponowną walidację i przeliczenie dojazdu.")
                },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )

            if (state.category == ClientCategory.KLIENT && !state.isEdit) {
                Text(text = "Typ", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ClientType.entries.forEach { type ->
                        FilterChip(
                            selected = state.type == type,
                            onClick = { viewModel.onType(type) },
                            label = { Text(type.label) },
                        )
                    }
                }
            }

            state.error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = viewModel::save,
                enabled = !state.isSaving,
                shape = ButtonShape,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        state.isSaving -> "Zapisuję…"
                        state.isEdit -> "Zapisz zmiany"
                        else -> "Dodaj: ${state.category.oneLabel}"
                    },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
