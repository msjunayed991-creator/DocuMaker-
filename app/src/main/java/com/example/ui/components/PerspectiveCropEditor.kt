package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.domain.model.CropQuad
import com.example.domain.model.NormPoint
import com.example.ui.theme.DocuRed
import com.example.ui.theme.InkBlack
import com.example.ui.theme.PureWhite
import kotlin.math.hypot
import kotlin.math.min

private enum class CornerHandle {
    TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT
}

@Composable
fun PerspectiveCropEditor(
    bitmap: Bitmap,
    initialQuad: CropQuad,
    onRotate90: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: (CropQuad) -> Unit,
    modifier: Modifier = Modifier
) {
    var quad by remember(initialQuad) { mutableStateOf(initialQuad) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var activeHandle by remember { mutableStateOf<CornerHandle?>(null) }

    // Compute exact displayed image rect inside container (ContentScale.Fit)
    val imageRect = remember(containerSize, bitmap.width, bitmap.height) {
        if (containerSize.width == 0 || containerSize.height == 0) {
            null
        } else {
            val cw = containerSize.width.toFloat()
            val ch = containerSize.height.toFloat()
            val bw = bitmap.width.toFloat().coerceAtLeast(1f)
            val bh = bitmap.height.toFloat().coerceAtLeast(1f)
            val scale = min(cw / bw, ch / bh)
            val dw = bw * scale
            val dh = bh * scale
            val left = (cw - dw) / 2f
            val top = (ch - dh) / 2f
            androidx.compose.ui.geometry.Rect(left, top, left + dw, top + dh)
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = InkBlack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.testTag("crop_cancel_btn")
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel crop", tint = PureWhite)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Crop & Perspective",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = PureWhite
                    )
                    Text(
                        text = "Drag corners to align document edges",
                        style = MaterialTheme.typography.bodySmall,
                        color = PureWhite.copy(alpha = 0.7f)
                    )
                }
                IconButton(
                    onClick = onRotate90,
                    modifier = Modifier.testTag("crop_rotate_btn")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.RotateRight,
                        contentDescription = "Rotate 90 degrees",
                        tint = PureWhite
                    )
                }
            }

            // Interactive image + 4-corner quad overlay
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .onSizeChanged { containerSize = it }
                    .pointerInput(imageRect) {
                        val rect = imageRect ?: return@pointerInput
                        detectDragGestures(
                            onDragStart = { offset ->
                                val pts = listOf(
                                    CornerHandle.TOP_LEFT to Offset(rect.left + quad.topLeft.x * rect.width, rect.top + quad.topLeft.y * rect.height),
                                    CornerHandle.TOP_RIGHT to Offset(rect.left + quad.topRight.x * rect.width, rect.top + quad.topRight.y * rect.height),
                                    CornerHandle.BOTTOM_RIGHT to Offset(rect.left + quad.bottomRight.x * rect.width, rect.top + quad.bottomRight.y * rect.height),
                                    CornerHandle.BOTTOM_LEFT to Offset(rect.left + quad.bottomLeft.x * rect.width, rect.top + quad.bottomLeft.y * rect.height)
                                )
                                val nearest = pts.minByOrNull { (_, p) ->
                                    hypot((p.x - offset.x).toDouble(), (p.y - offset.y).toDouble())
                                }
                                activeHandle = if (nearest != null && hypot((nearest.second.x - offset.x).toDouble(), (nearest.second.y - offset.y).toDouble()) < 130.0) {
                                    nearest.first
                                } else null
                            },
                            onDragEnd = { activeHandle = null },
                            onDragCancel = { activeHandle = null },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val handle = activeHandle ?: return@detectDragGestures
                                val dx = dragAmount.x / rect.width
                                val dy = dragAmount.y / rect.height
                                quad = when (handle) {
                                    CornerHandle.TOP_LEFT -> quad.copy(
                                        topLeft = NormPoint(
                                            (quad.topLeft.x + dx).coerceIn(0f, 0.9f),
                                            (quad.topLeft.y + dy).coerceIn(0f, 0.9f)
                                        )
                                    )
                                    CornerHandle.TOP_RIGHT -> quad.copy(
                                        topRight = NormPoint(
                                            (quad.topRight.x + dx).coerceIn(0.1f, 1f),
                                            (quad.topRight.y + dy).coerceIn(0f, 0.9f)
                                        )
                                    )
                                    CornerHandle.BOTTOM_RIGHT -> quad.copy(
                                        bottomRight = NormPoint(
                                            (quad.bottomRight.x + dx).coerceIn(0.1f, 1f),
                                            (quad.bottomRight.y + dy).coerceIn(0.1f, 1f)
                                        )
                                    )
                                    CornerHandle.BOTTOM_LEFT -> quad.copy(
                                        bottomLeft = NormPoint(
                                            (quad.bottomLeft.x + dx).coerceIn(0f, 0.9f),
                                            (quad.bottomLeft.y + dy).coerceIn(0.1f, 1f)
                                        )
                                    )
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Document to crop",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                val rect = imageRect
                if (rect != null) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val tl = Offset(rect.left + quad.topLeft.x * rect.width, rect.top + quad.topLeft.y * rect.height)
                        val tr = Offset(rect.left + quad.topRight.x * rect.width, rect.top + quad.topRight.y * rect.height)
                        val br = Offset(rect.left + quad.bottomRight.x * rect.width, rect.top + quad.bottomRight.y * rect.height)
                        val bl = Offset(rect.left + quad.bottomLeft.x * rect.width, rect.top + quad.bottomLeft.y * rect.height)

                        val quadPath = Path().apply {
                            moveTo(tl.x, tl.y)
                            lineTo(tr.x, tr.y)
                            lineTo(br.x, br.y)
                            lineTo(bl.x, bl.y)
                            close()
                        }

                        drawPath(
                            path = quadPath,
                            color = DocuRed,
                            style = Stroke(width = 5f)
                        )

                        listOf(tl, tr, br, bl).forEach { corner ->
                            drawCircle(color = DocuRed, radius = 22f, center = corner)
                            drawCircle(color = PureWhite, radius = 13f, center = corner)
                        }
                    }
                }
            }

            // Bottom action bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF18181D))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = { quad = CropQuad() },
                    modifier = Modifier.testTag("crop_reset_btn")
                ) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = null, tint = PureWhite)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Full Frame", color = PureWhite)
                }

                Button(
                    onClick = { onConfirm(quad) },
                    colors = ButtonDefaults.buttonColors(containerColor = DocuRed, contentColor = PureWhite),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("crop_apply_btn")
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Apply Crop")
                }
            }
        }
    }
}
