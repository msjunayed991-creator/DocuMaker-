package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.local.PdfRepository
import com.example.data.processing.ImageProcessor
import com.example.domain.model.CropQuad
import com.example.domain.model.DocumentSourceType
import com.example.domain.model.ImageFilterType
import com.example.ui.components.PerspectiveCropEditor
import com.example.ui.theme.DocuRed
import com.example.ui.theme.InkBlack
import com.example.ui.theme.PureWhite
import com.example.ui.viewmodel.ImageToPdfViewModel
import kotlinx.coroutines.launch

private data class ScannedPageDraft(
    val uri: Uri,
    val cropQuad: CropQuad = CropQuad(),
    val rotation: Int = 0
)

@Composable
fun CameraScannerScreen(
    repository: PdfRepository,
    imageToPdfViewModel: ImageToPdfViewModel,
    onBack: () -> Unit,
    onFinishToStudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var flashEnabled by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(true) }
    var isCapturing by remember { mutableStateOf(false) }
    var captureError by remember { mutableStateOf<String?>(null) }

    val scannedPages = remember { mutableStateListOf<ScannedPageDraft>() }
    var pendingCropIndex by remember { mutableStateOf<Int?>(null) }
    var pendingCropBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    LaunchedEffect(pendingCropIndex) {
        val idx = pendingCropIndex
        pendingCropBitmap = if (idx != null && idx in scannedPages.indices) {
            ImageProcessor.loadBitmapFromUri(context, scannedPages[idx].uri, maxDimension = 1280)
        } else {
            null
        }
    }

    if (pendingCropIndex != null && pendingCropBitmap != null) {
        val idx = pendingCropIndex!!
        val draft = scannedPages[idx]
        val rotated = remember(pendingCropBitmap, draft.rotation) {
            ImageProcessor.rotateBitmap(pendingCropBitmap!!, draft.rotation)
        }
        PerspectiveCropEditor(
            bitmap = rotated,
            initialQuad = draft.cropQuad,
            onRotate90 = {
                scannedPages[idx] = draft.copy(rotation = (draft.rotation + 90) % 360)
            },
            onCancel = { pendingCropIndex = null },
            onConfirm = { newQuad ->
                scannedPages[idx] = scannedPages[idx].copy(cropQuad = newQuad)
                pendingCropIndex = null
            }
        )
        return
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = InkBlack
    ) {
        if (!hasCameraPermission) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(DocuRed.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = null,
                        tint = DocuRed,
                        modifier = Modifier.size(38.dp)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Camera Access Required",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = PureWhite
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "DocuMaker needs the camera to scan paper documents, receipts, and notes locally on your device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PureWhite.copy(alpha = 0.75f)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = DocuRed, contentColor = PureWhite),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("grant_camera_btn")
                ) {
                    Text("Enable Camera")
                }
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26262C), contentColor = PureWhite),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Back")
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Top Overlay Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("scanner_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PureWhite)
                    }
                    Surface(
                        color = Color(0xFF1F1F25),
                        shape = CircleShape
                    ) {
                        Text(
                            text = "Document Scanner",
                            style = MaterialTheme.typography.labelLarge,
                            color = PureWhite,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                    Row {
                        IconButton(onClick = { showGrid = !showGrid }) {
                            Icon(
                                imageVector = if (showGrid) Icons.Filled.GridOn else Icons.Filled.GridOff,
                                contentDescription = "Toggle grid",
                                tint = PureWhite
                            )
                        }
                        IconButton(onClick = {
                            flashEnabled = !flashEnabled
                            imageCapture?.flashMode = if (flashEnabled) {
                                ImageCapture.FLASH_MODE_ON
                            } else {
                                ImageCapture.FLASH_MODE_OFF
                            }
                        }) {
                            Icon(
                                imageVector = if (flashEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                                contentDescription = "Toggle flash",
                                tint = if (flashEnabled) DocuRed else PureWhite
                            )
                        }
                    }
                }

                // CameraX Viewfinder
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.Black)
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            val previewView = PreviewView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                runCatching {
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.surfaceProvider = previewView.surfaceProvider
                                    }
                                    val capture = ImageCapture.Builder()
                                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                        .setFlashMode(
                                            if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                                        )
                                        .build()
                                    imageCapture = capture
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        capture
                                    )
                                }.onFailure { err ->
                                    captureError = err.message ?: "Camera unavailable"
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        }
                    )

                    if (showGrid) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val lineColor = PureWhite.copy(alpha = 0.25f)
                            drawLine(lineColor, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), 2f)
                            drawLine(lineColor, Offset(size.width * 2f / 3f, 0f), Offset(size.width * 2f / 3f, size.height), 2f)
                            drawLine(lineColor, Offset(0f, size.height / 3f), Offset(size.width, size.height / 3f), 2f)
                            drawLine(lineColor, Offset(0f, size.height * 2f / 3f), Offset(size.width, size.height * 2f / 3f), 2f)
                        }
                    }

                    captureError?.let { err ->
                        Surface(
                            color = InkBlack.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(20.dp)
                        ) {
                            Text(
                                text = err,
                                color = PureWhite,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }

                // Bottom Capture Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Last captured thumbnail (tap to crop/dewarp)
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF1F1F25))
                            .border(
                                width = 1.5.dp,
                                color = if (scannedPages.isNotEmpty()) DocuRed else Color.DarkGray,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable(enabled = scannedPages.isNotEmpty()) {
                                pendingCropIndex = scannedPages.lastIndex
                            }
                            .testTag("scanner_last_thumb"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (scannedPages.isNotEmpty()) {
                            AsyncImage(
                                model = scannedPages.last().uri,
                                contentDescription = "Edit last scan",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Surface(
                                color = DocuRed,
                                shape = CircleShape,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(3.dp)
                            ) {
                                Text(
                                    text = "${scannedPages.size}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = PureWhite,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        } else {
                            Text("0", color = PureWhite.copy(alpha = 0.5f), style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    // Shutter Button
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .border(4.dp, PureWhite, CircleShape)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(if (isCapturing) Color.Gray else DocuRed)
                            .clickable(enabled = !isCapturing) {
                                val cap = imageCapture ?: return@clickable
                                isCapturing = true
                                val outFile = repository.createCameraCaptureFile()
                                val outputOptions = ImageCapture.OutputFileOptions.Builder(outFile).build()
                                cap.takePicture(
                                    outputOptions,
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                            isCapturing = false
                                            val uri = Uri.fromFile(outFile)
                                            scannedPages.add(ScannedPageDraft(uri = uri))
                                            pendingCropIndex = scannedPages.lastIndex
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            isCapturing = false
                                            captureError = exception.message ?: "Capture failed"
                                        }
                                    }
                                )
                            }
                            .testTag("scanner_shutter_btn")
                    )

                    // Next / Combine Scans Button
                    Button(
                        onClick = {
                            if (scannedPages.isNotEmpty()) {
                                scope.launch {
                                    imageToPdfViewModel.startNewSession(
                                        sourceType = DocumentSourceType.CAMERA_SCAN,
                                        initialUris = emptyList()
                                    )
                                    scannedPages.forEachIndexed { idx, draft ->
                                        imageToPdfViewModel.addImages(listOf(draft.uri), defaultScanFilter = true)
                                        val addedId = imageToPdfViewModel.uiState.value.items.lastOrNull()?.id
                                        if (addedId != null) {
                                            imageToPdfViewModel.updateCropQuad(addedId, draft.cropQuad)
                                            if (draft.rotation != 0) {
                                                imageToPdfViewModel.rotateItem(addedId, draft.rotation)
                                            }
                                            imageToPdfViewModel.updateFilter(addedId, ImageFilterType.DOCUMENT)
                                        }
                                    }
                                    onFinishToStudio()
                                }
                            }
                        },
                        enabled = scannedPages.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = DocuRed, contentColor = PureWhite),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("scanner_next_btn")
                    ) {
                        Text("Next (${scannedPages.size})")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
