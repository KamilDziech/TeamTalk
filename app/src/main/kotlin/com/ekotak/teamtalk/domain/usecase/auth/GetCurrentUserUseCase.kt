package com.ekotak.teamtalk.domain.usecase.auth

import com.ekotak.teamtalk.domain.model.User
import com.ekotak.teamtalk.domain.repository.AuthRepository
import javax.inject.Inject

class GetCurrentUserUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(): User = authRepository.getCurrentUser()
}
