package com.ari.streamer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ari.streamer.data.AppDatabase
import com.ari.streamer.data.AppTheme
import com.ari.streamer.data.Category
import com.ari.streamer.data.Station
import com.ari.streamer.data.ThemeColors
import com.ari.streamer.data.UserPreferences
import com.ari.streamer.playback.PlaybackManager
import com.ari.streamer.util.M3uParser
import com.ari.streamer.util.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val stationDao = db.stationDao()
    private val userPreferences = UserPreferences(application)
    
    val playbackManager = PlaybackManager(application)

    val categories = stationDao.getAllCategories()
        .map { list ->
            list.sortedWith(Comparator { o1, o2 ->
                val name1 = o1.name.trim()
                val name2 = o2.name.trim()
                val isFav1 = name1.equals("Favourites", ignoreCase = true) || name1.equals("Favorites", ignoreCase = true)
                val isFav2 = name2.equals("Favourites", ignoreCase = true) || name2.equals("Favorites", ignoreCase = true)
                when {
                    isFav1 && isFav2 -> 0
                    isFav1 -> -1
                    isFav2 -> 1
                    else -> name1.compareTo(name2, ignoreCase = true)
                }
            })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stations = stationDao.getAllStations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val themeMode = userPreferences.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppTheme.SYSTEM)
        
    val lightThemeColors = userPreferences.lightThemeColorsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeColors("#FF6200EE", "#FF03DAC5", "#FFFFFFFF"))
        
    val darkThemeColors = userPreferences.darkThemeColorsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeColors("#FFBB86FC", "#FF03DAC5", "#FF121212"))

    val useLargerBuffer = userPreferences.useLargerBufferFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val tvGradientColor1 = userPreferences.tvGradientColor1Flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "#0F3E2E")

    val tvGradientColor2 = userPreferences.tvGradientColor2Flow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "#121212")

    val widget1BgColor = userPreferences.widget1BgColorFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "#151517")

    val widget1Opacity = userPreferences.widget1OpacityFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.9f)

    val widget2BgColor = userPreferences.widget2BgColorFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "#151517")

    val widget2Opacity = userPreferences.widget2OpacityFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.9f)

    val appLanguage = userPreferences.appLanguageFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "auto")

    val m3uUrl = userPreferences.m3uUrlFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "https://pastebin.com/raw/i4YM5tAL")

    enum class UpdateStatus { Idle, Loading, Success, Error }
    
    private val _updateStatus = MutableStateFlow(UpdateStatus.Idle)
    val updateStatus = _updateStatus.asStateFlow()

    fun updateAllWidgets() {
        val context = getApplication<Application>()
        val widgetManager = android.appwidget.AppWidgetManager.getInstance(context)
        
        // Large Widget
        val component1 = android.content.ComponentName(context, com.ari.streamer.widget.RadioWidgetProvider::class.java)
        val ids1 = widgetManager.getAppWidgetIds(component1)
        if (ids1.isNotEmpty()) {
            val intent = android.content.Intent(context, com.ari.streamer.widget.RadioWidgetProvider::class.java).apply {
                action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids1)
            }
            context.sendBroadcast(intent)
        }

        // Compact Widget
        val component2 = android.content.ComponentName(context, com.ari.streamer.widget.CompactRadioWidgetProvider::class.java)
        val ids2 = widgetManager.getAppWidgetIds(component2)
        if (ids2.isNotEmpty()) {
            val intent = android.content.Intent(context, com.ari.streamer.widget.CompactRadioWidgetProvider::class.java).apply {
                action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids2)
            }
            context.sendBroadcast(intent)
        }

        // Favorites Widget (Widget 3)
        val component3 = android.content.ComponentName(context, com.ari.streamer.widget.FavoriteIconsRadioWidgetProvider::class.java)
        val ids3 = widgetManager.getAppWidgetIds(component3)
        if (ids3.isNotEmpty()) {
            val intent = android.content.Intent(context, com.ari.streamer.widget.FavoriteIconsRadioWidgetProvider::class.java).apply {
                action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids3)
            }
            context.sendBroadcast(intent)
        }
    }

    fun resetUpdateStatus() {
        _updateStatus.value = UpdateStatus.Idle
    }

    fun playStation(station: Station) {
        playbackManager.playStation(station)
    }

    fun playNextStation() {
        val currentList = stations.value.sortedBy { it.name.lowercase() }
        if (currentList.isEmpty()) return
        val currentStation = playbackManager.playbackState.value.currentStation ?: return
        val currentIndex = currentList.indexOfFirst { it.id == currentStation.id }
        if (currentIndex != -1) {
            val nextIndex = if (currentIndex + 1 < currentList.size) currentIndex + 1 else 0
            playStation(currentList[nextIndex])
        }
    }

    fun playPreviousStation() {
        val currentList = stations.value.sortedBy { it.name.lowercase() }
        if (currentList.isEmpty()) return
        val currentStation = playbackManager.playbackState.value.currentStation ?: return
        val currentIndex = currentList.indexOfFirst { it.id == currentStation.id }
        if (currentIndex != -1) {
            val prevIndex = if (currentIndex - 1 >= 0) currentIndex - 1 else currentList.size - 1
            playStation(currentList[prevIndex])
        }
    }

    fun addStation(name: String, url: String, logoUrl: String?, categoryId: Long?) {
        viewModelScope.launch {
            val count = stations.value.size
            stationDao.insertStation(
                Station(
                    name = name,
                    streamUrl = url,
                    logoUrl = logoUrl,
                    categoryId = categoryId,
                    orderIndex = count
                )
            )
        }
    }

    fun addCategory(name: String) {
        viewModelScope.launch {
            val existing = stationDao.getCategoryByName(name.trim())
            if (existing == null) {
                val count = categories.value.size
                stationDao.insertCategory(Category(name = name.trim(), orderIndex = count))
            }
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch { stationDao.deleteCategory(category) }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch { stationDao.updateCategory(category) }
    }

    fun deleteStation(station: Station) {
        viewModelScope.launch { 
            stationDao.deleteStation(station)
            updateAllWidgets()
        }
    }

    fun deleteStations(ids: List<Long>) {
        viewModelScope.launch {
            stationDao.deleteStationsByIds(ids)
            updateAllWidgets()
        }
    }

    fun updateStation(station: Station) {
        viewModelScope.launch { stationDao.updateStation(station) }
    }

    fun setThemeMode(mode: AppTheme) {
        viewModelScope.launch { userPreferences.setThemeMode(mode) }
    }

    fun setLightColors(colors: ThemeColors) {
        viewModelScope.launch { userPreferences.setLightColors(colors) }
    }

    fun setDarkColors(colors: ThemeColors) {
        viewModelScope.launch { userPreferences.setDarkColors(colors) }
    }

    fun setUseLargerBuffer(use: Boolean) {
        viewModelScope.launch { userPreferences.setUseLargerBuffer(use) }
    }

    fun setTvGradientColor1(colorHex: String) {
        viewModelScope.launch { userPreferences.setTvGradientColor1(colorHex) }
    }

    fun setTvGradientColor2(colorHex: String) {
        viewModelScope.launch { userPreferences.setTvGradientColor2(colorHex) }
    }

    fun setWidget1BgColor(colorHex: String) {
        viewModelScope.launch { 
            userPreferences.setWidget1BgColor(colorHex)
            updateAllWidgets()
        }
    }

    fun setWidget1Opacity(opacity: Float) {
        viewModelScope.launch { 
            userPreferences.setWidget1Opacity(opacity)
            updateAllWidgets()
        }
    }

    fun setWidget2BgColor(colorHex: String) {
        viewModelScope.launch { 
            userPreferences.setWidget2BgColor(colorHex)
            updateAllWidgets()
        }
    }

    fun setWidget2Opacity(opacity: Float) {
        viewModelScope.launch { 
            userPreferences.setWidget2Opacity(opacity)
            updateAllWidgets()
        }
    }

    fun saveWidgetSettings(w1BgColor: String, w1Opacity: Float, w2BgColor: String, w2Opacity: Float) {
        viewModelScope.launch {
            userPreferences.setWidget1BgColor(w1BgColor)
            userPreferences.setWidget1Opacity(w1Opacity)
            userPreferences.setWidget2BgColor(w2BgColor)
            userPreferences.setWidget2Opacity(w2Opacity)
            updateAllWidgets()
        }
    }

    fun setAppLanguage(lang: String) {
        viewModelScope.launch { userPreferences.setAppLanguage(lang) }
    }

    fun setM3uUrl(url: String) {
        viewModelScope.launch { userPreferences.setM3uUrl(url) }
    }

    suspend fun importFromM3u(inputStream: InputStream) = withContext(Dispatchers.IO) {
        val entries = M3uParser.parse(inputStream)
        
        // Clear the database completely to perform a fresh and clean restore
        stationDao.clearAllStations()
        stationDao.clearAllCategories()

        val newCategoriesMap = mutableMapOf<String, Long>()
        var stationCounter = 0

        entries.forEach { entry ->
            val catName = entry.categoryName?.trim() ?: "Uncategorized"
            val catKey = catName.lowercase()
            val catId = newCategoriesMap[catKey] ?: run {
                val id = stationDao.insertCategory(
                    Category(
                        name = catName,
                        orderIndex = newCategoriesMap.size
                    )
                )
                newCategoriesMap[catKey] = id
                id
            }

            stationDao.insertStation(
                Station(
                    name = entry.title,
                    streamUrl = entry.url,
                    logoUrl = entry.logoUrl,
                    categoryId = catId,
                    orderIndex = stationCounter++
                )
            )
        }
    }

    fun updateFromRemoteM3u(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _updateStatus.value = UpdateStatus.Loading
            try {
                val inputStream = NetworkClient.downloadM3u(url)
                if (inputStream != null) {
                    val entries = M3uParser.parse(inputStream)
                    
                    // Clear all existing categories and stations as requested
                    stationDao.clearAllStations()
                    stationDao.clearAllCategories()
                    
                    val newCategoriesMap = mutableMapOf<String, Long>()
                    var stationCounter = 0

                    entries.forEach { entry ->
                        val catName = entry.categoryName?.trim() ?: "Uncategorized"
                        val catKey = catName.lowercase()
                        val catId = newCategoriesMap[catKey] ?: run {
                            val id = stationDao.insertCategory(
                                Category(
                                    name = catName,
                                    orderIndex = newCategoriesMap.size
                                )
                            )
                            newCategoriesMap[catKey] = id
                            id
                        }

                        stationDao.insertStation(
                            Station(
                                name = entry.title,
                                streamUrl = entry.url,
                                logoUrl = entry.logoUrl,
                                categoryId = catId,
                                orderIndex = stationCounter++
                            )
                        )
                    }
                    _updateStatus.value = UpdateStatus.Success
                } else {
                    _updateStatus.value = UpdateStatus.Error
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _updateStatus.value = UpdateStatus.Error
            }
        }
    }

    suspend fun exportToM3u(outputStream: java.io.OutputStream) = withContext(Dispatchers.IO) {
        val stations = stationDao.getAllStations().first()
        val categories = stationDao.getAllCategories().first().associateBy { it.id }

        outputStream.bufferedWriter().use { writer ->
            writer.write("#EXTM3U\n\n")
            stations.forEach { station ->
                val categoryName = categories[station.categoryId]?.name ?: "Uncategorized"
                val logoUrl = station.logoUrl ?: ""
                writer.write("#EXTINF:-1 group-title=\"$categoryName\" tvg-logo=\"$logoUrl\",${station.name}\n")
                writer.write("${station.streamUrl}\n\n")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        playbackManager.release()
    }
}
