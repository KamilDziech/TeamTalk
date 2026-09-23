package com.ekotak.teamtalk.presentation.installations

import android.graphics.Bitmap
import android.graphics.Paint
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream

/**
 * PODPIS KLIENTA PALCEM — na budowie, a nie wieczorem w biurze.
 *
 * Panel zbiera podpis myszą (`SignaturePad.tsx`) i zapisuje go jako data URL
 * PNG; telefon robi dokładnie to samo, żeby PDF protokołu nie musiał znać dwóch
 * formatów. Rysujemy po prostu kreski między punktami dotyku — krzywe Béziera
 * wyglądałyby ładniej, ale podpis ma być DOWODEM, nie grafiką, a każda
 * biblioteka więcej to jedna rzecz, której projekt nie zbuduje offline.
 */
@Composable
fun SignaturePad(
    value: String?,
    onChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Kreski trzymamy w pikselach płótna; przy zmianie rozmiaru ekranu podpis
    // zostaje taki, jak go złożono — to migawka, nie rysunek do edycji.
    var strokes by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(Pair(0, 0)) }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset -> current = listOf(offset) },
                        onDrag = { change, _ ->
                            current = current + change.position
                            change.consume()
                        },
                        onDragEnd = {
                            if (current.size > 1) {
                                strokes = strokes + listOf(current)
                                onChange(
                                    signatureDataUrl(strokes, canvasSize.first, canvasSize.second),
                                )
                            }
                            current = emptyList()
                        },
                    )
                },
        ) {
            Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                canvasSize = Pair(size.width.toInt(), size.height.toInt())
                (strokes + listOf(current)).forEach { stroke ->
                    for (i in 1 until stroke.size) {
                        drawLine(
                            color = Color.Black,
                            start = stroke[i - 1],
                            end = stroke[i],
                            strokeWidth = 4f,
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
            if (strokes.isEmpty() && current.isEmpty()) {
                Text(
                    text = if (value.isNullOrBlank()) {
                        "Podpis klienta — palcem po białym polu"
                    } else {
                        "Podpis złożony (zapisany przy protokole)"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8A8F98),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = {
                    strokes = emptyList()
                    current = emptyList()
                    onChange(null)
                },
            ) { Text("Wyczyść podpis") }
        }
    }
}

/**
 * Kreski → PNG w data URL, w tym samym formacie, co panel
 * (`data:image/png;base64,…`). Tło białe, nie przezroczyste: PDF wkleja obraz
 * na białą kartkę, a przezroczystość na niektórych czytnikach wychodzi czarnym
 * prostokątem.
 */
private fun signatureDataUrl(strokes: List<List<Offset>>, width: Int, height: Int): String? {
    if (strokes.isEmpty() || width <= 0 || height <= 0) return null
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        strokeWidth = 4f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    strokes.forEach { stroke ->
        val path = android.graphics.Path()
        stroke.forEachIndexed { index, point ->
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        canvas.drawPath(path, paint)
    }
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    bitmap.recycle()
    return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}
