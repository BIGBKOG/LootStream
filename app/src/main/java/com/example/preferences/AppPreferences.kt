package com.example.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import com.example.data.model.CollectionsFilter
import com.example.data.model.MainTab
import com.example.data.model.MediaSourceType
import com.example.data.model.PlayerResizeMode
import com.example.i18n.AppLanguage
import com.example.ui.theme.EpicGamesBlue
import com.example.ui.theme.GogPurple
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.SteamBlue
import com.example.util.DevicePerformanceHelper

enum class ThemeMode(val label: String) {
    DARK("Dark Mode"),
    LIGHT("Light Mode"),
    SYSTEM("System Default")
}

enum class CardDensity(val label: String, val description: String) {
    RICH_CARDS("Rich Cards", "Large poster art with full metadata"),
    COMPACT_CARDS("Compact Cards", "Streamlined horizontal row cards"),
    MINIMAL_LIST("Minimal List", "Compact text-first list view")
}

enum class TargetFrameRate(val label: String, val fps: Int, val description: String) {
    AUTO("Adaptive (60/90/120)", 0, "Hardware adaptive sync up to 120Hz"),
    FPS_60("60 FPS", 60, "Standard 60Hz display refresh rate"),
    FPS_90("90 FPS", 90, "Smooth 90Hz enhanced motion"),
    FPS_120("120 FPS", 120, "Extreme 120Hz maximum fluidity")
}

enum class DpiScaleMode(val label: String, val shortName: String, val description: String) {
    AUTO_ADAPTIVE("Auto (DPI-Aware)", "Auto", "Automatically tunes layout, spacing & targets to screen DPI"),
    COMPACT("Compact (High-Density)", "Compact", "Condensed spacing to display maximum media on screen"),
    BALANCED("Balanced Standard", "Balanced", "Standard Material 3 spacing and dimensions"),
    SPACIOUS("Spacious Comfort", "Spacious", "Larger touch targets and generous padding")
}

enum class AccentCategory(val label: String) {
    ALL("All (23)"),
    CYBER("Cyber (5)"),
    VAULT("Vault (5)"),
    STUDIO("Studio (6)"),
    NEON("Neon Pro (7)")
}

enum class AccentTheme(
    val title: String,
    val darkPrimary: Color,
    val darkSecondary: Color,
    val lightPrimary: Color,
    val lightSecondary: Color,
    val description: String,
    val shortName: String,
    val category: AccentCategory
) {
    CYBER_CYAN(
        "Cyber Cyan",
        NeonCyan,
        NeonGreen,
        Color(0xFF0284C7),
        Color(0xFF0D9488),
        "Vibrant high-contrast cyber blue",
        "Cyan",
        AccentCategory.CYBER
    ),
    STEAM_BLUE(
        "Stream Sky",
        SteamBlue,
        Color(0xFF38BDF8),
        Color(0xFF0369A1),
        Color(0xFF0284C7),
        "Crisp theater sky blue",
        "Sky",
        AccentCategory.STUDIO
    ),
    GOG_PURPLE(
        "Ultraviolet",
        GogPurple,
        Color(0xFFD946EF),
        Color(0xFF7E22CE),
        Color(0xFFA855F7),
        "Deep electric violet & magenta",
        "Ultra Violet",
        AccentCategory.CYBER
    ),
    RAZER_LIME(
        "Toxic Lime",
        Color(0xFF22C55E),
        Color(0xFF10B981),
        Color(0xFF15803D),
        Color(0xFF16A34A),
        "High-energy matrix green",
        "Toxic Lime",
        AccentCategory.CYBER
    ),
    INFERNO_RED(
        "Inferno Red",
        Color(0xFFEF4444),
        Color(0xFFF97316),
        Color(0xFFB91C1C),
        Color(0xFFDC2626),
        "Warm cinema red & ember",
        "Inferno",
        AccentCategory.CYBER
    ),
    GOLDEN_LOOT(
        "Stream Gold",
        Color(0xFFFBBF24),
        Color(0xFFF59E0B),
        Color(0xFFB45309),
        Color(0xFFD97706),
        "Warm marquee amber gold",
        "Gold",
        AccentCategory.VAULT
    ),
    EPIC_INDIGO(
        "Cinema Indigo",
        EpicGamesBlue,
        Color(0xFF8B5CF6),
        Color(0xFF4338CA),
        Color(0xFF6366F1),
        "Deep projection indigo",
        "Indigo",
        AccentCategory.STUDIO
    ),
    CHERRY_ROSE(
        "Cherry Blossom",
        Color(0xFFEC4899),
        Color(0xFFF43F5E),
        Color(0xFFBE185D),
        Color(0xFFE11D48),
        "Vivid neon pink & sakura",
        "Sakura",
        AccentCategory.CYBER
    ),
    OCEAN_TEAL(
        "Ocean Teal",
        Color(0xFF14B8A6),
        Color(0xFF06B6D4),
        Color(0xFF0F766E),
        Color(0xFF0D9488),
        "Deep oceanic crystal teal",
        "Teal",
        AccentCategory.VAULT
    ),
    SUNSET_CORAL(
        "Sunset Coral",
        Color(0xFFFB923C),
        Color(0xFFF43F5E),
        Color(0xFFC2410C),
        Color(0xFFEA580C),
        "Warm twilight sunset blaze",
        "Sunset",
        AccentCategory.VAULT
    ),
    DEEP_ROYALE(
        "Royal Violet",
        Color(0xFF8B5CF6),
        Color(0xFFC084FC),
        Color(0xFF6D28D9),
        Color(0xFF7C3AED),
        "Majestic royal imperial purple",
        "Royale",
        AccentCategory.VAULT
    ),
    EMERALD_MINT(
        "Emerald Vault",
        Color(0xFF10B981),
        Color(0xFF34D399),
        Color(0xFF047857),
        Color(0xFF059669),
        "Lush emerald gemstone green",
        "Emerald",
        AccentCategory.VAULT
    ),
    PLAYSTATION_BLUE(
        "Cobalt Wave",
        Color(0xFF0070D1),
        Color(0xFF38BDF8),
        Color(0xFF003791),
        Color(0xFF0070D1),
        "Electric cobalt studio blue",
        "Cobalt",
        AccentCategory.STUDIO
    ),
    XBOX_GREEN(
        "Matrix Emerald",
        Color(0xFF107C10),
        Color(0xFF22C55E),
        Color(0xFF0E5A0E),
        Color(0xFF107C10),
        "Crisp emerald pulse green",
        "Emerald",
        AccentCategory.STUDIO
    ),
    NINTENDO_RED(
        "Crimson Laser",
        Color(0xFFE60012),
        Color(0xFFFF4D5A),
        Color(0xFF99000C),
        Color(0xFFE60012),
        "Iconic ruby cinema crimson",
        "Ruby",
        AccentCategory.STUDIO
    ),
    DISCORD_BLURPLE(
        "Cyber Blurple",
        Color(0xFF5865F2),
        Color(0xFFAA5CC3),
        Color(0xFF4752C4),
        Color(0xFF7E22CE),
        "Self-hosted media violet-blue",
        "Blurple",
        AccentCategory.STUDIO
    ),
    BACKLOG_SLAYER(
        "Crimson Flare",
        Color(0xFFFF0055),
        Color(0xFFFF5500),
        Color(0xFFDC2626),
        Color(0xFFB91C1C),
        "Radiant neon crimson & blaze",
        "Flare",
        AccentCategory.NEON
    ),
    VAULT_CLEANER(
        "24K Cyber Gold",
        Color(0xFFFFD700),
        Color(0xFF00FFCC),
        Color(0xFFB45309),
        Color(0xFF0F766E),
        "Pure 24K vault gold & cyber teal",
        "Vault Gold",
        AccentCategory.NEON
    ),
    NEBULA_BLACKOUT(
        "Nebula Blackout",
        Color(0xFFB026FF),
        Color(0xFF00F0FF),
        Color(0xFF7E22CE),
        Color(0xFF0284C7),
        "Cosmic nebula violet & electric cyan",
        "Nebula",
        AccentCategory.NEON
    ),
    PRISM_DRIFT(
        "Prism Drift",
        Color(0xFFFF2E93),
        Color(0xFF00E5FF),
        Color(0xFFDB2777),
        Color(0xFF0284C7),
        "Razor-sharp magenta & glacier cyan",
        "Prism",
        AccentCategory.NEON
    ),
    SOLAR_NOVA(
        "Solar Nova",
        Color(0xFFFF6B00),
        Color(0xFFFFD166),
        Color(0xFFC2410C),
        Color(0xFFD97706),
        "Supernova orange & molten gold",
        "Nova",
        AccentCategory.NEON
    ),
    TOXIC_OVERDRIVE(
        "Volt Overdrive",
        Color(0xFFA3E635),
        Color(0xFFC026D3),
        Color(0xFF4D7C0F),
        Color(0xFF9333EA),
        "Acid volt lime & plasma violet",
        "Overdrive",
        AccentCategory.NEON
    ),
    AURORA_SOVEREIGN(
        "Aurora Mint",
        Color(0xFF00FFA3),
        Color(0xFF3B82F6),
        Color(0xFF047857),
        Color(0xFF1D4ED8),
        "Borealis emerald & deep sapphire",
        "Aurora",
        AccentCategory.NEON
    );

    fun primary(isDark: Boolean): Color = if (isDark) darkPrimary else lightPrimary
    fun secondary(isDark: Boolean): Color = if (isDark) darkSecondary else lightSecondary
}

enum class BackgroundTheme(
    val title: String,
    val isDark: Boolean,
    val backgroundColor: Color,
    val surfaceColor: Color,
    val surfaceVariantColor: Color,
    val borderColor: Color,
    val textPrimaryColor: Color,
    val textSecondaryColor: Color
) {
    CYBER_SLATE(
        "Cyber Slate",
        true,
        Color(0xFF0B0F19),
        Color(0xFF131926),
        Color(0xFF1C2538),
        Color(0xFF26334D),
        Color(0xFFF1F5F9),
        Color(0xFF94A3B8)
    ),
    OLED_BLACK(
        "OLED Midnight",
        true,
        Color(0xFF000000),
        Color(0xFF0D0D11),
        Color(0xFF16161D),
        Color(0xFF24242F),
        Color(0xFFF8FAFC),
        Color(0xFF94A3B8)
    ),
    MIDNIGHT_NAVY(
        "Midnight Navy",
        true,
        Color(0xFF070D1E),
        Color(0xFF0F1A38),
        Color(0xFF17264F),
        Color(0xFF233668),
        Color(0xFFF1F5F9),
        Color(0xFF94A3B8)
    ),
    CLEAN_SNOW(
        "Clean Snow",
        false,
        Color(0xFFF8FAFC),
        Color(0xFFFFFFFF),
        Color(0xFFF1F5F9),
        Color(0xFFE2E8F0),
        Color(0xFF0F172A),
        Color(0xFF475569)
    ),
    COOL_FROST(
        "Cool Frost",
        false,
        Color(0xFFF0F4F8),
        Color(0xFFFFFFFF),
        Color(0xFFE2E8F0),
        Color(0xFFCBD5E1),
        Color(0xFF0F172A),
        Color(0xFF475569)
    ),
    WARM_SAND(
        "Warm Sand",
        false,
        Color(0xFFFAF7F2),
        Color(0xFFFFFFFF),
        Color(0xFFF3EDE2),
        Color(0xFFE5DCCF),
        Color(0xFF1C1917),
        Color(0xFF57534E)
    )
}

class AppPreferences(context: Context) {
    val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences =
        context.getSharedPreferences("lootstream_app_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_USER_PROFILE_NAME = "key_user_profile_name"
        private const val KEY_USER_PROFILE_AVATAR_PATH = "key_user_profile_avatar_path"
        private const val KEY_LOW_END_OPTIMIZATION = "key_low_end_optimization"
        private const val KEY_THEME_MODE = "key_theme_mode"
        private const val KEY_ACCENT_THEME = "key_accent_theme"
        private const val KEY_BG_THEME_DARK = "key_bg_theme_dark"
        private const val KEY_BG_THEME_LIGHT = "key_bg_theme_light"
        private const val KEY_CARD_DENSITY = "key_card_density"
        private const val KEY_DEFAULT_TAB = "key_default_tab"
        private const val KEY_DEFAULT_SOURCE_FILTER = "key_default_source_filter"
        private const val KEY_DEFAULT_PLAYBACK_SPEED = "key_default_playback_speed"
        private const val KEY_AUTO_PLAY_NEXT = "key_auto_play_next"
        private const val KEY_AUTO_SYNC_SERVERS_ON_OPEN = "key_auto_sync_servers_on_open"
        private const val KEY_SHOW_DURATION_BADGE = "key_show_duration_badge"
        private const val KEY_SHOW_RESUME_BAR = "key_show_resume_bar"
        private const val KEY_PLAYER_RESIZE_MODE = "key_player_resize_mode"
        private const val KEY_COLLECTIONS_FILTER = "key_collections_filter"
        private const val KEY_HAS_SEEN_ONBOARDING = "key_has_seen_onboarding"
        private const val KEY_UI_ANIMATIONS_ENABLED = "key_ui_animations_enabled"
        private const val KEY_TARGET_FRAME_RATE = "key_target_frame_rate"
        private const val KEY_DPI_OPTIMIZATION_ENABLED = "key_dpi_optimization_enabled"
        private const val KEY_DPI_SCALE_MODE = "key_dpi_scale_mode"
        private const val KEY_DETECTED_SCREEN_DPI = "key_detected_screen_dpi"
        private const val KEY_DATA_SAVER_MODE = "key_data_saver_mode"
        private const val KEY_CUSTOM_FONT_PATH = "key_custom_font_path"
        private const val KEY_CUSTOM_FONT_NAME = "key_custom_font_name"
        private const val KEY_APP_LANGUAGE = "key_app_language"
        private const val KEY_HAS_SELECTED_INITIAL_LANGUAGE = "key_has_selected_initial_language"
        private const val KEY_LAST_BACKUP_TIMESTAMP = "key_last_backup_timestamp"
        private const val KEY_LAST_BACKUP_STATUS = "key_last_backup_status"
    }

    var themeMode: ThemeMode
        get() {
            val saved = prefs.getString(KEY_THEME_MODE, ThemeMode.DARK.name)
            return runCatching { ThemeMode.valueOf(saved ?: ThemeMode.DARK.name) }.getOrDefault(ThemeMode.DARK)
        }
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value.name).apply()

    var accentTheme: AccentTheme
        get() {
            val saved = prefs.getString(KEY_ACCENT_THEME, AccentTheme.CYBER_CYAN.name)
            return runCatching { AccentTheme.valueOf(saved ?: AccentTheme.CYBER_CYAN.name) }.getOrDefault(AccentTheme.CYBER_CYAN)
        }
        set(value) = prefs.edit().putString(KEY_ACCENT_THEME, value.name).apply()

    fun isAccentThemeUnlocked(@Suppress("UNUSED_PARAMETER") theme: AccentTheme): Boolean = true

    var darkBackgroundTheme: BackgroundTheme
        get() {
            val saved = prefs.getString(KEY_BG_THEME_DARK, BackgroundTheme.CYBER_SLATE.name)
            return runCatching { BackgroundTheme.valueOf(saved ?: BackgroundTheme.CYBER_SLATE.name) }.getOrDefault(BackgroundTheme.CYBER_SLATE)
        }
        set(value) = prefs.edit().putString(KEY_BG_THEME_DARK, value.name).apply()

    var lightBackgroundTheme: BackgroundTheme
        get() {
            val saved = prefs.getString(KEY_BG_THEME_LIGHT, BackgroundTheme.CLEAN_SNOW.name)
            return runCatching { BackgroundTheme.valueOf(saved ?: BackgroundTheme.CLEAN_SNOW.name) }.getOrDefault(BackgroundTheme.CLEAN_SNOW)
        }
        set(value) = prefs.edit().putString(KEY_BG_THEME_LIGHT, value.name).apply()

    var customFontPath: String
        get() = prefs.getString(KEY_CUSTOM_FONT_PATH, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_FONT_PATH, value).apply()

    var customFontName: String
        get() = prefs.getString(KEY_CUSTOM_FONT_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_FONT_NAME, value).apply()

    var cardDensity: CardDensity
        get() {
            val saved = prefs.getString(KEY_CARD_DENSITY, CardDensity.RICH_CARDS.name)
            return runCatching { CardDensity.valueOf(saved ?: CardDensity.RICH_CARDS.name) }.getOrDefault(CardDensity.RICH_CARDS)
        }
        set(value) = prefs.edit().putString(KEY_CARD_DENSITY, value.name).apply()

    var defaultTab: MainTab
        get() {
            val saved = prefs.getString(KEY_DEFAULT_TAB, MainTab.HOME.name)
            return runCatching { MainTab.valueOf(saved ?: MainTab.HOME.name) }.getOrDefault(MainTab.HOME)
        }
        set(value) = prefs.edit().putString(KEY_DEFAULT_TAB, value.name).apply()

    var defaultSourceFilter: MediaSourceType
        get() {
            val saved = prefs.getString(KEY_DEFAULT_SOURCE_FILTER, MediaSourceType.ALL.name)
            return runCatching { MediaSourceType.valueOf(saved ?: MediaSourceType.ALL.name) }.getOrDefault(MediaSourceType.ALL)
        }
        set(value) = prefs.edit().putString(KEY_DEFAULT_SOURCE_FILTER, value.name).apply()

    var defaultPlaybackSpeed: Float
        get() = prefs.getFloat(KEY_DEFAULT_PLAYBACK_SPEED, 1.0f).coerceIn(0.5f, 2.5f)
        set(value) = prefs.edit().putFloat(KEY_DEFAULT_PLAYBACK_SPEED, value.coerceIn(0.5f, 2.5f)).apply()

    var autoPlayNext: Boolean
        get() = prefs.getBoolean(KEY_AUTO_PLAY_NEXT, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_PLAY_NEXT, value).apply()

    var autoSyncServersOnOpen: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SYNC_SERVERS_ON_OPEN, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SYNC_SERVERS_ON_OPEN, value).apply()

    var showDurationBadge: Boolean
        get() = prefs.getBoolean(KEY_SHOW_DURATION_BADGE, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_DURATION_BADGE, value).apply()

    var showResumeBar: Boolean
        get() = prefs.getBoolean(KEY_SHOW_RESUME_BAR, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_RESUME_BAR, value).apply()

    var playerResizeMode: PlayerResizeMode
        get() {
            val saved = prefs.getString(KEY_PLAYER_RESIZE_MODE, PlayerResizeMode.FIT.name)
            return runCatching { PlayerResizeMode.valueOf(saved ?: PlayerResizeMode.FIT.name) }.getOrDefault(PlayerResizeMode.FIT)
        }
        set(value) = prefs.edit().putString(KEY_PLAYER_RESIZE_MODE, value.name).apply()

    var collectionsFilter: CollectionsFilter
        get() {
            val saved = prefs.getString(KEY_COLLECTIONS_FILTER, CollectionsFilter.ALL.name)
            return runCatching { CollectionsFilter.valueOf(saved ?: CollectionsFilter.ALL.name) }.getOrDefault(CollectionsFilter.ALL)
        }
        set(value) = prefs.edit().putString(KEY_COLLECTIONS_FILTER, value.name).apply()

    var hasSeenOnboarding: Boolean
        get() = prefs.getBoolean(KEY_HAS_SEEN_ONBOARDING, false)
        set(value) = prefs.edit().putBoolean(KEY_HAS_SEEN_ONBOARDING, value).apply()

    var isAnimationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_UI_ANIMATIONS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_UI_ANIMATIONS_ENABLED, value).apply()

    var isLowEndOptimization: Boolean
        get() = prefs.getBoolean(KEY_LOW_END_OPTIMIZATION, DevicePerformanceHelper.isLowEndDevice(appContext))
        set(value) = prefs.edit().putBoolean(KEY_LOW_END_OPTIMIZATION, value).apply()

    var targetFrameRate: TargetFrameRate
        get() {
            val saved = prefs.getString(KEY_TARGET_FRAME_RATE, TargetFrameRate.AUTO.name)
            return runCatching { TargetFrameRate.valueOf(saved ?: TargetFrameRate.AUTO.name) }.getOrDefault(TargetFrameRate.AUTO)
        }
        set(value) = prefs.edit().putString(KEY_TARGET_FRAME_RATE, value.name).apply()

    var isDpiOptimizationEnabled: Boolean
        get() = prefs.getBoolean(KEY_DPI_OPTIMIZATION_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_DPI_OPTIMIZATION_ENABLED, value).apply()

    var dpiScaleMode: DpiScaleMode
        get() {
            val saved = prefs.getString(KEY_DPI_SCALE_MODE, DpiScaleMode.AUTO_ADAPTIVE.name)
            return runCatching { DpiScaleMode.valueOf(saved ?: DpiScaleMode.AUTO_ADAPTIVE.name) }.getOrDefault(DpiScaleMode.AUTO_ADAPTIVE)
        }
        set(value) = prefs.edit().putString(KEY_DPI_SCALE_MODE, value.name).apply()

    var detectedScreenDpi: Int
        get() = prefs.getInt(KEY_DETECTED_SCREEN_DPI, 420)
        set(value) = prefs.edit().putInt(KEY_DETECTED_SCREEN_DPI, value).apply()

    var isDataSaverMode: Boolean
        get() = prefs.getBoolean(KEY_DATA_SAVER_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_DATA_SAVER_MODE, value).apply()

    var userProfileName: String
        get() = prefs.getString(KEY_USER_PROFILE_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_PROFILE_NAME, value).apply()

    var userProfileAvatarPath: String
        get() = prefs.getString(KEY_USER_PROFILE_AVATAR_PATH, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_PROFILE_AVATAR_PATH, value).apply()

    var appLanguage: AppLanguage
        get() = AppLanguage.fromCode(prefs.getString(KEY_APP_LANGUAGE, AppLanguage.ENGLISH.code))
        set(value) = prefs.edit().putString(KEY_APP_LANGUAGE, value.code).apply()

    var hasSelectedInitialLanguage: Boolean
        get() = prefs.getBoolean(KEY_HAS_SELECTED_INITIAL_LANGUAGE, false)
        set(value) = prefs.edit().putBoolean(KEY_HAS_SELECTED_INITIAL_LANGUAGE, value).apply()

    var lastBackupTimestamp: Long
        get() = prefs.getLong(KEY_LAST_BACKUP_TIMESTAMP, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_BACKUP_TIMESTAMP, value).apply()

    var lastBackupStatus: String
        get() = prefs.getString(KEY_LAST_BACKUP_STATUS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_BACKUP_STATUS, value).apply()
}
