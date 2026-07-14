package com.glyphweather.weather

import android.content.Context
import android.util.Log
import com.glyphweather.data.WeatherPrefs
import kotlinx.coroutines.CancellationException
import org.json.JSONException
import java.io.IOException

/** Single point for fetching weather: location -> Open-Meteo -> save. */
class WeatherRepository(private val context: Context) {

    private val location = LocationProvider(context)
    private val prefs = WeatherPrefs(context)

    sealed interface Result {
        data class Success(val weather: CurrentWeather) : Result
        data object NoLocation : Result
        data class Error(val throwable: Throwable, val isRetryable: Boolean) : Result
    }

    suspend fun refresh(): Result {
        if (prefs.debugOverride) {
            Log.d(TAG, "Debug override active, skipping real refresh")
            return Result.NoLocation
        }
        Log.d(TAG, "Refreshing weather...")
        val loc = location.current()
        if (loc == null) {
            Log.w(TAG, "Failed to get location")
            return Result.NoLocation
        }
        Log.d(TAG, "Location obtained: ${loc.latitude}, ${loc.longitude}")

        return try {
            val weather = OpenMeteoClient.fetch(loc.latitude, loc.longitude)
            Log.d(TAG, "Weather fetched: ${weather.condition}, ${weather.temperatureC}°C")
            
            prefs.setWeather(
                condition = weather.condition,
                temperatureC = weather.temperatureC,
                updated = System.currentTimeMillis()
            )
            Result.Success(weather)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.e(TAG, "Error fetching weather", t)
            Result.Error(t, isRetryable = classifyRetryable(t))
        }
    }

    private fun classifyRetryable(t: Throwable): Boolean = when {
        t is IOException -> true
        t is SecurityException -> false
        t is JSONException -> false
        t is RuntimeException && (t.message ?: "").startsWith("Open-Meteo HTTP") -> true
        else -> false
    }

    companion object {
        private const val TAG = "WeatherRepository"
    }
}
