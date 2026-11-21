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
        savedCityDao.observeCities().map { entities ->
            entities.map { entity -> enrichPopularTranslations(entity.toDomain()) }
        }

    suspend fun ensureDefaultCity(): SavedCity = withContext(Dispatchers.IO) {
        val existing = savedCityDao.getCitiesOnce()
        if (existing.isNotEmpty()) {
            return@withContext enrichPopularTranslations(existing.first().toDomain())
        }
        val defaultCity = popularCities.first()
        savedCityDao.upsertCity(SavedCityEntity.fromDomain(defaultCity))
        defaultCity
    }

    suspend fun getCityById(cityId: String): SavedCity? = withContext(Dispatchers.IO) {
        savedCityDao.getCityById(cityId)?.toDomain()?.let { enrichPopularTranslations(it) }
    }

    suspend fun saveCity(city: SavedCity) = withContext(Dispatchers.IO) {
        savedCityDao.upsertCity(SavedCityEntity.fromDomain(enrichPopularTranslations(city)))
    }

    suspend fun upsertCurrentGpsCity(detected: DetectedLocation): SavedCity = withContext(Dispatchers.IO) {
        val city = SavedCity(
            id = CURRENT_LOCATION_ID,
            nameEn = detected.nameEn,
            nameAr = detected.nameAr,
            countryEn = detected.countryEn,
            countryAr = detected.countryAr,
            localizedNames = detected.localizedNames,
            localizedCountries = detected.localizedCountries,
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

        val currentHourPrefix = currentDto.time.take(13)
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

    suspend fun searchCities(query: String, languageCode: String): List<GeocodingCity> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.length < 2) return@withContext emptyList()

            val primaryLang = languageCode.lowercase().ifBlank { "en" }
            val secondaryLang = if (primaryLang == "en") "ar" else "en"

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
                    val nameAr = if (primaryLang == "ar") item.name else (if (secondaryLang == "ar") alt?.name ?: item.name else item.name)
                    val countryEn = if (primaryLang == "en") (item.country ?: "") else (alt?.country ?: item.country ?: "")
                    val countryAr = if (primaryLang == "ar") (item.country ?: "") else (if (secondaryLang == "ar") alt?.country ?: (item.country ?: "") else (item.country ?: ""))
                    val cityId = String.format(Locale.US, "%.3f_%.3f", item.latitude, item.longitude)

                    val localizedNames = buildMap {
                        put(primaryLang, item.name)
                        put("en", nameEn)
                        if (primaryLang == "ar" || secondaryLang == "ar") put("ar", nameAr)
                    }
                    val localizedCountries = buildMap {
                        if (!item.country.isNullOrBlank()) put(primaryLang, item.country)
                        if (countryEn.isNotBlank()) put("en", countryEn)
                        if ((primaryLang == "ar" || secondaryLang == "ar") && countryAr.isNotBlank()) put("ar", countryAr)
                    }

                    GeocodingCity(
                        id = cityId,
                        name = item.name,
                        nameEn = nameEn,
                        nameAr = nameAr,
                        country = item.country ?: "",
                        countryEn = countryEn,
                        countryAr = countryAr,
                        localizedNames = localizedNames,
                        localizedCountries = localizedCountries,
                        admin1 = item.admin1,
                        latitude = item.latitude,
                        longitude = item.longitude,
                        countryCode = item.countryCode
                    )
                }.distinctBy {
                    "${(it.latitude * 100).toInt()}_${(it.longitude * 100).toInt()}_${it.nameEn}"
                }
            }
        }

    fun getSuggestedPopularCities(): List<SavedCity> = popularCities

    private fun enrichPopularTranslations(city: SavedCity): SavedCity {
        val popularMatch = popularCities.firstOrNull { it.id == city.id } ?: return city
        return city.copy(
            localizedNames = popularMatch.localizedNames + city.localizedNames,
            localizedCountries = popularMatch.localizedCountries + city.localizedCountries
        )
    }

    companion object {
        const val CURRENT_LOCATION_ID = "current_gps"
        private const val CACHE_FRESH_MS = 10 * 60 * 1000L

        val popularCities = listOf(
            SavedCity(
                id = "30.044_31.236",
                nameEn = "Cairo",
                nameAr = "القاهرة",
                countryEn = "Egypt",
                countryAr = "مصر",
                localizedNames = mapOf(
                    "en" to "Cairo", "ar" to "القاهرة", "ja" to "カイロ", "zh" to "开罗",
                    "fr" to "Le Caire", "es" to "El Cairo", "de" to "Kairo", "it" to "Il Cairo",
                    "pt" to "Cairo", "ko" to "카이로", "ru" to "Каир", "tr" to "Kahire"
                ),
                localizedCountries = mapOf(
                    "en" to "Egypt", "ar" to "مصر", "ja" to "エジプト", "zh" to "埃及",
                    "fr" to "Égypte", "es" to "Egipto", "de" to "Ägypten", "it" to "Egitto",
                    "pt" to "Egito", "ko" to "이집트", "ru" to "Египет", "tr" to "Mısır"
                ),
                latitude = 30.0444,
                longitude = 31.2357
            ),
            SavedCity(
                id = "24.714_46.675",
                nameEn = "Riyadh",
                nameAr = "الرياض",
                countryEn = "Saudi Arabia",
                countryAr = "المملكة العربية السعودية",
                localizedNames = mapOf(
                    "en" to "Riyadh", "ar" to "الرياض", "ja" to "リヤド", "zh" to "利雅得",
                    "fr" to "Riyad", "es" to "Riad", "de" to "Riad", "it" to "Riyad",
                    "pt" to "Riade", "ko" to "리야드", "ru" to "Эр-Рияд", "tr" to "Riyad"
                ),
                localizedCountries = mapOf(
                    "en" to "Saudi Arabia", "ar" to "السعودية", "ja" to "サウジアラビア", "zh" to "沙特阿拉伯",
                    "fr" to "Arabie saoudite", "es" to "Arabia Saudita", "de" to "Saudi-Arabien",
                    "it" to "Arabia Saudita", "pt" to "Arábia Saudita", "ko" to "사우디아라비아",
                    "ru" to "Саудовская Аравия", "tr" to "Suudi Arabistan"
                ),
                latitude = 24.7136,
                longitude = 46.6753
            ),
            SavedCity(
                id = "25.205_55.271",
                nameEn = "Dubai",
                nameAr = "دبي",
                countryEn = "United Arab Emirates",
                countryAr = "الإمارات العربية المتحدة",
                localizedNames = mapOf(
                    "en" to "Dubai", "ar" to "دبي", "ja" to "ドバイ", "zh" to "迪拜",
                    "fr" to "Dubaï", "es" to "Dubái", "de" to "Dubai", "it" to "Dubai",
                    "pt" to "Dubai", "ko" to "두바이", "ru" to "Дубай", "tr" to "Dubai"
                ),
                localizedCountries = mapOf(
                    "en" to "United Arab Emirates", "ar" to "الإمارات", "ja" to "アラブ首長国連邦",
                    "zh" to "阿联酋", "fr" to "Émirats arabes unis", "es" to "Emiratos Árabes Unidos",
                    "de" to "Vereinigte Arabische Emirate", "it" to "Emirati Arabi Uniti",
                    "pt" to "Emirados Árabes Unidos", "ko" to "아랍에미리트", "ru" to "ОАЭ", "tr" to "BAE"
                ),
                latitude = 25.2048,
                longitude = 55.2708
            ),
            SavedCity(
                id = "51.507_-0.128",
                nameEn = "London",
                nameAr = "لندن",
                countryEn = "United Kingdom",
                countryAr = "المملكة المتحدة",
                localizedNames = mapOf(
                    "en" to "London", "ar" to "لندن", "ja" to "ロンドン", "zh" to "伦敦",
                    "fr" to "Londres", "es" to "Londres", "de" to "London", "it" to "Londra",
                    "pt" to "Londres", "ko" to "런던", "ru" to "Лондон", "tr" to "Londra"
                ),
                localizedCountries = mapOf(
                    "en" to "United Kingdom", "ar" to "المملكة المتحدة", "ja" to "イギリス", "zh" to "英国",
                    "fr" to "Royaume-Uni", "es" to "Reino Unido", "de" to "Vereinigtes Königreich",
                    "it" to "Regno Unito", "pt" to "Reino Unido", "ko" to "영국",
                    "ru" to "Великобритания", "tr" to "Birleşik Krallık"
                ),
                latitude = 51.5074,
                longitude = -0.1278
            ),
            SavedCity(
                id = "48.857_2.352",
                nameEn = "Paris",
                nameAr = "باريس",
                countryEn = "France",
                countryAr = "فرنسا",
                localizedNames = mapOf(
                    "en" to "Paris", "ar" to "باريس", "ja" to "パリ", "zh" to "巴黎",
                    "fr" to "Paris", "es" to "París", "de" to "Paris", "it" to "Parigi",
                    "pt" to "Paris", "ko" to "파리", "ru" to "Париж", "tr" to "Paris"
                ),
                localizedCountries = mapOf(
                    "en" to "France", "ar" to "فرنسا", "ja" to "フランス", "zh" to "法国",
                    "fr" to "France", "es" to "Francia", "de" to "Frankreich", "it" to "Francia",
                    "pt" to "França", "ko" to "프랑스", "ru" to "Франция", "tr" to "Fransa"
                ),
                latitude = 48.8566,
                longitude = 2.3522
            ),
            SavedCity(
                id = "40.713_-74.006",
                nameEn = "New York",
                nameAr = "نيويورك",
                countryEn = "United States",
                countryAr = "الولايات المتحدة",
                localizedNames = mapOf(
                    "en" to "New York", "ar" to "نيويورك", "ja" to "ニューヨーク", "zh" to "纽约",
                    "fr" to "New York", "es" to "Nueva York", "de" to "New York", "it" to "New York",
                    "pt" to "Nova Iorque", "ko" to "뉴욕", "ru" to "Нью-Йорк", "tr" to "New York"
                ),
                localizedCountries = mapOf(
                    "en" to "United States", "ar" to "الولايات المتحدة", "ja" to "アメリカ", "zh" to "美国",
                    "fr" to "États-Unis", "es" to "Estados Unidos", "de" to "Vereinigte Staaten",
                    "it" to "Stati Uniti", "pt" to "Estados Unidos", "ko" to "미국",
                    "ru" to "США", "tr" to "ABD"
                ),
                latitude = 40.7128,
                longitude = -74.0060
            ),
            SavedCity(
                id = "35.676_139.650",
                nameEn = "Tokyo",
                nameAr = "طوكيو",
                countryEn = "Japan",
                countryAr = "اليابان",
                localizedNames = mapOf(
                    "en" to "Tokyo", "ar" to "طوكيو", "ja" to "東京", "zh" to "东京",
                    "fr" to "Tokyo", "es" to "Tokio", "de" to "Tokio", "it" to "Tokyo",
                    "pt" to "Tóquio", "ko" to "도쿄", "ru" to "Токио", "tr" to "Tokyo"
                ),
                localizedCountries = mapOf(
                    "en" to "Japan", "ar" to "اليابان", "ja" to "日本", "zh" to "日本",
                    "fr" to "Japon", "es" to "Japón", "de" to "Japan", "it" to "Giappone",
                    "pt" to "Japão", "ko" to "일본", "ru" to "Япония", "tr" to "Japonya"
                ),
                latitude = 35.6762,
                longitude = 139.6503
            ),
            SavedCity(
                id = "52.520_13.405",
                nameEn = "Berlin",
                nameAr = "برلين",
                countryEn = "Germany",
                countryAr = "ألمانيا",
                localizedNames = mapOf(
                    "en" to "Berlin", "ar" to "برلين", "ja" to "ベルリン", "zh" to "柏林",
                    "fr" to "Berlin", "es" to "Berlín", "de" to "Berlin", "it" to "Berlino",
                    "pt" to "Berlim", "ko" to "베를린", "ru" to "Берлин", "tr" to "Berlin"
                ),
                localizedCountries = mapOf(
                    "en" to "Germany", "ar" to "ألمانيا", "ja" to "ドイツ", "zh" to "德国",
                    "fr" to "Allemagne", "es" to "Alemania", "de" to "Deutschland", "it" to "Germania",
                    "pt" to "Alemanha", "ko" to "독일", "ru" to "Германия", "tr" to "Almanya"
                ),
                latitude = 52.5200,
                longitude = 13.4050
            )
        )
    }
}
