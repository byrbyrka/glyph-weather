package com.glyphweather.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
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
import com.glyphweather.weather.WeatherCondition
import com.glyphweather.work.WeatherScheduler
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
            if (granted) {
                requestBackgroundPermission()
            } else {
                pendingEnable = false
                updateUi()
                Toast.makeText(this, "Permissions required for weather updates", Toast.LENGTH_LONG).show()
            }
        }
    }

    private val backgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (pendingEnable) {
            pendingEnable = false
            if (granted) {
                doEnable()
            } else {
                updateUi()
                Toast.makeText(this, "Background location is recommended", Toast.LENGTH_SHORT).show()
                // Still try to enable if we have at least foreground
                if (hasLocationPermission()) doEnable()
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
            WeatherScheduler.refreshNow(this)
            Toast.makeText(this, "Refreshing...", Toast.LENGTH_SHORT).show()
        }

        observeWork()
        updateUi()
        updatePreview()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_debug) {
            showDebugMenu()
            return true
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
        pendingEnable = true
        if (!hasLocationPermission()) {
            permissionLauncher.launch(foregroundPermissions)
        } else if (!hasBackgroundLocationPermission()) {
            requestBackgroundPermission()
        } else {
            pendingEnable = false
            doEnable()
        }
    }

    private fun requestBackgroundPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBackgroundLocationPermission()) {
            backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            pendingEnable = false
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

        val temp = prefs.temperatureC
        binding.tempText.text = if (temp.isNaN()) "—" else "${Math.round(temp)}°C"

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
        binding.statusText.text = getString(
            if (prefs.enabled) R.string.status_on else R.string.status_off
        )
    }

    /** Loads the animation for the current condition and loops it in the on-screen preview. */
    private fun updatePreview() {
        previewJob?.cancel()
        previewJob = lifecycleScope.launch {
            val frames = withContext(Dispatchers.IO) {
                runCatching { GlyphAnimation.load(this@MainActivity, prefs.condition).frames }
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

    private fun hasBackgroundLocationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * Hidden debug menu: reachable from the toolbar overflow ("⋮") menu.
     * Lets you force any [WeatherCondition] onto the Glyph Matrix (test mode) — while
     * active, real weather refreshes no longer overwrite the forced condition.
     */
    private fun showDebugMenu() {
        val conditions = WeatherCondition.entries
        var selected = conditions.indexOf(prefs.condition).coerceAtLeast(0)

        val info = buildString {
            appendLine("Condition: ${prefs.condition.name}")
            appendLine("Temperature: ${prefs.temperatureC}")
            appendLine("Last updated: ${prefs.lastUpdated}")
            appendLine("Display enabled: ${prefs.enabled}")
            appendLine("Test mode: ${prefs.debugOverride}")
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Debug Menu")
            .setMessage(info)
            .setSingleChoiceItems(
                conditions.map { it.titleEn }.toTypedArray(),
                selected
            ) { _, index -> selected = index }
            .setPositiveButton("Apply") { _, _ ->
                val condition = conditions[selected]
                prefs.debugOverride = true
                prefs.setWeather(condition, prefs.temperatureC, System.currentTimeMillis())
                updateUi()
                updatePreview()
                Toast.makeText(this, "Test mode: ${condition.titleEn}", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Turn Off Test Mode") { _, _ ->
                prefs.debugOverride = false
                Toast.makeText(this, "Test mode off", Toast.LENGTH_SHORT).show()
                WeatherScheduler.refreshNow(this)
            }
            .setNegativeButton("Close", null)
            .show()
    }
}
