package com.glyphweather.data

import android.content.Context
import android.content.SharedPreferences
import com.glyphweather.weather.IconPack
import com.glyphweather.weather.OpenMeteoClient
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

    /**
     * URL template for the weather API (from "Weather Source" menu). Supports `{lat}`,
     * `{lon}`, `{key}` placeholders. Defaults to Open-Meteo.
     */
    var weatherUrlTemplate: String
        get() = sp.getString(KEY_WEATHER_URL, null) ?: OpenMeteoClient.DEFAULT_URL_TEMPLATE
        set(value) = edit { putString(KEY_WEATHER_URL, value) }

    /** Optional API key substituted into [weatherUrlTemplate]'s `{key}` placeholder. */
    var weatherApiKey: String
        get() = sp.getString(KEY_WEATHER_API_KEY, null) ?: ""
        set(value) = edit { putString(KEY_WEATHER_API_KEY, value) }

    /** Which Glyph animation set to use: the original pack or the newer, smoother one. */
    var iconPack: IconPack
        get() = runCatching {
            IconPack.valueOf(sp.getString(KEY_ICON_PACK, null) ?: "")
        }.getOrDefault(IconPack.NEW)
        set(value) = edit { putString(KEY_ICON_PACK, value.name) }

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
        private const val KEY_WEATHER_URL = "weather_url_template"
        private const val KEY_WEATHER_API_KEY = "weather_api_key"
        private const val KEY_ICON_PACK = "icon_pack"
    }
}
