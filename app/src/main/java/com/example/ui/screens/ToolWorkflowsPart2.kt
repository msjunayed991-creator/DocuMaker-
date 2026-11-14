package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.data.local.PdfDocumentEntity
import com.example.data.processing.PdfDrawStroke
import com.example.data.processing.PdfTextOverlay
import com.example.ui.theme.DocuRed
import com.example.ui.theme.InkBlack
import com.example.ui.theme.PureWhite
import com.example.ui.viewmodel.PdfToolsViewModel
import kotlinx.coroutines.launch
import java.io.File

private val InkColors = listOf(
    InkBlack,
    DocuRed,
    Color(0xFF1D4ED8),
    Color(0xFF15803D)
)

@Composable
internal fun AddTextWorkflow(
    allDocs: List<PdfDocumentEntity>,
    isProcessing: Boolean,
    viewModel: PdfToolsViewModel
) {
    var selectedDoc by remember { mutableStateOf<PdfDocumentEntity?>(null) }
    var activePage by remember { mutableIntStateOf(0) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var inputText by remember { mutableStateOf("Approved") }
    var fontSize by remember { mutableFloatStateOf(18f) }
    var isBold by remember { mutableStateOf(true) }
    var textColor by remember { mutableStateOf(DocuRed) }
    var normX by remember { mutableFloatStateOf(0.25f) }
    var normY by remember { mutableFloatStateOf(0.50f) }
    val overlays = remember { mutableStateListOf<PdfTextOverlay>() }

    val doc = selectedDoc
    LaunchedEffect(doc?.id, activePage) {
        if (doc != null) {
            pageBitmap = viewModel.renderPagePreview(File(doc.filePath), activePage, 900)
        }
    }

    if (doc == null) {
        SinglePdfPickerStep(allDocs, "Select a PDF to Add Text", viewModel) {
            selectedDoc = it
            activePage = 0
            overlays.clear()
        }
    } else {
        var outputName by remember(doc.id) { mutableStateOf("${doc.fileName.removeSuffix(".pdf")}_text") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (doc.pageCount > 1) {
                PageSelectorStrip(
                    pageCount = doc.pageCount,
                    activePage = activePage,
                    onSelectPage = { activePage = it }
                )
            }

            Text(
                text = "Tap anywhere on the page to position text",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(
                        pageBitmap?.let { it.width.toFloat() / it.height.toFloat().coerceAtLeast(1f) } ?: 0.72f
                    )
                    .clip(RoundedCornerShape(10.dp))
                    .background(PureWhite)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            normX = (offset.x / size.width.toFloat()).coerceIn(0.03f, 0.88f)
                            normY = (offset.y / size.height.toFloat()).coerceIn(0.06f, 0.95f)
                        }
                    }
            ) {
                pageBitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "PDF page preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )
                }

                // Draw placed overlays + active marker
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = DocuRed,
                        radius = 12f,
                        center = Offset(normX * size.width, normY * size.height)
                    )
                    overlays.filter { it.pageIndex == activePage }.forEach { ov ->
                        drawCircle(
                            color = Color(ov.colorInt),
                            radius = 9f,
                            center = Offset(ov.normX * size.width, ov.normY * size.height)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("Text to Stamp") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InkColors.forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    width = if (textColor == c) 3.dp else 1.dp,
                                    color = if (textColor == c) PureWhite else Color.Gray,
                                    shape = CircleShape
                                )
                                .clickable { textColor = c }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Bold", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = isBold,
                        onCheckedChange = { isBold = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = PureWhite, checkedTrackColor = DocuRed)
                    )
                }
            }

            Text("Size: ${fontSize.toInt()} pt", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = fontSize,
                onValueChange = { fontSize = it },
                valueRange = 10f..42f,
                colors = SliderDefaults.colors(thumbColor = DocuRed, activeTrackColor = DocuRed)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            overlays.add(
                                PdfTextOverlay(
                                    pageIndex = activePage,
                                    text = inputText.trim(),
                                    normX = normX,
                                    normY = normY,
                                    fontSizeSp = fontSize,
                                    colorInt = textColor.toArgb(),
                                    isBold = isBold
                                )
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Place (${overlays.size})")
                }
                if (overlays.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { overlays.clear() },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Clear")
                    }
                }
            }

            OutlinedTextField(
                value = outputName,
                onValueChange = { outputName = it },
                label = { Text("Output Filename") },
                suffix = { Text(".pdf") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val toApply = if (overlays.isNotEmpty()) {
                        overlays.toList()
                    } else if (inputText.isNotBlank()) {
                        listOf(
                            PdfTextOverlay(
                                pageIndex = activePage,
                                text = inputText.trim(),
                                normX = normX,
                                normY = normY,
                                fontSizeSp = fontSize,
                                colorInt = textColor.toArgb(),
                                isBold = isBold
                            )
                        )
                    } else emptyList()
                    viewModel.addTextToPdf(File(doc.filePath), toApply, outputName)
                },
                enabled = !isProcessing && (overlays.isNotEmpty() || inputText.isNotBlank()),
                colors = ButtonDefaults.buttonColors(containerColor = DocuRed, contentColor = PureWhite),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Save PDF with Text")
            }
        }
    }
}

@Composable
internal fun DrawOnPdfWorkflow(
    allDocs: List<PdfDocumentEntity>,
    isProcessing: Boolean,
    viewModel: PdfToolsViewModel
) {
    var selectedDoc by remember { mutableStateOf<PdfDocumentEntity?>(null) }
    var activePage by remember { mutableIntStateOf(0) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var penColor by remember { mutableStateOf(DocuRed) }
    var strokeNorm by remember { mutableFloatStateOf(0.008f) }
    val strokes = remember { mutableStateListOf<PdfDrawStroke>() }
    var currentPoints by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }

    val doc = selectedDoc
    LaunchedEffect(doc?.id, activePage) {
        if (doc != null) {
            pageBitmap = viewModel.renderPagePreview(File(doc.filePath), activePage, 900)
        }
    }

    if (doc == null) {
        SinglePdfPickerStep(allDocs, "Select a PDF to Draw On", viewModel) {
            selectedDoc = it
            activePage = 0
            strokes.clear()
        }
    } else {
        var outputName by remember(doc.id) { mutableStateOf("${doc.fileName.removeSuffix(".pdf")}_marked") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (doc.pageCount > 1) {
                PageSelectorStrip(doc.pageCount, activePage) {
                    activePage = it
                    strokes.clear()
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InkColors.forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    width = if (penColor == c) 3.dp else 1.dp,
                                    color = if (penColor == c) PureWhite else Color.Gray,
                                    shape = CircleShape
                                )
                                .clickable { penColor = c }
                        )
                    }
                }
                Row {
                    IconButton(
                        onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) },
                        enabled = strokes.isNotEmpty()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo stroke")
                    }
                    IconButton(
                        onClick = { strokes.clear() },
                        enabled = strokes.isNotEmpty()
                    ) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear drawing")
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(PureWhite)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    .pointerInput(penColor, strokeNorm) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val nx = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                val ny = (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                                currentPoints = listOf(nx to ny)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val nx = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                val ny = (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                                currentPoints = currentPoints + (nx to ny)
                            },
                            onDragEnd = {
                                if (currentPoints.size >= 2) {
                                    strokes.add(PdfDrawStroke(currentPoints, penColor.toArgb(), strokeNorm))
                                }
                                currentPoints = emptyList()
                            },
                            onDragCancel = { currentPoints = emptyList() }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                pageBitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Draw canvas",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val drawOne: (List<Pair<Float, Float>>, Color, Float) -> Unit = { pts, color, wNorm ->
                        if (pts.size >= 2) {
                            val p = Path().apply {
                                moveTo(pts.first().first * size.width, pts.first().second * size.height)
                                pts.drop(1).forEach { (nx, ny) -> lineTo(nx * size.width, ny * size.height) }
                            }
                            drawPath(
                                path = p,
                                color = color,
                                style = Stroke(
                                    width = wNorm * size.width,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }
                    strokes.forEach { drawOne(it.points, Color(it.colorInt), it.strokeWidthNorm) }
                    if (currentPoints.size >= 2) drawOne(currentPoints, penColor, strokeNorm)
                }
            }

            OutlinedTextField(
                value = outputName,
                onValueChange = { outputName = it },
                label = { Text("Output Filename") },
                suffix = { Text(".pdf") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { viewModel.drawOnPdf(File(doc.filePath), activePage, strokes.toList(), outputName) },
                enabled = strokes.isNotEmpty() && !isProcessing,
                colors = ButtonDefaults.buttonColors(containerColor = DocuRed, contentColor = PureWhite),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Save Marked PDF")
            }
        }
    }
}

@Composable
internal fun AddSignatureWorkflow(
    allDocs: List<PdfDocumentEntity>,
    savedSignatures: List<File>,
    isProcessing: Boolean,
    viewModel: PdfToolsViewModel
) {
    val scope = rememberCoroutineScope()
    var selectedDoc by remember { mutableStateOf<PdfDocumentEntity?>(null) }
    var activePage by remember { mutableIntStateOf(0) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var activeSigBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showSignaturePad by remember { mutableStateOf(savedSignatures.isEmpty()) }

    var sigNormX by remember { mutableFloatStateOf(0.5f) }
    var sigNormY by remember { mutableFloatStateOf(0.75f) }
    var sigWidthNorm by remember { mutableFloatStateOf(0.35f) }

    val doc = selectedDoc
    LaunchedEffect(doc?.id, activePage) {
        if (doc != null) {
            pageBitmap = viewModel.renderPagePreview(File(doc.filePath), activePage, 900)
        }
    }

    if (doc == null) {
        SinglePdfPickerStep(allDocs, "Select a PDF to Sign", viewModel) {
            selectedDoc = it
            activePage = 0
        }
    } else if (showSignaturePad || activeSigBitmap == null) {
        SignaturePadPanel(
            savedSignatures = savedSignatures,
            viewModel = viewModel,
            onSelectSaved = { file ->
                scope.launch {
                    activeSigBitmap = viewModel.loadSignatureBitmap(file)
                    showSignaturePad = false
                }
            },
            onCreatedSignature = { bmp ->
                viewModel.saveSignatureBitmap(bmp)
                activeSigBitmap = bmp
                showSignaturePad = false
            }
        )
    } else {
        var outputName by remember(doc.id) { mutableStateOf("${doc.fileName.removeSuffix(".pdf")}_signed") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (doc.pageCount > 1) {
                PageSelectorStrip(doc.pageCount, activePage) { activePage = it }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tap or drag on page to place signature", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { showSignaturePad = true }) {
                    Text("Change Signature", color = DocuRed)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(
                        pageBitmap?.let { it.width.toFloat() / it.height.toFloat().coerceAtLeast(1f) } ?: 0.72f
                    )
                    .clip(RoundedCornerShape(10.dp))
                    .background(PureWhite)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            sigNormX = (offset.x / size.width.toFloat()).coerceIn(0.15f, 0.85f)
                            sigNormY = (offset.y / size.height.toFloat()).coerceIn(0.10f, 0.90f)
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            sigNormX = (change.position.x / size.width.toFloat()).coerceIn(0.15f, 0.85f)
                            sigNormY = (change.position.y / size.height.toFloat()).coerceIn(0.10f, 0.90f)
                        }
                    }
            ) {
                pageBitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "PDF page to sign",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )
                }

                // Visual signature placement box
                val sigBmp = activeSigBitmap
                if (sigBmp != null) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = sigWidthNorm * size.width
                        val aspect = sigBmp.height.toFloat() / sigBmp.width.toFloat().coerceAtLeast(1f)
                        val h = w * aspect
                        val left = sigNormX * size.width - w / 2f
                        val top = sigNormY * size.height - h / 2f
                        drawImage(
                            image = sigBmp.asImageBitmap(),
                            dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()),
                            dstSize = IntSize(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1))
                        )
                        drawRect(
                            color = DocuRed,
                            topLeft = Offset(left, top),
                            size = androidx.compose.ui.geometry.Size(w, h),
                            style = Stroke(width = 3f)
                        )
                    }
                }
            }

            Text("Signature Size: ${(sigWidthNorm * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = sigWidthNorm,
                onValueChange = { sigWidthNorm = it },
                valueRange = 0.16f..0.65f,
                colors = SliderDefaults.colors(thumbColor = DocuRed, activeTrackColor = DocuRed)
            )

            OutlinedTextField(
                value = outputName,
                onValueChange = { outputName = it },
                label = { Text("Output Filename") },
                suffix = { Text(".pdf") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    activeSigBitmap?.let { sigBmp ->
                        viewModel.applySignatureToPdf(
                            source = File(doc.filePath),
                            pageIndex = activePage,
                            signatureBitmap = sigBmp,
                            normX = sigNormX,
                            normY = sigNormY,
                            normWidth = sigWidthNorm,
                            outputBaseName = outputName
                        )
                    }
                },
                enabled = activeSigBitmap != null && !isProcessing,
                colors = ButtonDefaults.buttonColors(containerColor = DocuRed, contentColor = PureWhite),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Save Signed PDF")
            }
        }
    }
}

@Composable
private fun SignaturePadPanel(
    savedSignatures: List<File>,
    viewModel: PdfToolsViewModel,
    onSelectSaved: (File) -> Unit,
    onCreatedSignature: (Bitmap) -> Unit
) {
    var inkColor by remember { mutableStateOf(InkBlack) }
    val strokes = remember { mutableStateListOf<List<Pair<Float, Float>>>() }
    var activeStroke by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize(600, 300)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (savedSignatures.isNotEmpty()) {
            Text("Saved Signatures", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(savedSignatures, key = { it.absolutePath }) { sigFile ->
                    var thumb by remember(sigFile.absolutePath) { mutableStateOf<Bitmap?>(null) }
                    LaunchedEffect(sigFile.absolutePath) {
                        thumb = viewModel.loadSignatureBitmap(sigFile)
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = PureWhite,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .size(120.dp, 64.dp)
                            .clickable { onSelectSaved(sigFile) }
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            thumb?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = "Saved signature",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(6.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            IconButton(
                                onClick = { viewModel.deleteSignatureFile(sigFile) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(22.dp)
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete signature", tint = DocuRed, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Draw New Signature", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InkColors.take(3).forEach { c ->
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(if (inkColor == c) 2.5.dp else 1.dp, PureWhite, CircleShape)
                            .clickable { inkColor = c }
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(PureWhite)
                .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            activeStroke = listOf(
                                (offset.x / size.width.toFloat()).coerceIn(0f, 1f) to
                                    (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                            )
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            activeStroke = activeStroke + (
                                (change.position.x / size.width.toFloat()).coerceIn(0f, 1f) to
                                    (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                                )
                        },
                        onDragEnd = {
                            if (activeStroke.size >= 2) strokes.add(activeStroke)
                            activeStroke = emptyList()
                        },
                        onDragCancel = { activeStroke = emptyList() }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val drawStroke: (List<Pair<Float, Float>>) -> Unit = { pts ->
                    if (pts.size >= 2) {
                        val path = Path().apply {
                            moveTo(pts.first().first * size.width, pts.first().second * size.height)
                            pts.drop(1).forEach { (x, y) -> lineTo(x * size.width, y * size.height) }
                        }
                        drawPath(
                            path = path,
                            color = inkColor,
                            style = Stroke(width = 7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }
                strokes.forEach(drawStroke)
                drawStroke(activeStroke)
            }

            if (strokes.isEmpty() && activeStroke.isEmpty()) {
                Text(
                    text = "Sign here with your finger",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { strokes.clear() },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Clear")
            }
            Button(
                onClick = {
                    val bmpW = canvasSize.width.coerceAtLeast(480)
                    val bmpH = canvasSize.height.coerceAtLeast(220)
                    val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(AndroidColor.TRANSPARENT)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = inkColor.toArgb()
                        style = Paint.Style.STROKE
                        strokeWidth = 9f
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                    }
                    strokes.forEach { pts ->
                        if (pts.size >= 2) {
                            val path = AndroidPath()
                            path.moveTo(pts.first().first * bmpW, pts.first().second * bmpH)
                            pts.drop(1).forEach { (x, y) -> path.lineTo(x * bmpW, y * bmpH) }
                            canvas.drawPath(path, paint)
                        }
                    }
                    onCreatedSignature(bitmap)
                },
                enabled = strokes.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = DocuRed, contentColor = PureWhite),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Use Signature")
            }
        }
    }
}

@Composable
private fun PageSelectorStrip(
    pageCount: Int,
    activePage: Int,
    onSelectPage: (Int) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 2.dp)
    ) {
        items(pageCount) { idx ->
            FilterChip(
                selected = idx == activePage,
                onClick = { onSelectPage(idx) },
                label = { Text("Page ${idx + 1}") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = DocuRed,
                    selectedLabelColor = PureWhite
                )
            )
        }
    }
}
