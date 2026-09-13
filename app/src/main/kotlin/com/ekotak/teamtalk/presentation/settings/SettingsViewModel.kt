package com.ekotak.teamtalk.presentation.settings

import android.content.Context
import android.telephony.SubscriptionManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.data.local.preferences.CallRecordingPreferences
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.data.local.preferences.SimPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class SimInfo(val subId: Int, val label: String)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionPreferences: SessionPreferences,
    private val simPreferences: SimPreferences,
    private val callRecordingPreferences: CallRecordingPreferences,
) : ViewModel() {

    val appVersion: String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
    } catch (_: Exception) { "—" }

    val themeMode: StateFlow<ThemeMode> = sessionPreferences.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    private val _simCards = MutableStateFlow<List<SimInfo>>(emptyList())
    val simCards: StateFlow<List<SimInfo>> = _simCards.asStateFlow()

    private val _monitoredSubId = MutableStateFlow(simPreferences.monitoredSubId)
    val monitoredSubId: StateFlow<Int> = _monitoredSubId.asStateFlow()

    private val _recordingUploadEnabled = MutableStateFlow(callRecordingPreferences.enabled)
    /** Wysyłka nagrań rozmów z nagrywarki telefonu do transkrypcji na serwerze. */
    val recordingUploadEnabled: StateFlow<Boolean> = _recordingUploadEnabled.asStateFlow()

    init {
        loadSimCards()
    }

    private fun loadSimCards() {
        val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
        val sims = try {
            sm?.activeSubscriptionInfoList?.map { info ->
                SimInfo(
                    subId = info.subscriptionId,
                    label = info.displayName?.toString()?.takeIf { it.isNotBlank() }
                        ?: "SIM ${info.simSlotIndex + 1}",
                )
            } ?: emptyList()
        } catch (_: Exception) { emptyList() }
        _simCards.value = sims
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { sessionPreferences.saveThemeMode(mode) }
    }

    fun setRecordingUploadEnabled(enabled: Boolean) {
        callRecordingPreferences.enabled = enabled
        _recordingUploadEnabled.value = enabled
    }

    fun setMonitoredSubId(subId: Int) {
        simPreferences.monitoredSubId = subId
        _monitoredSubId.value = subId
    }
}
