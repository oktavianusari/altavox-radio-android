package com.ari.streamer.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ari.streamer.data.Station

@Composable
fun TvNowPlaying(
    station: Station,
    isPlaying: Boolean,
    format: String?,
    bitrate: String?,
    onPlayPauseClick: () -> Unit
) {
    // Glassmorphism container
    Box(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Station Logo
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(station.logoUrl)
                    .crossfade(true)
                    .error(com.ari.streamer.R.drawable.ic_radio_dark)
                    .fallback(com.ari.streamer.R.drawable.ic_radio_dark)
                    .build(),
                contentDescription = "Now Playing Logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(androidx.compose.ui.graphics.Color.White)
                    .padding(2.dp)
            )

            // Info text Column
            Column(
                modifier = Modifier.width(140.dp) // Bound width so it fits beautifully
            ) {
                Text(
                    text = station.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                val metadata = listOfNotNull(format, bitrate).joinToString(" | ")
                Text(
                    text = metadata.ifEmpty { "Streaming..." },
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Play/Stop Action Button
            Card(
                onClick = onPlayPauseClick,
                shape = CardDefaults.shape(shape = CircleShape),
                scale = CardDefaults.scale(focusedScale = 1.1f),
                colors = CardDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.2f),
                    focusedContainerColor = Color(0xFF1DB954) // Spotify green on focus
                ),
                modifier = Modifier.size(32.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize().clickable { onPlayPauseClick() }, contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = if (isPlaying) Color.Black else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
