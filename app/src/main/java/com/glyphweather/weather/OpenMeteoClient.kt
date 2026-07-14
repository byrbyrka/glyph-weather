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
    val isDay: Boolean
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
            "&current=weather_code,is_day,temperature_2m" +
            "&timezone=auto"

    /**
     * [urlTemplate] may contain `{lat}`, `{lon}`, and `{key}` placeholders — the last is
     * substituted with [apiKey] so custom services that require one can embed it as a query
     * parameter (e.g. `&appid={key}`).
     */
    suspend fun fetch(
        urlTemplate: String,
        lat: Double,
        lon: Double,
        apiKey: String = ""
    ): CurrentWeather = withContext(Dispatchers.IO) {
        val url = URL(buildUrl(urlTemplate, lat, lon, apiKey))
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conn.responseCode !in 200..299) {
                throw RuntimeException("Weather API HTTP ${conn.responseCode}")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val current = JSONObject(body).getJSONObject("current")
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
                isDay = isDay
            )
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
