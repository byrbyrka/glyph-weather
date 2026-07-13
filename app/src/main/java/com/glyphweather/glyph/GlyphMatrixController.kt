package com.glyphweather.glyph

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager

/**
 * Controller for Nothing Glyph Matrix.
 * Supports Phone (2a) and Phone (2a) Plus.
 */
class GlyphMatrixController(private val context: Context) {

    private var manager: GlyphMatrixManager? = null
    @Volatile private var connected = false
    @Volatile private var pendingFrame: IntArray? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private val callback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(name: ComponentName?) {
            Log.d(TAG, "Glyph service connected. Registering device...")
            
            // Try known matrix device IDs
            val devices = listOf(
                Glyph.DEVICE_23111,  // Phone (2a)
                Glyph.DEVICE_25111p  // Phone (2a) Plus
            )
            
            var success = false
            for (device in devices) {
                try {
                    manager?.register(device)
                    Log.i(TAG, "Registered successfully for device: $device")
                    success = true
                    break
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

    private fun push(grid: IntArray) {
        mainHandler.post {
            try {
                manager?.setAppMatrixFrame(grid)
            } catch (t: Throwable) {
                Log.e(TAG, "setAppMatrixFrame error", t)
            }
        }
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
