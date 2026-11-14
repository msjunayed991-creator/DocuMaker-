package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.PdfDocumentEntity
import com.example.data.local.PdfRepository
import com.example.domain.util.FormatUtils
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.DocumentListCard
import com.example.ui.components.EmptyDocumentsPlaceholder
import com.example.ui.components.RenamePdfDialog
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.DocuRed
import com.example.ui.theme.DocuRedDark
import com.example.ui.theme.InkBlack
import com.example.ui.theme.PureWhite
import com.example.ui.viewmodel.FilesViewModel
import com.example.ui.viewmodel.HomeViewModel
import java.io.File

private data class QuickActionItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentRed: Boolean,
    val testTag: String,
    val onClick: () -> Unit
)

@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel,
    filesViewModel: FilesViewModel,
    repository: PdfRepository,
    onCreatePdf: () -> Unit,
    onImageToPdf: () -> Unit,
    onCameraScanner: () -> Unit,
    onOpenReader: (Long) -> Unit,
    onOpenTools: () -> Unit,
    onOpenAllFiles: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by homeViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var docToRename by remember { mutableStateOf<PdfDocumentEntity?>(null) }
    var docToDelete by remember { mutableStateOf<PdfDocumentEntity?>(null) }
    var docToExport by remember { mutableStateOf<PdfDocumentEntity?>(null) }

    val importPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            homeViewModel.importExternalPdf(uri) { doc ->
                onOpenReader(doc.id)
            }
        }
    }

    val exportSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        val doc = docToExport
        if (destUri != null && doc != null) {
            filesViewModel.exportPdf(doc, destUri)
        }
        docToExport = null
    }

    val quickActions = remember(
        onCreatePdf,
        onImageToPdf,
        onCameraScanner,
        onOpenTools
    ) {
        listOf(
            QuickActionItem(
                title = "Create PDF",
                subtitle = "Start new",
                icon = Icons.Filled.AddCircle,
                accentRed = true,
                testTag = "home_quick_create_pdf",
                onClick = onCreatePdf
            ),
            QuickActionItem(
                title = "Image to PDF",
                subtitle = "Gallery photos",
                icon = Icons.Filled.PhotoLibrary,
                accentRed = false,
                testTag = "home_quick_image_to_pdf",
                onClick = onImageToPdf
            ),
            QuickActionItem(
                title = "Scanner",
                subtitle = "Camera scan",
                icon = Icons.Filled.CameraAlt,
                accentRed = true,
                testTag = "home_quick_scanner",
                onClick = onCameraScanner
            ),
            QuickActionItem(
                title = "PDF Reader",
                subtitle = "Open & view",
                icon = Icons.Filled.Description,
                accentRed = false,
                testTag = "home_quick_reader",
                onClick = { importPdfLauncher.launch(arrayOf("application/pdf")) }
            ),
            QuickActionItem(
                title = "PDF Tools",
                subtitle = "9 offline tools",
                icon = Icons.Filled.Build,
                accentRed = false,
                testTag = "home_quick_tools",
                onClick = onOpenTools
            )
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Header Brand Bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(DocuRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PictureAsPdf,
                            contentDescription = "DocuMaker Logo",
                            tint = PureWhite,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "DocuMaker",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${state.totalDocCount} files • ${FormatUtils.formatFileSize(state.totalStorageBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = "Offline privacy",
                            tint = DocuRed,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Offline",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Studio Hero Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = InkBlack),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(InkBlack, CharcoalSurface, Color(0xFF2B0E11))
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Surface(
                            color = DocuRed.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, DocuRed.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "PDF WORKSPACE",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = PureWhite,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Turn photos & scans into crisp PDFs",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = PureWhite
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Real filters, 4-corner perspective dewarp, page layout control & 9 offline PDF tools.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PureWhite.copy(alpha = 0.78f)
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onImageToPdf,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DocuRed,
                                    contentColor = PureWhite
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("hero_image_to_pdf_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PhotoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Image to PDF", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = onCameraScanner,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PureWhite),
                                border = BorderStroke(1.dp, PureWhite.copy(alpha = 0.35f)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("hero_scan_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CameraAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Scan Doc", maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        // Quick Actions Horizontal Strip
        item {
            Column {
                Text(
                    text = "Quick Actions",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(quickActions) { item ->
                        Card(
                            modifier = Modifier
                                .width(122.dp)
                                .clickable(onClick = item.onClick)
                                .testTag(item.testTag),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (item.accentRed) DocuRed else MaterialTheme.colorScheme.secondaryContainer
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.title,
                                        tint = if (item.accentRed) PureWhite else MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // Tools Banner Shortcut
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clickable(onClick = onOpenTools)
                    .testTag("home_tools_banner"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Build,
                            contentDescription = "PDF Tools",
                            tint = DocuRedDark
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "All PDF Tools",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Merge, Compress, PDF to JPG, Rotate, Reorder, Sign & Draw",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Recent Documents Section
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Recent Documents",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { importPdfLauncher.launch(arrayOf("application/pdf")) }) {
                        Icon(
                            imageVector = Icons.Filled.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import")
                    }
                    TextButton(onClick = onOpenAllFiles) {
                        Text("See All", color = DocuRed)
                    }
                }
            }
        }

        if (state.recentDocuments.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    EmptyDocumentsPlaceholder(
                        title = "Your PDF studio is ready",
                        subtitle = "Create your first PDF from gallery photos, scan a document, or import an existing PDF.",
                        actionLabel = "Create First PDF",
                        onAction = onImageToPdf
                    )
                }
            }
        } else {
            items(state.recentDocuments, key = { it.id }) { doc ->
                DocumentListCard(
                    document = doc,
                    onOpen = { onOpenReader(doc.id) },
                    onToggleFavorite = { homeViewModel.toggleFavorite(doc) },
                    onShare = {
                        val intent = repository.getShareIntent(File(doc.filePath))
                        context.startActivity(android.content.Intent.createChooser(intent, "Share PDF"))
                    },
                    onRename = { docToRename = doc },
                    onSaveCopy = {
                        docToExport = doc
                        exportSafLauncher.launch(doc.fileName)
                    },
                    onDelete = { docToDelete = doc },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }
    }

    docToRename?.let { target ->
        RenamePdfDialog(
            initialName = target.fileName,
            onDismiss = { docToRename = null },
            onConfirm = { newName ->
                filesViewModel.renameDocument(target, newName)
                docToRename = null
            }
        )
    }

    docToDelete?.let { target ->
        DeleteConfirmDialog(
            fileName = target.fileName,
            onDismiss = { docToDelete = null },
            onConfirm = {
                filesViewModel.deleteDocument(target)
                docToDelete = null
            }
        )
    }
}
