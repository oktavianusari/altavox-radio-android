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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
    onStationClick: (Station) -> Unit,
    onSettingsClick: () -> Unit
) {
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
        contentPadding = PaddingValues(bottom = 100.dp) // Space for mini player
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AltaVox",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = currentTime,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(end = 24.dp)
                    )

                    Card(
                        onClick = onSettingsClick,
                        modifier = Modifier.size(48.dp),
                        scale = CardDefaults.scale(focusedScale = 1.1f),
                        colors = CardDefaults.colors(
                            containerColor = Color.Transparent,
                            focusedContainerColor = Color.White.copy(alpha = 0.2f)
                        )
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }

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
                                TvStationCard(
                                    station = pair[0],
                                    onClick = { onStationClick(pair[0]) }
                                )
                                if (pair.size > 1) {
                                    TvStationCard(
                                        station = pair[1],
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
                            TvStationCard(
                                station = pair[0],
                                onClick = { onStationClick(pair[0]) }
                            )
                            if (pair.size > 1) {
                                TvStationCard(
                                    station = pair[1],
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

@Composable
fun TvStationCard(
    station: Station,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(120.dp) // 50% smaller from 180dp is ~120dp for better TV grid
            .height(160.dp), // 50% smaller from 240dp is 160dp
        scale = CardDefaults.scale(focusedScale = 1.05f),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(3.dp, Color.White),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            )
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
                    .weight(1f) // Use weight to keep space for text
                    .padding(8.dp) // Add padding so logo doesn't touch edges
                    .background(Color.White, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            )
            
            Column(
                modifier = Modifier.padding(8.dp)
            ) {
                Text(
                    text = station.name,
                    color = Color.Black,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp, // Smaller font for smaller card
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
