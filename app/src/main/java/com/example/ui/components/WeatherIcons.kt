package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.ui.theme.RainBlue
import com.example.ui.theme.SnowIce
import com.example.ui.theme.StormViolet
import com.example.ui.theme.SunGold
import com.example.ui.theme.SunWarmGlow
import com.example.ui.utils.WeatherFormatter
import com.example.ui.utils.WeatherMood
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WeatherConditionGraphic(
    weatherCode: Int,
    isDay: Boolean,
    modifier: Modifier = Modifier,
    cloudLightColor: Color = Color.White,
    cloudShadowColor: Color = Color(0xFFCBD8E8)
) {
    val mood = WeatherFormatter.weatherMood(weatherCode, isDay)

    Canvas(modifier = modifier) {
        when (mood) {
            WeatherMood.CLEAR_DAY -> drawSun(
                center = Offset(size.width * 0.5f, size.height * 0.5f),
                radius = size.minDimension * 0.25f,
                drawRays = true
            )

            WeatherMood.CLEAR_NIGHT -> drawCrescentMoon(
                center = Offset(size.width * 0.5f, size.height * 0.52f),
                radius = size.minDimension * 0.28f
            )

            WeatherMood.PARTLY_CLOUDY_DAY -> {
                drawSun(
                    center = Offset(size.width * 0.64f, size.height * 0.34f),
                    radius = size.minDimension * 0.18f,
                    drawRays = true
                )
                drawCloud(
                    origin = Offset(size.width * 0.14f, size.height * 0.38f),
                    width = size.width * 0.68f,
                    height = size.height * 0.40f,
                    topColor = cloudLightColor,
                    bottomColor = cloudShadowColor
                )
            }

            WeatherMood.PARTLY_CLOUDY_NIGHT -> {
                drawCrescentMoon(
                    center = Offset(size.width * 0.66f, size.height * 0.34f),
                    radius = size.minDimension * 0.18f
                )
                drawCloud(
                    origin = Offset(size.width * 0.14f, size.height * 0.38f),
                    width = size.width * 0.68f,
                    height = size.height * 0.40f,
                    topColor = cloudLightColor,
                    bottomColor = cloudShadowColor
                )
            }

            WeatherMood.CLOUDY -> {
                drawCloud(
                    origin = Offset(size.width * 0.26f, size.height * 0.22f),
                    width = size.width * 0.58f,
                    height = size.height * 0.34f,
                    topColor = cloudShadowColor,
                    bottomColor = cloudShadowColor.copy(alpha = 0.85f)
                )
                drawCloud(
                    origin = Offset(size.width * 0.12f, size.height * 0.36f),
                    width = size.width * 0.72f,
                    height = size.height * 0.42f,
                    topColor = cloudLightColor,
                    bottomColor = cloudShadowColor
                )
            }

            WeatherMood.FOG -> {
                drawCloud(
                    origin = Offset(size.width * 0.16f, size.height * 0.20f),
                    width = size.width * 0.68f,
                    height = size.height * 0.36f,
                    topColor = cloudLightColor,
                    bottomColor = cloudShadowColor
                )
                val strokeW = size.minDimension * 0.065f
                for (row in 0..2) {
                    val y = size.height * (0.64f + row * 0.12f)
                    val startX = size.width * (if (row == 1) 0.24f else 0.16f)
                    val endX = size.width * (if (row == 1) 0.84f else 0.76f)
                    drawLine(
                        color = cloudShadowColor,
                        start = Offset(startX, y),
                        end = Offset(endX, y),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                }
            }

            WeatherMood.RAIN -> {
                drawCloud(
                    origin = Offset(size.width * 0.14f, size.height * 0.18f),
                    width = size.width * 0.72f,
                    height = size.height * 0.40f,
                    topColor = cloudLightColor,
                    bottomColor = cloudShadowColor
                )
                val dropStroke = size.minDimension * 0.075f
                val dropsX = listOf(0.32f, 0.50f, 0.68f)
                dropsX.forEachIndexed { idx, xFrac ->
                    val topY = size.height * (if (idx == 1) 0.68f else 0.63f)
                    val botY = topY + size.height * 0.16f
                    drawLine(
                        color = RainBlue,
                        start = Offset(size.width * xFrac, topY),
                        end = Offset(size.width * (xFrac - 0.05f), botY),
                        strokeWidth = dropStroke,
                        cap = StrokeCap.Round
                    )
                }
            }

            WeatherMood.SNOW -> {
                drawCloud(
                    origin = Offset(size.width * 0.14f, size.height * 0.18f),
                    width = size.width * 0.72f,
                    height = size.height * 0.40f,
                    topColor = cloudLightColor,
                    bottomColor = cloudShadowColor
                )
                val r = size.minDimension * 0.052f
                listOf(
                    Offset(size.width * 0.30f, size.height * 0.72f),
                    Offset(size.width * 0.50f, size.height * 0.80f),
                    Offset(size.width * 0.70f, size.height * 0.70f)
                ).forEach { pt ->
                    drawCircle(color = SnowIce, radius = r, center = pt)
                }
            }

            WeatherMood.THUNDERSTORM -> {
                drawCloud(
                    origin = Offset(size.width * 0.14f, size.height * 0.16f),
                    width = size.width * 0.72f,
                    height = size.height * 0.40f,
                    topColor = cloudShadowColor,
                    bottomColor = StormViolet.copy(alpha = 0.55f)
                )
                val bolt = Path().apply {
                    moveTo(size.width * 0.54f, size.height * 0.54f)
                    lineTo(size.width * 0.41f, size.height * 0.72f)
                    lineTo(size.width * 0.51f, size.height * 0.72f)
                    lineTo(size.width * 0.44f, size.height * 0.90f)
                    lineTo(size.width * 0.62f, size.height * 0.67f)
                    lineTo(size.width * 0.51f, size.height * 0.67f)
                    close()
                }
                drawPath(path = bolt, color = SunGold)
            }
        }
    }
}

private fun DrawScope.drawSun(center: Offset, radius: Float, drawRays: Boolean) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(SunGold.copy(alpha = 0.35f), Color.Transparent),
            center = center,
            radius = radius * 2.0f
        ),
        radius = radius * 2.0f,
        center = center
    )
    if (drawRays) {
        val rayCount = 8
        val innerR = radius * 1.32f
        val outerR = radius * 1.72f
        val rayWidth = radius * 0.20f
        for (i in 0 until rayCount) {
            val angle = (i * (360.0 / rayCount)) * PI / 180.0
            val start = Offset(
                center.x + (innerR * cos(angle)).toFloat(),
                center.y + (innerR * sin(angle)).toFloat()
            )
            val end = Offset(
                center.x + (outerR * cos(angle)).toFloat(),
                center.y + (outerR * sin(angle)).toFloat()
            )
            drawLine(
                color = SunGold,
                start = start,
                end = end,
                strokeWidth = rayWidth,
                cap = StrokeCap.Round
            )
        }
    }
    drawCircle(
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFFFFDE6A), SunWarmGlow),
            start = Offset(center.x - radius, center.y - radius),
            end = Offset(center.x + radius, center.y + radius)
        ),
        radius = radius,
        center = center
    )
}

private fun DrawScope.drawCrescentMoon(center: Offset, radius: Float) {
    val moonPath = Path().apply {
        addOval(
            androidx.compose.ui.geometry.Rect(
                center.x - radius,
                center.y - radius,
                center.x + radius,
                center.y + radius
            )
        )
    }
    val cutPath = Path().apply {
        val offset = radius * 0.45f
        addOval(
            androidx.compose.ui.geometry.Rect(
                center.x - radius + offset,
                center.y - radius - offset * 0.45f,
                center.x + radius + offset,
                center.y + radius - offset * 0.45f
            )
        )
    }
    val crescent = Path.combine(
        operation = androidx.compose.ui.graphics.PathOperation.Difference,
        path1 = moonPath,
        path2 = cutPath
    )
    drawPath(
        path = crescent,
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFFFFF1B8), Color(0xFFFFCA6B))
        )
    )
    // Two subtle stars
    drawCircle(
        color = Color(0xFFFFE598),
        radius = radius * 0.11f,
        center = Offset(center.x + radius * 0.85f, center.y - radius * 0.65f)
    )
    drawCircle(
        color = Color(0xFFFFE598).copy(alpha = 0.7f),
        radius = radius * 0.08f,
        center = Offset(center.x + radius * 1.15f, center.y + radius * 0.05f)
    )
}

private fun DrawScope.drawCloud(
    origin: Offset,
    width: Float,
    height: Float,
    topColor: Color,
    bottomColor: Color
) {
    val brush = Brush.verticalGradient(
        colors = listOf(topColor, bottomColor),
        startY = origin.y,
        endY = origin.y + height
    )
    val baseHeight = height * 0.52f
    val baseTop = origin.y + height - baseHeight
    drawRoundRect(
        brush = brush,
        topLeft = Offset(origin.x, baseTop),
        size = Size(width, baseHeight),
        cornerRadius = CornerRadius(baseHeight * 0.5f, baseHeight * 0.5f)
    )
    drawCircle(
        brush = brush,
        radius = height * 0.36f,
        center = Offset(origin.x + width * 0.34f, origin.y + height * 0.46f)
    )
    drawCircle(
        brush = brush,
        radius = height * 0.44f,
        center = Offset(origin.x + width * 0.58f, origin.y + height * 0.38f)
    )
    drawCircle(
        brush = brush,
        radius = height * 0.28f,
        center = Offset(origin.x + width * 0.78f, origin.y + height * 0.56f)
    )
}
