package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.i18n.AppLanguage
import com.example.i18n.LocalAppLanguage
import com.example.i18n.LocalAppStrings
import com.example.ui.LootStreamViewModel

@Composable
fun StorageSettingsCategoryContent(
    viewModel: LootStreamViewModel,
    onRequestResyncDialog: () -> Unit,
    onRequestClearLocalDialog: () -> Unit
) {
    val s = LocalAppStrings.current
    val lang = LocalAppLanguage.current
    val storageMetrics by viewModel.storageMetrics.collectAsState()
    val isDataSaverMode by viewModel.isDataSaverMode.collectAsState()

    SectionHeader(title = s.settingsCacheMetrics, icon = Icons.Default.Speed)
    Spacer(modifier = Modifier.height(8.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(14.dp)
            .testTag("storage_accelerator_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = when (lang) {
                        AppLanguage.SPANISH -> "Espacio en caché total"
                        AppLanguage.FRENCH -> "Empreinte totale du cache"
                        AppLanguage.ENGLISH -> "Total Cache & DB Footprint"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = storageMetrics.formattedTotal,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "MEDIA3 + ROOM",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StorageMetricBox(
                label = when (lang) {
                    AppLanguage.SPANISH -> "MINIATURAS"
                    AppLanguage.FRENCH -> "JAQUETTES"
                    AppLanguage.ENGLISH -> "POSTERS"
                },
                value = storageMetrics.formattedImageCache,
                modifier = Modifier.weight(1f)
            )
            StorageMetricBox(
                label = when (lang) {
                    AppLanguage.SPANISH -> "RED"
                    AppLanguage.FRENCH -> "RÉSEAU"
                    AppLanguage.ENGLISH -> "NETWORK"
                },
                value = storageMetrics.formattedHttpCache,
                modifier = Modifier.weight(1f)
            )
            StorageMetricBox(
                label = when (lang) {
                    AppLanguage.SPANISH -> "BASE DATOS"
                    AppLanguage.FRENCH -> "BASE"
                    AppLanguage.ENGLISH -> "DATABASE"
                },
                value = storageMetrics.formattedDatabase,
                modifier = Modifier.weight(1f)
            )
        }

        Button(
            onClick = { viewModel.clearImageAndHttpCaches() },
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .height(48.dp)
                .testTag("deep_clean_button"),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.background
            )
        ) {
            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = s.settingsClearCache,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = s.settingsDataSaver,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when (lang) {
                        AppLanguage.SPANISH -> "Limita el tamaño de caché de imágenes para reducir memoria y datos"
                        AppLanguage.FRENCH -> "Limite le cache d'images pour économiser RAM et données"
                        AppLanguage.ENGLISH -> "Reduces poster disk/memory cache footprint on metered connections"
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = isDataSaverMode,
                onCheckedChange = { viewModel.setDataSaverMode(it) },
                modifier = Modifier.testTag("switch_data_saver_mode"),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.background,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    SectionHeader(title = s.settingsBackupTitle, icon = Icons.Default.Sync)
    Spacer(modifier = Modifier.height(8.dp))

    BackupRestoreCard(viewModel = viewModel)

    Spacer(modifier = Modifier.height(20.dp))

    SectionHeader(
        title = when (lang) {
            AppLanguage.SPANISH -> "GESTIÓN DE SERVIDORES Y BIBLIOTECA"
            AppLanguage.FRENCH -> "GESTION SERVEURS & MÉDIATHÈQUE"
            AppLanguage.ENGLISH -> "SERVERS & LIBRARY MANAGEMENT"
        },
        icon = Icons.Default.Delete
    )
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
        OutlinedButton(
            onClick = onRequestResyncDialog,
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("btn_resync_host_servers"),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = s.settingsResyncServers, fontSize = 13.sp)
        }

        OutlinedButton(
            onClick = onRequestClearLocalDialog,
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("btn_clear_local_media"),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Text(text = s.settingsClearLocalMedia, fontSize = 13.sp)
        }
    }
}

@Composable
private fun StorageMetricBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column {
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
