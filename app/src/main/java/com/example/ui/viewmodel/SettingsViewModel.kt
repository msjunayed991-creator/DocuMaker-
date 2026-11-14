package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.PdfRepository
import com.example.data.local.SettingsRepository
import com.example.data.local.UserPreferences
import com.example.domain.model.PdfPageSettings
import com.example.domain.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val librarySizeBytes: Long = 0L,
    val statusMessage: String? = null
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val pdfRepository: PdfRepository
) : ViewModel() {

    private val libraryBytes = MutableStateFlow(0L)
    private val statusMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.preferencesFlow,
        libraryBytes,
        statusMessage
    ) { prefs, bytes, msg ->
        SettingsUiState(
            preferences = prefs,
            librarySizeBytes = bytes,
            statusMessage = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    init {
        refreshStorageStats()
    }

    fun refreshStorageStats() {
        viewModelScope.launch {
            libraryBytes.value = pdfRepository.getTotalLibraryBytes()
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun updateDefaultPageSettings(settings: PdfPageSettings) {
        viewModelScope.launch {
            settingsRepository.updateDefaultPageSettings(settings)
            statusMessage.value = "Default PDF settings saved"
        }
    }

    fun clearTemporaryCache() {
        viewModelScope.launch {
            val freed = pdfRepository.clearCacheBytes()
            refreshStorageStats()
            statusMessage.value = if (freed > 0L) "Temporary cache cleared" else "Cache is already clean"
        }
    }

    fun clearMessage() {
        statusMessage.value = null
    }

    class Factory(
        private val settingsRepository: SettingsRepository,
        private val pdfRepository: PdfRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(settingsRepository, pdfRepository) as T
        }
    }
}
