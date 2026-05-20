package com.ari.streamer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import android.app.Activity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.runtime.SideEffect
fun parseColor(colorString: String, defaultColor: Color): Color {
    return try {
        Color(android.graphics.Color.parseColor(colorString))
    } catch (e: Exception) {
        defaultColor
    }
}

@Composable
fun StreamerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    primaryColorHex: String? = null,
    secondaryColorHex: String? = null,
    backgroundColorHex: String? = null,
    content: @Composable () -> Unit
) {
    val defaultPrimary = if (darkTheme) Color(0xFFBB86FC) else Color(0xFF6200EE)
    val defaultSecondary = if (darkTheme) Color(0xFF03DAC5) else Color(0xFF03DAC5)
    val defaultBackground = if (darkTheme) Color(0xFF121212) else Color(0xFFFFFBFE)

    // Check if user has explicitly set custom colors
    val hasCustomColors = primaryColorHex != null || secondaryColorHex != null || backgroundColorHex != null

    val colorScheme = when {
        // Use Material You dynamic colors if Android 12+ and no custom colors set
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasCustomColors -> {
            val context = androidx.compose.ui.platform.LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        // Use custom or default static colors
        else -> {
            val primary = primaryColorHex?.let { parseColor(it, defaultPrimary) } ?: defaultPrimary
            val secondary = secondaryColorHex?.let { parseColor(it, defaultSecondary) } ?: defaultSecondary
            val background = backgroundColorHex?.let { parseColor(it, defaultBackground) } ?: defaultBackground

            if (darkTheme) {
                darkColorScheme(
                    primary = primary,
                    secondary = secondary,
                    background = background,
                    surface = background
                )
            } else {
                lightColorScheme(
                    primary = primary,
                    secondary = secondary,
                    background = background,
                    surface = background
                )
            }
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

