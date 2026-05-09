package com.ekotak.teamtalk.domain.usecase.auth

import com.ekotak.teamtalk.domain.repository.AuthRepository
import javax.inject.Inject

class LogoutUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke() = authRepository.logout()
}
