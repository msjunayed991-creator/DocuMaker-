package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.PdfRepository
import com.example.data.processing.ImageProcessor
import com.example.domain.model.ImageAdjustments
import com.example.domain.model.ImageFilterType
import com.example.domain.model.ImageFitMode
import com.example.domain.model.PageMargin
import com.example.domain.model.PageOrientation
import com.example.domain.model.PageSizePreset
import com.example.domain.model.SelectedImageItem
import com.example.domain.util.FormatUtils
import com.example.ui.components.PdfPageLivePreview
import com.example.ui.components.PerspectiveCropEditor
import com.example.ui.components.RenamePdfDialog
import com.example.ui.theme.DocuRed
import com.example.ui.theme.InkBlack
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SuccessGreen
import com.example.ui.viewmodel.ImageToPdfViewModel
import java.io.File

private enum class StudioSubTab(val label: String) {
    PAGES("Pages & Order"),
    FILTERS("Filters"),
    SETTINGS("Page Settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageToPdfScreen(
    viewModel: ImageToPdfViewModel,
    repository: PdfRepository,
    onBack: () -> Unit,
    onOpenScanner: () -> Unit,
    onOpenReader: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedSubTab by remember { mutableStateOf(StudioSubTab.PAGES) }
    var showCropEditor by remember { mutableStateOf(false) }
    var showGenerateSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    // Decoded source & processed preview bitmap for the active item
    val activeItem = state.activeItem
    var activeSourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var activePreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var filterThumbs by remember { mutableStateOf<Map<ImageFilterType, Bitmap>>(emptyMap()) }

    LaunchedEffect(activeItem?.id, activeItem?.uriString) {
        if (activeItem == null) {
            activeSourceBitmap = null
            activePreviewBitmap = null
            filterThumbs = emptyMap()
            return@LaunchedEffect
        }
        val decoded = ImageProcessor.loadBitmapFromUri(context, Uri.parse(activeItem.uriString), maxDimension = 1280)
        activeSourceBitmap = decoded
        if (decoded != null) {
            val thumbBase = ImageProcessor.loadBitmapFromUri(context, Uri.parse(activeItem.uriString), maxDimension = 180)
            if (thumbBase != null) {
                val map = ImageFilterType.entries.associateWith { f ->
                    ImageProcessor.applyFilterAndAdjustments(thumbBase, ImageAdjustments(filter = f))
                }
                filterThumbs = map
            }
        }
    }

    LaunchedEffect(activeSourceBitmap, activeItem?.adjustments) {
        val src = activeSourceBitmap
        val adj = activeItem?.adjustments
        if (src != null && adj != null) {
            activePreviewBitmap = ImageProcessor.applyFullPipeline(src, adj)
        } else {
            activePreviewBitmap = null
        }
    }

    LaunchedEffect(state.statusMessage, state.errorMessage) {
        val msg = state.errorMessage ?: state.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50)
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addImages(uris)
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            viewModel.addImages(listOf(uri))
        }
        pendingCameraUri = null
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = repository.createCameraCaptureUri()
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        }
    }

    val exportSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        if (destUri != null) viewModel.exportGeneratedPdf(destUri)
    }

    BackHandler(enabled = showCropEditor) {
        showCropEditor = false
    }

    if (showCropEditor && activeItem != null && activeSourceBitmap != null) {
        // Show crop editor on rotated bitmap without prior crop so user can readjust corners freely
        val baseForCrop = remember(activeSourceBitmap, activeItem.adjustments.rotationDegrees) {
            ImageProcessor.rotateBitmap(activeSourceBitmap!!, activeItem.adjustments.rotationDegrees)
        }
        PerspectiveCropEditor(
            bitmap = baseForCrop,
            initialQuad = activeItem.adjustments.cropQuad,
            onRotate90 = { viewModel.rotateItem(activeItem.id, 90) },
            onCancel = { showCropEditor = false },
            onConfirm = { newQuad ->
                viewModel.updateCropQuad(activeItem.id, newQuad)
                showCropEditor = false
            }
        )
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Image to PDF",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${state.items.size} image(s) • ${state.pageSettings.sizePreset.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("img2pdf_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = { showGenerateSheet = true },
                        enabled = state.items.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = DocuRed, contentColor = PureWhite),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .testTag("img2pdf_convert_btn")
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create PDF")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.items.isEmpty()) {
                EmptySelectionState(
                    onPickPhotos = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onCapturePhoto = {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                            PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            val uri = repository.createCameraCaptureUri()
                            pendingCameraUri = uri
                            takePictureLauncher.launch(uri)
                        } else {
                            cameraPermLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onOpenScanner = onOpenScanner
                )
            } else {
                // Active Preview Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .background(InkBlack)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedSubTab == StudioSubTab.SETTINGS) {
                        PdfPageLivePreview(
                            settings = state.pageSettings,
                            previewBitmap = activePreviewBitmap
                        )
                    } else if (activePreviewBitmap != null) {
                        Image(
                            bitmap = activePreviewBitmap!!.asImageBitmap(),
                            contentDescription = "Selected image preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }

                    // Page badge overlay
                    val idx = state.items.indexOfFirst { it.id == activeItem?.id }
                    if (idx >= 0) {
                        Surface(
                            color = InkBlack.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp)
                        ) {
                            Text(
                                text = "Page ${idx + 1} / ${state.items.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = PureWhite,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Quick Edit Pills
                    if (activeItem != null && selectedSubTab != StudioSubTab.SETTINGS) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = InkBlack.copy(alpha = 0.8f),
                                shape = CircleShape,
                                modifier = Modifier
                                    .clickable { showCropEditor = true }
                                    .testTag("open_crop_btn")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Crop, contentDescription = "Crop", tint = PureWhite, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Crop", style = MaterialTheme.typography.labelSmall, color = PureWhite)
                                }
                            }
                            Surface(
                                color = InkBlack.copy(alpha = 0.8f),
                                shape = CircleShape,
                                modifier = Modifier
                                    .clickable { viewModel.rotateItem(activeItem.id, 90) }
                                    .testTag("quick_rotate_btn")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate", tint = PureWhite, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Rotate", style = MaterialTheme.typography.labelSmall, color = PureWhite)
                                }
                            }
                        }
                    }
                }

                // Studio Sub-Tabs: Pages & Order | Filters | Page Settings
                TabRow(
                    selectedTabIndex = selectedSubTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = DocuRed,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedSubTab.ordinal]),
                            color = DocuRed
                        )
                    }
                ) {
                    StudioSubTab.entries.forEach { tab ->
                        Tab(
                            selected = selectedSubTab == tab,
                            onClick = { selectedSubTab = tab },
                            text = {
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1
                                )
                            },
                            modifier = Modifier.testTag("studio_tab_${tab.name.lowercase()}")
                        )
                    }
                }

                // Tab Content
                when (selectedSubTab) {
                    StudioSubTab.PAGES -> {
                        PagesOrderSection(
                            items = state.items,
                            activeItemId = activeItem?.id,
                            onSelect = viewModel::setActiveItem,
                            onMove = viewModel::moveItem,
                            onRemove = viewModel::removeItem,
                            onAddFromGallery = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onTakePhoto = {
                                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                                    PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    val uri = repository.createCameraCaptureUri()
                                    pendingCameraUri = uri
                                    takePictureLauncher.launch(uri)
                                } else {
                                    cameraPermLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }
                        )
                    }

                    StudioSubTab.FILTERS -> {
                        if (activeItem != null) {
                            FiltersEditorSection(
                                item = activeItem,
                                filterThumbs = filterThumbs,
                                onSelectFilter = { viewModel.updateFilter(activeItem.id, it) },
                                onBrightnessChange = { viewModel.updateBrightness(activeItem.id, it) },
                                onContrastChange = { viewModel.updateContrast(activeItem.id, it) },
                                onSaturationChange = { viewModel.updateSaturation(activeItem.id, it) },
                                onReset = { viewModel.resetAdjustments(activeItem.id) },
                                onApplyAll = viewModel::applyActiveFilterToAll
                            )
                        }
                    }

                    StudioSubTab.SETTINGS -> {
                        PageSettingsEditorSection(
                            viewModel = viewModel,
                            state = state
                        )
                    }
                }
            }
        }
    }

    // Generate PDF Bottom Sheet
    if (showGenerateSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                if (!state.progress.isGenerating) {
                    showGenerateSheet = false
                    viewModel.clearGeneratedResult()
                }
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                val generated = state.generatedDocument
                if (generated != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "PDF Generated!",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "${generated.fileName} • ${generated.pageCount} pages • ${FormatUtils.formatFileSize(generated.fileSizeBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = {
                            showGenerateSheet = false
                            viewModel.clearGeneratedResult()
                            onOpenReader(generated.id)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DocuRed, contentColor = PureWhite),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("generated_open_btn")
                    ) {
                        Icon(Icons.Filled.Description, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open in PDF Reader")
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val intent = repository.getShareIntent(File(generated.filePath))
                                context.startActivity(Intent.createChooser(intent, "Share PDF"))
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share")
                        }
                        OutlinedButton(
                            onClick = { exportSafLauncher.launch(generated.fileName) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Copy")
                        }
                        OutlinedButton(
                            onClick = { showRenameDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Rename")
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = {
                            showGenerateSheet = false
                            viewModel.clearGeneratedResult()
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Done")
                    }
                } else {
                    Text(
                        text = "Export PDF Document",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${state.items.size} page(s) • ${state.pageSettings.sizePreset.label} ${state.pageSettings.orientation.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = state.customFileName,
                        onValueChange = viewModel::updateCustomFileName,
                        label = { Text("PDF Filename") },
                        suffix = { Text(".pdf") },
                        singleLine = true,
                        enabled = !state.progress.isGenerating,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pdf_filename_input")
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (state.progress.isGenerating) {
                        LinearProgressIndicator(
                            progress = { state.progress.fraction },
                            color = DocuRed,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Processing page ${state.progress.currentPage} of ${state.progress.totalPages}...",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    } else {
                        Button(
                            onClick = { viewModel.generatePdf(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = DocuRed, contentColor = PureWhite),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("confirm_generate_pdf_btn")
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate PDF")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showRenameDialog && state.generatedDocument != null) {
        RenamePdfDialog(
            initialName = state.generatedDocument!!.fileName,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                viewModel.renameGeneratedPdf(newName)
                showRenameDialog = false
            }
        )
    }
}

@Composable
private fun EmptySelectionState(
    onPickPhotos: () -> Unit,
    onCapturePhoto: () -> Unit,
    onOpenScanner: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.AddPhotoAlternate,
                contentDescription = null,
                tint = DocuRed,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Select Images to Convert",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Pick multiple photos from your gallery or capture pages with the camera. You can reorder, crop, apply filters, and customize page layout.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onPickPhotos,
            colors = ButtonDefaults.buttonColors(containerColor = DocuRed, contentColor = PureWhite),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("pick_gallery_btn")
        ) {
            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Select Photos from Gallery")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCapturePhoto,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("capture_camera_btn")
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Camera")
            }
            OutlinedButton(
                onClick = onOpenScanner,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("launch_scanner_btn")
            ) {
                Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Doc Scanner")
            }
        }
    }
}

@Composable
private fun PagesOrderSection(
    items: List<SelectedImageItem>,
    activeItemId: String?,
    onSelect: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (String) -> Unit,
    onAddFromGallery: () -> Unit,
    onTakePhoto: () -> Unit
) {
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Long-press & drag to reorder pages",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onAddFromGallery,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("add_more_gallery_btn")
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Photos", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onTakePhoto,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Camera", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                val isSelected = item.id == activeItemId
                val isDragging = draggingIndex == index

                Card(
                    modifier = Modifier
                        .width(128.dp)
                        .graphicsLayer {
                            if (isDragging) {
                                translationX = dragOffsetX
                                scaleX = 1.05f
                                scaleY = 1.05f
                            }
                        }
                        .pointerInput(index, items.size) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingIndex = index
                                    dragOffsetX = 0f
                                },
                                onDragEnd = {
                                    draggingIndex = null
                                    dragOffsetX = 0f
                                },
                                onDragCancel = {
                                    draggingIndex = null
                                    dragOffsetX = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetX += dragAmount.x
                                    val thresholdPx = 110f
                                    val curIdx = draggingIndex ?: index
                                    if (dragOffsetX > thresholdPx && curIdx < items.lastIndex) {
                                        onMove(curIdx, curIdx + 1)
                                        draggingIndex = curIdx + 1
                                        dragOffsetX -= thresholdPx
                                    } else if (dragOffsetX < -thresholdPx && curIdx > 0) {
                                        onMove(curIdx, curIdx - 1)
                                        draggingIndex = curIdx - 1
                                        dragOffsetX += thresholdPx
                                    }
                                }
                            )
                        }
                        .clickable { onSelect(item.id) }
                        .testTag("page_thumb_${index}"),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        width = if (isSelected) 2.5.dp else 1.dp,
                        color = if (isSelected) DocuRed else MaterialTheme.colorScheme.outlineVariant
                    ),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(132.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(InkBlack)
                        ) {
                            AsyncImage(
                                model = Uri.parse(item.uriString),
                                contentDescription = "Page ${index + 1}",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        rotationZ = item.adjustments.rotationDegrees.toFloat()
                                    },
                                contentScale = ContentScale.Crop
                            )
                            Surface(
                                color = if (isSelected) DocuRed else InkBlack.copy(alpha = 0.75f),
                                shape = CircleShape,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = PureWhite,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                            Surface(
                                color = InkBlack.copy(alpha = 0.75f),
                                shape = CircleShape,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .clickable { onRemove(item.id) }
                                    .testTag("remove_page_$index")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Remove page ${index + 1}",
                                    tint = PureWhite,
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .size(14.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { if (index > 0) onMove(index, index - 1) },
                                enabled = index > 0,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Move left",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Icon(
                                Icons.Filled.DragIndicator,
                                contentDescription = "Drag to reorder",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            IconButton(
                                onClick = { if (index < items.lastIndex) onMove(index, index + 1) },
                                enabled = index < items.lastIndex,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Move right",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FiltersEditorSection(
    item: SelectedImageItem,
    filterThumbs: Map<ImageFilterType, Bitmap>,
    onSelectFilter: (ImageFilterType) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onContrastChange: (Float) -> Unit,
    onSaturationChange: (Float) -> Unit,
    onReset: () -> Unit,
    onApplyAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Presets",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onReset) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset")
                }
                TextButton(onClick = onApplyAll) {
                    Text("Apply to All", color = DocuRed, fontWeight = FontWeight.Bold)
                }
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(ImageFilterType.entries) { filterType ->
                val selected = item.adjustments.filter == filterType
                val thumb = filterThumbs[filterType]
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(76.dp)
                        .clickable { onSelectFilter(filterType) }
                        .testTag("filter_${filterType.name.lowercase()}")
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                width = if (selected) 2.5.dp else 1.dp,
                                color = if (selected) DocuRed else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (thumb != null && !thumb.isRecycled) {
                            Image(
                                bitmap = thumb.asImageBitmap(),
                                contentDescription = filterType.label,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Filled.Tune, contentDescription = null)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = filterType.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (selected) DocuRed else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }
        }

        // Fine-tune Sliders
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Brightness (${item.adjustments.brightness.toInt()})",
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = item.adjustments.brightness,
                    onValueChange = onBrightnessChange,
                    valueRange = -100f..100f,
                    colors = SliderDefaults.colors(thumbColor = DocuRed, activeTrackColor = DocuRed)
                )

                Text(
                    text = "Contrast (${String.format("%.2f", item.adjustments.contrast)}x)",
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = item.adjustments.contrast,
                    onValueChange = onContrastChange,
                    valueRange = 0.5f..2.0f,
                    colors = SliderDefaults.colors(thumbColor = DocuRed, activeTrackColor = DocuRed)
                )

                Text(
                    text = "Saturation (${String.format("%.2f", item.adjustments.saturation)}x)",
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = item.adjustments.saturation,
                    onValueChange = onSaturationChange,
                    valueRange = 0f..2.0f,
                    colors = SliderDefaults.colors(thumbColor = DocuRed, activeTrackColor = DocuRed)
                )
            }
        }
    }
}

@Composable
private fun PageSettingsEditorSection(
    viewModel: ImageToPdfViewModel,
    state: com.example.ui.viewmodel.ImageToPdfUiState
) {
    val settings = state.pageSettings
    var customWText by remember(settings.customWidthMm) { mutableStateOf(settings.customWidthMm.toString()) }
    var customHText by remember(settings.customHeightMm) { mutableStateOf(settings.customHeightMm.toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Page Size", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(PageSizePreset.entries) { preset ->
                FilterChip(
                    selected = settings.sizePreset == preset,
                    onClick = { viewModel.updatePageSize(preset) },
                    label = { Text(preset.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DocuRed,
                        selectedLabelColor = PureWhite
                    ),
                    modifier = Modifier.testTag("page_size_${preset.name.lowercase()}")
                )
            }
        }

        if (settings.sizePreset == PageSizePreset.CUSTOM) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = customWText,
                    onValueChange = {
                        customWText = it
                        it.toIntOrNull()?.let { w ->
                            viewModel.updateCustomDimensionsMm(w, customHText.toIntOrNull() ?: settings.customHeightMm)
                        }
                    },
                    label = { Text("Width (mm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = customHText,
                    onValueChange = {
                        customHText = it
                        it.toIntOrNull()?.let { h ->
                            viewModel.updateCustomDimensionsMm(customWText.toIntOrNull() ?: settings.customWidthMm, h)
                        }
                    },
                    label = { Text("Height (mm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Text("Orientation", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PageOrientation.entries.forEach { orient ->
                FilterChip(
                    selected = settings.orientation == orient,
                    onClick = { viewModel.updateOrientation(orient) },
                    label = { Text(orient.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DocuRed,
                        selectedLabelColor = PureWhite
                    ),
                    modifier = Modifier.testTag("orient_${orient.name.lowercase()}")
                )
            }
        }

        Text("Page Margins", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(PageMargin.entries) { margin ->
                FilterChip(
                    selected = settings.margin == margin,
                    onClick = { viewModel.updateMargin(margin) },
                    label = { Text(margin.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DocuRed,
                        selectedLabelColor = PureWhite
                    )
                )
            }
        }

        Text("Image Fit", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ImageFitMode.entries.forEach { fit ->
                FilterChip(
                    selected = settings.imageFit == fit,
                    onClick = { viewModel.updateImageFit(fit) },
                    label = { Text(fit.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = DocuRed,
                        selectedLabelColor = PureWhite
                    )
                )
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Maintain Aspect Ratio", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = settings.maintainAspectRatio,
                        onCheckedChange = viewModel::updateMaintainAspectRatio,
                        colors = SwitchDefaults.colors(checkedThumbColor = PureWhite, checkedTrackColor = DocuRed)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("White Page Background", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = settings.whiteBackground,
                        onCheckedChange = viewModel::updateWhiteBackground,
                        colors = SwitchDefaults.colors(checkedThumbColor = PureWhite, checkedTrackColor = DocuRed)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "PDF Image Quality: ${settings.jpegQuality}%",
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = settings.jpegQuality.toFloat(),
                    onValueChange = { viewModel.updateJpegQuality(it.toInt()) },
                    valueRange = 45f..100f,
                    colors = SliderDefaults.colors(thumbColor = DocuRed, activeTrackColor = DocuRed)
                )
            }
        }
    }
}
