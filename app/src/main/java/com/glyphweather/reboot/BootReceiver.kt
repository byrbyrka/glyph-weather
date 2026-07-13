package com.glyphweather.reboot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.glyphweather.data.WeatherPrefs
import com.glyphweather.glyph.GlyphWeatherService
import com.glyphweather.work.WeatherScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!WeatherPrefs(context).enabled) return

        val pendingResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) goAsync() else null
        try {
            WeatherScheduler.schedulePeriodic(context)
            GlyphWeatherService.start(context)
        } finally {
            pendingResult?.finish()
        }
    }
}
