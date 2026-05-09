package com.ekotak.teamtalk.domain.model

data class Profile(
    val id: String,
    val displayName: String,
    val isAdmin: Boolean,
    val createdAt: String,
    val updatedAt: String,
)
