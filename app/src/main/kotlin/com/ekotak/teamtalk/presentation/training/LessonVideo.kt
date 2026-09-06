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
 * Strona odtwarzacza. Dla YouTube z mostkiem raportującym postęp, dla reszty
 * gołe `iframe` — bez API dostawcy nie ma czego mierzyć.
 */
private fun playerHtml(lesson: TrainingLesson): String {
    val videoId = youtubeId(lesson.videoUrl)
    if (videoId != null) {
        return """
            <!doctype html><html><head>
            <meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no">
            <style>html,body{margin:0;height:100%;background:#000}#p{width:100%;height:100%}</style>
            </head><body><div id="p"></div>
            <script src="https://www.youtube.com/iframe_api"></script>
            <script>
              var player, played = 0, ticker = null;
              function onYouTubeIframeAPIReady() {
                player = new YT.Player('p', {
                  videoId: '$videoId',
                  playerVars: { playsinline: 1, rel: 0, modestbranding: 1 },
                  events: { onStateChange: onState }
                });
              }
              function onState(e) {
                if (e.data === YT.PlayerState.PLAYING) { start(); } else { stop(); }
                if (e.data === YT.PlayerState.ENDED) { Bridge.ended(); }
              }
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
        <style>html,body{margin:0;height:100%;background:#000}iframe{border:0;width:100%;height:100%}</style>
        </head><body>
        <iframe src="$embed" allow="autoplay; fullscreen; picture-in-picture" allowfullscreen></iframe>
        </body></html>
    """.trimIndent()
}

/** Most JS → Kotlin. Wywołania lecą z wątku WebView, więc wracamy na główny. */
private class VideoBridge(
    private val onProgress: (Float) -> Unit,
    private val onEnded: () -> Unit,
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
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LessonVideo(
    lesson: TrainingLesson,
    onProgress: (Float) -> Unit,
    onEnded: () -> Unit,
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
                addJavascriptInterface(VideoBridge(onProgress, onEnded), "Bridge")
                // Baza `youtube.com` jest wymagana przez IFrame API — z `about:blank`
                // player zgłasza błąd pochodzenia i nie startuje.
                loadDataWithBaseURL(
                    "https://www.youtube.com",
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
