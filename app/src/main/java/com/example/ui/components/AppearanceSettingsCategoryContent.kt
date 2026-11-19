package com.example.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MainTab
import com.example.i18n.AppLanguage
import com.example.i18n.LocalAppLanguage
import com.example.i18n.LocalAppStrings
import com.example.i18n.localizedLabel
import com.example.preferences.BackgroundTheme
import com.example.preferences.CardDensity
import com.example.preferences.DpiScaleMode
import com.example.preferences.ThemeMode
import com.example.ui.LootStreamViewModel

@Composable
fun AppearanceSettingsCategoryContent(
    viewModel: LootStreamViewModel
) {
    val s = LocalAppStrings.current
    val lang = LocalAppLanguage.current
    val themeMode by viewModel.themeMode.collectAsState()
    val accentTheme by viewModel.accentTheme.collectAsState()
    val darkBgTheme by viewModel.darkBackgroundTheme.collectAsState()
    val lightBgTheme by viewModel.lightBackgroundTheme.collectAsState()
    val cardDensity by viewModel.cardDensity.collectAsState()
    val customFontPath by viewModel.customFontPath.collectAsState()
    val customFontName by viewModel.customFontName.collectAsState()
    val dpiScaleMode by viewModel.dpiScaleMode.collectAsState()
    val defaultTab by viewModel.defaultTab.collectAsState()

    val isCurrentDark = themeMode != ThemeMode.LIGHT

    SectionHeader(title = s.settingsThemeMode, icon = Icons.Default.BrightnessAuto)
    Spacer(modifier = Modifier.height(8.dp))

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeMode.entries.forEach { mode ->
            val isSelected = mode == themeMode
            val icon = when (mode) {
                ThemeMode.DARK -> Icons.Default.DarkMode
                ThemeMode.LIGHT -> Icons.Default.LightMode
                ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface)
                    .border(
                        if (isSelected) 1.5.dp else 1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { viewModel.setThemeMode(mode) }
                    .padding(vertical = 12.dp)
                    .testTag("theme_mode_${mode.name.lowercase()}"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = mode.localizedLabel(lang),
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    AccentThemePickerSection(
        accentTheme = accentTheme,
        isCurrentDark = isCurrentDark,
        viewModel = viewModel
    )

    Spacer(modifier = Modifier.height(20.dp))

    SectionHeader(title = s.settingsBackgroundPalette, icon = Icons.Default.ColorLens)
    Spacer(modifier = Modifier.height(8.dp))

    SettingsOptionCard {
        val availableThemes = BackgroundTheme.entries.filter {
            if (themeMode == ThemeMode.LIGHT) !it.isDark else it.isDark
        }
        availableThemes.forEach { bg ->
            val isSelected = if (themeMode == ThemeMode.LIGHT) bg == lightBgTheme else bg == darkBgTheme
            SelectableOptionRow(
                title = bg.title,
                subtitle = null,
                selected = isSelected,
                lang = lang,
                testTag = "bg_theme_${bg.name.lowercase()}",
                onClick = {
                    if (themeMode == ThemeMode.LIGHT) viewModel.setLightBackgroundTheme(bg)
                    else viewModel.setDarkBackgroundTheme(bg)
                },
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(bg.backgroundColor)
                            .border(1.dp, bg.borderColor, RoundedCornerShape(6.dp))
                    )
                }
            )
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    CustomFontSettingsCard(
        customFontPath = customFontPath,
        customFontName = customFontName,
        viewModel = viewModel
    )

    Spacer(modifier = Modifier.height(20.dp))

    SectionHeader(title = s.settingsCardDensity, icon = Icons.Default.ViewAgenda)
    Spacer(modifier = Modifier.height(8.dp))

    SettingsOptionCard {
        CardDensity.entries.forEach { density ->
            SelectableOptionRow(
                title = density.localizedLabel(lang),
                subtitle = density.description,
                selected = density == cardDensity,
                lang = lang,
                testTag = "density_${density.name.lowercase()}",
                onClick = { viewModel.setCardDensity(density) }
            )
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    SectionHeader(
        title = when (lang) {
            AppLanguage.SPANISH -> "ESCALA DE UI Y TAMAÑO EN PANTALLA"
            AppLanguage.FRENCH -> "ÉCHELLE DE L'INTERFACE & TAILLE D'ÉCRAN"
            AppLanguage.ENGLISH -> "UI SCALE & SCREEN FOOTPRINT"
        },
        icon = Icons.Default.Tune
    )
    Spacer(modifier = Modifier.height(8.dp))

    SettingsOptionCard {
        DpiScaleMode.entries.forEach { mode ->
            SelectableOptionRow(
                title = mode.localizedLabel(lang),
                subtitle = mode.description,
                selected = mode == dpiScaleMode,
                lang = lang,
                testTag = "dpi_scale_${mode.name.lowercase()}",
                onClick = { viewModel.setDpiScaleMode(mode) }
            )
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    SectionHeader(title = s.settingsDefaultTab, icon = Icons.Default.Home)
    Spacer(modifier = Modifier.height(8.dp))

    SettingsOptionCard {
        listOf(
            Triple(MainTab.HOME, Icons.Default.Home, s.subtitleHome),
            Triple(MainTab.LIBRARY, Icons.Default.VideoLibrary, s.subtitleLibrary),
            Triple(MainTab.UP_NEXT, Icons.Default.QueuePlayNext, s.subtitleUpNext),
            Triple(MainTab.COLLECTIONS, Icons.Default.Bookmarks, s.subtitleVault),
            Triple(MainTab.SETTINGS, Icons.Default.Settings, s.subtitleSettings)
        ).forEach { (tab, icon, description) ->
            val isSelected = tab == defaultTab
            SelectableOptionRow(
                title = tab.localizedLabel(lang),
                subtitle = description,
                selected = isSelected,
                lang = lang,
                testTag = "default_tab_${tab.name.lowercase()}",
                onClick = { viewModel.setDefaultTab(tab) },
                leadingContent = { TabOptionIcon(icon = icon, selected = isSelected) }
            )
        }
    }
}

@Composable
private fun SettingsOptionCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        content()
    }
}

@Composable
private fun SelectableOptionRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    lang: AppLanguage,
    testTag: String,
    onClick: () -> Unit,
    leadingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            leadingContent?.invoke()
            Column {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = when (lang) {
                    AppLanguage.SPANISH -> "Seleccionado"
                    AppLanguage.FRENCH -> "Sélectionné"
                    AppLanguage.ENGLISH -> "Selected"
                },
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun TabOptionIcon(icon: ImageVector, selected: Boolean) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}
