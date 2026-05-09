package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CallLogResponseDto(
    val id: String,
    @SerialName("client_id")       val clientId: String? = null,
    @SerialName("employee_id")     val employeeId: String? = null,
    val type: String,
    val status: String,
    val timestamp: String,
    @SerialName("reservation_by")  val reservationBy: String? = null,
    @SerialName("reservation_at")  val reservationAt: String? = null,
    val recipients: List<String> = emptyList(),
    @SerialName("caller_phone")    val callerPhone: String? = null,
    @SerialName("dedup_key")       val dedupKey: String? = null,
    @SerialName("merged_into_id")  val mergedIntoId: String? = null,
    @SerialName("phone_account_id") val phoneAccountId: String? = null,
    @SerialName("created_at")      val createdAt: String,
    @SerialName("updated_at")      val updatedAt: String,
    /** Present when the request includes embed=clients. */
    val clients: ClientResponseDto? = null,
)

@Serializable
data class CreateCallLogRequest(
    @SerialName("client_id")       val clientId: String? = null,
    @SerialName("employee_id")     val employeeId: String? = null,
    val type: String,
    val status: String,
    val timestamp: String? = null,
    @SerialName("caller_phone")    val callerPhone: String? = null,
    @SerialName("dedup_key")       val dedupKey: String? = null,
    @SerialName("phone_account_id") val phoneAccountId: String? = null,
)

@Serializable
data class UpdateCallLogRequest(
    val status: String? = null,
    val type: String? = null,
    @SerialName("reservation_by")  val reservationBy: String? = null,
    @SerialName("reservation_at")  val reservationAt: String? = null,
    @SerialName("merged_into_id")  val mergedIntoId: String? = null,
)

@Serializable
data class AppendRecipientRequest(
    @SerialName("p_call_log_id")  val callLogId: String,
    @SerialName("p_recipient_id") val recipientId: String,
)
