package com.example

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `app name matches english and arabic resources`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals("SkyPulse", context.getString(R.string.app_name))

    val arConfig = Configuration(context.resources.configuration).apply {
      setLocale(Locale("ar"))
    }
    val arContext = context.createConfigurationContext(arConfig)
    assertEquals("نبض الطقس", arContext.getString(R.string.app_name))
    assertEquals("سماء صافية", arContext.getString(R.string.weather_clear))
  }
}
