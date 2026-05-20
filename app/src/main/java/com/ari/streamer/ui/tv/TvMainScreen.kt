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
import com.ari.streamer.ui.theme.parseColor

@Composable
fun TvMainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToSearch: () -> Unit
) {
    val playbackState by viewModel.playbackManager.playbackState.collectAsState()
    val stations by viewModel.stations.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val tvGradientColor1 by viewModel.tvGradientColor1.collectAsState()
    val tvGradientColor2 by viewModel.tvGradientColor2.collectAsState()

    val currentStation = playbackState.currentStation
    val isPlaying = playbackState.isPlaying
    val format = playbackState.format
    val bitrate = playbackState.bitrate

    val color1 = parseColor(tvGradientColor1, Color(0xFF0F3E2E))
    val color2 = parseColor(tvGradientColor2, Color(0xFF121212))

    val backgroundBrush = Brush.linearGradient(
        colors = listOf(color1, color2)
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
                    currentStation = currentStation,
                    isPlaying = isPlaying,
                    format = format,
                    bitrate = bitrate,
                    onStationClick = { station ->
                        if (currentStation?.id == station.id) {
                            if (isPlaying) viewModel.playbackManager.pause()
                            else viewModel.playbackManager.play()
                        } else {
                            viewModel.playStation(station)
                        }
                    },
                    onPlayPauseClick = {
                        if (isPlaying) viewModel.playbackManager.pause()
                        else viewModel.playbackManager.play()
                    },
                    onSettingsClick = onNavigateToSettings,
                    onSearchClick = onNavigateToSearch,
                    viewModel = viewModel
                )
            }
        }
    }
}
