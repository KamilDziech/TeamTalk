package com.ekotak.teamtalk.presentation.crm

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.ekotak.teamtalk.domain.model.DealDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Podgląd pliku na pełnym ekranie — mobilny odpowiednik `Lightbox` panelu.
 *
 * Zdjęcie i strona PDF-a idą tą samą drogą: renderujemy je u siebie i pokazujemy
 * jako bitmapę, więc PDF ogląda się bez wychodzenia z aplikacji i bez zasięgu.
 * „Otwórz w systemie" zostaje dla plików, których nie umiemy narysować, i dla
 * chętnych na czytnik z zakładkami.
 *
 * Powiększanie jest szczyptą i dwuklikiem — na rzucie kondygnacji trzeba
 * dojechać do pojedynczego pomieszczenia, a stały kadr nic by nie dał.
 */
@Composable
fun DocumentViewer(document: DealDocument, onClose: () -> Unit) {
    val store = rememberDocumentFileStore()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pages by remember(document.id) { mutableIntStateOf(1) }
    var page by remember(document.id) { mutableIntStateOf(1) }
    var bitmap by remember(document.id) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(document.id) { mutableStateOf(false) }

    LaunchedEffect(document.id) {
        pages = store.pageCount(document)
    }
    LaunchedEffect(document.id, page) {
        bitmap = null
        failed = false
        val loaded = store.image(document, page, targetPx = VIEWER_PX)
        bitmap = loaded
        failed = loaded == null
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            when {
                bitmap != null -> ZoomableImage(bitmap!!)

                failed -> Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = if (document.pending) {
                            "Treść tego pliku zniknęła z telefonu, zanim zdążył się wysłać."
                        } else {
                            "Nie udało się pobrać pliku. Spróbuj z zasięgiem."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                }

                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }

            // Pasek na wierzchu obrazu, nie nad nim: przy rzucie liczy się każdy
            // piksel wysokości, a nazwa pliku i tak jest tu tylko podpisem.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Zamknij", tint = Color.White)
                }
                Text(
                    text = document.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        scope.launch {
                            // Kopiowanie i ewentualne pobranie treści to dysk
                            // i sieć — nie na wątku, który rysuje podgląd.
                            val file = withContext(Dispatchers.IO) {
                                store.content(document)
                                store.exportCopy(document)
                            }
                            if (file != null) openWithSystem(context, file, document)
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Otwórz w innej aplikacji",
                        tint = Color.White,
                    )
                }
            }

            if (pages > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { if (page > 1) page-- }, enabled = page > 1) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Poprzednia strona",
                            tint = if (page > 1) Color.White else Color.White.copy(alpha = 0.35f),
                        )
                    }
                    Text(
                        text = "$page / $pages",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                    )
                    IconButton(onClick = { if (page < pages) page++ }, enabled = page < pages) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Następna strona",
                            tint = if (page < pages) {
                                Color.White
                            } else {
                                Color.White.copy(alpha = 0.35f)
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Obraz z powiększaniem szczyptą i dwuklikiem; poza zakresem wraca do kadru. */
@Composable
private fun ZoomableImage(image: ImageBitmap) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Image(
        bitmap = image,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                    if (scale <= 1f) {
                        // Wróciliśmy do kadru — przesunięcie traci sens i tylko
                        // zostawiałoby obraz przyklejony do krawędzi ekranu.
                        offsetX = 0f
                        offsetY = 0f
                    } else {
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        if (scale > 1f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = DOUBLE_TAP_ZOOM
                            // Dwuklik ma powiększyć TO, w co ktoś stuknął, a nie
                            // środek ekranu — stąd przesunięcie o wektor od środka.
                            val center = Offset(size.width / 2f, size.height / 2f)
                            offsetX = (center.x - tap.x) * (DOUBLE_TAP_ZOOM - 1f)
                            offsetY = (center.y - tap.y) * (DOUBLE_TAP_ZOOM - 1f)
                        }
                    },
                )
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offsetX
                translationY = offsetY
            },
    )
}

/**
 * Oddaje plik systemowi. `FLAG_GRANT_READ_URI_PERMISSION` jest obowiązkowe: bez
 * niego aplikacja otwierająca dostanie `content://`, do którego nie ma prawa.
 */
private fun openWithSystem(context: Context, file: File, document: DealDocument) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, document.contentType.ifBlank { "*/*" })
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Bez aplikacji do tego typu pliku nie ma co robić — podgląd w karcie
        // i tak został otwarty, więc człowiek nie zostaje z pustym ekranem.
    }
}

private const val VIEWER_PX = 1600
private const val MAX_ZOOM = 6f
private const val DOUBLE_TAP_ZOOM = 2.5f
