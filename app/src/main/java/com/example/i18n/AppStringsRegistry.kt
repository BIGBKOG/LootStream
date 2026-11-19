package com.example.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalAppStrings = staticCompositionLocalOf<AppStrings> { StringsEn }
val LocalAppLanguage = staticCompositionLocalOf<AppLanguage> { AppLanguage.ENGLISH }

object AppStringsRegistry {
    fun get(language: AppLanguage): AppStrings {
        return when (language) {
            AppLanguage.ENGLISH -> StringsEn
            AppLanguage.SPANISH -> StringsEs
            AppLanguage.FRENCH -> StringsFr
        }
    }
}

@Composable
fun ProvideAppStrings(
    language: AppLanguage,
    content: @Composable () -> Unit
) {
    val strings = AppStringsRegistry.get(language)
    CompositionLocalProvider(
        LocalAppStrings provides strings,
        LocalAppLanguage provides language,
        content = content
    )
}
