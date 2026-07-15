package com.glyphweather.weather

/**
 * Which metric is displayed on the Glyph Matrix when the user shakes the phone.
 */
enum class ShakeMetric(
    val titleEn: String,
    val symbol: String,
    val requiresAirQuality: Boolean = false
) {
    TEMPERATURE("Temperature", ""),
    UV_INDEX("UV Index", ""),
    PRECIPITATION_PROBABILITY("Rain %", "%"),
    APPARENT_TEMPERATURE("Feels Like", ""),
    AQI("Air Quality", "");

    companion object {
        fun default() = TEMPERATURE
    }
}
