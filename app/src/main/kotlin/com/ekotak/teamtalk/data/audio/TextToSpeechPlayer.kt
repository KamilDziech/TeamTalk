package com.ekotak.teamtalk.data.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Czytanie odpowiedzi asystenta na głos (wbudowany [TextToSpeech], pl-PL).
 *
 * Silnik startuje LENIWIE — dopiero przy pierwszym [speak], bo większość wejść
 * do asystenta kończy się czytaniem z ekranu i nie ma po co trzymać silnika w
 * pamięci. Wypowiedzi nie kolejkujemy: nowa odpowiedź przerywa poprzednią
 * (QUEUE_FLUSH), inaczej po serii pytań telefon mówiłby sam do siebie.
 *
 * Gdy w systemie nie ma polskiego głosu, [speak] po prostu milczy — ekran i tak
 * pokazuje odpowiedź, więc nie ma sensu straszyć użytkownika błędem.
 */
@Singleton
class TextToSpeechPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var engine: TextToSpeech? = null
    private var ready = false

    /** Tekst czekający na dokończenie inicjalizacji silnika (jeden, ostatni). */
    private var pending: String? = null

    fun speak(text: String) {
        val content = text.trim()
        if (content.isEmpty()) return
        val current = engine
        if (current != null && ready) {
            current.speak(content, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
            return
        }
        pending = content
        if (current == null) init()
    }

    fun stop() {
        pending = null
        engine?.stop()
    }

    /** Zwalnia silnik — wołane przy opuszczeniu ekranu asystenta. */
    fun release() {
        pending = null
        engine?.stop()
        engine?.shutdown()
        engine = null
        ready = false
    }

    private fun init() {
        engine = TextToSpeech(context) { status ->
            val tts = engine ?: return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) {
                ready = false
                pending = null
                return@TextToSpeech
            }
            val result = tts.setLanguage(Locale.forLanguageTag("pl-PL"))
            ready = result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
            val waiting = pending
            pending = null
            if (ready && waiting != null) {
                tts.speak(waiting, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
            }
        }
    }

    private companion object {
        const val UTTERANCE_ID = "assistant-reply"
    }
}
