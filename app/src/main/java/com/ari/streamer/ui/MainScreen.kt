package com.ari.streamer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.widget.Toast
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ari.streamer.R
import com.ari.streamer.data.Station

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit
) {
    val categories by viewModel.categories.collectAsState()
    val stations by viewModel.stations.collectAsState()
    val playbackState by viewModel.playbackManager.playbackState.collectAsState()
    val updateStatus by viewModel.updateStatus.collectAsState()
    val context = LocalContext.current
    
    var stationToEdit by remember { mutableStateOf<Station?>(null) }
    var m3uUrl by remember { mutableStateOf("https://pastebin.com/raw/i4YM5tAL") }

    LaunchedEffect(updateStatus) {
        when (updateStatus) {
            MainViewModel.UpdateStatus.Success -> {
                Toast.makeText(context, "Update finished successfully!", Toast.LENGTH_SHORT).show()
                viewModel.resetUpdateStatus()
            }
            MainViewModel.UpdateStatus.Error -> {
                Toast.makeText(context, "Failed to update from remote server.", Toast.LENGTH_SHORT).show()
                viewModel.resetUpdateStatus()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AltaVox Radio") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            if (playbackState.currentStation != null) {
                NowPlayingBar(
                    station = playbackState.currentStation!!,
                    isPlaying = playbackState.isPlaying,
                    format = playbackState.format,
                    bitrate = playbackState.bitrate,
                    nowPlayingTitle = playbackState.nowPlayingTitle,
                    onPlayPause = {
                        if (playbackState.isPlaying) viewModel.playbackManager.stop()
                        else viewModel.playbackManager.play()
                    }
                )
            }
        }
    ) { padding ->
        if (stations.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Welcome to AltaVox Radio!",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "You don't have any radio stations yet. Enter an M3U URL below to get started.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedTextField(
                    value = m3uUrl,
                    onValueChange = { m3uUrl = it },
                    label = { Text("M3U Playlist URL") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.updateFromRemoteM3u(m3uUrl) },
                    enabled = updateStatus != MainViewModel.UpdateStatus.Loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (updateStatus == MainViewModel.UpdateStatus.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                        Text("Downloading...")
                    } else {
                        Text("Download Playlist")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                categories.forEach { category ->
                    val categoryStations = stations.filter { it.categoryId == category.id || (it.categoryId == null && category.name == "Uncategorized") }
                    if (categoryStations.isNotEmpty()) {
                        item {
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        items(categoryStations) { station ->
                            StationItem(
                                station = station,
                                onClick = { viewModel.playStation(station) },
                                onLongClick = { stationToEdit = station }
                            )
                        }
                    }
                }
            }
        }
    }

    if (stationToEdit != null) {
        EditStationDialog(
            station = stationToEdit!!,
            categories = categories,
            onDismiss = { stationToEdit = null },
            onSave = { updatedStation ->
                viewModel.updateStation(updatedStation)
                stationToEdit = null
            },
            onDelete = {
                viewModel.deleteStation(stationToEdit!!)
                stationToEdit = null
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StationItem(station: Station, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(station.logoUrl)
                .crossfade(true)
                .error(R.drawable.ic_launcher)
                .fallback(R.drawable.ic_launcher)
                .build(),
            contentDescription = null,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = station.name, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun NowPlayingBar(
    station: Station,
    isPlaying: Boolean,
    format: String?,
    bitrate: String?,
    nowPlayingTitle: String?,
    onPlayPause: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(station.logoUrl)
                    .crossfade(true)
                    .error(R.drawable.ic_launcher)
                    .fallback(R.drawable.ic_launcher)
                    .build(),
                contentDescription = null,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = station.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                
                val details = mutableListOf<String>()
                if (nowPlayingTitle?.startsWith("Error:") == true) {
                    details.add(nowPlayingTitle)
                } else {
                    if (format != null) details.add(format)
                    if (bitrate != null) details.add(bitrate)
                }
                
                if (details.isNotEmpty()) {
                    Text(
                        text = details.joinToString(" • "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                } else {
                    Text(
                        text = "Loading...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }
            IconButton(onClick = onPlayPause) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Stop" else "Play",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditStationDialog(
    station: Station,
    categories: List<com.ari.streamer.data.Category>,
    onDismiss: () -> Unit,
    onSave: (Station) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(station.name) }
    var streamUrl by remember { mutableStateOf(station.streamUrl) }
    var logoUrl by remember { mutableStateOf(station.logoUrl ?: "") }
    var categoryId by remember { mutableStateOf(station.categoryId) }
    var expanded by remember { mutableStateOf(false) }

    val currentCategoryName = categories.find { it.id == categoryId }?.name ?: "Uncategorized"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Station") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = streamUrl,
                    onValueChange = { streamUrl = it },
                    label = { Text("Stream URL") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = logoUrl,
                    onValueChange = { logoUrl = it },
                    label = { Text("Logo URL") },
                    singleLine = true
                )

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = currentCategoryName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Uncategorized") },
                            onClick = {
                                categoryId = null
                                expanded = false
                            }
                        )
                        categories.filter { !it.name.equals("Uncategorized", ignoreCase = true) }.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    categoryId = category.id
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(station.copy(
                        name = name,
                        streamUrl = streamUrl,
                        logoUrl = logoUrl.ifBlank { null },
                        categoryId = categoryId
                    ))
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Delete")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
