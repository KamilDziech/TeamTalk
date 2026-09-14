package com.ekotak.teamtalk.data.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Zdjęcie wizytówki przed wysyłką: obrót wg EXIF i zmniejszenie do 2000 px
 * na dłuższym boku, JPEG 88%.
 *
 * Aparat Samsunga zapisuje 12–50 Mpx z obrotem tylko w EXIF-ie. Taki plik to
 * kilka MB na komórkowym łączu, a serwer i tak nie potrzebuje więcej: model
 * ogląda obraz w ~1600 px, a QR czyta się dobrze już przy tysiącu. Obrót
 * wprost w pikselach, bo dekoder QR po stronie serwera EXIF-u nie czyta.
 */
object CardPhoto {
    private const val MAX_SIDE = 2000
    private const val QUALITY = 88

    fun prepare(original: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(original, 0, original.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // Najpierw tanie próbkowanie potęgą dwójki (pamięć), potem dokładne skalowanie.
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(
            original,
            0,
            original.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null

        val rotation = runCatching {
            when (ExifInterface(ByteArrayInputStream(original)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)

        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation)
        }
        val output = if (scale < 1f || rotation != 0f) {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        } else {
            decoded
        }
        return ByteArrayOutputStream().use { out ->
            output.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            if (output !== decoded) output.recycle()
            decoded.recycle()
            out.toByteArray()
        }
    }
}
