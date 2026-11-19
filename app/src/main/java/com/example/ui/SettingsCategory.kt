package com.example.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.i18n.AppStrings

enum class SettingsCategory(
    val icon: ImageVector
) {
    APPEARANCE(Icons.Default.Palette),
    PLAYBACK(Icons.Default.PlayCircle),
    GENERAL(Icons.Default.Tune),
    STORAGE(Icons.Default.Storage),
    ABOUT(Icons.Default.Info);

    fun localizedLabel(s: AppStrings): String = when (this) {
        APPEARANCE -> s.catAppearanceTitle
        PLAYBACK -> s.catPlaybackTitle
        GENERAL -> s.catGeneralTitle
        STORAGE -> s.catStorageTitle
        ABOUT -> s.catAboutTitle
    }

    fun localizedDescription(s: AppStrings): String = when (this) {
        APPEARANCE -> s.catAppearanceDesc
        PLAYBACK -> s.catPlaybackDesc
        GENERAL -> s.catGeneralDesc
        STORAGE -> s.catStorageDesc
        ABOUT -> s.catAboutDesc
    }
}
