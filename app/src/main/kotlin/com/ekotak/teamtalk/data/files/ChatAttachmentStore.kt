package com.ekotak.teamtalk.data.files

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.ekotak.teamtalk.domain.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Docelowa krawędź miniatury zdjęcia w dymku — tyle, ile zajmuje na ekranie. */
private const val THUMB_PX = 720

/**
 * Zdjęcia i pliki z Komunikatora.
 *
 * Załącznik jest NIEZMIENNY (wiadomości się nie edytuje), więc raz pobrany plik
 * zostaje w cache'u telefonu i drugi raz nie leci przez sieć — a dymek ze
 * zdjęciem z montażu nie mruga przy każdym przewinięciu listy.
 *
 * Bitmapy trzymamy w mapie w pamięci procesu: wątek rozmowy przewija się w tę
 * i z powrotem, a dekodowanie JPEG-a przy każdym przejściu przez ekran
 * zrywałoby płynność przewijania.
 */
@Singleton
class ChatAttachmentStore @Inject constructor(
    private val chat: ChatRepository,
) {
    private val thumbs = mutableMapOf<String, ImageBitmap>()

    /** Gotowa miniatura, jeśli już ją mamy — do pierwszego rysowania bez migania. */
    fun cached(messageId: String): ImageBitmap? = thumbs[messageId]

    suspend fun image(messageId: String, fileName: String): ImageBitmap? =
        thumbs[messageId] ?: withContext(Dispatchers.IO) {
            val file = runCatching { chat.downloadAttachment(messageId, fileName) }.getOrNull()
                ?: return@withContext null
            val bitmap = decodeSampled(file) ?: return@withContext null
            val image = bitmap.asImageBitmap()
            thumbs[messageId] = image
            image
        }

    /** Plik do otwarcia systemowym podglądem (PDF, dokument, nagranie). */
    suspend fun file(messageId: String, fileName: String): File? = withContext(Dispatchers.IO) {
        runCatching { chat.downloadAttachment(messageId, fileName) }.getOrNull()
    }

    /** Dekodowanie z pomniejszeniem — pełny kadr z aparatu to kilkanaście MB. */
    private fun decodeSampled(file: File) = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > THUMB_PX) sample *= 2
        BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }.getOrNull()
}
