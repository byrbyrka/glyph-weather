package com.glyphweather.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Detects a phone shake gesture from accelerometer events: triggers [onShake]
 * once [SHAKE_COUNT] acceleration spikes above [SHAKE_THRESHOLD] happen within
 * a [SHAKE_WINDOW_MS] rolling window (the classic "shake to X" heuristic).
 */
class ShakeDetector(private val onShake: () -> Unit) : SensorEventListener {

    private val recentShakeTimestamps = ArrayDeque<Long>()

    override fun onSensorChanged(event: SensorEvent) {
        val gX = event.values[0] / SensorManager.GRAVITY_EARTH
        val gY = event.values[1] / SensorManager.GRAVITY_EARTH
        val gZ = event.values[2] / SensorManager.GRAVITY_EARTH
        val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)
        if (gForce < SHAKE_THRESHOLD) return

        val now = System.currentTimeMillis()
        recentShakeTimestamps.addLast(now)
        while (recentShakeTimestamps.isNotEmpty() && now - recentShakeTimestamps.first() > SHAKE_WINDOW_MS) {
            recentShakeTimestamps.removeFirst()
        }
        if (recentShakeTimestamps.size >= SHAKE_COUNT) {
            recentShakeTimestamps.clear()
            onShake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        private const val SHAKE_THRESHOLD = 2.2f
        private const val SHAKE_WINDOW_MS = 1000L
        private const val SHAKE_COUNT = 3
    }
}
