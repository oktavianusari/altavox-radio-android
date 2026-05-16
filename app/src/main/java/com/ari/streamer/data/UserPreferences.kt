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
}
