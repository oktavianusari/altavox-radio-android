package com.ari.streamer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class AppTheme {
    SYSTEM, LIGHT, DARK
}

data class ThemeColors(
    val primaryHex: String,
    val secondaryHex: String,
    val backgroundHex: String
)

class UserPreferences(private val context: Context) {

    companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LIGHT_PRIMARY = stringPreferencesKey("light_primary")
        val LIGHT_SECONDARY = stringPreferencesKey("light_secondary")
        val LIGHT_BACKGROUND = stringPreferencesKey("light_background")
        val DARK_PRIMARY = stringPreferencesKey("dark_primary")
        val DARK_SECONDARY = stringPreferencesKey("dark_secondary")
        val DARK_BACKGROUND = stringPreferencesKey("dark_background")
        val USE_LARGER_BUFFER = booleanPreferencesKey("use_larger_buffer")
        val TV_GRADIENT_COLOR_1 = stringPreferencesKey("tv_gradient_color_1")
        val TV_GRADIENT_COLOR_2 = stringPreferencesKey("tv_gradient_color_2")
        val WIDGET_1_BG_COLOR = stringPreferencesKey("widget_1_bg_color")
        val WIDGET_1_OPACITY = androidx.datastore.preferences.core.floatPreferencesKey("widget_1_opacity")
        val WIDGET_2_BG_COLOR = stringPreferencesKey("widget_2_bg_color")
        val WIDGET_2_OPACITY = androidx.datastore.preferences.core.floatPreferencesKey("widget_2_opacity")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val LAST_PLAYED_STATION_ID = androidx.datastore.preferences.core.longPreferencesKey("last_played_station_id")
    }

    val themeModeFlow: Flow<AppTheme> = context.dataStore.data.map { preferences ->
        AppTheme.valueOf(preferences[THEME_MODE] ?: AppTheme.SYSTEM.name)
    }

    val lightThemeColorsFlow: Flow<ThemeColors> = context.dataStore.data.map { preferences ->
        ThemeColors(
            primaryHex = preferences[LIGHT_PRIMARY] ?: "#FF6200EE",
            secondaryHex = preferences[LIGHT_SECONDARY] ?: "#FF03DAC5",
            backgroundHex = preferences[LIGHT_BACKGROUND] ?: "#FFFFFFFF"
        )
    }

    val darkThemeColorsFlow: Flow<ThemeColors> = context.dataStore.data.map { preferences ->
        ThemeColors(
            primaryHex = preferences[DARK_PRIMARY] ?: "#FFBB86FC",
            secondaryHex = preferences[DARK_SECONDARY] ?: "#FF03DAC5",
            backgroundHex = preferences[DARK_BACKGROUND] ?: "#FF121212"
        )
    }

    val useLargerBufferFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[USE_LARGER_BUFFER] ?: false
    }

    val tvGradientColor1Flow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[TV_GRADIENT_COLOR_1] ?: "#0F3E2E"
    }

    val tvGradientColor2Flow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[TV_GRADIENT_COLOR_2] ?: "#121212"
    }

    val widget1BgColorFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[WIDGET_1_BG_COLOR] ?: "#151517"
    }

    val widget1OpacityFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[WIDGET_1_OPACITY] ?: 0.9f
    }

    val widget2BgColorFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[WIDGET_2_BG_COLOR] ?: "#151517"
    }

    val widget2OpacityFlow: Flow<Float> = context.dataStore.data.map { preferences ->
        preferences[WIDGET_2_OPACITY] ?: 0.9f
    }

    val appLanguageFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[APP_LANGUAGE] ?: "en"
    }

    val lastPlayedStationIdFlow: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[LAST_PLAYED_STATION_ID] ?: -1L
    }

    suspend fun setThemeMode(mode: AppTheme) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode.name
        }
    }

    suspend fun setLightColors(colors: ThemeColors) {
        context.dataStore.edit { preferences ->
            preferences[LIGHT_PRIMARY] = colors.primaryHex
            preferences[LIGHT_SECONDARY] = colors.secondaryHex
            preferences[LIGHT_BACKGROUND] = colors.backgroundHex
        }
    }

    suspend fun setDarkColors(colors: ThemeColors) {
        context.dataStore.edit { preferences ->
            preferences[DARK_PRIMARY] = colors.primaryHex
            preferences[DARK_SECONDARY] = colors.secondaryHex
            preferences[DARK_BACKGROUND] = colors.backgroundHex
        }
    }

    suspend fun setUseLargerBuffer(use: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[USE_LARGER_BUFFER] = use
        }
    }

    suspend fun setTvGradientColor1(colorHex: String) {
        context.dataStore.edit { preferences ->
            preferences[TV_GRADIENT_COLOR_1] = colorHex
        }
    }

    suspend fun setTvGradientColor2(colorHex: String) {
        context.dataStore.edit { preferences ->
            preferences[TV_GRADIENT_COLOR_2] = colorHex
        }
    }

    suspend fun setWidget1BgColor(colorHex: String) {
        context.dataStore.edit { preferences ->
            preferences[WIDGET_1_BG_COLOR] = colorHex
        }
    }

    suspend fun setWidget1Opacity(opacity: Float) {
        context.dataStore.edit { preferences ->
            preferences[WIDGET_1_OPACITY] = opacity
        }
    }

    suspend fun setWidget2BgColor(colorHex: String) {
        context.dataStore.edit { preferences ->
            preferences[WIDGET_2_BG_COLOR] = colorHex
        }
    }

    suspend fun setWidget2Opacity(opacity: Float) {
        context.dataStore.edit { preferences ->
            preferences[WIDGET_2_OPACITY] = opacity
        }
    }

    suspend fun setAppLanguage(lang: String) {
        context.dataStore.edit { preferences ->
            preferences[APP_LANGUAGE] = lang
        }
    }

    suspend fun setLastPlayedStationId(stationId: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_PLAYED_STATION_ID] = stationId
        }
    }
}
