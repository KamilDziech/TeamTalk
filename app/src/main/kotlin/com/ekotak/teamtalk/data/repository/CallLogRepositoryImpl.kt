package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.local.dao.CallLogDao
import com.ekotak.teamtalk.data.local.dao.ClientDao
import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.mapper.toEntity
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.AppendRecipientRequest
import com.ekotak.teamtalk.data.remote.dto.CreateCallLogRequest
import com.ekotak.teamtalk.data.remote.dto.UpdateCallLogRequest
import com.ekotak.teamtalk.domain.model.CallLog
import com.ekotak.teamtalk.domain.model.CallLogFilter
import com.ekotak.teamtalk.domain.model.CallStatus
import com.ekotak.teamtalk.domain.model.CallType
import com.ekotak.teamtalk.domain.repository.CallLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

class CallLogRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
    private val callLogDao: CallLogDao,
    private val clientDao: ClientDao,
) : CallLogRepository {

    override fun getCallLogs(filter: CallLogFilter): Flow<List<CallLog>> = channelFlow {
        val localFlow = if (filter.statusIn != null) {
            callLogDao.observeByStatusIn(filter.statusIn)
        } else {
            callLogDao.observeFiltered(
                statusEq      = filter.statusEq,
                statusNeq     = filter.statusNeq,
                typeNeq       = filter.typeNeq,
                typeNeq2      = filter.typeNeq2,
                clientIdEq    = filter.clientIdEq,
                callerPhoneEq = filter.callerPhoneEq,
                timestampGte  = filter.timestampGte,
                timestampLte  = filter.timestampLte,
            )
        }

        launch {
            localFlow.collect { entities ->
                val clientIds = entities.mapNotNull { it.clientId }.distinct()
                val clientMap = if (clientIds.isNotEmpty())
                    clientDao.getByIds(clientIds).associateBy { it.id }
                else emptyMap()
                send(entities.map { e ->
                    e.toDomain().copy(client = e.clientId?.let { clientMap[it]?.toDomain() })
                })
            }
        }

        try {
            val fresh = api.getCallLogs(
                statusEq      = filter.statusEq,
                statusNeq     = filter.statusNeq,
                typeNeq       = filter.typeNeq,
                typeNeq2      = filter.typeNeq2,
                clientIdEq    = filter.clientIdEq,
                callerPhoneEq = filter.callerPhoneEq,
                statusIn      = filter.statusIn?.joinToString(","),
                timestampGte  = filter.timestampGte,
                timestampLte  = filter.timestampLte,
                embed         = if (filter.embedClients) "clients" else null,
                limit         = filter.limit,
            )
            callLogDao.upsertAll(fresh.map { it.toEntity() })

            if (filter.embedClients) {
                val embeddedClients = fresh.mapNotNull { it.clients }
                if (embeddedClients.isNotEmpty()) {
                    clientDao.upsertAll(embeddedClients.map { it.toEntity() })
                }
            }
        } catch (_: Exception) {}
    }

    override suspend fun createCallLog(
        clientId: String?,
        employeeId: String?,
        type: CallType,
        status: CallStatus,
        timestamp: String?,
        callerPhone: String?,
        dedupKey: String?,
        phoneAccountId: String?,
    ): CallLog {
        val dto = api.createCallLog(
            CreateCallLogRequest(
                clientId       = clientId,
                employeeId     = employeeId,
                type           = type.value,
                status         = status.value,
                timestamp      = timestamp,
                callerPhone    = callerPhone,
                dedupKey       = dedupKey,
                phoneAccountId = phoneAccountId,
            )
        )
        callLogDao.upsert(dto.toEntity())
        return dto.toDomain()
    }

    override suspend fun updateCallLog(
        id: String,
        status: CallStatus?,
        type: CallType?,
        reservationBy: String?,
        reservationAt: String?,
        mergedIntoId: String?,
    ): CallLog {
        val dto = api.updateCallLog(
            id,
            UpdateCallLogRequest(
                status        = status?.value,
                type          = type?.value,
                reservationBy = reservationBy,
                reservationAt = reservationAt,
                mergedIntoId  = mergedIntoId,
            )
        )
        callLogDao.upsert(dto.toEntity())
        return dto.toDomain()
    }

    override suspend fun appendUniqueRecipient(callLogId: String, recipientId: String) {
        api.appendUniqueRecipient(AppendRecipientRequest(callLogId, recipientId))
    }
}
