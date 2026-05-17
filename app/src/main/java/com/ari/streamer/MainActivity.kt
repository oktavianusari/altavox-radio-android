package com.ari.streamer

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ari.streamer.data.AppTheme
import com.ari.streamer.service.RadioPlaybackService
import com.ari.streamer.ui.MainScreen
import com.ari.streamer.ui.MainViewModel
import com.ari.streamer.ui.SettingsScreen
import com.ari.streamer.ui.theme.StreamerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val createDocumentLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        uri?.let {
            lifecycleScope.launch {
                try {
                    contentResolver.openOutputStream(it)?.use { outputStream ->
                        viewModel.exportToM3u(outputStream)
                    }
                    Toast.makeText(this@MainActivity, "Backup saved", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Backup failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val openDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            lifecycleScope.launch {
                try {
                    contentResolver.openInputStream(it)?.use { inputStream ->
                        viewModel.importFromM3u(inputStream)
                    }
                    Toast.makeText(this@MainActivity, "Restore successful", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Restore failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Detect if running on TV and redirect to TvActivity
        val uiModeManager = getSystemService(android.content.Context.UI_MODE_SERVICE) as android.app.UiModeManager
        if (uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION) {
            startActivity(Intent(this, TvActivity::class.java))
            finish()
            return
        }

        // Start foreground service if needed or let ExoPlayer handle it
        val intent = Intent(this, RadioPlaybackService::class.java)
        startService(intent)

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val lightColors by viewModel.lightThemeColors.collectAsState()
            val darkColors by viewModel.darkThemeColors.collectAsState()

            val isDarkTheme = when (themeMode) {
                AppTheme.SYSTEM -> isSystemInDarkTheme()
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
            }

            val currentColors = if (isDarkTheme) darkColors else lightColors

            StreamerTheme(
                darkTheme = isDarkTheme,
                primaryColorHex = currentColors.primaryHex,
                secondaryColorHex = currentColors.secondaryHex,
                backgroundColorHex = currentColors.backgroundHex
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = "main") {
                        composable("main") {
                            MainScreen(
                                viewModel = viewModel,
                                onNavigateToSettings = { navController.navigate("settings") }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onBackupClick = {
                                    createDocumentLauncher.launch("backup_radio.m3u")
                                },
                                onRestoreClick = {
                                    openDocumentLauncher.launch(arrayOf("*/*"))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
