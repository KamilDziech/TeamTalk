package com.ekotak.teamtalk.presentation.map

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RadialGradient
import android.graphics.Shader
import com.ekotak.teamtalk.domain.model.MapPalette
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay
import kotlin.math.min

/**
 * Mapa cieplna punktów. osmdroid nie ma warstwy cieplnej, więc rysujemy ją
 * sami — z gradientem `leaflet.heat` z panelu (niebieski → zielony → żółty →
 * czerwony), żeby to samo skupisko wyglądało po obu stronach tak samo.
 *
 * Zagęszczenie liczymy siatką w pikselach ekranu (komórka = promień plamy),
 * a nie sumowaniem przezroczystości piksel po pikselu: przy setkach punktów
 * daje ten sam obraz, a rysuje się bez zauważalnej zwłoki przy przesuwaniu mapy.
 */
class HeatOverlay(
    private var points: List<GeoPoint> = emptyList(),
    /** Promień plamy w pikselach ekranu. */
    private val radiusPx: Float = 90f,
) : Overlay() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val screen = Point()

    fun setPoints(newPoints: List<GeoPoint>) {
        points = newPoints
    }

    override fun draw(canvas: Canvas, mapView: MapView?, shadow: Boolean) {
        if (shadow || points.isEmpty() || mapView == null) return
        val projection: Projection = mapView.projection

        // Punkty ekranowe (z zapasem poza kadrem, żeby plamy nie ucinały się na krawędzi).
        val margin = radiusPx * 2
        val visible = ArrayList<Pair<Float, Float>>(points.size)
        for (p in points) {
            projection.toPixels(p, screen)
            val x = screen.x.toFloat()
            val y = screen.y.toFloat()
            if (x < -margin || y < -margin || x > canvas.width + margin || y > canvas.height + margin) {
                continue
            }
            visible += x to y
        }
        if (visible.isEmpty()) return

        // Zagęszczenie: ile punktów wpada do tej samej komórki siatki i jej sąsiadów.
        val cell = radiusPx
        val counts = HashMap<Long, Int>()
        for ((x, y) in visible) {
            val key = key((x / cell).toInt(), (y / cell).toInt())
            counts[key] = (counts[key] ?: 0) + 1
        }
        val maxCount = counts.values.max()

        for ((x, y) in visible) {
            val cx = (x / cell).toInt()
            val cy = (y / cell).toInt()
            var neighbours = 0
            for (dx in -1..1) {
                for (dy in -1..1) neighbours += counts[key(cx + dx, cy + dy)] ?: 0
            }
            val intensity = if (maxCount <= 1) 0.45f else min(1f, neighbours / (maxCount * 2f))
            val color = colorFor(intensity)
            paint.shader = RadialGradient(
                x,
                y,
                radiusPx,
                intArrayOf(withAlpha(color, 0.55f), withAlpha(color, 0.28f), withAlpha(color, 0f)),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(x, y, radiusPx, paint)
        }
        paint.shader = null
    }

    /** Kolor z gradientu panelu dla zagęszczenia 0..1 (bez interpolacji odcieni). */
    private fun colorFor(intensity: Float): Int {
        val stops = MapPalette.HEAT_STOPS
        for ((threshold, color) in stops) {
            if (intensity <= threshold) return color.toInt()
        }
        return stops.last().second.toInt()
    }

    private fun withAlpha(color: Int, factor: Float): Int {
        val alpha = (255 * factor).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (color and 0x00FFFFFF)
    }

    private fun key(x: Int, y: Int): Long = (x.toLong() shl 32) xor (y.toLong() and 0xFFFFFFFFL)
}
