package com.example

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.AppLanguage
import com.example.data.model.ThemeMode
import com.example.ui.components.OpeningAnimationOverlay
import com.example.ui.screen.WeatherDashboardScreen
import com.example.ui.theme.SkyPulseTheme
import com.example.ui.viewmodel.WeatherViewModel
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SkyPulseRoot()
        }
    }
}

private class LocalizedContextWrapper(
    base: Context,
    private val localizedResources: Resources
) : ContextWrapper(base) {
    override fun getResources(): Resources = localizedResources
}

@Composable
fun SkyPulseRoot(weatherViewModel: WeatherViewModel = viewModel()) {
    val preferences by weatherViewModel.preferences.collectAsStateWithLifecycle()
    var showOpeningAnimation by rememberSaveable { mutableStateOf(true) }

    val isDarkTheme = when (preferences.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val baseContext = LocalContext.current
    val baseConfig = LocalConfiguration.current
    val registryOwner = LocalActivityResultRegistryOwner.current
        ?: (baseContext as? ActivityResultRegistryOwner)

    val targetLocale = remember(preferences.language, baseConfig) {
        when (preferences.language) {
            AppLanguage.SYSTEM -> baseConfig.locales.get(0) ?: Locale.getDefault()
            else -> Locale.forLanguageTag(preferences.language.code)
        }
    }

    val isArabic = targetLocale.language.equals("ar", ignoreCase = true)
    val isRtl = TextUtils.getLayoutDirectionFromLocale(targetLocale) == View.LAYOUT_DIRECTION_RTL
    val layoutDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    val localizedContext: Context = remember(baseContext, targetLocale) {
        val config = Configuration(baseContext.resources.configuration).apply {
            setLocale(targetLocale)
            setLayoutDirection(targetLocale)
        }
        val configContext = baseContext.createConfigurationContext(config)
        LocalizedContextWrapper(baseContext, configContext.resources)
    }

    val localizedConfig: Configuration = remember(localizedContext) {
        localizedContext.resources.configuration
    }

    val providedLocals = buildList {
        add(LocalContext provides localizedContext)
        add(LocalConfiguration provides localizedConfig)
        add(LocalLayoutDirection provides layoutDirection)
        if (registryOwner != null) {
            add(LocalActivityResultRegistryOwner provides registryOwner)
        }
    }.toTypedArray()

    CompositionLocalProvider(*providedLocals) {
        SkyPulseTheme(
            darkTheme = isDarkTheme,
            isArabic = isArabic
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                WeatherDashboardScreen(
                    viewModel = weatherViewModel,
                    locale = targetLocale,
                    isDarkTheme = isDarkTheme,
                    isOpeningOverlayVisible = showOpeningAnimation
                )
                if (showOpeningAnimation) {
                    OpeningAnimationOverlay(
                        isDarkTheme = isDarkTheme,
                        onAnimationFinished = { showOpeningAnimation = false }
                    )
                }
            }
        }
    }
}
