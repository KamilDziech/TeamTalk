package com.ekotak.teamtalk.domain.usecase.auth

import com.ekotak.teamtalk.domain.model.Session
import com.ekotak.teamtalk.domain.repository.AuthRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(email: String, password: String): Session {
        require(email.isNotBlank()) { "Email nie może być pusty" }
        require(password.isNotBlank()) { "Hasło nie może być puste" }
        return authRepository.login(email, password)
    }
}
