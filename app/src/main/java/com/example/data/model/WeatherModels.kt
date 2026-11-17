package com.example.data.model

import com.squareup.moshi.JsonClass

enum class AppLanguage(val code: String) {
    SYSTEM("system"),
    ENGLISH("en"),
    ARABIC("ar");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: SYSTEM
    }
}

enum class ThemeMode(val code: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromCode(code: String?): ThemeMode =
            entries.firstOrNull { it.code == code } ?: SYSTEM
    }
}

enum class TemperatureUnit(val code: String) {
    CELSIUS("C"),
    FAHRENHEIT("F");

    companion object {
        fun fromCode(code: String?): TemperatureUnit =
            entries.firstOrNull { it.code == code } ?: CELSIUS
    }
}

enum class WindSpeedUnit(val code: String) {
    KMH("kmh"),
    MPH("mph");

    companion object {
        fun fromCode(code: String?): WindSpeedUnit =
            entries.firstOrNull { it.code == code } ?: KMH
    }
}

data class UserPreferences(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val windSpeedUnit: WindSpeedUnit = WindSpeedUnit.KMH,
    val activeCityId: String? = null,
    val locationPromptDismissed: Boolean = false
)

data class SavedCity(
    val id: String,
    val nameEn: String,
    val nameAr: String,
    val countryEn: String,
    val countryAr: String,
    val admin1: String? = null,
    val latitude: Double,
    val longitude: Double,
    val isCurrentLocation: Boolean = false,
    val lastTempC: Double? = null,
    val lastWeatherCode: Int? = null,
    val lastIsDay: Boolean? = null,
    val lastHighC: Double? = null,
    val lastLowC: Double? = null,
    val updatedAt: Long = 0L
) {
    fun displayName(isArabic: Boolean): String =
        if (isArabic) nameAr.ifBlank { nameEn } else nameEn.ifBlank { nameAr }

    fun displayCountry(isArabic: Boolean): String =
        if (isArabic) countryAr.ifBlank { countryEn } else countryEn.ifBlank { countryAr }
}

data class GeocodingCity(
    val id: String,
    val name: String,
    val nameEn: String,
    val nameAr: String,
    val country: String,
    val countryEn: String,
    val countryAr: String,
    val admin1: String?,
    val latitude: Double,
    val longitude: Double,
    val countryCode: String?
)

@JsonClass(generateAdapter = true)
data class CurrentWeather(
    val timeIso: String,
    val temperatureC: Double,
    val apparentTemperatureC: Double,
    val humidityPercent: Int,
    val isDay: Boolean,
    val precipitationMm: Double,
    val weatherCode: Int,
    val cloudCoverPercent: Int,
    val pressureHpa: Double,
    val windSpeedKmh: Double,
    val windDirectionDeg: Int,
    val windGustsKmh: Double,
    val uvIndex: Double,
    val visibilityMeters: Double
)

@JsonClass(generateAdapter = true)
data class HourlyForecast(
    val timeIso: String,
    val temperatureC: Double,
    val apparentTemperatureC: Double,
    val humidityPercent: Int,
    val precipitationProbability: Int,
    val weatherCode: Int,
    val uvIndex: Double,
    val visibilityMeters: Double,
    val isDay: Boolean
)

@JsonClass(generateAdapter = true)
data class DailyForecast(
    val dateIso: String,
    val weatherCode: Int,
    val maxTempC: Double,
    val minTempC: Double,
    val sunriseIso: String,
    val sunsetIso: String,
    val uvIndexMax: Double,
    val precipitationSumMm: Double,
    val precipitationProbabilityMax: Int,
    val windSpeedMaxKmh: Double
)

@JsonClass(generateAdapter = true)
data class AirQuality(
    val usAqi: Int?,
    val pm25: Double?,
    val pm10: Double?,
    val ozone: Double?
)

@JsonClass(generateAdapter = true)
data class WeatherBundle(
    val cityId: String,
    val timezone: String,
    val current: CurrentWeather,
    val hourly: List<HourlyForecast>,
    val daily: List<DailyForecast>,
    val airQuality: AirQuality?,
    val fetchedAtMillis: Long
)
