package com.portalphotos.app.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.portalphotos.app.data.cache.CacheManager
import com.portalphotos.app.data.crop.SmartCropDetector
import com.portalphotos.app.data.db.PortalPhotosDatabase
import com.portalphotos.app.data.model.AlbumEntity
import com.portalphotos.app.data.model.MediaItem
import com.portalphotos.app.data.prefs.AppPreferences
import com.portalphotos.app.data.prefs.UserSettings
import com.portalphotos.app.data.repository.AlbumRepository
import com.portalphotos.app.data.server.LocalWebServer
import com.portalphotos.app.data.server.NetworkUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ViewerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = PortalPhotosDatabase.getDatabase(application)
    val repository = AlbumRepository(db.albumDao())
    val preferences = AppPreferences(application)
    val cacheManager = CacheManager(application)
    private val smartCropDetector = SmartCropDetector()

    private val localWebServer = LocalWebServer(repository, port = 12345)

    private val _localWebUrl = MutableStateFlow<String?>(null)
    val localWebUrl: StateFlow<String?> = _localWebUrl.asStateFlow()

    val userSettings: StateFlow<UserSettings> = preferences.userSettingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    val allAlbums: StateFlow<List<AlbumEntity>> = repository.allAlbums
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val rawMediaItems: StateFlow<List<MediaItem>> = repository.mediaItemsForSelectedAlbums
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val displayMediaItems: StateFlow<List<MediaItem>> = combine(rawMediaItems, userSettings) { items, settings ->
        if (settings.shuffleMode) {
            items.shuffled()
        } else {
            items
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _isAudioMuted = MutableStateFlow(true)
    val isAudioMuted: StateFlow<Boolean> = _isAudioMuted.asStateFlow()

    private val _isPlaying = MutableStateFlow(true)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    init {
        // Sync mute state from initial user settings
        viewModelScope.launch {
            userSettings.collect { settings ->
                _isAudioMuted.value = (settings.soundMode == com.portalphotos.app.data.prefs.SoundMode.MUTED)
            }
        }

        // Start Local Web Server on port 12345
        try {
            localWebServer.start()
            val ip = NetworkUtils.getLocalIpAddress()
            if (ip != null) {
                _localWebUrl.value = "http://$ip:12345"
            } else {
                _localWebUrl.value = "http://localhost:12345"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Periodic Background Album Refresh Loop (Every 1 hour)
        viewModelScope.launch {
            while (true) {
                delay(3600_000L) // 1 hour
                try {
                    repository.refreshAllAlbums()
                } catch (e: Exception) {
                    // Ignore background sync errors
                }
            }
        }
    }

    fun toggleAudioMute() {
        _isAudioMuted.value = !_isAudioMuted.value
    }

    fun togglePlayPause() {
        _isPlaying.value = !_isPlaying.value
    }

    fun pausePlayback() {
        _isPlaying.value = false
    }

    fun resumePlayback() {
        _isPlaying.value = true
    }

    fun addAlbumUrl(url: String) {
        _uiMessage.value = "Adding album... Syncing photos in background"
        repository.addAlbumUrlAsync(url) { result ->
            result.onSuccess { album ->
                _uiMessage.value = "Added album: ${album.title}"
            }.onFailure { err ->
                _uiMessage.value = "Error loading album: ${err.localizedMessage}"
            }
        }
    }

    fun toggleAlbumSelection(albumId: String, isSelected: Boolean) {
        viewModelScope.launch {
            repository.toggleAlbumSelection(albumId, isSelected)
        }
    }

    fun deleteAlbum(albumId: String) {
        viewModelScope.launch {
            repository.deleteAlbum(albumId)
        }
    }

    fun clearUiMessage() {
        _uiMessage.value = null
    }

    suspend fun getFocalPoint(url: String, bitmap: Bitmap): Offset {
        val cached = cacheManager.focalPointCache[url]
        if (cached != null) return cached

        val focal = smartCropDetector.calculateFocalPoint(bitmap)
        cacheManager.focalPointCache[url] = focal
        return focal
    }

    fun preloadWindow(currentIndex: Int) {
        val items = displayMediaItems.value
        val depth = userSettings.value.preloadDepth
        cacheManager.preloadItems(items, currentIndex, lookaheadDepth = depth)
    }

    override fun onCleared() {
        super.onCleared()
        localWebServer.stop()
        cacheManager.release()
        smartCropDetector.close()
    }
}
