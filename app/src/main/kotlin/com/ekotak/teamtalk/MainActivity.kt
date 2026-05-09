package com.ekotak.teamtalk

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ekotak.teamtalk.presentation.navigation.TeamTalkNavGraph
import com.ekotak.teamtalk.presentation.settings.SettingsViewModel
import com.ekotak.teamtalk.presentation.settings.ThemeMode
import com.ekotak.teamtalk.presentation.theme.TeamTalkTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CALL_LOG_ID = "extra_call_log_id"
    }

    private val settingsVm: SettingsViewModel by viewModels()
    private var deepLinkCallLogId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deepLinkCallLogId = intent.getStringExtra(EXTRA_CALL_LOG_ID)
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
                TeamTalkNavGraph(deepLinkCallLogId = deepLinkCallLogId)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deepLinkCallLogId = intent.getStringExtra(EXTRA_CALL_LOG_ID)
    }
}
