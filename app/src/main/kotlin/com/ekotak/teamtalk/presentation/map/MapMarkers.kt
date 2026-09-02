package com.ekotak.teamtalk.presentation.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.TypedValue

/**
 * Ikony punktów mapy rysowane tak jak w panelu (`divIcon` z Leafletu):
 * kolorowe kółko z białą obwódką i czarną literą. Litera niesie tę samą
 * informację co kolor — punkty da się rozróżnić także przy daltonizmie.
 *
 * Rysujemy je sami zamiast wozić komplet plików PNG: kolorów jest tyle, ile
 * etapów lejka i statusów serwisu, a każdy z inną literą.
 */
object MapMarkers {

    /** Pin punktu: kółko `color` z literą `letter`. `selected` = powiększony. */
    fun pin(context: Context, colorArgb: Long, letter: String, selected: Boolean = false): Drawable {
        val size = dp(context, if (selected) 34f else 26f)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val radius = center - dp(context, 2f)

        // Cień pod obwódką — bez niego jasny pin ginie na jasnym podkładzie OSM.
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(115, 0, 0, 0)
        }
        canvas.drawCircle(center, center, radius, shadow)

        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(230, 255, 255, 255)
        }
        canvas.drawCircle(center, center, radius - dp(context, 0.5f), ring)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorArgb.toInt() }
        canvas.drawCircle(center, center, radius - dp(context, 2f), fill)

        drawCentered(canvas, letter.take(1), center, size * 0.44f, Color.parseColor("#0B0B0B"), bold = true)
        return BitmapDrawable(context.resources, bitmap)
    }

    /**
     * Klaster: półprzezroczyste kółko z liczbą punktów — kolorystyka jak
     * w domyślnym `leaflet.markercluster`, żeby mapa czytała się tak samo
     * jak w panelu. Większe skupisko = większe kółko.
     */
    fun cluster(context: Context, count: Int): Drawable {
        val diameter = dp(
            context,
            when {
                count < 10 -> 34f
                count < 100 -> 40f
                else -> 48f
            },
        )
        val bitmap = Bitmap.createBitmap(diameter, diameter, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = diameter / 2f

        val outer = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(153, 110, 204, 57) }
        canvas.drawCircle(center, center, center, outer)

        val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(217, 110, 204, 57) }
        canvas.drawCircle(center, center, center - dp(context, 4f), inner)

        drawCentered(canvas, count.toString(), center, diameter * 0.34f, Color.parseColor("#20340F"), bold = true)
        return BitmapDrawable(context.resources, bitmap)
    }

    private fun drawCentered(
        canvas: Canvas,
        text: String,
        center: Float,
        textSize: Float,
        color: Int,
        bold: Boolean,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.textSize = textSize
            typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
        }
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        canvas.drawText(text, center, center + bounds.height() / 2f, paint)
    }

    private fun dp(context: Context, value: Float): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            context.resources.displayMetrics,
        ).toInt()
}
