package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.PdfDocumentEntity
import com.example.data.local.PdfRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val recentDocuments: List<PdfDocumentEntity> = emptyList(),
    val totalDocCount: Int = 0,
    val totalStorageBytes: Long = 0L,
    val statusMessage: String? = null
)

class HomeViewModel(
    private val repository: PdfRepository
) : ViewModel() {

    private val statusMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        repository.recentDocuments,
        repository.allDocuments,
        statusMessage
    ) { recent, all, msg ->
        HomeUiState(
            recentDocuments = recent,
            totalDocCount = all.size,
            totalStorageBytes = all.sumOf { it.fileSizeBytes },
            statusMessage = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun importExternalPdf(uri: Uri, onImported: (PdfDocumentEntity) -> Unit) {
        viewModelScope.launch {
            val doc = repository.importExternalPdf(uri)
            if (doc != null) {
                statusMessage.value = "Imported ${doc.fileName}"
                onImported(doc)
            } else {
                statusMessage.value = "Could not import selected PDF"
            }
        }
    }

    fun toggleFavorite(doc: PdfDocumentEntity) {
        viewModelScope.launch { repository.toggleFavorite(doc) }
    }

    fun clearMessage() {
        statusMessage.value = null
    }

    class Factory(private val repository: PdfRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(repository) as T
        }
    }
}
