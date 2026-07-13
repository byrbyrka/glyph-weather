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
 * Open-Meteo client — free and no API key required.
 * https://open-meteo.com/en/docs
 */
object OpenMeteoClient {

    suspend fun fetch(lat: Double, lon: Double): CurrentWeather = withContext(Dispatchers.IO) {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&current=weather_code,is_day,temperature_2m" +
                "&timezone=auto"
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conn.responseCode !in 200..299) {
                throw RuntimeException("Open-Meteo HTTP ${conn.responseCode}")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val current = JSONObject(body).getJSONObject("current")
            val code = current.optInt("weather_code", -1)
            val isDay = current.optInt("is_day", 1) == 1
            val temp = current.optDouble("temperature_2m", Double.NaN)
            if (code < 0 || temp.isNaN()) {
                throw JSONException("Invalid Open-Meteo response: code=$code, temp=$temp")
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
}
