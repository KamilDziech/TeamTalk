package com.ekotak.teamtalk.domain.model

data class User(
    val id: String,
    val email: String,
    val displayName: String,
    val isAdmin: Boolean = false,
)
