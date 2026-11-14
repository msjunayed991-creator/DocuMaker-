package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.ImageFitMode
import com.example.domain.model.PageMargin
import com.example.domain.model.PageOrientation
import com.example.domain.model.PageSizePreset
import com.example.domain.model.PdfPageSettings
import com.example.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "documaker_settings")

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultPageSettings: PdfPageSettings = PdfPageSettings()
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PAGE_SIZE = stringPreferencesKey("default_page_size")
        val ORIENTATION = stringPreferencesKey("default_orientation")
        val MARGIN = stringPreferencesKey("default_margin")
        val IMAGE_FIT = stringPreferencesKey("default_image_fit")
        val MAINTAIN_ASPECT = booleanPreferencesKey("maintain_aspect_ratio")
        val WHITE_BG = booleanPreferencesKey("white_background")
        val JPEG_QUALITY = intPreferencesKey("jpeg_quality")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val theme = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM
        val size = prefs[Keys.PAGE_SIZE]?.let { runCatching { PageSizePreset.valueOf(it) }.getOrNull() }
            ?: PageSizePreset.A4
        val orientation = prefs[Keys.ORIENTATION]?.let { runCatching { PageOrientation.valueOf(it) }.getOrNull() }
            ?: PageOrientation.PORTRAIT
        val margin = prefs[Keys.MARGIN]?.let { runCatching { PageMargin.valueOf(it) }.getOrNull() }
            ?: PageMargin.NORMAL
        val fit = prefs[Keys.IMAGE_FIT]?.let { runCatching { ImageFitMode.valueOf(it) }.getOrNull() }
            ?: ImageFitMode.FIT
        val maintainAspect = prefs[Keys.MAINTAIN_ASPECT] ?: true
        val whiteBg = prefs[Keys.WHITE_BG] ?: true
        val quality = prefs[Keys.JPEG_QUALITY] ?: 88

        UserPreferences(
            themeMode = theme,
            defaultPageSettings = PdfPageSettings(
                sizePreset = size,
                orientation = orientation,
                margin = margin,
                imageFit = fit,
                maintainAspectRatio = maintainAspect,
                whiteBackground = whiteBg,
                jpegQuality = quality
            )
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun updateDefaultPageSettings(settings: PdfPageSettings) {
        context.dataStore.edit {
            it[Keys.PAGE_SIZE] = settings.sizePreset.name
            it[Keys.ORIENTATION] = settings.orientation.name
            it[Keys.MARGIN] = settings.margin.name
            it[Keys.IMAGE_FIT] = settings.imageFit.name
            it[Keys.MAINTAIN_ASPECT] = settings.maintainAspectRatio
            it[Keys.WHITE_BG] = settings.whiteBackground
            it[Keys.JPEG_QUALITY] = settings.jpegQuality
        }
    }
}
