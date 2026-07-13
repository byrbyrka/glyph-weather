# Glyph Weather — Weather on the Glyph Matrix (Nothing Phone (4a) Pro)

Android app that changes the image on the **Glyph Matrix** on the back of the
Nothing Phone (4a) Pro depending on the current weather. Each weather condition
maps to one of your animations (`glyph_weather_*.json`).

## How it works

1. Every selected interval (by default 15 minutes, configurable from 1 minute to
   1 hour) the app obtains the device location and requests the current weather
   from **Open-Meteo** (free, no API key).
2. The WMO weather code + the day/night flag are mapped to one of 7 states.
3. A foreground service keeps the connection to the Glyph Matrix and loops the
   matching animation via `GlyphMatrixManager.setAppMatrixFrame(...)`.

### Animation format

The Glyph Matrix on the 4a Pro is a ring of **137 mini-LEDs** inscribed in a
**13×13** grid (169 cells, 32 "cut" corners). Each frame in the JSON (`p`)
contains exactly 137 brightness values `0..255` — only real LEDs, row by row,
skipping corners. The number of lit LEDs per row:
`5,9,11,11,13,13,13,13,13,11,11,9,5`.

The expansion from 137 → 169 and the inverse mapping live in
[`GlyphFrameAnimation.kt`](app/src/main/java/com/glyphweather/glyph/GlyphFrameAnimation.kt).

### Weather → animation mapping

| Weather (WMO code)                        | Animation                      |
|-------------------------------------------|--------------------------------|
| 0–1 clear (day / night)                   | `sunny` / `clear_night`        |
| 2 partly cloudy                           | `partly_cloudy`                |
| 3 overcast, 45/48 fog                     | `cloudy`                       |
| 51–67 drizzle/rain, 80–82 showers         | `rain`                         |
| 71–77 snow, 85/86 snowfall                | `snow`                         |
| 95–99 thunderstorm                        | `thunderstorm`                 |

## Build

1. Open the project folder in **Android Studio** (Ladybug or newer).
2. Download the official **Glyph Matrix SDK** (`glyph-matrix-sdk-2.0.aar`) from
   the [GlyphMatrix-Developer-Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit)
   and place the `.aar` into [`app/libs/`](app/libs/).
3. Sync Gradle → Run on a device.

> The project is intentionally shipped without the proprietary Glyph SDK binary.
> Without it the `com.nothing.ketchum.*` classes are missing and the build fails.

## Device requirements

- Nothing Phone (4a) Pro (the registration constant is `Glyph.DEVICE_25111p`).
- System version **20250801+**, otherwise `setAppMatrixFrame` is unavailable.
- Enable Glyph access for the app in system settings if the phone asks.

### Debugging Glyph without release

```
adb shell settings put global nt_glyph_interface_debug_enable 1
```
(the mode turns itself off after 48 hours)

## Permissions

- `com.nothing.ketchum.permission.ENABLE` — access to the matrix.
- Location (coarse/fine + background on Android 10+) — to determine where you
  are for weather.
- Notifications — required foreground service notification.

## Structure

```
app/src/main/
├─ assets/                     ← your 7 animations glyph_weather_*.json
├─ java/com/glyphweather/
│  ├─ ui/MainActivity.kt        screen: on/off, refresh, matrix preview
│  ├─ ui/GlyphPreviewView.kt    draws the 137-LED ring on screen
│  ├─ glyph/
│  │  ├─ GlyphFrameAnimation.kt frame parser + 13×13/137 geometry
│  │  ├─ GlyphMatrixController.kt Nothing SDK wrapper
│  │  └─ GlyphWeatherService.kt foreground service, animation loop
│  ├─ weather/                  Open-Meteo, location, WMO mapping
│  ├─ work/                     WorkManager (periodic weather refresh)
│  └─ data/WeatherPrefs.kt      last state and settings
```

## If the matrix shows "garbage"

Frame values are passed as brightness `0..255` in a 13×13=169 array
(`setAppMatrixFrame`). If the layout differs on your firmware, edit only
`GlyphMatrix.expandToGrid()` in `GlyphFrameAnimation.kt`; the rest of the code
should not be touched.

## Update interval

The interval can be set from **1 minute to 60 minutes** in the app settings.

- **15–60 minutes**: updates are scheduled via WorkManager.
- **1–14 minutes**: updates are triggered by a timer inside the foreground
  service, because WorkManager does not support intervals shorter than
  15 minutes.

## Troubleshooting

- **No location in background**: make sure `ACCESS_BACKGROUND_LOCATION` is
  granted.
- **Glyph does not change after background update**: the service reads the
  saved weather state on every animation cycle, so the glyph should switch
  automatically.
- **After reboot the glyph is off**: ensure the app has `RECEIVE_BOOT_COMPLETED`
  permission; the service restarts automatically if the toggle was on.
