package com.glyphweather.glyph

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.glyphweather.data.WeatherPrefs
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager

/**
 * Controller for Nothing Glyph Matrix.
 * Registers against all known Glyph Matrix devices, prioritizing Phone (4a) Pro.
 */
class GlyphMatrixController(private val context: Context) {

    private var manager: GlyphMatrixManager? = null
    @Volatile private var connected = false
    @Volatile private var pendingFrame: IntArray? = null
    @Volatile private var brightness: Int = WeatherPrefs.MAX_BRIGHTNESS

    private val prefs = WeatherPrefs(context)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val callback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(name: ComponentName?) {
            Log.d(TAG, "Glyph service connected. Registering device...")
            
            // Try known matrix device IDs, target device (4a Pro) first
            val devices = listOf(
                Glyph.DEVICE_25111p, // Phone (4a) Pro
                Glyph.DEVICE_25111,  // Phone (4a)
                Glyph.DEVICE_24111,  // Phone (3a)
                Glyph.DEVICE_23112,  // Phone (3)
                Glyph.DEVICE_23113,  // Phone (2a) Plus
                Glyph.DEVICE_23111   // Phone (2a)
            )

            var success = false
            for (device in devices) {
                try {
                    if (manager?.register(device) == true) {
                        Log.i(TAG, "Registered successfully for device: $device")
                        success = true
                        break
                    } else {
                        Log.w(TAG, "register() returned false for device: $device")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to register $device: ${e.message}")
                }
            }
            
            if (success) {
                connected = true
                pendingFrame?.let { push(it) }
            } else {
                Log.e(TAG, "Could not register any supported Glyph Matrix device.")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Glyph service disconnected")
            connected = false
        }
    }

    fun connect() {
        if (manager != null) return
        manager = GlyphMatrixManager.getInstance(context.applicationContext)
        try {
            manager?.init(callback)
        } catch (t: Throwable) {
            Log.e(TAG, "Init failed", t)
        }
    }

    fun show(grid: IntArray) {
        pendingFrame = grid
        if (connected) push(grid)
    }

    /** Updates the brightness multiplier applied to every frame. */
    fun setBrightness(value: Int) {
        brightness = value.coerceIn(0, WeatherPrefs.MAX_BRIGHTNESS)
        pendingFrame?.let { show(it) }
    }

    private fun push(grid: IntArray) {
        val scaled = if (brightness == WeatherPrefs.MAX_BRIGHTNESS) grid else scale(grid, brightness)
        mainHandler.post {
            try {
                manager?.setAppMatrixFrame(scaled)
            } catch (t: Throwable) {
                Log.e(TAG, "setAppMatrixFrame error", t)
            }
        }
    }

    private fun scale(grid: IntArray, brightness: Int): IntArray {
        if (brightness == 0) return IntArray(grid.size)
        val factor = brightness / WeatherPrefs.MAX_BRIGHTNESS.toFloat()
        return IntArray(grid.size) { i -> (grid[i] * factor).toInt().coerceIn(0, 255) }
    }

    fun disconnect() {
        try {
            manager?.closeAppMatrix()
            manager?.turnOff()
            manager?.unInit()
        } catch (t: Throwable) {
            Log.e(TAG, "Disconnect error", t)
        }
        connected = false
        pendingFrame = null
        manager = null
    }

    companion object {
        private const val TAG = "GlyphMatrix"
    }
}
