package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.DailyForecast
import com.example.data.model.TemperatureUnit
import com.example.data.model.WindSpeedUnit
import com.example.ui.theme.RainBlue
import com.example.ui.theme.SunGold
import com.example.ui.utils.WeatherFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DailyForecastCard(
    daily: List<DailyForecast>,
    tempUnit: TemperatureUnit,
    windUnit: WindSpeedUnit,
    locale: Locale,
    modifier: Modifier = Modifier
) {
    if (daily.isEmpty()) return

    var expandedDate by remember { mutableStateOf<String?>(null) }
    val weekMin = daily.minOf { it.minTempC }
    val weekMax = daily.maxOf { it.maxTempC }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 18.dp, horizontal = 18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.section_daily),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(Modifier.height(10.dp))

            daily.forEachIndexed { index, day ->
                val isExpanded = expandedDate == day.dateIso
                val dayLabel = when (index) {
                    0 -> stringResource(R.string.today_label)
                    1 -> stringResource(R.string.tomorrow_label)
                    else -> WeatherFormatter.formatDayName(day.dateIso, locale)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            expandedDate = if (isExpanded) null else day.dateIso
                        }
                        .testTag("daily_row_$index")
                        .padding(vertical = 10.dp, horizontal = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1.25f)) {
                            Text(
                                text = dayLabel,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            Text(
                                text = WeatherFormatter.formatShortDate(day.dateIso, locale),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            modifier = Modifier.width(62.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            WeatherConditionGraphic(
                                weatherCode = day.weatherCode,
                                isDay = true,
                                modifier = Modifier.size(28.dp)
                            )
                            if (day.precipitationProbabilityMax >= 15) {
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = "${day.precipitationProbabilityMax}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RainBlue
                                )
                            }
                        }

                        Spacer(Modifier.width(8.dp))

                        Text(
                            text = WeatherFormatter.formatTemp(day.minTempC, tempUnit),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(34.dp)
                        )

                        TemperatureRangeBar(
                            dayMin = day.minTempC,
                            dayMax = day.maxTempC,
                            weekMin = weekMin,
                            weekMax = weekMax,
                            modifier = Modifier
                                .weight(1.1f)
                                .height(6.dp)
                        )

                        Spacer(Modifier.width(8.dp))

                        Text(
                            text = WeatherFormatter.formatTemp(day.maxTempC, tempUnit),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.width(34.dp)
                        )

                        Icon(
                            imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    AnimatedVisibility(visible = isExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(WeatherFormatter.weatherConditionResId(day.weatherCode)),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                DetailChip(
                                    icon = Icons.Rounded.WbSunny,
                                    tint = SunGold,
                                    label = "${stringResource(R.string.metric_uv)}: ${day.uvIndexMax.roundToInt()}"
                                )
                                DetailChip(
                                    icon = Icons.Rounded.Air,
                                    tint = MaterialTheme.colorScheme.primary,
                                    label = "${WeatherFormatter.toDisplayWind(day.windSpeedMaxKmh, windUnit)} " +
                                        stringResource(
                                            if (windUnit == WindSpeedUnit.KMH) R.string.unit_kmh else R.string.unit_mph
                                        )
                                )
                                DetailChip(
                                    icon = Icons.Rounded.WaterDrop,
                                    tint = RainBlue,
                                    label = "${day.precipitationSumMm} ${stringResource(R.string.unit_mm)}"
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${stringResource(R.string.sunrise_label)}: ${
                                        WeatherFormatter.formatClockTime(day.sunriseIso, locale)
                                    }",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${stringResource(R.string.sunset_label)}: ${
                                        WeatherFormatter.formatClockTime(day.sunsetIso, locale)
                                    }",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (index < daily.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun TemperatureRangeBar(
    dayMin: Double,
    dayMax: Double,
    weekMin: Double,
    weekMax: Double,
    modifier: Modifier = Modifier
) {
    val span = (weekMax - weekMin).coerceAtLeast(1.0)
    val startFrac = ((dayMin - weekMin) / span).toFloat().coerceIn(0f, 1f)
    val endFrac = ((dayMax - weekMin) / span).toFloat().coerceIn(startFrac + 0.08f, 1f)

    BoxWithConstraints(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val totalWidth = maxWidth
        val barOffset = totalWidth * startFrac
        val barWidth = (totalWidth * (endFrac - startFrac)).coerceAtLeast(8.dp)

        Box(
            modifier = Modifier
                .offset(x = barOffset)
                .width(barWidth)
                .height(6.dp)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFF4FA8FF), SunGold)
                    )
                )
        )
    }
}
