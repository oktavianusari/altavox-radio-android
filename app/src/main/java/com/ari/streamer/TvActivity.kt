package com.ari.streamer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.tv.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ari.streamer.data.AppTheme
import com.ari.streamer.service.RadioPlaybackService
import com.ari.streamer.ui.MainViewModel
import com.ari.streamer.ui.theme.StreamerTheme
import com.ari.streamer.ui.tv.TvMainScreen
import com.ari.streamer.ui.tv.TvSettingsScreen

class TvActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Start foreground service if needed or let ExoPlayer handle it
        val intent = Intent(this, RadioPlaybackService::class.java)
        startService(intent)

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val lightColors by viewModel.lightThemeColors.collectAsState()
            val darkColors by viewModel.darkThemeColors.collectAsState()

            val isDarkTheme = when (themeMode) {
                AppTheme.SYSTEM -> isSystemInDarkTheme()
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
            }

            val currentColors = if (isDarkTheme) darkColors else lightColors

            StreamerTheme(
                darkTheme = isDarkTheme,
                primaryColorHex = currentColors.primaryHex,
                secondaryColorHex = currentColors.secondaryHex,
                backgroundColorHex = currentColors.backgroundHex
            ) {
                val playbackState by viewModel.playbackManager.playbackState.collectAsState()
                val isPlaying = playbackState.isPlaying
                
                // Keep screen on while playing
                val currentView = LocalView.current
                androidx.compose.runtime.DisposableEffect(isPlaying) {
                    if (isPlaying) {
                        currentView.keepScreenOn = true
                    } else {
                        currentView.keepScreenOn = false
                    }
                    onDispose {
                        currentView.keepScreenOn = false
                    }
                }

                // Using TV Surface
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "main") {
                        composable("main") {
                            TvMainScreen(
                                viewModel = viewModel,
                                onNavigateToSettings = { navController.navigate("settings") }
                            )
                        }
                        composable("settings") {
                            TvSettingsScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
