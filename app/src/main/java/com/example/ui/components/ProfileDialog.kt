package com.example.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tour
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.example.data.model.MediaLibraryStats
import com.example.data.model.UserProfile
import com.example.i18n.AppLanguage
import com.example.i18n.LocalAppLanguage
import com.example.i18n.LocalAppStrings
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.LocalLowEndOptimization
import java.io.File
import java.util.Locale

@Composable
fun ProfileDialog(
    profile: UserProfile,
    stats: MediaLibraryStats,
    onOpenHostDialog: () -> Unit,
    onSaveProfile: (name: String, avatarUri: Uri?, removeAvatar: Boolean) -> Unit,
    onReplayTour: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val s = LocalAppStrings.current
    val lang = LocalAppLanguage.current
    val lowEnd = LocalLowEndOptimization.current

    var nameInput by remember(profile.name) { mutableStateOf(profile.name) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var shouldRemoveAvatar by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            shouldRemoveAvatar = false
        }
    }

    val avatarFile = remember(profile.avatarPath) {
        if (profile.avatarPath.isNotBlank()) File(profile.avatarPath) else null
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                .testTag("profile_dialog"),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(NeonCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = s.profileTitle,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = when (lang) {
                                    AppLanguage.SPANISH -> "Guardado localmente y en respaldos JSON"
                                    AppLanguage.FRENCH -> "Stocké localement et dans les sauvegardes JSON"
                                    AppLanguage.ENGLISH -> "Stored locally & in JSON backups"
                                },
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("profile_dialog_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Avatar Preview
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .border(2.dp, Brush.sweepGradient(listOf(NeonCyan, NeonGreen, NeonCyan)), CircleShape)
                        .background(Color(0xFF0F121A)),
                    contentAlignment = Alignment.Center
                ) {
                    val hasPendingUri = selectedImageUri != null
                    val hasExistingFile = !shouldRemoveAvatar && avatarFile != null && avatarFile.exists()
                    when {
                        hasPendingUri -> AsyncImage(
                            model = remember(selectedImageUri, lowEnd) {
                                ImageRequest.Builder(context)
                                    .data(selectedImageUri)
                                    .crossfade(!lowEnd)
                                    .bitmapConfig(if (lowEnd) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888)
                                    .allowRgb565(true)
                                    .precision(if (lowEnd) Precision.INEXACT else Precision.AUTOMATIC)
                                    .size(240, 240)
                                    .build()
                            },
                            contentDescription = "Selected profile avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                        hasExistingFile -> AsyncImage(
                            model = remember(avatarFile, lowEnd) {
                                ImageRequest.Builder(context)
                                    .data(avatarFile)
                                    .crossfade(!lowEnd)
                                    .bitmapConfig(if (lowEnd) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888)
                                    .allowRgb565(true)
                                    .precision(if (lowEnd) Precision.INEXACT else Precision.AUTOMATIC)
                                    .size(240, 240)
                                    .build()
                            },
                            contentDescription = "Profile avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                        nameInput.isNotBlank() -> Text(
                            text = nameInput.trim().take(1).uppercase(),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            color = NeonCyan
                        )
                        else -> Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                // Avatar action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("profile_pick_photo_btn")
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(s.profileChangeAvatar, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    if (selectedImageUri != null || (!shouldRemoveAvatar && profile.hasCustomAvatar)) {
                        TextButton(
                            onClick = {
                                selectedImageUri = null
                                shouldRemoveAvatar = true
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444)),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("profile_remove_photo_btn")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(s.profileRemoveAvatar, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { if (it.length <= 30) nameInput = it },
                    label = { Text(s.profileDisplayName) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_name_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                // Library Summary Stats Strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProfileStatTile(
                        label = s.statTotalMedia,
                        value = stats.totalItems.toString(),
                        accent = NeonCyan,
                        modifier = Modifier.weight(1f)
                    )
                    ProfileStatTile(
                        label = s.statWatched,
                        value = String.format(Locale.US, "%.1fh", stats.watchedDurationHours),
                        accent = NeonGreen,
                        modifier = Modifier.weight(1f)
                    )
                    ProfileStatTile(
                        label = s.statServers,
                        value = stats.connectedServersCount.toString(),
                        accent = Color(0xFFA855F7),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Quick shortcuts
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenHostDialog()
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("profile_host_btn")
                    ) {
                        Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(15.dp), tint = NeonCyan)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(s.actionConnectServer, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onReplayTour()
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("profile_replay_tour_btn")
                    ) {
                        Icon(Icons.Default.Tour, contentDescription = null, modifier = Modifier.size(15.dp), tint = NeonGreen)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(s.settingsReplayTour, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    }
                }

                Button(
                    onClick = {
                        onSaveProfile(nameInput.trim(), selectedImageUri, shouldRemoveAvatar)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("profile_save_btn"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color(0xFF0B0F19)
                    )
                ) {
                    Text(s.profileSave, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun ProfileStatTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = accent,
                textAlign = TextAlign.Center
            )
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
