package com.ekotak.teamtalk.presentation.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionPreferences: SessionPreferences,
) : ViewModel() {

    val appVersion: String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
    } catch (_: Exception) { "—" }

    val themeMode: StateFlow<ThemeMode> = sessionPreferences.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { sessionPreferences.saveThemeMode(mode) }
    }
}
