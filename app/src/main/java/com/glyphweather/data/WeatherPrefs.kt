package com.glyphweather.data

import android.content.Context
import android.content.SharedPreferences
import com.glyphweather.weather.WeatherCondition

/** Simple storage for the latest weather state and the operation flag. */
class WeatherPrefs(context: Context) {

    private val sp = context.getSharedPreferences("glyph_weather", Context.MODE_PRIVATE)

    private inline fun edit(block: SharedPreferences.Editor.() -> Unit) {
        sp.edit().apply(block).apply()
    }

    var condition: WeatherCondition
        get() = runCatching {
            WeatherCondition.valueOf(sp.getString(KEY_CONDITION, null) ?: "")
        }.getOrDefault(WeatherCondition.SUNNY)
        set(value) = edit { putString(KEY_CONDITION, value.name) }

    var temperatureC: Double
        get() = java.lang.Double.longBitsToDouble(
            sp.getLong(KEY_TEMP, java.lang.Double.doubleToRawLongBits(Double.NaN))
        )
        set(value) = edit { putLong(KEY_TEMP, java.lang.Double.doubleToRawLongBits(value)) }

    var lastUpdated: Long
        get() = sp.getLong(KEY_UPDATED, 0L)
        set(value) = edit { putLong(KEY_UPDATED, value) }

    /** Whether the user has enabled weather display on the matrix. */
    var enabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, false)
        set(value) = edit { putBoolean(KEY_ENABLED, value) }

    /**
     * Test mode (set from the hidden debug menu): while true, scheduled/manual
     * weather refreshes must not overwrite [condition] with real data.
     */
    var debugOverride: Boolean
        get() = sp.getBoolean(KEY_DEBUG_OVERRIDE, false)
        set(value) = edit { putBoolean(KEY_DEBUG_OVERRIDE, value) }

    /** Atomic write of the entire weather state in one apply(). */
    fun setWeather(condition: WeatherCondition, temperatureC: Double, updated: Long) {
        edit {
            putString(KEY_CONDITION, condition.name)
            putLong(KEY_TEMP, java.lang.Double.doubleToRawLongBits(temperatureC))
            putLong(KEY_UPDATED, updated)
        }
    }

    companion object {
        private const val KEY_CONDITION = "condition"
        private const val KEY_TEMP = "temperature"
        private const val KEY_UPDATED = "updated"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_DEBUG_OVERRIDE = "debug_override"
    }
}
