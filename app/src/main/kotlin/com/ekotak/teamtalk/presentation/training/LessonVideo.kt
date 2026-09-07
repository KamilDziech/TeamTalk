package com.ekotak.teamtalk.presentation.training

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.ekotak.teamtalk.BuildConfig
import com.ekotak.teamtalk.domain.model.TrainingLesson
import java.net.URI

/**
 * Odtwarzacz lekcji. Panel osadza film w `iframe`, więc na telefonie robi to
 * `WebView` — to jedyne miejsce w aplikacji, gdzie treść przychodzi jako strona,
 * a nie jako JSON.
 *
 * Dla YouTube ładujemy IFrame API i **liczymy sekundy faktycznie odtworzone**
 * (tick co sekundę tylko w stanie PLAYING), więc przewinięcie na koniec nie
 * odblokuje testu. Loom i inne źródła takiego sygnału nie dają — tam bramką jest
 * ręczne „Obejrzałem film" po chwili spędzonej na ekranie.
 */

/** Id filmu z linku YouTube (`watch?v=`, `youtu.be/`, `/embed/`). */
fun youtubeId(url: String): String? = try {
    val uri = URI(url)
    val host = uri.host.orEmpty()
    val path = uri.path.orEmpty()
    val id = when {
        host.contains("youtu.be") -> path.trimStart('/').substringBefore('/')
        path.startsWith("/embed/") -> path.removePrefix("/embed/").substringBefore('/')
        else -> uri.query.orEmpty()
            .split("&")
            .firstOrNull { it.startsWith("v=") }
            ?.removePrefix("v=")
            .orEmpty()
    }
    id.ifBlank { null }
} catch (_: Exception) {
    null
}

/** Link osadzenia dla źródeł innych niż YouTube (dziś: Loom). */
private fun loomEmbedUrl(url: String): String? = try {
    val id = URI(url).path.orEmpty().trimEnd('/').substringAfterLast('/')
    id.ifBlank { null }?.let { "https://www.loom.com/embed/$it" }
} catch (_: Exception) {
    null
}

/** Czy da się w ogóle osadzić ten link — inaczej ekran proponuje przeglądarkę. */
fun canEmbed(lesson: TrainingLesson): Boolean =
    youtubeId(lesson.videoUrl) != null || loomEmbedUrl(lesson.videoUrl) != null

/**
 * Ramka odtwarzacza. Wysokość MUSI być w `vw`, nie w procentach: Compose tworzy
 * `WebView` w `factory` i od razu ładuje stronę, więc dokument dostaje wysokość
 * okna 0 i cała kaskada `height:100%` (html → body → iframe) pada do zera.
 * Sprawdzone na SM-S928B 2026-09-07: iframe miał 352×0 px, czyli w aplikacji
 * widać było czarny prostokąt. `vw` liczy się od szerokości, która jest znana
 * od pierwszego layoutu, a ramka na ekranie lekcji jest 16:9 (`56.25vw`).
 */
private const val PLAYER_CSS =
    "html,body{margin:0;background:#000;overflow:hidden}" +
        "#p{display:block;border:0;width:100vw;height:56.25vw}"

/**
 * Strona odtwarzacza. Dla YouTube z mostkiem raportującym postęp, dla reszty
 * gołe `iframe` — bez API dostawcy nie ma czego mierzyć.
 */
private fun playerHtml(lesson: TrainingLesson): String {
    val videoId = youtubeId(lesson.videoUrl)
    if (videoId != null) {
        return """
            <!doctype html><html><head>
            <meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no">
            <style>$PLAYER_CSS</style>
            </head><body><div id="p"></div>
            <script src="https://www.youtube.com/iframe_api"></script>
            <script>
              var player, played = 0, ticker = null;
              function onYouTubeIframeAPIReady() {
                player = new YT.Player('p', {
                  videoId: '$videoId',
                  playerVars: { playsinline: 1, rel: 0, modestbranding: 1 },
                  events: { onStateChange: onState, onError: onError }
                });
              }
              function onState(e) {
                if (e.data === YT.PlayerState.PLAYING) { start(); } else { stop(); }
                if (e.data === YT.PlayerState.ENDED) { Bridge.ended(); }
              }
              /* 101/150 = właściciel zabronił osadzania, 100 = film zdjęty,
                 152/153 = YouTube odrzucił stronę osadzającą. Bez tego sygnału
                 ekran zostawał z zablokowanym testem i zero wyjaśnienia. */
              function onError(e) { stop(); Bridge.failed(e.data | 0); }
              /* Liczymy CZAS ODTWARZANIA, nie pozycję — przewijanie do przodu
                 nie może liczyć się jako obejrzane. */
              function start() {
                if (ticker) return;
                ticker = setInterval(function () {
                  played += 1;
                  var d = (player && player.getDuration) ? player.getDuration() : 0;
                  if (d > 0) Bridge.progress(Math.min(1, played / d));
                }, 1000);
              }
              function stop() { if (ticker) { clearInterval(ticker); ticker = null; } }
            </script></body></html>
        """.trimIndent()
    }

    val embed = loomEmbedUrl(lesson.videoUrl) ?: lesson.videoUrl
    return """
        <!doctype html><html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no">
        <style>$PLAYER_CSS</style>
        </head><body>
        <iframe id="p" src="$embed" allow="autoplay; fullscreen; picture-in-picture" allowfullscreen></iframe>
        </body></html>
    """.trimIndent()
}

/** Most JS → Kotlin. Wywołania lecą z wątku WebView, więc wracamy na główny. */
private class VideoBridge(
    private val onProgress: (Float) -> Unit,
    private val onEnded: () -> Unit,
    private val onFailed: (Int) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun progress(fraction: Double) {
        main.post { onProgress(fraction.toFloat()) }
    }

    @JavascriptInterface
    fun ended() {
        main.post { onEnded() }
    }

    @JavascriptInterface
    fun failed(code: Int) {
        main.post { onFailed(code) }
    }
}

/**
 * Pochodzenie strony odtwarzacza. NIE może to być `https://www.youtube.com`:
 * YouTube odrzuca osadzenie, które podaje się za jego własną domenę, i zwraca
 * „Ten film jest niedostępny, kod błędu 152" — nawet dla filmu, który normalnie
 * osadza się bez problemu (sprawdzone 2026-09-07 na tym samym filmie ze strony
 * na `localhost:3000`). Bierzemy więc adres naszego API: to prawdziwa domena,
 * a IFrame API i tak samo dopisze `origin` z `location.origin`.
 */
private val playerBaseUrl: String = BuildConfig.API_BASE_URL.trimEnd('/')

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LessonVideo(
    lesson: TrainingLesson,
    onProgress: (Float) -> Unit,
    onEnded: () -> Unit,
    onFailed: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // HTML zależy wyłącznie od linku — przebudowa przy każdej rekompozycji
    // przeładowywałaby odtwarzacz i gubiła to, co ktoś już obejrzał.
    val html = remember(lesson.videoUrl, lesson.videoProvider) { playerHtml(lesson) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // Bez tego pełny ekran YouTube nie ma gdzie się otworzyć.
                webChromeClient = WebChromeClient()
                setBackgroundColor(android.graphics.Color.BLACK)
                addJavascriptInterface(VideoBridge(onProgress, onEnded, onFailed), "Bridge")
                // Baza musi być prawdziwym adresem https — z `about:blank` IFrame
                // API zgłasza błąd pochodzenia i nie startuje, a z `youtube.com`
                // YouTube odrzuca osadzenie (kod 152). Stąd domena naszego API.
                loadDataWithBaseURL(
                    playerBaseUrl,
                    html,
                    "text/html",
                    "utf-8",
                    null,
                )
            }
        },
        onRelease = { web ->
            web.loadUrl("about:blank")
            web.destroy()
        },
    )
}
