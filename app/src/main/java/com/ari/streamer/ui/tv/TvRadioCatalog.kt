package com.ari.streamer.ui.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.text.style.TextAlign
import com.ari.streamer.ui.MainViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ari.streamer.data.Category
import com.ari.streamer.data.Station

@Composable
fun TvRadioCatalog(
    stations: List<Station>,
    categories: List<Category>,
    currentStation: Station?,
    isPlaying: Boolean,
    format: String?,
    bitrate: String?,
    onStationClick: (Station) -> Unit,
    onPlayPauseClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val updateStatus by viewModel.updateStatus.collectAsState()

    androidx.compose.runtime.LaunchedEffect(updateStatus) {
        when (updateStatus) {
            MainViewModel.UpdateStatus.Success -> {
                android.widget.Toast.makeText(context, "Playlist updated successfully!", android.widget.Toast.LENGTH_SHORT).show()
                viewModel.resetUpdateStatus()
            }
            MainViewModel.UpdateStatus.Error -> {
                android.widget.Toast.makeText(context, "Failed to update playlist.", android.widget.Toast.LENGTH_SHORT).show()
                viewModel.resetUpdateStatus()
            }
            else -> {}
        }
    }
    // Time state
    var currentTime by androidx.compose.runtime.remember { 
        androidx.compose.runtime.mutableStateOf(
            java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        ) 
    }
    
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            currentTime = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
            kotlinx.coroutines.delay(10000) // Update every 10 seconds
        }
    }

    // Group stations by category
    val stationsByCategory = stations.groupBy { it.categoryId }

    TvLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp) // Regular bottom padding (no massive bar at the bottom)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AltaVox",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Small Now Playing Box next to the clock
                    if (currentStation != null) {
                        TvNowPlaying(
                            station = currentStation,
                            isPlaying = isPlaying,
                            format = format,
                            bitrate = bitrate,
                            onPlayPauseClick = onPlayPauseClick
                        )
                    }

                    Text(
                        text = currentTime,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Card(
                        onClick = onSearchClick,
                        modifier = Modifier.size(44.dp),
                        scale = CardDefaults.scale(focusedScale = 1.1f),
                        colors = CardDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            focusedContainerColor = Color.White.copy(alpha = 0.2f)
                        )
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Radio",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Card(
                        onClick = onSettingsClick,
                        modifier = Modifier.size(44.dp),
                        scale = CardDefaults.scale(focusedScale = 1.1f),
                        colors = CardDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            focusedContainerColor = Color.White.copy(alpha = 0.2f)
                        )
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }

        if (stations.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Welcome to AltaVox Radio!",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Your catalog is empty. You can sync M3U playlist from settings or search for online radio stations now.",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 48.dp)
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Card 1: Sync Now
                        val m3uUrl by viewModel.m3uUrl.collectAsState()

                        Card(
                            onClick = {
                                if (updateStatus != MainViewModel.UpdateStatus.Loading) {
                                    viewModel.updateFromRemoteM3u(m3uUrl)
                                }
                            },
                            modifier = Modifier
                                .width(220.dp)
                                .height(120.dp),
                            scale = CardDefaults.scale(focusedScale = 1.05f),
                            colors = CardDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.1f),
                                focusedContainerColor = Color.White.copy(alpha = 0.25f)
                            ),
                            border = CardDefaults.border(
                                focusedBorder = Border(
                                    border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                )
                            )
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (updateStatus == MainViewModel.UpdateStatus.Loading) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(text = "Downloading...", color = Color.White, fontSize = 12.sp)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Sync",
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "SYNC PLAYLIST NOW",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        // Card 2: Search Online Radio
                        Card(
                            onClick = onSearchClick,
                            modifier = Modifier
                                .width(220.dp)
                                .height(120.dp),
                            scale = CardDefaults.scale(focusedScale = 1.05f),
                            colors = CardDefaults.colors(
                                containerColor = Color.White.copy(alpha = 0.1f),
                                focusedContainerColor = Color.White.copy(alpha = 0.25f)
                            ),
                            border = CardDefaults.border(
                                focusedBorder = Border(
                                    border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                )
                            )
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "SEARCH ONLINE RADIO",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        } else {
            categories.forEach { category ->
                val categoryStations = stationsByCategory[category.id] ?: emptyList()
                if (categoryStations.isNotEmpty()) {
                    item {
                        Text(
                            text = category.name.uppercase(),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.5.sp,
                            modifier = Modifier.padding(bottom = 16.dp, top = 24.dp)
                        )
                    }

                    item {
                        TvLazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val chunkedStations = categoryStations.chunked(2)
                            items(chunkedStations) { pair ->
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    val isFirstCurrent = currentStation?.id == pair[0].id
                                    TvStationCard(
                                        station = pair[0],
                                        isCurrent = isFirstCurrent,
                                        isPlaying = isPlaying,
                                        onClick = { onStationClick(pair[0]) }
                                    )
                                    if (pair.size > 1) {
                                        val isSecondCurrent = currentStation?.id == pair[1].id
                                        TvStationCard(
                                            station = pair[1],
                                            isCurrent = isSecondCurrent,
                                            isPlaying = isPlaying,
                                            onClick = { onStationClick(pair[1]) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // Uncategorized stations
            val uncategorizedStations = stationsByCategory[null] ?: emptyList()
            if (uncategorizedStations.isNotEmpty()) {
                item {
                    Text(
                        text = "OTHER STATIONS",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(bottom = 16.dp, top = 24.dp)
                    )
                }

                item {
                    TvLazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val chunkedStations = uncategorizedStations.chunked(2)
                        items(chunkedStations) { pair ->
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                val isFirstCurrent = currentStation?.id == pair[0].id
                                TvStationCard(
                                    station = pair[0],
                                    isCurrent = isFirstCurrent,
                                    isPlaying = isPlaying,
                                    onClick = { onStationClick(pair[0]) }
                                )
                                if (pair.size > 1) {
                                    val isSecondCurrent = currentStation?.id == pair[1].id
                                    TvStationCard(
                                        station = pair[1],
                                        isCurrent = isSecondCurrent,
                                        isPlaying = isPlaying,
                                        onClick = { onStationClick(pair[1]) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TvStationCard(
    station: Station,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    // Spotify-like green border for currently active playing station card, sleek gray for paused
    val borderStroke = if (isCurrent) {
        Border(
            border = BorderStroke(3.dp, if (isPlaying) Color(0xFF1DB954) else Color.Gray),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
        )
    } else {
        Border(
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
        )
    }

    val focusedBorderStroke = Border(
        border = BorderStroke(3.dp, if (isCurrent && isPlaying) Color(0xFF1DB954) else Color.White),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    )

    Card(
        onClick = onClick,
        modifier = Modifier
            .width(120.dp)
            .height(160.dp),
        scale = CardDefaults.scale(focusedScale = 1.05f),
        border = CardDefaults.border(
            border = borderStroke,
            focusedBorder = focusedBorderStroke
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(station.logoUrl ?: "https://ui-avatars.com/api/?name=${station.name}&background=random")
                    .crossfade(true)
                    .build(),
                contentDescription = "Station Logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(8.dp)
                    .background(Color.White, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            )
            
            Column(
                modifier = Modifier.padding(8.dp)
            ) {
                Text(
                    text = station.name,
                    color = Color.Black,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Radio",
                    color = Color.DarkGray,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
    }
}
