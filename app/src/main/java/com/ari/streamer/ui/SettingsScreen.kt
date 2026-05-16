package com.ari.streamer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.ari.streamer.data.AppTheme
import com.ari.streamer.data.ThemeColors
import com.ari.streamer.ui.theme.parseColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val lightColors by viewModel.lightThemeColors.collectAsState()
    val darkColors by viewModel.darkThemeColors.collectAsState()
    val useLargerBuffer by viewModel.useLargerBuffer.collectAsState()
    val categories by viewModel.categories.collectAsState()
    var m3uUrl by remember { mutableStateOf("https://pastebin.com/raw/i4YM5tAL") }

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<com.ari.streamer.data.Category?>(null) }
    var categoryInputName by remember { mutableStateOf("") }
    
    val updateStatus by viewModel.updateStatus.collectAsState()
    val context = LocalContext.current

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
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Remote M3U Update", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = m3uUrl,
                onValueChange = { m3uUrl = it },
                label = { Text("M3U URL") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { viewModel.updateFromRemoteM3u(m3uUrl) },
                enabled = updateStatus != MainViewModel.UpdateStatus.Loading
            ) {
                if (updateStatus == MainViewModel.UpdateStatus.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp).padding(end = 8.dp),
                        strokeWidth = 2.dp
                    )
                    Text("Updating...")
                } else {
                    Text("Update Radio List from Remote Server")
                }
            }

            Divider()

            Text("Theme Mode", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTheme.values().forEach { mode ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        label = { Text(mode.name) }
                    )
                }
            }

            Divider()

            Text("Light Theme Colors (Hex)", style = MaterialTheme.typography.titleMedium)
            ColorPickerRow("Primary", lightColors.primaryHex) { newHex ->
                viewModel.setLightColors(lightColors.copy(primaryHex = newHex))
            }
            ColorPickerRow("Secondary", lightColors.secondaryHex) { newHex ->
                viewModel.setLightColors(lightColors.copy(secondaryHex = newHex))
            }
            ColorPickerRow("Background", lightColors.backgroundHex) { newHex ->
                viewModel.setLightColors(lightColors.copy(backgroundHex = newHex))
            }

            Divider()

            Text("Dark Theme Colors (Hex)", style = MaterialTheme.typography.titleMedium)
            ColorPickerRow("Primary", darkColors.primaryHex) { newHex ->
                viewModel.setDarkColors(darkColors.copy(primaryHex = newHex))
            }
            ColorPickerRow("Secondary", darkColors.secondaryHex) { newHex ->
                viewModel.setDarkColors(darkColors.copy(secondaryHex = newHex))
            }
            ColorPickerRow("Background", darkColors.backgroundHex) { newHex ->
                viewModel.setDarkColors(darkColors.copy(backgroundHex = newHex))
            }

            Divider()

            Text("Backup & Restore", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onBackupClick) { Text("Backup to .m3u") }
                Button(onClick = onRestoreClick) { Text("Restore from .m3u") }
            }
            
            Divider()
            
            Text("Advanced Playback", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Use Larger Buffer (Slow Network)")
                Switch(
                    checked = useLargerBuffer,
                    onCheckedChange = { viewModel.setUseLargerBuffer(it) }
                )
            }

            Divider()

            Text("Manage Categories", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { 
                categoryInputName = ""
                showAddCategoryDialog = true 
            }) {
                Text("Add New Category")
            }

            if (categories.isEmpty()) {
                Text("No categories found.", style = MaterialTheme.typography.bodyMedium)
            } else {
                categories.forEach { category ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(category.name, modifier = Modifier.weight(1f))
                        Row {
                            TextButton(onClick = { 
                                categoryInputName = category.name
                                categoryToEdit = category 
                            }) {
                                Text("Edit")
                            }
                            if (!category.name.equals("Uncategorized", ignoreCase = true)) {
                                TextButton(onClick = { viewModel.deleteCategory(category) }) {
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
            }

            Divider()

            Text(
                "AltaVox Radio 1.0 by Oktavianus Ari",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }

    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text("Add Category") },
            text = {
                OutlinedTextField(
                    value = categoryInputName,
                    onValueChange = { categoryInputName = it },
                    label = { Text("Category Name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (categoryInputName.isNotBlank()) {
                        viewModel.addCategory(categoryInputName)
                        showAddCategoryDialog = false
                    }
                }) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (categoryToEdit != null) {
        AlertDialog(
            onDismissRequest = { categoryToEdit = null },
            title = { Text("Edit Category") },
            text = {
                OutlinedTextField(
                    value = categoryInputName,
                    onValueChange = { categoryInputName = it },
                    label = { Text("Category Name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (categoryInputName.isNotBlank()) {
                        viewModel.updateCategory(categoryToEdit!!.copy(name = categoryInputName))
                        categoryToEdit = null
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToEdit = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ColorPickerRow(label: String, hexValue: String, onHexChanged: (String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(parseColor(hexValue, Color.Gray))
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), CircleShape)
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Select $label") },
            text = {
                val presetColors = listOf(
                    "#F44336", "#E91E63", "#9C27B0", "#673AB7", 
                    "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4", 
                    "#009688", "#4CAF50", "#8BC34A", "#CDDC39", 
                    "#FFEB3B", "#FFC107", "#FF9800", "#FF5722",
                    "#795548", "#9E9E9E", "#607D8B", "#000000", "#FFFFFF",
                    "#121212", "#1E1E1E", "#2C2C2C", "#6200EE", "#03DAC5"
                )
                
                Column {
                    val chunkedColors = presetColors.chunked(5)
                    chunkedColors.forEach { rowColors ->
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            rowColors.forEach { colorHex ->
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(parseColor(colorHex, Color.Gray))
                                        .border(1.dp, Color.Gray, CircleShape)
                                        .clickable {
                                            onHexChanged(colorHex)
                                            showDialog = false
                                        }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
