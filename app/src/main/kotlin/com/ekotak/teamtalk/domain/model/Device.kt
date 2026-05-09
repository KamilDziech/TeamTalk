package com.ekotak.teamtalk.domain.model

data class Device(
    val id: String,
    val userName: String,
    val pushToken: String,
    val deviceInfo: String?,
    val lastActiveAt: String,
    val createdAt: String,
    val updatedAt: String,
)
