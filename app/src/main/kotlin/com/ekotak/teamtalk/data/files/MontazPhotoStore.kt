package com.ekotak.teamtalk.data.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.domain.model.MontazPhoto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TREŚĆ zdjęć powykonawczych montażu — pobieranie, pamięć podręczna i miniatury.
 *
 * Osobno od `DocumentFileStore`, bo zdjęcia montażu schodzą inną trasą
 * (`/api/installations/{id}/photos/{photoId}`) i nie są plikami deala. Reszta
 * decyzji jest ta sama: bez biblioteki do obrazów (projekt buduje się offline),
 * z dwoma katalogami o różnej obietnicy trwałości —
 *  • `cacheDir/montaz-photos` — pobrane kadry; system może je skasować,
 *  • `filesDir/montaz-photos/outbox` — KOPIE zdjęć czekających w kolejce. To
 *    jedyna kopia kadru zrobionego w kotłowni bez zasięgu, więc nie leży
 *    w cache i nie zależy od tego, czy ktoś skasował oryginał z galerii.
 */
@Singleton
class MontazPhotoStore @Inject constructor(
    private val api: TeamTalkApi,
    @ApplicationContext private val context: Context,
) {

    private val memory = LruCache<String, ImageBitmap>(MEMORY_ENTRIES)
    private val locks = mutableMapOf<String, Mutex>()
    private val locksGuard = Mutex()

    private val cacheDir: File by lazy { File(context.cacheDir, "montaz-photos").apply { mkdirs() } }
    private val outboxDir: File by lazy {
        File(File(context.filesDir, "montaz-photos"), "outbox").apply { mkdirs() }
    }

    /** Miniatura z pamięci, jeśli już jest — pierwsza klatka bez migotania. */
    fun cached(photo: MontazPhoto, targetPx: Int = THUMB_PX): ImageBitmap? =
        memory.get(key(photo.id, targetPx))

    /**
     * Kadr jako bitmapa. Zdjęcie z kolejki czytamy z `outbox` — bez ruchu po
     * sieci, bo serwer o nim jeszcze nie wie.
     */
    suspend fun image(photo: MontazPhoto, targetPx: Int = THUMB_PX): ImageBitmap? {
        val key = key(photo.id, targetPx)
        memory.get(key)?.let { return it }
        return withLockFor(key) {
            memory.get(key)?.let { return@withLockFor it }
            val file = content(photo) ?: return@withLockFor null
            val bitmap = withContext(Dispatchers.IO) { decode(file, targetPx) }
                ?: return@withLockFor null
            val image = bitmap.asImageBitmap()
            memory.put(key, image)
            image
        }
    }

    /** Plik z treścią zdjęcia albo `null`, gdy nie da się jej teraz zdobyć. */
    private suspend fun content(photo: MontazPhoto): File? {
        photo.localPath?.let { path ->
            val staged = File(path)
            return if (staged.isFile) staged else null
        }
        val cached = File(cacheDir, photo.id)
        if (cached.isFile && cached.length() > 0) return cached
        return withContext(Dispatchers.IO) {
            val bytes = runCatching {
                api.downloadMontazPhoto(photo.installationId, photo.id).use { it.bytes() }
            }.getOrNull() ?: return@withContext null
            runCatching { cached.writeBytes(bytes) }.getOrNull() ?: return@withContext null
            cached
        }
    }

    /**
     * Odkłada kadr do `outbox` i oddaje ścieżkę kopii. Wywołujący zapisuje ją
     * w wierszu `montaz_photos`, więc po restarcie telefonu kolejka wciąż wie,
     * co ma wysłać.
     */
    fun stage(bytes: ByteArray, fileName: String): File? {
        val safe = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(48)
        val target = File(outboxDir, "${System.currentTimeMillis()}-$safe")
        return runCatching { target.writeBytes(bytes); target }.getOrNull()
    }

    /** Kopia wysłana (albo odrzucona przez serwer) — nie ma po co jej trzymać. */
    fun dropStaged(path: String?) {
        if (path == null) return
        runCatching { File(path).delete() }
    }

    /**
     * Zmniejszamy przy dekodowaniu: zdjęcie z aparatu ma kilkanaście megapikseli,
     * a kafelek kilkadziesiąt punktów — pełna bitmapa byłaby czystą stratą
     * pamięci przy siatce kadrów.
     */
    private fun decode(file: File, targetPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val longer = maxOf(bounds.outWidth, bounds.outHeight)
        if (longer <= 0) return null
        var sample = 1
        while (longer / sample > targetPx * 2) sample *= 2
        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }

    private fun key(id: String, targetPx: Int) = "$id#$targetPx"

    /** Jedno pobranie na klucz — siatka kafelków prosi o to samo naraz. */
    private suspend fun <T> withLockFor(key: String, block: suspend () -> T): T {
        val lock = locksGuard.withLock { locks.getOrPut(key) { Mutex() } }
        return lock.withLock { block() }
    }

    private companion object {
        const val MEMORY_ENTRIES = 32
        const val THUMB_PX = 240
    }
}
