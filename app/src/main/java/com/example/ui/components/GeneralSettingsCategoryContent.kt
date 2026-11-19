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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MediaSourceType
import com.example.i18n.AppLanguage
import com.example.i18n.LocalAppLanguage
import com.example.i18n.LocalAppStrings
import com.example.i18n.localizedLabel
import com.example.ui.LootStreamViewModel

@Composable
fun GeneralSettingsCategoryContent(
    viewModel: LootStreamViewModel
) {
    val s = LocalAppStrings.current
    val lang = LocalAppLanguage.current
    val appLanguage by viewModel.appLanguage.collectAsState()
    val defaultSourceFilter by viewModel.defaultSourceFilter.collectAsState()
    val isAnimationsEnabled by viewModel.isAnimationsEnabled.collectAsState()
    val isDpiOptimizationEnabled by viewModel.isDpiOptimizationEnabled.collectAsState()
    val targetFrameRate by viewModel.targetFrameRate.collectAsState()
    val detectedRefreshRate by viewModel.detectedRefreshRate.collectAsState()
    val isLowEndOptimization by viewModel.isLowEndOptimization.collectAsState()

    SectionHeader(title = s.settingsLanguage, icon = Icons.Default.Language)
    Spacer(modifier = Modifier.height(8.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(14.dp)
            .testTag("settings_language_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = when (lang) {
                AppLanguage.SPANISH -> "Elige el idioma de la interfaz en toda la aplicación"
                AppLanguage.FRENCH -> "Choisissez la langue de l'interface dans toute l'application"
                AppLanguage.ENGLISH -> "Choose the interface language across all tabs and dialogs"
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp
        )
        listOf(
            Triple(AppLanguage.ENGLISH, "English (US / Global)", "settings_language_en"),
            Triple(AppLanguage.SPANISH, "Español (Latinoamérica / España)", "settings_language_es"),
            Triple(AppLanguage.FRENCH, "Français (Standard)", "settings_language_fr")
        ).forEach { (language, desc, tag) ->
            val isSelected = appLanguage == language
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent)
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { viewModel.setAppLanguage(language) }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag(tag),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = language.flagEmoji, fontSize = 20.sp)
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = language.nativeName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = when (lang) {
                                            AppLanguage.SPANISH -> "ACTIVO"
                                            AppLanguage.FRENCH -> "ACTIF"
                                            AppLanguage.ENGLISH -> "ACTIVE"
                                        },
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        Text(
                            text = desc,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = language.nativeName,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    SectionHeader(title = s.settingsDefaultSource, icon = Icons.Default.FilterList)
    Spacer(modifier = Modifier.height(8.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = when (lang) {
                AppLanguage.SPANISH -> "Filtro de fuente predeterminado al abrir la Biblioteca"
                AppLanguage.FRENCH -> "Filtre de source par défaut à l'ouverture de la Médiathèque"
                AppLanguage.ENGLISH -> "Default media source filter when opening Library"
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        MediaSourceType.entries.forEach { source ->
            val isSelected = source == defaultSourceFilter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                    .border(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { viewModel.setDefaultSourceFilter(source) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("default_source_${source.name.lowercase()}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(Color(source.badgeColorHex)))
                    Text(
                        text = source.localizedLabel(lang),
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    SectionHeader(
        title = when (lang) {
            AppLanguage.SPANISH -> "ANIMACIONES Y PANTALLA"
            AppLanguage.FRENCH -> "ANIMATIONS & ÉCRAN"
            AppLanguage.ENGLISH -> "MOTION & ADAPTIVE DISPLAY"
        },
        icon = Icons.Default.Tune
    )
    Spacer(modifier = Modifier.height(8.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        SimpleSettingSwitchRow(
            title = s.settingsAnimations,
            subtitle = when (lang) {
                AppLanguage.SPANISH -> "Transiciones expresivas Material 3 y resplandor en tarjetas"
                AppLanguage.FRENCH -> "Transitions expressives Material 3 et bordures lumineuses"
                AppLanguage.ENGLISH -> "Enable expressive Material 3 springs and animated border glows"
            },
            checked = isAnimationsEnabled,
            switchModifier = Modifier.testTag("switch_ui_animations"),
            onCheckedChange = { viewModel.setAnimationsEnabled(it) }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

        SimpleSettingSwitchRow(
            title = when (lang) {
                AppLanguage.SPANISH -> "Optimización Inteligente de DPI"
                AppLanguage.FRENCH -> "Optimisation Intelligente du DPI"
                AppLanguage.ENGLISH -> "Smart DPI Display Scaling"
            },
            subtitle = when (lang) {
                AppLanguage.SPANISH -> "Ajusta cuadrículas y tipografía según la densidad real de tu pantalla"
                AppLanguage.FRENCH -> "Adapte grilles et typographie selon la densité de votre écran"
                AppLanguage.ENGLISH -> "Adapts grids, spacing & typography cleanly across phones and tablets"
            },
            checked = isDpiOptimizationEnabled,
            switchModifier = Modifier.testTag("switch_dpi_optimization"),
            onCheckedChange = { viewModel.setDpiOptimizationEnabled(it) }
        )
    }

    Spacer(modifier = Modifier.height(20.dp))

    PreRenderingPerformanceSection(
        targetFrameRate = targetFrameRate,
        detectedRefreshRate = detectedRefreshRate,
        isLowEndOptimization = isLowEndOptimization,
        onToggleLowEndOptimization = { viewModel.setLowEndOptimization(it) },
        onSelectFrameRate = { viewModel.setTargetFrameRate(it) }
    )

    Spacer(modifier = Modifier.height(20.dp))

    SectionHeader(title = s.settingsReplayTour, icon = Icons.Default.AutoAwesome)
    Spacer(modifier = Modifier.height(8.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = s.settingsReplayTour,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when (lang) {
                        AppLanguage.SPANISH -> "Vuelve a ver el recorrido por el reproductor Media3, PC Host y temas"
                        AppLanguage.FRENCH -> "Revoir le tour du lecteur Media3, PC Host et des thèmes"
                        AppLanguage.ENGLISH -> "Revisit LootStream's tour covering PC Host sync, local import, Media3 & themes"
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )
            }
        }

        Button(
            onClick = { viewModel.replayOnboarding() },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("replay_onboarding_button"),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.background
            )
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("  ${s.settingsReplayTour}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}
