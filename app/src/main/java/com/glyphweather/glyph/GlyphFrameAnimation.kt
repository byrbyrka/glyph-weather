package com.glyphweather.glyph

import android.content.Context
import com.glyphweather.weather.IconPack
import com.glyphweather.weather.WeatherCondition
import org.json.JSONObject

/**
 * Glyph Matrix geometry of the Nothing Phone (4a) Pro.
 *
 * Physically, it is a circle of 137 mini-LEDs inscribed in a 13×13 grid (169 cells);
 * 32 corner cells are "cut out". In assets, each frame (`p`) contains exactly
 * 137 brightness values 0..255 — only real LEDs, line by line, skipping the
 * cut-out corners.
 *
 * Number of "live" LEDs by row (top to bottom), symmetrical and centered
 * in each 13-wide row:
 *   5, 9, 11, 11, 13, 13, 13, 13, 13, 11, 11, 9, 5  = 137
 */
object GlyphMatrix {
    const val GRID = 13
    const val CELLS = GRID * GRID          // 169
    const val LED_COUNT = 137

    val ROW_LIT = intArrayOf(5, 9, 11, 11, 13, 13, 13, 13, 13, 11, 11, 9, 5)

    /** Starting column of the "live" area in each row (centered). */
    private val ROW_START = ROW_LIT.map { (GRID - it) / 2 }.toIntArray()

    /**
     * Expands 137 values into a dense 13×13 = 169 array (row-major),
     * suitable for GlyphMatrixManager.setAppMatrixFrame(int[]).
     * Cut-out corners get 0 (LED absent).
     */
    fun expandToGrid(raw: IntArray): IntArray {
        val out = IntArray(CELLS)
        if (raw.size != LED_COUNT) {
            // Unexpected format — lay it out as is to avoid crashing.
            for (i in raw.indices) if (i < CELLS) out[i] = raw[i]
            return out
        }
        var src = 0
        for (row in 0 until GRID) {
            val start = ROW_START[row]
            val count = ROW_LIT[row]
            for (c in 0 until count) {
                out[row * GRID + start + c] = raw[src++]
            }
        }
        return out
    }

    /** true if the cell (row,col) of the 13×13 grid is a real LED (not a cut-out corner). */
    fun isLed(row: Int, col: Int): Boolean {
        if (row !in 0 until GRID || col !in 0 until GRID) return false
        val start = ROW_START[row]
        return col in start until (start + ROW_LIT[row])
    }
}

/** One animation frame: expanded 169 grid + duration. */
class GlyphFrame(val grid: IntArray, val durationMs: Long)

/** Glyph Matrix animation parsed from an asset. */
class GlyphAnimation(
    val condition: WeatherCondition,
    val frames: List<GlyphFrame>
) {
    companion object {
        /** Loads and parses an asset for the specified weather condition and icon pack. */
        fun load(
            context: Context,
            condition: WeatherCondition,
            pack: IconPack = IconPack.NEW
        ): GlyphAnimation {
            val path = pack.assetDir + condition.asset
            val text = context.assets.open(path)
                .bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            val framesJson = json.getJSONArray("frames")
            val frames = ArrayList<GlyphFrame>(framesJson.length())
            for (i in 0 until framesJson.length()) {
                val f = framesJson.getJSONObject(i)
                val p = f.getJSONArray("p")
                val raw = IntArray(p.length()) { p.getInt(it) }
                val duration = f.optLong("d", 400L)
                frames.add(GlyphFrame(GlyphMatrix.expandToGrid(raw), duration))
            }
            return GlyphAnimation(condition, frames)
        }
    }
}
