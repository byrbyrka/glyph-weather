package com.glyphweather.glyph

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.glyphweather.R
import com.glyphweather.data.WeatherPrefs
import com.glyphweather.sensor.ShakeDetector
import com.glyphweather.ui.MainActivity
import com.glyphweather.weather.IconPack
import com.glyphweather.weather.ShakeMetric
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

    private lateinit var sensorManager: SensorManager
    private var shakeDetector: ShakeDetector? = null

    @Volatile private var temperatureOverrideUntil: Long = 0L
    @Volatile private var temperatureGrid: IntArray? = null

    override fun onCreate() {
        super.onCreate()
        prefs = WeatherPrefs(this)
        controller = GlyphMatrixController(this)
        controller.setBrightness(prefs.glyphBrightness)
        controller.connect()
        createChannel()
        registerShakeDetector()
    }

    private fun registerShakeDetector() {
        sensorManager = getSystemService(SensorManager::class.java)
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        val detector = ShakeDetector { onShakeDetected() }
        shakeDetector = detector
        sensorManager.registerListener(detector, accelerometer, SensorManager.SENSOR_DELAY_GAME)
    }

    private fun onShakeDetected() {
        if (!prefs.shakeEnabled) return
        val grid = renderShakeMetric() ?: return
        temperatureGrid = grid
        temperatureOverrideUntil = System.currentTimeMillis() + TEMPERATURE_DISPLAY_MS
    }

    private fun renderShakeMetric(): IntArray? {
        return when (prefs.shakeMetric) {
            ShakeMetric.TEMPERATURE -> {
                val tempC = prefs.temperatureC
                if (tempC.isNaN()) return null
                GlyphDigits.renderTemperature(prefs.temperatureUnit.fromCelsius(tempC))
            }
            ShakeMetric.UV_INDEX -> {
                val uv = prefs.uvIndex
                if (uv.isNaN()) return null
                GlyphDigits.renderNumber(Math.round(uv).toInt())
            }
            ShakeMetric.PRECIPITATION_PROBABILITY -> {
                val prob = prefs.precipitationProbability
                if (prob < 0) return null
                GlyphDigits.renderNumber(prob.coerceIn(0, 100))
            }
            ShakeMetric.APPARENT_TEMPERATURE -> {
                val feels = prefs.apparentTemperatureC
                if (feels.isNaN()) return null
                GlyphDigits.renderTemperature(prefs.temperatureUnit.fromCelsius(feels))
            }
            ShakeMetric.AQI -> {
                val aqi = prefs.airQualityIndex
                if (aqi < 0) return null
                GlyphDigits.renderNumber(aqi)
            }
        }
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
        var lastPack: IconPack? = null
        var animation: GlyphAnimation? = null
        var wasBlocked = false

        while (scope.isActive) {
            controller.setBrightness(prefs.glyphBrightness)

            val blocked = prefs.isSchedulerBlocking()
            if (blocked) {
                if (!wasBlocked) {
                    controller.show(IntArray(GlyphMatrix.CELLS))
                    startForegroundCompat(NOTIF_ID, buildNotification(prefs.condition, schedulerOff = true))
                }
                wasBlocked = true
                delay(SCHEDULER_POLL_MS)
                continue
            }
            wasBlocked = false

            val overrideGrid = temperatureGrid
            if (overrideGrid != null && System.currentTimeMillis() < temperatureOverrideUntil) {
                controller.show(overrideGrid)
                delay(TEMPERATURE_POLL_MS)
                continue
            }

            val currentCondition = prefs.condition
            val currentPack = prefs.iconPack

            // Reload animation ONLY if condition/pack changed or not loaded yet
            if (currentCondition != lastCondition || currentPack != lastPack || animation == null) {
                lastCondition = currentCondition
                lastPack = currentPack
                animation = try {
                    GlyphAnimation.load(this@GlyphWeatherService, currentCondition, currentPack)
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
                // If condition or icon pack changed mid-animation, break to reload
                if (prefs.condition != lastCondition || prefs.iconPack != lastPack) break
                // Shake detected mid-animation: break out to show the temperature immediately
                if (temperatureGrid != null && System.currentTimeMillis() < temperatureOverrideUntil) break
                // Scheduler may have turned on while we were animating
                if (prefs.isSchedulerBlocking()) break

                controller.show(frame.grid)
                delay(frame.durationMs.coerceAtLeast(30L))
            }
        }
    }

    override fun onDestroy() {
        shakeDetector?.let { sensorManager.unregisterListener(it) }
        playJob?.cancel()
        controller.disconnect()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundCompat(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(id, notification)
        }
    }

    private fun buildNotification(condition: WeatherCondition, schedulerOff: Boolean = false): Notification {
        val tempC = prefs.temperatureC
        val unit = prefs.temperatureUnit
        val tempText = if (tempC.isNaN()) "" else " · ${Math.round(unit.fromCelsius(tempC))}${unit.symbol}"
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val contentText = if (schedulerOff) {
            getString(R.string.status_scheduler_off)
        } else {
            condition.titleEn + tempText
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_glyph)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
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
        private const val TEMPERATURE_DISPLAY_MS = 3000L
        private const val TEMPERATURE_POLL_MS = 200L
        private const val SCHEDULER_POLL_MS = 60_000L
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
