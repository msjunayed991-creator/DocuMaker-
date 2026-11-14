package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.PdfDocumentEntity
import com.example.data.local.PdfRepository
import com.example.domain.model.FileSortOption
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.DocumentListCard
import com.example.ui.components.EmptyDocumentsPlaceholder
import com.example.ui.components.RenamePdfDialog
import com.example.ui.theme.DocuRed
import com.example.ui.theme.PureWhite
import com.example.ui.viewmodel.FilesTab
import com.example.ui.viewmodel.FilesViewModel
import java.io.File

@Composable
fun FilesScreen(
    viewModel: FilesViewModel,
    repository: PdfRepository,
    onOpenReader: (Long) -> Unit,
    onCreatePdf: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var sortMenuExpanded by remember { mutableStateOf(false) }
    var docToRename by remember { mutableStateOf<PdfDocumentEntity?>(null) }
    var docToDelete by remember { mutableStateOf<PdfDocumentEntity?>(null) }
    var docToExport by remember { mutableStateOf<PdfDocumentEntity?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importPdf(uri)
    }

    val exportSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        val doc = docToExport
        if (destUri != null && doc != null) {
            viewModel.exportPdf(doc, destUri)
        }
        docToExport = null
    }

    LaunchedEffect(state.feedbackMessage) {
        state.feedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            // Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "My PDF Files",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${state.totalCount} saved locally",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/pdf")) },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.testTag("files_import_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FileOpen,
                            contentDescription = "Import PDF",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import")
                    }
                    Box {
                        IconButton(
                            onClick = { sortMenuExpanded = true },
                            modifier = Modifier.testTag("files_sort_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sort files"
                            )
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            FileSortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = option.label,
                                            fontWeight = if (option == state.sortOption) FontWeight.Bold else FontWeight.Normal,
                                            color = if (option == state.sortOption) DocuRed else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        sortMenuExpanded = false
                                        viewModel.updateSort(option)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::updateSearch,
                placeholder = { Text("Search PDFs by filename...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearch("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = DocuRed,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .testTag("files_search_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Category Tabs (All Files / Recent / Favorites)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilesTab.entries.forEach { tab ->
                    val selected = tab == state.selectedTab
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.selectTab(tab) },
                        label = {
                            Text(
                                text = tab.label,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DocuRed,
                            selectedLabelColor = PureWhite
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("files_tab_${tab.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (state.documents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyDocumentsPlaceholder(
                        title = when {
                            state.searchQuery.isNotBlank() -> "No matching PDFs"
                            state.selectedTab == FilesTab.FAVORITES -> "No favorites yet"
                            else -> "No PDF files yet"
                        },
                        subtitle = when {
                            state.searchQuery.isNotBlank() -> "Try a different search keyword."
                            state.selectedTab == FilesTab.FAVORITES -> "Tap the star icon on any PDF to pin it here."
                            else -> "Convert images or scan documents to build your offline PDF library."
                        },
                        actionLabel = if (state.searchQuery.isBlank() && state.selectedTab == FilesTab.ALL) "Create PDF" else null,
                        onAction = onCreatePdf
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.documents, key = { it.id }) { doc ->
                        DocumentListCard(
                            document = doc,
                            onOpen = { onOpenReader(doc.id) },
                            onToggleFavorite = { viewModel.toggleFavorite(doc) },
                            onShare = {
                                val intent = repository.getShareIntent(File(doc.filePath))
                                context.startActivity(Intent.createChooser(intent, "Share PDF"))
                            },
                            onRename = { docToRename = doc },
                            onSaveCopy = {
                                docToExport = doc
                                exportSafLauncher.launch(doc.fileName)
                            },
                            onDelete = { docToDelete = doc }
                        )
                    }
                }
            }
        }
    }

    docToRename?.let { target ->
        RenamePdfDialog(
            initialName = target.fileName,
            onDismiss = { docToRename = null },
            onConfirm = { newName ->
                viewModel.renameDocument(target, newName)
                docToRename = null
            }
        )
    }

    docToDelete?.let { target ->
        DeleteConfirmDialog(
            fileName = target.fileName,
            onDismiss = { docToDelete = null },
            onConfirm = {
                viewModel.deleteDocument(target)
                docToDelete = null
            }
        )
    }
}
