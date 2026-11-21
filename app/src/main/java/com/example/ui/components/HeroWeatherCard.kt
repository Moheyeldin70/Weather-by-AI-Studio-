package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.SavedCity
import com.example.data.model.TemperatureUnit
import com.example.data.model.WeatherBundle
import com.example.ui.utils.WeatherFormatter
import com.example.ui.utils.WeatherMood
import java.util.Locale

@Composable
fun HeroWeatherCard(
    city: SavedCity,
    bundle: WeatherBundle,
    tempUnit: TemperatureUnit,
    locale: Locale,
    isDarkTheme: Boolean,
    isOffline: Boolean,
    modifier: Modifier = Modifier
) {
    val current = bundle.current
    val today = bundle.daily.firstOrNull()
    val mood = WeatherFormatter.weatherMood(current.weatherCode, current.isDay)
    val gradientColors = heroSkyGradient(mood = mood, isDarkTheme = isDarkTheme)

    val languageCode = locale.language.lowercase()
    val highText = today?.let { WeatherFormatter.formatTemp(it.maxTempC, tempUnit) } ?: "--°"
    val lowText = today?.let { WeatherFormatter.formatTemp(it.minTempC, tempUnit) } ?: "--°"
    val feelsText = WeatherFormatter.formatTemp(current.apparentTemperatureC, tempUnit)
    val updatedClock = WeatherFormatter.formatUpdatedClock(bundle.fetchedAtMillis, locale)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(gradientColors))
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_sky_atmosphere_1794929301461),
            contentDescription = stringResource(R.string.cd_hero_sky),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .alpha(if (isDarkTheme) 0.14f else 0.24f)
        )

        // Subtle scrim to ensure crisp WCAG contrast for white hero text
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.08f),
                            Color.Black.copy(alpha = 0.28f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 22.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (city.isCurrentLocation) {
                            Icon(
                                imageVector = Icons.Rounded.MyLocation,
                                contentDescription = stringResource(R.string.current_location_badge),
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = city.displayName(languageCode),
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    val country = city.displayCountry(languageCode)
                    if (country.isNotBlank()) {
                        Text(
                            text = country,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.18f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isOffline) {
                            Icon(
                                imageVector = Icons.Rounded.CloudOff,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text = if (isOffline) {
                                stringResource(R.string.offline_cached)
                            } else {
                                stringResource(R.string.updated_time, updatedClock)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = WeatherFormatter.formatTemp(current.temperatureC, tempUnit),
                        style = MaterialTheme.typography.displayLarge,
                        color = Color.White
                    )
                    Text(
                        text = stringResource(WeatherFormatter.weatherConditionResId(current.weatherCode)),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                }

                WeatherConditionGraphic(
                    weatherCode = current.weatherCode,
                    isDay = current.isDay,
                    modifier = Modifier.size(96.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.18f)
                ) {
                    Text(
                        text = stringResource(R.string.high_low, highText, lowText),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.18f)
                ) {
                    Text(
                        text = stringResource(R.string.feels_like, feelsText),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

private fun heroSkyGradient(mood: WeatherMood, isDarkTheme: Boolean): List<Color> {
    if (isDarkTheme) {
        return when (mood) {
            WeatherMood.CLEAR_DAY -> listOf(Color(0xFF103F79), Color(0xFF1A60A8))
            WeatherMood.CLEAR_NIGHT -> listOf(Color(0xFF0A162D), Color(0xFF172C52))
            WeatherMood.PARTLY_CLOUDY_DAY -> listOf(Color(0xFF163D6B), Color(0xFF28598A))
            WeatherMood.PARTLY_CLOUDY_NIGHT -> listOf(Color(0xFF0E1B33), Color(0xFF1F3354))
            WeatherMood.CLOUDY, WeatherMood.FOG -> listOf(Color(0xFF1E2B3A), Color(0xFF33475B))
            WeatherMood.RAIN -> listOf(Color(0xFF12263F), Color(0xFF204166))
            WeatherMood.SNOW -> listOf(Color(0xFF1D354F), Color(0xFF35587A))
            WeatherMood.THUNDERSTORM -> listOf(Color(0xFF181936), Color(0xFF302B63))
        }
    }
    return when (mood) {
        WeatherMood.CLEAR_DAY -> listOf(Color(0xFF1368C4), Color(0xFF42A1F5))
        WeatherMood.CLEAR_NIGHT -> listOf(Color(0xFF13264D), Color(0xFF26457C))
        WeatherMood.PARTLY_CLOUDY_DAY -> listOf(Color(0xFF226BB4), Color(0xFF5FA2DB))
        WeatherMood.PARTLY_CLOUDY_NIGHT -> listOf(Color(0xFF1B2E52), Color(0xFF344F7A))
        WeatherMood.CLOUDY, WeatherMood.FOG -> listOf(Color(0xFF48607A), Color(0xFF6B849E))
        WeatherMood.RAIN -> listOf(Color(0xFF244973), Color(0xFF3D6F9E))
        WeatherMood.SNOW -> listOf(Color(0xFF3B668E), Color(0xFF6A9AC4))
        WeatherMood.THUNDERSTORM -> listOf(Color(0xFF252754), Color(0xFF483E80))
    }
}
