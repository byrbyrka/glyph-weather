package com.glyphweather.weather

enum class TemperatureUnit(val symbol: String) {
    CELSIUS("°C"),
    FAHRENHEIT("°F"),
    KELVIN("K");

    fun fromCelsius(celsius: Double): Double = when (this) {
        CELSIUS -> celsius
        FAHRENHEIT -> celsius * 9.0 / 5.0 + 32.0
        KELVIN -> celsius + 273.15
    }

    fun toCelsius(value: Double): Double = when (this) {
        CELSIUS -> value
        FAHRENHEIT -> (value - 32.0) * 5.0 / 9.0
        KELVIN -> value - 273.15
    }
}
