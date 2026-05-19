package com.ekotak.teamtalk.data.scanner

import android.content.Context
import android.provider.CallLog
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceCallLogReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class DeviceCall(
        val phoneNumber: String,
        val cachedName: String?,
        val timestampMs: Long,
        val phoneAccountId: String?,
    )

    fun readMissedCallsSince(sinceMs: Long, phoneAccountId: String? = null): List<DeviceCall> {
        val calls = mutableListOf<DeviceCall>()
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.DATE,
            CallLog.Calls.PHONE_ACCOUNT_ID,
        )
        val selection = buildString {
            append("${CallLog.Calls.TYPE} = ${CallLog.Calls.MISSED_TYPE} AND ${CallLog.Calls.DATE} > ?")
            if (phoneAccountId != null) append(" AND ${CallLog.Calls.PHONE_ACCOUNT_ID} = ?")
        }
        val selectionArgs = buildList {
            add(sinceMs.toString())
            if (phoneAccountId != null) add(phoneAccountId)
        }.toTypedArray()

        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${CallLog.Calls.DATE} ASC",
            )?.use { cursor ->
                val numberIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val nameIdx   = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val dateIdx   = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
                val simIdx    = cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)
                while (cursor.moveToNext()) {
                    val number = cursor.getString(numberIdx) ?: continue
                    calls.add(DeviceCall(
                        phoneNumber    = number,
                        cachedName     = if (nameIdx >= 0) cursor.getString(nameIdx)?.takeIf { it.isNotBlank() } else null,
                        timestampMs    = cursor.getLong(dateIdx),
                        phoneAccountId = if (simIdx >= 0) cursor.getString(simIdx) else null,
                    ))
                }
            }
        } catch (_: SecurityException) {}

        return calls
    }

    fun readMostRecentCallSince(sinceMs: Long, phoneAccountId: String? = null): DeviceCall? {
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.DATE,
            CallLog.Calls.PHONE_ACCOUNT_ID,
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
                val nameIdx  = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val simIdx   = cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)
                DeviceCall(
                    phoneNumber    = number,
                    cachedName     = if (nameIdx >= 0) cursor.getString(nameIdx)?.takeIf { it.isNotBlank() } else null,
                    timestampMs    = cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)),
                    phoneAccountId = if (simIdx >= 0) cursor.getString(simIdx) else null,
                )
            }
        } catch (_: SecurityException) { null }
    }

    fun normalizePhone(raw: String): String {
        val digits = raw.replace(Regex("[^\\d]"), "")
        return if (digits.length >= 9) digits.takeLast(9) else digits
    }
}
