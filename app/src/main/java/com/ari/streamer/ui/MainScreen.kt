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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Radio
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToSearch: () -> Unit
) {
    val categories by viewModel.categories.collectAsState()
    val stations by viewModel.stations.collectAsState()
    val playbackState by viewModel.playbackManager.playbackState.collectAsState()
    val updateStatus by viewModel.updateStatus.collectAsState()
    val context = LocalContext.current
    
    var stationToEdit by remember { mutableStateOf<Station?>(null) }
    var m3uUrl by remember { mutableStateOf("https://pastebin.com/raw/i4YM5tAL") }
    var searchQuery by remember { mutableStateOf("") }
    var showWarningDialog by remember { mutableStateOf(false) }

    val filteredStations = remember(searchQuery, stations, categories) {
        val filtered = if (searchQuery.isBlank()) stations
        else stations.filter { station ->
            station.name.contains(searchQuery, ignoreCase = true) ||
            categories.find { it.id == station.categoryId }?.name?.contains(searchQuery, ignoreCase = true) == true
        }
        filtered.sortedBy { it.name.lowercase() }
    }

    LaunchedEffect(updateStatus) {
        when (updateStatus) {
            MainViewModel.UpdateStatus.Success -> {
                Toast.makeText(context, context.getString(R.string.playlist_updated), Toast.LENGTH_SHORT).show()
                viewModel.resetUpdateStatus()
            }
            MainViewModel.UpdateStatus.Error -> {
                Toast.makeText(context, context.getString(R.string.failed_update_remote), Toast.LENGTH_SHORT).show()
                viewModel.resetUpdateStatus()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_radio))
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
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
                    Icons.Default.Radio,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.welcome_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.welcome_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedTextField(
                    value = m3uUrl,
                    onValueChange = { m3uUrl = it },
                    label = { Text(stringResource(R.string.playlist_url)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (m3uUrl.isNotBlank()) {
                            showWarningDialog = true
                        } else {
                            Toast.makeText(context, context.getString(R.string.url_cannot_be_blank), Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = updateStatus != MainViewModel.UpdateStatus.Loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (updateStatus == MainViewModel.UpdateStatus.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                        Text(stringResource(R.string.downloading))
                    } else {
                        Text(stringResource(R.string.download_playlist))
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Spotify-inspired modern fully rounded search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
                    leadingIcon = { 
                        Icon(
                            imageVector = Icons.Default.Search, 
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        ) 
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear, 
                                    contentDescription = "Clear search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(28.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )

                if (searchQuery.isBlank()) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        categories.forEach { category ->
                            val categoryStations = stations
                                .filter { it.categoryId == category.id || (it.categoryId == null && category.name.equals("Uncategorized", ignoreCase = true)) }
                                .sortedBy { it.name.lowercase() }
                            if (categoryStations.isNotEmpty()) {
                                item {
                                    val catDisplayName = if (category.name.equals("Uncategorized", ignoreCase = true)) {
                                        stringResource(R.string.uncategorized)
                                    } else if (category.name.equals("Favourites", ignoreCase = true)) {
                                        stringResource(R.string.favourites)
                                    } else {
                                        category.name
                                    }
                                    
                                    Text(
                                        text = catDisplayName,
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

                        // Fallback for stasiuns whose categoryId == null or doesn't match any category in categories list
                        val uncategorizedStations = stations
                            .filter { station ->
                                val hasNoCat = station.categoryId == null
                                val isCatMissing = station.categoryId != null && categories.none { it.id == station.categoryId }
                                val hasNoDbUncategorized = categories.none { it.name.equals("Uncategorized", ignoreCase = true) }
                                
                                (hasNoCat && hasNoDbUncategorized) || (isCatMissing && hasNoDbUncategorized)
                            }
                            .sortedBy { it.name.lowercase() }
                            
                        if (uncategorizedStations.isNotEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.uncategorized),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            items(uncategorizedStations) { station ->
                                StationItem(
                                    station = station,
                                    onClick = { viewModel.playStation(station) },
                                    onLongClick = { stationToEdit = station }
                                )
                            }
                        }
                    }
                } else {
                    if (filteredStations.isEmpty()) {
                        // Spotify-like Premium Empty State
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.no_stations_found),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item {
                                Text(
                                    text = stringResource(R.string.search_results),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            items(filteredStations) { station ->
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

    if (showWarningDialog) {
        AlertDialog(
            onDismissRequest = { showWarningDialog = false },
            title = { Text(stringResource(R.string.warning)) },
            text = { Text(stringResource(R.string.remote_m3u_warning_text)) },
            confirmButton = {
                Button(
                    onClick = {
                        showWarningDialog = false
                        viewModel.updateFromRemoteM3u(m3uUrl)
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showWarningDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
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
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
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
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
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

    val currentCategoryName = categories.find { it.id == categoryId }?.name ?: stringResource(R.string.uncategorized)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_radio_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name_label)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = streamUrl,
                    onValueChange = { streamUrl = it },
                    label = { Text(stringResource(R.string.stream_url_label)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = logoUrl,
                    onValueChange = { logoUrl = it },
                    label = { Text(stringResource(R.string.logo_url_label)) },
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
                        label = { Text(stringResource(R.string.category_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.uncategorized)) },
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
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text(stringResource(R.string.delete))
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}
