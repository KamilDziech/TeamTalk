package com.ekotak.teamtalk.data.meeting

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Nagrywarka spotkania (moduł Spotkanie, 2026-09-28). Jedna na proces, sterowana
 * przez [com.ekotak.teamtalk.service.MeetingRecordingService], który trzyma ją
 * przy życiu jako usługa pierwszoplanowa z mikrofonem — dzięki temu nagrywa
 * w tle i przy zablokowanym ekranie.
 *
 * Format: AAC w strumieniu ADTS (.aac), mono 16 kHz, 32 kb/s — 2 h mowy to ~29 MB.
 * ADTS, a nie MP4, bo MP4 bez domknięcia (padnięty proces, rozładowany telefon)
 * jest nieczytelny, a ADTS da się odczytać do ostatniej zapisanej ramki. Plik
 * leży w `filesDir`, nie w cache — to jedyna kopia do czasu wysyłki.
 *
 * Aktywne nagranie zapisujemy w SharedPreferences: gdy proces zginie w trakcie,
 * [recoverOrphan] przy następnym starcie aplikacji wyśle to, co zdążyło się nagrać.
 */
@Singleton
class MeetingRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    enum class Phase { IDLE, RECORDING, PAUSED }

    data class State(
        val meetingId: String? = null,
        val title: String = "",
        val phase: Phase = Phase.IDLE,
        /** Czas nagrania netto (bez pauz) w chwili [sinceElapsed]. */
        val recordedMs: Long = 0,
        /** `SystemClock.elapsedRealtime()` ostatniego wznowienia; 0 = pauza/stop. */
        val sinceElapsed: Long = 0,
    ) {
        fun recordedNowMs(): Long =
            recordedMs + if (phase == Phase.RECORDING && sinceElapsed > 0) SystemClock.elapsedRealtime() - sinceElapsed else 0
    }

    data class Finished(val meetingId: String, val file: File, val durationSec: Int)

    private val prefs = context.getSharedPreferences("meeting_recording", Context.MODE_PRIVATE)
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    val isActive: Boolean get() = _state.value.phase != Phase.IDLE

    /**
     * [day] — dzień spotkania wielodniowego (v2, D9: jedno nagranie na dzień).
     * Trafia do nazwy pliku, a ta jest kluczem pracy wysyłki — dzień 2 nie
     * zderzy się z niewysłanym jeszcze dniem 1 tego samego spotkania.
     */
    fun start(meetingId: String, title: String, day: Int = 1) {
        if (isActive) return
        val dir = File(context.filesDir, "meetings").apply { mkdirs() }
        val out = File(dir, "${meetingId}_d${day}_${System.currentTimeMillis()}.aac")
        @Suppress("DEPRECATION")
        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
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
        recorder = mr
        file = out
        prefs.edit()
            .putString(KEY_MEETING, meetingId)
            .putString(KEY_PATH, out.absolutePath)
            .putLong(KEY_STARTED, System.currentTimeMillis())
            .apply()
        _state.value = State(meetingId, title, Phase.RECORDING, 0, SystemClock.elapsedRealtime())
    }

    fun pause() {
        val s = _state.value
        if (s.phase != Phase.RECORDING) return
        runCatching { recorder?.pause() }
        _state.value = s.copy(phase = Phase.PAUSED, recordedMs = s.recordedNowMs(), sinceElapsed = 0)
    }

    fun resume() {
        val s = _state.value
        if (s.phase != Phase.PAUSED) return
        runCatching { recorder?.resume() }
        _state.update { it.copy(phase = Phase.RECORDING, sinceElapsed = SystemClock.elapsedRealtime()) }
    }

    /** Zatrzymuje i zwraca plik do wysyłki (null, gdy nic nie nagrano). */
    fun stop(): Finished? {
        val s = _state.value
        val id = s.meetingId ?: return null
        val out = file
        val durationSec = (s.recordedNowMs() / 1000).toInt()
        try {
            recorder?.stop()
        } catch (_: Exception) {
            // stop() rzuca, gdy nie zapisano ani jednej ramki — plik i tak zostawiamy,
            // serwer oceni, czy jest co spisać.
        }
        runCatching { recorder?.release() }
        recorder = null
        file = null
        prefs.edit().clear().apply()
        _state.value = State()
        return out?.takeIf { it.exists() && it.length() > 0 }?.let { Finished(id, it, durationSec) }
    }

    /**
     * Nagranie osierocone przez padnięty proces: plik jest, nagrywarki już nie ma.
     * Zwraca je do wysyłki i czyści znacznik. Wołane przy starcie aplikacji.
     */
    fun recoverOrphan(): Finished? {
        if (isActive) return null
        val id = prefs.getString(KEY_MEETING, null) ?: return null
        val path = prefs.getString(KEY_PATH, null)
        val started = prefs.getLong(KEY_STARTED, 0L)
        prefs.edit().clear().apply()
        val f = path?.let { File(it) }?.takeIf { it.exists() && it.length() > 0 } ?: return null
        val sec = if (started > 0) ((f.lastModified() - started) / 1000).toInt().coerceAtLeast(0) else 0
        return Finished(id, f, sec)
    }

    private companion object {
        const val KEY_MEETING = "meeting_id"
        const val KEY_PATH = "path"
        const val KEY_STARTED = "started_at"
    }
}
