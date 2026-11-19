package com.example.data.model

import java.io.File

/**
 * User Profile model stored in local app data and included in JSON backup export/import.
 */
data class UserProfile(
    val name: String = "",
    val avatarPath: String = ""
) {
    val displayName: String
        get() = name.trim().ifBlank { "Streamer" }

    val initial: String
        get() = name.trim().take(1).uppercase().ifBlank { "L" }

    val hasCustomAvatar: Boolean
        get() = avatarPath.isNotBlank() && File(avatarPath).exists() && File(avatarPath).length() > 0
}
