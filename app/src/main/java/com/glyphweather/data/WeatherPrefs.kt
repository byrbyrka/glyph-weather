package com.glyphweather.data

import android.content.Context
import android.content.SharedPreferences
import com.glyphweather.weather.IconPack
import com.glyphweather.weather.OpenMeteoClient
import com.glyphweather.weather.ShakeMetric
import com.glyphweather.weather.TemperatureUnit
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

    /** Temperature unit preference (Celsius, Fahrenheit, Kelvin). */
    var temperatureUnit: TemperatureUnit
        get() = runCatching {
            TemperatureUnit.valueOf(sp.getString(KEY_TEMP_UNIT, null) ?: "")
        }.getOrDefault(TemperatureUnit.CELSIUS)
        set(value) = edit { putString(KEY_TEMP_UNIT, value.name) }

    /**
     * Brightness multiplier for the physical Glyph Matrix, 0..255.
     * Stored as a fraction but exposed as an integer for the UI slider.
     */
    var glyphBrightness: Int
        get() = sp.getInt(KEY_GLYPH_BRIGHTNESS, DEFAULT_BRIGHTNESS)
        set(value) = edit { putInt(KEY_GLYPH_BRIGHTNESS, value.coerceIn(0, MAX_BRIGHTNESS)) }

    /** Whether the automatic on/off scheduler is active. */
    var schedulerEnabled: Boolean
        get() = sp.getBoolean(KEY_SCHEDULER_ENABLED, false)
        set(value) = edit { putBoolean(KEY_SCHEDULER_ENABLED, value) }

    /**
     * Scheduler "off" time in minutes from midnight (0..1439).
     * When the current time is between [schedulerOffMinutes] and [schedulerOnMinutes]
     * the Glyph Matrix display is turned off.
     */
    var schedulerOffMinutes: Int
        get() = sp.getInt(KEY_SCHEDULER_OFF_MINUTES, DEFAULT_OFF_MINUTES)
        set(value) = edit { putInt(KEY_SCHEDULER_OFF_MINUTES, value.coerceIn(0, MINUTES_PER_DAY - 1)) }

    var schedulerOnMinutes: Int
        get() = sp.getInt(KEY_SCHEDULER_ON_MINUTES, DEFAULT_ON_MINUTES)
        set(value) = edit { putInt(KEY_SCHEDULER_ON_MINUTES, value.coerceIn(0, MINUTES_PER_DAY - 1)) }

    /** Returns true if the scheduler is enabled and the current time falls in the off interval. */
    fun isSchedulerBlocking(nowMinutes: Int = currentDayMinutes()): Boolean {
        if (!schedulerEnabled) return false
        val off = schedulerOffMinutes
        val on = schedulerOnMinutes
        return if (off < on) {
            nowMinutes in off until on
        } else {
            nowMinutes >= off || nowMinutes < on
        }
    }

    /** Whether shaking the phone shows an overlay metric on the Glyph Matrix. */
    var shakeEnabled: Boolean
        get() = sp.getBoolean(KEY_SHAKE_ENABLED, true)
        set(value) = edit { putBoolean(KEY_SHAKE_ENABLED, value) }

    /** Which metric is shown when the phone is shaken. */
    var shakeMetric: ShakeMetric
        get() = runCatching {
            ShakeMetric.valueOf(sp.getString(KEY_SHAKE_METRIC, null) ?: "")
        }.getOrDefault(ShakeMetric.default())
        set(value) = edit { putString(KEY_SHAKE_METRIC, value.name) }

    /** Extended weather metrics displayed on shake. */
    var uvIndex: Double
        get() = java.lang.Double.longBitsToDouble(
            sp.getLong(KEY_UV_INDEX, java.lang.Double.doubleToRawLongBits(Double.NaN))
        )
        set(value) = edit { putLong(KEY_UV_INDEX, java.lang.Double.doubleToRawLongBits(value)) }

    var precipitationProbability: Int
        get() = sp.getInt(KEY_PRECIPITATION_PROBABILITY, -1)
        set(value) = edit { putInt(KEY_PRECIPITATION_PROBABILITY, value) }

    var apparentTemperatureC: Double
        get() = java.lang.Double.longBitsToDouble(
            sp.getLong(KEY_APPARENT_TEMP, java.lang.Double.doubleToRawLongBits(Double.NaN))
        )
        set(value) = edit { putLong(KEY_APPARENT_TEMP, java.lang.Double.doubleToRawLongBits(value)) }

    var airQualityIndex: Int
        get() = sp.getInt(KEY_AIR_QUALITY_INDEX, -1)
        set(value) = edit { putInt(KEY_AIR_QUALITY_INDEX, value) }

    /** Atomic write of the entire weather state in one apply(). */
    fun setWeather(
        condition: WeatherCondition,
        temperatureC: Double,
        updated: Long,
        uvIndex: Double? = null,
        precipitationProbability: Int? = null,
        apparentTemperatureC: Double? = null,
        airQualityIndex: Int? = null
    ) {
        edit {
            putString(KEY_CONDITION, condition.name)
            putLong(KEY_TEMP, java.lang.Double.doubleToRawLongBits(temperatureC))
            putLong(KEY_UPDATED, updated)
            uvIndex?.let { putLong(KEY_UV_INDEX, java.lang.Double.doubleToRawLongBits(it)) }
            precipitationProbability?.let { putInt(KEY_PRECIPITATION_PROBABILITY, it) }
            apparentTemperatureC?.let { putLong(KEY_APPARENT_TEMP, java.lang.Double.doubleToRawLongBits(it)) }
            airQualityIndex?.let { putInt(KEY_AIR_QUALITY_INDEX, it) }
        }
    }

    companion object {
        const val MAX_BRIGHTNESS = 255
        const val DEFAULT_BRIGHTNESS = 255
        const val MINUTES_PER_DAY = 1440

        private const val KEY_CONDITION = "condition"
        private const val KEY_TEMP = "temperature"
        private const val KEY_UPDATED = "updated"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_DEBUG_OVERRIDE = "debug_override"
        private const val KEY_WEATHER_URL = "weather_url_template"
        private const val KEY_WEATHER_API_KEY = "weather_api_key"
        private const val KEY_ICON_PACK = "icon_pack"
        private const val KEY_TEMP_UNIT = "temperature_unit"
        private const val KEY_GLYPH_BRIGHTNESS = "glyph_brightness"
        private const val KEY_SCHEDULER_ENABLED = "scheduler_enabled"
        private const val KEY_SCHEDULER_OFF_MINUTES = "scheduler_off_minutes"
        private const val KEY_SCHEDULER_ON_MINUTES = "scheduler_on_minutes"
        private const val KEY_SHAKE_ENABLED = "shake_enabled"
        private const val KEY_SHAKE_METRIC = "shake_metric"
        private const val KEY_UV_INDEX = "uv_index"
        private const val KEY_PRECIPITATION_PROBABILITY = "precipitation_probability"
        private const val KEY_APPARENT_TEMP = "apparent_temperature"
        private const val KEY_AIR_QUALITY_INDEX = "air_quality_index"

        private const val DEFAULT_OFF_MINUTES = 23 * 60 // 23:00
        private const val DEFAULT_ON_MINUTES = 7 * 60    // 07:00

        private fun currentDayMinutes(): Int {
            val calendar = java.util.Calendar.getInstance()
            return calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 +
                    calendar.get(java.util.Calendar.MINUTE)
        }
    }
}
