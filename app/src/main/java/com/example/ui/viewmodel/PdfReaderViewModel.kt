package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.PdfDocumentEntity
import com.example.data.local.PdfRepository
import com.example.data.processing.PdfEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class PdfReaderUiState(
    val document: PdfDocumentEntity? = null,
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val zoomScale: Float = 1.0f,
    val isFullScreen: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val statusMessage: String? = null
)

class PdfReaderViewModel(
    private val repository: PdfRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PdfReaderUiState())
    val uiState: StateFlow<PdfReaderUiState> = _uiState.asStateFlow()

    private val pageCache = object : LruCache<Int, Bitmap>(10) {}

    fun loadDocument(docId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            pageCache.evictAll()
            val doc = repository.getDocumentById(docId)
            if (doc == null) {
                _uiState.update { it.copy(isLoading = false, error = "Document not found") }
                return@launch
            }
            val file = File(doc.filePath)
            if (!file.exists()) {
                _uiState.update { it.copy(isLoading = false, error = "PDF file missing on storage") }
                return@launch
            }
            repository.markOpened(docId)
            val count = PdfEngine.getPdfPageCount(file)
            if (count <= 0) {
                _uiState.update { it.copy(isLoading = false, error = "Unable to parse PDF pages") }
                return@launch
            }
            _uiState.update {
                it.copy(
                    document = doc,
                    pageCount = count,
                    currentPage = 0,
                    zoomScale = 1.0f,
                    isLoading = false
                )
            }
        }
    }

    suspend fun getOrRenderPage(pageIndex: Int, widthPx: Int): Bitmap? {
        pageCache.get(pageIndex)?.let { cached ->
            if (!cached.isRecycled) return cached
        }
        val doc = _uiState.value.document ?: return null
        val bmp = PdfEngine.renderPdfPage(File(doc.filePath), pageIndex, widthPx.coerceIn(720, 1600))
        if (bmp != null) {
            pageCache.put(pageIndex, bmp)
        }
        return bmp
    }

    fun setCurrentPage(pageIndex: Int) {
        _uiState.update { it.copy(currentPage = pageIndex.coerceIn(0, maxOf(0, it.pageCount - 1))) }
    }

    fun setZoom(scale: Float) {
        _uiState.update { it.copy(zoomScale = scale.coerceIn(0.8f, 3.5f)) }
    }

    fun toggleFullScreen() {
        _uiState.update { it.copy(isFullScreen = !it.isFullScreen) }
    }

    fun toggleFavorite() {
        val doc = _uiState.value.document ?: return
        viewModelScope.launch {
            repository.toggleFavorite(doc)
            _uiState.update { it.copy(document = doc.copy(isFavorite = !doc.isFavorite)) }
        }
    }

    fun renameDocument(newName: String) {
        val doc = _uiState.value.document ?: return
        viewModelScope.launch {
            val updated = repository.renameDocument(doc, newName)
            if (updated != null) {
                _uiState.update { it.copy(document = updated, statusMessage = "Renamed to ${updated.fileName}") }
            } else {
                _uiState.update { it.copy(statusMessage = "Rename failed — invalid or duplicate name") }
            }
        }
    }

    fun exportToUri(destUri: Uri) {
        val doc = _uiState.value.document ?: return
        viewModelScope.launch {
            val ok = repository.exportPdfToUri(File(doc.filePath), destUri)
            _uiState.update {
                it.copy(statusMessage = if (ok) "Saved copy to device" else "Could not export PDF")
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    override fun onCleared() {
        pageCache.evictAll()
        super.onCleared()
    }

    class Factory(private val repository: PdfRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PdfReaderViewModel(repository) as T
        }
    }
}
