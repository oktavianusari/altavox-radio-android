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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val stationDao = db.stationDao()
    private val userPreferences = UserPreferences(application)
    
    val playbackManager = PlaybackManager(application)

    val categories = stationDao.getAllCategories()
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

    fun updateFromRemoteM3u(url: String) {
        viewModelScope.launch {
            _updateStatus.value = UpdateStatus.Loading
            try {
                val inputStream = NetworkClient.downloadM3u(url)
                if (inputStream != null) {
                    val entries = M3uParser.parse(inputStream)
                    
                    val currentCategories = stationDao.getAllCategories().first()
                    val existingStations = stationDao.getAllStations().first().associateBy { it.streamUrl }
                    val existingCategories = currentCategories.associateBy { it.name.trim() }
                    val newCategoriesMap = mutableMapOf<String, Long>()

                    entries.forEach { entry ->
                        val catName = entry.categoryName?.trim() ?: "Uncategorized"
                        val catId = existingCategories[catName]?.id ?: newCategoriesMap[catName] ?: run {
                            // double-check with database just in case
                            val dbCat = stationDao.getCategoryByName(catName)
                            if (dbCat != null) {
                                newCategoriesMap[catName] = dbCat.id
                                dbCat.id
                            } else {
                                val id = stationDao.insertCategory(Category(name = catName, orderIndex = existingCategories.size + newCategoriesMap.size))
                                newCategoriesMap[catName] = id
                                id
                            }
                        }

                        if (!existingStations.containsKey(entry.url)) {
                            stationDao.insertStation(
                                Station(
                                    name = entry.title,
                                    streamUrl = entry.url,
                                    logoUrl = entry.logoUrl,
                                    categoryId = catId,
                                    orderIndex = existingStations.size // simple order
                                )
                            )
                        } else {
                            val existing = existingStations[entry.url]!!
                            stationDao.updateStation(
                                existing.copy(
                                    name = entry.title,
                                    logoUrl = entry.logoUrl,
                                    categoryId = catId
                                )
                            )
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

    override fun onCleared() {
        super.onCleared()
        playbackManager.release()
    }
}
