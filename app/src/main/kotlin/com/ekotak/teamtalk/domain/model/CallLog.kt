package com.ekotak.teamtalk.domain.model

enum class CallType(val value: String) {
    MISSED("missed"),
    COMPLETED("completed"),
    MERGED("merged"),
    SKIPPED("skipped");

    companion object {
        fun fromValue(value: String): CallType =
            entries.firstOrNull { it.value == value } ?: MISSED
    }
}

enum class CallStatus(val value: String) {
    MISSED("missed"),
    RESERVED("reserved"),
    COMPLETED("completed");

    companion object {
        fun fromValue(value: String): CallStatus =
            entries.firstOrNull { it.value == value } ?: MISSED
    }
}

data class CallLog(
    val id: String,
    val clientId: String?,
    val employeeId: String?,
    val type: CallType,
    val status: CallStatus,
    val timestamp: String,
    val reservationBy: String?,
    val reservationAt: String?,
    val recipients: List<String>,
    val callerPhone: String?,
    val dedupKey: String?,
    val mergedIntoId: String?,
    val phoneAccountId: String?,
    val createdAt: String,
    val updatedAt: String,
    /** Populated when API is queried with embed=clients. */
    val client: Client? = null,
)

data class CallLogFilter(
    val statusEq: String? = null,
    val statusNeq: String? = null,
    val typeNeq: String? = null,
    val typeNeq2: String? = null,
    val clientIdEq: String? = null,
    val callerPhoneEq: String? = null,
    val statusIn: List<String>? = null,
    val timestampGte: String? = null,
    val timestampLte: String? = null,
    val embedClients: Boolean = false,
    val limit: Int? = null,
)
