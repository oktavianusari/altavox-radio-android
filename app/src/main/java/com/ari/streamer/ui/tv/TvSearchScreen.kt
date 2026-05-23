package com.ari.streamer.ui.tv

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ari.streamer.data.RadioSearchResult
import com.ari.streamer.data.Station
import com.ari.streamer.ui.MainViewModel
import com.ari.streamer.ui.theme.parseColor
import com.ari.streamer.util.NetworkClient
import kotlinx.coroutines.launch

@Composable
fun TvSearchScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val tvGradientColor1 by viewModel.tvGradientColor1.collectAsState()
    val tvGradientColor2 by viewModel.tvGradientColor2.collectAsState()
    
    val color1 = parseColor(tvGradientColor1, Color(0xFF0F3E2E))
    val color2 = parseColor(tvGradientColor2, Color(0xFF121212))
    
    val backgroundBrush = Brush.linearGradient(
        colors = listOf(color1, color2)
    )
    
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val categories by viewModel.categories.collectAsState()
    val playbackState by viewModel.playbackManager.playbackState.collectAsState()
    
    var nameQuery by remember { mutableStateOf("") }
    var countryQuery by remember { mutableStateOf("") }
    var cityQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<RadioSearchResult>>(emptyList()) }
    
    // Category selection dialog state
    var selectedResultForAdd by remember { mutableStateOf<RadioSearchResult?>(null) }
    var showCategoryDialog by remember { mutableStateOf(false) }

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
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(bottom = 48.dp)
        ) {
            // Header Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Card(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(48.dp),
                        scale = CardDefaults.scale(focusedScale = 1.1f),
                        colors = CardDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            focusedContainerColor = Color.White.copy(alpha = 0.2f)
                        )
                    ) {
                        Box(modifier = Modifier.fillMaxSize().clickable { onNavigateBack() }, contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "SEARCH ONLINE RADIO",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Search Input Panel
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // OutlinedTextField for Name
                    androidx.compose.material3.OutlinedTextField(
                        value = nameQuery,
                        onValueChange = { nameQuery = it },
                        label = { androidx.compose.material3.Text("Radio Station Name", color = Color.White.copy(alpha = 0.8f)) },
                        singleLine = true,
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White.copy(alpha = 0.8f),
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                            focusedContainerColor = Color.White.copy(alpha = 0.15f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionDown -> {
                                            focusManager.moveFocus(FocusDirection.Down)
                                            true
                                        }
                                        Key.DirectionUp -> {
                                            focusManager.moveFocus(FocusDirection.Up)
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                    )
                    
                    // OutlinedTextField for Country
                    androidx.compose.material3.OutlinedTextField(
                        value = countryQuery,
                        onValueChange = { countryQuery = it },
                        label = { androidx.compose.material3.Text("Country (Optional)", color = Color.White.copy(alpha = 0.8f)) },
                        singleLine = true,
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White.copy(alpha = 0.8f),
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                            focusedContainerColor = Color.White.copy(alpha = 0.15f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionDown -> {
                                            focusManager.moveFocus(FocusDirection.Down)
                                            true
                                        }
                                        Key.DirectionUp -> {
                                            focusManager.moveFocus(FocusDirection.Up)
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                    )
                    
                    // OutlinedTextField for City
                    androidx.compose.material3.OutlinedTextField(
                        value = cityQuery,
                        onValueChange = { cityQuery = it },
                        label = { androidx.compose.material3.Text("City (Optional)", color = Color.White.copy(alpha = 0.8f)) },
                        singleLine = true,
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White.copy(alpha = 0.8f),
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                            focusedContainerColor = Color.White.copy(alpha = 0.15f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionDown -> {
                                            focusManager.moveFocus(FocusDirection.Down)
                                            true
                                        }
                                        Key.DirectionUp -> {
                                            focusManager.moveFocus(FocusDirection.Up)
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                    )
                    
                    // Search Button Card
                    Card(
                        onClick = {
                            if (nameQuery.isBlank() && countryQuery.isBlank() && cityQuery.isBlank()) {
                                Toast.makeText(context, "Please enter some search criteria", Toast.LENGTH_SHORT).show()
                                return@Card
                            }
                            isLoading = true
                            coroutineScope.launch {
                                try {
                                    val results = NetworkClient.searchRadioStations(nameQuery, countryQuery, cityQuery)
                                    searchResults = results
                                    if (results.isEmpty()) {
                                        Toast.makeText(context, "No stations found with the criteria.", Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    Toast.makeText(context, "Search failed: ${e.message}", Toast.LENGTH_LONG).show()
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        scale = CardDefaults.scale(focusedScale = 1.02f),
                        colors = CardDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.15f),
                            focusedContainerColor = Color.White.copy(alpha = 0.3f),
                            pressedContainerColor = Color.White.copy(alpha = 0.35f)
                        ),
                        border = cardBorder
                    ) {
                        Box(modifier = Modifier.fillMaxSize().clickable {
                            if (nameQuery.isBlank() && countryQuery.isBlank() && cityQuery.isBlank()) {
                                Toast.makeText(context, "Please enter some search criteria", Toast.LENGTH_SHORT).show()
                                return@clickable
                            }
                            isLoading = true
                            coroutineScope.launch {
                                try {
                                    val results = NetworkClient.searchRadioStations(nameQuery, countryQuery, cityQuery)
                                    searchResults = results
                                    if (results.isEmpty()) {
                                        Toast.makeText(context, "No stations found with the criteria.", Toast.LENGTH_LONG).show()
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    Toast.makeText(context, "Search failed: ${e.message}", Toast.LENGTH_LONG).show()
                                } finally {
                                    isLoading = false
                                }
                            }
                        }, contentAlignment = Alignment.Center) {
                            if (isLoading) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "SEARCH STATIONS",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Search Results Heading
            if (searchResults.isNotEmpty()) {
                item {
                    Text(
                        text = "RESULTS (${searchResults.size})",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                }
                
                items(searchResults) { result ->
                    val isCurrentPlaying = playbackState.currentStation?.streamUrl == result.streamUrl
                    val isPlayingActive = isCurrentPlaying && playbackState.isPlaying
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .background(
                                color = Color.White.copy(alpha = 0.06f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.2f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(result.logoUrl)
                                    .crossfade(true)
                                    .error(com.ari.streamer.R.drawable.ic_radio)
                                    .fallback(com.ari.streamer.R.drawable.ic_radio)
                                    .build(),
                                contentDescription = "Station Logo",
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(
                                        androidx.compose.ui.graphics.Color(0xFF1E2A24),
                                        androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                                    )
                                    .padding(4.dp)
                            )
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = result.name,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${result.codec} • ${if (result.bitrate > 0) "${result.bitrate} kbps" else "128 kbps"} • ${result.country}",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            
                            // Play/Stop preview button card
                            Card(
                                onClick = {
                                    if (isPlayingActive) {
                                        viewModel.playbackManager.stop()
                                    } else {
                                        val previewStation = Station(
                                            id = 0,
                                            name = result.name,
                                            streamUrl = result.streamUrl,
                                            logoUrl = result.logoUrl,
                                            categoryId = null,
                                            orderIndex = 0
                                        )
                                        viewModel.playStation(previewStation)
                                    }
                                },
                                modifier = Modifier.size(48.dp),
                                scale = CardDefaults.scale(focusedScale = 1.1f),
                                colors = CardDefaults.colors(
                                    containerColor = if (isPlayingActive) Color(0xFFFF1744) else Color(0xFF1DB954),
                                    focusedContainerColor = if (isPlayingActive) Color(0xFFFF5252) else Color(0xFF1ED760)
                                )
                            ) {
                                Box(modifier = Modifier.fillMaxSize().clickable {
                                    if (isPlayingActive) {
                                        viewModel.playbackManager.stop()
                                    } else {
                                        val previewStation = Station(
                                            id = 0,
                                            name = result.name,
                                            streamUrl = result.streamUrl,
                                            logoUrl = result.logoUrl,
                                            categoryId = null,
                                            orderIndex = 0
                                        )
                                        viewModel.playStation(previewStation)
                                    }
                                }, contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isPlayingActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlayingActive) "Stop Preview" else "Play Preview",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            // Add button card
                            Card(
                                onClick = {
                                    selectedResultForAdd = result
                                    showCategoryDialog = true
                                },
                                modifier = Modifier.size(48.dp),
                                scale = CardDefaults.scale(focusedScale = 1.1f),
                                colors = CardDefaults.colors(
                                    containerColor = Color.White.copy(alpha = 0.15f),
                                    focusedContainerColor = Color.White.copy(alpha = 0.3f)
                                )
                            ) {
                                Box(modifier = Modifier.fillMaxSize().clickable {
                                    selectedResultForAdd = result
                                    showCategoryDialog = true
                                }, contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add Station",
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (!isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Search results will appear here.",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
    
    // Premium TV-friendly Category Selection Dialog
    if (showCategoryDialog && selectedResultForAdd != null) {
        val result = selectedResultForAdd!!
        
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showCategoryDialog = false
                selectedResultForAdd = null
            },
            title = {
                androidx.compose.material3.Text(
                    text = "Select Category",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.width(360.dp)
                ) {
                    androidx.compose.material3.Text(
                        text = "Choose a category to save '${result.name}' to:",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                    
                    Box(modifier = Modifier.height(200.dp)) {
                        TvLazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // "Uncategorized" Item
                            item {
                                Card(
                                    onClick = {
                                        viewModel.addStation(
                                            name = result.name,
                                            url = result.streamUrl,
                                            logoUrl = result.logoUrl,
                                            categoryId = null
                                        )
                                        Toast.makeText(context, "${result.name} added!", Toast.LENGTH_SHORT).show()
                                        showCategoryDialog = false
                                        selectedResultForAdd = null
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    colors = CardDefaults.colors(
                                        containerColor = Color.White.copy(alpha = 0.1f),
                                        focusedContainerColor = Color.White.copy(alpha = 0.25f)
                                    ),
                                    scale = CardDefaults.scale(focusedScale = 1.02f)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize().clickable {
                                        viewModel.addStation(
                                            name = result.name,
                                            url = result.streamUrl,
                                            logoUrl = result.logoUrl,
                                            categoryId = null
                                        )
                                        Toast.makeText(context, "${result.name} added!", Toast.LENGTH_SHORT).show()
                                        showCategoryDialog = false
                                        selectedResultForAdd = null
                                    }.padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                                        Text(text = "Uncategorized", color = Color.White, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                            
                            // User categories
                            items(categories.filter { !it.name.equals("Uncategorized", ignoreCase = true) }) { category ->
                                Card(
                                    onClick = {
                                        viewModel.addStation(
                                            name = result.name,
                                            url = result.streamUrl,
                                            logoUrl = result.logoUrl,
                                            categoryId = category.id
                                        )
                                        Toast.makeText(context, "${result.name} added to ${category.name}!", Toast.LENGTH_SHORT).show()
                                        showCategoryDialog = false
                                        selectedResultForAdd = null
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    colors = CardDefaults.colors(
                                        containerColor = Color.White.copy(alpha = 0.1f),
                                        focusedContainerColor = Color.White.copy(alpha = 0.25f)
                                    ),
                                    scale = CardDefaults.scale(focusedScale = 1.02f)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize().clickable {
                                        viewModel.addStation(
                                            name = result.name,
                                            url = result.streamUrl,
                                            logoUrl = result.logoUrl,
                                            categoryId = category.id
                                        )
                                        Toast.makeText(context, "${result.name} added to ${category.name}!", Toast.LENGTH_SHORT).show()
                                        showCategoryDialog = false
                                        selectedResultForAdd = null
                                    }.padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                                        Text(text = category.name, color = Color.White, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showCategoryDialog = false
                        selectedResultForAdd = null
                    }
                ) {
                    androidx.compose.material3.Text(
                        text = "Cancel",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                }
            },
            containerColor = Color(0xFF1E1E1E),
            textContentColor = Color.White
        )
    }
}
