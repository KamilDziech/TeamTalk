package com.ekotak.teamtalk.data.audio

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zamiana mowy na tekst na urządzeniu przez wbudowany [SpeechRecognizer] (język pl-PL).
 *
 * Dyktowanie ciągłe: po każdym rozpoznanym fragmencie (lub po ciszy) sesja jest
 * automatycznie wznawiana, dzięki czemu użytkownik może mówić dłużej niż jedną
 * wypowiedź. Zakończenie następuje dopiero po [stop] / [cancel].
 *
 * WAŻNE: wszystkie metody muszą być wołane z wątku głównego (wymóg SpeechRecognizer).
 */
@Singleton
class SpeechToText @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    /** Tekst już rozpoznany (zatwierdzone fragmenty). */
    private val finalized = StringBuilder()

    /** Wołane na żywo z pełnym tekstem (zatwierdzone + bieżący fragment). */
    var onText: ((String) -> Unit)? = null

    /** Wołane przy błędzie uniemożliwiającym dalsze rozpoznawanie. */
    var onError: ((String) -> Unit)? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start() {
        if (!isAvailable()) {
            onError?.invoke("Rozpoznawanie mowy niedostępne na tym urządzeniu")
            return
        }
        finalized.clear()
        listening = true
        ensureRecognizer()
        recognizer?.startListening(buildIntent())
    }

    /** Zatrzymuje dyktowanie i zwraca zebrany tekst. */
    fun stop(): String {
        listening = false
        recognizer?.stopListening()
        val text = finalized.toString().trim()
        release()
        return text
    }

    fun cancel() {
        listening = false
        recognizer?.cancel()
        finalized.clear()
        release()
    }

    private fun release() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun ensureRecognizer() {
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(listener)
            }
        }
    }

    /** Wznawia nasłuch (dyktowanie ciągłe), o ile użytkownik nie zakończył. */
    private fun restart() {
        if (!listening) return
        // Drobne opóźnienie stabilizuje ponowny start na części urządzeń.
        mainHandler.post {
            if (listening) {
                ensureRecognizer()
                recognizer?.startListening(buildIntent())
            }
        }
    }

    private fun appendFinal(segment: String) {
        val trimmed = segment.trim()
        if (trimmed.isEmpty()) return
        if (finalized.isNotEmpty()) finalized.append(' ')
        finalized.append(trimmed)
    }

    private fun compose(partial: String): String = buildString {
        append(finalized)
        val p = partial.trim()
        if (p.isNotEmpty()) {
            if (isNotEmpty()) append(' ')
            append(p)
        }
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResults(results: Bundle?) {
            onText?.invoke(compose(results.firstText()))
        }

        override fun onResults(results: Bundle?) {
            appendFinal(results.firstText())
            onText?.invoke(finalized.toString())
            restart()
        }

        override fun onError(error: Int) {
            // Cisza / brak dopasowania to naturalny koniec wypowiedzi — wznawiamy.
            if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            ) {
                restart()
                return
            }
            if (listening) {
                listening = false
                onError?.invoke(errorMessage(error))
                release()
            }
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun Bundle?.firstText(): String =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()

    private fun buildIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pl-PL")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            // Użyj modelu offline, gdy dostępny; w innym wypadku system wybierze online.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            }
        }

    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Błąd nagrywania dźwięku"
        SpeechRecognizer.ERROR_CLIENT -> "Błąd klienta rozpoznawania"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Brak uprawnienia do mikrofonu"
        SpeechRecognizer.ERROR_NETWORK -> "Błąd sieci podczas rozpoznawania"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Przekroczono czas oczekiwania sieci"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Rozpoznawanie mowy zajęte, spróbuj ponownie"
        SpeechRecognizer.ERROR_SERVER -> "Błąd serwera rozpoznawania"
        else -> "Nie udało się rozpoznać mowy"
    }
}
