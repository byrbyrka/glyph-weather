package com.glyphweather.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import com.glyphweather.R
import com.glyphweather.data.WeatherPrefs
import com.glyphweather.glyph.GlyphAnimation
import com.glyphweather.glyph.GlyphDigits
import com.glyphweather.glyph.GlyphMatrix
import com.glyphweather.weather.IconPack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Home screen widget that shows the current weather condition, temperature,
 * last update time and a static preview of the Glyph Matrix animation.
 */
class WeatherWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        private const val PREVIEW_SIZE_PX = 256

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, WeatherWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) updateWidgets(context, manager, ids)
        }

        private fun updateWidgets(
            context: Context,
            manager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            val prefs = WeatherPrefs(context)

            CoroutineScope(Dispatchers.IO).launch {
                val condition = prefs.condition
                val pack = prefs.iconPack
                val frames = try {
                    GlyphAnimation.load(context, condition, pack).frames
                } catch (t: Throwable) {
                    emptyList()
                }
                val frameGrid = GlyphPreviewBitmap.firstFrameOrEmpty(frames)
                val previewBitmap = GlyphPreviewBitmap.render(frameGrid, PREVIEW_SIZE_PX)

                val tempC = prefs.temperatureC
                val unit = prefs.temperatureUnit
                val tempText = if (tempC.isNaN()) {
                    context.getString(R.string.updated_never)
                } else {
                    "${Math.round(unit.fromCelsius(tempC))}${unit.symbol}"
                }
                val updatedText = if (prefs.lastUpdated == 0L) {
                    context.getString(R.string.updated_never)
                } else {
                    android.text.format.DateUtils.getRelativeTimeSpanString(prefs.lastUpdated).toString()
                }

                withContext(Dispatchers.Main) {
                    for (id in appWidgetIds) {
                        updateAppWidget(context, manager, id, condition.titleEn, tempText, updatedText, previewBitmap)
                    }
                }
            }
        }

        private fun updateAppWidget(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int,
            condition: String,
            tempText: String,
            updatedText: String,
            previewBitmap: Bitmap
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_weather)
            views.setTextViewText(R.id.widgetConditionText, condition)
            views.setTextViewText(R.id.widgetTempText, tempText)
            views.setTextViewText(R.id.widgetUpdatedText, updatedText)
            views.setImageViewBitmap(R.id.widgetPreviewImage, previewBitmap)

            val open = PendingIntent.getActivity(
                context,
                appWidgetId,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, open)

            manager.updateAppWidget(appWidgetId, views)
        }
    }
}
