package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MediaSortOption
import com.example.data.model.MediaSourceType
import com.example.data.model.MediaType
import com.example.data.model.WatchStatus
import com.example.i18n.LocalAppLanguage
import com.example.i18n.LocalAppStrings
import com.example.i18n.localizedLabel
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMediaActionSheet(
    onDismiss: () -> Unit,
    onPickGalleryVideos: () -> Unit,
    onImportDocumentFiles: () -> Unit,
    onAddStreamUrl: () -> Unit,
    onConnectHost: () -> Unit
) {
    val s = LocalAppStrings.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 10.dp)
                .testTag("add_media_action_sheet")
        ) {
            Text(
                text = s.addMediaSheetTitle,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onSurface
            )

            AddMediaSheetRow(
                icon = Icons.Default.Dns,
                tint = NeonPurple,
                title = s.addMediaHostTitle,
                subtitle = s.addMediaHostDesc,
                testTag = "sheet_connect_host",
                onClick = { onDismiss(); onConnectHost() }
            )

            AddMediaSheetRow(
                icon = Icons.Default.VideoLibrary,
                tint = NeonGreen,
                title = s.addMediaVideoPickerTitle,
                subtitle = s.addMediaVideoPickerDesc,
                testTag = "sheet_pick_gallery_videos",
                onClick = { onDismiss(); onPickGalleryVideos() }
            )

            AddMediaSheetRow(
                icon = Icons.Default.FolderOpen,
                tint = MaterialTheme.colorScheme.primary,
                title = s.addMediaDocumentTitle,
                subtitle = s.addMediaDocumentDesc,
                testTag = "sheet_import_documents",
                onClick = { onDismiss(); onImportDocumentFiles() }
            )

            AddMediaSheetRow(
                icon = Icons.Default.AddLink,
                tint = NeonCyan,
                title = s.addMediaStreamTitle,
                subtitle = s.addMediaStreamDesc,
                testTag = "sheet_add_stream_url",
                onClick = { onDismiss(); onAddStreamUrl() }
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AddMediaSheetRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MediaFilterSortSheet(
    sourceFilter: MediaSourceType,
    mediaTypeFilter: MediaType,
    watchStatusFilter: WatchStatus?,
    sortOption: MediaSortOption,
    onSelectSource: (MediaSourceType) -> Unit,
    onSelectMediaType: (MediaType) -> Unit,
    onSelectWatchStatus: (WatchStatus?) -> Unit,
    onSelectSort: (MediaSortOption) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val s = LocalAppStrings.current
    val language = LocalAppLanguage.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 10.dp)
                .testTag("media_filter_sort_sheet")
        ) {
            Text(
                text = s.filterTitle,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(14.dp))
            Text(s.filterSource, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MediaSourceType.entries.forEach { src ->
                    FilterChip(
                        selected = sourceFilter == src,
                        onClick = { onSelectSource(src) },
                        label = { Text(src.localizedLabel(language), fontSize = 12.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(s.filterMediaType, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(MediaType.ALL, MediaType.MOVIE, MediaType.SERIES, MediaType.VIDEO, MediaType.AUDIO, MediaType.LIVE_STREAM).forEach { type ->
                    FilterChip(
                        selected = mediaTypeFilter == type,
                        onClick = { onSelectMediaType(type) },
                        label = { Text(type.localizedLabel(language), fontSize = 12.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(s.filterWatchStatus, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = watchStatusFilter == null,
                    onClick = { onSelectWatchStatus(null) },
                    label = { Text(MediaType.ALL.localizedLabel(language), fontSize = 12.sp) }
                )
                WatchStatus.entries.forEach { st ->
                    FilterChip(
                        selected = watchStatusFilter == st,
                        onClick = { onSelectWatchStatus(st) },
                        label = { Text(st.localizedLabel(language), fontSize = 12.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(s.filterSortBy, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MediaSortOption.entries.forEach { opt ->
                    FilterChip(
                        selected = sortOption == opt,
                        onClick = { onSelectSort(opt) },
                        label = { Text(opt.localizedLabel(language), fontSize = 12.sp) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text(s.filterReset, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text(s.filterApply, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
