package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.PdfDocumentEntity
import com.example.data.local.PdfRepository
import com.example.domain.model.FileSortOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class FilesTab(val label: String) {
    ALL("All Files"),
    RECENT("Recent"),
    FAVORITES("Favorites")
}

data class FilesUiState(
    val selectedTab: FilesTab = FilesTab.ALL,
    val searchQuery: String = "",
    val sortOption: FileSortOption = FileSortOption.DATE_DESC,
    val documents: List<PdfDocumentEntity> = emptyList(),
    val totalCount: Int = 0,
    val feedbackMessage: String? = null
)

class FilesViewModel(
    private val repository: PdfRepository
) : ViewModel() {

    private val selectedTab = MutableStateFlow(FilesTab.ALL)
    private val searchQuery = MutableStateFlow("")
    private val sortOption = MutableStateFlow(FileSortOption.DATE_DESC)
    private val feedbackMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<FilesUiState> = combine(
        repository.allDocuments,
        selectedTab,
        searchQuery,
        sortOption,
        feedbackMessage
    ) { allDocs, tab, query, sort, msg ->
        val filteredByTab = when (tab) {
            FilesTab.ALL -> allDocs
            FilesTab.RECENT -> allDocs.sortedByDescending { it.lastOpenedAt }.take(15)
            FilesTab.FAVORITES -> allDocs.filter { it.isFavorite }
        }
        val filteredByQuery = if (query.isBlank()) {
            filteredByTab
        } else {
            val q = query.trim().lowercase()
            filteredByTab.filter { it.fileName.lowercase().contains(q) }
        }
        val sorted = when (sort) {
            FileSortOption.DATE_DESC -> filteredByQuery.sortedByDescending { it.createdAt }
            FileSortOption.DATE_ASC -> filteredByQuery.sortedBy { it.createdAt }
            FileSortOption.NAME_ASC -> filteredByQuery.sortedBy { it.fileName.lowercase() }
            FileSortOption.NAME_DESC -> filteredByQuery.sortedByDescending { it.fileName.lowercase() }
            FileSortOption.SIZE_DESC -> filteredByQuery.sortedByDescending { it.fileSizeBytes }
            FileSortOption.SIZE_ASC -> filteredByQuery.sortedBy { it.fileSizeBytes }
        }
        FilesUiState(
            selectedTab = tab,
            searchQuery = query,
            sortOption = sort,
            documents = sorted,
            totalCount = allDocs.size,
            feedbackMessage = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FilesUiState()
    )

    fun selectTab(tab: FilesTab) {
        selectedTab.value = tab
    }

    fun updateSearch(query: String) {
        searchQuery.value = query
    }

    fun updateSort(option: FileSortOption) {
        sortOption.value = option
    }

    fun toggleFavorite(doc: PdfDocumentEntity) {
        viewModelScope.launch { repository.toggleFavorite(doc) }
    }

    fun renameDocument(doc: PdfDocumentEntity, newName: String) {
        viewModelScope.launch {
            val updated = repository.renameDocument(doc, newName)
            feedbackMessage.value = if (updated != null) {
                "Renamed to ${updated.fileName}"
            } else {
                "Rename failed — name already exists or invalid"
            }
        }
    }

    fun deleteDocument(doc: PdfDocumentEntity) {
        viewModelScope.launch {
            repository.deleteDocument(doc)
            feedbackMessage.value = "Deleted ${doc.fileName}"
        }
    }

    fun importPdf(uri: Uri) {
        viewModelScope.launch {
            val imported = repository.importExternalPdf(uri)
            feedbackMessage.value = if (imported != null) {
                "Imported ${imported.fileName}"
            } else {
                "Unable to import PDF file"
            }
        }
    }

    fun exportPdf(doc: PdfDocumentEntity, destUri: Uri) {
        viewModelScope.launch {
            val ok = repository.exportPdfToUri(File(doc.filePath), destUri)
            feedbackMessage.value = if (ok) "Saved copy to device" else "Export failed"
        }
    }

    fun clearFeedback() {
        feedbackMessage.value = null
    }

    class Factory(private val repository: PdfRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FilesViewModel(repository) as T
        }
    }
}
