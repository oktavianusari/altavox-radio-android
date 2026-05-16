package com.ari.streamer.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ari.streamer.ui.MainViewModel

@Composable
fun TvMainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit
) {
    val playbackState by viewModel.playbackManager.playbackState.collectAsState()
    val stations by viewModel.stations.collectAsState()
    val categories by viewModel.categories.collectAsState()

    val currentStation = playbackState.currentStation
    val isPlaying = playbackState.isPlaying
    val format = playbackState.format
    val bitrate = playbackState.bitrate

    // Dark green gradient similar to Spotify TV app
    val backgroundBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF0F3E2E), // Dark Green top-left
            Color(0xFF121212)  // Very dark gray/black bottom-right
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Main Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .padding(start = 48.dp, top = 32.dp, bottom = 32.dp, end = 32.dp)
            ) {
                TvRadioCatalog(
                    stations = stations,
                    categories = categories,
                    onStationClick = { station ->
                        viewModel.playStation(station)
                    },
                    onSettingsClick = onNavigateToSettings
                )
            }
        }

        // Mini Player at the bottom if a station is currently playing
        if (currentStation != null) {
            Box(modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)) {
                TvNowPlaying(
                    station = currentStation,
                    isPlaying = isPlaying,
                    format = format,
                    bitrate = bitrate,
                    onPlayPauseClick = { 
                        if (isPlaying) viewModel.playbackManager.pause() 
                        else viewModel.playbackManager.play() 
                    }
                )
            }
        }
    }
}
