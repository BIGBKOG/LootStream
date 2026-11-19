package com.example.shortcuts

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.MainActivity
import com.example.R
import com.example.i18n.AppLanguage
import com.example.i18n.AppStringsRegistry
import com.example.preferences.AppPreferences

object AppShortcutHelper {
    private const val TAG = "AppShortcutHelper"

    const val ID_UP_NEXT = "shortcut_up_next"
    const val ID_LIBRARY = "shortcut_library"
    const val ID_FAVORITES = "shortcut_favorites"

    const val ACTION_UP_NEXT = "com.example.action.UP_NEXT"
    const val ACTION_LIBRARY = "com.example.action.LIBRARY"
    const val ACTION_FAVORITES = "com.example.action.FAVORITES"

    const val EXTRA_TARGET_TAB = "EXTRA_TARGET_TAB"

    fun registerDynamicShortcuts(context: Context) {
        updateDynamicShortcuts(context)
    }

    fun updateDynamicShortcuts(
        context: Context,
        continueCount: Int? = null,
        totalMediaCount: Int? = null,
        favoritesCount: Int? = null
    ) {
        try {
            val lang = AppPreferences(context).appLanguage
            val s = AppStringsRegistry.get(lang)

            val upNextIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_UP_NEXT
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_TARGET_TAB, "QUEUE")
            }
            val upNextLong = if (continueCount != null && continueCount > 0) {
                when (lang) {
                    AppLanguage.SPANISH -> "$continueCount por continuar"
                    AppLanguage.FRENCH -> "$continueCount à reprendre"
                    AppLanguage.ENGLISH -> "$continueCount in Up Next"
                }
            } else {
                s.subtitleUpNext
            }
            val upNextShortcut = ShortcutInfoCompat.Builder(context, ID_UP_NEXT)
                .setShortLabel(s.tabUpNext)
                .setLongLabel(upNextLong)
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_calendar))
                .setIntent(upNextIntent)
                .setRank(1)
                .build()

            val libraryIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_LIBRARY
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_TARGET_TAB, "LIBRARY")
            }
            val libraryLong = if (totalMediaCount != null && totalMediaCount > 0) {
                when (lang) {
                    AppLanguage.SPANISH -> "$totalMediaCount elementos multimedia"
                    AppLanguage.FRENCH -> "$totalMediaCount médias"
                    AppLanguage.ENGLISH -> "$totalMediaCount media items"
                }
            } else {
                s.subtitleLibrary
            }
            val libraryShortcut = ShortcutInfoCompat.Builder(context, ID_LIBRARY)
                .setShortLabel(s.tabLibrary)
                .setLongLabel(libraryLong)
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_drops))
                .setIntent(libraryIntent)
                .setRank(2)
                .build()

            val favIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_FAVORITES
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_TARGET_TAB, "VAULT")
            }
            val favLong = if (favoritesCount != null && favoritesCount > 0) {
                when (lang) {
                    AppLanguage.SPANISH -> "$favoritesCount favoritos en bóveda"
                    AppLanguage.FRENCH -> "$favoritesCount favoris dans le coffre"
                    AppLanguage.ENGLISH -> "$favoritesCount vaulted favorites"
                }
            } else {
                s.subtitleVault
            }
            val favShortcut = ShortcutInfoCompat.Builder(context, ID_FAVORITES)
                .setShortLabel(s.tabVault)
                .setLongLabel(favLong)
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_vault))
                .setIntent(favIntent)
                .setRank(3)
                .build()

            ShortcutManagerCompat.setDynamicShortcuts(
                context,
                listOf(upNextShortcut, libraryShortcut, favShortcut)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update dynamic shortcuts: ${e.message}")
        }
    }

    fun reportShortcutUsed(context: Context, shortcutId: String) {
        runCatching { ShortcutManagerCompat.reportShortcutUsed(context, shortcutId) }
    }
}
