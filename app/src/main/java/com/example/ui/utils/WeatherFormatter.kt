package com.example.ui.utils

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.example.R
import com.example.data.model.TemperatureUnit
import com.example.data.model.WindSpeedUnit
import com.example.ui.theme.GaugeGood
import com.example.ui.theme.GaugeHazardous
import com.example.ui.theme.GaugeModerate
import com.example.ui.theme.GaugeSensitive
import com.example.ui.theme.GaugeUnhealthy
import com.example.ui.theme.GaugeVeryUnhealthy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class WeatherMood {
    CLEAR_DAY,
    CLEAR_NIGHT,
    PARTLY_CLOUDY_DAY,
    PARTLY_CLOUDY_NIGHT,
    CLOUDY,
    FOG,
    RAIN,
    SNOW,
    THUNDERSTORM
}

object WeatherFormatter {

    fun toDisplayTemp(tempC: Double, unit: TemperatureUnit): Int {
        return when (unit) {
            TemperatureUnit.CELSIUS -> tempC.roundToInt()
            TemperatureUnit.FAHRENHEIT -> ((tempC * 9.0 / 5.0) + 32.0).roundToInt()
        }
    }

    fun formatTemp(tempC: Double, unit: TemperatureUnit): String {
        return "${toDisplayTemp(tempC, unit)}°"
    }

    fun formatTempWithUnit(tempC: Double, unit: TemperatureUnit): String {
        val suffix = if (unit == TemperatureUnit.CELSIUS) "C" else "F"
        return "${toDisplayTemp(tempC, unit)}°$suffix"
    }

    fun toDisplayWind(speedKmh: Double, unit: WindSpeedUnit): Int {
        return when (unit) {
            WindSpeedUnit.KMH -> speedKmh.roundToInt()
            WindSpeedUnit.MPH -> (speedKmh * 0.621371).roundToInt()
        }
    }

    fun formatVisibilityValue(meters: Double, windUnit: WindSpeedUnit): String {
        val km = meters / 1000.0
        return when (windUnit) {
            WindSpeedUnit.KMH -> String.format(Locale.US, "%.1f", km).removeSuffix(".0")
            WindSpeedUnit.MPH -> String.format(Locale.US, "%.1f", km * 0.621371).removeSuffix(".0")
        }
    }

    @StringRes
    fun windDirectionResId(deg: Int): Int {
        val normalized = ((deg % 360) + 360) % 360
        return when {
            normalized < 23 -> R.string.dir_n
            normalized < 68 -> R.string.dir_ne
            normalized < 113 -> R.string.dir_e
            normalized < 158 -> R.string.dir_se
            normalized < 203 -> R.string.dir_s
            normalized < 248 -> R.string.dir_sw
            normalized < 293 -> R.string.dir_w
            normalized < 338 -> R.string.dir_nw
            else -> R.string.dir_n
        }
    }

    @StringRes
    fun weatherConditionResId(code: Int): Int {
        return when (code) {
            0 -> R.string.weather_clear
            1 -> R.string.weather_mainly_clear
            2 -> R.string.weather_partly_cloudy
            3 -> R.string.weather_overcast
            45, 48 -> R.string.weather_fog
            51, 53 -> R.string.weather_drizzle
            55, 56, 57 -> R.string.weather_drizzle_dense
            61 -> R.string.weather_rain_light
            63 -> R.string.weather_rain_moderate
            65 -> R.string.weather_rain_heavy
            66, 67 -> R.string.weather_freezing_rain
            71, 73, 77 -> R.string.weather_snow_light
            75 -> R.string.weather_snow_heavy
            80, 81, 82 -> R.string.weather_rain_showers
            85, 86 -> R.string.weather_snow_showers
            95 -> R.string.weather_thunderstorm
            96, 99 -> R.string.weather_thunderstorm_hail
            else -> R.string.weather_partly_cloudy
        }
    }

    fun weatherMood(code: Int, isDay: Boolean): WeatherMood {
        return when (code) {
            0, 1 -> if (isDay) WeatherMood.CLEAR_DAY else WeatherMood.CLEAR_NIGHT
            2 -> if (isDay) WeatherMood.PARTLY_CLOUDY_DAY else WeatherMood.PARTLY_CLOUDY_NIGHT
            3 -> WeatherMood.CLOUDY
            45, 48 -> WeatherMood.FOG
            51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> WeatherMood.RAIN
            71, 73, 75, 77, 85, 86 -> WeatherMood.SNOW
            95, 96, 99 -> WeatherMood.THUNDERSTORM
            else -> if (isDay) WeatherMood.PARTLY_CLOUDY_DAY else WeatherMood.PARTLY_CLOUDY_NIGHT
        }
    }

    @StringRes
    fun aqiLabelResId(usAqi: Int?): Int {
        if (usAqi == null) return R.string.aqi_unavailable
        return when {
            usAqi <= 50 -> R.string.aqi_good
            usAqi <= 100 -> R.string.aqi_moderate
            usAqi <= 150 -> R.string.aqi_sensitive
            usAqi <= 200 -> R.string.aqi_unhealthy
            usAqi <= 300 -> R.string.aqi_very_unhealthy
            else -> R.string.aqi_hazardous
        }
    }

    fun aqiColor(usAqi: Int?): Color {
        if (usAqi == null) return GaugeModerate
        return when {
            usAqi <= 50 -> GaugeGood
            usAqi <= 100 -> GaugeModerate
            usAqi <= 150 -> GaugeSensitive
            usAqi <= 200 -> GaugeUnhealthy
            usAqi <= 300 -> GaugeVeryUnhealthy
            else -> GaugeHazardous
        }
    }

    @StringRes
    fun uvLabelResId(uv: Double): Int {
        return when {
            uv < 3.0 -> R.string.uv_low
            uv < 6.0 -> R.string.uv_moderate
            uv < 8.0 -> R.string.uv_high
            uv < 11.0 -> R.string.uv_very_high
            else -> R.string.uv_extreme
        }
    }

    fun uvColor(uv: Double): Color {
        return when {
            uv < 3.0 -> GaugeGood
            uv < 6.0 -> GaugeModerate
            uv < 8.0 -> GaugeSensitive
            uv < 11.0 -> GaugeUnhealthy
            else -> GaugeVeryUnhealthy
        }
    }

    @StringRes
    fun humidityComfortResId(humidity: Int): Int {
        return when {
            humidity < 30 -> R.string.dew_comfort_dry
            humidity <= 65 -> R.string.dew_comfort_comfortable
            else -> R.string.dew_comfort_humid
        }
    }

    fun formatHourLabel(timeIso: String, isArabic: Boolean): String {
        val hourPart = timeIso.substringAfter("T", "").take(2).toIntOrNull() ?: return timeIso
        val isPm = hourPart >= 12
        val hour12 = when {
            hourPart == 0 -> 12
            hourPart > 12 -> hourPart - 12
            else -> hourPart
        }
        return if (isArabic) {
            val suffix = if (isPm) "م" else "ص"
            "$hour12 $suffix"
        } else {
            val suffix = if (isPm) "PM" else "AM"
            "$hour12 $suffix"
        }
    }

    fun formatClockTime(timeIso: String, isArabic: Boolean): String {
        val timePart = timeIso.substringAfter("T", "")
        if (timePart.length < 5) return "--:--"
        val hour = timePart.substring(0, 2).toIntOrNull() ?: return timePart
        val minute = timePart.substring(3, 5)
        val isPm = hour >= 12
        val hour12 = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        val suffix = if (isArabic) (if (isPm) "م" else "ص") else (if (isPm) "PM" else "AM")
        return "$hour12:$minute $suffix"
    }

    fun formatDayName(dateIso: String, isArabic: Boolean): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val date = parser.parse(dateIso) ?: return dateIso
            val locale = if (isArabic) Locale("ar") else Locale.ENGLISH
            SimpleDateFormat("EEEE", locale).format(date)
        } catch (_: Exception) {
            dateIso
        }
    }

    fun formatShortDate(dateIso: String, isArabic: Boolean): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val date = parser.parse(dateIso) ?: return dateIso
            val locale = if (isArabic) Locale("ar") else Locale.ENGLISH
            SimpleDateFormat("d MMM", locale).format(date)
        } catch (_: Exception) {
            dateIso
        }
    }

    fun formatUpdatedClock(epochMillis: Long, isArabic: Boolean): String {
        val sdf = SimpleDateFormat("h:mm a", Locale.US)
        val raw = sdf.format(Date(epochMillis))
        return if (isArabic) {
            raw.replace("AM", "ص").replace("PM", "م")
        } else {
            raw
        }
    }

    fun computeSunProgress(currentIso: String, sunriseIso: String, sunsetIso: String): Float {
        val nowMins = isoToMinutesOfDay(currentIso) ?: return 0.5f
        val riseMins = isoToMinutesOfDay(sunriseIso) ?: return 0.5f
        val setMins = isoToMinutesOfDay(sunsetIso) ?: return 0.5f
        if (setMins <= riseMins) return 0.5f
        return ((nowMins - riseMins).toFloat() / (setMins - riseMins).toFloat()).coerceIn(0f, 1f)
    }

    private fun isoToMinutesOfDay(iso: String): Int? {
        val time = iso.substringAfter("T", "")
        if (time.length < 5) return null
        val h = time.substring(0, 2).toIntOrNull() ?: return null
        val m = time.substring(3, 5).toIntOrNull() ?: return null
        return h * 60 + m
    }
}
