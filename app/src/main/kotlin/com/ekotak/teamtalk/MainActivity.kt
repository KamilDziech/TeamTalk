package com.ekotak.teamtalk

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.ekotak.teamtalk.presentation.navigation.TeamTalkNavGraph
import com.ekotak.teamtalk.presentation.permissions.PermissionScreen
import com.ekotak.teamtalk.presentation.permissions.allPermissionsGranted
import com.ekotak.teamtalk.presentation.settings.SettingsViewModel
import com.ekotak.teamtalk.presentation.settings.ThemeMode
import com.ekotak.teamtalk.presentation.theme.TeamTalkTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CALL_LOG_ID = "extra_call_log_id"
        const val EXTRA_POST_CALL_PHONE = "extra_post_call_phone"
        const val EXTRA_OPEN_POST_CALL_NOTE = "extra_open_post_call_note"

        /** Karta zadania otwierana z powiadomienia o wywołaniu (@) w komentarzu. */
        const val EXTRA_TASK_ID = "extra_task_id"
    }

    private val settingsVm: SettingsViewModel by viewModels()
    private var deepLinkCallLogId by mutableStateOf<String?>(null)
    private var deepLinkPostCallPhone by mutableStateOf<String?>(null)
    private var deepLinkTaskId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        deepLinkCallLogId = intent.getStringExtra(EXTRA_CALL_LOG_ID)
        deepLinkTaskId = intent.getStringExtra(EXTRA_TASK_ID)
        if (intent.getBooleanExtra(EXTRA_OPEN_POST_CALL_NOTE, false)) {
            deepLinkPostCallPhone = intent.getStringExtra(EXTRA_POST_CALL_PHONE) ?: ""
        }
        requestOverlayPermissionIfNeeded()
        enableEdgeToEdge()
        setContent {
            val themeMode by settingsVm.themeMode.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.DARK   -> true
                ThemeMode.LIGHT  -> false
                ThemeMode.SYSTEM -> systemDark
            }
            // Ikony paska statusu i nawigacji zależne od faktycznego motywu apki
            // (nie systemu): ciemne na jasnym, jasne na ciemnym — pasek statusu
            // wtapia się w tło zamiast być przykryty ciemnym blokiem.
            val view = LocalView.current
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
            TeamTalkTheme(darkTheme = darkTheme) {
                var permissionsDone by remember {
                    mutableStateOf(allPermissionsGranted(this@MainActivity))
                }
                if (!permissionsDone) {
                    PermissionScreen(onContinue = { permissionsDone = true })
                } else {
                    TeamTalkNavGraph(
                        deepLinkCallLogId = deepLinkCallLogId,
                        deepLinkPostCallPhone = deepLinkPostCallPhone,
                        deepLinkTaskId = deepLinkTaskId,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deepLinkCallLogId = intent.getStringExtra(EXTRA_CALL_LOG_ID)
        deepLinkTaskId = intent.getStringExtra(EXTRA_TASK_ID)
        if (intent.getBooleanExtra(EXTRA_OPEN_POST_CALL_NOTE, false)) {
            deepLinkPostCallPhone = intent.getStringExtra(EXTRA_POST_CALL_PHONE) ?: ""
        }
    }

    private fun requestOverlayPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (Settings.canDrawOverlays(this)) return
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("overlay_asked", false)) return
        prefs.edit().putBoolean("overlay_asked", true).apply()
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }
}
