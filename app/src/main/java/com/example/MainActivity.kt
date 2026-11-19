package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.example.data.model.MainTab
import com.example.i18n.LocaleSyncHelper
import com.example.i18n.ProvideAppStrings
import com.example.preferences.TargetFrameRate
import com.example.shortcuts.AppShortcutHelper
import com.example.ui.HomeScreen
import com.example.ui.LootStreamViewModel
import com.example.ui.density.LocalScreenDensityInfo
import com.example.ui.density.rememberScreenDensityInfo
import com.example.ui.theme.LootDropTheme
import com.example.util.DevicePerformanceHelper
import com.example.util.LocalDevicePerformance
import com.example.util.LocalLowEndOptimization

class MainActivity : ComponentActivity() {

    private val viewModel: LootStreamViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val dm = resources.displayMetrics
        viewModel.setDetectedScreenDpi(dm.densityDpi)

        runCatching { handleIntent(intent) }
        runCatching { AppShortcutHelper.registerDynamicShortcuts(this) }

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val accentTheme by viewModel.accentTheme.collectAsState()
            val darkBgTheme by viewModel.darkBackgroundTheme.collectAsState()
            val lightBgTheme by viewModel.lightBackgroundTheme.collectAsState()
            val customFontPath by viewModel.customFontPath.collectAsState()
            val targetFrameRate by viewModel.targetFrameRate.collectAsState()
            val isDpiOptimizationEnabled by viewModel.isDpiOptimizationEnabled.collectAsState()
            val dpiScaleMode by viewModel.dpiScaleMode.collectAsState()
            val stats by viewModel.stats.collectAsState()
            val appLanguage by viewModel.appLanguage.collectAsState()
            val isLowEndOptimization by viewModel.isLowEndOptimization.collectAsState()

            LaunchedEffect(appLanguage) {
                LocaleSyncHelper.applyAppLanguage(this@MainActivity, appLanguage)
            }

            LaunchedEffect(stats, appLanguage) {
                AppShortcutHelper.updateDynamicShortcuts(
                    context = this@MainActivity,
                    continueCount = stats.continueWatchingCount,
                    totalMediaCount = stats.totalItems,
                    favoritesCount = stats.favoritesCount
                )
            }

            val screenDensityInfo = rememberScreenDensityInfo(
                dpiScaleMode = dpiScaleMode,
                isOptimizationEnabled = isDpiOptimizationEnabled
            )

            LaunchedEffect(screenDensityInfo.densityDpi) {
                viewModel.setDetectedScreenDpi(screenDensityInfo.densityDpi)
            }

            LaunchedEffect(targetFrameRate) {
                applyDisplayRefreshRate(targetFrameRate)
            }

            val perfProfile = remember { DevicePerformanceHelper.getProfile(this@MainActivity) }

            CompositionLocalProvider(
                LocalScreenDensityInfo provides screenDensityInfo,
                LocalDevicePerformance provides perfProfile,
                LocalLowEndOptimization provides isLowEndOptimization
            ) {
                ProvideAppStrings(language = appLanguage) {
                    LootDropTheme(
                        themeMode = themeMode,
                        accentTheme = accentTheme,
                        darkBackgroundTheme = darkBgTheme,
                        lightBackgroundTheme = lightBgTheme,
                        customFontPath = customFontPath
                    ) {
                        HomeScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }

    private fun applyDisplayRefreshRate(targetRate: TargetFrameRate) {
        try {
            val currentDisplay = display
            val detectedHz = currentDisplay?.refreshRate?.toInt() ?: 60
            viewModel.setDetectedRefreshRate(detectedHz)

            val targetFps = when (targetRate) {
                TargetFrameRate.FPS_60 -> 60f
                TargetFrameRate.FPS_90 -> 90f
                TargetFrameRate.FPS_120 -> 120f
                TargetFrameRate.AUTO -> 0f
            }
            val params = window.attributes
            params.preferredRefreshRate = targetFps

            val modes = currentDisplay?.supportedModes ?: emptyArray()
            val matchedMode = when (targetRate) {
                TargetFrameRate.FPS_60 -> modes.find { it.refreshRate in 59.0f..61.0f }
                TargetFrameRate.FPS_90 -> modes.find { it.refreshRate in 89.0f..91.0f }
                    ?: modes.maxByOrNull { it.refreshRate }
                TargetFrameRate.FPS_120 -> modes.find { it.refreshRate in 119.0f..121.0f }
                    ?: modes.maxByOrNull { it.refreshRate }
                TargetFrameRate.AUTO -> modes.maxByOrNull { it.refreshRate }
            }

            if (matchedMode != null) {
                params.preferredDisplayModeId = matchedMode.modeId
            }
            window.attributes = params
        } catch (_: Exception) {
            // Safe fallback if display mode adjustment is not supported by device/emulator
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            AppShortcutHelper.ACTION_UP_NEXT -> {
                viewModel.selectTab(MainTab.UP_NEXT)
                AppShortcutHelper.reportShortcutUsed(this, AppShortcutHelper.ID_UP_NEXT)
            }
            AppShortcutHelper.ACTION_LIBRARY -> {
                viewModel.selectTab(MainTab.LIBRARY)
                AppShortcutHelper.reportShortcutUsed(this, AppShortcutHelper.ID_LIBRARY)
            }
            AppShortcutHelper.ACTION_FAVORITES -> {
                viewModel.selectTab(MainTab.COLLECTIONS)
                AppShortcutHelper.reportShortcutUsed(this, AppShortcutHelper.ID_FAVORITES)
            }
        }

        val targetTab = intent.getStringExtra(AppShortcutHelper.EXTRA_TARGET_TAB)
            ?: intent.getStringExtra("EXTRA_TARGET_TAB")
        when (targetTab) {
            "QUEUE", "UP_NEXT" -> {
                viewModel.selectTab(MainTab.UP_NEXT)
                AppShortcutHelper.reportShortcutUsed(this, AppShortcutHelper.ID_UP_NEXT)
            }
            "LIBRARY" -> {
                viewModel.selectTab(MainTab.LIBRARY)
                AppShortcutHelper.reportShortcutUsed(this, AppShortcutHelper.ID_LIBRARY)
            }
            "VAULT", "COLLECTIONS" -> {
                viewModel.selectTab(MainTab.COLLECTIONS)
                AppShortcutHelper.reportShortcutUsed(this, AppShortcutHelper.ID_FAVORITES)
            }
            "HOME" -> viewModel.selectTab(MainTab.HOME)
            "SETTINGS" -> viewModel.selectTab(MainTab.SETTINGS)
        }
    }
}
