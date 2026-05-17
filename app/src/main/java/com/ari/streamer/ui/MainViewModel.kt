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

    enum class UpdateStatus { Idle, Loading, Success, Error }
    
    private val _updateStatus = MutableStateFlow(UpdateStatus.Idle)
    val updateStatus = _updateStatus.asStateFlow()

    fun resetUpdateStatus() {
        _updateStatus.value = UpdateStatus.Idle
    }

    fun playStation(station: Station) {
        playbackManager.playStation(station)
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
        viewModelScope.launch { stationDao.deleteStation(station) }
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
                    
                    val currentCategories = stationDao.getAllCategories().first()
                    val existingStations = stationDao.getAllStations().first().associateBy { it.streamUrl }.toMutableMap()
                    val existingCategories = currentCategories.associateBy { it.name.trim().lowercase() }.toMutableMap()
                    val newCategoriesMap = mutableMapOf<String, Long>()

                    entries.forEach { entry ->
                        val catName = entry.categoryName?.trim() ?: "Uncategorized"
                        val catKey = catName.lowercase()
                        val catId = existingCategories[catKey]?.id ?: newCategoriesMap[catKey] ?: run {
                            val dbCat = stationDao.getCategoryByName(catName)
                            if (dbCat != null) {
                                newCategoriesMap[catKey] = dbCat.id
                                dbCat.id
                            } else {
                                val id = stationDao.insertCategory(
                                    Category(
                                        name = catName,
                                        orderIndex = existingCategories.size + newCategoriesMap.size
                                    )
                                )
                                newCategoriesMap[catKey] = id
                                id
                            }
                        }

                        if (!existingStations.containsKey(entry.url)) {
                            val newId = stationDao.insertStation(
                                Station(
                                    name = entry.title,
                                    streamUrl = entry.url,
                                    logoUrl = entry.logoUrl,
                                    categoryId = catId,
                                    orderIndex = existingStations.size
                                )
                            )
                            existingStations[entry.url] = Station(
                                id = newId,
                                name = entry.title,
                                streamUrl = entry.url,
                                logoUrl = entry.logoUrl,
                                categoryId = catId,
                                orderIndex = existingStations.size
                            )
                        } else {
                            val existing = existingStations[entry.url]!!
                            val updated = existing.copy(
                                name = entry.title,
                                logoUrl = entry.logoUrl,
                                categoryId = catId
                            )
                            stationDao.updateStation(updated)
                            existingStations[entry.url] = updated
                        }
                    }
                    _updateStatus.value = UpdateStatus.Success
                } else {
                    _updateStatus.value = UpdateStatus.Error
                }
            } catch (e: Exception) {
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
