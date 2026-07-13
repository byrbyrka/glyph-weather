package com.glyphweather.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.glyphweather.glyph.GlyphMatrix
import kotlin.math.min

/**
 * On-screen Glyph Matrix preview: draws a circle of 137 LEDs (13×13 grid),
 * mirroring what is sent to the phone's back panel.
 */
class GlyphPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var grid: IntArray = IntArray(GlyphMatrix.CELLS)

    private val ledPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val offPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(28, 28, 30)
    }

    /** Update the frame (dense 169 array). */
    fun setGrid(newGrid: IntArray) {
        grid = newGrid
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val g = GlyphMatrix.GRID
        val size = min(width, height).toFloat()
        val cell = size / g
        val radius = cell * 0.42f
        val offsetX = (width - size) / 2f
        val offsetY = (height - size) / 2f

        for (row in 0 until g) {
            for (col in 0 until g) {
                if (!GlyphMatrix.isLed(row, col)) continue
                val cx = offsetX + col * cell + cell / 2f
                val cy = offsetY + row * cell + cell / 2f
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
    }
}
