package com.example.ui.screen

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.components.CitiesSheet
import com.example.ui.components.DailyForecastCard
import com.example.ui.components.HeroWeatherCard
import com.example.ui.components.HourlyForecastCard
import com.example.ui.components.LanguagePickerDialog
import com.example.ui.components.SettingsSheet
import com.example.ui.components.WeatherMetricsGrid
import com.example.ui.viewmodel.LocationNotice
import com.example.ui.viewmodel.WeatherUiState
import com.example.ui.viewmodel.WeatherViewModel
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherDashboardScreen(
    viewModel: WeatherViewModel,
    locale: Locale,
    isDarkTheme: Boolean,
    isOpeningOverlayVisible: Boolean = false
) {
    val languageCode = locale.language.lowercase()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val savedCities by viewModel.savedCities.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val isLocating by viewModel.isLocating.collectAsStateWithLifecycle()
    val locationNotice by viewModel.locationNotice.collectAsStateWithLifecycle()
    val pendingResolution by viewModel.pendingLocationResolution.collectAsStateWithLifecycle()

    var showCitiesSheet by rememberSaveable { mutableStateOf(false) }
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var showLanguagePicker by rememberSaveable { mutableStateOf(false) }
    var showLocationRationaleDialog by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val activity = LocalView.current.context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasLocationPermission by remember { mutableStateOf(viewModel.hasLocationPermission()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasLocationPermission = viewModel.hasLocationPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPermission = granted || viewModel.hasLocationPermission()
        if (granted) {
            viewModel.detectCurrentLocation(activeLanguageCode = languageCode)
        } else {
            viewModel.notifyLocationDenied()
        }
    }

    val locationSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.detectCurrentLocation(activeLanguageCode = languageCode)
        } else {
            viewModel.notifyLocationDisabled()
        }
    }

    LaunchedEffect(pendingResolution) {
        val resolvable = pendingResolution ?: return@LaunchedEffect
        runCatching {
            val intentSenderRequest = IntentSenderRequest.Builder(resolvable.resolution).build()
            locationSettingsLauncher.launch(intentSenderRequest)
        }.onFailure {
            viewModel.notifyLocationDisabled()
        }
        viewModel.consumePendingLocationResolution()
    }

    fun requestLocationPermissionDirectly() {
        locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    fun triggerGpsLookup() {
        when {
            viewModel.hasLocationPermission() -> viewModel.detectCurrentLocation(activeLanguageCode = languageCode)
            activity != null && ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) -> showLocationRationaleDialog = true
            else -> requestLocationPermissionDirectly()
        }
    }

    LaunchedEffect(locationNotice) {
        val notice = locationNotice ?: return@LaunchedEffect
        val message = when (notice) {
            LocationNotice.DENIED -> context.getString(R.string.location_denied_message)
            LocationNotice.LOCATION_DISABLED -> context.getString(R.string.location_disabled_message)
            LocationNotice.UNAVAILABLE -> context.getString(R.string.location_unavailable_message)
        }
        val actionLabel = if (notice == LocationNotice.DENIED || notice == LocationNotice.LOCATION_DISABLED) {
            context.getString(R.string.open_settings)
        } else {
            null
        }
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = actionLabel,
            duration = SnackbarDuration.Long
        )
        if (result == SnackbarResult.ActionPerformed) {
            val intent = if (notice == LocationNotice.DENIED) {
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                )
            } else {
                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }
        viewModel.clearLocationNotice()
    }

    val activeCityTitle = when (val state = uiState) {
        is WeatherUiState.Success -> state.city.displayName(languageCode)
        is WeatherUiState.Error -> state.city?.displayName(languageCode)
        is WeatherUiState.Loading -> savedCities.firstOrNull { it.id == preferences.activeCityId }
            ?.displayName(languageCode)
    } ?: stringResource(R.string.app_name)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            DashboardTopBar(
                cityTitle = activeCityTitle,
                isLocating = isLocating,
                onCityClick = { showCitiesSheet = true },
                onLanguagePickerClick = { showLanguagePicker = true },
                onGpsClick = { triggerGpsLookup() },
                onSettingsClick = { showSettingsSheet = true }
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshWeather() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is WeatherUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = stringResource(R.string.loading_weather),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                is WeatherUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier
                                .widthIn(max = 360.dp)
                                .padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(52.dp)
                            )
                            Text(
                                text = stringResource(R.string.error_load_weather),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Button(
                                onClick = { viewModel.refreshWeather() },
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("retry_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }

                is WeatherUiState.Success -> {
                    val revealReady = !isOpeningOverlayVisible
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        val isExpanded = maxWidth >= 840.dp
                        val scrollState = rememberScrollState()

                        if (isExpanded) {
                            Row(
                                modifier = Modifier
                                    .widthIn(max = 1160.dp)
                                    .fillMaxSize()
                                    .verticalScroll(scrollState)
                                    .padding(horizontal = 24.dp, vertical = 12.dp)
                                    .navigationBarsPadding(),
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    AnimatedVisibility(
                                        visible = !hasLocationPermission &&
                                            !preferences.locationPromptDismissed
                                    ) {
                                        LocationPermissionPromptCard(
                                            onAllowClick = { triggerGpsLookup() },
                                            onDismissClick = { viewModel.dismissLocationPrompt() }
                                        )
                                    }
                                    StaggeredEntranceItem(visible = revealReady, delayMillis = 0) {
                                        HeroWeatherCard(
                                            city = state.city,
                                            bundle = state.bundle,
                                            tempUnit = preferences.temperatureUnit,
                                            locale = locale,
                                            isDarkTheme = isDarkTheme,
                                            isOffline = state.isOffline
                                        )
                                    }
                                    StaggeredEntranceItem(visible = revealReady, delayMillis = 120) {
                                        WeatherMetricsGrid(
                                            bundle = state.bundle,
                                            windUnit = preferences.windSpeedUnit,
                                            locale = locale
                                        )
                                    }
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    StaggeredEntranceItem(visible = revealReady, delayMillis = 60) {
                                        HourlyForecastCard(
                                            hourly = state.bundle.hourly,
                                            tempUnit = preferences.temperatureUnit,
                                            locale = locale
                                        )
                                    }
                                    StaggeredEntranceItem(visible = revealReady, delayMillis = 160) {
                                        DailyForecastCard(
                                            daily = state.bundle.daily,
                                            tempUnit = preferences.temperatureUnit,
                                            windUnit = preferences.windSpeedUnit,
                                            locale = locale
                                        )
                                    }
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .widthIn(max = 680.dp)
                                    .fillMaxWidth()
                                    .verticalScroll(scrollState)
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                                    .navigationBarsPadding(),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                AnimatedVisibility(
                                    visible = !hasLocationPermission &&
                                        !preferences.locationPromptDismissed
                                ) {
                                    LocationPermissionPromptCard(
                                        onAllowClick = { triggerGpsLookup() },
                                        onDismissClick = { viewModel.dismissLocationPrompt() }
                                    )
                                }
                                StaggeredEntranceItem(visible = revealReady, delayMillis = 0) {
                                    HeroWeatherCard(
                                        city = state.city,
                                        bundle = state.bundle,
                                        tempUnit = preferences.temperatureUnit,
                                        locale = locale,
                                        isDarkTheme = isDarkTheme,
                                        isOffline = state.isOffline
                                    )
                                }
                                StaggeredEntranceItem(visible = revealReady, delayMillis = 70) {
                                    HourlyForecastCard(
                                        hourly = state.bundle.hourly,
                                        tempUnit = preferences.temperatureUnit,
                                        locale = locale
                                    )
                                }
                                StaggeredEntranceItem(visible = revealReady, delayMillis = 140) {
                                    DailyForecastCard(
                                        daily = state.bundle.daily,
                                        tempUnit = preferences.temperatureUnit,
                                        windUnit = preferences.windSpeedUnit,
                                        locale = locale
                                    )
                                }
                                StaggeredEntranceItem(visible = revealReady, delayMillis = 210) {
                                    WeatherMetricsGrid(
                                        bundle = state.bundle,
                                        windUnit = preferences.windSpeedUnit,
                                        locale = locale
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCitiesSheet) {
        CitiesSheet(
            savedCities = savedCities,
            activeCityId = preferences.activeCityId,
            popularCities = viewModel.popularCities,
            searchQuery = searchQuery,
            searchResults = searchResults,
            isSearching = isSearching,
            isLocating = isLocating,
            tempUnit = preferences.temperatureUnit,
            languageCode = languageCode,
            onSearchQueryChange = { viewModel.updateSearchQuery(it, languageCode) },
            onSelectSavedCity = { cityId ->
                viewModel.selectCity(cityId)
                viewModel.clearSearch()
                showCitiesSheet = false
            },
            onDeleteCity = { viewModel.deleteCity(it) },
            onSelectGeocodedCity = { geo ->
                viewModel.addAndSelectGeocodedCity(geo)
                viewModel.clearSearch()
                showCitiesSheet = false
            },
            onAddPopularCity = { city ->
                viewModel.addAndSelectCity(city)
                viewModel.clearSearch()
                showCitiesSheet = false
            },
            onUseGpsClick = {
                triggerGpsLookup()
                showCitiesSheet = false
            },
            onDismiss = {
                viewModel.clearSearch()
                showCitiesSheet = false
            }
        )
    }

    if (showSettingsSheet) {
        SettingsSheet(
            preferences = preferences,
            onLanguageChange = { viewModel.setLanguage(it) },
            onThemeModeChange = { viewModel.setThemeMode(it) },
            onTempUnitChange = { viewModel.setTemperatureUnit(it) },
            onWindUnitChange = { viewModel.setWindSpeedUnit(it) },
            onDismiss = { showSettingsSheet = false }
        )
    }

    if (showLanguagePicker) {
        LanguagePickerDialog(
            selectedLanguage = preferences.language,
            onSelectLanguage = { viewModel.setLanguage(it) },
            onDismiss = { showLanguagePicker = false }
        )
    }

    if (showLocationRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showLocationRationaleDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.MyLocation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text(stringResource(R.string.location_prompt_title)) },
            text = { Text(stringResource(R.string.location_permission_rationale)) },
            confirmButton = {
                Button(
                    onClick = {
                        showLocationRationaleDialog = false
                        requestLocationPermissionDirectly()
                    },
                    modifier = Modifier.testTag("rationale_allow_button")
                ) {
                    Text(stringResource(R.string.allow_location))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLocationRationaleDialog = false },
                    modifier = Modifier.testTag("rationale_dismiss_button")
                ) {
                    Text(stringResource(R.string.not_now))
                }
            }
        )
    }
}

@Composable
private fun LocationPermissionPromptCard(
    onAllowClick: () -> Unit,
    onDismissClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("location_prompt_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MyLocation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.location_prompt_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = stringResource(R.string.location_permission_rationale),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismissClick,
                    modifier = Modifier.testTag("prompt_not_now_button")
                ) {
                    Text(stringResource(R.string.not_now))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onAllowClick,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("prompt_allow_location_button")
                ) {
                    Text(stringResource(R.string.allow_location))
                }
            }
        }
    }
}

@Composable
private fun StaggeredEntranceItem(
    visible: Boolean,
    delayMillis: Int,
    content: @Composable () -> Unit
) {
    var hasAnimated by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (hasAnimated) 1f else 0f) }

    LaunchedEffect(visible) {
        if (visible && !hasAnimated) {
            if (delayMillis > 0) delay(delayMillis.toLong())
            progress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.85f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            hasAnimated = true
        }
    }

    Box(
        modifier = Modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 28.dp.toPx()
        }
    ) {
        content()
    }
}

@Composable
private fun DashboardTopBar(
    cityTitle: String,
    isLocating: Boolean,
    onCityClick: () -> Unit,
    onLanguagePickerClick: () -> Unit,
    onGpsClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .weight(1f, fill = false)
                .minimumInteractiveComponentSize()
                .clip(CircleShape)
                .clickable(onClick = onCityClick)
                .testTag("city_selector_button"),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = stringResource(R.string.cd_open_cities),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = cityTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onLanguagePickerClick)
                    .testTag("quick_language_toggle"),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Language,
                        contentDescription = stringResource(R.string.setting_language),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.language_chip),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface
            ) {
                IconButton(
                    onClick = onGpsClick,
                    enabled = !isLocating,
                    modifier = Modifier.testTag("top_gps_button")
                ) {
                    if (isLocating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.MyLocation,
                            contentDescription = stringResource(R.string.cd_use_gps),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface
            ) {
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.testTag("settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = stringResource(R.string.cd_settings),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
