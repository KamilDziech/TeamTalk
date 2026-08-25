package com.ekotak.teamtalk.data.scanner

import android.content.Context
import android.provider.CallLog
import com.ekotak.teamtalk.domain.model.CallDirection
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Czyta z natywnego dziennika połączeń telefonu ostatnie połączenie po jego
 * zakończeniu (numer, kierunek, czas trwania, SIM). Używane przez
 * CallMonitorService/PostCallNoteWorker do zapisania połączenia w board360.
 */
@Singleton
class DeviceCallLogReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class DeviceCall(
        val phoneNumber: String,
        val cachedName: String?,
        val timestampMs: Long,
        val phoneAccountId: String?,
        val direction: CallDirection,
        val durationSec: Int?,
    )

    private fun typeToDirection(type: Int): CallDirection = when (type) {
        CallLog.Calls.INCOMING_TYPE -> CallDirection.INBOUND
        CallLog.Calls.OUTGOING_TYPE -> CallDirection.OUTBOUND
        CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> CallDirection.MISSED
        else -> CallDirection.OUTBOUND
    }

    fun readMostRecentCallSince(sinceMs: Long, phoneAccountId: String? = null): DeviceCall? {
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.DATE,
            CallLog.Calls.PHONE_ACCOUNT_ID,
            CallLog.Calls.TYPE,
            CallLog.Calls.DURATION,
        )
        val selection = buildString {
            append("${CallLog.Calls.DATE} >= ?")
            if (phoneAccountId != null) append(" AND ${CallLog.Calls.PHONE_ACCOUNT_ID} = ?")
        }
        val selectionArgs = buildList {
            add((sinceMs - 5_000).toString())
            if (phoneAccountId != null) add(phoneAccountId)
        }.toTypedArray()

        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${CallLog.Calls.DATE} DESC",
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val number = cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER))
                    ?.takeIf { it.isNotBlank() } ?: return@use null
                val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val simIdx  = cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)
                val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val durIdx  = cursor.getColumnIndex(CallLog.Calls.DURATION)
                DeviceCall(
                    phoneNumber    = number,
                    cachedName     = if (nameIdx >= 0) cursor.getString(nameIdx)?.takeIf { it.isNotBlank() } else null,
                    timestampMs    = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)),
                    phoneAccountId = if (simIdx >= 0) cursor.getString(simIdx) else null,
                    direction      = if (typeIdx >= 0) typeToDirection(cursor.getInt(typeIdx)) else CallDirection.OUTBOUND,
                    durationSec    = if (durIdx >= 0) cursor.getInt(durIdx) else null,
                )
            }
        } catch (_: SecurityException) { null }
    }

    fun normalizePhone(raw: String): String {
        val digits = raw.replace(Regex("[^\\d]"), "")
        return if (digits.length >= 9) digits.takeLast(9) else digits
    }
}
