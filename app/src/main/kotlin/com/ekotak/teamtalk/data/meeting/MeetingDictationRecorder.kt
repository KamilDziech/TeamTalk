package com.ekotak.teamtalk.data.meeting

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dyktowanie „Co chcesz omówić?" w kreatorze spotkania (v2, D15). Krótka
 * wypowiedź do pliku w `cacheDir` — to NIE jest jedyna kopia czegoś ważnego
 * (tekst zawsze można wpisać ręcznie), więc cache wystarcza, a plik kasuje
 * wołający po wysyłce.
 *
 * Osobna od [MeetingRecorder] (ta nagrywa całe spotkanie w usłudze) i od
 * nagrywarki głosówek czatu — żeby jedna nie zatrzymała drugiej.
 * Format: AAC w MP4 (.m4a), mono 16 kHz — Whisperowi wystarczy, plik mały.
 */
@Singleton
class MeetingDictationRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null

    val isRecording: Boolean get() = recorder != null

    fun start() {
        if (recorder != null) return
        val out = File(context.cacheDir, "meeting_dictation_${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION")
        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        try {
            mr.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioSamplingRate(16_000)
                setAudioEncodingBitRate(48_000)
                setOutputFile(out.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            runCatching { mr.release() }
            out.delete()
            throw e
        }
        recorder = mr
        file = out
    }

    /** Zatrzymuje i zwraca plik (null, gdy nic się nie nagrało — np. od razu stop). */
    fun stop(): File? {
        val mr = recorder ?: return null
        val out = file
        recorder = null
        file = null
        val ok = try {
            mr.stop(); true
        } catch (_: Exception) {
            // stop() rzuca, gdy nie zapisano ani jednej ramki — plik MP4 jest wtedy pusty.
            false
        }
        runCatching { mr.release() }
        if (!ok) {
            out?.delete()
            return null
        }
        return out?.takeIf { it.exists() && it.length() > 0 }
    }

    fun cancel() {
        stop()?.delete()
    }
}
