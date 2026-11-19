package com.example.data.remote

import android.content.Context
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val HTTP_CACHE_SIZE = 15L * 1024L * 1024L // 15 MB HTTP cache

    @Volatile
    private var okHttpClientInstance: OkHttpClient? = null

    fun getOkHttpClient(context: Context): OkHttpClient {
        return okHttpClientInstance ?: synchronized(this) {
            okHttpClientInstance ?: buildOkHttpClient(context.applicationContext).also {
                okHttpClientInstance = it
            }
        }
    }

    private fun buildOkHttpClient(context: Context): OkHttpClient {
        val cacheDir = File(context.cacheDir, "http_cache")
        val cache = runCatching { Cache(cacheDir, HTTP_CACHE_SIZE) }.getOrNull()

        return OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .apply {
                if (cache != null) cache(cache)
            }
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "LootStream-Android/1.0")
                    .build()
                chain.proceed(request)
            }
            .let { HttpLoggingConfigurator.configure(it) }
            .build()
    }

    fun getHttpCacheSizeBytes(context: Context): Long {
        return runCatching {
            val cacheDir = File(context.cacheDir, "http_cache")
            if (cacheDir.exists()) {
                cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            } else 0L
        }.getOrDefault(0L)
    }

    fun clearHttpCache(context: Context): Long {
        var reclaimed = 0L
        runCatching {
            val cacheDir = File(context.cacheDir, "http_cache")
            if (cacheDir.exists()) {
                reclaimed = cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                okHttpClientInstance?.cache?.evictAll()
                cacheDir.deleteRecursively()
            }
        }
        return reclaimed
    }
}
