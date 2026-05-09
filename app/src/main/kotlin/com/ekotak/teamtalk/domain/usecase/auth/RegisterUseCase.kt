package com.ekotak.teamtalk.domain.usecase.auth

import com.ekotak.teamtalk.domain.model.Session
import com.ekotak.teamtalk.domain.repository.AuthRepository
import javax.inject.Inject

class RegisterUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(
        email: String,
        password: String,
        displayName: String,
    ): Session {
        require(email.isNotBlank()) { "Email nie może być pusty" }
        require('@' in email) { "Nieprawidłowy format email" }
        require(password.length >= 6) { "Hasło musi mieć co najmniej 6 znaków" }
        require(displayName.isNotBlank()) { "Imię nie może być puste" }
        return authRepository.register(email, password, displayName)
    }
}
