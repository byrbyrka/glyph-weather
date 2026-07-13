package com.glyphweather

import android.app.Application
import com.google.android.material.color.DynamicColors

/** Application entry point. WorkManager is initialized automatically (androidx.startup). */
class GlyphWeatherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Применяем динамические цвета Material 3 (Android 12+)
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
