package com.ekotak.teamtalk.presentation.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import com.ekotak.teamtalk.domain.model.MapPalette
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow

/**
 * Mapa cieplna punktów. osmdroid nie ma warstwy cieplnej, więc rysujemy ją
 * sami — tak jak `leaflet.heat` w panelu: najpierw sumujemy zagęszczenie na
 * osobnej warstwie (kanał alfa), a dopiero potem malujemy je gradientem
 * (niebieski → zielony → żółty → czerwony), żeby to samo skupisko wyglądało
 * po obu stronach tak samo.
 *
 * Sumowanie zamiast rysowania osobnych plam ma znaczenie dla czytelności:
 * nachodzące na siebie punkty naprawdę się dodają, więc skupisko wychodzi
 * czerwone i kryjące, a pojedyncze zgłoszenie zostaje bladą kropką — na
 * jasnych kafelkach OSM to różnica między „widać" a „nie widać".
 *
 * Warstwę liczymy w [SCALE] razy mniejszej rozdzielczości i skalujemy przy
 * rysowaniu: rozmycie plam i tak zjada szczegół, a przeliczenie pikseli przy
 * każdym przesunięciu mapy schodzi z milionów do setek tysięcy.
 */
class HeatOverlay(
    private var points: List<GeoPoint> = emptyList(),
    /** Promień plamy w pikselach ekranu. */
    private val radiusPx: Float = 90f,
) : Overlay() {

    private val screen = Point()
    private val spritePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outputPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val src = Rect()
    private val dst = Rect()

    /** Pojedyncza plama — rysowana raz, potem tylko stemplowana w punktach. */
    private val blob: Bitmap by lazy { buildBlob(radiusPx / SCALE) }

    private var buffer: Bitmap? = null
    private var bufferCanvas: Canvas? = null
    private var pixels = IntArray(0)

    fun setPoints(newPoints: List<GeoPoint>) {
        points = newPoints
    }

    override fun draw(canvas: Canvas, mapView: MapView?, shadow: Boolean) {
        if (shadow || points.isEmpty() || mapView == null) return
        val width = canvas.width
        val height = canvas.height
        if (width <= 0 || height <= 0) return
        val projection: Projection = mapView.projection

        val bw = max(1, width / SCALE)
        val bh = max(1, height / SCALE)
        val layer = ensureBuffer(bw, bh) ?: return
        val layerCanvas = bufferCanvas ?: return
        layer.eraseColor(0)

        // Zagęszczenie: plamy z zapasem poza kadrem, żeby nie ucinały się na krawędzi.
        val margin = radiusPx * 1.5f
        val half = blob.width / 2f
        var stamped = false
        for (p in points) {
            projection.toPixels(p, screen)
            val x = screen.x.toFloat()
            val y = screen.y.toFloat()
            if (x < -margin || y < -margin || x > width + margin || y > height + margin) {
                continue
            }
            layerCanvas.drawBitmap(blob, x / SCALE - half, y / SCALE - half, spritePaint)
            stamped = true
        }
        if (!stamped) return

        // Zagęszczenie (alfa) → kolor z gradientu. Cała zamiana siedzi w tablicy
        // [RAMP], więc na piksel wypada jedno odczytanie zamiast liczenia odcieni.
        if (pixels.size != bw * bh) pixels = IntArray(bw * bh)
        layer.getPixels(pixels, 0, bw, 0, 0, bw, bh)
        for (i in pixels.indices) {
            val density = pixels[i] ushr 24
            pixels[i] = if (density == 0) 0 else RAMP[density]
        }
        layer.setPixels(pixels, 0, bw, 0, 0, bw, bh)

        src.set(0, 0, bw, bh)
        dst.set(0, 0, width, height)
        canvas.drawBitmap(layer, src, dst, outputPaint)
    }

    private fun ensureBuffer(width: Int, height: Int): Bitmap? {
        val current = buffer
        if (current != null && !current.isRecycled &&
            current.width == width && current.height == height
        ) {
            return current
        }
        current?.recycle()
        val created = runCatching {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }.getOrNull()
        buffer = created
        bufferCanvas = created?.let { Canvas(it) }
        return created
    }

    /**
     * Plama w skali szarości: biel z miękkim spadkiem do przezroczystości.
     * Liczy się z niej sam kanał alfa — kolor przychodzi dopiero z gradientu.
     */
    private fun buildBlob(radius: Float): Bitmap {
        val size = ceil(radius * 2).toInt().coerceAtLeast(2)
        val center = size / 2f
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                center,
                center,
                radius,
                intArrayOf(white(BLOB_PEAK), white(BLOB_PEAK * 0.45f), white(0f)),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        Canvas(bitmap).drawCircle(center, center, radius, paint)
        return bitmap
    }

    private companion object {
        /** Warstwę zagęszczenia liczymy w tylu razy mniejszej rozdzielczości. */
        const val SCALE = 4

        /** Krycie pojedynczej plamy — kilka nachodzących dobija do skali czerwieni. */
        const val BLOB_PEAK = 0.48f

        /** Poniżej tego zagęszczenia nie malujemy nic — inaczej mapa dostaje mgłę. */
        const val CUTOFF = 0.05f

        /** Krycie najgorętszego miejsca. Poniżej pełnego, żeby ulice były widoczne. */
        const val MAX_ALPHA = 236f

        val RAMP: IntArray = buildRamp()

        fun white(alpha: Float): Int =
            ((255 * alpha).toInt().coerceIn(0, 255) shl 24) or 0x00FFFFFF

        /** Zagęszczenie 0..255 → gotowy kolor ARGB warstwy. */
        fun buildRamp(): IntArray {
            val ramp = IntArray(256)
            for (density in 1 until 256) {
                val t = density / 255f
                if (t < CUTOFF) continue
                val norm = ((t - CUTOFF) / (1f - CUTOFF)).coerceIn(0f, 1f)
                // Pierwiastkowa krzywa krycia: słabe skupiska od razu widać,
                // a mocne i tak dochodzą do maksimum.
                val alpha = (norm.pow(0.6f) * MAX_ALPHA).toInt().coerceIn(0, 255)
                ramp[density] = (alpha shl 24) or (colorAt(norm) and 0x00FFFFFF)
            }
            return ramp
        }

        /** Kolor z gradientu panelu dla zagęszczenia 0..1, z płynnym przejściem. */
        fun colorAt(t: Float): Int {
            val stops = MapPalette.HEAT_STOPS
            val first = stops.first()
            if (t <= first.first) return first.second.toInt()
            for (i in 1 until stops.size) {
                val (hi, hiColor) = stops[i]
                if (t <= hi) {
                    val (lo, loColor) = stops[i - 1]
                    val f = ((t - lo) / (hi - lo)).coerceIn(0f, 1f)
                    return blend(loColor.toInt(), hiColor.toInt(), f)
                }
            }
            return stops.last().second.toInt()
        }

        fun blend(from: Int, to: Int, f: Float): Int {
            val r = channel(from, 16) + (channel(to, 16) - channel(from, 16)) * f
            val g = channel(from, 8) + (channel(to, 8) - channel(from, 8)) * f
            val b = channel(from, 0) + (channel(to, 0) - channel(from, 0)) * f
            return (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
        }

        fun channel(color: Int, shift: Int): Float = ((color shr shift) and 0xFF).toFloat()
    }
}
