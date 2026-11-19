package com.example.ui

import androidx.compose.runtime.Composable
import com.example.data.model.HostServerConfig
import com.example.data.model.MediaItem
import com.example.data.model.MediaLibraryStats
import com.example.data.model.MediaSortOption
import com.example.data.model.MediaSourceType
import com.example.data.model.MediaType
import com.example.data.model.UserProfile
import com.example.data.model.WatchStatus
import com.example.i18n.AppLanguage
import com.example.ui.components.AddMediaActionSheet
import com.example.ui.components.AddStreamUrlDialog
import com.example.ui.components.HostServerDialog
import com.example.ui.components.InitialLanguageDialog
import com.example.ui.components.MediaDetailDialog
import com.example.ui.components.MediaFilterSortSheet
import com.example.ui.components.OnboardingWalkthroughDialog
import com.example.ui.components.ProfileDialog

@Composable
internal fun HomeDialogsOverlay(
    viewModel: LootStreamViewModel,
    appLanguage: AppLanguage,
    showInitialLanguageDialog: Boolean,
    showOnboardingDialog: Boolean,
    showProfileDialog: Boolean,
    showAddMediaSheet: Boolean,
    showHostDialog: Boolean,
    showStreamUrlDialog: Boolean,
    showFilterSheet: Boolean,
    selectedMediaDetail: MediaItem?,
    userProfile: UserProfile,
    stats: MediaLibraryStats,
    servers: List<HostServerConfig>,
    isSyncing: Boolean,
    sourceFilter: MediaSourceType,
    mediaTypeFilter: MediaType,
    watchStatusFilter: WatchStatus?,
    sortOption: MediaSortOption,
    onPickGalleryVideos: () -> Unit,
    onImportDocumentFiles: () -> Unit
) {
    if (showFilterSheet) {
        MediaFilterSortSheet(
            sourceFilter = sourceFilter,
            mediaTypeFilter = mediaTypeFilter,
            watchStatusFilter = watchStatusFilter,
            sortOption = sortOption,
            onSelectSource = { viewModel.setSourceFilter(it) },
            onSelectMediaType = { viewModel.setMediaTypeFilter(it) },
            onSelectWatchStatus = { viewModel.setWatchStatusFilter(it) },
            onSelectSort = { viewModel.setSortOption(it) },
            onReset = { viewModel.resetLibraryFilters() },
            onDismiss = { viewModel.setShowFilterSheet(false) }
        )
    }

    if (showAddMediaSheet) {
        AddMediaActionSheet(
            onDismiss = { viewModel.setShowAddMediaSheet(false) },
            onPickGalleryVideos = onPickGalleryVideos,
            onImportDocumentFiles = onImportDocumentFiles,
            onAddStreamUrl = { viewModel.setShowStreamUrlDialog(true) },
            onConnectHost = { viewModel.setShowHostDialog(true) }
        )
    }

    if (showHostDialog) {
        HostServerDialog(
            servers = servers,
            isSyncing = isSyncing,
            onDismiss = { viewModel.setShowHostDialog(false) },
            onConnect = { url, pin, onDone ->
                viewModel.connectHostServer(url, pin, onDone)
            },
            onSyncServer = { viewModel.syncServer(it) },
            onRemoveServer = { viewModel.removeServer(it) }
        )
    }

    if (showStreamUrlDialog) {
        AddStreamUrlDialog(
            onDismiss = { viewModel.setShowStreamUrlDialog(false) },
            onAddStream = { title, streamUrl, mediaType, posterUrl ->
                viewModel.addDirectStreamUrl(
                    title = title,
                    streamUrl = streamUrl,
                    mediaType = mediaType,
                    posterUrl = posterUrl,
                    onSuccess = { viewModel.setShowStreamUrlDialog(false) }
                )
            }
        )
    }

    selectedMediaDetail?.let { item ->
        MediaDetailDialog(
            item = item,
            onDismiss = { viewModel.dismissMediaDetail() },
            onPlayClick = { fromBeginning ->
                viewModel.dismissMediaDetail()
                viewModel.playMedia(item = item, startFromBeginning = fromBeginning)
            },
            onToggleFavorite = { viewModel.toggleFavorite(item) },
            onToggleWatchlist = { viewModel.toggleWatchlist(item) },
            onUpdateWatchStatus = { status -> viewModel.updateWatchStatus(item, status) },
            onDeleteItem = { viewModel.deleteMediaItem(item) }
        )
    }

    if (showInitialLanguageDialog) {
        InitialLanguageDialog(
            initialLanguage = appLanguage,
            onSelectLanguagePreview = { viewModel.setAppLanguage(it) },
            onConfirmLanguage = { viewModel.completeInitialLanguageSelection(it) }
        )
    } else if (showOnboardingDialog) {
        OnboardingWalkthroughDialog(
            initialProfile = userProfile,
            currentLanguage = appLanguage,
            onLanguageChange = { viewModel.setAppLanguage(it) },
            onSaveProfile = { name, uri, remove -> viewModel.saveProfile(name, uri, remove) },
            onDismiss = { viewModel.completeOnboarding() },
            onFinish = { viewModel.completeOnboarding() }
        )
    }

    if (showProfileDialog) {
        ProfileDialog(
            profile = userProfile,
            stats = stats,
            onOpenHostDialog = { viewModel.setShowHostDialog(true) },
            onSaveProfile = { name, uri, remove -> viewModel.saveProfile(name, uri, remove) },
            onReplayTour = { viewModel.replayOnboarding() },
            onDismiss = { viewModel.setShowProfileDialog(false) }
        )
    }
}
