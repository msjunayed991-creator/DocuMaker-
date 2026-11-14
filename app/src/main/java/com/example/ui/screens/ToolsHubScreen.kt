package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.PdfToolType
import com.example.ui.theme.DocuRed
import com.example.ui.theme.InkBlack
import com.example.ui.theme.PureWhite

@Composable
fun ToolsHubScreen(
    onLaunchTool: (PdfToolType) -> Unit,
    modifier: Modifier = Modifier
) {
    val grouped = PdfToolType.entries.groupBy { it.category }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column {
                Text(
                    text = "PDF Toolkit",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "All 10 tools run 100% offline on your device",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        grouped.forEach { (category, tools) ->
            item(key = category) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = category.uppercase(),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = DocuRed
                    )
                    tools.chunked(2).forEach { rowTools ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowTools.forEach { tool ->
                                ToolCard(
                                    tool = tool,
                                    onClick = { onLaunchTool(tool) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowTools.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolCard(
    tool: PdfToolType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, isPrimaryAccent) = toolIconAndAccent(tool)
    Card(
        modifier = modifier
            .clickable(onClick = onClick)
            .testTag("tool_card_${tool.name.lowercase()}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isPrimaryAccent) DocuRed else InkBlack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = tool.title,
                        tint = PureWhite,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Offline",
                        style = MaterialTheme.typography.labelSmall,
                        color = DocuRed,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = tool.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = tool.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2,
                maxLines = 2
            )
        }
    }
}

private fun toolIconAndAccent(tool: PdfToolType): Pair<ImageVector, Boolean> = when (tool) {
    PdfToolType.IMAGE_TO_PDF -> Icons.Filled.PhotoLibrary to true
    PdfToolType.CAMERA_SCANNER -> Icons.Filled.CameraAlt to true
    PdfToolType.MERGE_PDF -> Icons.AutoMirrored.Filled.CallMerge to false
    PdfToolType.COMPRESS_PDF -> Icons.Filled.Compress to true
    PdfToolType.PDF_TO_JPG -> Icons.Filled.Image to false
    PdfToolType.ROTATE_PDF -> Icons.Filled.RotateRight to false
    PdfToolType.REORDER_PAGES -> Icons.Filled.SwapVert to false
    PdfToolType.ADD_TEXT -> Icons.Filled.TextFields to false
    PdfToolType.DRAW_ON_PDF -> Icons.Filled.Brush to true
    PdfToolType.ADD_SIGNATURE -> Icons.Filled.Draw to true
}
