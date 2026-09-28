package com.ekotak.teamtalk.data.meeting

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File

/**
 * Podsumowanie spotkania nagrane głosem przez prowadzącego (D9, 2026-09-28) —
 * gdy spotkanie nie ma nagrania. To 1–3 minuty przy otwartym oknie, więc bez
 * usługi pierwszoplanowej: nagrywarka żyje w ViewModelu karty spotkania.
 * Format jak nagranie spotkania ([MeetingRecorder]): AAC ADTS, mono 16 kHz, 32 kb/s.
 */
class VoiceSummaryRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedElapsed = 0L

    val isRecording: Boolean get() = recorder != null

    /** Milisekundy od startu bieżącego nagrania (0, gdy nie nagrywa). */
    fun elapsedMs(): Long = if (recorder != null) SystemClock.elapsedRealtime() - startedElapsed else 0

    fun start(meetingId: String) {
        if (recorder != null) return
        val dir = File(context.filesDir, "meetings").apply { mkdirs() }
        val out = File(dir, "${filePrefix(meetingId)}${System.currentTimeMillis()}.aac")
        @Suppress("DEPRECATION")
        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        try {
            mr.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioSamplingRate(16_000)
                setAudioEncodingBitRate(32_000)
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
        startedElapsed = SystemClock.elapsedRealtime()
    }

    /** Zatrzymuje i zwraca plik z długością w sekundach (null, gdy nic się nie nagrało). */
    fun stop(): Pair<File, Int>? {
        val mr = recorder ?: return null
        val sec = (elapsedMs() / 1000).toInt()
        runCatching { mr.stop() }
        runCatching { mr.release() }
        recorder = null
        val out = file
        file = null
        return out?.takeIf { it.exists() && it.length() > 0 }?.let { it to sec }
    }

    /** Przerwanie (wyjście z ekranu w trakcie) — nagranie do kosza. */
    fun cancel() {
        stop()?.first?.delete()
    }

    companion object {
        fun filePrefix(meetingId: String) = "summary_${meetingId}_"

        /** Starsze, niewysłane podsumowania tego spotkania — przy nowym zostają niepotrzebne. */
        fun deleteOthers(context: Context, meetingId: String, keep: File) {
            File(context.filesDir, "meetings")
                .listFiles { f -> f.name.startsWith(filePrefix(meetingId)) && f != keep }
                ?.forEach { it.delete() }
        }
    }
}
