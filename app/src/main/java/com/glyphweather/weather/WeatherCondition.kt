package com.glyphweather.weather

/**
 * Weather conditions, each corresponding to one Glyph Matrix animation
 * (asset in app/src/main/assets/).
 */
enum class WeatherCondition(
    val asset: String,
    val titleEn: String,
    val titleRu: String
) {
    SUNNY("glyph_weather_sunny.json", "Sunny", "Ясно"),
    CLEAR_NIGHT("glyph_weather_clear_night.json", "Clear Night", "Ясная ночь"),
    PARTLY_CLOUDY("glyph_weather_partly_cloudy.json", "Partly Cloudy", "Переменная облачность"),
    CLOUDY("glyph_weather_cloudy.json", "Cloudy", "Облачно"),
    RAIN("glyph_weather_rain.json", "Rain", "Дождь"),
    SNOW("glyph_weather_snow.json", "Snow", "Снег"),
    THUNDERSTORM("glyph_weather_thunderstorm.json", "Thunderstorm", "Гроза");

    companion object {
        /**
         * Mapping of WMO weather code (Open-Meteo `weather_code`) and day/night flag
         * to state. WMO code table:
         * 0 clear; 1 mainly clear; 2 partly cloudy; 3 overcast;
         * 45/48 fog; 51-57 drizzle; 61-67 rain; 71-77 snow;
         * 80-82 rain showers; 85/86 snow showers; 95-99 thunderstorm.
         */
        fun fromWmo(code: Int, isDay: Boolean): WeatherCondition = when (code) {
            0, 1 -> if (isDay) SUNNY else CLEAR_NIGHT
            2 -> PARTLY_CLOUDY
            3, 45, 48 -> CLOUDY
            51, 53, 55, 56, 57,
            61, 63, 65, 66, 67,
            80, 81, 82 -> RAIN
            71, 73, 75, 77, 85, 86 -> SNOW
            95, 96, 99 -> THUNDERSTORM
            else -> if (isDay) SUNNY else CLEAR_NIGHT
        }
    }
}
