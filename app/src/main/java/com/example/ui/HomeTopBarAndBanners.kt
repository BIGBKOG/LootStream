package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.MainTab
import com.example.data.model.MediaLibraryStats
import com.example.data.model.MediaSortOption
import com.example.data.model.MediaSourceType
import com.example.data.model.MediaType
import com.example.data.model.UserProfile
import com.example.data.model.WatchStatus
import com.example.i18n.LocalAppLanguage
import com.example.i18n.LocalAppStrings
import com.example.i18n.localizedLabel
import com.example.ui.components.ExpressiveTopActionBar
import com.example.ui.components.ProfileAvatarCircle
import com.example.ui.theme.CollapsibleHeaderState
import com.example.ui.theme.ExpressiveMotion
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.collapsibleHeaderLayout
import com.example.util.LocalLowEndOptimization
import java.util.Locale

@Composable
internal fun HomeTopBarChrome(
    currentTab: MainTab,
    userProfile: UserProfile,
    reducedMotion: Boolean,
    collapsibleHeaderState: CollapsibleHeaderState,
    isSearchExpanded: Boolean,
    searchQuery: String,
    activeFilterCount: Int,
    connectedServersCount: Int,
    isSyncing: Boolean,
    isLowEndOptimization: Boolean,
    statusMessage: String?,
    errorMessage: String?,
    onOpenProfile: () -> Unit,
    onToggleSearch: () -> Unit,
    onFilterClick: () -> Unit,
    onAddMediaClick: () -> Unit,
    onDismissStatusBanner: () -> Unit,
    onDismissErrorBanner: () -> Unit
) {
    val s = LocalAppStrings.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .collapsibleHeaderLayout(collapsibleHeaderState, applyParallaxFade = !reducedMotion)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                if (currentTab == MainTab.HOME) {
                    ProfileAvatarCircle(profile = userProfile, size = 36.dp, onClick = onOpenProfile)
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.ic_lootstream_logo),
                        contentDescription = "LootStream Logo",
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
                Column {
                    val tabBadgeLabel = when (currentTab) {
                        MainTab.HOME -> s.navHomeShort
                        MainTab.LIBRARY -> s.navLibraryShort
                        MainTab.UP_NEXT -> s.navUpNextShort
                        MainTab.COLLECTIONS -> s.navVaultShort
                        MainTab.SETTINGS -> s.navSettingsShort
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Loot",
                            style = MaterialTheme.typography.titleLarge,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            "Stream",
                            style = MaterialTheme.typography.titleLarge,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                                .border(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), CircleShape)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            if (reducedMotion) {
                                Text(
                                    tabBadgeLabel,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 0.5.sp
                                )
                            } else {
                                AnimatedContent(
                                    targetState = tabBadgeLabel,
                                    transitionSpec = { ExpressiveMotion.popNumberTransform(false) },
                                    label = "top_tab_badge"
                                ) { tabBadgeText ->
                                    Text(
                                        tabBadgeText,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }
                    }
                    val subtitleLabel = when (currentTab) {
                        MainTab.HOME -> if (userProfile.name.isNotBlank()) {
                            "${userProfile.name.uppercase()} • ${s.subtitleHome.uppercase()}"
                        } else {
                            s.subtitleHome.uppercase()
                        }
                        MainTab.LIBRARY -> s.subtitleLibrary.uppercase()
                        MainTab.UP_NEXT -> s.subtitleUpNext.uppercase()
                        MainTab.COLLECTIONS -> s.subtitleVault.uppercase()
                        MainTab.SETTINGS -> s.subtitleSettings.uppercase()
                    }
                    Text(
                        text = subtitleLabel,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            ExpressiveTopActionBar(
                isSearchExpanded = isSearchExpanded && currentTab != MainTab.SETTINGS,
                searchQuery = searchQuery,
                onToggleSearch = onToggleSearch,
                activeFilterCount = activeFilterCount,
                onFilterClick = onFilterClick,
                connectedServersCount = connectedServersCount,
                isSyncing = isSyncing,
                onAddMediaClick = onAddMediaClick,
                isLowEndOptimization = isLowEndOptimization
            )
        }

        AnimatedVisibility(
            visible = (errorMessage != null || statusMessage != null) && currentTab != MainTab.SETTINGS,
            enter = ExpressiveMotion.expressiveExpandEnter(reducedMotion),
            exit = ExpressiveMotion.expressiveCollapseExit(reducedMotion)
        ) {
            val isError = errorMessage != null
            val msg = errorMessage ?: statusMessage ?: ""
            val borderTint = if (isError) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, borderTint, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("home_status_banner"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isError) Icons.Default.ErrorOutline else Icons.Default.Sync,
                        contentDescription = null,
                        tint = if (isError) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        msg,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = if (isError) onDismissErrorBanner else onDismissStatusBanner,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun VaultSummaryBanner(
    stats: MediaLibraryStats,
    isAnimationsEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val s = LocalAppStrings.current
    val effectiveAnimations = isAnimationsEnabled && !LocalLowEndOptimization.current
    val (borderAlpha, iconBob, iconRot) = rememberVaultBannerAnimations(enabled = effectiveAnimations)
    val animatedHours = if (effectiveAnimations) {
        animateFloatAsState(
            targetValue = stats.watchedDurationHours.toFloat(),
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
            label = "animatedWatchedHours"
        ).value
    } else {
        stats.watchedDurationHours.toFloat()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        NeonGreen.copy(alpha = 0.14f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f)
                    )
                )
            )
            .border(1.dp, NeonGreen.copy(alpha = borderAlpha * 0.6f), RoundedCornerShape(20.dp))
            .padding(16.dp)
            .testTag("vault_summary_banner")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(NeonGreen.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Bookmarks, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(15.dp))
                    }
                    Text(
                        s.vaultBannerTitle.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonGreen,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = String.format(Locale.US, "%.1fh", animatedHours),
                        style = MaterialTheme.typography.displayMedium,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        s.statWatched.uppercase(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonGreen,
                        modifier = Modifier.padding(bottom = 5.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${stats.favoritesCount} ★ • ${stats.watchedCount} ${s.statWatched} • ${stats.totalItems} ${s.statTotalMedia}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .then(
                        if (effectiveAnimations) {
                            Modifier.graphicsLayer { translationY = iconBob; rotationZ = iconRot }
                        } else Modifier
                    )
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(NeonGreen.copy(alpha = 0.15f))
                    .border(1.dp, NeonGreen.copy(alpha = borderAlpha), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayCircle, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
internal fun ActiveMediaFiltersSummaryPill(
    sourceFilter: MediaSourceType,
    mediaTypeFilter: MediaType,
    watchStatusFilter: WatchStatus?,
    sortOption: MediaSortOption,
    onOpenFilterSheet: () -> Unit,
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalAppStrings.current
    val language = LocalAppLanguage.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .clickable(onClick = onOpenFilterSheet)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
            Text(
                text = buildString {
                    if (sourceFilter != MediaSourceType.ALL) append(sourceFilter.localizedLabel(language))
                    if (mediaTypeFilter != MediaType.ALL) {
                        if (isNotEmpty()) append(" • ")
                        append(mediaTypeFilter.localizedLabel(language))
                    }
                    if (watchStatusFilter != null) {
                        if (isNotEmpty()) append(" • ")
                        append(watchStatusFilter.localizedLabel(language))
                    }
                    if (sortOption != MediaSortOption.RECENTLY_PLAYED) {
                        if (isNotEmpty()) append(" • ")
                        append(sortOption.localizedLabel(language))
                    }
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = s.filterReset,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onResetFilters)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
internal fun rememberAnimatedBeamAngle(enabled: Boolean): Float {
    if (!enabled) return 0f
    val transition = rememberInfiniteTransition(label = "borderBeamTransition")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 3800, easing = LinearEasing), RepeatMode.Restart),
        label = "borderBeamAngle"
    )
    return angle
}

@Composable
private fun rememberVaultBannerAnimations(enabled: Boolean): Triple<Float, Float, Float> {
    if (!enabled) return Triple(0.6f, 0f, 0f)
    val transition = rememberInfiniteTransition(label = "vaultBannerAnim")
    val borderAlpha by transition.animateFloat(0.4f, 0.9f, infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "vaultBorderAlpha")
    val medalBob by transition.animateFloat(-2.5f, 2.5f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "vaultIconBob")
    val medalRot by transition.animateFloat(-5f, 5f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "vaultIconRot")
    return Triple(borderAlpha, medalBob, medalRot)
}

@Composable
internal fun rememberEmptyBob(enabled: Boolean): Float {
    if (!enabled) return 0f
    val transition = rememberInfiniteTransition(label = "emptyBob")
    val bob by transition.animateFloat(-4f, 4f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "emptyIconBob")
    return bob
}
