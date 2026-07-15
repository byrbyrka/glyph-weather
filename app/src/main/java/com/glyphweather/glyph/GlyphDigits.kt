package com.glyphweather.glyph

/**
 * Renders short numeric strings (temperature) onto the 13x13 Glyph Matrix grid,
 * using a 3x5 pixel font placed in the matrix's five full-width rows (rows 4-8,
 * see [GlyphMatrix.ROW_LIT]).
 */
object GlyphDigits {

    private const val CHAR_WIDTH = 3
    private const val CHAR_HEIGHT = 5
    private const val CHAR_GAP = 1
    private const val FIRST_ROW = 4

    private val FONT: Map<Char, List<String>> = mapOf(
        '0' to listOf("111", "101", "101", "101", "111"),
        '1' to listOf("010", "110", "010", "010", "111"),
        '2' to listOf("111", "001", "111", "100", "111"),
        '3' to listOf("111", "001", "011", "001", "111"),
        '4' to listOf("101", "101", "111", "001", "001"),
        '5' to listOf("111", "100", "111", "001", "111"),
        '6' to listOf("111", "100", "111", "101", "111"),
        '7' to listOf("111", "001", "001", "001", "001"),
        '8' to listOf("111", "101", "111", "101", "111"),
        '9' to listOf("111", "101", "111", "001", "111"),
        '-' to listOf("000", "000", "111", "000", "000")
    )

    /**
     * Builds a dense 169-cell grid showing the rounded temperature (e.g. "-15", "23"),
     * centered horizontally in the matrix. Values that don't fit (more than 3 characters)
     * are clamped to the nearest representable extreme.
     */
    fun renderTemperature(temperatureC: Double): IntArray = renderNumber(Math.round(temperatureC).toInt())

    /**
     * Renders a generic integer value (temperature, UV, AQI, rain probability, etc.).
     * Negative values are supported. Values above 999 or below -99 are clamped.
     */
    fun renderNumber(value: Int): IntArray {
        val grid = IntArray(GlyphMatrix.CELLS)
        var rounded = value
        if (rounded > 999) rounded = 999
        if (rounded < -99) rounded = -99
        var text = rounded.toString()
        if (text.length > 3) text = text.take(3)

        val width = text.length * CHAR_WIDTH + (text.length - 1) * CHAR_GAP
        var col = (GlyphMatrix.GRID - width) / 2

        for (char in text) {
            val pattern = FONT[char] ?: FONT['-']!!
            for (r in 0 until CHAR_HEIGHT) {
                val rowBits = pattern[r]
                for (c in 0 until CHAR_WIDTH) {
                    if (rowBits[c] == '1') {
                        grid[(FIRST_ROW + r) * GlyphMatrix.GRID + col + c] = 255
                    }
                }
            }
            col += CHAR_WIDTH + CHAR_GAP
        }
        return grid
    }
}
