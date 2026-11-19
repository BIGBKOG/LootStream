package com.example

import android.app.Application
import android.content.ComponentCallbacks2
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.data.local.AppDatabase
import com.example.data.remote.ApiClient
import com.example.i18n.LocaleSyncHelper
import com.example.preferences.AppPreferences
import com.example.shortcuts.AppShortcutHelper
import java.io.File

class LootDropApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        val preferences = AppPreferences(this)
        LocaleSyncHelper.applyAppLanguage(this, preferences.appLanguage)
        AppShortcutHelper.registerDynamicShortcuts(this)
    }

    override fun newImageLoader(): ImageLoader {
        val preferences = AppPreferences(this)
        val isDataSaver = preferences.isDataSaverMode
        val isLowEnd = preferences.isLowEndOptimization

        val memoryPercent = when {
            isLowEnd -> 0.14
            isDataSaver -> 0.18
            else -> 0.25
        }
        val diskCacheMb = when {
            isLowEnd -> 35L
            isDataSaver -> 50L
            else -> 150L
        }

        return ImageLoader.Builder(this)
            .okHttpClient { ApiClient.getOkHttpClient(this) }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(memoryPercent)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_image_cache"))
                    .maxSizeBytes(diskCacheMb * 1024L * 1024L)
                    .build()
            }
            .crossfade(!isLowEnd)
            .respectCacheHeaders(false)
            .build()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            runCatching {
                coil.Coil.imageLoader(this).memoryCache?.clear()
            }
        }
    }

    companion object {
        fun clearCoilImageCache(application: Application) {
            val imageLoader = coil.Coil.imageLoader(application)
            imageLoader.memoryCache?.clear()
            imageLoader.diskCache?.clear()
            runCatching {
                File(application.cacheDir, "local_thumbs").deleteRecursively()
            }
        }

        fun getDiskCacheSizeBytes(application: Application): Long {
            val imageCacheSize = runCatching {
                coil.Coil.imageLoader(application).diskCache?.size ?: 0L
            }.getOrDefault(0L)
            val thumbDirSize = runCatching {
                File(application.cacheDir, "local_thumbs").walkTopDown().filter { it.isFile }.sumOf { it.length() }
            }.getOrDefault(0L)
            return imageCacheSize + thumbDirSize
        }

        fun getHttpCacheSizeBytes(application: Application): Long {
            return ApiClient.getHttpCacheSizeBytes(application)
        }

        fun getDatabaseSizeBytes(application: Application): Long {
            val dbFile = application.getDatabasePath(AppDatabase.DB_NAME)
            val walFile = File(dbFile.path + "-wal")
            val shmFile = File(dbFile.path + "-shm")
            var total = if (dbFile.exists()) dbFile.length() else 0L
            if (walFile.exists()) total += walFile.length()
            if (shmFile.exists()) total += shmFile.length()
            return total
        }
    }
}
