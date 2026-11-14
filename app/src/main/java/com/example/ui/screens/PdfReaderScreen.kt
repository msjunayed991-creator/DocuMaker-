package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.PdfRepository
import com.example.ui.components.RenamePdfDialog
import com.example.ui.theme.DocuRed
import com.example.ui.theme.InkBlack
import com.example.ui.theme.PureWhite
import com.example.ui.viewmodel.PdfReaderViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    docId: Long,
    viewModel: PdfReaderViewModel,
    repository: PdfRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showRenameDialog by remember { mutableStateOf(false) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }

    val exportSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        if (destUri != null) viewModel.exportToUri(destUri)
    }

    LaunchedEffect(docId) {
        viewModel.loadDocument(docId)
    }

    LaunchedEffect(state.statusMessage) {
        state.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val visiblePageIndex by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }

    LaunchedEffect(visiblePageIndex) {
        if (state.pageCount > 0) {
            viewModel.setCurrentPage(visiblePageIndex)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(InkBlack.copy(alpha = 0.94f))
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(
                        color = DocuRed,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                state.error != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.error!!,
                            style = MaterialTheme.typography.titleMedium,
                            color = PureWhite
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = DocuRed,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.clickable(onClick = onBack)
                        ) {
                            Text(
                                text = "Go Back",
                                color = PureWhite,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { viewModel.toggleFullScreen() },
                                    onDoubleTap = {
                                        val nextZoom = if (state.zoomScale > 1.2f) 1.0f else 2.0f
                                        viewModel.setZoom(nextZoom)
                                        if (nextZoom == 1.0f) panOffsetX = 0f
                                    }
                                )
                            }
                            .pointerInput(state.zoomScale) {
                                if (state.zoomScale > 1.05f) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val newScale = (state.zoomScale * zoom).coerceIn(0.8f, 3.5f)
                                        viewModel.setZoom(newScale)
                                        if (newScale > 1.05f) {
                                            panOffsetX = (panOffsetX + pan.x).coerceIn(-600f, 600f)
                                        } else {
                                            panOffsetX = 0f
                                        }
                                    }
                                }
                            }
                            .graphicsLayer {
                                scaleX = state.zoomScale
                                scaleY = state.zoomScale
                                translationX = panOffsetX
                            },
                        contentPadding = PaddingValues(
                            top = if (state.isFullScreen) 24.dp else innerPadding.calculateTopPadding() + 72.dp,
                            bottom = if (state.isFullScreen) 24.dp else 120.dp,
                            start = 12.dp,
                            end = 12.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(state.pageCount, key = { it }) { pageIdx ->
                            PdfPageItem(
                                pageIndex = pageIdx,
                                totalPages = state.pageCount,
                                renderPage = { widthPx ->
                                    viewModel.getOrRenderPage(pageIdx, widthPx)
                                }
                            )
                        }
                    }
                }
            }

            // Top App Bar (hidden in full-screen reading)
            AnimatedVisibility(
                visible = !state.isFullScreen,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = state.document?.fileName ?: "PDF Reader",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (state.pageCount > 0) {
                                Text(
                                    text = "Page ${state.currentPage + 1} of ${state.pageCount} • ${(state.zoomScale * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("reader_back_btn")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        val doc = state.document
                        if (doc != null) {
                            IconButton(onClick = { viewModel.toggleFavorite() }) {
                                Icon(
                                    imageVector = if (doc.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                    contentDescription = "Favorite",
                                    tint = if (doc.isFavorite) DocuRed else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(onClick = { showRenameDialog = true }) {
                                Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = "Rename")
                            }
                            IconButton(onClick = { exportSafLauncher.launch(doc.fileName) }) {
                                Icon(Icons.Filled.SaveAlt, contentDescription = "Save Copy")
                            }
                            IconButton(
                                onClick = {
                                    val intent = repository.getShareIntent(File(doc.filePath))
                                    context.startActivity(Intent.createChooser(intent, "Share PDF"))
                                },
                                modifier = Modifier.testTag("reader_share_btn")
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = "Share PDF")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                    )
                )
            }

            // Bottom Floating Navigation + Zoom Bar
            AnimatedVisibility(
                visible = !state.isFullScreen && state.pageCount > 0,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    shadowElevation = 10.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        if (state.pageCount > 1) {
                            Slider(
                                value = state.currentPage.toFloat(),
                                onValueChange = { target ->
                                    val idx = target.toInt().coerceIn(0, state.pageCount - 1)
                                    scope.launch { listState.scrollToItem(idx) }
                                },
                                valueRange = 0f..(state.pageCount - 1).toFloat(),
                                steps = (state.pageCount - 2).coerceAtLeast(0),
                                colors = SliderDefaults.colors(thumbColor = DocuRed, activeTrackColor = DocuRed),
                                modifier = Modifier.height(26.dp)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        val prev = (state.currentPage - 1).coerceAtLeast(0)
                                        scope.launch { listState.animateScrollToItem(prev) }
                                    },
                                    enabled = state.currentPage > 0,
                                    modifier = Modifier.testTag("reader_prev_page")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous page")
                                }
                                Text(
                                    text = "${state.currentPage + 1} / ${state.pageCount}",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                IconButton(
                                    onClick = {
                                        val next = (state.currentPage + 1).coerceAtMost(state.pageCount - 1)
                                        scope.launch { listState.animateScrollToItem(next) }
                                    },
                                    enabled = state.currentPage < state.pageCount - 1,
                                    modifier = Modifier.testTag("reader_next_page")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next page")
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        viewModel.setZoom(state.zoomScale - 0.25f)
                                        if (state.zoomScale - 0.25f <= 1.0f) panOffsetX = 0f
                                    },
                                    modifier = Modifier.testTag("reader_zoom_out")
                                ) {
                                    Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom out")
                                }
                                Text(
                                    text = "${(state.zoomScale * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier
                                        .clickable {
                                            viewModel.setZoom(1.0f)
                                            panOffsetX = 0f
                                        }
                                        .padding(horizontal = 4.dp)
                                )
                                IconButton(
                                    onClick = { viewModel.setZoom(state.zoomScale + 0.25f) },
                                    modifier = Modifier.testTag("reader_zoom_in")
                                ) {
                                    Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom in")
                                }
                                IconButton(
                                    onClick = { viewModel.toggleFullScreen() },
                                    modifier = Modifier.testTag("reader_fullscreen_btn")
                                ) {
                                    Icon(Icons.Filled.Fullscreen, contentDescription = "Full-screen reading")
                                }
                            }
                        }
                    }
                }
            }

            // Floating exit fullscreen button
            if (state.isFullScreen) {
                Surface(
                    shape = CircleShape,
                    color = InkBlack.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(18.dp)
                        .clickable { viewModel.toggleFullScreen() }
                ) {
                    Icon(
                        imageVector = Icons.Filled.FullscreenExit,
                        contentDescription = "Exit full-screen",
                        tint = PureWhite,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }

    if (showRenameDialog && state.document != null) {
        RenamePdfDialog(
            initialName = state.document!!.fileName,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                viewModel.renameDocument(newName)
                showRenameDialog = false
            }
        )
    }
}

@Composable
private fun PdfPageItem(
    pageIndex: Int,
    totalPages: Int,
    renderPage: suspend (Int) -> Bitmap?
) {
    var bitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pageIndex) {
        bitmap = renderPage(1080)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (bitmap == null) Modifier.height(460.dp) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            val bmp = bitmap
            if (bmp != null && !bmp.isRecycled) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "PDF page ${pageIndex + 1} of $totalPages",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth
                )
            } else {
                CircularProgressIndicator(color = DocuRed, modifier = Modifier.size(32.dp))
            }

            Surface(
                color = InkBlack.copy(alpha = 0.65f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            ) {
                Text(
                    text = "${pageIndex + 1} / $totalPages",
                    style = MaterialTheme.typography.labelSmall,
                    color = PureWhite,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
        }
    }
}
