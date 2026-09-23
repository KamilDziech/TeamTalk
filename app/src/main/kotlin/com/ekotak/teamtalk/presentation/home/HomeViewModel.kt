package com.ekotak.teamtalk.presentation.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Pulpit — które kafelki pokazać. Większość widzi każdy; kafelek z
 * [HomeModule.requiredPermission] tylko ten, kto ma to prawo w `GET /auth/me`.
 *
 * Uprawnienia trzymamy też w telefonie: koordynator bez zasięgu ma zobaczyć
 * swój Harmonogram (moduł działa offline), a nie pulpit bez kafelka.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _permissions = MutableStateFlow(prefs.getStringSet(KEY, emptySet()).orEmpty().toSet())
    val permissions: StateFlow<Set<String>> = _permissions.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { authRepository.getCurrentUser().permissions.toSet() }
                .onSuccess { fresh ->
                    prefs.edit().putStringSet(KEY, fresh).apply()
                    _permissions.value = fresh
                }
        }
    }

    private companion object {
        const val PREFS = "home_modules"
        const val KEY = "permissions"
    }
}
