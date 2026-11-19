package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.i18n.AppLanguage
import com.example.i18n.LocalAppLanguage
import com.example.i18n.LocalAppStrings
import com.example.ui.components.AboutSettingsCategoryContent
import com.example.ui.components.AppearanceSettingsCategoryContent
import com.example.ui.components.GeneralSettingsCategoryContent
import com.example.ui.components.PlaybackSettingsCategoryContent
import com.example.ui.components.SettingsFooter
import com.example.ui.components.StorageSettingsCategoryContent
import com.example.ui.theme.ExpressiveMotion
import com.example.ui.theme.rememberExpressiveCornerRadius

@Composable
fun SettingsScreen(
    viewModel: LootStreamViewModel,
    scrollState: ScrollState = rememberScrollState(),
    modifier: Modifier = Modifier
) {
    val s = LocalAppStrings.current
    val lang = LocalAppLanguage.current
    val isAnimationsEnabled by viewModel.isAnimationsEnabled.collectAsState()
    val isLowEndOptimization by viewModel.isLowEndOptimization.collectAsState()
    val reducedMotion = !isAnimationsEnabled || isLowEndOptimization

    var selectedCategory by rememberSaveable { mutableStateOf(SettingsCategory.APPEARANCE) }
    var showClearLocalDialog by remember { mutableStateOf(false) }
    var showResyncDialog by remember { mutableStateOf(false) }

    LaunchedEffect(selectedCategory) {
        scrollState.animateScrollTo(0)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("settings_screen")
    ) {
        // Section Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column {
                Text(
                    text = s.tabSettings,
                    style = MaterialTheme.typography.headlineMedium,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = s.subtitleSettings,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Categorized Settings Tabs Bar
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_category_tabs")
        ) {
            items(SettingsCategory.entries) { category ->
                val isSelected = category == selectedCategory
                val tabInteraction = remember { MutableInteractionSource() }
                val isPressed by tabInteraction.collectIsPressedAsState()
                val tabScale by animateFloatAsState(
                    targetValue = if (isPressed) 0.92f else if (isSelected) 1.02f else 1f,
                    animationSpec = ExpressiveMotion.spatialFast(reducedMotion),
                    label = "settings_tab_scale_${category.name}"
                )
                val tabCorner = rememberExpressiveCornerRadius(
                    isSelected = isSelected,
                    isPressed = isPressed,
                    baseRadius = 12.dp,
                    selectedRadius = 16.dp,
                    pressedRadius = 20.dp,
                    enabled = !reducedMotion
                )
                val tabBg by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                    animationSpec = ExpressiveMotion.effectsDefault(reducedMotion),
                    label = "settings_tab_bg_${category.name}"
                )
                Surface(
                    onClick = { selectedCategory = category },
                    interactionSource = tabInteraction,
                    shape = RoundedCornerShape(tabCorner),
                    color = tabBg,
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .height(48.dp)
                        .graphicsLayer {
                            scaleX = tabScale
                            scaleY = tabScale
                        }
                        .testTag("settings_tab_${category.name.lowercase()}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 14.dp)
                    ) {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = category.localizedLabel(s),
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Active Category Description Pill
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
                AnimatedContent(
                    targetState = selectedCategory.localizedDescription(s),
                    transitionSpec = { ExpressiveMotion.popNumberTransform(reducedMotion) },
                    label = "settings_category_desc"
                ) { desc ->
                    Text(
                        text = desc,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        AnimatedContent(
            targetState = selectedCategory,
            transitionSpec = {
                val forward = targetState.ordinal >= initialState.ordinal
                ExpressiveMotion.directionalTabSlideTransform(forward = forward, reducedMotion = reducedMotion)
            },
            label = "settings_category_content"
        ) { category ->
            Column(modifier = Modifier.fillMaxWidth()) {
                when (category) {
                    SettingsCategory.APPEARANCE -> AppearanceSettingsCategoryContent(viewModel = viewModel)
                    SettingsCategory.PLAYBACK -> PlaybackSettingsCategoryContent(viewModel = viewModel)
                    SettingsCategory.GENERAL -> GeneralSettingsCategoryContent(viewModel = viewModel)
                    SettingsCategory.STORAGE -> StorageSettingsCategoryContent(
                        viewModel = viewModel,
                        onRequestResyncDialog = { showResyncDialog = true },
                        onRequestClearLocalDialog = { showClearLocalDialog = true }
                    )
                    SettingsCategory.ABOUT -> AboutSettingsCategoryContent()
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        SettingsFooter()

        Spacer(modifier = Modifier.height(104.dp))
    }

    if (showClearLocalDialog) {
        AlertDialog(
            onDismissRequest = { showClearLocalDialog = false },
            title = { Text(s.settingsClearLocalMedia) },
            text = {
                Text(
                    when (lang) {
                        AppLanguage.SPANISH -> "Esto quitará las entradas locales y URLs de streaming importadas en LootStream. Los archivos originales en tu dispositivo no se eliminan."
                        AppLanguage.FRENCH -> "Cela supprimera les fichiers locaux et URLs importés dans LootStream. Vos fichiers d'origine restent intacts."
                        AppLanguage.ENGLISH -> "This removes imported local files and direct stream URLs from LootStream's catalog. Original files on your device are not deleted."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearLocalAndStreamMedia()
                        showClearLocalDialog = false
                    }
                ) {
                    Text(s.confirmAction, color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearLocalDialog = false }) {
                    Text(s.cancelAction, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showResyncDialog) {
        AlertDialog(
            onDismissRequest = { showResyncDialog = false },
            title = { Text(s.settingsResyncServers) },
            text = {
                Text(
                    when (lang) {
                        AppLanguage.SPANISH -> "Actualiza bibliotecas, portadas y progreso de reproducción desde todos los PC Host conectados."
                        AppLanguage.FRENCH -> "Actualise les médiathèques, jaquettes et progression depuis tous les PC Host connectés."
                        AppLanguage.ENGLISH -> "Refreshes libraries, HD artwork, and watch progress across all connected PC Host servers."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.syncAllServers(silent = false)
                        showResyncDialog = false
                    }
                ) {
                    Text(s.confirmAction, color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResyncDialog = false }) {
                    Text(s.cancelAction, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}
