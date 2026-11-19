package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.LootDropApplication
import com.example.data.local.AppDatabase
import com.example.data.model.CollectionsFilter
import com.example.data.model.HostServerConfig
import com.example.data.model.MainTab
import com.example.data.model.MediaItem
import com.example.data.model.MediaLibraryStats
import com.example.data.model.MediaSortOption
import com.example.data.model.MediaSourceType
import com.example.data.model.MediaType
import com.example.data.model.PlayerResizeMode
import com.example.data.model.UserProfile
import com.example.data.model.WatchStatus
import com.example.data.remote.ApiClient
import com.example.data.repository.MediaRepository
import com.example.i18n.AppLanguage
import com.example.i18n.LocaleSyncHelper
import com.example.player.MediaPlaybackController
import com.example.player.PlaybackUiState
import com.example.preferences.AccentTheme
import com.example.preferences.AppPreferences
import com.example.preferences.BackgroundTheme
import com.example.preferences.CardDensity
import com.example.preferences.DpiScaleMode
import com.example.preferences.TargetFrameRate
import com.example.preferences.ThemeMode
import com.example.util.CustomFontHelper
import com.example.util.MediaBackupManager
import com.example.util.UserProfileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MediaStorageMetrics(
    val diskCacheBytes: Long = 0L,
    val httpCacheBytes: Long = 0L,
    val databaseBytes: Long = 0L
) {
    val totalBytes: Long get() = diskCacheBytes + httpCacheBytes + databaseBytes
    val formattedTotal: String get() = formatBytes(totalBytes)
    val formattedImageCache: String get() = formatBytes(diskCacheBytes)
    val formattedHttpCache: String get() = formatBytes(httpCacheBytes)
    val formattedDatabase: String get() = formatBytes(databaseBytes)

    private fun formatBytes(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(java.util.Locale.US, "%.0f KB", kb)
            else -> "$bytes B"
        }
    }
}

class LootStreamViewModel(application: Application) : AndroidViewModel(application) {

    val preferences = AppPreferences(application)
    val repository = MediaRepository(application)
    val playbackController = MediaPlaybackController(application, repository, preferences)

    val playbackState: StateFlow<PlaybackUiState> = playbackController.uiState

    val allMediaItems: StateFlow<List<MediaItem>> = repository.observeMediaItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val servers: StateFlow<List<HostServerConfig>> = repository.observeServers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val stats: StateFlow<MediaLibraryStats> = combine(allMediaItems, servers) { items, srvList ->
        repository.computeStats(items, srvList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), MediaLibraryStats())

    // Navigation & Filtering
    private val _selectedTab = MutableStateFlow(preferences.defaultTab)
    val selectedTab: StateFlow<MainTab> = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearchVisible = MutableStateFlow(false)
    val isSearchVisible: StateFlow<Boolean> = _isSearchVisible.asStateFlow()

    private val _sourceFilter = MutableStateFlow(preferences.defaultSourceFilter)
    val sourceFilter: StateFlow<MediaSourceType> = _sourceFilter.asStateFlow()

    private val _mediaTypeFilter = MutableStateFlow(MediaType.ALL)
    val mediaTypeFilter: StateFlow<MediaType> = _mediaTypeFilter.asStateFlow()

    private val _watchStatusFilter = MutableStateFlow<WatchStatus?>(null)
    val watchStatusFilter: StateFlow<WatchStatus?> = _watchStatusFilter.asStateFlow()

    private val _sortOption = MutableStateFlow(MediaSortOption.RECENTLY_PLAYED)
    val sortOption: StateFlow<MediaSortOption> = _sortOption.asStateFlow()

    private val _collectionsFilter = MutableStateFlow(preferences.collectionsFilter)
    val collectionsFilter: StateFlow<CollectionsFilter> = _collectionsFilter.asStateFlow()

    val filteredLibraryItems: StateFlow<List<MediaItem>> = combine(
        allMediaItems,
        _searchQuery,
        _sourceFilter,
        _mediaTypeFilter,
        _watchStatusFilter
    ) { items, query, src, type, status ->
        FilterParams(items, query, src, type, status)
    }.combine(_sortOption) { params, sort ->
        MediaFilterEngine.filterAndSort(
            items = params.items,
            searchQuery = params.query,
            sourceFilter = params.source,
            mediaTypeFilter = params.type,
            watchStatusFilter = params.status,
            sortOption = sort
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    private data class FilterParams(
        val items: List<MediaItem>,
        val query: String,
        val source: MediaSourceType,
        val type: MediaType,
        val status: WatchStatus?
    )

    val continueWatchingItems: StateFlow<List<MediaItem>> = allMediaItems.combine(_searchQuery) { items, _ ->
        items.filter { it.isResumeAvailable || it.watchStatus == WatchStatus.IN_PROGRESS }
            .sortedByDescending { it.lastPlayedAt }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val recentlyAddedItems: StateFlow<List<MediaItem>> = allMediaItems.combine(_searchQuery) { items, _ ->
        items.sortedByDescending { it.addedAt }.take(20)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val upNextQueueItems: StateFlow<List<MediaItem>> = allMediaItems.combine(_searchQuery) { items, query ->
        val q = query.trim().lowercase()
        items.filter {
            (it.isWatchlist && it.watchStatus != WatchStatus.WATCHED) &&
                (q.isEmpty() || it.title.lowercase().contains(q) || it.subtitle.lowercase().contains(q))
        }.sortedByDescending { it.addedAt }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val recentlyWatchedItems: StateFlow<List<MediaItem>> = allMediaItems.combine(_searchQuery) { items, query ->
        val q = query.trim().lowercase()
        items.filter {
            it.watchStatus == WatchStatus.WATCHED &&
                (q.isEmpty() || it.title.lowercase().contains(q) || it.subtitle.lowercase().contains(q))
        }.sortedByDescending { it.lastPlayedAt }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val collectionsItems: StateFlow<List<MediaItem>> = combine(
        allMediaItems,
        _collectionsFilter,
        _searchQuery
    ) { items, filter, query ->
        MediaFilterEngine.filterCollections(items, filter, query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    // Status & Dialogs
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _selectedMediaDetail = MutableStateFlow<MediaItem?>(null)
    val selectedMediaDetail: StateFlow<MediaItem?> = _selectedMediaDetail.asStateFlow()

    private val _showAddMediaSheet = MutableStateFlow(false)
    val showAddMediaSheet: StateFlow<Boolean> = _showAddMediaSheet.asStateFlow()

    private val _showHostDialog = MutableStateFlow(false)
    val showHostDialog: StateFlow<Boolean> = _showHostDialog.asStateFlow()

    private val _showStreamUrlDialog = MutableStateFlow(false)
    val showStreamUrlDialog: StateFlow<Boolean> = _showStreamUrlDialog.asStateFlow()

    private val _showFilterSheet = MutableStateFlow(false)
    val showFilterSheet: StateFlow<Boolean> = _showFilterSheet.asStateFlow()

    private val _showProfileDialog = MutableStateFlow(false)
    val showProfileDialog: StateFlow<Boolean> = _showProfileDialog.asStateFlow()

    private val _showInitialLanguageDialog = MutableStateFlow(!preferences.hasSelectedInitialLanguage)
    val showInitialLanguageDialog: StateFlow<Boolean> = _showInitialLanguageDialog.asStateFlow()

    private val _showOnboardingDialog = MutableStateFlow(preferences.hasSelectedInitialLanguage && !preferences.hasSeenOnboarding)
    val showOnboardingDialog: StateFlow<Boolean> = _showOnboardingDialog.asStateFlow()

    // Reactive Preferences
    private val _themeMode = MutableStateFlow(preferences.themeMode)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _accentTheme = MutableStateFlow(preferences.accentTheme)
    val accentTheme: StateFlow<AccentTheme> = _accentTheme.asStateFlow()

    private val _darkBackgroundTheme = MutableStateFlow(preferences.darkBackgroundTheme)
    val darkBackgroundTheme: StateFlow<BackgroundTheme> = _darkBackgroundTheme.asStateFlow()

    private val _lightBackgroundTheme = MutableStateFlow(preferences.lightBackgroundTheme)
    val lightBackgroundTheme: StateFlow<BackgroundTheme> = _lightBackgroundTheme.asStateFlow()

    private val _cardDensity = MutableStateFlow(preferences.cardDensity)
    val cardDensity: StateFlow<CardDensity> = _cardDensity.asStateFlow()

    private val _defaultTab = MutableStateFlow(preferences.defaultTab)
    val defaultTab: StateFlow<MainTab> = _defaultTab.asStateFlow()

    private val _defaultSourceFilter = MutableStateFlow(preferences.defaultSourceFilter)
    val defaultSourceFilter: StateFlow<MediaSourceType> = _defaultSourceFilter.asStateFlow()

    private val _defaultPlaybackSpeed = MutableStateFlow(preferences.defaultPlaybackSpeed)
    val defaultPlaybackSpeed: StateFlow<Float> = _defaultPlaybackSpeed.asStateFlow()

    private val _autoPlayNext = MutableStateFlow(preferences.autoPlayNext)
    val autoPlayNext: StateFlow<Boolean> = _autoPlayNext.asStateFlow()

    private val _autoSyncServersOnOpen = MutableStateFlow(preferences.autoSyncServersOnOpen)
    val autoSyncServersOnOpen: StateFlow<Boolean> = _autoSyncServersOnOpen.asStateFlow()

    private val _showDurationBadge = MutableStateFlow(preferences.showDurationBadge)
    val showDurationBadge: StateFlow<Boolean> = _showDurationBadge.asStateFlow()

    private val _showResumeBar = MutableStateFlow(preferences.showResumeBar)
    val showResumeBar: StateFlow<Boolean> = _showResumeBar.asStateFlow()

    private val _playerResizeMode = MutableStateFlow(preferences.playerResizeMode)
    val playerResizeMode: StateFlow<PlayerResizeMode> = _playerResizeMode.asStateFlow()

    private val _isAnimationsEnabled = MutableStateFlow(preferences.isAnimationsEnabled)
    val isAnimationsEnabled: StateFlow<Boolean> = _isAnimationsEnabled.asStateFlow()

    private val _isLowEndOptimization = MutableStateFlow(preferences.isLowEndOptimization)
    val isLowEndOptimization: StateFlow<Boolean> = _isLowEndOptimization.asStateFlow()

    private val _targetFrameRate = MutableStateFlow(preferences.targetFrameRate)
    val targetFrameRate: StateFlow<TargetFrameRate> = _targetFrameRate.asStateFlow()

    private val _isDpiOptimizationEnabled = MutableStateFlow(preferences.isDpiOptimizationEnabled)
    val isDpiOptimizationEnabled: StateFlow<Boolean> = _isDpiOptimizationEnabled.asStateFlow()

    private val _dpiScaleMode = MutableStateFlow(preferences.dpiScaleMode)
    val dpiScaleMode: StateFlow<DpiScaleMode> = _dpiScaleMode.asStateFlow()

    private val _isDataSaverMode = MutableStateFlow(preferences.isDataSaverMode)
    val isDataSaverMode: StateFlow<Boolean> = _isDataSaverMode.asStateFlow()

    private val _customFontPath = MutableStateFlow(preferences.customFontPath)
    val customFontPath: StateFlow<String> = _customFontPath.asStateFlow()

    private val _customFontName = MutableStateFlow(preferences.customFontName)
    val customFontName: StateFlow<String> = _customFontName.asStateFlow()

    private val _appLanguage = MutableStateFlow(preferences.appLanguage)
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    private val _userProfile = MutableStateFlow(UserProfileManager.getProfile(preferences))
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private val _storageMetrics = MutableStateFlow(MediaStorageMetrics())
    val storageMetrics: StateFlow<MediaStorageMetrics> = _storageMetrics.asStateFlow()

    private val _lastBackupStatus = MutableStateFlow(preferences.lastBackupStatus)
    val lastBackupStatus: StateFlow<String> = _lastBackupStatus.asStateFlow()

    private val _lastBackupTimestamp = MutableStateFlow(preferences.lastBackupTimestamp)
    val lastBackupTimestamp: StateFlow<Long> = _lastBackupTimestamp.asStateFlow()

    private val _detectedRefreshRate = MutableStateFlow(60)
    val detectedRefreshRate: StateFlow<Int> = _detectedRefreshRate.asStateFlow()

    fun setDetectedRefreshRate(hz: Int) {
        if (hz > 0) _detectedRefreshRate.value = hz
    }

    fun setDetectedScreenDpi(dpi: Int) {
        if (dpi > 0) preferences.detectedScreenDpi = dpi
    }

    init {
        refreshStorageMetrics()
        if (preferences.autoSyncServersOnOpen) {
            viewModelScope.launch {
                val existingServers = repository.getAllServersOnce()
                if (existingServers.isNotEmpty()) {
                    syncAllServers(silent = true)
                }
            }
        }
    }

    fun selectTab(tab: MainTab) {
        _selectedTab.value = tab
    }

    fun setSearchVisible(visible: Boolean) {
        _isSearchVisible.value = visible
        if (!visible) _searchQuery.value = ""
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSourceFilter(source: MediaSourceType) {
        _sourceFilter.value = source
    }

    fun setMediaTypeFilter(type: MediaType) {
        _mediaTypeFilter.value = type
    }

    fun setWatchStatusFilter(status: WatchStatus?) {
        _watchStatusFilter.value = status
    }

    fun setSortOption(option: MediaSortOption) {
        _sortOption.value = option
    }

    fun resetLibraryFilters() {
        _searchQuery.value = ""
        _sourceFilter.value = MediaSourceType.ALL
        _mediaTypeFilter.value = MediaType.ALL
        _watchStatusFilter.value = null
        _sortOption.value = MediaSortOption.RECENTLY_PLAYED
    }

    fun setCollectionsFilter(filter: CollectionsFilter) {
        preferences.collectionsFilter = filter
        _collectionsFilter.value = filter
    }

    fun openMediaDetail(item: MediaItem) {
        _selectedMediaDetail.value = item
    }

    fun dismissMediaDetail() {
        _selectedMediaDetail.value = null
    }

    fun setShowAddMediaSheet(show: Boolean) { _showAddMediaSheet.value = show }
    fun setShowHostDialog(show: Boolean) { _showHostDialog.value = show }
    fun setShowStreamUrlDialog(show: Boolean) { _showStreamUrlDialog.value = show }
    fun setShowFilterSheet(show: Boolean) { _showFilterSheet.value = show }
    fun setShowProfileDialog(show: Boolean) { _showProfileDialog.value = show }

    fun dismissStatusMessage() { _statusMessage.value = null }
    fun dismissErrorMessage() { _errorMessage.value = null }

    fun completeInitialLanguageSelection(language: AppLanguage) {
        setAppLanguage(language)
        preferences.hasSelectedInitialLanguage = true
        _showInitialLanguageDialog.value = false
        if (!preferences.hasSeenOnboarding) {
            _showOnboardingDialog.value = true
        }
    }

    fun completeOnboarding() {
        preferences.hasSeenOnboarding = true
        _showOnboardingDialog.value = false
    }

    fun replayOnboarding() {
        _showOnboardingDialog.value = true
    }

    fun playMedia(item: MediaItem, queue: List<MediaItem>? = null, startFromBeginning: Boolean = false) {
        val effectiveQueue = queue ?: filteredLibraryItems.value.ifEmpty { listOf(item) }
        playbackController.playMedia(
            item = item,
            queue = effectiveQueue,
            startFromBeginning = startFromBeginning,
            expandPlayer = true
        )
    }

    fun connectHostServer(
        url: String,
        pin: String,
        onComplete: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            _isSyncing.value = true
            _errorMessage.value = null
            val res = repository.connectHostServer(url, pin)
            _isSyncing.value = false
            res.onSuccess { srv ->
                _statusMessage.value = "Connected to ${srv.name} (${srv.itemCount} items synced)"
                onComplete(true, null)
                refreshStorageMetrics()
            }.onFailure { err ->
                val msg = err.message ?: "Unable to connect to LootStream PC Host"
                _errorMessage.value = msg
                onComplete(false, msg)
            }
        }
    }

    fun syncServer(serverId: String) {
        viewModelScope.launch {
            _isSyncing.value = true
            val res = repository.syncServer(serverId)
            _isSyncing.value = false
            res.onSuccess { count ->
                _statusMessage.value = "Synced $count media items"
                refreshStorageMetrics()
            }.onFailure { err ->
                _errorMessage.value = err.message ?: "Server sync failed"
            }
        }
    }

    fun syncAllServers(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _isSyncing.value = true
            val res = repository.syncAllServers()
            if (!silent) _isSyncing.value = false
            res.onSuccess { total ->
                if (!silent) {
                    _statusMessage.value = if (total > 0) "Synced $total items from servers" else "No active servers to sync"
                }
                refreshStorageMetrics()
            }.onFailure { err ->
                if (!silent) _errorMessage.value = err.message ?: "Sync failed"
            }
        }
    }

    fun removeServer(serverId: String) {
        viewModelScope.launch {
            repository.removeServer(serverId)
            _statusMessage.value = "Server removed"
            refreshStorageMetrics()
        }
    }

    fun importLocalMedia(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isSyncing.value = true
            val count = repository.importLocalMedia(uris)
            _isSyncing.value = false
            if (count > 0) {
                _statusMessage.value = "Imported $count local media ${if (count == 1) "file" else "files"}"
                refreshStorageMetrics()
            } else {
                _errorMessage.value = "Could not import selected media"
            }
        }
    }

    fun addDirectStreamUrl(
        title: String,
        streamUrl: String,
        mediaType: MediaType,
        posterUrl: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = repository.addDirectStreamUrl(
                title = title,
                streamUrl = streamUrl,
                mediaType = mediaType,
                posterUrl = posterUrl
            )
            res.onSuccess { item ->
                _statusMessage.value = "Added stream \"${item.title}\""
                refreshStorageMetrics()
                onSuccess()
            }.onFailure { err ->
                _errorMessage.value = err.message ?: "Invalid stream URL"
            }
        }
    }

    fun toggleFavorite(item: MediaItem) {
        viewModelScope.launch {
            val updated = repository.toggleFavorite(item)
            if (_selectedMediaDetail.value?.id == updated.id) {
                _selectedMediaDetail.value = updated
            }
        }
    }

    fun toggleWatchlist(item: MediaItem) {
        viewModelScope.launch {
            val updated = repository.toggleWatchlist(item)
            if (_selectedMediaDetail.value?.id == updated.id) {
                _selectedMediaDetail.value = updated
            }
        }
    }

    fun updateWatchStatus(item: MediaItem, status: WatchStatus) {
        viewModelScope.launch {
            val updated = repository.updateWatchStatus(item, status)
            if (_selectedMediaDetail.value?.id == updated.id) {
                _selectedMediaDetail.value = updated
            }
        }
    }

    fun deleteMediaItem(item: MediaItem) {
        viewModelScope.launch {
            if (playbackState.value.currentItem?.id == item.id) {
                playbackController.stopAndClose()
            }
            repository.deleteMediaItem(item.id)
            if (_selectedMediaDetail.value?.id == item.id) {
                _selectedMediaDetail.value = null
            }
            _statusMessage.value = "Removed \"${item.title}\""
            refreshStorageMetrics()
        }
    }

    fun clearLocalAndStreamMedia() {
        viewModelScope.launch {
            repository.clearLocalAndStreamMedia()
            _statusMessage.value = "Cleared local and URL stream entries"
            refreshStorageMetrics()
        }
    }

    fun exportBackupToUri(uri: Uri) {
        viewModelScope.launch {
            val res = MediaBackupManager.exportToUri(getApplication(), preferences, uri)
            res.onSuccess { count ->
                _lastBackupStatus.value = preferences.lastBackupStatus
                _lastBackupTimestamp.value = preferences.lastBackupTimestamp
                _statusMessage.value = "Exported backup ($count media items)"
            }.onFailure { err ->
                _errorMessage.value = err.message ?: "Backup export failed"
            }
        }
    }

    fun importBackupFromUri(uri: Uri) {
        viewModelScope.launch {
            val res = MediaBackupManager.importFromUri(getApplication(), preferences, uri)
            res.onSuccess { summary ->
                syncPreferencesFromDisk()
                _lastBackupStatus.value = preferences.lastBackupStatus
                _lastBackupTimestamp.value = preferences.lastBackupTimestamp
                _statusMessage.value = "Restored ${summary.restoredItems} items & ${summary.restoredServers} servers"
                refreshStorageMetrics()
            }.onFailure { err ->
                _errorMessage.value = err.message ?: "Backup import failed"
            }
        }
    }

    fun clearImageAndHttpCaches() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                LootDropApplication.clearCoilImageCache(getApplication())
                ApiClient.clearHttpCache(getApplication())
                AppDatabase.vacuumAndCompact(getApplication())
            }
            refreshStorageMetrics()
            _statusMessage.value = "Cleared media caches & compacted database"
        }
    }

    fun refreshStorageMetrics() {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            _storageMetrics.value = MediaStorageMetrics(
                diskCacheBytes = LootDropApplication.getDiskCacheSizeBytes(app),
                httpCacheBytes = LootDropApplication.getHttpCacheSizeBytes(app),
                databaseBytes = LootDropApplication.getDatabaseSizeBytes(app)
            )
        }
    }

    fun saveProfile(name: String, avatarUri: Uri?, removeAvatar: Boolean) {
        val updated = UserProfileManager.saveProfile(
            context = getApplication(),
            preferences = preferences,
            name = name,
            avatarUri = avatarUri,
            removeAvatar = removeAvatar
        )
        _userProfile.value = updated
        _statusMessage.value = "Profile updated"
    }

    fun importCustomFont(uri: Uri) {
        viewModelScope.launch {
            when (val res = withContext(Dispatchers.IO) {
                CustomFontHelper.importFontFromUri(getApplication(), uri)
            }) {
                is CustomFontHelper.ImportResult.Success -> {
                    preferences.customFontPath = res.filePath
                    preferences.customFontName = res.displayName
                    _customFontPath.value = res.filePath
                    _customFontName.value = res.displayName
                    _statusMessage.value = "Applied font: ${res.displayName}"
                }
                is CustomFontHelper.ImportResult.Error -> {
                    _errorMessage.value = res.message
                }
            }
        }
    }

    fun resetCustomFont() {
        CustomFontHelper.clearCustomFonts(getApplication())
        preferences.customFontPath = ""
        preferences.customFontName = ""
        _customFontPath.value = ""
        _customFontName.value = ""
        _statusMessage.value = "Restored default typography"
    }

    // Preference Setters
    fun setThemeMode(mode: ThemeMode) {
        preferences.themeMode = mode
        _themeMode.value = mode
    }

    fun setAccentTheme(theme: AccentTheme) {
        preferences.accentTheme = theme
        _accentTheme.value = theme
    }

    fun setDarkBackgroundTheme(bg: BackgroundTheme) {
        preferences.darkBackgroundTheme = bg
        _darkBackgroundTheme.value = bg
    }

    fun setLightBackgroundTheme(bg: BackgroundTheme) {
        preferences.lightBackgroundTheme = bg
        _lightBackgroundTheme.value = bg
    }

    fun setCardDensity(density: CardDensity) {
        preferences.cardDensity = density
        _cardDensity.value = density
    }

    fun setDefaultTab(tab: MainTab) {
        preferences.defaultTab = tab
        _defaultTab.value = tab
    }

    fun setDefaultSourceFilter(source: MediaSourceType) {
        preferences.defaultSourceFilter = source
        _defaultSourceFilter.value = source
    }

    fun setDefaultPlaybackSpeed(speed: Float) {
        preferences.defaultPlaybackSpeed = speed
        _defaultPlaybackSpeed.value = speed
        playbackController.setPlaybackSpeed(speed)
    }

    fun setAutoPlayNext(enabled: Boolean) {
        preferences.autoPlayNext = enabled
        _autoPlayNext.value = enabled
    }

    fun setAutoSyncServersOnOpen(enabled: Boolean) {
        preferences.autoSyncServersOnOpen = enabled
        _autoSyncServersOnOpen.value = enabled
    }

    fun setShowDurationBadge(enabled: Boolean) {
        preferences.showDurationBadge = enabled
        _showDurationBadge.value = enabled
    }

    fun setShowResumeBar(enabled: Boolean) {
        preferences.showResumeBar = enabled
        _showResumeBar.value = enabled
    }

    fun setPlayerResizeMode(mode: PlayerResizeMode) {
        preferences.playerResizeMode = mode
        _playerResizeMode.value = mode
    }

    fun setAnimationsEnabled(enabled: Boolean) {
        preferences.isAnimationsEnabled = enabled
        _isAnimationsEnabled.value = enabled
    }

    fun setLowEndOptimization(enabled: Boolean) {
        preferences.isLowEndOptimization = enabled
        _isLowEndOptimization.value = enabled
    }

    fun setTargetFrameRate(fps: TargetFrameRate) {
        preferences.targetFrameRate = fps
        _targetFrameRate.value = fps
    }

    fun setDpiOptimizationEnabled(enabled: Boolean) {
        preferences.isDpiOptimizationEnabled = enabled
        _isDpiOptimizationEnabled.value = enabled
    }

    fun setDpiScaleMode(mode: DpiScaleMode) {
        preferences.dpiScaleMode = mode
        _dpiScaleMode.value = mode
    }

    fun setDataSaverMode(enabled: Boolean) {
        preferences.isDataSaverMode = enabled
        _isDataSaverMode.value = enabled
    }

    fun setAppLanguage(language: AppLanguage) {
        preferences.appLanguage = language
        _appLanguage.value = language
        LocaleSyncHelper.applyAppLanguage(getApplication(), language)
    }

    private fun syncPreferencesFromDisk() {
        _themeMode.value = preferences.themeMode
        _accentTheme.value = preferences.accentTheme
        _darkBackgroundTheme.value = preferences.darkBackgroundTheme
        _lightBackgroundTheme.value = preferences.lightBackgroundTheme
        _cardDensity.value = preferences.cardDensity
        _defaultTab.value = preferences.defaultTab
        _defaultSourceFilter.value = preferences.defaultSourceFilter
        _defaultPlaybackSpeed.value = preferences.defaultPlaybackSpeed
        _autoPlayNext.value = preferences.autoPlayNext
        _autoSyncServersOnOpen.value = preferences.autoSyncServersOnOpen
        _showDurationBadge.value = preferences.showDurationBadge
        _showResumeBar.value = preferences.showResumeBar
        _playerResizeMode.value = preferences.playerResizeMode
        _collectionsFilter.value = preferences.collectionsFilter
        _isAnimationsEnabled.value = preferences.isAnimationsEnabled
        _isLowEndOptimization.value = preferences.isLowEndOptimization
        _targetFrameRate.value = preferences.targetFrameRate
        _isDpiOptimizationEnabled.value = preferences.isDpiOptimizationEnabled
        _dpiScaleMode.value = preferences.dpiScaleMode
        _isDataSaverMode.value = preferences.isDataSaverMode
        _appLanguage.value = preferences.appLanguage
        _userProfile.value = UserProfileManager.getProfile(preferences)
    }

    override fun onCleared() {
        super.onCleared()
        playbackController.release()
    }
}
