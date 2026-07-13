package com.glyphweather.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.glyphweather.weather.WeatherRepository

/**
 * Periodically updates the weather.
 */
class WeatherWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "WeatherWorker: doWork started")
        val repo = WeatherRepository(applicationContext)
        return try {
            when (val result = repo.refresh()) {
                is WeatherRepository.Result.Success -> {
                    Log.d(TAG, "WeatherWorker: Success")
                    Result.success()
                }
                is WeatherRepository.Result.NoLocation -> {
                    Log.w(TAG, "WeatherWorker: No location available")
                    Result.success() 
                }
                is WeatherRepository.Result.Error -> {
                    Log.e(TAG, "WeatherWorker: Error", result.throwable)
                    if (result.isRetryable) Result.retry() else Result.failure()
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "WeatherWorker: Fatal crash", t)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "WeatherWorker"
    }
}
