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
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.ari.streamer.R
import com.ari.streamer.data.AppTheme
import com.ari.streamer.data.ThemeColors
import com.ari.streamer.ui.theme.parseColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToHelp: () -> Unit,
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val lightColors by viewModel.lightThemeColors.collectAsState()
    val darkColors by viewModel.darkThemeColors.collectAsState()
    val useLargerBuffer by viewModel.useLargerBuffer.collectAsState()
    val categories by viewModel.categories.collectAsState()
    
    val widget1BgColor by viewModel.widget1BgColor.collectAsState()
    val widget1Opacity by viewModel.widget1Opacity.collectAsState()
    val widget2BgColor by viewModel.widget2BgColor.collectAsState()
    val widget2Opacity by viewModel.widget2Opacity.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()

    var localWidget1BgColor by remember(widget1BgColor) { mutableStateOf(widget1BgColor) }
    var localWidget1Opacity by remember(widget1Opacity) { mutableStateOf(widget1Opacity) }
    var localWidget2BgColor by remember(widget2BgColor) { mutableStateOf(widget2BgColor) }
    var localWidget2Opacity by remember(widget2Opacity) { mutableStateOf(widget2Opacity) }
    
    val savedM3uUrl by viewModel.m3uUrl.collectAsState()
    var m3uUrl by remember(savedM3uUrl) { mutableStateOf(savedM3uUrl) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<com.ari.streamer.data.Category?>(null) }
    var categoryInputName by remember { mutableStateOf("") }
    var showWarningDialog by remember { mutableStateOf(false) }
    
    val updateStatus by viewModel.updateStatus.collectAsState()
    val context = LocalContext.current

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
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
            Text(stringResource(R.string.remote_m3u_update), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = m3uUrl,
                onValueChange = { m3uUrl = it },
                label = { Text(stringResource(R.string.m3u_url)) },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    if (m3uUrl.isNotBlank()) {
                        showWarningDialog = true
                    } else {
                        Toast.makeText(context, context.getString(R.string.url_cannot_be_blank), Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = updateStatus != MainViewModel.UpdateStatus.Loading
            ) {
                if (updateStatus == MainViewModel.UpdateStatus.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp).padding(end = 8.dp),
                        strokeWidth = 2.dp
                    )
                    Text(stringResource(R.string.updating))
                } else {
                    Text(stringResource(R.string.update_m3u_button))
                }
            }

            Divider()

            Text(stringResource(R.string.theme_mode), style = MaterialTheme.typography.titleMedium)
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

            Text(stringResource(R.string.light_theme_colors), style = MaterialTheme.typography.titleMedium)
            ColorPickerRow(stringResource(R.string.color_primary), lightColors.primaryHex) { newHex ->
                viewModel.setLightColors(lightColors.copy(primaryHex = newHex))
            }
            ColorPickerRow(stringResource(R.string.color_secondary), lightColors.secondaryHex) { newHex ->
                viewModel.setLightColors(lightColors.copy(secondaryHex = newHex))
            }
            ColorPickerRow(stringResource(R.string.color_background), lightColors.backgroundHex) { newHex ->
                viewModel.setLightColors(lightColors.copy(backgroundHex = newHex))
            }

            Divider()

            Text(stringResource(R.string.dark_theme_colors), style = MaterialTheme.typography.titleMedium)
            ColorPickerRow(stringResource(R.string.color_primary), darkColors.primaryHex) { newHex ->
                viewModel.setDarkColors(darkColors.copy(primaryHex = newHex))
            }
            ColorPickerRow(stringResource(R.string.color_secondary), darkColors.secondaryHex) { newHex ->
                viewModel.setDarkColors(darkColors.copy(secondaryHex = newHex))
            }
            ColorPickerRow(stringResource(R.string.color_background), darkColors.backgroundHex) { newHex ->
                viewModel.setDarkColors(darkColors.copy(backgroundHex = newHex))
            }

            Divider()

            // 1. & 2. WIDGET SETTINGS
            Text(stringResource(R.string.widget_settings), style = MaterialTheme.typography.titleMedium)
            
            Text(
                text = stringResource(R.string.widget_settings_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(vertical = 4.dp)
            )
            
            // Unified Widgets Background and Opacity
            ColorPickerRow(stringResource(R.string.widget_bg), localWidget1BgColor) { newHex ->
                localWidget1BgColor = newHex
            }
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.widget_opacity_label), style = MaterialTheme.typography.bodyMedium)
                    Text("${(localWidget1Opacity * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                }
                Slider(
                    value = localWidget1Opacity,
                    onValueChange = { localWidget1Opacity = it },
                    valueRange = 0.0f..1.0f
                )
            }

            Button(
                onClick = {
                    viewModel.saveWidgetSettings(
                        localWidget1BgColor,
                        localWidget1Opacity,
                        localWidget1BgColor,
                        localWidget1Opacity
                    )
                    Toast.makeText(context, context.getString(R.string.widget_settings_saved), Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(stringResource(R.string.save_widget_settings), color = MaterialTheme.colorScheme.onPrimary)
            }

            Divider()

            // 4. LANGUAGE SETTINGS
            Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilterChip(
                    selected = appLanguage == "auto",
                    onClick = { viewModel.setAppLanguage("auto") },
                    label = { Text(stringResource(R.string.language_system)) }
                )
                FilterChip(
                    selected = appLanguage == "en",
                    onClick = { viewModel.setAppLanguage("en") },
                    label = { Text("English") }
                )
                FilterChip(
                    selected = appLanguage == "id",
                    onClick = { viewModel.setAppLanguage("id") },
                    label = { Text("Indonesia") }
                )
            }

            Divider()

            Text(stringResource(R.string.backup_restore), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onBackupClick) { Text(stringResource(R.string.backup_to_m3u)) }
                Button(onClick = onRestoreClick) { Text(stringResource(R.string.restore_from_m3u)) }
            }
            
            Divider()
            
            Text(stringResource(R.string.advanced_playback), style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.use_larger_buffer))
                Switch(
                    checked = useLargerBuffer,
                    onCheckedChange = { viewModel.setUseLargerBuffer(it) }
                )
            }

            Divider()

            Text(stringResource(R.string.manage_categories), style = MaterialTheme.typography.titleMedium)
            Button(onClick = { 
                categoryInputName = ""
                showAddCategoryDialog = true 
            }) {
                Text(stringResource(R.string.add_new_category))
            }

            if (categories.isEmpty()) {
                Text(stringResource(R.string.no_categories), style = MaterialTheme.typography.bodyMedium)
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
                                Text(stringResource(R.string.edit))
                            }
                            if (!category.name.equals("Uncategorized", ignoreCase = true)) {
                                TextButton(onClick = { viewModel.deleteCategory(category) }) {
                                    Text(stringResource(R.string.delete))
                                }
                            }
                        }
                    }
                }
            }

            Divider()

            // Clickable Help list item that navigates to the dedicated Help Screen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToHelp() }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = stringResource(R.string.help),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.help_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                "AltaVox Radio 1.1 by Oktavianus Ari",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
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
                        viewModel.setM3uUrl(m3uUrl)
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

    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text(stringResource(R.string.add_category_title)) },
            text = {
                OutlinedTextField(
                    value = categoryInputName,
                    onValueChange = { categoryInputName = it },
                    label = { Text(stringResource(R.string.category_name_label)) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (categoryInputName.isNotBlank()) {
                        viewModel.addCategory(categoryInputName)
                        showAddCategoryDialog = false
                    }
                }) {
                    Text(stringResource(R.string.add_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (categoryToEdit != null) {
        AlertDialog(
            onDismissRequest = { categoryToEdit = null },
            title = { Text(stringResource(R.string.edit_category_title)) },
            text = {
                OutlinedTextField(
                    value = categoryInputName,
                    onValueChange = { categoryInputName = it },
                    label = { Text(stringResource(R.string.category_name_label)) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (categoryInputName.isNotBlank()) {
                        viewModel.updateCategory(categoryToEdit!!.copy(name = categoryInputName))
                        categoryToEdit = null
                    }
                }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToEdit = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

fun isValidHexColor(colorStr: String): Boolean {
    val clean = colorStr.trim()
    val pattern = "^#?([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$".toRegex()
    return clean.matches(pattern)
}

fun formatHexColor(colorStr: String): String {
    val clean = colorStr.trim()
    return if (clean.startsWith("#")) clean else "#$clean"
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
            title = { Text(stringResource(R.string.select_color_title, label)) },
            text = {
                val presetColors = listOf(
                    "#F44336", "#E91E63", "#9C27B0", "#673AB7", 
                    "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4", 
                    "#009688", "#4CAF50", "#8BC34A", "#CDDC39", 
                    "#FFEB3B", "#FFC107", "#FF9800", "#FF5722",
                    "#795548", "#9E9E9E", "#607D8B", "#000000", "#FFFFFF",
                    "#121212", "#1E1E1E", "#2C2C2C", "#6200EE", "#03DAC5"
                )
                
                var customHex by remember { mutableStateOf(hexValue) }
                var isError by remember { mutableStateOf(!isValidHexColor(hexValue)) }

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.custom_hex_code),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = customHex,
                            onValueChange = {
                                customHex = it
                                isError = !isValidHexColor(it)
                            },
                            placeholder = { Text("#RRGGBB / #AARRGGBB") },
                            isError = isError,
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.hex_code_label)) }
                        )
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (!isError && isValidHexColor(customHex)) 
                                        parseColor(formatHexColor(customHex), Color.Gray)
                                    else Color.Gray
                                )
                                .border(1.dp, Color.Gray, CircleShape)
                        )
                    }
                    if (isError) {
                        Text(
                            text = stringResource(R.string.invalid_hex_format),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Button(
                        onClick = {
                            if (!isError && isValidHexColor(customHex)) {
                                onHexChanged(formatHexColor(customHex))
                                showDialog = false
                            }
                        },
                        enabled = !isError && customHex.isNotBlank(),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(R.string.apply))
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        text = stringResource(R.string.predefined_colors),
                        style = MaterialTheme.typography.titleSmall
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
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.close_btn))
                }
            }
        )
    }
}
