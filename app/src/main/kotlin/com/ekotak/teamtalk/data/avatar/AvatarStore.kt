package com.ekotak.teamtalk.data.avatar

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zdjęcia profilowe zespołu — te same, które pokazuje panel board360
 * (`GET /api/users/:id/avatar`). TeamTalk i panel siedzą na jednym API, więc
 * awatar ma być ten sam człowiek na obu ekranach, a nie dwa różne kółka.
 *
 * Bez biblioteki do obrazów (Coil/Glide): projekt buduje się offline, a jedna
 * mała rzecz nie jest warta nowej zależności. Stąd własne trzy piętra:
 *  • pamięć — [LruCache] na przewijanie list bez mrugania,
 *  • dysk (`cacheDir/avatars`) — żeby zdjęcia były też bez zasięgu i po
 *    restarcie aplikacji; katalog kasowalny przez system bez straty danych,
 *  • sieć — dopiero gdy dwa wyższe piętra nie mają nic.
 *
 * 404 („brak zdjęcia") zapamiętujemy w [missing]: to normalny stan konta, nie
 * awaria, i nie ma po co pytać o nie przy każdym otwarciu filtra.
 */
@Singleton
class AvatarStore @Inject constructor(
    private val api: TeamTalkApi,
    @ApplicationContext private val context: Context,
) {

    private val memory = LruCache<String, ImageBitmap>(MEMORY_ENTRIES)
    private val missing = mutableSetOf<String>()
    private val locks = mutableMapOf<String, Mutex>()
    private val locksGuard = Mutex()

    private val dir: File by lazy { File(context.cacheDir, "avatars").apply { mkdirs() } }

    /** Zdjęcie z pamięci, jeśli już jest — do pierwszej klatki bez migotania. */
    fun cached(userId: String): ImageBitmap? = memory.get(userId)

    /**
     * Zdjęcie osoby albo `null`, gdy konto go nie ma (albo nie da się go teraz
     * pobrać). Wywołujący pokazuje wtedy kółko z inicjałami.
     */
    suspend fun avatar(userId: String): ImageBitmap? {
        memory.get(userId)?.let { return it }
        synchronized(missing) { if (userId in missing) return null }

        // Jedno pobranie na osobę: lista osób pokazuje ten sam awatar w kilku
        // miejscach naraz (chip „Moje" i wiersz w dziale).
        val lock = locksGuard.withLock { locks.getOrPut(userId) { Mutex() } }
        return lock.withLock {
            memory.get(userId)?.let { return@withLock it }
            withContext(Dispatchers.IO) {
                fromDisk(userId)?.let { return@withContext it }
                fromNetwork(userId)
            }
        }
    }

    private fun fromDisk(userId: String): ImageBitmap? {
        val file = File(dir, userId)
        if (!file.isFile) return null
        val bitmap = decode(runCatching { file.readBytes() }.getOrNull() ?: return null)
        return bitmap?.also { memory.put(userId, it) }
    }

    private suspend fun fromNetwork(userId: String): ImageBitmap? {
        val bytes = runCatching { api.getUserAvatar(userId).use { it.bytes() } }
            .getOrElse { err ->
                // 404 = konto bez zdjęcia; każdy inny błąd (brak zasięgu, 5xx)
                // zostawiamy nierozstrzygnięty, żeby spróbować później.
                if (err.isNotFound()) synchronized(missing) { missing += userId }
                return null
            }
        if (bytes.size > MAX_BYTES) return null
        val bitmap = decode(bytes) ?: return null
        memory.put(userId, bitmap)
        runCatching { File(dir, userId).writeBytes(bytes) }
        return bitmap
    }

    /**
     * Dekodowanie ze zmniejszeniem: panel przyjmuje zdjęcia do 5 MB, a na liście
     * kółko ma kilkadziesiąt pikseli — pełna bitmapa byłaby czystą stratą pamięci.
     */
    private fun decode(bytes: ByteArray): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val longer = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longer / sample > TARGET_PX * 2) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }

    private companion object {
        const val MEMORY_ENTRIES = 64
        const val TARGET_PX = 96
        const val MAX_BYTES = 5 * 1024 * 1024
    }
}

/** Czy błąd Retrofita to 404 — czyli „konto nie ma zdjęcia". */
private fun Throwable.isNotFound(): Boolean =
    this is retrofit2.HttpException && code() == 404
