package com.glyphweather.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Current weather response from Open-Meteo. */
data class CurrentWeather(
    val condition: WeatherCondition,
    val temperatureC: Double,
    val weatherCode: Int,
    val isDay: Boolean,
    val uvIndex: Double? = null,
    val precipitationProbability: Int? = null,
    val apparentTemperatureC: Double? = null,
    val airQualityIndex: Int? = null
)

/**
 * Weather API client. Talks to Open-Meteo by default (free, no API key required:
 * https://open-meteo.com/en/docs), but the URL is a template so a user-supplied API/service
 * can be substituted from the "Weather Source" menu. Whatever service is used, its response
 * must follow the same shape Open-Meteo returns: a `current` object with `weather_code`,
 * `is_day`, and `temperature_2m` fields.
 */
object OpenMeteoClient {

    const val DEFAULT_URL_TEMPLATE =
        "https://api.open-meteo.com/v1/forecast" +
            "?latitude={lat}&longitude={lon}" +
            "&current=weather_code,is_day,temperature_2m,uv_index,precipitation_probability,apparent_temperature" +
            "&timezone=auto"

    private const val AIR_QUALITY_URL_TEMPLATE =
        "https://air-quality-api.open-meteo.com/v1/air-quality" +
            "?latitude={lat}&longitude={lon}" +
            "&current=european_aqi" +
            "&timezone=auto"

    /**
     * [urlTemplate] may contain `{lat}`, `{lon}`, and `{key}` placeholders — the last is
     * substituted with [apiKey] so custom services that require one can embed it as a query
     * parameter (e.g. `&appid={key}`).
     *
     * [fetchAirQuality] controls whether a separate Open-Meteo Air Quality request is made
     * to obtain the AQI value.
     */
    suspend fun fetch(
        urlTemplate: String,
        lat: Double,
        lon: Double,
        apiKey: String = "",
        fetchAirQuality: Boolean = false
    ): CurrentWeather = withContext(Dispatchers.IO) {
        val weather = fetchWeather(urlTemplate, lat, lon, apiKey)
        if (!fetchAirQuality) return@withContext weather
        val aqi = runCatching { fetchAirQuality(lat, lon) }.getOrNull()
        weather.copy(airQualityIndex = aqi)
    }

    private suspend fun fetchWeather(
        urlTemplate: String,
        lat: Double,
        lon: Double,
        apiKey: String
    ): CurrentWeather = request(buildUrl(urlTemplate, lat, lon, apiKey)) { current ->
        val code = current.optInt("weather_code", -1)
        val isDay = current.optInt("is_day", 1) == 1
        val temp = current.optDouble("temperature_2m", Double.NaN)
        if (code < 0 || temp.isNaN()) {
            throw JSONException("Invalid weather API response: code=$code, temp=$temp")
        }
        CurrentWeather(
            condition = WeatherCondition.fromWmo(code, isDay),
            temperatureC = temp,
            weatherCode = code,
            isDay = isDay,
            uvIndex = current.optDouble("uv_index", Double.NaN).takeIf { !it.isNaN() },
            precipitationProbability = current.optInt("precipitation_probability", -1).takeIf { it >= 0 },
            apparentTemperatureC = current.optDouble("apparent_temperature", Double.NaN).takeIf { !it.isNaN() }
        )
    }

    private suspend fun fetchAirQuality(lat: Double, lon: Double): Int =
        request(buildUrl(AIR_QUALITY_URL_TEMPLATE, lat, lon, "")) { current ->
            current.optInt("european_aqi", -1).also {
                if (it < 0) throw JSONException("Missing AQI value")
            }
        }

    private suspend inline fun <T> request(url: String, parser: (JSONObject) -> T): T {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (conn.responseCode !in 200..299) {
                throw RuntimeException("Weather API HTTP ${conn.responseCode}")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            parser(JSONObject(body).getJSONObject("current"))
        } finally {
            conn.disconnect()
        }
    }

    private fun buildUrl(template: String, lat: Double, lon: Double, apiKey: String): String =
        template
            .replace("{lat}", lat.toString())
            .replace("{lon}", lon.toString())
            .replace("{key}", apiKey)
}
