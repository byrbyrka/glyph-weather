package com.glyphweather.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.util.concurrent.TimeUnit

/** Scheduling weather updates via WorkManager. */
object WeatherScheduler {

    const val PERIODIC = "weather_periodic"
    const val ONE_SHOT = "weather_now"

    private val networkOnly = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Periodic update every 15 minutes (minimum allowed by WorkManager). */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<WeatherWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkOnly)
            .setBackoffCriteria(BackoffPolicy.LINEAR, Duration.ofMinutes(1))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /** Immediate update (via "Refresh" button or when enabled). */
    fun refreshNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<WeatherWorker>()
            .setConstraints(networkOnly)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_SHOT,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }
}
