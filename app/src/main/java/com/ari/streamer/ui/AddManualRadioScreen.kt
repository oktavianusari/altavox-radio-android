package com.ari.streamer.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ari.streamer.R
import com.ari.streamer.data.Station

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddManualRadioScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val categories by viewModel.categories.collectAsState()
    val context = LocalContext.current

    var name by remember { mutableStateOf("") }
    var streamUrl by remember { mutableStateOf("") }
    var logoUrl by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var categoryExpanded by remember { mutableStateOf(false) }

    val currentCategoryName = categories.find { it.id == categoryId }?.name ?: stringResource(R.string.uncategorized)
    
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_manual_radio_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.8f)
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    )
                ),
            contentAlignment = Alignment.TopCenter
        ) {
            // Card Content with Glassmorphic design
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = if (isTablet) 500.dp else 400.dp)
                    .padding(16.dp)
                    .padding(top = 16.dp)
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.15f),
                                    Color.White.copy(alpha = 0.03f)
                                )
                            ),
                            shape = RoundedCornerShape(24.dp)
                        ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {


                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(stringResource(R.string.name_label)) },
                            placeholder = { Text(stringResource(R.string.name_placeholder)) },
                            supportingText = { Text(stringResource(R.string.name_supporting_text)) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = streamUrl,
                            onValueChange = { streamUrl = it },
                            label = { Text(stringResource(R.string.stream_url_label)) },
                            placeholder = { Text(stringResource(R.string.url_placeholder)) },
                            supportingText = { Text(stringResource(R.string.url_supporting_text)) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = logoUrl,
                            onValueChange = { logoUrl = it },
                            label = { Text(stringResource(R.string.logo_url_label)) },
                            placeholder = { Text(stringResource(R.string.logo_placeholder)) },
                            supportingText = { Text(stringResource(R.string.logo_supporting_text)) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        ExposedDropdownMenuBox(
                            expanded = categoryExpanded,
                            onExpandedChange = { categoryExpanded = !categoryExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                                OutlinedTextField(
                                value = currentCategoryName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.category_label)) },
                                supportingText = { Text(stringResource(R.string.category_supporting_text)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = categoryExpanded,
                                onDismissRequest = { categoryExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.uncategorized)) },
                                    onClick = {
                                        categoryId = null
                                        categoryExpanded = false
                                    }
                                )
                                categories.filter { !it.name.equals("Uncategorized", ignoreCase = true) && !it.name.equals("Favourites", ignoreCase = true) && !it.name.equals("Favorites", ignoreCase = true) }.forEach { category ->
                                    DropdownMenuItem(
                                        text = { Text(category.name) },
                                        onClick = {
                                            categoryId = category.id
                                            categoryExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                if (name.isBlank()) {
                                    Toast.makeText(context, context.getString(R.string.name_cannot_be_blank), Toast.LENGTH_SHORT).show()
                                } else if (streamUrl.isBlank()) {
                                    Toast.makeText(context, context.getString(R.string.url_cannot_be_blank), Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.addStation(
                                        name = name.trim(),
                                        url = streamUrl.trim(),
                                        logoUrl = logoUrl.trim().ifBlank { null },
                                        categoryId = categoryId
                                    )
                                    Toast.makeText(context, context.getString(R.string.radio_added_successfully), Toast.LENGTH_SHORT).show()
                                    onNavigateBack()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            }
        }
    }
}
