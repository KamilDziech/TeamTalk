package com.ekotak.teamtalk.data.contacts

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rozwiązuje nazwę kontaktu z książki telefonu po numerze (ContactsContract).
 * Wynik jest cache'owany w pamięci (pusty string = sprawdzono, brak kontaktu).
 * Wymaga uprawnienia READ_CONTACTS — bez niego zwraca null (nie rzuca).
 */
@Singleton
class ContactNameResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cache = ConcurrentHashMap<String, String>()

    /** Nazwa kontaktu dla numeru lub null (brak dopasowania / brak uprawnienia). */
    suspend fun resolve(phone: String?): String? {
        val key = normalize(phone)
        if (key.isBlank()) return null
        cache[key]?.let { return it.ifBlank { null } }
        val name = withContext(Dispatchers.IO) { query(phone!!) }
        cache[key] = name ?: ""
        return name
    }

    private fun query(phone: String): String? {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phone),
        )
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null,
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        } catch (_: Exception) {
            null
        }
    }

    private fun normalize(raw: String?): String {
        val digits = raw?.replace(Regex("[^\\d]"), "").orEmpty()
        return if (digits.length >= 9) digits.takeLast(9) else digits
    }
}
