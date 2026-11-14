package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.domain.model.DocumentSourceType
import com.example.domain.model.PdfToolType
import com.example.ui.components.DocuMakerBottomBar
import com.example.ui.navigation.DocuMakerRoutes
import com.example.ui.screens.CameraScannerScreen
import com.example.ui.screens.FilesScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ImageToPdfScreen
import com.example.ui.screens.PdfReaderScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.ToolWorkflowScreen
import com.example.ui.screens.ToolsHubScreen
import com.example.ui.theme.DocuMakerTheme
import com.example.ui.theme.DocuRed
import com.example.ui.theme.InkBlack
import com.example.ui.theme.PureWhite
import com.example.ui.viewmodel.FilesViewModel
import com.example.ui.viewmodel.HomeViewModel
import com.example.ui.viewmodel.ImageToPdfViewModel
import com.example.ui.viewmodel.PdfReaderViewModel
import com.example.ui.viewmodel.PdfToolsViewModel
import com.example.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var externalPdfUriState by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        val container = (application as DocuMakerApplication).container

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.Factory(container.settingsRepository, container.pdfRepository)
            )
            val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

            DocuMakerTheme(themeMode = settingsState.preferences.themeMode) {
                DocuMakerApp(
                    container = container,
                    settingsViewModel = settingsViewModel,
                    externalPdfUri = externalPdfUriState,
                    onConsumeExternalPdf = { externalPdfUriState = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            externalPdfUriState = intent.data
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocuMakerApp(
    container: AppContainer,
    settingsViewModel: SettingsViewModel,
    externalPdfUri: Uri?,
    onConsumeExternalPdf: () -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val scope = rememberCoroutineScope()

    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(container.pdfRepository)
    )
    val filesViewModel: FilesViewModel = viewModel(
        factory = FilesViewModel.Factory(container.pdfRepository)
    )
    val imageToPdfViewModel: ImageToPdfViewModel = viewModel(
        factory = ImageToPdfViewModel.Factory(container.pdfRepository, container.settingsRepository)
    )
    val readerViewModel: PdfReaderViewModel = viewModel(
        factory = PdfReaderViewModel.Factory(container.pdfRepository)
    )
    val toolsViewModel: PdfToolsViewModel = viewModel(
        factory = PdfToolsViewModel.Factory(container.pdfRepository)
    )

    var showCreateSheet by remember { mutableStateOf(false) }

    val importPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val doc = container.pdfRepository.importExternalPdf(uri)
                if (doc != null) {
                    navController.navigate(DocuMakerRoutes.pdfReaderRoute(doc.id))
                }
            }
        }
    }

    LaunchedEffect(externalPdfUri) {
        val uri = externalPdfUri ?: return@LaunchedEffect
        onConsumeExternalPdf()
        val doc = container.pdfRepository.importExternalPdf(uri)
        if (doc != null) {
            navController.navigate(DocuMakerRoutes.pdfReaderRoute(doc.id))
        }
    }

    val topLevelRoutes = setOf(
        DocuMakerRoutes.HOME,
        DocuMakerRoutes.FILES,
        DocuMakerRoutes.TOOLS,
        DocuMakerRoutes.SETTINGS
    )
    val showBottomBar = currentRoute in topLevelRoutes

    val navigateToTopLevel: (String) -> Unit = { route ->
        if (currentRoute != route) {
            navController.navigate(route) {
                popUpTo(DocuMakerRoutes.HOME) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                DocuMakerBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = navigateToTopLevel,
                    onCreateClick = { showCreateSheet = true }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = DocuMakerRoutes.HOME,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            composable(DocuMakerRoutes.HOME) {
                HomeScreen(
                    homeViewModel = homeViewModel,
                    filesViewModel = filesViewModel,
                    repository = container.pdfRepository,
                    onCreatePdf = { showCreateSheet = true },
                    onImageToPdf = {
                        imageToPdfViewModel.startNewSession(DocumentSourceType.IMAGE_TO_PDF)
                        navController.navigate(DocuMakerRoutes.IMAGE_TO_PDF)
                    },
                    onCameraScanner = {
                        navController.navigate(DocuMakerRoutes.CAMERA_SCANNER)
                    },
                    onOpenReader = { docId ->
                        navController.navigate(DocuMakerRoutes.pdfReaderRoute(docId))
                    },
                    onOpenTools = { navigateToTopLevel(DocuMakerRoutes.TOOLS) },
                    onOpenAllFiles = { navigateToTopLevel(DocuMakerRoutes.FILES) },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
                )
            }

            composable(DocuMakerRoutes.FILES) {
                FilesScreen(
                    viewModel = filesViewModel,
                    repository = container.pdfRepository,
                    onOpenReader = { docId ->
                        navController.navigate(DocuMakerRoutes.pdfReaderRoute(docId))
                    },
                    onCreatePdf = { showCreateSheet = true },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
                )
            }

            composable(DocuMakerRoutes.TOOLS) {
                ToolsHubScreen(
                    onLaunchTool = { tool ->
                        when (tool) {
                            PdfToolType.IMAGE_TO_PDF -> {
                                imageToPdfViewModel.startNewSession(DocumentSourceType.IMAGE_TO_PDF)
                                navController.navigate(DocuMakerRoutes.IMAGE_TO_PDF)
                            }
                            PdfToolType.CAMERA_SCANNER -> {
                                navController.navigate(DocuMakerRoutes.CAMERA_SCANNER)
                            }
                            else -> {
                                navController.navigate(DocuMakerRoutes.toolWorkflowRoute(tool))
                            }
                        }
                    },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
                )
            }

            composable(DocuMakerRoutes.SETTINGS) {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
                )
            }

            composable(DocuMakerRoutes.IMAGE_TO_PDF) {
                ImageToPdfScreen(
                    viewModel = imageToPdfViewModel,
                    repository = container.pdfRepository,
                    onBack = { navController.popBackStack() },
                    onOpenScanner = { navController.navigate(DocuMakerRoutes.CAMERA_SCANNER) },
                    onOpenReader = { docId ->
                        navController.navigate(DocuMakerRoutes.pdfReaderRoute(docId))
                    }
                )
            }

            composable(DocuMakerRoutes.CAMERA_SCANNER) {
                CameraScannerScreen(
                    repository = container.pdfRepository,
                    imageToPdfViewModel = imageToPdfViewModel,
                    onBack = { navController.popBackStack() },
                    onFinishToStudio = {
                        navController.navigate(DocuMakerRoutes.IMAGE_TO_PDF) {
                            popUpTo(DocuMakerRoutes.CAMERA_SCANNER) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                route = DocuMakerRoutes.PDF_READER,
                arguments = listOf(navArgument("docId") { type = NavType.LongType })
            ) { entry ->
                val docId = entry.arguments?.getLong("docId") ?: -1L
                PdfReaderScreen(
                    docId = docId,
                    viewModel = readerViewModel,
                    repository = container.pdfRepository,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = DocuMakerRoutes.TOOL_WORKFLOW,
                arguments = listOf(navArgument("toolType") { type = NavType.StringType })
            ) { entry ->
                val raw = entry.arguments?.getString("toolType").orEmpty()
                val tool = runCatching { PdfToolType.valueOf(raw) }.getOrDefault(PdfToolType.MERGE_PDF)
                ToolWorkflowScreen(
                    toolType = tool,
                    viewModel = toolsViewModel,
                    repository = container.pdfRepository,
                    onBack = { navController.popBackStack() },
                    onOpenReader = { docId ->
                        navController.navigate(DocuMakerRoutes.pdfReaderRoute(docId))
                    }
                )
            }
        }
    }

    if (showCreateSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCreateSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Create New PDF",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Choose how you want to start your document",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CreateOptionCard(
                    title = "Image to PDF",
                    subtitle = "Select multiple gallery photos, apply filters & customize layout",
                    icon = Icons.Filled.PhotoLibrary,
                    accentRed = true,
                    testTag = "sheet_image_to_pdf",
                    onClick = {
                        showCreateSheet = false
                        imageToPdfViewModel.startNewSession(DocumentSourceType.IMAGE_TO_PDF)
                        navController.navigate(DocuMakerRoutes.IMAGE_TO_PDF)
                    }
                )
                CreateOptionCard(
                    title = "Camera Document Scanner",
                    subtitle = "Scan paper documents with 4-corner perspective correction",
                    icon = Icons.Filled.CameraAlt,
                    accentRed = false,
                    testTag = "sheet_camera_scanner",
                    onClick = {
                        showCreateSheet = false
                        navController.navigate(DocuMakerRoutes.CAMERA_SCANNER)
                    }
                )
                CreateOptionCard(
                    title = "Import Existing PDF",
                    subtitle = "Open a PDF from device storage into Reader & Toolkit",
                    icon = Icons.Filled.FileOpen,
                    accentRed = false,
                    testTag = "sheet_import_pdf",
                    onClick = {
                        showCreateSheet = false
                        importPdfLauncher.launch(arrayOf("application/pdf"))
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun CreateOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentRed: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (accentRed) DocuRed else InkBlack),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = PureWhite)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
