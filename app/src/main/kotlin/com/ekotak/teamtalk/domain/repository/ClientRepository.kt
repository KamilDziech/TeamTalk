package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.Client
import kotlinx.coroutines.flow.Flow

interface ClientRepository {
    /** Stream of clients from the local cache, refreshed from the network. */
    fun getClients(phoneEq: String? = null, groupId: String? = null): Flow<List<Client>>

    suspend fun getClientById(id: String): Client

    suspend fun getClientByPhone(phone: String): Client?

    suspend fun createClient(
        phone: String,
        name: String?,
        address: String?,
        notes: String?,
        groupId: String? = null,
    ): Client

    suspend fun updateClient(
        id: String,
        phone: String? = null,
        name: String? = null,
        address: String? = null,
        notes: String? = null,
    ): Client

    suspend fun deleteClient(id: String)
}
