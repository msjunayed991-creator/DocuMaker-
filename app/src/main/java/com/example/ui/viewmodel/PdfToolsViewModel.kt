package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.PdfDocumentEntity
import com.example.data.local.PdfRepository
import com.example.data.processing.PdfDrawStroke
import com.example.data.processing.PdfEngine
import com.example.data.processing.PdfTextOverlay
import com.example.domain.model.CompressionLevel
import com.example.domain.model.DocumentSourceType
import com.example.domain.util.FormatUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ToolResult(
    val document: PdfDocumentEntity? = null,
    val exportedJpgCount: Int = 0,
    val originalSizeBytes: Long = 0L,
    val newSizeBytes: Long = 0L,
    val summaryMessage: String = ""
)

data class PdfToolsUiState(
    val isProcessing: Boolean = false,
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val result: ToolResult? = null,
    val errorMessage: String? = null,
    val savedSignatures: List<File> = emptyList()
)

class PdfToolsViewModel(
    private val repository: PdfRepository
) : ViewModel() {

    val allDocuments: StateFlow<List<PdfDocumentEntity>> = repository.allDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(PdfToolsUiState())
    val uiState: StateFlow<PdfToolsUiState> = _uiState.asStateFlow()

    init {
        refreshSignatures()
    }

    fun refreshSignatures() {
        viewModelScope.launch {
            _uiState.update { it.copy(savedSignatures = repository.listSavedSignatures()) }
        }
    }

    suspend fun importExternalPdfToLibrary(uri: Uri): PdfDocumentEntity? {
        return repository.importExternalPdf(uri)
    }

    suspend fun renderPagePreview(file: File, pageIndex: Int, widthPx: Int = 720): Bitmap? {
        return PdfEngine.renderPdfPage(file, pageIndex, widthPx)
    }

    suspend fun getPageCount(file: File): Int = PdfEngine.getPdfPageCount(file)

    fun mergePdfs(files: List<File>, outputBaseName: String) {
        if (files.size < 2) {
            _uiState.update { it.copy(errorMessage = "Select at least 2 PDF files to merge") }
            return
        }
        runToolTask(outputBaseName, DocumentSourceType.MERGED) { outFile, onProg ->
            val pages = PdfEngine.mergePdfs(files, outFile, onProg)
            pages to "Merged ${files.size} PDFs ($pages pages)"
        }
    }

    fun compressPdf(source: File, level: CompressionLevel, outputBaseName: String) {
        val originalSize = source.length()
        viewModelScope.launch {
            _uiState.update {
                it.copy(isProcessing = true, progressCurrent = 0, progressTotal = 1, result = null, errorMessage = null)
            }
            val outFile = repository.createUniquePdfFile(outputBaseName)
            runCatching {
                val pages = PdfEngine.compressPdf(source, outFile, level) { cur, tot ->
                    _uiState.update { it.copy(progressCurrent = cur, progressTotal = tot) }
                }
                val entity = repository.registerGeneratedPdf(outFile, pages, DocumentSourceType.COMPRESSED)
                val newSize = outFile.length()
                val savedPct = if (originalSize > 0) {
                    (((originalSize - newSize).toDouble() / originalSize.toDouble()) * 100.0).toInt()
                } else 0
                val summary = if (savedPct > 0) {
                    "Reduced size by $savedPct% (${FormatUtils.formatFileSize(originalSize)} → ${FormatUtils.formatFileSize(newSize)})"
                } else {
                    "Saved compressed PDF (${FormatUtils.formatFileSize(newSize)})"
                }
                ToolResult(
                    document = entity,
                    originalSizeBytes = originalSize,
                    newSizeBytes = newSize,
                    summaryMessage = summary
                )
            }.fold(
                onSuccess = { res -> _uiState.update { it.copy(isProcessing = false, result = res) } },
                onFailure = { err ->
                    runCatching { outFile.delete() }
                    _uiState.update { it.copy(isProcessing = false, errorMessage = err.message ?: "Compression failed") }
                }
            )
        }
    }

    fun exportPdfToJpg(context: Context, source: File, pageIndices: List<Int>, baseName: String) {
        if (pageIndices.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Select at least one page to export") }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(isProcessing = true, progressCurrent = 0, progressTotal = pageIndices.size, result = null, errorMessage = null)
            }
            runCatching {
                val uris = PdfEngine.exportPdfToJpgs(context, source, pageIndices, baseName) { cur, tot ->
                    _uiState.update { it.copy(progressCurrent = cur, progressTotal = tot) }
                }
                ToolResult(
                    exportedJpgCount = uris.size,
                    summaryMessage = "Exported ${uris.size} JPG image(s) to Pictures/DocuMaker"
                )
            }.fold(
                onSuccess = { res -> _uiState.update { it.copy(isProcessing = false, result = res) } },
                onFailure = { err -> _uiState.update { it.copy(isProcessing = false, errorMessage = err.message ?: "JPG export failed") } }
            )
        }
    }

    fun rotatePdf(source: File, pageRotations: Map<Int, Int>, outputBaseName: String) {
        runToolTask(outputBaseName, DocumentSourceType.ROTATED) { outFile, onProg ->
            val pages = PdfEngine.rotatePdf(source, outFile, pageRotations, onProg)
            pages to "Saved rotated PDF ($pages pages)"
        }
    }

    fun reorderPdf(source: File, orderedIndices: List<Int>, outputBaseName: String) {
        if (orderedIndices.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "At least one page must remain") }
            return
        }
        runToolTask(outputBaseName, DocumentSourceType.REORDERED) { outFile, onProg ->
            val pages = PdfEngine.reorderPdf(source, outFile, orderedIndices, onProg)
            pages to "Saved reordered PDF ($pages pages)"
        }
    }

    fun addTextToPdf(source: File, overlays: List<PdfTextOverlay>, outputBaseName: String) {
        if (overlays.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Add at least one text element first") }
            return
        }
        runToolTask(outputBaseName, DocumentSourceType.ANNOTATED) { outFile, onProg ->
            val pages = PdfEngine.addTextToPdf(source, outFile, overlays, onProg)
            pages to "Added text to PDF ($pages pages)"
        }
    }

    fun drawOnPdf(source: File, pageIndex: Int, strokes: List<PdfDrawStroke>, outputBaseName: String) {
        if (strokes.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Draw at least one stroke first") }
            return
        }
        runToolTask(outputBaseName, DocumentSourceType.ANNOTATED) { outFile, onProg ->
            val pages = PdfEngine.drawOnPdf(source, outFile, pageIndex, strokes, onProg)
            pages to "Saved markup on page ${pageIndex + 1}"
        }
    }

    fun saveSignatureBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            repository.saveSignature(bitmap)
            refreshSignatures()
        }
    }

    fun deleteSignatureFile(file: File) {
        viewModelScope.launch {
            repository.deleteSignature(file)
            refreshSignatures()
        }
    }

    suspend fun loadSignatureBitmap(file: File): Bitmap? = repository.loadBitmapFromFile(file)

    fun applySignatureToPdf(
        source: File,
        pageIndex: Int,
        signatureBitmap: Bitmap,
        normX: Float,
        normY: Float,
        normWidth: Float,
        outputBaseName: String
    ) {
        runToolTask(outputBaseName, DocumentSourceType.SIGNED) { outFile, onProg ->
            val pages = PdfEngine.addSignatureToPdf(
                source = source,
                outputFile = outFile,
                targetPageIndex = pageIndex,
                signatureBitmap = signatureBitmap,
                normCenterX = normX,
                normCenterY = normY,
                normWidth = normWidth,
                onProgress = onProg
            )
            pages to "Signed page ${pageIndex + 1} successfully"
        }
    }

    private fun runToolTask(
        outputBaseName: String,
        sourceType: DocumentSourceType,
        block: suspend (outFile: File, onProgress: (Int, Int) -> Unit) -> Pair<Int, String>
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isProcessing = true, progressCurrent = 0, progressTotal = 1, result = null, errorMessage = null)
            }
            val outFile = repository.createUniquePdfFile(outputBaseName)
            runCatching {
                val (pages, summary) = block(outFile) { cur, tot ->
                    _uiState.update { it.copy(progressCurrent = cur, progressTotal = tot) }
                }
                val entity = repository.registerGeneratedPdf(outFile, pages, sourceType)
                ToolResult(
                    document = entity,
                    newSizeBytes = outFile.length(),
                    summaryMessage = summary
                )
            }.fold(
                onSuccess = { res -> _uiState.update { it.copy(isProcessing = false, result = res) } },
                onFailure = { err ->
                    runCatching { outFile.delete() }
                    _uiState.update { it.copy(isProcessing = false, errorMessage = err.message ?: "Operation failed") }
                }
            )
        }
    }

    fun clearResultAndError() {
        _uiState.update { it.copy(result = null, errorMessage = null) }
    }

    class Factory(private val repository: PdfRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PdfToolsViewModel(repository) as T
        }
    }
}
