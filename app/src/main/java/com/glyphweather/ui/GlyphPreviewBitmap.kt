package com.glyphweather.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.glyphweather.glyph.GlyphMatrix
import kotlin.math.min

/**
 * Renders a Glyph Matrix frame into a [Bitmap] for use in AppWidget RemoteViews.
 */
object GlyphPreviewBitmap {

    fun render(grid: IntArray, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)

        val g = GlyphMatrix.GRID
        val cell = sizePx.toFloat() / g
        val radius = cell * 0.42f
        val ledPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val offPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(28, 28, 30)
        }

        for (row in 0 until g) {
            for (col in 0 until g) {
                if (!GlyphMatrix.isLed(row, col)) continue
                val cx = col * cell + cell / 2f
                val cy = row * cell + cell / 2f
                val v = grid[row * g + col].coerceIn(0, 255)
                if (v == 0) {
                    canvas.drawCircle(cx, cy, radius, offPaint)
                } else {
                    ledPaint.color = Color.argb(255, 255, 255, 255)
                    ledPaint.alpha = (40 + v * 215 / 255)
                    canvas.drawCircle(cx, cy, radius, ledPaint)
                }
            }
        }
        return bitmap
    }

    /** Picks the first non-empty frame from the animation, or an empty grid. */
    fun firstFrameOrEmpty(frames: List<com.glyphweather.glyph.GlyphFrame>): IntArray {
        return frames.firstOrNull()?.grid ?: IntArray(GlyphMatrix.CELLS)
    }
}
