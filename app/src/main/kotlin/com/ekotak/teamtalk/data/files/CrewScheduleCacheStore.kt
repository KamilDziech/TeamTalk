package com.ekotak.teamtalk.data.files

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kopia osi harmonogramu ekip w pamięci telefonu — jeden plik JSON na okno
 * (`od_do.json`), dokładnie w kształcie odpowiedzi `GET /api/schedule`.
 *
 * Plik, a nie tabela Room, świadomie: oś czyta się i zapisuje w całości,
 * jednym żądaniem, więc rozbicie jej na tabele nic by nie dało — a każda
 * zmiana kontraktu panelu kosztowałaby migrację bazy, o której numer
 * przepychają się równoległe moduły. `filesDir`, nie `cacheDir`: koordynator
 * bez zasięgu ma zobaczyć oś, a nie pustkę po sprzątaniu systemu.
 */
@Singleton
class CrewScheduleCacheStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dir: File by lazy { File(context.filesDir, "crew-schedule").apply { mkdirs() } }

    suspend fun read(from: LocalDate, to: LocalDate): String? = withContext(Dispatchers.IO) {
        runCatching { file(from, to).takeIf { it.isFile }?.readText() }.getOrNull()
    }

    suspend fun write(from: LocalDate, to: LocalDate, body: String) = withContext(Dispatchers.IO) {
        runCatching {
            val target = file(from, to)
            val tmp = File(dir, "${target.name}.tmp")
            tmp.writeText(body)
            if (!tmp.renameTo(target)) {
                target.delete()
                tmp.renameTo(target)
            }
            prune()
        }
    }

    /**
     * Kolejność ekip ułożona bez zasięgu — jedna linia na id, od góry osi.
     * Leży obok okien osi, ale nie kończy się na `.json`, więc `prune()` jej
     * nie rusza. Pusta/brak pliku = nic nie czeka.
     */
    suspend fun readPendingCrewOrder(): List<String>? = withContext(Dispatchers.IO) {
        runCatching {
            pendingOrder.takeIf { it.isFile }?.readLines()?.map { it.trim() }?.filter { it.isNotEmpty() }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    suspend fun writePendingCrewOrder(ids: List<String>) = withContext(Dispatchers.IO) {
        runCatching { pendingOrder.writeText(ids.joinToString("\n")) }
    }

    suspend fun clearPendingCrewOrder() = withContext(Dispatchers.IO) {
        runCatching { pendingOrder.delete() }
    }

    private val pendingOrder: File get() = File(dir, "crew-order.pending")

    /**
     * Dni wolne i pracujące montera (`GET /schedule/my-days`) — jeden plik,
     * ostatnia odpowiedź. Rozszerzenie inne niż `.json`, żeby `prune()` go nie zjadł.
     */
    suspend fun readMyDays(): String? = withContext(Dispatchers.IO) {
        runCatching { myDays.takeIf { it.isFile }?.readText() }.getOrNull()
    }

    suspend fun writeMyDays(body: String) = withContext(Dispatchers.IO) {
        runCatching { myDays.writeText(body) }
    }

    private val myDays: File get() = File(dir, "my-days.cache")

    private fun file(from: LocalDate, to: LocalDate) = File(dir, "${from}_$to.json")

    /** Trzymamy ostatnio oglądane okna, starsze wypadają. */
    private fun prune() {
        val files = dir.listFiles { f -> f.name.endsWith(".json") }?.sortedByDescending { it.lastModified() }
            ?: return
        files.drop(MAX_WINDOWS).forEach { it.delete() }
    }

    private companion object {
        const val MAX_WINDOWS = 16
    }
}
