package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.WeatherBundle
import com.example.data.model.WindSpeedUnit
import com.example.ui.theme.GaugeGood
import com.example.ui.theme.GaugeModerate
import com.example.ui.theme.GaugeSensitive
import com.example.ui.theme.GaugeUnhealthy
import com.example.ui.theme.RainBlue
import com.example.ui.theme.SunGold
import com.example.ui.utils.WeatherFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun WeatherMetricsGrid(
    bundle: WeatherBundle,
    windUnit: WindSpeedUnit,
    isArabic: Boolean,
    modifier: Modifier = Modifier
) {
    val current = bundle.current
    val today = bundle.daily.firstOrNull()
    val aqi = bundle.airQuality
    val windUnitStr = stringResource(if (windUnit == WindSpeedUnit.KMH) R.string.unit_kmh else R.string.unit_mph)
    val visUnitStr = stringResource(if (windUnit == WindSpeedUnit.KMH) R.string.unit_km else R.string.unit_mi)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.section_details),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        // Row 1: Air Quality + UV Index
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = stringResource(R.string.metric_aqi),
                icon = Icons.Rounded.Eco,
                iconTint = WeatherFormatter.aqiColor(aqi?.usAqi),
                modifier = Modifier.weight(1f)
            ) {
                val usAqi = aqi?.usAqi
                val badgeColor = WeatherFormatter.aqiColor(usAqi)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = usAqi?.toString() ?: "--",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = CircleShape,
                        color = badgeColor.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = stringResource(WeatherFormatter.aqiLabelResId(usAqi)),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                GradientLevelBar(
                    fraction = ((usAqi ?: 20) / 250f).coerceIn(0.05f, 1f),
                    colors = listOf(GaugeGood, GaugeModerate, GaugeSensitive, GaugeUnhealthy)
                )
                Spacer(Modifier.height(8.dp))
                val pm25Text = aqi?.pm25?.let { String.format(Locale.US, "%.1f", it) } ?: "--"
                Text(
                    text = stringResource(R.string.pm25_label, pm25Text),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            MetricCard(
                title = stringResource(R.string.metric_uv),
                icon = Icons.Rounded.WbSunny,
                iconTint = WeatherFormatter.uvColor(current.uvIndex),
                modifier = Modifier.weight(1f)
            ) {
                val uvColor = WeatherFormatter.uvColor(current.uvIndex)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = current.uvIndex.roundToInt().toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = CircleShape,
                        color = uvColor.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = stringResource(WeatherFormatter.uvLabelResId(current.uvIndex)),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = uvColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                GradientLevelBar(
                    fraction = (current.uvIndex.toFloat() / 11f).coerceIn(0.05f, 1f),
                    colors = listOf(GaugeGood, GaugeModerate, GaugeSensitive, GaugeUnhealthy)
                )
                Spacer(Modifier.height(8.dp))
                val maxUv = today?.uvIndexMax?.roundToInt() ?: current.uvIndex.roundToInt()
                Text(
                    text = "${stringResource(R.string.today_label)}: $maxUv",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Row 2: Wind + Sun Path
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = stringResource(R.string.metric_wind),
                icon = Icons.Rounded.Air,
                iconTint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${WeatherFormatter.toDisplayWind(current.windSpeedKmh, windUnit)}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = " $windUnitStr",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        Text(
                            text = stringResource(WeatherFormatter.windDirectionResId(current.windDirectionDeg)),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    WindCompassDial(
                        directionDeg = current.windDirectionDeg,
                        dialColor = MaterialTheme.colorScheme.outline,
                        needleColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(46.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
                val gustsText = "${WeatherFormatter.toDisplayWind(current.windGustsKmh, windUnit)} $windUnitStr"
                Text(
                    text = stringResource(R.string.wind_gusts, gustsText),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            MetricCard(
                title = stringResource(R.string.metric_sunrise_sunset),
                icon = Icons.Rounded.WbTwilight,
                iconTint = SunGold,
                modifier = Modifier.weight(1f)
            ) {
                val sunriseIso = today?.sunriseIso.orEmpty()
                val sunsetIso = today?.sunsetIso.orEmpty()
                val progress = WeatherFormatter.computeSunProgress(
                    currentIso = current.timeIso,
                    sunriseIso = sunriseIso,
                    sunsetIso = sunsetIso
                )

                SunArcGraphic(
                    progress = progress,
                    isDay = current.isDay,
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                    arcColor = SunGold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.sunrise_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = WeatherFormatter.formatClockTime(sunriseIso, isArabic),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = stringResource(R.string.sunset_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = WeatherFormatter.formatClockTime(sunsetIso, isArabic),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Row 3: Humidity + Pressure & Visibility
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = stringResource(R.string.metric_humidity),
                icon = Icons.Rounded.WaterDrop,
                iconTint = RainBlue,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "${current.humidityPercent}%",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                GradientLevelBar(
                    fraction = (current.humidityPercent / 100f).coerceIn(0.05f, 1f),
                    colors = listOf(Color(0xFF7AD7EA), RainBlue)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(WeatherFormatter.humidityComfortResId(current.humidityPercent)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            MetricCard(
                title = stringResource(R.string.metric_pressure),
                icon = Icons.Rounded.Compress,
                iconTint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${current.pressureHpa.roundToInt()}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = " ${stringResource(R.string.unit_hpa)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Visibility,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${stringResource(R.string.metric_visibility)}: ${
                            WeatherFormatter.formatVisibilityValue(current.visibilityMeters, windUnit)
                        } $visUnitStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.cloud_cover_label, current.cloudCoverPercent),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun GradientLevelBar(
    fraction: Float,
    colors: List<Color>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0.05f, 1f))
                .height(6.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(colors))
        )
    }
}

@Composable
private fun WindCompassDial(
    directionDeg: Int,
    dialColor: Color,
    needleColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.44f
        drawCircle(
            color = dialColor.copy(alpha = 0.45f),
            radius = radius,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
        // Arrow pointing in wind direction (0 = North/up)
        val rad = ((directionDeg - 90) * PI / 180.0)
        val tip = Offset(
            center.x + (radius * 0.72f * cos(rad)).toFloat(),
            center.y + (radius * 0.72f * sin(rad)).toFloat()
        )
        val tail = Offset(
            center.x - (radius * 0.55f * cos(rad)).toFloat(),
            center.y - (radius * 0.55f * sin(rad)).toFloat()
        )
        drawLine(
            color = needleColor,
            start = tail,
            end = tip,
            strokeWidth = 2.6.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(
            color = needleColor,
            radius = 3.5.dp.toPx(),
            center = tip
        )
    }
}

@Composable
private fun SunArcGraphic(
    progress: Float,
    isDay: Boolean,
    trackColor: Color,
    arcColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val pad = 8.dp.toPx()
        val arcW = (w - pad * 2f).coerceAtLeast(10f)
        val arcH = (h - pad).coerceAtLeast(10f) * 2f
        val topLeft = Offset(pad, h - arcH / 2f - 4.dp.toPx())

        // Horizon baseline
        drawLine(
            color = trackColor,
            start = Offset(0f, h - 4.dp.toPx()),
            end = Offset(w, h - 4.dp.toPx()),
            strokeWidth = 1.5.dp.toPx()
        )

        // Dashed full arc
        drawArc(
            color = trackColor,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = topLeft,
            size = Size(arcW, arcH),
            style = Stroke(
                width = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
            )
        )

        // Active sun arc
        drawArc(
            color = arcColor,
            startAngle = 180f,
            sweepAngle = 180f * progress,
            useCenter = false,
            topLeft = topLeft,
            size = Size(arcW, arcH),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )

        if (isDay) {
            val angleRad = (180f + 180f * progress) * PI / 180.0
            val cx = topLeft.x + arcW / 2f
            val cy = topLeft.y + arcH / 2f
            val sunPos = Offset(
                cx + ((arcW / 2f) * cos(angleRad)).toFloat(),
                cy + ((arcH / 2f) * sin(angleRad)).toFloat()
            )
            drawCircle(
                color = arcColor.copy(alpha = 0.28f),
                radius = 9.dp.toPx(),
                center = sunPos
            )
            drawCircle(
                color = arcColor,
                radius = 5.dp.toPx(),
                center = sunPos
            )
        }
    }
}
