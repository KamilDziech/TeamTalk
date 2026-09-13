package com.ekotak.teamtalk.data.recording

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max

/**
 * Odnajduje nagranie rozmowy zapisane przez systemową aplikację Telefon.
 *
 * Od Androida 10 zwykła aplikacja nie ma dostępu do dźwięku rozmowy GSM, więc
 * TeamTalk sam nie nagrywa. Samsung robi to wbudowaną nagrywarką (Telefon →
 * Ustawienia → Nagrywaj połączenia → automatycznie) i odkłada plik do
 * `Recordings/Call/` (starsze One UI: `Call/`, `Sounds/Call/`). Plik widać
 * w MediaStore jak każdy inny dźwięk.
 *
 * Nazwa pliku jest zlokalizowana i zawiera nazwę kontaktu, więc na niej się nie
 * opieramy — dopasowanie idzie po CZASIE (plik powstał między startem połączenia
 * a kilkoma minutami po jego końcu) i po DŁUGOŚCI (nagranie trwa tyle, co
 * rozmowa z rejestru połączeń).
 */
@Singleton
class CallRecordingFinder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class Recording(
        val uri: Uri,
        val displayName: String,
        val mimeType: String,
        val sizeBytes: Long,
        val durationMs: Long?,
        val addedAtMs: Long,
    )

    /** Uprawnienie do odczytu cudzych plików audio (Android 13+: READ_MEDIA_AUDIO). */
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED

    /**
     * Najlepiej pasujące nagranie albo null, gdy go (jeszcze) nie ma — nagrywarka
     * dopisuje plik do MediaStore dopiero po zamknięciu, zwykle kilka sekund po
     * rozłączeniu.
     */
    fun find(callStartMs: Long, callEndMs: Long, callDurationSec: Int?): Recording? {
        if (!hasPermission()) return null
        val candidates = query(sinceMs = callStartMs - WINDOW_BEFORE_MS)
            .filter { isCallRecordingPath(it.second) }
            .map { it.first }
            .filter { it.addedAtMs in (callStartMs - WINDOW_BEFORE_MS)..(callEndMs + WINDOW_AFTER_MS) }
            .filter { durationMatches(it.durationMs, callDurationSec) }
        return candidates.minByOrNull { abs(it.addedAtMs - callEndMs) }
    }

    /** Kopia nagrania do katalogu cache — Retrofit wysyła plik, nie strumień z content://. */
    fun copyToCache(recording: Recording): File? = runCatching {
        val dir = File(context.cacheDir, "call-recordings").apply { mkdirs() }
        val ext = recording.displayName.substringAfterLast('.', "m4a").take(5)
        val target = File(dir, "rec-${recording.addedAtMs}.$ext")
        context.contentResolver.openInputStream(recording.uri)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        } ?: return null
        target
    }.getOrNull()

    private fun query(sinceMs: Long): List<Pair<Recording, String>> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.RELATIVE_PATH
        } else {
            @Suppress("DEPRECATION") MediaStore.Audio.Media.DATA
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            pathColumn,
        )
        val out = mutableListOf<Pair<Recording, String>>()
        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                "${MediaStore.Audio.Media.DATE_ADDED} >= ?",
                arrayOf((sinceMs / 1000).toString()),
                "${MediaStore.Audio.Media.DATE_ADDED} DESC",
            )?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val mimeIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val sizeIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val durIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val addedIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val pathIdx = c.getColumnIndexOrThrow(pathColumn)
                while (c.moveToNext()) {
                    val id = c.getLong(idIdx)
                    val rec = Recording(
                        uri = ContentUris.withAppendedId(collection, id),
                        displayName = c.getString(nameIdx) ?: "nagranie.m4a",
                        mimeType = c.getString(mimeIdx) ?: "audio/mp4",
                        sizeBytes = c.getLong(sizeIdx),
                        durationMs = if (c.isNull(durIdx)) null else c.getLong(durIdx),
                        addedAtMs = c.getLong(addedIdx) * 1000,
                    )
                    out += rec to (c.getString(pathIdx) ?: "")
                }
            }
        }
        return out
    }

    private fun durationMatches(recordingMs: Long?, callDurationSec: Int?): Boolean {
        if (recordingMs == null || recordingMs <= 0 || callDurationSec == null || callDurationSec <= 0) return true
        val diffSec = abs(recordingMs / 1000.0 - callDurationSec)
        return diffSec <= max(DURATION_TOLERANCE_SEC, callDurationSec * DURATION_TOLERANCE_RATIO)
    }

    companion object {
        /** Samsung zakłada plik w chwili odebrania, więc potrafi być starszy niż koniec rozmowy. */
        private const val WINDOW_BEFORE_MS = 15_000L
        /** Zapis długiej rozmowy do m4a trwa; po kwadransie to już na pewno inne nagranie. */
        private const val WINDOW_AFTER_MS = 15 * 60_000L
        private const val DURATION_TOLERANCE_SEC = 6.0
        private const val DURATION_TOLERANCE_RATIO = 0.15

        fun audioPermission(): String =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
            else Manifest.permission.READ_EXTERNAL_STORAGE

        /**
         * Katalog nagrywarki rozmów: segment `Call` w ścieżce (`Recordings/Call/`,
         * `Call/`, `Sounds/Call/`). Komunikatory (WhatsApp trzyma notatki głosowe
         * w `Android/media/…`) i dyktafon (`Recordings/Voice Recorder/`) odpadają.
         */
        fun isCallRecordingPath(path: String): Boolean {
            val normalized = path.replace('\\', '/').trimEnd('/')
            if (normalized.contains("Android/media", ignoreCase = true)) return false
            val segments = normalized.split('/')
            return segments.any { it.equals("Call", ignoreCase = true) || it.equals("Call recordings", ignoreCase = true) }
        }
    }
}
