package com.example.i18n

import android.content.Context
import android.content.res.Configuration
import com.example.shortcuts.AppShortcutHelper
import java.util.Locale

object LocaleSyncHelper {
    fun applyAppLanguage(context: Context, language: AppLanguage): Context {
        val locale = language.locale
        Locale.setDefault(locale)

        val resources = context.resources
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)

        AppShortcutHelper.registerDynamicShortcuts(context)
        return context.createConfigurationContext(config)
    }
}
