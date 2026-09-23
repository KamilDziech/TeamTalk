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
