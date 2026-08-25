package com.ekotak.teamtalk.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import com.ekotak.teamtalk.presentation.components.AppTopBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.data.local.preferences.SimPreferences
import com.ekotak.teamtalk.presentation.profile.ProfileViewModel
import com.ekotak.teamtalk.presentation.theme.ButtonShape
import com.ekotak.teamtalk.presentation.theme.Red600

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsVm: SettingsViewModel = hiltViewModel(),
    profileVm: ProfileViewModel = hiltViewModel(),
) {
    val themeMode by settingsVm.themeMode.collectAsState()
    val profileState by profileVm.uiState.collectAsState()
    val simCards by settingsVm.simCards.collectAsState()
    val monitoredSubId by settingsVm.monitoredSubId.collectAsState()

    Scaffold(
        topBar = { AppTopBar(title = "Ustawienia") },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Theme section ────────────────────────────────────────────────
            SectionHeader("Motyw")
            listOf(
                ThemeMode.SYSTEM to "Systemowy",
                ThemeMode.LIGHT  to "Jasny",
                ThemeMode.DARK   to "Ciemny",
            ).forEach { (mode, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = themeMode == mode,
                        onClick = { settingsVm.setThemeMode(mode) },
                        modifier = Modifier.size(36.dp),
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()

            // ── SIM section ──────────────────────────────────────────────────
            if (simCards.isNotEmpty()) {
                SectionHeader("Karta SIM do monitorowania")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = monitoredSubId == SimPreferences.ALL_SIMS,
                        onClick = { settingsVm.setMonitoredSubId(SimPreferences.ALL_SIMS) },
                        modifier = Modifier.size(36.dp),
                    )
                    Text(
                        text = "Wszystkie karty SIM",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                simCards.forEach { sim ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = monitoredSubId == sim.subId,
                            onClick = { settingsVm.setMonitoredSubId(sim.subId) },
                            modifier = Modifier.size(36.dp),
                        )
                        Text(
                            text = sim.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
            }

            // ── App version section ──────────────────────────────────────────
            SectionHeader("Aplikacja")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Wersja",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = settingsVm.appVersion,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()

            // ── Account section ──────────────────────────────────────────────
            SectionHeader("Konto")
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                if (profileState.displayName.isNotBlank()) {
                    Text(
                        text = profileState.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (profileState.email.isNotBlank()) {
                    Text(
                        text = profileState.email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = profileVm::logout,
                    shape = ButtonShape,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !profileState.isLoggingOut,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Red600),
                    border = BorderStroke(1.dp, Red600),
                ) {
                    if (profileState.isLoggingOut) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Red600, strokeWidth = 2.dp)
                    } else {
                        Text("Wyloguj się", color = Red600)
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
