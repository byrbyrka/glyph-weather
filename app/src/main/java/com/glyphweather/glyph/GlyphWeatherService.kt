package com.glyphweather.glyph

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.glyphweather.R
import com.glyphweather.data.WeatherPrefs
import com.glyphweather.ui.MainActivity
import com.glyphweather.weather.WeatherCondition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service: maintains a connection with the Glyph Matrix and plays the
 * animation of the current weather condition in a loop.
 */
class GlyphWeatherService : android.app.Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var controller: GlyphMatrixController
    private lateinit var prefs: WeatherPrefs

    private var playJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        prefs = WeatherPrefs(this)
        controller = GlyphMatrixController(this)
        controller.connect()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> applyCurrentCondition()
        }
        return START_STICKY
    }

    private fun applyCurrentCondition() {
        val condition = prefs.condition
        // Only start the loop if it's not already running or condition changed
        val previous = playJob
        playJob = scope.launch {
            previous?.cancelAndJoin()
            playLoop()
        }
    }

    private suspend fun playLoop() {
        var lastCondition: WeatherCondition? = null
        var animation: GlyphAnimation? = null

        while (scope.isActive) {
            val currentCondition = prefs.condition
            
            // Reload animation ONLY if condition changed or not loaded yet
            if (currentCondition != lastCondition || animation == null) {
                lastCondition = currentCondition
                animation = try {
                    GlyphAnimation.load(this@GlyphWeatherService, currentCondition)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Log.e(TAG, "Cannot load animation for $currentCondition", t)
                    null
                }
                // Update notification when condition changes
                startForegroundCompat(NOTIF_ID, buildNotification(currentCondition))
            }

            val frames = animation?.frames ?: emptyList()
            if (frames.isEmpty()) {
                delay(2000)
                continue
            }

            for (frame in frames) {
                if (!scope.isActive) break
                // If condition changed mid-animation, break to reload
                if (prefs.condition != lastCondition) break
                
                controller.show(frame.grid)
                delay(frame.durationMs.coerceAtLeast(30L))
            }
        }
    }

    override fun onDestroy() {
        playJob?.cancel()
        controller.disconnect()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundCompat(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(id, notification)
        }
    }

    private fun buildNotification(condition: WeatherCondition): Notification {
        val temp = prefs.temperatureC
        val tempText = if (temp.isNaN()) "" else " · ${Math.round(temp)}°C"
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_glyph)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(condition.titleRu + tempText)
            .setOngoing(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "GlyphWeatherService"
        private const val CHANNEL_ID = "glyph_weather"
        private const val NOTIF_ID = 42
        const val ACTION_STOP = "com.glyphweather.action.STOP"

        fun start(context: Context) {
            val i = Intent(context, GlyphWeatherService::class.java)
            try {
                ContextCompat.startForegroundService(context, i)
            } catch (t: Throwable) {
                Log.w(TAG, "startForegroundService failed", t)
            }
        }

        fun stop(context: Context) {
            val i = Intent(context, GlyphWeatherService::class.java).setAction(ACTION_STOP)
            context.startService(i)
        }
    }
}
