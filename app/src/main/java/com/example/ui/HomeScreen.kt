package com.example.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.example.data.model.MainTab
import com.example.data.model.MediaSortOption
import com.example.data.model.MediaSourceType
import com.example.data.model.MediaType
import com.example.i18n.LocalAppStrings
import com.example.player.LootStreamPlayerOverlay
import com.example.player.MiniPlayerBar
import com.example.ui.components.ExpressiveFloatingNavBar
import com.example.ui.density.LocalScreenDensityInfo
import com.example.ui.home.HomeTabContent
import com.example.ui.theme.ExpressiveMotion
import com.example.ui.theme.rememberCollapsibleHeaderNestedScrollConnection
import com.example.ui.theme.rememberCollapsibleHeaderState
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.absoluteValue

@Composable
fun HomeScreen(viewModel: LootStreamViewModel) {
    val context = LocalContext.current
    val screenDensityInfo = LocalScreenDensityInfo.current
    val s = LocalAppStrings.current

    val selectedTab by viewModel.selectedTab.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearchVisible by viewModel.isSearchVisible.collectAsState()
    val sourceFilter by viewModel.sourceFilter.collectAsState()
    val mediaTypeFilter by viewModel.mediaTypeFilter.collectAsState()
    val watchStatusFilter by viewModel.watchStatusFilter.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val collectionsFilter by viewModel.collectionsFilter.collectAsState()

    val filteredLibraryItems by viewModel.filteredLibraryItems.collectAsState()
    val continueWatchingItems by viewModel.continueWatchingItems.collectAsState()
    val recentlyAddedItems by viewModel.recentlyAddedItems.collectAsState()
    val upNextQueueItems by viewModel.upNextQueueItems.collectAsState()
    val recentlyWatchedItems by viewModel.recentlyWatchedItems.collectAsState()
    val collectionsItems by viewModel.collectionsItems.collectAsState()

    val servers by viewModel.servers.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()

    val isSyncing by viewModel.isSyncing.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val selectedMediaDetail by viewModel.selectedMediaDetail.collectAsState()
    val showAddMediaSheet by viewModel.showAddMediaSheet.collectAsState()
    val showHostDialog by viewModel.showHostDialog.collectAsState()
    val showStreamUrlDialog by viewModel.showStreamUrlDialog.collectAsState()
    val showFilterSheet by viewModel.showFilterSheet.collectAsState()
    val showProfileDialog by viewModel.showProfileDialog.collectAsState()
    val showInitialLanguageDialog by viewModel.showInitialLanguageDialog.collectAsState()
    val showOnboardingDialog by viewModel.showOnboardingDialog.collectAsState()

    val cardDensity by viewModel.cardDensity.collectAsState()
    val showDurationBadge by viewModel.showDurationBadge.collectAsState()
    val showResumeBar by viewModel.showResumeBar.collectAsState()
    val isAnimationsEnabled by viewModel.isAnimationsEnabled.collectAsState()
    val isLowEndOptimization by viewModel.isLowEndOptimization.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importLocalMedia(uris)
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
            viewModel.importLocalMedia(uris)
        }
    }

    val launchGalleryPicker: () -> Unit = {
        videoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
        )
    }

    val launchDocumentPicker: () -> Unit = {
        documentPickerLauncher.launch(arrayOf("video/*", "audio/*"))
    }

    val activeFilterCount = remember(sourceFilter, mediaTypeFilter, watchStatusFilter, sortOption) {
        var count = 0
        if (sourceFilter != MediaSourceType.ALL) count++
        if (mediaTypeFilter != MediaType.ALL) count++
        if (watchStatusFilter != null) count++
        if (sortOption != MediaSortOption.RECENTLY_PLAYED) count++
        count
    }

    val reducedMotion = !isAnimationsEnabled || isLowEndOptimization
    val borderBeamAngle = rememberAnimatedBeamAngle(enabled = !reducedMotion)

    val tabs = remember { MainTab.entries }
    val pagerState = rememberPagerState(
        initialPage = tabs.indexOf(selectedTab).coerceAtLeast(0),
        pageCount = { tabs.size }
    )

    LaunchedEffect(pagerState.currentPage) {
        val tab = tabs[pagerState.currentPage]
        if (tab != selectedTab) viewModel.selectTab(tab)
    }
    LaunchedEffect(selectedTab) {
        val targetIndex = tabs.indexOf(selectedTab)
        if (targetIndex >= 0 && targetIndex != pagerState.currentPage) {
            if (reducedMotion || abs(targetIndex - pagerState.currentPage) > 1) {
                pagerState.scrollToPage(targetIndex)
            } else {
                pagerState.animateScrollToPage(targetIndex)
            }
        }
    }

    LaunchedEffect(statusMessage) {
        if (statusMessage != null) {
            delay(3500L)
            viewModel.dismissStatusMessage()
        }
    }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            delay(4500L)
            viewModel.dismissErrorMessage()
        }
    }

    val homeListState = rememberLazyListState()
    val libraryListState = rememberLazyListState()
    val upNextListState = rememberLazyListState()
    val collectionsListState = rememberLazyListState()
    val libraryGridState = rememberLazyGridState()
    val collectionsGridState = rememberLazyGridState()
    val settingsScrollState = rememberScrollState()

    val collapsibleHeaderState = rememberCollapsibleHeaderState(reducedMotion = reducedMotion)
    val currentTab = tabs[pagerState.currentPage]
    val canCollapseCurrentTab = when (currentTab) {
        MainTab.HOME -> homeListState.canScrollForward || homeListState.canScrollBackward
        MainTab.LIBRARY -> if (screenDensityInfo.gridColumns > 1) {
            libraryGridState.canScrollForward || libraryGridState.canScrollBackward
        } else {
            libraryListState.canScrollForward || libraryListState.canScrollBackward
        }
        MainTab.UP_NEXT -> upNextListState.canScrollForward || upNextListState.canScrollBackward
        MainTab.COLLECTIONS -> if (screenDensityInfo.gridColumns > 1) {
            collectionsGridState.canScrollForward || collectionsGridState.canScrollBackward
        } else {
            collectionsListState.canScrollForward || collectionsListState.canScrollBackward
        }
        MainTab.SETTINGS -> settingsScrollState.maxValue > 0
    }

    LaunchedEffect(currentTab, isSearchVisible) { collapsibleHeaderState.expandAnimated() }
    LaunchedEffect(canCollapseCurrentTab) {
        if (!canCollapseCurrentTab) collapsibleHeaderState.expandAnimated()
    }

    val collapsibleTopBarConnection = rememberCollapsibleHeaderNestedScrollConnection(
        state = collapsibleHeaderState,
        canCollapseProvider = { !isSearchVisible && canCollapseCurrentTab }
    )

    BackHandler(enabled = !playbackState.isPlayerExpanded && (isSearchVisible || selectedTab != MainTab.HOME)) {
        when {
            isSearchVisible -> viewModel.setSearchVisible(false)
            selectedTab != MainTab.HOME -> viewModel.selectTab(MainTab.HOME)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.96f)
                                )
                            )
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AnimatedVisibility(
                        visible = playbackState.hasActiveMedia && !playbackState.isPlayerExpanded,
                        enter = ExpressiveMotion.expressiveExpandEnter(reducedMotion),
                        exit = ExpressiveMotion.expressiveCollapseExit(reducedMotion)
                    ) {
                        MiniPlayerBar(
                            state = playbackState,
                            onExpand = { viewModel.playbackController.expandPlayer() },
                            onTogglePlayPause = { viewModel.playbackController.togglePlayPause() },
                            onSkipNext = { viewModel.playbackController.skipNext() },
                            onClose = { viewModel.playbackController.stopAndClose() }
                        )
                    }

                    ExpressiveFloatingNavBar(
                        selectedTab = currentTab,
                        onTabSelected = { viewModel.selectTab(it) },
                        vaultCount = stats.favoritesCount,
                        upNextCount = stats.continueWatchingCount,
                        isLowEndOptimization = isLowEndOptimization
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
                    .nestedScroll(collapsibleTopBarConnection)
            ) {
                HomeTopBarChrome(
                    currentTab = currentTab,
                    userProfile = userProfile,
                    reducedMotion = reducedMotion,
                    collapsibleHeaderState = collapsibleHeaderState,
                    isSearchExpanded = isSearchVisible,
                    searchQuery = searchQuery,
                    activeFilterCount = activeFilterCount,
                    connectedServersCount = stats.connectedServersCount,
                    isSyncing = isSyncing,
                    isLowEndOptimization = isLowEndOptimization,
                    statusMessage = statusMessage,
                    errorMessage = errorMessage,
                    onOpenProfile = { viewModel.setShowProfileDialog(true) },
                    onToggleSearch = {
                        if (currentTab == MainTab.SETTINGS || currentTab == MainTab.HOME) {
                            viewModel.selectTab(MainTab.LIBRARY)
                            viewModel.setSearchVisible(true)
                        } else {
                            viewModel.setSearchVisible(!isSearchVisible)
                        }
                    },
                    onFilterClick = {
                        if (currentTab == MainTab.SETTINGS || currentTab == MainTab.HOME) {
                            viewModel.selectTab(MainTab.LIBRARY)
                        }
                        viewModel.setShowFilterSheet(true)
                    },
                    onAddMediaClick = { viewModel.setShowAddMediaSheet(true) },
                    onDismissStatusBanner = { viewModel.dismissStatusMessage() },
                    onDismissErrorBanner = { viewModel.dismissErrorMessage() }
                )

                AnimatedVisibility(
                    visible = isSearchVisible && currentTab != MainTab.SETTINGS,
                    enter = ExpressiveMotion.expressiveExpandEnter(reducedMotion),
                    exit = ExpressiveMotion.expressiveCollapseExit(reducedMotion)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            placeholder = {
                                Text(
                                    s.searchPlaceholder,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_text_input"),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { viewModel.updateSearchQuery("") },
                                        modifier = Modifier.testTag("clear_search_button")
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Clear search",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = if (isLowEndOptimization) 0 else 1,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .testTag("main_horizontal_pager")
                ) { page ->
                    val pageModifier = if (!reducedMotion) {
                        val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                            .absoluteValue
                            .coerceIn(0f, 1f)
                        if (pageOffset > 0.001f) {
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val pageScale = lerp(1f, 0.95f, pageOffset)
                                    scaleX = pageScale
                                    scaleY = pageScale
                                    alpha = lerp(1f, 0.72f, pageOffset)
                                    translationY = lerp(0f, 14f, pageOffset)
                                }
                        } else {
                            Modifier.fillMaxSize()
                        }
                    } else {
                        Modifier.fillMaxSize()
                    }

                    Box(modifier = pageModifier) {
                        when (tabs[page]) {
                            MainTab.HOME -> HomeTabContent(
                                continueWatchingItems = continueWatchingItems,
                                recentlyAddedItems = recentlyAddedItems,
                                servers = servers,
                                stats = stats,
                                isSyncing = isSyncing,
                                isAnimationsEnabled = isAnimationsEnabled,
                                isLowEndOptimization = isLowEndOptimization,
                                listState = homeListState,
                                onRefresh = { viewModel.syncAllServers() },
                                onItemClick = { viewModel.openMediaDetail(it) },
                                onPlayClick = { item, queue ->
                                    viewModel.playMedia(item = item, queue = queue)
                                },
                                onOpenLibrary = { viewModel.selectTab(MainTab.LIBRARY) },
                                onOpenVault = { viewModel.selectTab(MainTab.COLLECTIONS) },
                                onOpenHostDialog = { viewModel.setShowHostDialog(true) },
                                onImportLocalMedia = { viewModel.setShowAddMediaSheet(true) },
                                onOpenStreamUrlDialog = { viewModel.setShowStreamUrlDialog(true) },
                                onSyncAllServers = { viewModel.syncAllServers() }
                            )

                            MainTab.LIBRARY -> MediaLibraryTabContent(
                                items = filteredLibraryItems,
                                sourceFilter = sourceFilter,
                                mediaTypeFilter = mediaTypeFilter,
                                watchStatusFilter = watchStatusFilter,
                                sortOption = sortOption,
                                cardDensity = cardDensity,
                                showDurationBadge = showDurationBadge,
                                showResumeBar = showResumeBar,
                                isSyncing = isSyncing,
                                isAnimationsEnabled = isAnimationsEnabled,
                                isLowEndOptimization = isLowEndOptimization,
                                beamAngle = borderBeamAngle,
                                screenDensityInfo = screenDensityInfo,
                                listState = libraryListState,
                                gridState = libraryGridState,
                                onRefresh = { viewModel.syncAllServers() },
                                onSelectSource = { viewModel.setSourceFilter(it) },
                                onSelectMediaType = { viewModel.setMediaTypeFilter(it) },
                                onOpenFilterSheet = { viewModel.setShowFilterSheet(true) },
                                onResetFilters = { viewModel.resetLibraryFilters() },
                                onOpenAddMedia = { viewModel.setShowAddMediaSheet(true) },
                                onOpenHostDialog = { viewModel.setShowHostDialog(true) },
                                onImportLocalMedia = launchDocumentPicker,
                                onItemClick = { viewModel.openMediaDetail(it) },
                                onPlayClick = { item ->
                                    viewModel.playMedia(item = item, queue = filteredLibraryItems)
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(it) },
                                onToggleWatchlist = { viewModel.toggleWatchlist(it) }
                            )

                            MainTab.UP_NEXT -> UpNextTimelineTabContent(
                                continueWatchingItems = continueWatchingItems,
                                queueItems = upNextQueueItems,
                                recentlyWatchedItems = recentlyWatchedItems,
                                cardDensity = cardDensity,
                                showDurationBadge = showDurationBadge,
                                showResumeBar = showResumeBar,
                                isSyncing = isSyncing,
                                isAnimationsEnabled = isAnimationsEnabled,
                                isLowEndOptimization = isLowEndOptimization,
                                beamAngle = borderBeamAngle,
                                listState = upNextListState,
                                onRefresh = { viewModel.syncAllServers() },
                                onItemClick = { viewModel.openMediaDetail(it) },
                                onPlayClick = { item, queue ->
                                    viewModel.playMedia(item = item, queue = queue)
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(it) },
                                onToggleWatchlist = { viewModel.toggleWatchlist(it) }
                            )

                            MainTab.COLLECTIONS -> CollectionsTabContent(
                                items = collectionsItems,
                                stats = stats,
                                collectionsFilter = collectionsFilter,
                                cardDensity = cardDensity,
                                showDurationBadge = showDurationBadge,
                                showResumeBar = showResumeBar,
                                isAnimationsEnabled = isAnimationsEnabled,
                                isLowEndOptimization = isLowEndOptimization,
                                beamAngle = borderBeamAngle,
                                screenDensityInfo = screenDensityInfo,
                                listState = collectionsListState,
                                gridState = collectionsGridState,
                                onSelectFilter = { viewModel.setCollectionsFilter(it) },
                                onItemClick = { viewModel.openMediaDetail(it) },
                                onPlayClick = { item ->
                                    viewModel.playMedia(item = item, queue = collectionsItems)
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(it) },
                                onToggleWatchlist = { viewModel.toggleWatchlist(it) }
                            )

                            MainTab.SETTINGS -> SettingsScreen(
                                viewModel = viewModel,
                                scrollState = settingsScrollState,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        HomeDialogsOverlay(
            viewModel = viewModel,
            appLanguage = appLanguage,
            showInitialLanguageDialog = showInitialLanguageDialog,
            showOnboardingDialog = showOnboardingDialog,
            showProfileDialog = showProfileDialog,
            showAddMediaSheet = showAddMediaSheet,
            showHostDialog = showHostDialog,
            showStreamUrlDialog = showStreamUrlDialog,
            showFilterSheet = showFilterSheet,
            selectedMediaDetail = selectedMediaDetail,
            userProfile = userProfile,
            stats = stats,
            servers = servers,
            isSyncing = isSyncing,
            sourceFilter = sourceFilter,
            mediaTypeFilter = mediaTypeFilter,
            watchStatusFilter = watchStatusFilter,
            sortOption = sortOption,
            onPickGalleryVideos = launchGalleryPicker,
            onImportDocumentFiles = launchDocumentPicker
        )

        AnimatedVisibility(
            visible = playbackState.isPlayerExpanded && playbackState.hasActiveMedia,
            enter = fadeIn() + slideInVertically { it / 6 },
            exit = fadeOut() + slideOutVertically { it / 6 }
        ) {
            LootStreamPlayerOverlay(
                state = playbackState,
                player = viewModel.playbackController.player,
                onMinimize = { viewModel.playbackController.minimizePlayer() },
                onClose = { viewModel.playbackController.stopAndClose() },
                onTogglePlayPause = { viewModel.playbackController.togglePlayPause() },
                onSeekTo = { viewModel.playbackController.seekTo(it) },
                onSeekBy = { viewModel.playbackController.seekBy(it) },
                onSkipNext = { viewModel.playbackController.skipNext() },
                onSkipPrevious = { viewModel.playbackController.skipPrevious() },
                onCycleSpeed = { viewModel.playbackController.cyclePlaybackSpeed() },
                onCycleResizeMode = { viewModel.playbackController.cycleResizeMode() },
                onRetry = { viewModel.playbackController.retryCurrent() }
            )
        }
    }
}
