package com.ekotak.teamtalk.data.files

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zmniejszanie kadrów audytu przed wrzuceniem ich do kolejki.
 *
 * Zdjęcie z aparatu ma 5–8 MB. Komplet ośmiu kadrów to ~50 MB, a robi się je
 * pod lasem, na jednej kresce LTE — bez zmniejszenia kolejka wysyłałaby audyt
 * do wieczora, zużywając baterię i pakiet. Dłuższy bok 2000 px wystarcza, żeby
 * przeczytać tabliczkę znamionową i policzyć pętle na rozdzielaczu, a plik
 * schodzi do 600–900 KB.
 *
 * Kopii w pełnej rozdzielczości NIE trzymamy — byłaby drugą kopią tego samego
 * w pamięci telefonu, której nikt nigdy nie ogląda.
 *
 * Obrót z EXIF-a wpalamy w piksele, bo przy ponownym kodowaniu znaczniki EXIF
 * przepadają: bez tego kadr zrobiony pionowo wracałby w panelu położony na bok.
 */
@Singleton
class AuditPhotoScaler @Inject constructor() {

    /**
     * Zmniejszony JPEG albo — gdy czegokolwiek nie da się zrobić — bajty
     * wejściowe bez zmian. Zdjęcie zrobione w terenie ma trafić do kolejki
     * nawet wtedy, gdy dekodowanie się nie powiodło.
     */
    fun downscale(bytes: ByteArray, maxPx: Int = MAX_PX, quality: Int = QUALITY): ByteArray =
        runCatching { scale(bytes, maxPx, quality) }.getOrNull() ?: bytes

    private fun scale(bytes: ByteArray, maxPx: Int, quality: Int): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val longer = maxOf(bounds.outWidth, bounds.outHeight)
        if (longer <= 0) return null

        // Dwustopniowo: `inSampleSize` schodzi potęgami dwójki (tanio, bez
        // trzymania pełnej bitmapy w pamięci), a dokładny rozmiar dociągamy
        // skalowaniem — inaczej kadr 4000 px zostałby 2500 px albo 1250 px.
        var sample = 1
        while (longer / sample > maxPx * 2) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null

        val rotated = applyExifRotation(decoded, bytes)
        val current = maxOf(rotated.width, rotated.height)
        val target = if (current > maxPx) {
            val ratio = maxPx.toDouble() / current
            Bitmap.createScaledBitmap(
                rotated,
                (rotated.width * ratio).toInt().coerceAtLeast(1),
                (rotated.height * ratio).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            rotated
        }

        val out = ByteArrayOutputStream()
        target.compress(Bitmap.CompressFormat.JPEG, quality, out)
        val result = out.toByteArray()
        // Zmniejszony plik większy od oryginału (mały kadr, mocna kompresja
        // źródła) — wtedy nie ma czego poprawiać.
        return if (result.size < bytes.size) result else null
    }

    /** Obraca bitmapę zgodnie z EXIF-em oryginału (brak znacznika = bez zmian). */
    private fun applyExifRotation(bitmap: Bitmap, bytes: ByteArray): Bitmap {
        val degrees = runCatching {
            val exif = ExifInterface(ByteArrayInputStream(bytes))
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        if (degrees == 0f) return bitmap
        return runCatching {
            Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                Matrix().apply { postRotate(degrees) },
                true,
            )
        }.getOrDefault(bitmap)
    }

    private companion object {
        /** Dłuższy bok kadru po zmniejszeniu [px]. */
        const val MAX_PX = 2000

        /** Jakość JPEG — 85% to granica, poniżej której widać artefakty na tabliczkach. */
        const val QUALITY = 85
    }
}
