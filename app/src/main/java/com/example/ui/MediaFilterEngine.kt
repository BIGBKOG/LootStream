package com.example.ui

import com.example.data.model.CollectionsFilter
import com.example.data.model.MediaItem
import com.example.data.model.MediaSortOption
import com.example.data.model.MediaSourceType
import com.example.data.model.MediaType
import com.example.data.model.WatchStatus

object MediaFilterEngine {

    fun filterAndSort(
        items: List<MediaItem>,
        searchQuery: String,
        sourceFilter: MediaSourceType,
        mediaTypeFilter: MediaType,
        watchStatusFilter: WatchStatus?,
        sortOption: MediaSortOption
    ): List<MediaItem> {
        val query = searchQuery.trim().lowercase()
        val filtered = items.filter { item ->
            val matchesSource = sourceFilter == MediaSourceType.ALL || item.sourceType == sourceFilter
            val matchesType = when (mediaTypeFilter) {
                MediaType.ALL -> true
                MediaType.SERIES -> item.mediaType == MediaType.SERIES || item.mediaType == MediaType.EPISODE
                else -> item.mediaType == mediaTypeFilter
            }
            val matchesStatus = watchStatusFilter == null || item.watchStatus == watchStatusFilter
            val matchesQuery = query.isEmpty() ||
                item.title.lowercase().contains(query) ||
                item.subtitle.lowercase().contains(query) ||
                item.overview.lowercase().contains(query) ||
                (item.seriesName?.lowercase()?.contains(query) == true) ||
                item.genres.any { it.lowercase().contains(query) }

            matchesSource && matchesType && matchesStatus && matchesQuery
        }

        return when (sortOption) {
            MediaSortOption.RECENTLY_PLAYED -> filtered.sortedWith(
                compareByDescending<MediaItem> { it.lastPlayedAt }
                    .thenByDescending { it.addedAt }
            )
            MediaSortOption.NEWEST_ADDED -> filtered.sortedByDescending { it.addedAt }
            MediaSortOption.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
            MediaSortOption.DURATION_DESC -> filtered.sortedByDescending { it.durationMs }
            MediaSortOption.RATING_DESC -> filtered.sortedByDescending { it.communityRating ?: 0.0 }
        }
    }

    fun filterCollections(
        items: List<MediaItem>,
        filter: CollectionsFilter,
        searchQuery: String
    ): List<MediaItem> {
        val query = searchQuery.trim().lowercase()
        return items.filter { item ->
            val inVault = item.isFavorite || item.watchStatus == WatchStatus.WATCHED || item.isResumeAvailable || item.isWatchlist
            val matchesFilter = when (filter) {
                CollectionsFilter.ALL -> inVault || item.sourceType != MediaSourceType.ALL
                CollectionsFilter.FAVORITES -> item.isFavorite
                CollectionsFilter.IN_PROGRESS -> item.isResumeAvailable || item.watchStatus == WatchStatus.IN_PROGRESS
                CollectionsFilter.WATCHED -> item.watchStatus == WatchStatus.WATCHED
                CollectionsFilter.HOST_ONLY -> item.sourceType == MediaSourceType.PC_HOST
                CollectionsFilter.LOCAL_ONLY -> item.sourceType == MediaSourceType.LOCAL || item.sourceType == MediaSourceType.STREAM_URL
            }
            val matchesQuery = query.isEmpty() ||
                item.title.lowercase().contains(query) ||
                item.subtitle.lowercase().contains(query) ||
                item.genres.any { it.lowercase().contains(query) }

            matchesFilter && matchesQuery
        }.sortedWith(
            compareByDescending<MediaItem> { it.isFavorite }
                .thenByDescending { it.lastPlayedAt }
                .thenByDescending { it.addedAt }
        )
    }
}
