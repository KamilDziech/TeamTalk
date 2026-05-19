package com.ekotak.teamtalk

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.ekotak.teamtalk.presentation.navigation.TeamTalkNavGraph
import com.ekotak.teamtalk.presentation.settings.SettingsViewModel
import com.ekotak.teamtalk.presentation.settings.ThemeMode
import com.ekotak.teamtalk.presentation.theme.TeamTalkTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CALL_LOG_ID = "extra_call_log_id"
        const val EXTRA_POST_CALL_PHONE = "extra_post_call_phone"
    }

    private val settingsVm: SettingsViewModel by viewModels()
    private var deepLinkCallLogId by mutableStateOf<String?>(null)
    private var deepLinkPostCallPhone by mutableStateOf<String?>(null)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions granted/denied — receiver works as long as READ_PHONE_STATE is granted */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        deepLinkCallLogId = intent.getStringExtra(EXTRA_CALL_LOG_ID)
        deepLinkPostCallPhone = intent.getStringExtra(EXTRA_POST_CALL_PHONE)?.ifBlank { null }
        requestRequiredPermissions()
        enableEdgeToEdge()
        setContent {
            val themeMode by settingsVm.themeMode.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.DARK   -> true
                ThemeMode.LIGHT  -> false
                ThemeMode.SYSTEM -> systemDark
            }
            TeamTalkTheme(darkTheme = darkTheme) {
                TeamTalkNavGraph(
                    deepLinkCallLogId = deepLinkCallLogId,
                    deepLinkPostCallPhone = deepLinkPostCallPhone,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deepLinkCallLogId = intent.getStringExtra(EXTRA_CALL_LOG_ID)
        deepLinkPostCallPhone = intent.getStringExtra(EXTRA_POST_CALL_PHONE)?.ifBlank { null }
    }

    private fun requestRequiredPermissions() {
        val required = buildList {
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.READ_CALL_LOG)
            add(Manifest.permission.READ_CONTACTS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }
}
