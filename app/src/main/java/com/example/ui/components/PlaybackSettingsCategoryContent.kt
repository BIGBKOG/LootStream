package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PlayerResizeMode
import com.example.i18n.AppLanguage
import com.example.i18n.LocalAppLanguage
import com.example.i18n.LocalAppStrings
import com.example.i18n.localizedLabel
import com.example.ui.LootStreamViewModel
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import kotlin.math.abs

@Composable
fun PlaybackSettingsCategoryContent(
    viewModel: LootStreamViewModel
) {
    val s = LocalAppStrings.current
    val lang = LocalAppLanguage.current
    val defaultSpeed by viewModel.defaultPlaybackSpeed.collectAsState()
    val resizeMode by viewModel.playerResizeMode.collectAsState()
    val autoPlayNext by viewModel.autoPlayNext.collectAsState()
    val autoSyncOnOpen by viewModel.autoSyncServersOnOpen.collectAsState()
    val showDurationBadge by viewModel.showDurationBadge.collectAsState()
    val showResumeBar by viewModel.showResumeBar.collectAsState()
    val servers by viewModel.servers.collectAsState()

    SectionHeader(title = s.settingsDefaultSpeed, icon = Icons.Default.Speed)
    Spacer(modifier = Modifier.height(8.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = when (lang) {
                AppLanguage.SPANISH -> "Velocidad inicial al reproducir videos, películas o audio"
                AppLanguage.FRENCH -> "Vitesse initiale lors de la lecture de vidéos ou d'audio"
                AppLanguage.ENGLISH -> "Default playback speed when starting any video or audio stream"
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                val isSelected = abs(defaultSpeed - speed) < 0.05f
                val label = if (speed == 1.0f) "1.0x" else "${speed}x"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { viewModel.setDefaultPlaybackSpeed(speed) }
                        .padding(vertical = 10.dp)
                        .testTag("speed_option_$label"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    SectionHeader(title = s.settingsResizeMode, icon = Icons.Default.AspectRatio)
    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PlayerResizeMode.entries.forEach { mode ->
            val isSelected = mode == resizeMode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface)
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { viewModel.setPlayerResizeMode(mode) }
                    .padding(vertical = 12.dp)
                    .testTag("resize_mode_${mode.name.lowercase()}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = mode.localizedLabel(lang),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    SectionHeader(
        title = when (lang) {
            AppLanguage.SPANISH -> "COMPORTAMIENTO DE REPRODUCCIÓN"
            AppLanguage.FRENCH -> "COMPORTEMENT DE LECTURE"
            AppLanguage.ENGLISH -> "PLAYER & STREAMING OPTIONS"
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
            title = s.settingsAutoPlayNext,
            subtitle = when (lang) {
                AppLanguage.SPANISH -> "Avanza automáticamente al siguiente elemento de la cola"
                AppLanguage.FRENCH -> "Passe automatiquement au média suivant dans la file"
                AppLanguage.ENGLISH -> "Automatically advance to the next item in your queue"
            },
            checked = autoPlayNext,
            switchModifier = Modifier.testTag("switch_auto_play_next"),
            onCheckedChange = { viewModel.setAutoPlayNext(it) }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

        SimpleSettingSwitchRow(
            title = s.settingsAutoSyncOnOpen,
            subtitle = when (lang) {
                AppLanguage.SPANISH -> "Actualiza las bibliotecas de tu PC Host al iniciar LootStream"
                AppLanguage.FRENCH -> "Actualise les médiathèques du PC Host à l'ouverture"
                AppLanguage.ENGLISH -> "Refresh connected PC Host libraries when opening LootStream"
            },
            checked = autoSyncOnOpen,
            switchModifier = Modifier.testTag("switch_auto_sync_servers"),
            onCheckedChange = { viewModel.setAutoSyncServersOnOpen(it) }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

        SimpleSettingSwitchRow(
            title = s.settingsShowDuration,
            subtitle = when (lang) {
                AppLanguage.SPANISH -> "Muestra la duración total en las portadas multimedia"
                AppLanguage.FRENCH -> "Affiche la durée sur les jaquettes"
                AppLanguage.ENGLISH -> "Show runtime badges on media thumbnails and cards"
            },
            checked = showDurationBadge,
            switchModifier = Modifier.testTag("switch_show_duration"),
            onCheckedChange = { viewModel.setShowDurationBadge(it) }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

        SimpleSettingSwitchRow(
            title = s.settingsShowResumeBar,
            subtitle = when (lang) {
                AppLanguage.SPANISH -> "Muestra la barra de progreso en videos iniciados"
                AppLanguage.FRENCH -> "Affiche la barre de progression sur les vidéos en cours"
                AppLanguage.ENGLISH -> "Show playback progress indicator bars on partially watched items"
            },
            checked = showResumeBar,
            switchModifier = Modifier.testTag("switch_show_resume_bar"),
            onCheckedChange = { viewModel.setShowResumeBar(it) }
        )
    }

    Spacer(modifier = Modifier.height(20.dp))

    SectionHeader(title = s.hostConnectedServers, icon = Icons.Default.Dns)
    Spacer(modifier = Modifier.height(8.dp))

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(NeonCyan.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (servers.isEmpty()) s.hostNoServers else "${servers.size} PC Host(s)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (servers.isEmpty()) {
                            when (lang) {
                                AppLanguage.SPANISH -> "Conecta tu Host Linux o Windows (LootStreamHost.exe) para transmitir directo"
                                AppLanguage.FRENCH -> "Connectez votre Hôte Linux ou Windows (LootStreamHost.exe) pour diffuser"
                                AppLanguage.ENGLISH -> "Connect your Linux Host or Windows LootStreamHost.exe for direct LAN streaming"
                            }
                        } else {
                            servers.joinToString(" • ") { "${it.name} (${it.itemCount})" }
                        },
                        fontSize = 11.sp,
                        color = if (servers.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else NeonGreen
                    )
                }
            }

            Button(
                onClick = { viewModel.setShowHostDialog(true) },
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("manage_host_servers_btn"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color(0xFF0B0F19)
                )
            ) {
                Text(
                    text = s.btnConnectHost,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
