package com.ari.streamer.ui

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ari.streamer.R
import com.ari.streamer.data.Station
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput

sealed class LazyPlaylistItem {
    data class Header(val title: String) : LazyPlaylistItem()
    data class StationRow(val station: Station) : LazyPlaylistItem()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToAddManual: () -> Unit
) {
    val categories by viewModel.categories.collectAsState()
    val stations by viewModel.stations.collectAsState()
    val playbackState by viewModel.playbackManager.playbackState.collectAsState()
    val updateStatus by viewModel.updateStatus.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var stationToEdit by remember { mutableStateOf<Station?>(null) }
    var m3uUrl by remember { mutableStateOf("https://pastebin.com/raw/i4YM5tAL") }
    var searchQuery by remember { mutableStateOf("") }
    var showWarningDialog by remember { mutableStateOf(false) }

    // Multi-selection states
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedStationIds by remember { mutableStateOf(setOf<Long>()) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }

    var showAddMenu by remember { mutableStateOf(false) }

    val filteredStations = remember(searchQuery, stations, categories) {
        val filtered = if (searchQuery.isBlank()) stations
        else stations.filter { station ->
            station.name.contains(searchQuery, ignoreCase = true) ||
            categories.find { it.id == station.categoryId }?.name?.contains(searchQuery, ignoreCase = true) == true
        }
        filtered.sortedBy { it.name.lowercase() }
    }

    // Dynamic Tablet Detection
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    // Build modern flat list representation
    val flattenedList = remember(searchQuery, stations, categories, filteredStations) {
        val list = mutableListOf<LazyPlaylistItem>()
        if (searchQuery.isBlank()) {
            categories.forEach { category ->
                val categoryStations = stations
                    .filter { it.categoryId == category.id || (it.categoryId == null && category.name.equals("Uncategorized", ignoreCase = true)) }
                    .sortedBy { it.name.lowercase() }
                if (categoryStations.isNotEmpty()) {
                    list.add(LazyPlaylistItem.Header(category.name))
                    categoryStations.forEach { list.add(LazyPlaylistItem.StationRow(it)) }
                }
            }

            // Fallback for stations whose categoryId == null or doesn't match any category in categories list
            val uncategorizedStations = stations
                .filter { station ->
                    val hasNoCat = station.categoryId == null
                    val isCatMissing = station.categoryId != null && categories.none { it.id == station.categoryId }
                    val hasNoDbUncategorized = categories.none { it.name.equals("Uncategorized", ignoreCase = true) }
                    
                    (hasNoCat && hasNoDbUncategorized) || (isCatMissing && hasNoDbUncategorized)
                }
                .sortedBy { it.name.lowercase() }
                
            if (uncategorizedStations.isNotEmpty()) {
                list.add(LazyPlaylistItem.Header("Uncategorized"))
                uncategorizedStations.forEach { list.add(LazyPlaylistItem.StationRow(it)) }
            }
        } else {
            if (filteredStations.isNotEmpty()) {
                list.add(LazyPlaylistItem.Header("Search Results"))
                filteredStations.forEach { list.add(LazyPlaylistItem.StationRow(it)) }
            }
        }
        list
    }

    // Pre-calculate alphabetical jump mappings
    val letterMap = remember(flattenedList) {
        val map = mutableMapOf<Char, Int>()
        flattenedList.forEachIndexed { index, item ->
            if (item is LazyPlaylistItem.StationRow) {
                val firstChar = item.station.name.firstOrNull()?.uppercaseChar()
                if (firstChar != null && firstChar in 'A'..'Z' && !map.containsKey(firstChar)) {
                    map[firstChar] = index
                }
            }
        }
        map
    }

    // State for LazyColumn
    val lazyListState = rememberLazyListState()

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
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text(stringResource(R.string.items_selected, selectedStationIds.size)) },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedStationIds = emptySet()
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.cancel))
                        }
                    },
                    actions = {
                        val allSelected = filteredStations.isNotEmpty() && selectedStationIds.size == filteredStations.size
                        TextButton(onClick = {
                            if (allSelected) {
                                selectedStationIds = emptySet()
                            } else {
                                selectedStationIds = filteredStations.map { it.id }.toSet()
                            }
                        }) {
                            Text(
                                text = if (allSelected) stringResource(R.string.deselect_all)
                                       else stringResource(R.string.select_all)
                            )
                        }
                        IconButton(
                            onClick = { showBulkDeleteDialog = true },
                            enabled = selectedStationIds.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.bulk_delete_title))
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    actions = {
                        Box {
                            IconButton(onClick = { showAddMenu = true }) {
                                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_radio))
                            }
                            DropdownMenu(
                                expanded = showAddMenu,
                                onDismissRequest = { showAddMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_search_online)) },
                                    onClick = {
                                        showAddMenu = false
                                        onNavigateToSearch()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_add_manually)) },
                                    onClick = {
                                        showAddMenu = false
                                        onNavigateToAddManual()
                                    }
                                )
                            }
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                        }
                    }
                )
            }
        },
        bottomBar = {
            // Suppress NowPlayingBar on tablet completely since it has its own right pane
            if (!isTablet && playbackState.currentStation != null) {
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
            // High-End Tablet Split-Pane Support vs Smartphone Layout
            if (isTablet) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    // Left Pane (40% width): Search, Categorized list, and Fast alphabet side scroll
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.4f)
                    ) {
                        // Search Bar
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

                        Box(modifier = Modifier.fillMaxSize()) {
                            if (searchQuery.isNotBlank() && filteredStations.isEmpty()) {
                                // Search Empty State
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
                                // List container with Alphabet Scroll Space
                                Row(modifier = Modifier.fillMaxSize()) {
                                    LazyColumn(
                                        state = lazyListState,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    ) {
                                        flattenedList.forEach { item ->
                                            when (item) {
                                                is LazyPlaylistItem.Header -> {
                                                    item {
                                                        val displayName = when {
                                                            item.title.equals("Uncategorized", ignoreCase = true) -> stringResource(R.string.uncategorized)
                                                            item.title.equals("Favourites", ignoreCase = true) -> stringResource(R.string.favourites)
                                                            item.title.equals("Search Results", ignoreCase = true) -> stringResource(R.string.search_results)
                                                            else -> item.title
                                                        }
                                                        Text(
                                                            text = displayName,
                                                            style = MaterialTheme.typography.titleMedium,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                                        )
                                                    }
                                                }
                                                is LazyPlaylistItem.StationRow -> {
                                                    item(key = item.station.id) {
                                                        val isSelected = selectedStationIds.contains(item.station.id)
                                                        StationItem(
                                                            station = item.station,
                                                            onClick = { viewModel.playStation(item.station) },
                                                            onPlay = { viewModel.playStation(item.station) },
                                                            onEdit = { stationToEdit = item.station },
                                                            onDelete = { viewModel.deleteStation(item.station) },
                                                            onSelectMultiple = {
                                                                isSelectionMode = true
                                                                selectedStationIds = setOf(item.station.id)
                                                            },
                                                            isSelectionMode = isSelectionMode,
                                                            isSelected = isSelected,
                                                            onSelectedChange = { checked ->
                                                                selectedStationIds = if (checked) {
                                                                    selectedStationIds + item.station.id
                                                                } else {
                                                                    selectedStationIds - item.station.id
                                                                }
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Alphabet scroll index strip (only show if active list >= 5 stations)
                                    if (stations.size >= 5) {
                                        AlphabetFastScroll(
                                            letters = ('A'..'Z').toList(),
                                            onLetterSelected = { letter ->
                                                letterMap[letter]?.let { targetIdx ->
                                                    coroutineScope.launch {
                                                        lazyListState.scrollToItem(targetIdx)
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .width(26.dp)
                                                .fillMaxHeight()
                                                .padding(vertical = 8.dp, horizontal = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    VerticalDivider(
                        modifier = Modifier.fillMaxHeight(),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )

                    // Right Pane (60% width): Premium Live Visualizer Now Playing Center
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.6f)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        PremiumNowPlayingPane(
                            playbackState = playbackState,
                            onPlayPause = {
                                if (playbackState.isPlaying) viewModel.playbackManager.stop()
                                else viewModel.playbackManager.play()
                            }
                        )
                    }
                }
            } else {
                // Smartphone single column layout
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
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

                    Box(modifier = Modifier.fillMaxSize()) {
                        if (searchQuery.isNotBlank() && filteredStations.isEmpty()) {
                            // Search Empty State
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
                            Row(modifier = Modifier.fillMaxSize()) {
                                LazyColumn(
                                    state = lazyListState,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
                                    flattenedList.forEach { item ->
                                        when (item) {
                                            is LazyPlaylistItem.Header -> {
                                                item {
                                                    val displayName = when {
                                                        item.title.equals("Uncategorized", ignoreCase = true) -> stringResource(R.string.uncategorized)
                                                        item.title.equals("Favourites", ignoreCase = true) -> stringResource(R.string.favourites)
                                                        item.title.equals("Search Results", ignoreCase = true) -> stringResource(R.string.search_results)
                                                        else -> item.title
                                                    }
                                                    Text(
                                                        text = displayName,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                                    )
                                                }
                                            }
                                            is LazyPlaylistItem.StationRow -> {
                                                item(key = item.station.id) {
                                                    val isSelected = selectedStationIds.contains(item.station.id)
                                                    StationItem(
                                                        station = item.station,
                                                        onClick = { viewModel.playStation(item.station) },
                                                        onPlay = { viewModel.playStation(item.station) },
                                                        onEdit = { stationToEdit = item.station },
                                                        onDelete = { viewModel.deleteStation(item.station) },
                                                        onSelectMultiple = {
                                                            isSelectionMode = true
                                                            selectedStationIds = setOf(item.station.id)
                                                        },
                                                        isSelectionMode = isSelectionMode,
                                                        isSelected = isSelected,
                                                        onSelectedChange = { checked ->
                                                            selectedStationIds = if (checked) {
                                                                selectedStationIds + item.station.id
                                                            } else {
                                                                selectedStationIds - item.station.id
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Alphabet Scroll Strip (only show if list >= 5 stations)
                                if (stations.size >= 5) {
                                    AlphabetFastScroll(
                                        letters = ('A'..'Z').toList(),
                                        onLetterSelected = { letter ->
                                            letterMap[letter]?.let { targetIdx ->
                                                coroutineScope.launch {
                                                    lazyListState.scrollToItem(targetIdx)
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .width(26.dp)
                                            .fillMaxHeight()
                                            .padding(vertical = 8.dp, horizontal = 2.dp)
                                    )
                                }
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

    if (showBulkDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            title = { Text(stringResource(R.string.bulk_delete_confirm_title)) },
            text = { Text(stringResource(R.string.bulk_delete_confirm_desc, selectedStationIds.size)) },
            confirmButton = {
                Button(
                    onClick = {
                        showBulkDeleteDialog = false
                        viewModel.deleteStations(selectedStationIds.toList())
                        isSelectionMode = false
                        selectedStationIds = emptySet()
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun PremiumNowPlayingPane(
    playbackState: com.ari.streamer.playback.PlaybackState,
    onPlayPause: () -> Unit
) {
    val station = playbackState.currentStation
    if (station == null) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            // Elegant pulsing breathing empty state visualizer
            val infiniteTransition = rememberInfiniteTransition(label = "breathing")
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.92f,
                targetValue = 1.08f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale"
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size((96.0f * scale).dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(32.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Radio,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.welcome_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    } else {
        // High-end glassmorphic/premium card visual layout
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
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
                    .size(192.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = station.name,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            val details = mutableListOf<String>()
            if (playbackState.nowPlayingTitle?.startsWith("Error:") == true) {
                details.add(playbackState.nowPlayingTitle)
            } else {
                if (playbackState.format != null) details.add(playbackState.format)
                if (playbackState.bitrate != null) details.add(playbackState.bitrate)
            }
            
            Text(
                text = if (details.isNotEmpty()) details.joinToString(" • ") else "Connecting...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Equalizer animation
            Box(
                modifier = Modifier
                    .height(60.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                EqualizerVisualizer(isPlaying = playbackState.isPlaying)
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Premium Large Action button
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier
                    .size(72.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(36.dp))
            ) {
                Icon(
                    imageVector = if (playbackState.isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (playbackState.isPlaying) "Stop" else "Play",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

@Composable
fun EqualizerVisualizer(isPlaying: Boolean, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        for (i in 0 until 6) {
            val heightFraction by if (isPlaying) {
                infiniteTransition.animateFloat(
                    initialValue = 0.15f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 400 + i * 150, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "bar_$i"
                )
            } else {
                remember { mutableStateOf(0.15f) }
            }
            
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .fillMaxHeight(heightFraction)
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
fun AlphabetFastScroll(
    letters: List<Char>,
    onLetterSelected: (Char) -> Unit,
    modifier: Modifier = Modifier
) {

    var selectedLetter by remember { mutableStateOf<Char?>(null) }

    val primary = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = modifier
            .background(
                color = surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = 1.dp,
                color = outlineVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            )
            .pointerInput(letters) {
                // Drag gesture: handle swipe across the strip
                detectDragGestures(
                    onDragStart = { offset ->
                        val itemHeight = size.height.toFloat() / letters.size
                        val index = (offset.y / itemHeight).toInt().coerceIn(0, letters.size - 1)
                        val letter = letters[index]
                        selectedLetter = letter
                        onLetterSelected(letter)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val itemHeight = size.height.toFloat() / letters.size
                        val index = (change.position.y / itemHeight).toInt().coerceIn(0, letters.size - 1)
                        val letter = letters[index]
                        if (letter != selectedLetter) {
                            selectedLetter = letter
                            onLetterSelected(letter)
                        }
                    },
                    onDragEnd = { selectedLetter = null },
                    onDragCancel = { selectedLetter = null }
                )
            }
            .pointerInput(letters) {
                // Tap gesture: simple press on a letter
                detectTapGestures { offset ->
                    val itemHeight = size.height.toFloat() / letters.size
                    val index = (offset.y / itemHeight).toInt().coerceIn(0, letters.size - 1)
                    onLetterSelected(letters[index])
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            letters.forEach { letter ->
                val isActive = letter == selectedLetter
                Box(
                    modifier = Modifier
                        .size(width = 22.dp, height = 16.dp)
                        .background(
                            color = if (isActive) primary else androidx.compose.ui.graphics.Color.Transparent,
                            shape = RoundedCornerShape(4.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = letter.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isActive) androidx.compose.ui.graphics.Color.White else primary
                    )
                }
            }
        }
    }
}



@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StationItem(
    station: Station,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSelectMultiple: () -> Unit,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onSelectedChange: (Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onSelectedChange(!isSelected)
                    } else {
                        onClick()
                    }
                },
                onLongClick = {
                    if (!isSelectionMode) {
                        expanded = true
                    }
                }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onSelectedChange(it) },
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(station.logoUrl)
                .size(128) // Ultra-light memory footprint
                .crossfade(true)
                .error(R.drawable.ic_launcher)
                .fallback(R.drawable.ic_launcher)
                .build(),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(androidx.compose.ui.graphics.Color.White) // Ensure transparent logos have white background
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = station.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        
        Box {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_play)) },
                    onClick = {
                        expanded = false
                        onPlay()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_edit)) },
                    onClick = {
                        expanded = false
                        onEdit()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_delete)) },
                    onClick = {
                        expanded = false
                        onDelete()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_select_multiple)) },
                    onClick = {
                        expanded = false
                        onSelectMultiple()
                    }
                )
            }
        }
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
                    .size(128) // Ultra-light memory footprint
                    .crossfade(true)
                    .error(R.drawable.ic_launcher)
                    .fallback(R.drawable.ic_launcher)
                    .build(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(androidx.compose.ui.graphics.Color.White) // White background for transparent logos
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
                        categories.filter { !it.name.equals("Uncategorized", ignoreCase = true) && !it.name.equals("Favourites", ignoreCase = true) && !it.name.equals("Favorites", ignoreCase = true) }.forEach { category ->
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
