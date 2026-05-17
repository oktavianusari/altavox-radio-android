package com.ari.streamer.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.ari.streamer.ui.MainViewModel
import com.ari.streamer.ui.theme.parseColor

@Composable
fun TvSettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val tvGradientColor1 by viewModel.tvGradientColor1.collectAsState()
    val tvGradientColor2 by viewModel.tvGradientColor2.collectAsState()
    val useLargerBuffer by viewModel.useLargerBuffer.collectAsState()
    val updateStatus by viewModel.updateStatus.collectAsState()

    var m3uUrl by remember { mutableStateOf("https://pastebin.com/raw/i4YM5tAL") }
    val context = LocalContext.current

    // Aesthetic TV-optimized dark presets
    val presetColors = listOf(
        "#0F3E2E", // Forest/Spotify Green
        "#0F2042", // Deep Ocean Blue
        "#2E0F3E", // Royal Purple
        "#3E0F0F", // Crimson Maroon
        "#1C1C1E", // Slate Charcoal
        "#121212", // Pure Dark Gray/Black
        "#0A2E2B", // Midnight Teal
        "#3D1B04"  // Burnt Orange
    )

    val color1 = parseColor(tvGradientColor1, Color(0xFF0F3E2E))
    val color2 = parseColor(tvGradientColor2, Color(0xFF121212))

    val backgroundBrush = Brush.linearGradient(
        colors = listOf(color1, color2)
    )

    LaunchedEffect(updateStatus) {
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

    val cardColors = CardDefaults.colors(
        containerColor = Color.White.copy(alpha = 0.1f),
        focusedContainerColor = Color.White.copy(alpha = 0.25f),
        pressedContainerColor = Color.White.copy(alpha = 0.3f)
    )

    val cardBorder = CardDefaults.border(
        focusedBorder = Border(
            border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .padding(horizontal = 48.dp, vertical = 32.dp)
    ) {
        TvLazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(bottom = 48.dp)
        ) {
            item {
                Text(
                    text = "SETTINGS",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Section 1: Color 1 (Top-Left)
            item {
                Column {
                    Text(
                        text = "TV GRADIENT COLOR 1 (TOP-LEFT)",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    TvLazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(presetColors) { colorHex ->
                            val isSelected = colorHex.equals(tvGradientColor1, ignoreCase = true)
                            Card(
                                onClick = { viewModel.setTvGradientColor1(colorHex) },
                                modifier = Modifier.size(60.dp),
                                shape = CardDefaults.shape(androidx.compose.foundation.shape.CircleShape),
                                scale = CardDefaults.scale(focusedScale = 1.2f),
                                colors = CardDefaults.colors(
                                    containerColor = parseColor(colorHex, Color.Gray),
                                    focusedContainerColor = parseColor(colorHex, Color.Gray)
                                ),
                                border = CardDefaults.border(
                                    focusedBorder = Border(
                                        border = androidx.compose.foundation.BorderStroke(3.dp, Color.White),
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    ),
                                    border = if (isSelected) Border(
                                        border = androidx.compose.foundation.BorderStroke(2.dp, Color.White.copy(alpha = 0.8f)),
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    ) else Border.None
                                )
                            ) {
                                if (isSelected) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 2: Color 2 (Bottom-Right)
            item {
                Column {
                    Text(
                        text = "TV GRADIENT COLOR 2 (BOTTOM-RIGHT)",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    TvLazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(presetColors) { colorHex ->
                            val isSelected = colorHex.equals(tvGradientColor2, ignoreCase = true)
                            Card(
                                onClick = { viewModel.setTvGradientColor2(colorHex) },
                                modifier = Modifier.size(60.dp),
                                shape = CardDefaults.shape(androidx.compose.foundation.shape.CircleShape),
                                scale = CardDefaults.scale(focusedScale = 1.2f),
                                colors = CardDefaults.colors(
                                    containerColor = parseColor(colorHex, Color.Gray),
                                    focusedContainerColor = parseColor(colorHex, Color.Gray)
                                ),
                                border = CardDefaults.border(
                                    focusedBorder = Border(
                                        border = androidx.compose.foundation.BorderStroke(3.dp, Color.White),
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    ),
                                    border = if (isSelected) Border(
                                        border = androidx.compose.foundation.BorderStroke(2.dp, Color.White.copy(alpha = 0.8f)),
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    ) else Border.None
                                )
                            ) {
                                if (isSelected) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 3: Buffering options
            item {
                Column {
                    Text(
                        text = "PLAYBACK BUFFERING",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Card(
                        onClick = { viewModel.setUseLargerBuffer(!useLargerBuffer) },
                        modifier = Modifier.fillMaxWidth().height(68.dp),
                        scale = CardDefaults.scale(focusedScale = 1.02f),
                        colors = cardColors,
                        border = cardBorder
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(verticalArrangement = Arrangement.Center) {
                                Text(
                                    text = "Use Larger Buffer",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Enhances playback stability on slow or unstable internet connections",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                text = if (useLargerBuffer) "ENABLED" else "DISABLED",
                                color = if (useLargerBuffer) Color(0xFF00E676) else Color(0xFFFF1744),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // Section 4: M3U Sync
            item {
                Column {
                    Text(
                        text = "DATABASE & PLAYLIST",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Card(
                        onClick = { viewModel.updateFromRemoteM3u(m3uUrl) },
                        modifier = Modifier.fillMaxWidth().height(68.dp),
                        scale = CardDefaults.scale(focusedScale = 1.02f),
                        colors = cardColors,
                        border = cardBorder
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(verticalArrangement = Arrangement.Center) {
                                Text(
                                    text = "Refresh Playlist from Remote M3U",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "URL: $m3uUrl",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                            }
                            if (updateStatus == MainViewModel.UpdateStatus.Loading) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Text(
                                    text = "SYNC NOW",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            // Section 5: Return button
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    onClick = onNavigateBack,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    scale = CardDefaults.scale(focusedScale = 1.02f),
                    colors = CardDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.15f),
                        focusedContainerColor = Color.White.copy(alpha = 0.35f),
                        pressedContainerColor = Color.White.copy(alpha = 0.4f)
                    ),
                    border = cardBorder
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "BACK TO CATALOG",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            letterSpacing = 1.2.sp
                        )
                    }
                }
            }
        }
    }
}
