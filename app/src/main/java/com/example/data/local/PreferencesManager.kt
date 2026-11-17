package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AppLanguage
import com.example.data.model.TemperatureUnit
import com.example.data.model.ThemeMode
import com.example.data.model.UserPreferences
import com.example.data.model.WindSpeedUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "skypulse_prefs")

class PreferencesManager(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val TEMP_UNIT = stringPreferencesKey("temp_unit")
        val WIND_UNIT = stringPreferencesKey("wind_unit")
        val ACTIVE_CITY_ID = stringPreferencesKey("active_city_id")
        val LOCATION_PROMPT_DISMISSED = booleanPreferencesKey("location_prompt_dismissed")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            language = AppLanguage.fromCode(prefs[Keys.LANGUAGE]),
            themeMode = ThemeMode.fromCode(prefs[Keys.THEME_MODE]),
            temperatureUnit = TemperatureUnit.fromCode(prefs[Keys.TEMP_UNIT]),
            windSpeedUnit = WindSpeedUnit.fromCode(prefs[Keys.WIND_UNIT]),
            activeCityId = prefs[Keys.ACTIVE_CITY_ID],
            locationPromptDismissed = prefs[Keys.LOCATION_PROMPT_DISMISSED] ?: false
        )
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun setThemeMode(themeMode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = themeMode.code }
    }

    suspend fun setTemperatureUnit(unit: TemperatureUnit) {
        context.dataStore.edit { it[Keys.TEMP_UNIT] = unit.code }
    }

    suspend fun setWindSpeedUnit(unit: WindSpeedUnit) {
        context.dataStore.edit { it[Keys.WIND_UNIT] = unit.code }
    }

    suspend fun setActiveCityId(cityId: String) {
        context.dataStore.edit { it[Keys.ACTIVE_CITY_ID] = cityId }
    }

    suspend fun setLocationPromptDismissed(dismissed: Boolean) {
        context.dataStore.edit { it[Keys.LOCATION_PROMPT_DISMISSED] = dismissed }
    }
}
