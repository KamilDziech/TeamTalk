package com.ekotak.teamtalk.domain.model

data class Client(
    val id: String,
    val phone: String,
    val name: String?,
    val address: String?,
    val notes: String?,
    val groupId: String?,
    val createdAt: String,
    val updatedAt: String,
)
