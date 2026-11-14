package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.PdfDocumentEntity
import com.example.data.local.PdfRepository
import com.example.data.local.SettingsRepository
import com.example.data.processing.PdfEngine
import com.example.domain.model.CropQuad
import com.example.domain.model.DocumentSourceType
import com.example.domain.model.ImageAdjustments
import com.example.domain.model.ImageFilterType
import com.example.domain.model.ImageFitMode
import com.example.domain.model.PageMargin
import com.example.domain.model.PageOrientation
import com.example.domain.model.PageSizePreset
import com.example.domain.model.PdfPageSettings
import com.example.domain.model.SelectedImageItem
import com.example.domain.util.FormatUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class GenerationProgress(
    val isGenerating: Boolean = false,
    val currentPage: Int = 0,
    val totalPages: Int = 0
) {
    val fraction: Float
        get() = if (totalPages <= 0) 0f else (currentPage.toFloat() / totalPages.toFloat()).coerceIn(0f, 1f)
}

data class ImageToPdfUiState(
    val items: List<SelectedImageItem> = emptyList(),
    val activeItemId: String? = null,
    val pageSettings: PdfPageSettings = PdfPageSettings(),
    val customFileName: String = FormatUtils.defaultPdfName("DocuMaker"),
    val sourceType: DocumentSourceType = DocumentSourceType.IMAGE_TO_PDF,
    val progress: GenerationProgress = GenerationProgress(),
    val generatedDocument: PdfDocumentEntity? = null,
    val errorMessage: String? = null,
    val statusMessage: String? = null
) {
    val activeItem: SelectedImageItem?
        get() = items.find { it.id == activeItemId } ?: items.firstOrNull()
}

class ImageToPdfViewModel(
    private val repository: PdfRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImageToPdfUiState())
    val uiState: StateFlow<ImageToPdfUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val prefs = settingsRepository.preferencesFlow.first()
            _uiState.update { it.copy(pageSettings = prefs.defaultPageSettings) }
        }
    }

    fun startNewSession(sourceType: DocumentSourceType, initialUris: List<Uri> = emptyList()) {
        viewModelScope.launch {
            val prefs = settingsRepository.preferencesFlow.first()
            val prefix = if (sourceType == DocumentSourceType.CAMERA_SCAN) "Scan" else "DocuMaker"
            val defaultFilter = if (sourceType == DocumentSourceType.CAMERA_SCAN) {
                ImageFilterType.DOCUMENT
            } else {
                ImageFilterType.ORIGINAL
            }
            val newItems = initialUris.mapIndexed { idx, uri ->
                SelectedImageItem(
                    uriString = uri.toString(),
                    displayName = "Page ${idx + 1}",
                    adjustments = ImageAdjustments(filter = defaultFilter)
                )
            }
            _uiState.value = ImageToPdfUiState(
                items = newItems,
                activeItemId = newItems.firstOrNull()?.id,
                pageSettings = prefs.defaultPageSettings,
                customFileName = FormatUtils.defaultPdfName(prefix),
                sourceType = sourceType
            )
        }
    }

    fun addImages(uris: List<Uri>, defaultScanFilter: Boolean = false) {
        if (uris.isEmpty()) return
        _uiState.update { state ->
            val startIdx = state.items.size
            val filter = if (defaultScanFilter || state.sourceType == DocumentSourceType.CAMERA_SCAN) {
                ImageFilterType.DOCUMENT
            } else {
                ImageFilterType.ORIGINAL
            }
            val added = uris.mapIndexed { idx, uri ->
                SelectedImageItem(
                    uriString = uri.toString(),
                    displayName = "Page ${startIdx + idx + 1}",
                    adjustments = ImageAdjustments(filter = filter)
                )
            }
            val combined = state.items + added
            state.copy(
                items = combined,
                activeItemId = state.activeItemId ?: added.firstOrNull()?.id,
                generatedDocument = null
            )
        }
    }

    fun setActiveItem(id: String) {
        _uiState.update { it.copy(activeItemId = id) }
    }

    fun removeItem(id: String) {
        _uiState.update { state ->
            val updated = state.items.filterNot { it.id == id }
            val nextActive = if (state.activeItemId == id) updated.firstOrNull()?.id else state.activeItemId
            state.copy(items = updated, activeItemId = nextActive)
        }
    }

    fun moveItem(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            val list = state.items.toMutableList()
            if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) {
                return@update state
            }
            val moved = list.removeAt(fromIndex)
            list.add(toIndex, moved)
            state.copy(items = list)
        }
    }

    fun rotateItem(id: String, deltaDegrees: Int = 90) {
        updateItemAdjustments(id) { adj ->
            adj.copy(rotationDegrees = ((adj.rotationDegrees + deltaDegrees) % 360 + 360) % 360)
        }
    }

    fun updateCropQuad(id: String, quad: CropQuad) {
        updateItemAdjustments(id) { adj -> adj.copy(cropQuad = quad) }
    }

    fun updateFilter(id: String, filter: ImageFilterType) {
        updateItemAdjustments(id) { adj -> adj.copy(filter = filter) }
    }

    fun updateBrightness(id: String, brightness: Float) {
        updateItemAdjustments(id) { adj -> adj.copy(brightness = brightness.coerceIn(-100f, 100f)) }
    }

    fun updateContrast(id: String, contrast: Float) {
        updateItemAdjustments(id) { adj -> adj.copy(contrast = contrast.coerceIn(0.5f, 2.0f)) }
    }

    fun updateSaturation(id: String, saturation: Float) {
        updateItemAdjustments(id) { adj -> adj.copy(saturation = saturation.coerceIn(0f, 2.0f)) }
    }

    fun resetAdjustments(id: String) {
        updateItemAdjustments(id) { adj ->
            adj.copy(
                filter = ImageFilterType.ORIGINAL,
                brightness = 0f,
                contrast = 1f,
                saturation = 1f
            )
        }
    }

    fun applyActiveFilterToAll() {
        val active = _uiState.value.activeItem ?: return
        val template = active.adjustments
        _uiState.update { state ->
            state.copy(
                items = state.items.map { item ->
                    item.copy(
                        adjustments = item.adjustments.copy(
                            filter = template.filter,
                            brightness = template.brightness,
                            contrast = template.contrast,
                            saturation = template.saturation
                        )
                    )
                },
                statusMessage = "Applied ${template.filter.label} filter to all ${state.items.size} pages"
            )
        }
    }

    private fun updateItemAdjustments(id: String, transform: (ImageAdjustments) -> ImageAdjustments) {
        _uiState.update { state ->
            state.copy(
                items = state.items.map { item ->
                    if (item.id == id) item.copy(adjustments = transform(item.adjustments)) else item
                }
            )
        }
    }

    fun updatePageSize(preset: PageSizePreset) {
        _uiState.update { it.copy(pageSettings = it.pageSettings.copy(sizePreset = preset)) }
    }

    fun updateCustomDimensionsMm(widthMm: Int, heightMm: Int) {
        _uiState.update {
            it.copy(
                pageSettings = it.pageSettings.copy(
                    sizePreset = PageSizePreset.CUSTOM,
                    customWidthMm = widthMm.coerceIn(50, 1000),
                    customHeightMm = heightMm.coerceIn(50, 1400)
                )
            )
        }
    }

    fun updateOrientation(orientation: PageOrientation) {
        _uiState.update { it.copy(pageSettings = it.pageSettings.copy(orientation = orientation)) }
    }

    fun updateMargin(margin: PageMargin) {
        _uiState.update { it.copy(pageSettings = it.pageSettings.copy(margin = margin)) }
    }

    fun updateImageFit(fit: ImageFitMode) {
        _uiState.update { it.copy(pageSettings = it.pageSettings.copy(imageFit = fit)) }
    }

    fun updateMaintainAspectRatio(maintain: Boolean) {
        _uiState.update { it.copy(pageSettings = it.pageSettings.copy(maintainAspectRatio = maintain)) }
    }

    fun updateWhiteBackground(whiteBg: Boolean) {
        _uiState.update { it.copy(pageSettings = it.pageSettings.copy(whiteBackground = whiteBg)) }
    }

    fun updateJpegQuality(quality: Int) {
        _uiState.update { it.copy(pageSettings = it.pageSettings.copy(jpegQuality = quality.coerceIn(40, 100))) }
    }

    fun updateCustomFileName(name: String) {
        _uiState.update { it.copy(customFileName = name) }
    }

    fun generatePdf(context: Context) {
        val state = _uiState.value
        if (state.items.isEmpty() || state.progress.isGenerating) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    progress = GenerationProgress(isGenerating = true, currentPage = 0, totalPages = state.items.size),
                    errorMessage = null,
                    generatedDocument = null
                )
            }
            val outputFile = repository.createUniquePdfFile(state.customFileName)
            val result = runCatching {
                val pages = PdfEngine.generatePdfFromImages(
                    context = context,
                    items = state.items,
                    settings = state.pageSettings,
                    outputFile = outputFile,
                    onProgress = { current, total ->
                        _uiState.update {
                            it.copy(progress = GenerationProgress(true, current, total))
                        }
                    }
                )
                repository.registerGeneratedPdf(outputFile, pages, state.sourceType)
            }

            result.fold(
                onSuccess = { entity ->
                    _uiState.update {
                        it.copy(
                            progress = GenerationProgress(isGenerating = false, currentPage = entity.pageCount, totalPages = entity.pageCount),
                            generatedDocument = entity,
                            statusMessage = "PDF saved: ${entity.fileName}"
                        )
                    }
                },
                onFailure = { err ->
                    runCatching { outputFile.delete() }
                    _uiState.update {
                        it.copy(
                            progress = GenerationProgress(isGenerating = false),
                            errorMessage = err.message ?: "Failed to generate PDF"
                        )
                    }
                }
            )
        }
    }

    fun renameGeneratedPdf(newName: String) {
        val doc = _uiState.value.generatedDocument ?: return
        viewModelScope.launch {
            val renamed = repository.renameDocument(doc, newName)
            if (renamed != null) {
                _uiState.update { it.copy(generatedDocument = renamed, statusMessage = "Renamed to ${renamed.fileName}") }
            } else {
                _uiState.update { it.copy(errorMessage = "Could not rename file — name may already exist") }
            }
        }
    }

    fun exportGeneratedPdf(destUri: Uri) {
        val doc = _uiState.value.generatedDocument ?: return
        viewModelScope.launch {
            val ok = repository.exportPdfToUri(File(doc.filePath), destUri)
            _uiState.update {
                it.copy(statusMessage = if (ok) "Saved copy to selected folder" else "Failed to save copy")
            }
        }
    }

    fun clearGeneratedResult() {
        _uiState.update { it.copy(generatedDocument = null) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, statusMessage = null) }
    }

    class Factory(
        private val repository: PdfRepository,
        private val settingsRepository: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ImageToPdfViewModel(repository, settingsRepository) as T
        }
    }
}
