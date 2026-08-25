package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.local.dao.CallLogDao
import com.ekotak.teamtalk.data.local.dao.ClientDao
import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.mapper.toEntity
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.CreateCallLogRequest
import com.ekotak.teamtalk.domain.model.CallDirection
import com.ekotak.teamtalk.domain.model.CallLog
import com.ekotak.teamtalk.domain.model.CallLogFilter
import com.ekotak.teamtalk.domain.repository.CallLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

class CallLogRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
    private val callLogDao: CallLogDao,
    private val clientDao: ClientDao,
) : CallLogRepository {

    override fun getCallLogs(filter: CallLogFilter): Flow<List<CallLog>> = channelFlow {
        launch {
            // Łączymy połączenia z klientami z cache, by na liście pokazać nazwę
            // dzwoniącego (dopasowanie po clientId ustawia backend).
            combine(
                callLogDao.observeFiltered(
                    clientId = filter.clientId,
                    direction = filter.direction,
                    since = filter.since,
                ),
                clientDao.observeAll(),
            ) { logs, clients ->
                val clientsById = clients.associateBy { it.id }
                logs.map { entity ->
                    val log = entity.toDomain()
                    val client = entity.clientId?.let { clientsById[it]?.toDomain() }
                    if (client != null) log.copy(client = client) else log
                }
            }.collect(::send)
        }

        try {
            val fresh = api.getCallLogs(since = filter.since, limit = filter.limit)
            callLogDao.upsertAll(fresh.map { it.toEntity() })
        } catch (_: Exception) {}
    }

    override suspend fun createCallLog(
        phoneNumber: String,
        direction: CallDirection,
        startedAt: String,
        endedAt: String?,
        durationSec: Int?,
        simSlot: Int?,
        clientId: String?,
    ): CallLog {
        val request = CreateCallLogRequest(
            clientId = clientId,
            phoneNumber = phoneNumber,
            direction = direction.value,
            simSlot = simSlot,
            startedAt = startedAt,
            endedAt = endedAt,
            durationSec = durationSec,
        )
        val dto = api.createCallLogs(listOf(request)).first()
        callLogDao.upsert(dto.toEntity())
        return dto.toDomain()
    }
}
