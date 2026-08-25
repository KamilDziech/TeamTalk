package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.Client
import kotlinx.coroutines.flow.Flow

interface ClientRepository {
    /** Strumień klientów z lokalnego cache, odświeżany z sieci. Read-only (board360). */
    fun getClients(query: String? = null): Flow<List<Client>>

    suspend fun getClientById(id: String): Client

    suspend fun getClientByPhone(phone: String): Client?
}
