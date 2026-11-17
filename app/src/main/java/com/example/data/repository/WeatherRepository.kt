package com.example.data.repository

import com.example.data.local.CachedForecastDao
import com.example.data.local.CachedForecastEntity
import com.example.data.local.SavedCityDao
import com.example.data.local.SavedCityEntity
import com.example.data.model.AirQuality
import com.example.data.model.CurrentWeather
import com.example.data.model.DailyForecast
import com.example.data.model.ForecastResponseDto
import com.example.data.model.GeocodingCity
import com.example.data.model.HourlyForecast
import com.example.data.model.SavedCity
import com.example.data.model.WeatherBundle
import com.example.data.remote.NetworkModule
import com.example.data.remote.OpenMeteoAirQualityApi
import com.example.data.remote.OpenMeteoForecastApi
import com.example.data.remote.OpenMeteoGeocodingApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

sealed interface WeatherResult {
    data class Live(val bundle: WeatherBundle) : WeatherResult
    data class CachedOffline(val bundle: WeatherBundle, val errorMessage: String?) : WeatherResult
    data class Error(val message: String?) : WeatherResult
}

class WeatherRepository(
    private val savedCityDao: SavedCityDao,
    private val cachedForecastDao: CachedForecastDao,
    private val forecastApi: OpenMeteoForecastApi = NetworkModule.forecastApi,
    private val airQualityApi: OpenMeteoAirQualityApi = NetworkModule.airQualityApi,
    private val geocodingApi: OpenMeteoGeocodingApi = NetworkModule.geocodingApi
) {

    private val bundleAdapter = NetworkModule.moshi.adapter(WeatherBundle::class.java)

    val savedCitiesFlow: Flow<List<SavedCity>> =
        savedCityDao.observeCities().map { entities -> entities.map { it.toDomain() } }

    suspend fun ensureDefaultCity(): SavedCity = withContext(Dispatchers.IO) {
        val existing = savedCityDao.getCitiesOnce()
        if (existing.isNotEmpty()) {
            return@withContext existing.first().toDomain()
        }
        val defaultCity = popularCities.first()
        savedCityDao.upsertCity(SavedCityEntity.fromDomain(defaultCity))
        defaultCity
    }

    suspend fun getCityById(cityId: String): SavedCity? = withContext(Dispatchers.IO) {
        savedCityDao.getCityById(cityId)?.toDomain()
    }

    suspend fun saveCity(city: SavedCity) = withContext(Dispatchers.IO) {
        savedCityDao.upsertCity(SavedCityEntity.fromDomain(city))
    }

    suspend fun upsertCurrentGpsCity(detected: DetectedLocation): SavedCity = withContext(Dispatchers.IO) {
        val city = SavedCity(
            id = CURRENT_LOCATION_ID,
            nameEn = detected.nameEn,
            nameAr = detected.nameAr,
            countryEn = detected.countryEn,
            countryAr = detected.countryAr,
            latitude = detected.latitude,
            longitude = detected.longitude,
            isCurrentLocation = true,
            updatedAt = System.currentTimeMillis()
        )
        savedCityDao.upsertCity(SavedCityEntity.fromDomain(city))
        city
    }

    suspend fun deleteCity(cityId: String) = withContext(Dispatchers.IO) {
        savedCityDao.deleteCityById(cityId)
        cachedForecastDao.deleteForecast(cityId)
    }

    suspend fun getWeatherForCity(city: SavedCity, forceRefresh: Boolean = true): WeatherResult =
        withContext(Dispatchers.IO) {
            val cachedEntity = cachedForecastDao.getForecast(city.id)
            val cachedBundle = cachedEntity?.let {
                runCatching { bundleAdapter.fromJson(it.forecastJson) }.getOrNull()
            }

            if (!forceRefresh && cachedBundle != null &&
                System.currentTimeMillis() - cachedBundle.fetchedAtMillis < CACHE_FRESH_MS
            ) {
                return@withContext WeatherResult.Live(cachedBundle)
            }

            try {
                val bundle = fetchRemoteWeather(city)
                val json = bundleAdapter.toJson(bundle)
                cachedForecastDao.upsertForecast(
                    CachedForecastEntity(
                        cityId = city.id,
                        forecastJson = json,
                        fetchedAt = bundle.fetchedAtMillis
                    )
                )
                val todayDaily = bundle.daily.firstOrNull()
                savedCityDao.updateCityWeatherSummary(
                    id = city.id,
                    tempC = bundle.current.temperatureC,
                    weatherCode = bundle.current.weatherCode,
                    isDay = bundle.current.isDay,
                    highC = todayDaily?.maxTempC,
                    lowC = todayDaily?.minTempC,
                    updatedAt = bundle.fetchedAtMillis
                )
                WeatherResult.Live(bundle)
            } catch (e: Exception) {
                if (cachedBundle != null) {
                    WeatherResult.CachedOffline(cachedBundle, e.localizedMessage)
                } else {
                    WeatherResult.Error(e.localizedMessage)
                }
            }
        }

    private suspend fun fetchRemoteWeather(city: SavedCity): WeatherBundle = coroutineScope {
        val forecastDeferred = async {
            forecastApi.getForecast(latitude = city.latitude, longitude = city.longitude)
        }
        val aqiDeferred = async {
            runCatching {
                airQualityApi.getAirQuality(latitude = city.latitude, longitude = city.longitude)
            }.getOrNull()
        }

        val forecastDto = forecastDeferred.await()
        val aqiDto = aqiDeferred.await()

        mapToWeatherBundle(
            cityId = city.id,
            forecast = forecastDto,
            airQuality = aqiDto?.current?.let {
                AirQuality(
                    usAqi = it.usAqi,
                    pm25 = it.pm25,
                    pm10 = it.pm10,
                    ozone = it.ozone
                )
            }
        )
    }

    private fun mapToWeatherBundle(
        cityId: String,
        forecast: ForecastResponseDto,
        airQuality: AirQuality?
    ): WeatherBundle {
        val currentDto = forecast.current ?: error("Missing current weather payload")
        val hourlyDto = forecast.hourly
        val dailyDto = forecast.daily

        val hourlyList = mutableListOf<HourlyForecast>()
        val hourlyTimes = hourlyDto?.time.orEmpty()
        for (i in hourlyTimes.indices) {
            val time = hourlyTimes.getOrNull(i) ?: continue
            hourlyList.add(
                HourlyForecast(
                    timeIso = time,
                    temperatureC = hourlyDto?.temperature2m?.getOrNull(i) ?: currentDto.temperature2m,
                    apparentTemperatureC = hourlyDto?.apparentTemperature?.getOrNull(i) ?: currentDto.apparentTemperature,
                    humidityPercent = hourlyDto?.relativeHumidity2m?.getOrNull(i) ?: currentDto.relativeHumidity2m,
                    precipitationProbability = hourlyDto?.precipitationProbability?.getOrNull(i) ?: 0,
                    weatherCode = hourlyDto?.weatherCode?.getOrNull(i) ?: currentDto.weatherCode,
                    uvIndex = hourlyDto?.uvIndex?.getOrNull(i) ?: 0.0,
                    visibilityMeters = hourlyDto?.visibility?.getOrNull(i) ?: 10000.0,
                    isDay = (hourlyDto?.isDay?.getOrNull(i) ?: currentDto.isDay) == 1
                )
            )
        }

        // Match current hour in hourly list for current UV & visibility
        val currentHourPrefix = currentDto.time.take(13) // "YYYY-MM-DDTHH"
        val matchingHour = hourlyList.firstOrNull { it.timeIso.startsWith(currentHourPrefix) }
            ?: hourlyList.firstOrNull()

        val current = CurrentWeather(
            timeIso = currentDto.time,
            temperatureC = currentDto.temperature2m,
            apparentTemperatureC = currentDto.apparentTemperature,
            humidityPercent = currentDto.relativeHumidity2m,
            isDay = currentDto.isDay == 1,
            precipitationMm = currentDto.precipitation,
            weatherCode = currentDto.weatherCode,
            cloudCoverPercent = currentDto.cloudCover,
            pressureHpa = currentDto.pressureMsl,
            windSpeedKmh = currentDto.windSpeed10m,
            windDirectionDeg = currentDto.windDirection10m,
            windGustsKmh = currentDto.windGusts10m,
            uvIndex = matchingHour?.uvIndex ?: 0.0,
            visibilityMeters = matchingHour?.visibilityMeters ?: 10000.0
        )

        // Filter hourly to 24 hours starting from current hour
        val startIndex = hourlyList.indexOfFirst { it.timeIso >= currentDto.time.take(13) }
            .takeIf { it >= 0 } ?: 0
        val next24Hours = hourlyList.drop(startIndex).take(24)

        val dailyList = mutableListOf<DailyForecast>()
        val dailyDates = dailyDto?.time.orEmpty()
        for (i in dailyDates.indices) {
            val date = dailyDates.getOrNull(i) ?: continue
            dailyList.add(
                DailyForecast(
                    dateIso = date,
                    weatherCode = dailyDto?.weatherCode?.getOrNull(i) ?: 0,
                    maxTempC = dailyDto?.temperature2mMax?.getOrNull(i) ?: currentDto.temperature2m,
                    minTempC = dailyDto?.temperature2mMin?.getOrNull(i) ?: currentDto.temperature2m,
                    sunriseIso = dailyDto?.sunrise?.getOrNull(i) ?: "",
                    sunsetIso = dailyDto?.sunset?.getOrNull(i) ?: "",
                    uvIndexMax = dailyDto?.uvIndexMax?.getOrNull(i) ?: 0.0,
                    precipitationSumMm = dailyDto?.precipitationSum?.getOrNull(i) ?: 0.0,
                    precipitationProbabilityMax = dailyDto?.precipitationProbabilityMax?.getOrNull(i) ?: 0,
                    windSpeedMaxKmh = dailyDto?.windSpeed10mMax?.getOrNull(i) ?: 0.0
                )
            )
        }

        return WeatherBundle(
            cityId = cityId,
            timezone = forecast.timezone ?: "UTC",
            current = current,
            hourly = next24Hours,
            daily = dailyList.take(7),
            airQuality = airQuality,
            fetchedAtMillis = System.currentTimeMillis()
        )
    }

    suspend fun searchCities(query: String, isArabicUi: Boolean): List<GeocodingCity> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.length < 2) return@withContext emptyList()

            val primaryLang = if (isArabicUi) "ar" else "en"
            val secondaryLang = if (isArabicUi) "en" else "ar"

            coroutineScope {
                val primaryDeferred = async {
                    runCatching {
                        geocodingApi.searchCities(query = trimmed, count = 10, language = primaryLang)
                    }.getOrNull()?.results.orEmpty()
                }
                val secondaryDeferred = async {
                    runCatching {
                        geocodingApi.searchCities(query = trimmed, count = 10, language = secondaryLang)
                    }.getOrNull()?.results.orEmpty()
                }

                val primaryList = primaryDeferred.await()
                val secondaryList = secondaryDeferred.await()
                val secondaryById = secondaryList.associateBy { it.id }

                primaryList.map { item ->
                    val alt = secondaryById[item.id]
                    val nameEn = if (primaryLang == "en") item.name else (alt?.name ?: item.name)
                    val nameAr = if (primaryLang == "ar") item.name else (alt?.name ?: item.name)
                    val countryEn = if (primaryLang == "en") (item.country ?: "") else (alt?.country ?: item.country ?: "")
                    val countryAr = if (primaryLang == "ar") (item.country ?: "") else (alt?.country ?: item.country ?: "")
                    val cityId = String.format(Locale.US, "%.3f_%.3f", item.latitude, item.longitude)

                    GeocodingCity(
                        id = cityId,
                        name = item.name,
                        nameEn = nameEn,
                        nameAr = nameAr,
                        country = item.country ?: "",
                        countryEn = countryEn,
                        countryAr = countryAr,
                        admin1 = item.admin1,
                        latitude = item.latitude,
                        longitude = item.longitude,
                        countryCode = item.countryCode
                    )
                }.distinctBy {
                    // Deduplicate nearby identical matches
                    "${(it.latitude * 100).toInt()}_${(it.longitude * 100).toInt()}_${it.nameEn}"
                }
            }
        }

    fun getSuggestedPopularCities(): List<SavedCity> = popularCities

    companion object {
        const val CURRENT_LOCATION_ID = "current_gps"
        private const val CACHE_FRESH_MS = 10 * 60 * 1000L // 10 minutes

        val popularCities = listOf(
            SavedCity(
                id = "30.044_31.236",
                nameEn = "Cairo",
                nameAr = "القاهرة",
                countryEn = "Egypt",
                countryAr = "مصر",
                latitude = 30.0444,
                longitude = 31.2357
            ),
            SavedCity(
                id = "24.714_46.675",
                nameEn = "Riyadh",
                nameAr = "الرياض",
                countryEn = "Saudi Arabia",
                countryAr = "المملكة العربية السعودية",
                latitude = 24.7136,
                longitude = 46.6753
            ),
            SavedCity(
                id = "25.205_55.271",
                nameEn = "Dubai",
                nameAr = "دبي",
                countryEn = "United Arab Emirates",
                countryAr = "الإمارات العربية المتحدة",
                latitude = 25.2048,
                longitude = 55.2708
            ),
            SavedCity(
                id = "51.507_-0.128",
                nameEn = "London",
                nameAr = "لندن",
                countryEn = "United Kingdom",
                countryAr = "المملكة المتحدة",
                latitude = 51.5074,
                longitude = -0.1278
            ),
            SavedCity(
                id = "40.713_-74.006",
                nameEn = "New York",
                nameAr = "نيويورك",
                countryEn = "United States",
                countryAr = "الولايات المتحدة",
                latitude = 40.7128,
                longitude = -74.0060
            ),
            SavedCity(
                id = "35.676_139.650",
                nameEn = "Tokyo",
                nameAr = "طوكيو",
                countryEn = "Japan",
                countryAr = "اليابان",
                latitude = 35.6762,
                longitude = 139.6503
            )
        )

        fun isSameCoords(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Boolean {
            return abs(lat1 - lat2) < 0.01 && abs(lon1 - lon2) < 0.01
        }
    }
}
