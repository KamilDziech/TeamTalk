package com.ekotak.teamtalk.domain.usecase.auth

import com.ekotak.teamtalk.domain.model.Session
import com.ekotak.teamtalk.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Emits the current session or null when the user is not authenticated.
 * Used by NavGraph to decide which destination to show.
 */
class ObserveSessionUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    operator fun invoke(): Flow<Session?> = authRepository.observeSession()
}
