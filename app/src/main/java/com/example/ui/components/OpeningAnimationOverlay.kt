package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.RainBlue
import com.example.ui.theme.SunGold
import com.example.ui.theme.SunWarmGlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun OpeningAnimationOverlay(
    isDarkTheme: Boolean,
    onAnimationFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sunRise = remember { Animatable(0f) }
    val cloudDrift = remember { Animatable(0f) }
    val pulseGlow = remember { Animatable(0.65f) }
    val titleProgress = remember { Animatable(0f) }
    val exitAlpha = remember { Animatable(1f) }
    val exitScale = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        val job1 = launch {
            sunRise.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        val job2 = launch {
            cloudDrift.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
            )
        }
        val job3 = launch {
            pulseGlow.animateTo(
                targetValue = 1.15f,
                animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing)
            )
        }
        val job4 = launch {
            delay(180)
            titleProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
            )
        }
        job1.join()
        job2.join()
        job3.join()
        job4.join()
        delay(220)
        val fadeJob = launch {
            exitAlpha.animateTo(0f, animationSpec = tween(360, easing = FastOutSlowInEasing))
        }
        val scaleJob = launch {
            exitScale.animateTo(1.06f, animationSpec = tween(360, easing = FastOutSlowInEasing))
        }
        fadeJob.join()
        scaleJob.join()
        onAnimationFinished()
    }

    val backgroundColors = if (isDarkTheme) {
        listOf(Color(0xFF07111F), Color(0xFF0E2744), Color(0xFF163D6B))
    } else {
        listOf(Color(0xFF0D4E96), Color(0xFF1976D2), Color(0xFF53A8F5))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = exitAlpha.value
                scaleX = exitScale.value
                scaleY = exitScale.value
            }
            .background(Brush.verticalGradient(backgroundColors)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Canvas(modifier = Modifier.size(150.dp)) {
                val w = size.width
                val h = size.height

                // Sun rises smoothly behind the cloud
                val sunStartY = h * 0.62f
                val sunEndY = h * 0.36f
                val sunY = sunStartY + (sunEndY - sunStartY) * sunRise.value
                val sunCenter = Offset(w * 0.62f, sunY)
                val sunRadius = size.minDimension * 0.19f

                // Outer pulsing glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SunGold.copy(alpha = 0.42f * sunRise.value),
                            Color.Transparent
                        ),
                        center = sunCenter,
                        radius = sunRadius * 2.4f * pulseGlow.value
                    ),
                    radius = sunRadius * 2.4f * pulseGlow.value,
                    center = sunCenter
                )

                // Sun rays
                if (sunRise.value > 0.2f) {
                    val rayAlpha = ((sunRise.value - 0.2f) / 0.8f).coerceIn(0f, 1f)
                    for (i in 0 until 8) {
                        val angle = (i * 45.0 + sunRise.value * 22.0) * PI / 180.0
                        val rIn = sunRadius * 1.32f
                        val rOut = sunRadius * 1.68f
                        drawLine(
                            color = SunGold.copy(alpha = rayAlpha),
                            start = Offset(
                                sunCenter.x + (rIn * cos(angle)).toFloat(),
                                sunCenter.y + (rIn * sin(angle)).toFloat()
                            ),
                            end = Offset(
                                sunCenter.x + (rOut * cos(angle)).toFloat(),
                                sunCenter.y + (rOut * sin(angle)).toFloat()
                            ),
                            strokeWidth = sunRadius * 0.18f,
                            cap = StrokeCap.Round
                        )
                    }
                }

                // Sun core
                drawCircle(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFFFE57F), SunWarmGlow),
                        start = Offset(sunCenter.x - sunRadius, sunCenter.y - sunRadius),
                        end = Offset(sunCenter.x + sunRadius, sunCenter.y + sunRadius)
                    ),
                    radius = sunRadius * sunRise.value.coerceAtLeast(0.1f),
                    center = sunCenter
                )

                // Drifting foreground cloud
                val cloudXOffset = (1f - cloudDrift.value) * (-w * 0.14f)
                val cloudOrigin = Offset(w * 0.14f + cloudXOffset, h * 0.38f)
                val cloudW = w * 0.68f
                val cloudH = h * 0.36f
                val cloudAlpha = cloudDrift.value.coerceIn(0f, 1f)
                val cloudBrush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = cloudAlpha),
                        Color(0xFFDCE8F6).copy(alpha = cloudAlpha)
                    ),
                    startY = cloudOrigin.y,
                    endY = cloudOrigin.y + cloudH
                )

                val baseH = cloudH * 0.52f
                drawRoundRect(
                    brush = cloudBrush,
                    topLeft = Offset(cloudOrigin.x, cloudOrigin.y + cloudH - baseH),
                    size = Size(cloudW, baseH),
                    cornerRadius = CornerRadius(baseH * 0.5f, baseH * 0.5f)
                )
                drawCircle(
                    brush = cloudBrush,
                    radius = cloudH * 0.36f,
                    center = Offset(cloudOrigin.x + cloudW * 0.34f, cloudOrigin.y + cloudH * 0.46f)
                )
                drawCircle(
                    brush = cloudBrush,
                    radius = cloudH * 0.44f,
                    center = Offset(cloudOrigin.x + cloudW * 0.58f, cloudOrigin.y + cloudH * 0.38f)
                )
                drawCircle(
                    brush = cloudBrush,
                    radius = cloudH * 0.28f,
                    center = Offset(cloudOrigin.x + cloudW * 0.78f, cloudOrigin.y + cloudH * 0.56f)
                )

                // Accent sky droplet
                if (cloudDrift.value > 0.5f) {
                    val dropAlpha = ((cloudDrift.value - 0.5f) * 2f).coerceIn(0f, 1f)
                    drawCircle(
                        color = RainBlue.copy(alpha = dropAlpha),
                        radius = size.minDimension * 0.042f,
                        center = Offset(w * 0.30f, h * 0.30f - (1f - dropAlpha) * 10.dp.toPx())
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.graphicsLayer {
                    alpha = titleProgress.value
                    translationY = (1f - titleProgress.value) * 20.dp.toPx()
                }
            )
        }
    }
}
