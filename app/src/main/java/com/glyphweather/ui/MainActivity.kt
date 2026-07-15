package com.glyphweather.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.glyphweather.R
import com.glyphweather.data.WeatherPrefs
import com.glyphweather.databinding.ActivityMainBinding
import com.glyphweather.glyph.GlyphAnimation
import com.glyphweather.glyph.GlyphMatrix
import com.glyphweather.glyph.GlyphWeatherService
import com.glyphweather.weather.IconPack
import com.glyphweather.weather.OpenMeteoClient
import com.glyphweather.weather.ShakeMetric
import com.glyphweather.weather.TemperatureUnit
import com.glyphweather.weather.WeatherCondition
import com.glyphweather.work.WeatherScheduler
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: WeatherPrefs

    private val foregroundPermissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        if (pendingEnable) {
            pendingEnable = false
            if (granted) {
                doEnable()
            } else {
                updateUi()
                Toast.makeText(this, "Permissions required for weather updates", Toast.LENGTH_LONG).show()
            }
        }
    }

    private var pendingEnable = false
    private var previewJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        prefs = WeatherPrefs(this)

        binding.toggleButton.setOnClickListener {
            if (prefs.enabled) doDisable() else requestEnable()
        }
        binding.refreshButton.setOnClickListener {
            if (prefs.debugOverride) {
                prefs.debugOverride = false
                Toast.makeText(this, "Test mode off, refreshing...", Toast.LENGTH_SHORT).show()
                updateUi()
            } else {
                Toast.makeText(this, "Refreshing...", Toast.LENGTH_SHORT).show()
            }
            WeatherScheduler.refreshNow(this)
        }
        binding.iconPackButton.setOnClickListener {
            prefs.iconPack = if (prefs.iconPack == IconPack.NEW) IconPack.LEGACY else IconPack.NEW
            updatePreview()
            Toast.makeText(this, getString(R.string.icon_pack_switched, prefs.iconPack.titleEn), Toast.LENGTH_SHORT).show()
        }
        binding.tempUnitButton.setOnClickListener {
            showTempUnitMenu()
        }

        binding.brightnessSlider.value = prefs.glyphBrightness.toFloat()
        binding.brightnessSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                prefs.glyphBrightness = value.toInt()
                if (prefs.enabled) GlyphWeatherService.start(this)
            }
        }

        binding.schedulerSwitch.isChecked = prefs.schedulerEnabled
        binding.schedulerSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.schedulerEnabled = isChecked
            updateSchedulerUi()
            if (prefs.enabled) GlyphWeatherService.start(this)
            val msg = if (isChecked) R.string.scheduler_enabled else R.string.scheduler_disabled
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        binding.schedulerOffButton.setOnClickListener { showTimePicker(isOff = true) }
        binding.schedulerOnButton.setOnClickListener { showTimePicker(isOff = false) }

        binding.shakeSwitch.isChecked = prefs.shakeEnabled
        binding.shakeSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.shakeEnabled = isChecked
            updateShakeUi()
            val msg = if (isChecked) R.string.shake_enabled else R.string.shake_disabled
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        binding.shakeMetricButton.setOnClickListener { showShakeMetricDialog() }

        observeWork()
        updateUi()
        updatePreview()
        updateSchedulerUi()
        updateShakeUi()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_debug -> {
                showDebugMenu()
                return true
            }
            R.id.action_weather_source -> {
                showWeatherSourceMenu()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        updateUi()
        updatePreview()
    }

    override fun onPause() {
        super.onPause()
        previewJob?.cancel()
    }

    private fun requestEnable() {
        if (!hasLocationPermission()) {
            pendingEnable = true
            permissionLauncher.launch(foregroundPermissions)
        } else {
            doEnable()
        }
    }

    private fun doEnable() {
        prefs.enabled = true
        GlyphWeatherService.start(this)
        WeatherScheduler.schedulePeriodic(this)
        WeatherScheduler.refreshNow(this)
        updateUi()
    }

    private fun doDisable() {
        prefs.enabled = false
        WeatherScheduler.cancel(this)
        GlyphWeatherService.stop(this)
        updateUi()
    }

    private fun observeWork() {
        val wm = WorkManager.getInstance(this)

        // Observe both periodic and one-shot updates
        wm.getWorkInfosForUniqueWorkLiveData(WeatherScheduler.PERIODIC).observe(this) { handleWorkInfos(it) }
        wm.getWorkInfosForUniqueWorkLiveData(WeatherScheduler.ONE_SHOT).observe(this) { handleWorkInfos(it) }
    }

    private fun handleWorkInfos(infos: List<WorkInfo>?) {
        val info = infos?.firstOrNull() ?: return
        Log.d("MainActivity", "Work status changed: ${info.state}")

        val isRunning = info.state == WorkInfo.State.RUNNING || info.state == WorkInfo.State.ENQUEUED
        binding.refreshButton.isEnabled = !isRunning

        if (info.state.isFinished) {
            updateUi()
            updatePreview()
            if (info.state == WorkInfo.State.FAILED) {
                Toast.makeText(this, "Weather update failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUi() {
        val condition = prefs.condition
        binding.conditionText.text = condition.titleEn

        val tempC = prefs.temperatureC
        val unit = prefs.temperatureUnit
        binding.tempText.text = if (tempC.isNaN()) "—" else {
            val converted = unit.fromCelsius(tempC)
            "${Math.round(converted)}${unit.symbol}"
        }

        binding.updatedText.text = if (prefs.lastUpdated == 0L) {
            getString(R.string.updated_never)
        } else {
            getString(
                R.string.updated_at,
                DateUtils.getRelativeTimeSpanString(prefs.lastUpdated)
            )
        }

        binding.toggleButton.text = getString(
            if (prefs.enabled) R.string.turn_off else R.string.turn_on
        )
        binding.statusText.text = when {
            prefs.debugOverride -> getString(R.string.status_test_mode)
            prefs.enabled && prefs.isSchedulerBlocking() -> getString(R.string.status_scheduler_off)
            prefs.enabled -> getString(R.string.status_on)
            else -> getString(R.string.status_off)
        }
    }

    private fun updateSchedulerUi() {
        val off = prefs.schedulerOffMinutes
        val on = prefs.schedulerOnMinutes
        binding.schedulerOffButton.text = getString(R.string.scheduler_off, off / 60, off % 60)
        binding.schedulerOnButton.text = getString(R.string.scheduler_on, on / 60, on % 60)
        binding.schedulerOffButton.isEnabled = prefs.schedulerEnabled
        binding.schedulerOnButton.isEnabled = prefs.schedulerEnabled
    }

    private fun showTimePicker(isOff: Boolean) {
        val currentMinutes = if (isOff) prefs.schedulerOffMinutes else prefs.schedulerOnMinutes
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(currentMinutes / 60)
            .setMinute(currentMinutes % 60)
            .setTitleText(if (isOff) R.string.scheduler_off else R.string.scheduler_on)
            .build()
        picker.addOnPositiveButtonClickListener {
            val minutes = picker.hour * 60 + picker.minute
            if (isOff) prefs.schedulerOffMinutes = minutes else prefs.schedulerOnMinutes = minutes
            updateSchedulerUi()
            if (prefs.enabled) GlyphWeatherService.start(this)
        }
        picker.show(supportFragmentManager, "time_picker")
    }

    /** Loads the animation for the current condition and loops it in the on-screen preview. */
    private fun updatePreview() {
        previewJob?.cancel()
        previewJob = lifecycleScope.launch {
            val frames = withContext(Dispatchers.IO) {
                runCatching { GlyphAnimation.load(this@MainActivity, prefs.condition, prefs.iconPack).frames }
                    .getOrNull()
            }.orEmpty()

            if (frames.isEmpty()) {
                binding.previewView.setGrid(IntArray(GlyphMatrix.CELLS))
                return@launch
            }

            while (isActive) {
                for (frame in frames) {
                    if (!isActive) break
                    binding.previewView.setGrid(frame.grid)
                    delay(frame.durationMs.coerceAtLeast(30L))
                }
            }
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * Hidden debug menu: reachable from the toolbar overflow ("⋮") menu.
     * The "Test mode" switch is the on/off control: turning it ON forces the condition
     * selected below onto the Glyph Matrix and stops real weather refreshes from
     * overwriting it; turning it OFF returns to live weather immediately.
     */
    private fun showDebugMenu() {
        val conditions = WeatherCondition.entries
        val dialogView = layoutInflater.inflate(R.layout.dialog_debug, null)
        val infoText = dialogView.findViewById<TextView>(R.id.debugInfoText)
        val testModeSwitch = dialogView.findViewById<MaterialSwitch>(R.id.testModeSwitch)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.conditionRadioGroup)

        infoText.text = buildString {
            appendLine("Condition: ${prefs.condition.name}")
            appendLine("Temperature: ${prefs.temperatureC}")
            appendLine("Last updated: ${prefs.lastUpdated}")
            appendLine("Display enabled: ${prefs.enabled}")
        }

        conditions.forEach { condition ->
            val button = RadioButton(this).apply {
                text = condition.titleEn
                id = condition.ordinal
                isChecked = condition == prefs.condition
            }
            radioGroup.addView(button)
        }

        fun setRadioGroupEnabled(enabled: Boolean) {
            radioGroup.alpha = if (enabled) 1f else 0.4f
            for (i in 0 until radioGroup.childCount) {
                radioGroup.getChildAt(i).isEnabled = enabled
            }
        }

        testModeSwitch.isChecked = prefs.debugOverride
        setRadioGroupEnabled(prefs.debugOverride)

        testModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            setRadioGroupEnabled(isChecked)
            prefs.debugOverride = isChecked
            if (isChecked) {
                val condition = conditions[radioGroup.checkedRadioButtonId.coerceAtLeast(0)]
                prefs.setWeather(condition, prefs.temperatureC, System.currentTimeMillis())
                // Make sure the selection is actually visible on the physical Glyph Matrix,
                // even if the display toggle was off.
                prefs.enabled = true
                GlyphWeatherService.start(this)
                Toast.makeText(this, "Test mode on: ${condition.titleEn}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Test mode off, refreshing...", Toast.LENGTH_SHORT).show()
                WeatherScheduler.refreshNow(this)
            }
            updateUi()
            updatePreview()
        }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            if (!testModeSwitch.isChecked || checkedId < 0) return@setOnCheckedChangeListener
            val condition = conditions[checkedId]
            prefs.setWeather(condition, prefs.temperatureC, System.currentTimeMillis())
            updateUi()
            updatePreview()
            Toast.makeText(this, "Test mode: ${condition.titleEn}", Toast.LENGTH_SHORT).show()
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Debug Menu")
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show()
    }

    /**
     * "Weather Source" menu: lets the user point the app at a custom weather API/service
     * instead of the built-in Open-Meteo default. The URL is a template with {lat}, {lon},
     * and {key} placeholders — whatever service is configured must return JSON shaped like
     * Open-Meteo's `current` object (weather_code, is_day, temperature_2m).
     */
    private fun showWeatherSourceMenu() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_weather_source, null)
        val urlInput = dialogView.findViewById<TextInputEditText>(R.id.urlTemplateInput)
        val keyInput = dialogView.findViewById<TextInputEditText>(R.id.apiKeyInput)

        urlInput.setText(prefs.weatherUrlTemplate)
        keyInput.setText(prefs.weatherApiKey)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_weather_source)
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val url = urlInput.text?.toString()?.trim().orEmpty()
                if (!url.contains("{lat}") || !url.contains("{lon}")) {
                    Toast.makeText(this, getString(R.string.weather_source_invalid), Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                prefs.weatherUrlTemplate = url
                prefs.weatherApiKey = keyInput.text?.toString()?.trim().orEmpty()
                Toast.makeText(this, getString(R.string.weather_source_saved), Toast.LENGTH_SHORT).show()
                WeatherScheduler.refreshNow(this)
            }
            .setNeutralButton(R.string.weather_source_reset) { _, _ ->
                prefs.weatherUrlTemplate = OpenMeteoClient.DEFAULT_URL_TEMPLATE
                prefs.weatherApiKey = ""
                Toast.makeText(this, getString(R.string.weather_source_reset_done), Toast.LENGTH_SHORT).show()
                WeatherScheduler.refreshNow(this)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTempUnitMenu() {
        val units = TemperatureUnit.entries.toTypedArray()
        val names = units.map { it.symbol }.toTypedArray()
        val current = prefs.temperatureUnit
        val selectedIndex = units.indexOf(current)

        MaterialAlertDialogBuilder(this)
            .setTitle("Temperature Unit")
            .setSingleChoiceItems(names, selectedIndex) { dialog, which ->
                prefs.temperatureUnit = units[which]
                updateUi()
                Toast.makeText(this, "Unit changed to ${units[which].symbol}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateShakeUi() {
        binding.shakeMetricButton.isEnabled = prefs.shakeEnabled
        binding.shakeMetricButton.text = getString(R.string.shake_metric, prefs.shakeMetric.titleEn)
    }

    private fun showShakeMetricDialog() {
        val metrics = ShakeMetric.entries.toTypedArray()
        val names = metrics.map { it.titleEn }.toTypedArray()
        val current = prefs.shakeMetric
        val selectedIndex = metrics.indexOf(current)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.shake_metric_dialog_title)
            .setSingleChoiceItems(names, selectedIndex) { dialog, which ->
                prefs.shakeMetric = metrics[which]
                updateShakeUi()
                if (prefs.enabled) WeatherScheduler.refreshNow(this)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
