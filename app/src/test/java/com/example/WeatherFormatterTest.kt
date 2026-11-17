package com.example

import com.example.data.model.TemperatureUnit
import com.example.data.model.WindSpeedUnit
import com.example.ui.utils.WeatherFormatter
import com.example.ui.utils.WeatherMood
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherFormatterTest {

    @Test
    fun `temperature conversion celsius and fahrenheit`() {
        assertEquals(25, WeatherFormatter.toDisplayTemp(25.2, TemperatureUnit.CELSIUS))
        assertEquals(77, WeatherFormatter.toDisplayTemp(25.0, TemperatureUnit.FAHRENHEIT))
        assertEquals("32°", WeatherFormatter.formatTemp(0.0, TemperatureUnit.FAHRENHEIT))
    }

    @Test
    fun `wind speed conversion kmh and mph`() {
        assertEquals(100, WeatherFormatter.toDisplayWind(100.0, WindSpeedUnit.KMH))
        assertEquals(62, WeatherFormatter.toDisplayWind(100.0, WindSpeedUnit.MPH))
    }

    @Test
    fun `wmo codes map to expected mood and label`() {
        assertEquals(WeatherMood.CLEAR_DAY, WeatherFormatter.weatherMood(0, isDay = true))
        assertEquals(WeatherMood.CLEAR_NIGHT, WeatherFormatter.weatherMood(0, isDay = false))
        assertEquals(WeatherMood.RAIN, WeatherFormatter.weatherMood(63, isDay = true))
        assertEquals(WeatherMood.THUNDERSTORM, WeatherFormatter.weatherMood(95, isDay = true))
        assertEquals(R.string.weather_clear, WeatherFormatter.weatherConditionResId(0))
    }

    @Test
    fun `hour label formats correctly in english and arabic`() {
        assertEquals("3 PM", WeatherFormatter.formatHourLabel("2026-11-17T15:00", isArabic = false))
        assertEquals("3 م", WeatherFormatter.formatHourLabel("2026-11-17T15:00", isArabic = true))
        assertEquals("12 AM", WeatherFormatter.formatHourLabel("2026-11-17T00:00", isArabic = false))
    }

    @Test
    fun `sun progress clamps cleanly`() {
        assertEquals(0.5f, WeatherFormatter.computeSunProgress("2026-11-17T12:00", "2026-11-17T06:00", "2026-11-17T18:00"), 0.01f)
        assertEquals(0.0f, WeatherFormatter.computeSunProgress("2026-11-17T04:00", "2026-11-17T06:00", "2026-11-17T18:00"), 0.01f)
        assertEquals(1.0f, WeatherFormatter.computeSunProgress("2026-11-17T21:00", "2026-11-17T06:00", "2026-11-17T18:00"), 0.01f)
    }
}
