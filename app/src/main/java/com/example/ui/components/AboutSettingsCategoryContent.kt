package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import com.example.i18n.AppLanguage
import com.example.i18n.LocalAppLanguage
import com.example.i18n.LocalAppStrings
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen

private data class TechCreditEntry(
    val title: String,
    val role: String,
    val description: String,
    val icon: ImageVector,
    val badge: String? = null,
    val testTag: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutSettingsCategoryContent() {
    val s = LocalAppStrings.current
    val lang = LocalAppLanguage.current

    SectionHeader(
        title = when (lang) {
            AppLanguage.SPANISH -> "ACERCA DE LOOTSTREAM"
            AppLanguage.FRENCH -> "À PROPOS DE LOOTSTREAM"
            AppLanguage.ENGLISH -> "ABOUT LOOTSTREAM"
        },
        icon = Icons.Default.Info
    )

    Spacer(modifier = Modifier.height(8.dp))

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("about_hero_card"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_lootstream_logo),
                    contentDescription = "LootStream App Icon",
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            RoundedCornerShape(14.dp)
                        )
                )

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "LootStream",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "V${BuildConfig.VERSION_NAME}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "Build ${BuildConfig.VERSION_CODE} • ${s.aboutSubtitle}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = when (lang) {
                    AppLanguage.SPANISH -> "Reproductor multimedia personal privado para tu PC Host Windows (LootStreamHost.exe), videos locales con Photo Picker y streams HLS/DASH con Jetpack Media3."
                    AppLanguage.FRENCH -> "Lecteur multimédia personnel pour votre PC Host Windows (LootStreamHost.exe), vidéos locales et flux HLS/DASH avec Jetpack Media3."
                    AppLanguage.ENGLISH -> "Private personal media hub built for your Windows PC Host (LootStreamHost.exe), zero-permission local videos & audio, and direct HLS/DASH streams powered by Jetpack Media3."
                },
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AboutPillBadge(text = "Media3 ExoPlayer", accentColor = NeonGreen)
                AboutPillBadge(text = "LootStream PC Host", accentColor = MaterialTheme.colorScheme.primary)
                AboutPillBadge(text = "Zero Ads / Private", accentColor = NeonCyan)
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    SectionHeader(
        title = when (lang) {
            AppLanguage.SPANISH -> "MOTOR MULTIMEDIA Y PROTOCOLOS"
            AppLanguage.FRENCH -> "MOTEUR MÉDIA & PROTOCOLES"
            AppLanguage.ENGLISH -> "MEDIA ENGINE & PROTOCOLS"
        },
        icon = Icons.Default.Api
    )

    Spacer(modifier = Modifier.height(8.dp))

    val engineCredits = listOf(
        TechCreditEntry(
            title = "Jetpack Media3 (ExoPlayer)",
            role = when (lang) {
                AppLanguage.SPANISH -> "Motor de Reproducción Hardware"
                AppLanguage.FRENCH -> "Moteur de Lecture Matériel"
                AppLanguage.ENGLISH -> "Hardware Playback Engine"
            },
            description = when (lang) {
                AppLanguage.SPANISH -> "Soporte nativo para HLS (.m3u8), MPEG-DASH (.mpd), MP4, MKV, velocidad variable, ajuste de relación de aspecto y barra flotante."
                AppLanguage.FRENCH -> "Prise en charge native HLS (.m3u8), MPEG-DASH (.mpd), MP4, MKV, vitesse variable, recadrage et mini-lecteur."
                AppLanguage.ENGLISH -> "Hardware-accelerated playback for HLS (.m3u8), MPEG-DASH (.mpd), MP4, MKV, FLAC/MP3, variable speed & mini-player."
            },
            icon = Icons.Default.PlayCircle,
            badge = "MEDIA3",
            testTag = "credit_engine_media3"
        ),
        TechCreditEntry(
            title = "LootStreamHost.exe LAN Protocol",
            role = when (lang) {
                AppLanguage.SPANISH -> "Sincronización con Host de PC Propio"
                AppLanguage.FRENCH -> "Synchronisation avec PC Host Personnel"
                AppLanguage.ENGLISH -> "Windows PC Host & LAN Streaming"
            },
            description = when (lang) {
                AppLanguage.SPANISH -> "Emparejamiento directo por LAN con PIN opcional, escaneo automático de carpetas en Windows, HTTP Range 206 y sincronización de progreso."
                AppLanguage.FRENCH -> "Appairage LAN direct par code PIN, scan de dossiers Windows, streaming HTTP Range 206 et suivi de lecture."
                AppLanguage.ENGLISH -> "Direct LAN pairing with optional PIN, automatic Windows media folder indexing, HTTP Range 206 streaming & progress sync."
            },
            icon = Icons.Default.Dns,
            badge = "PC HOST",
            testTag = "credit_engine_pc_host"
        ),
        TechCreditEntry(
            title = "Storage Access Framework & Photo Picker",
            role = when (lang) {
                AppLanguage.SPANISH -> "Importación Local Sin Permisos Invasivos"
                AppLanguage.FRENCH -> "Importation Locale Sans Permissions"
                AppLanguage.ENGLISH -> "Zero-Permission Local Import"
            },
            description = when (lang) {
                AppLanguage.SPANISH -> "Importa videos, audio y fuentes .ttf/.otf mediante el selector del sistema Android sin permisos de almacenamiento global."
                AppLanguage.FRENCH -> "Importe vidéos, audio et polices .ttf/.otf via le sélecteur Android sans accès complet au stockage."
                AppLanguage.ENGLISH -> "Imports local videos, audio tracks, JSON backups, and custom .ttf/.otf fonts via Android Photo Picker & SAF."
            },
            icon = Icons.Default.Folder,
            badge = "PRIVACY",
            testTag = "credit_engine_saf"
        )
    )

    TechCreditsGroupCard(entries = engineCredits, cardTestTag = "about_engines_card")

    Spacer(modifier = Modifier.height(20.dp))

    SectionHeader(
        title = when (lang) {
            AppLanguage.SPANISH -> "ARQUITECTURA ANDROID"
            AppLanguage.FRENCH -> "ARCHITECTURE ANDROID"
            AppLanguage.ENGLISH -> "OPEN-SOURCE STACK"
        },
        icon = Icons.Default.Layers
    )

    Spacer(modifier = Modifier.height(8.dp))

    val techStack = listOf(
        TechCreditEntry(
            title = "Kotlin & Jetpack Compose (Material 3)",
            role = when (lang) {
                AppLanguage.SPANISH -> "Interfaz Reactiva y 23 Temas"
                AppLanguage.FRENCH -> "Interface Réactive & 23 Thèmes"
                AppLanguage.ENGLISH -> "Declarative UI & Cyber M3 Themes"
            },
            description = when (lang) {
                AppLanguage.SPANISH -> "Diseño adaptativo con soporte para DPI dinámico, 60/90/120 FPS y fuentes personalizadas."
                AppLanguage.FRENCH -> "Mise en page adaptative avec DPI dynamique, 60/90/120 FPS et polices personnalisées."
                AppLanguage.ENGLISH -> "Adaptive edge-to-edge UI with spring physics, 23 accent themes, custom fonts, and 60/90/120 FPS support."
            },
            icon = Icons.Default.Code,
            testTag = "credit_tech_compose"
        ),
        TechCreditEntry(
            title = "AndroidX Room, OkHttp & Coil",
            role = when (lang) {
                AppLanguage.SPANISH -> "Base de Datos Local, Red y Carátulas"
                AppLanguage.FRENCH -> "Base Locale, Réseau & Jaquettes"
                AppLanguage.ENGLISH -> "Local Persistence, Networking & Artwork"
            },
            description = when (lang) {
                AppLanguage.SPANISH -> "Persistencia en SQLite con Room, caché HTTP eficiente y renderizado veloz de miniaturas."
                AppLanguage.FRENCH -> "Persistance SQLite Room, cache HTTP performant et rendu rapide des miniatures."
                AppLanguage.ENGLISH -> "Room SQLite persistence for servers & watch positions, OkHttp networking, and Coil poster caching."
            },
            icon = Icons.Default.Layers,
            testTag = "credit_tech_storage"
        )
    )

    TechCreditsGroupCard(entries = techStack, cardTestTag = "about_tech_stack_card")
}

@Composable
private fun TechCreditsGroupCard(
    entries: List<TechCreditEntry>,
    cardTestTag: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .testTag(cardTestTag)
    ) {
        entries.forEachIndexed { index, entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag(entry.testTag),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                        .border(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = entry.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = entry.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (entry.badge != null) {
                            AboutPillBadge(text = entry.badge, accentColor = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Text(
                        text = entry.role,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = entry.description,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (index < entries.lastIndex) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
            }
        }
    }
}

@Composable
private fun AboutPillBadge(
    text: String,
    accentColor: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accentColor.copy(alpha = 0.14f))
            .border(0.6.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            color = accentColor
        )
    }
}
