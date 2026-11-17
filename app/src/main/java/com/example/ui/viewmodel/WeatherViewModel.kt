package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.PreferencesManager
import com.example.data.local.WeatherDatabase
import com.example.data.model.AppLanguage
import com.example.data.model.GeocodingCity
import com.example.data.model.SavedCity
import com.example.data.model.TemperatureUnit
import com.example.data.model.ThemeMode
import com.example.data.model.UserPreferences
import com.example.data.model.WeatherBundle
import com.example.data.model.WindSpeedUnit
import com.example.data.repository.LocationFetchResult
import com.example.data.repository.LocationProvider
import com.example.data.repository.WeatherRepository
import com.example.data.repository.WeatherResult
import com.google.android.gms.common.api.ResolvableApiException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface WeatherUiState {
    data object Loading : WeatherUiState
    data class Success(
        val city: SavedCity,
        val bundle: WeatherBundle,
        val isOffline: Boolean = false
    ) : WeatherUiState

    data class Error(
        val city: SavedCity?,
        val message: String?
    ) : WeatherUiState
}

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private val database = WeatherDatabase.getInstance(application)
    private val preferencesManager = PreferencesManager(application)
    private val locationProvider = LocationProvider(application)
    private val repository = WeatherRepository(
        savedCityDao = database.savedCityDao(),
        cachedForecastDao = database.cachedForecastDao()
    )

    val preferences: StateFlow<UserPreferences> = preferencesManager.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = UserPreferences()
        )

    val savedCities: StateFlow<List<SavedCity>> = repository.savedCitiesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<GeocodingCity>>(emptyList())
    val searchResults: StateFlow<List<GeocodingCity>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isLocating = MutableStateFlow(false)
    val isLocating: StateFlow<Boolean> = _isLocating.asStateFlow()

    private val _locationNotice = MutableStateFlow<LocationNotice?>(null)
    val locationNotice: StateFlow<LocationNotice?> = _locationNotice.asStateFlow()

    private val _pendingLocationResolution = MutableStateFlow<ResolvableApiException?>(null)
    val pendingLocationResolution: StateFlow<ResolvableApiException?> =
        _pendingLocationResolution.asStateFlow()

    val popularCities: List<SavedCity> = repository.getSuggestedPopularCities()

    private var searchJob: Job? = null
    private var weatherJob: Job? = null

    init {
        viewModelScope.launch {
            val prefs = preferencesManager.preferencesFlow.first()
            val shouldAutoDetectGps = locationProvider.hasLocationPermission() &&
                (prefs.activeCityId == null || prefs.activeCityId == WeatherRepository.CURRENT_LOCATION_ID)

            val initialCity = prefs.activeCityId?.let { repository.getCityById(it) }
                ?: repository.ensureDefaultCity()
            preferencesManager.setActiveCityId(initialCity.id)
            loadWeather(initialCity, forceRefresh = false)

            if (shouldAutoDetectGps) {
                detectCurrentLocation(silentIfUnavailable = true)
            }
        }
    }

    fun hasLocationPermission(): Boolean = locationProvider.hasLocationPermission()

    fun refreshWeather() {
        val currentCity = when (val state = _uiState.value) {
            is WeatherUiState.Success -> state.city
            is WeatherUiState.Error -> state.city
            is WeatherUiState.Loading -> null
        } ?: return

        viewModelScope.launch {
            _isRefreshing.value = true
            loadWeather(currentCity, forceRefresh = true)
            _isRefreshing.value = false
        }
    }

    fun selectCity(cityId: String) {
        viewModelScope.launch {
            val city = repository.getCityById(cityId) ?: return@launch
            preferencesManager.setActiveCityId(city.id)
            loadWeather(city, forceRefresh = false)
        }
    }

    fun addAndSelectCity(city: SavedCity) {
        viewModelScope.launch {
            repository.saveCity(city)
            preferencesManager.setActiveCityId(city.id)
            loadWeather(city, forceRefresh = true)
        }
    }

    fun addAndSelectGeocodedCity(geo: GeocodingCity) {
        val saved = SavedCity(
            id = geo.id,
            nameEn = geo.nameEn,
            nameAr = geo.nameAr,
            countryEn = geo.countryEn,
            countryAr = geo.countryAr,
            admin1 = geo.admin1,
            latitude = geo.latitude,
            longitude = geo.longitude,
            isCurrentLocation = false,
            updatedAt = System.currentTimeMillis()
        )
        addAndSelectCity(saved)
    }

    fun deleteCity(cityId: String) {
        viewModelScope.launch {
            val activeId = preferences.value.activeCityId
            repository.deleteCity(cityId)
            if (activeId == cityId) {
                val fallback = repository.ensureDefaultCity()
                preferencesManager.setActiveCityId(fallback.id)
                loadWeather(fallback, forceRefresh = false)
            }
        }
    }

    fun detectCurrentLocation(silentIfUnavailable: Boolean = false) {
        viewModelScope.launch {
            _isLocating.value = true
            val result = locationProvider.fetchCurrentLocation()
            _isLocating.value = false
            when (result) {
                is LocationFetchResult.Success -> {
                    preferencesManager.setLocationPromptDismissed(true)
                    val gpsCity = repository.upsertCurrentGpsCity(result.location)
                    preferencesManager.setActiveCityId(gpsCity.id)
                    loadWeather(gpsCity, forceRefresh = true)
                }

                is LocationFetchResult.ResolutionRequired -> {
                    if (!silentIfUnavailable) {
                        _pendingLocationResolution.value = result.exception
                    }
                }

                is LocationFetchResult.LocationServicesDisabled -> {
                    if (!silentIfUnavailable) {
                        _locationNotice.value = LocationNotice.LOCATION_DISABLED
                    }
                }

                is LocationFetchResult.PermissionMissing -> {
                    if (!silentIfUnavailable) {
                        _locationNotice.value = LocationNotice.DENIED
                    }
                }

                is LocationFetchResult.Unavailable -> {
                    if (!silentIfUnavailable) {
                        _locationNotice.value = LocationNotice.UNAVAILABLE
                    }
                }
            }
        }
    }

    fun consumePendingLocationResolution() {
        _pendingLocationResolution.value = null
    }

    fun dismissLocationPrompt() {
        viewModelScope.launch {
            preferencesManager.setLocationPromptDismissed(true)
        }
    }

    fun notifyLocationDenied() {
        _locationNotice.value = LocationNotice.DENIED
    }

    fun notifyLocationDisabled() {
        _locationNotice.value = LocationNotice.LOCATION_DISABLED
    }

    fun clearLocationNotice() {
        _locationNotice.value = null
    }

    fun updateSearchQuery(query: String, isArabicUi: Boolean) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            delay(320)
            val results = repository.searchCities(query, isArabicUi)
            _searchResults.value = results
            _isSearching.value = false
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _isSearching.value = false
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { preferencesManager.setLanguage(language) }
    }

    fun toggleLanguageQuickly(currentlyArabic: Boolean) {
        val next = if (currentlyArabic) AppLanguage.ENGLISH else AppLanguage.ARABIC
        setLanguage(next)
    }

    fun setThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch { preferencesManager.setThemeMode(themeMode) }
    }

    fun setTemperatureUnit(unit: TemperatureUnit) {
        viewModelScope.launch { preferencesManager.setTemperatureUnit(unit) }
    }

    fun setWindSpeedUnit(unit: WindSpeedUnit) {
        viewModelScope.launch { preferencesManager.setWindSpeedUnit(unit) }
    }

    private fun loadWeather(city: SavedCity, forceRefresh: Boolean) {
        weatherJob?.cancel()
        weatherJob = viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is WeatherUiState.Success || currentState.city.id != city.id) {
                _uiState.value = WeatherUiState.Loading
            }
            when (val result = repository.getWeatherForCity(city, forceRefresh = forceRefresh)) {
                is WeatherResult.Live -> {
                    val updatedCity = repository.getCityById(city.id) ?: city
                    _uiState.value = WeatherUiState.Success(
                        city = updatedCity,
                        bundle = result.bundle,
                        isOffline = false
                    )
                }

                is WeatherResult.CachedOffline -> {
                    val updatedCity = repository.getCityById(city.id) ?: city
                    _uiState.value = WeatherUiState.Success(
                        city = updatedCity,
                        bundle = result.bundle,
                        isOffline = true
                    )
                }

                is WeatherResult.Error -> {
                    _uiState.value = WeatherUiState.Error(
                        city = city,
                        message = result.message
                    )
                }
            }
        }
    }
}

enum class LocationNotice {
    DENIED,
    LOCATION_DISABLED,
    UNAVAILABLE
}
