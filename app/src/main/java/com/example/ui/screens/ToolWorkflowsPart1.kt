package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.PdfDocumentEntity
import com.example.data.local.PdfRepository
import com.example.domain.model.CompressionLevel
import com.example.domain.model.PdfToolType
import com.example.domain.util.FormatUtils
import com.example.ui.theme.DocuRed
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SuccessGreen
import com.example.ui.viewmodel.PdfToolsViewModel
import com.example.ui.viewmodel.ToolResult
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolWorkflowScreen(
    toolType: PdfToolType,
    viewModel: PdfToolsViewModel,
    repository: PdfRepository,
    onBack: () -> Unit,
    onOpenReader: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val allDocs by viewModel.allDocuments.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(toolType) {
        viewModel.clearResultAndError()
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearResultAndError()
        }
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
                            text = toolType.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = toolType.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("tool_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.result != null) {
                ToolCompletionCard(
                    result = state.result!!,
                    repository = repository,
                    onOpenReader = onOpenReader,
                    onDone = {
                        viewModel.clearResultAndError()
                        onBack()
                    }
                )
            } else {
                when (toolType) {
                    PdfToolType.MERGE_PDF -> MergePdfWorkflow(allDocs, state.isProcessing, state.progressCurrent, state.progressTotal, viewModel)
                    PdfToolType.COMPRESS_PDF -> CompressPdfWorkflow(allDocs, state.isProcessing, viewModel)
                    PdfToolType.PDF_TO_JPG -> PdfToJpgWorkflow(allDocs, state.isProcessing, viewModel)
                    PdfToolType.ROTATE_PDF -> RotatePdfWorkflow(allDocs, state.isProcessing, viewModel)
                    PdfToolType.REORDER_PAGES -> ReorderPdfWorkflow(allDocs, state.isProcessing, viewModel)
                    PdfToolType.ADD_TEXT -> AddTextWorkflow(allDocs, state.isProcessing, viewModel)
                    PdfToolType.DRAW_ON_PDF -> DrawOnPdfWorkflow(allDocs, state.isProcessing, viewModel)
                    PdfToolType.ADD_SIGNATURE -> AddSignatureWorkflow(allDocs, state.savedSignatures, state.isProcessing, viewModel)
                    else -> Unit
                }
            }

            if (state.isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp)
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Text(
                                text = "Processing locally...",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            val frac = if (state.progressTotal > 0) {
                                state.progressCurrent.toFloat() / state.progressTotal.toFloat()
                            } else 0f
                            LinearProgressIndicator(
                                progress = { frac },
                                color = DocuRed,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Page ${state.progressCurrent} of ${state.progressTotal}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SinglePdfPickerStep(
    allDocs: List<PdfDocumentEntity>,
    promptTitle: String,
    viewModel: PdfToolsViewModel,
    onPicked: (PdfDocumentEntity) -> Unit
) {
    val scope = rememberCoroutineScope()
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                viewModel.importExternalPdfToLibrary(uri)?.let(onPicked)
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = promptTitle,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/pdf")) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("tool_pick_external_pdf")
            ) {
                Icon(Icons.Filled.FileOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Browse Device Files (SAF)")
            }
        }

        if (allDocs.isEmpty()) {
            item {
                Text(
                    text = "No PDFs in your library yet. Tap Browse Device Files above to select any PDF.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        } else {
            items(allDocs, key = { it.id }) { doc ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPicked(doc) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, tint = DocuRed)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = doc.fileName,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${doc.pageCount} pages • ${FormatUtils.formatFileSize(doc.fileSizeBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolCompletionCard(
    result: ToolResult,
    repository: PdfRepository,
    onOpenReader: (Long) -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = SuccessGreen,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Completed!",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = result.summaryMessage,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        result.document?.let { doc ->
            Button(
                onClick = { onOpenReader(doc.id) },
                colors = ButtonDefaults.buttonColors(containerColor = DocuRed, contentColor = PureWhite),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(Icons.Filled.Description, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open in PDF Reader")
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    val intent = repository.getShareIntent(File(doc.filePath))
                    context.startActivity(Intent.createChooser(intent, "Share PDF"))
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Filled.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share PDF")
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        TextButton(onClick = onDone) {
            Text("Done")
        }
    }
}

@Composable
private fun MergePdfWorkflow(
    allDocs: List<PdfDocumentEntity>,
    isProcessing: Boolean,
    progressCurrent: Int,
    progressTotal: Int,
    viewModel: PdfToolsViewModel
) {
    val scope = rememberCoroutineScope()
    val selectedDocs = remember { mutableStateListOf<PdfDocumentEntity>() }
    var outputName by remember { mutableStateOf(FormatUtils.defaultPdfName("Merged")) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                uris.forEach { uri ->
                    viewModel.importExternalPdfToLibrary(uri)?.let { selectedDocs.add(it) }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Selected (${selectedDocs.size}) — min 2",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/pdf")) },
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add External")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (selectedDocs.isNotEmpty()) {
            selectedDocs.forEachIndexed { idx, doc ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, DocuRed.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${idx + 1}.",
                            style = MaterialTheme.typography.labelLarge,
                            color = DocuRed
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = doc.fileName,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                if (idx > 0) {
                                    val moved = selectedDocs.removeAt(idx)
                                    selectedDocs.add(idx - 1, moved)
                                }
                            },
                            enabled = idx > 0
                        ) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = {
                                if (idx < selectedDocs.lastIndex) {
                                    val moved = selectedDocs.removeAt(idx)
                                    selectedDocs.add(idx + 1, moved)
                                }
                            },
                            enabled = idx < selectedDocs.lastIndex
                        ) {
                            Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { selectedDocs.removeAt(idx) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text("Tap to add from library:", style = MaterialTheme.typography.labelLarge)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(allDocs, key = { it.id }) { doc ->
                val alreadyAdded = selectedDocs.any { it.id == doc.id }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { if (!alreadyAdded) selectedDocs.add(doc) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, tint = DocuRed)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "${doc.fileName} (${doc.pageCount}p)",
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (alreadyAdded) {
                            Text("Added", style = MaterialTheme.typography.labelSmall, color = DocuRed)
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = outputName,
            onValueChange = { outputName = it },
            label = { Text("Merged Filename") },
            suffix = { Text(".pdf") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { viewModel.mergePdfs(selectedDocs.map { File(it.filePath) }, outputName) },
            enabled = selectedDocs.size >= 2 && !isProcessing,
            colors = ButtonDefaults.buttonColors(containerColor = DocuRed, contentColor = PureWhite),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("Merge ${selectedDocs.size} PDFs")
        }
    }
}

@Composable
private fun CompressPdfWorkflow(
    allDocs: List<PdfDocumentEntity>,
    isProcessing: Boolean,
    viewModel: PdfToolsViewModel
) {
    var selectedDoc by remember { mutableStateOf<PdfDocumentEntity?>(null) }
    var selectedLevel by remember { mutableStateOf(CompressionLevel.MEDIUM) }
    var outputName by remember { mutableStateOf(FormatUtils.defaultPdfName("Compressed")) }

    val doc = selectedDoc
    if (doc == null) {
        SinglePdfPickerStep(allDocs, "Select a PDF to Compress", viewModel) {
            selectedDoc = it
            outputName = "${it.fileName.removeSuffix(".pdf")}_compressed"
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, tint = DocuRed)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(doc.fileName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "Original: ${FormatUtils.formatFileSize(doc.fileSizeBytes)} • ${doc.pageCount} pages",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { selectedDoc = null }) { Text("Change") }
                }
            }

            Text("Compression Level", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            CompressionLevel.entries.forEach { level ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedLevel = level },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        if (selectedLevel == level) 2.dp else 1.dp,
                        if (selectedLevel == level) DocuRed else MaterialTheme.colorScheme.outlineVariant
                    ),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedLevel == level,
                            onClick = { selectedLevel = level },
                            colors = RadioButtonDefaults.colors(selectedColor = DocuRed)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(level.label, fontWeight = FontWeight.SemiBold)
                            Text(level.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
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

            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = { viewModel.compressPdf(File(doc.filePath), selectedLevel, outputName) },
                enabled = !isProcessing,
                colors = ButtonDefaults.buttonColors(containerColor = DocuRed, contentColor = PureWhite),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("Compress PDF")
            }
        }
    }
}

@Composable
private fun PdfToJpgWorkflow(
    allDocs: List<PdfDocumentEntity>,
    isProcessing: Boolean,
    viewModel: PdfToolsViewModel
) {
    val context = LocalContext.current
    var selectedDoc by remember { mutableStateOf<PdfDocumentEntity?>(null) }
    val selectedPages = remember { mutableStateMapOf<Int, Boolean>() }

    val doc = selectedDoc
    if (doc == null) {
        SinglePdfPickerStep(allDocs, "Select a PDF to Export as JPG", viewModel) {
            selectedDoc = it
            selectedPages.clear()
            for (i in 0 until it.pageCount) selectedPages[i] = true
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${doc.fileName} (${doc.pageCount}p)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    val allSelected = (0 until doc.pageCount).all { selectedPages[it] == true }
                    for (i in 0 until doc.pageCount) selectedPages[i] = !allSelected
                }) {
                    Text("Toggle All", color = DocuRed)
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                items(doc.pageCount) { pageIdx ->
                    var thumb by remember(doc.id, pageIdx) { mutableStateOf<Bitmap?>(null) }
                    LaunchedEffect(doc.id, pageIdx) {
                        thumb = viewModel.renderPagePreview(File(doc.filePath), pageIdx, 360)
                    }
                    val checked = selectedPages[pageIdx] == true
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedPages[pageIdx] = !checked },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(
                            if (checked) 2.dp else 1.dp,
                            if (checked) DocuRed else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.75f)
                                .background(PureWhite)
                        ) {
                            thumb?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = "Page ${pageIdx + 1}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { selectedPages[pageIdx] = it },
                                colors = CheckboxDefaults.colors(checkedColor = DocuRed),
                                modifier = Modifier.align(Alignment.TopEnd)
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.secondary,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(6.dp)
                            ) {
                                Text(
                                    text = "Page ${pageIdx + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            val chosenIndices = (0 until doc.pageCount).filter { selectedPages[it] == true }
            Button(
                onClick = {
                    viewModel.exportPdfToJpg(
                        context = context,
                        source = File(doc.filePath),
                        pageIndices = chosenIndices,
                        baseName = doc.fileName.removeSuffix(".pdf")
                    )
                },
                enabled = chosenIndices.isNotEmpty() && !isProcessing,
                colors = ButtonDefaults.buttonColors(containerColor = DocuRed, contentColor = PureWhite),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Export ${chosenIndices.size} JPG(s) to Gallery")
            }
        }
    }
}

@Composable
private fun RotatePdfWorkflow(
    allDocs: List<PdfDocumentEntity>,
    isProcessing: Boolean,
    viewModel: PdfToolsViewModel
) {
    var selectedDoc by remember { mutableStateOf<PdfDocumentEntity?>(null) }
    val rotations = remember { mutableStateMapOf<Int, Int>() }
    var outputName by remember { mutableStateOf("") }

    val doc = selectedDoc
    if (doc == null) {
        SinglePdfPickerStep(allDocs, "Select a PDF to Rotate", viewModel) {
            selectedDoc = it
            rotations.clear()
            outputName = "${it.fileName.removeSuffix(".pdf")}_rotated"
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tap a page to rotate +90°", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = {
                    for (i in 0 until doc.pageCount) {
                        rotations[i] = ((rotations[i] ?: 0) + 90) % 360
                    }
                }) {
                    Text("Rotate All +90°", color = DocuRed, fontWeight = FontWeight.Bold)
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(doc.pageCount) { pageIdx ->
                    var thumb by remember(doc.id, pageIdx) { mutableStateOf<Bitmap?>(null) }
                    LaunchedEffect(doc.id, pageIdx) {
                        thumb = viewModel.renderPagePreview(File(doc.filePath), pageIdx, 360)
                    }
                    val deg = rotations[pageIdx] ?: 0
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { rotations[pageIdx] = (deg + 90) % 360 },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (deg != 0) DocuRed else MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.8f)
                                    .background(PureWhite),
                                contentAlignment = Alignment.Center
                            ) {
                                thumb?.let {
                                    Image(
                                        bitmap = it.asImageBitmap(),
                                        contentDescription = "Page ${pageIdx + 1}",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer { rotationZ = deg.toFloat() },
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = DocuRed)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Page ${pageIdx + 1} ($deg°)", style = MaterialTheme.typography.labelSmall)
                            }
                        }
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
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = { viewModel.rotatePdf(File(doc.filePath), rotations.toMap(), outputName) },
                enabled = !isProcessing,
                colors = ButtonDefaults.buttonColors(containerColor = DocuRed, contentColor = PureWhite),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Save Rotated PDF")
            }
        }
    }
}

@Composable
private fun ReorderPdfWorkflow(
    allDocs: List<PdfDocumentEntity>,
    isProcessing: Boolean,
    viewModel: PdfToolsViewModel
) {
    var selectedDoc by remember { mutableStateOf<PdfDocumentEntity?>(null) }
    val pageOrder = remember { mutableStateListOf<Int>() }
    var outputName by remember { mutableStateOf("") }

    val doc = selectedDoc
    if (doc == null) {
        SinglePdfPickerStep(allDocs, "Select a PDF to Reorder Pages", viewModel) {
            selectedDoc = it
            pageOrder.clear()
            for (i in 0 until it.pageCount) pageOrder.add(i)
            outputName = "${it.fileName.removeSuffix(".pdf")}_reordered"
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Text(
                text = "Reorder or remove pages (${pageOrder.size} remaining)",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(pageOrder, key = { idx, pageIdx -> "${pageIdx}_$idx" }) { listIdx, origPageIdx ->
                    var thumb by remember(doc.id, origPageIdx) { mutableStateOf<Bitmap?>(null) }
                    LaunchedEffect(doc.id, origPageIdx) {
                        thumb = viewModel.renderPagePreview(File(doc.filePath), origPageIdx, 200)
                    }
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp, 68.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PureWhite)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            ) {
                                thumb?.let {
                                    Image(
                                        bitmap = it.asImageBitmap(),
                                        contentDescription = "Page ${origPageIdx + 1}",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Position ${listIdx + 1}", fontWeight = FontWeight.Bold)
                                Text(
                                    "Original Page ${origPageIdx + 1}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (listIdx > 0) {
                                        val moved = pageOrder.removeAt(listIdx)
                                        pageOrder.add(listIdx - 1, moved)
                                    }
                                },
                                enabled = listIdx > 0
                            ) {
                                Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up")
                            }
                            IconButton(
                                onClick = {
                                    if (listIdx < pageOrder.lastIndex) {
                                        val moved = pageOrder.removeAt(listIdx)
                                        pageOrder.add(listIdx + 1, moved)
                                    }
                                },
                                enabled = listIdx < pageOrder.lastIndex
                            ) {
                                Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down")
                            }
                            IconButton(
                                onClick = { if (pageOrder.size > 1) pageOrder.removeAt(listIdx) },
                                enabled = pageOrder.size > 1
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove page", tint = DocuRed)
                            }
                        }
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
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = { viewModel.reorderPdf(File(doc.filePath), pageOrder.toList(), outputName) },
                enabled = pageOrder.isNotEmpty() && !isProcessing,
                colors = ButtonDefaults.buttonColors(containerColor = DocuRed, contentColor = PureWhite),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Save Reordered PDF")
            }
        }
    }
}
