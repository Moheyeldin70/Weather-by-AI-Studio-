package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.HourlyForecast
import com.example.data.model.TemperatureUnit
import com.example.ui.theme.RainBlue
import com.example.ui.utils.WeatherFormatter
import java.util.Locale

@Composable
fun HourlyForecastCard(
    hourly: List<HourlyForecast>,
    tempUnit: TemperatureUnit,
    locale: Locale,
    modifier: Modifier = Modifier
) {
    if (hourly.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 18.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.section_hourly),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(Modifier.height(12.dp))

            HourlyTemperatureSparkline(
                temps = hourly.map { WeatherFormatter.toDisplayTemp(it.temperatureC, tempUnit).toFloat() },
                lineColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .padding(horizontal = 20.dp)
            )

            Spacer(Modifier.height(10.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(hourly) { index, item ->
                    val isNow = index == 0
                    val label = if (isNow) {
                        stringResource(R.string.now_label)
                    } else {
                        WeatherFormatter.formatHourLabel(item.timeIso, locale)
                    }

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (isNow) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .width(68.dp)
                                .padding(vertical = 12.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isNow) FontWeight.Bold else FontWeight.Medium,
                                color = if (isNow) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )

                            WeatherConditionGraphic(
                                weatherCode = item.weatherCode,
                                isDay = item.isDay,
                                modifier = Modifier.size(34.dp)
                            )

                            if (item.precipitationProbability >= 10) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.WaterDrop,
                                        contentDescription = null,
                                        tint = RainBlue,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = "${item.precipitationProbability}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = RainBlue
                                    )
                                }
                            } else {
                                Spacer(Modifier.height(14.dp))
                            }

                            Text(
                                text = WeatherFormatter.formatTemp(item.temperatureC, tempUnit),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HourlyTemperatureSparkline(
    temps: List<Float>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    if (temps.size < 2) return
    val minT = temps.minOrNull() ?: 0f
    val maxT = temps.maxOrNull() ?: 1f
    val span = (maxT - minT).coerceAtLeast(1f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val topPad = 6.dp.toPx()
        val bottomPad = 6.dp.toPx()
        val usableH = (h - topPad - bottomPad).coerceAtLeast(1f)

        val points = temps.mapIndexed { idx, t ->
            val x = (idx.toFloat() / (temps.size - 1).toFloat()) * w
            val yNorm = (t - minT) / span
            val y = topPad + usableH * (1f - yNorm)
            Offset(x, y)
        }

        val linePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val curr = points[i]
                val cx = (prev.x + curr.x) / 2f
                cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
            }
        }

        val fillPath = Path().apply {
            addPath(linePath)
            lineTo(points.last().x, h)
            lineTo(points.first().x, h)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.22f), Color.Transparent)
            )
        )
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )
        drawCircle(
            color = lineColor,
            radius = 4.dp.toPx(),
            center = points.first()
        )
    }
}
