# Glyph Weather — weather on Glyph Matrix (Nothing Phone (4a) Pro)

An Android application that changes the image on the **Glyph Matrix** of the back panel of the
Nothing Phone (4a) Pro depending on the current weather. Each weather condition
corresponds to one of your animations (`glyph_weather_*.json`).

<img width="908" height="454" alt="weather_icons_preview_notext" src="https://github.com/user-attachments/assets/18a701e6-6e4e-45fa-9415-921bd67cabd2" />

## How it works

1. Every 15 minutes (WorkManager), the application determines the geolocation and requests
   the current weather from **Open-Meteo** (free, no API key required).
2. The WMO weather code + "day/night" flag are mapped to one of 7 states.
3. A Foreground service maintains a connection to the matrix and plays the
   corresponding animation in a loop via `GlyphMatrixManager.setAppMatrixFrame(...)`.

### Animation Format

The Glyph Matrix on the 4a Pro is a circle of **137 mini-LEDs** inscribed in a **13×13** grid
(169 cells, 32 corners "cut out"). Each frame in JSON (`p`) contains exactly 137
brightness values `0..255` — only real LEDs, line by line, skipping the corners.
The number of "live" LEDs by row: `5,9,11,11,13,13,13,13,13,11,11,9,5`.

Expanding 137 → 169 and reverse mapping lives in
[`GlyphFrameAnimation.kt`](app/src/main/java/com/glyphweather/glyph/GlyphFrameAnimation.kt).

### Weather → Animation Mapping Table

| Weather (WMO code)                         | Animation                       |
|-------------------------------------------|--------------------------------|
| 0–1 clear (day / night)                   | `sunny` / `clear_night`        |
| 2 partly cloudy                           | `partly_cloudy`                |
| 3 overcast, 45/48 fog                     | `cloudy`                       |
| 51–67 drizzle/rain, 80–82 rain showers    | `rain`                         |
| 71–77 snow, 85/86 snowfall                | `snow`                         |
| 95–99 thunderstorm                        | `thunderstorm`                 |

## Building

1. Open the project folder in **Android Studio** (Ladybug or newer).
2. Download the official **Glyph Matrix SDK** (`glyph-matrix-sdk-2.0.aar`) from the
   [GlyphMatrix-Developer-Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit)
   and place the `.aar` in the [`app/libs/`](app/libs/) folder.
3. Sync Gradle → Run on device.

> The project is intentionally shipped without the Glyph SDK binary (proprietary).
> Without it, the `com.nothing.ketchum.*` classes will not be found and the build will fail.

### Device Requirements

- Nothing Phone (4a) Pro (registration constant `Glyph.DEVICE_25111p`).
- System version **20250801+** — otherwise `setAppMatrixFrame` is unavailable.
- Enable Glyph access for the app in system settings once, if requested by the phone.

### Debugging Glyph without release (optional)

```
adb shell settings put global nt_glyph_interface_debug_enable 1
```
(the mode will turn off automatically after 48 hours)

## Permissions

- `com.nothing.ketchum.permission.ENABLE` — access to the matrix.
- Geolocation (coarse/fine) — to determine where you are for the weather.
- Notifications — mandatory foreground service notification.

## Structure

```
app/src/main/
├─ assets/                     ← your 7 glyph_weather_*.json animations
├─ java/com/glyphweather/
│  ├─ ui/MainActivity.kt        screen: on/off, refresh, matrix preview
│  ├─ ui/GlyphPreviewView.kt    draws a circle of 137 LEDs on the screen
│  ├─ glyph/
│  │  ├─ GlyphFrameAnimation.kt frame parser + 13×13/137 geometry
│  │  ├─ GlyphMatrixController.kt Nothing SDK wrapper
│  │  └─ GlyphWeatherService.kt foreground service, animation loop
│  ├─ weather/                  Open-Meteo, geolocation, WMO mapping
│  ├─ work/                     WorkManager (update every 15 min)
│  └─ data/WeatherPrefs.kt      last state
```

## If there is "mess" on the matrix

Frame values are passed as brightness `0..255` in a 13×13=169 array
(`setAppMatrixFrame`). If the layout on a specific firmware is different, edit
only `GlyphMatrix.expandToGrid()` in `GlyphFrameAnimation.kt`; no need to touch the rest of the code.
