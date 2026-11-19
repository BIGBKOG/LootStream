package com.example.i18n

import com.example.data.model.CollectionsFilter
import com.example.data.model.MainTab
import com.example.data.model.MediaSortOption
import com.example.data.model.MediaSourceType
import com.example.data.model.MediaType
import com.example.data.model.PlayerResizeMode
import com.example.data.model.WatchStatus
import com.example.preferences.CardDensity
import com.example.preferences.DpiScaleMode
import com.example.preferences.TargetFrameRate
import com.example.preferences.ThemeMode

fun MainTab.localizedLabel(language: AppLanguage): String = when (language) {
    AppLanguage.ENGLISH -> label
    AppLanguage.SPANISH -> when (this) {
        MainTab.HOME -> "Inicio"
        MainTab.LIBRARY -> "Biblioteca"
        MainTab.UP_NEXT -> "Cola"
        MainTab.COLLECTIONS -> "Bóveda"
        MainTab.SETTINGS -> "Ajustes"
    }
    AppLanguage.FRENCH -> when (this) {
        MainTab.HOME -> "Accueil"
        MainTab.LIBRARY -> "Médiathèque"
        MainTab.UP_NEXT -> "À Suivre"
        MainTab.COLLECTIONS -> "Coffre"
        MainTab.SETTINGS -> "Paramètres"
    }
}

fun MediaSourceType.localizedLabel(language: AppLanguage): String = when (language) {
    AppLanguage.ENGLISH -> label
    AppLanguage.SPANISH -> when (this) {
        MediaSourceType.ALL -> "Todas"
        MediaSourceType.PC_HOST -> "Host PC"
        MediaSourceType.LOCAL -> "Dispositivo"
        MediaSourceType.STREAM_URL -> "URL Stream"
    }
    AppLanguage.FRENCH -> when (this) {
        MediaSourceType.ALL -> "Toutes"
        MediaSourceType.PC_HOST -> "Hôte PC"
        MediaSourceType.LOCAL -> "Appareil"
        MediaSourceType.STREAM_URL -> "URL Flux"
    }
}

fun MediaType.localizedLabel(language: AppLanguage): String = when (language) {
    AppLanguage.ENGLISH -> label
    AppLanguage.SPANISH -> when (this) {
        MediaType.ALL -> "Todo"
        MediaType.MOVIE -> "Películas"
        MediaType.SERIES -> "Series"
        MediaType.EPISODE -> "Episodios"
        MediaType.VIDEO -> "Videos"
        MediaType.AUDIO -> "Audio"
        MediaType.LIVE_STREAM -> "Streams"
    }
    AppLanguage.FRENCH -> when (this) {
        MediaType.ALL -> "Tout"
        MediaType.MOVIE -> "Films"
        MediaType.SERIES -> "Séries"
        MediaType.EPISODE -> "Épisodes"
        MediaType.VIDEO -> "Vidéos"
        MediaType.AUDIO -> "Audio"
        MediaType.LIVE_STREAM -> "Flux"
    }
}

fun WatchStatus.localizedLabel(language: AppLanguage): String = when (language) {
    AppLanguage.ENGLISH -> label
    AppLanguage.SPANISH -> when (this) {
        WatchStatus.UNWATCHED -> "Sin ver"
        WatchStatus.IN_PROGRESS -> "En progreso"
        WatchStatus.WATCHED -> "Visto"
        WatchStatus.ON_HOLD -> "Pausado"
    }
    AppLanguage.FRENCH -> when (this) {
        WatchStatus.UNWATCHED -> "Non vu"
        WatchStatus.IN_PROGRESS -> "En cours"
        WatchStatus.WATCHED -> "Vu"
        WatchStatus.ON_HOLD -> "En pause"
    }
}

fun MediaSortOption.localizedLabel(language: AppLanguage): String = when (language) {
    AppLanguage.ENGLISH -> label
    AppLanguage.SPANISH -> when (this) {
        MediaSortOption.RECENTLY_PLAYED -> "Reproducido Reciente"
        MediaSortOption.NEWEST_ADDED -> "Añadido Reciente"
        MediaSortOption.TITLE_ASC -> "Título (A-Z)"
        MediaSortOption.DURATION_DESC -> "Mayor Duración"
        MediaSortOption.RATING_DESC -> "Mejor Valorado"
    }
    AppLanguage.FRENCH -> when (this) {
        MediaSortOption.RECENTLY_PLAYED -> "Lu Récemment"
        MediaSortOption.NEWEST_ADDED -> "Ajout Récent"
        MediaSortOption.TITLE_ASC -> "Titre (A-Z)"
        MediaSortOption.DURATION_DESC -> "Plus Long"
        MediaSortOption.RATING_DESC -> "Mieux Noté"
    }
}

fun CollectionsFilter.localizedLabel(language: AppLanguage): String = when (language) {
    AppLanguage.ENGLISH -> label
    AppLanguage.SPANISH -> when (this) {
        CollectionsFilter.ALL -> "Todo"
        CollectionsFilter.FAVORITES -> "Favoritos"
        CollectionsFilter.IN_PROGRESS -> "Continuar"
        CollectionsFilter.WATCHED -> "Vistos"
        CollectionsFilter.HOST_ONLY -> "Host PC"
        CollectionsFilter.LOCAL_ONLY -> "Local"
    }
    AppLanguage.FRENCH -> when (this) {
        CollectionsFilter.ALL -> "Tout"
        CollectionsFilter.FAVORITES -> "Favoris"
        CollectionsFilter.IN_PROGRESS -> "En Cours"
        CollectionsFilter.WATCHED -> "Vus"
        CollectionsFilter.HOST_ONLY -> "Hôte PC"
        CollectionsFilter.LOCAL_ONLY -> "Local"
    }
}

fun PlayerResizeMode.localizedLabel(language: AppLanguage): String = when (language) {
    AppLanguage.ENGLISH -> label
    AppLanguage.SPANISH -> when (this) {
        PlayerResizeMode.FIT -> "Ajustar"
        PlayerResizeMode.FILL -> "Rellenar"
        PlayerResizeMode.ZOOM -> "Zoom"
    }
    AppLanguage.FRENCH -> when (this) {
        PlayerResizeMode.FIT -> "Ajuster"
        PlayerResizeMode.FILL -> "Remplir"
        PlayerResizeMode.ZOOM -> "Zoom"
    }
}

fun ThemeMode.localizedLabel(language: AppLanguage): String = when (language) {
    AppLanguage.ENGLISH -> label
    AppLanguage.SPANISH -> when (this) {
        ThemeMode.DARK -> "Modo Oscuro"
        ThemeMode.LIGHT -> "Modo Claro"
        ThemeMode.SYSTEM -> "Sistema"
    }
    AppLanguage.FRENCH -> when (this) {
        ThemeMode.DARK -> "Mode Sombre"
        ThemeMode.LIGHT -> "Mode Clair"
        ThemeMode.SYSTEM -> "Système"
    }
}

fun CardDensity.localizedLabel(language: AppLanguage): String = when (language) {
    AppLanguage.ENGLISH -> label
    AppLanguage.SPANISH -> when (this) {
        CardDensity.RICH_CARDS -> "Tarjetas Completas"
        CardDensity.COMPACT_CARDS -> "Tarjetas Compactas"
        CardDensity.MINIMAL_LIST -> "Lista Mínima"
    }
    AppLanguage.FRENCH -> when (this) {
        CardDensity.RICH_CARDS -> "Cartes Riches"
        CardDensity.COMPACT_CARDS -> "Cartes Compactes"
        CardDensity.MINIMAL_LIST -> "Liste Minimale"
    }
}

fun TargetFrameRate.localizedLabel(language: AppLanguage): String = when (language) {
    AppLanguage.ENGLISH -> label
    AppLanguage.SPANISH -> when (this) {
        TargetFrameRate.AUTO -> "Adaptativo (60/90/120)"
        else -> label
    }
    AppLanguage.FRENCH -> when (this) {
        TargetFrameRate.AUTO -> "Adaptatif (60/90/120)"
        else -> label
    }
}

fun DpiScaleMode.localizedLabel(language: AppLanguage): String = when (language) {
    AppLanguage.ENGLISH -> label
    AppLanguage.SPANISH -> when (this) {
        DpiScaleMode.AUTO_ADAPTIVE -> "Auto (Según DPI)"
        DpiScaleMode.COMPACT -> "Compacto (Alta Densidad)"
        DpiScaleMode.BALANCED -> "Equilibrado Estándar"
        DpiScaleMode.SPACIOUS -> "Espacioso Confort"
    }
    AppLanguage.FRENCH -> when (this) {
        DpiScaleMode.AUTO_ADAPTIVE -> "Auto (Selon DPI)"
        DpiScaleMode.COMPACT -> "Compact (Haute Densité)"
        DpiScaleMode.BALANCED -> "Équilibré Standard"
        DpiScaleMode.SPACIOUS -> "Spacieux Confort"
    }
}
