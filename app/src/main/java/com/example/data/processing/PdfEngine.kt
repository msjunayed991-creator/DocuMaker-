package com.example.data.processing

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.example.domain.model.CompressionLevel
import com.example.domain.model.ImageFitMode
import com.example.domain.model.PdfPageSettings
import com.example.domain.model.SelectedImageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class PdfTextOverlay(
    val pageIndex: Int,
    val text: String,
    val normX: Float,
    val normY: Float,
    val fontSizeSp: Float,
    val colorInt: Int,
    val isBold: Boolean
)

data class PdfDrawStroke(
    val points: List<Pair<Float, Float>>, // normalized 0..1 coordinates
    val colorInt: Int,
    val strokeWidthNorm: Float
)

object PdfEngine {

    suspend fun generatePdfFromImages(
        context: Context,
        items: List<SelectedImageItem>,
        settings: PdfPageSettings,
        outputFile: File,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        require(items.isNotEmpty()) { "No images selected" }
        outputFile.parentFile?.mkdirs()

        val (pageW, pageH) = settings.resolvedPageDimensionsPt()
        val marginPt = settings.margin.points.toFloat().coerceAtMost(min(pageW, pageH) * 0.35f)

        val pdfDocument = PdfDocument()
        try {
            items.forEachIndexed { index, item ->
                onProgress(index + 1, items.size)
                val uri = Uri.parse(item.uriString)
                val rawBitmap = ImageProcessor.loadBitmapFromUri(context, uri, maxDimension = 2048)
                    ?: throw IllegalStateException("Could not decode image: ${item.displayName}")

                val processed = ImageProcessor.applyFullPipeline(rawBitmap, item.adjustments)
                if (processed !== rawBitmap) rawBitmap.recycle()

                // Apply JPEG quality compression if < 100 to control output file size cleanly
                val finalBitmap = if (settings.jpegQuality < 98) {
                    recompressBitmap(processed, settings.jpegQuality).also {
                        if (it !== processed) processed.recycle()
                    }
                } else {
                    processed
                }

                val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                if (settings.whiteBackground) {
                    canvas.drawColor(Color.WHITE)
                }

                val contentRect = RectF(
                    marginPt,
                    marginPt,
                    pageW - marginPt,
                    pageH - marginPt
                )
                drawBitmapInContentRect(
                    canvas = canvas,
                    bitmap = finalBitmap,
                    contentRect = contentRect,
                    fitMode = settings.imageFit,
                    maintainAspectRatio = settings.maintainAspectRatio
                )

                pdfDocument.finishPage(page)
                finalBitmap.recycle()
            }

            FileOutputStream(outputFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
            items.size
        } finally {
            pdfDocument.close()
        }
    }

    private fun drawBitmapInContentRect(
        canvas: Canvas,
        bitmap: Bitmap,
        contentRect: RectF,
        fitMode: ImageFitMode,
        maintainAspectRatio: Boolean
    ) {
        val availW = contentRect.width().coerceAtLeast(1f)
        val availH = contentRect.height().coerceAtLeast(1f)
        val bmpW = bitmap.width.toFloat().coerceAtLeast(1f)
        val bmpH = bitmap.height.toFloat().coerceAtLeast(1f)

        val destRect = if (!maintainAspectRatio && fitMode != ImageFitMode.ORIGINAL) {
            RectF(contentRect)
        } else {
            when (fitMode) {
                ImageFitMode.FIT -> {
                    val scale = min(availW / bmpW, availH / bmpH)
                    val drawW = bmpW * scale
                    val drawH = bmpH * scale
                    val left = contentRect.left + (availW - drawW) / 2f
                    val top = contentRect.top + (availH - drawH) / 2f
                    RectF(left, top, left + drawW, top + drawH)
                }
                ImageFitMode.FILL -> {
                    val scale = max(availW / bmpW, availH / bmpH)
                    val drawW = bmpW * scale
                    val drawH = bmpH * scale
                    val left = contentRect.left + (availW - drawW) / 2f
                    val top = contentRect.top + (availH - drawH) / 2f
                    RectF(left, top, left + drawW, top + drawH)
                }
                ImageFitMode.ORIGINAL -> {
                    val scale = min(1f, min(availW / bmpW, availH / bmpH))
                    val drawW = bmpW * scale
                    val drawH = bmpH * scale
                    val left = contentRect.left + (availW - drawW) / 2f
                    val top = contentRect.top + (availH - drawH) / 2f
                    RectF(left, top, left + drawW, top + drawH)
                }
            }
        }

        canvas.save()
        canvas.clipRect(contentRect)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bitmap, null, destRect, paint)
        canvas.restore()
    }

    private fun recompressBitmap(source: Bitmap, quality: Int): Bitmap {
        val baos = ByteArrayOutputStream()
        source.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(25, 100), baos)
        val bytes = baos.toByteArray()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: source
    }

    suspend fun getPdfPageCount(file: File): Int = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext 0
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    renderer.pageCount
                }
            }
        }.getOrDefault(0)
    }

    suspend fun renderPdfPage(
        file: File,
        pageIndex: Int,
        targetWidthPx: Int = 1080
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageIndex !in 0 until renderer.pageCount) return@runCatching null
                    renderer.openPage(pageIndex).use { page ->
                        val ratio = page.height.toFloat() / page.width.toFloat().coerceAtLeast(1f)
                        val width = targetWidthPx.coerceIn(120, 2400)
                        val height = (width * ratio).roundToInt().coerceAtLeast(120)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }
            }
        }.getOrNull()
    }

    suspend fun mergePdfs(
        sources: List<File>,
        outputFile: File,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        require(sources.size >= 2) { "Select at least 2 PDFs to merge" }
        outputFile.parentFile?.mkdirs()

        val totalPages = sources.sumOf { getPdfPageCount(it) }.coerceAtLeast(1)
        var currentPageNum = 0
        val outPdf = PdfDocument()

        try {
            for (srcFile in sources) {
                ParcelFileDescriptor.open(srcFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        for (i in 0 until renderer.pageCount) {
                            currentPageNum++
                            onProgress(currentPageNum, totalPages)
                            renderer.openPage(i).use { page ->
                                val pageW = page.width.coerceAtLeast(100)
                                val pageH = page.height.coerceAtLeast(100)
                                val scale = 2.0f
                                val bmp = Bitmap.createBitmap(
                                    (pageW * scale).roundToInt().coerceAtMost(2400),
                                    (pageH * scale).roundToInt().coerceAtMost(3200),
                                    Bitmap.Config.ARGB_8888
                                )
                                bmp.eraseColor(Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                                val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, currentPageNum).create()
                                val outPage = outPdf.startPage(pageInfo)
                                outPage.canvas.drawBitmap(
                                    bmp,
                                    null,
                                    RectF(0f, 0f, pageW.toFloat(), pageH.toFloat()),
                                    Paint(Paint.FILTER_BITMAP_FLAG)
                                )
                                outPdf.finishPage(outPage)
                                bmp.recycle()
                            }
                        }
                    }
                }
            }
            FileOutputStream(outputFile).use { outPdf.writeTo(it) }
            currentPageNum
        } finally {
            outPdf.close()
        }
    }

    suspend fun compressPdf(
        source: File,
        outputFile: File,
        level: CompressionLevel,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        val outPdf = PdfDocument()
        try {
            var count = 0
            ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    count = renderer.pageCount
                    for (i in 0 until count) {
                        onProgress(i + 1, count)
                        renderer.openPage(i).use { page ->
                            val pageW = page.width.coerceAtLeast(100)
                            val pageH = page.height.coerceAtLeast(100)
                            val renderW = (pageW * 1.6f * level.scale).roundToInt().coerceIn(240, 1800)
                            val renderH = (pageH * 1.6f * level.scale).roundToInt().coerceIn(240, 2400)
                            val raw = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
                            raw.eraseColor(Color.WHITE)
                            page.render(raw, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val compressed = recompressBitmap(raw, level.jpegQuality)
                            if (compressed !== raw) raw.recycle()

                            val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, i + 1).create()
                            val outPage = outPdf.startPage(pageInfo)
                            outPage.canvas.drawBitmap(
                                compressed,
                                null,
                                RectF(0f, 0f, pageW.toFloat(), pageH.toFloat()),
                                Paint(Paint.FILTER_BITMAP_FLAG)
                            )
                            outPdf.finishPage(outPage)
                            compressed.recycle()
                        }
                    }
                }
            }
            FileOutputStream(outputFile).use { outPdf.writeTo(it) }
            count
        } finally {
            outPdf.close()
        }
    }

    suspend fun exportPdfToJpgs(
        context: Context,
        source: File,
        pageIndices: List<Int>,
        baseName: String,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): List<Uri> = withContext(Dispatchers.IO) {
        val savedUris = mutableListOf<Uri>()
        ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val validIndices = pageIndices.filter { it in 0 until renderer.pageCount }
                validIndices.forEachIndexed { idx, pageIdx ->
                    onProgress(idx + 1, validIndices.size)
                    renderer.openPage(pageIdx).use { page ->
                        val width = (page.width * 2.2f).roundToInt().coerceIn(600, 2200)
                        val height = (page.height * 2.2f).roundToInt().coerceIn(600, 3000)
                        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        val fileName = "${baseName}_page_${pageIdx + 1}.jpg"
                        val uri = saveJpgToGallery(context, bmp, fileName)
                        if (uri != null) savedUris.add(uri)
                        bmp.recycle()
                    }
                }
            }
        }
        savedUris
    }

    private fun saveJpgToGallery(context: Context, bitmap: Bitmap, fileName: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/DocuMaker")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } else {
            val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
            val outFile = File(exportDir, fileName)
            FileOutputStream(outFile).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            Uri.fromFile(outFile)
        }
    }

    suspend fun rotatePdf(
        source: File,
        outputFile: File,
        pageRotations: Map<Int, Int>,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        val outPdf = PdfDocument()
        try {
            var total = 0
            ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    total = renderer.pageCount
                    for (i in 0 until total) {
                        onProgress(i + 1, total)
                        val rotation = ((pageRotations[i] ?: 0) % 360 + 360) % 360
                        renderer.openPage(i).use { page ->
                            val raw = Bitmap.createBitmap(
                                (page.width * 2f).roundToInt().coerceAtMost(2200),
                                (page.height * 2f).roundToInt().coerceAtMost(3000),
                                Bitmap.Config.ARGB_8888
                            )
                            raw.eraseColor(Color.WHITE)
                            page.render(raw, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                            val rotated = if (rotation != 0) {
                                ImageProcessor.rotateBitmap(raw, rotation).also {
                                    if (it !== raw) raw.recycle()
                                }
                            } else raw

                            val swap = rotation == 90 || rotation == 270
                            val pageW = if (swap) page.height else page.width
                            val pageH = if (swap) page.width else page.height
                            val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, i + 1).create()
                            val outPage = outPdf.startPage(pageInfo)
                            outPage.canvas.drawBitmap(
                                rotated,
                                null,
                                RectF(0f, 0f, pageW.toFloat(), pageH.toFloat()),
                                Paint(Paint.FILTER_BITMAP_FLAG)
                            )
                            outPdf.finishPage(outPage)
                            rotated.recycle()
                        }
                    }
                }
            }
            FileOutputStream(outputFile).use { outPdf.writeTo(it) }
            total
        } finally {
            outPdf.close()
        }
    }

    suspend fun reorderPdf(
        source: File,
        outputFile: File,
        orderedIndices: List<Int>,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        require(orderedIndices.isNotEmpty()) { "At least one page must remain" }
        outputFile.parentFile?.mkdirs()
        val outPdf = PdfDocument()
        try {
            ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val valid = orderedIndices.filter { it in 0 until renderer.pageCount }
                    valid.forEachIndexed { outIdx, srcIdx ->
                        onProgress(outIdx + 1, valid.size)
                        renderer.openPage(srcIdx).use { page ->
                            val pageW = page.width
                            val pageH = page.height
                            val bmp = Bitmap.createBitmap(
                                (pageW * 2f).roundToInt().coerceAtMost(2200),
                                (pageH * 2f).roundToInt().coerceAtMost(3000),
                                Bitmap.Config.ARGB_8888
                            )
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, outIdx + 1).create()
                            val outPage = outPdf.startPage(pageInfo)
                            outPage.canvas.drawBitmap(
                                bmp,
                                null,
                                RectF(0f, 0f, pageW.toFloat(), pageH.toFloat()),
                                Paint(Paint.FILTER_BITMAP_FLAG)
                            )
                            outPdf.finishPage(outPage)
                            bmp.recycle()
                        }
                    }
                    FileOutputStream(outputFile).use { outPdf.writeTo(it) }
                    valid.size
                }
            }
        } finally {
            outPdf.close()
        }
    }

    suspend fun addTextToPdf(
        source: File,
        outputFile: File,
        overlays: List<PdfTextOverlay>,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        editPdfPages(source, outputFile, onProgress) { pageIdx, canvas, pageW, pageH ->
            val pageOverlays = overlays.filter { it.pageIndex == pageIdx }
            for (overlay in pageOverlays) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = overlay.colorInt
                    textSize = overlay.fontSizeSp * (pageW / 360f)
                    typeface = if (overlay.isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                }
                val x = overlay.normX.coerceIn(0.02f, 0.92f) * pageW
                val y = overlay.normY.coerceIn(0.04f, 0.96f) * pageH
                canvas.drawText(overlay.text, x, y, paint)
            }
        }
    }

    suspend fun drawOnPdf(
        source: File,
        outputFile: File,
        targetPageIndex: Int,
        strokes: List<PdfDrawStroke>,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        editPdfPages(source, outputFile, onProgress) { pageIdx, canvas, pageW, pageH ->
            if (pageIdx == targetPageIndex) {
                for (stroke in strokes) {
                    if (stroke.points.size < 2) continue
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = stroke.colorInt
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                        strokeWidth = stroke.strokeWidthNorm * pageW
                    }
                    val path = Path()
                    val first = stroke.points.first()
                    path.moveTo(first.first * pageW, first.second * pageH)
                    for (pt in stroke.points.drop(1)) {
                        path.lineTo(pt.first * pageW, pt.second * pageH)
                    }
                    canvas.drawPath(path, paint)
                }
            }
        }
    }

    suspend fun addSignatureToPdf(
        source: File,
        outputFile: File,
        targetPageIndex: Int,
        signatureBitmap: Bitmap,
        normCenterX: Float,
        normCenterY: Float,
        normWidth: Float,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        editPdfPages(source, outputFile, onProgress) { pageIdx, canvas, pageW, pageH ->
            if (pageIdx == targetPageIndex) {
                val sigW = (normWidth.coerceIn(0.12f, 0.8f)) * pageW
                val aspect = signatureBitmap.height.toFloat() / signatureBitmap.width.toFloat().coerceAtLeast(1f)
                val sigH = sigW * aspect
                val left = (normCenterX * pageW) - sigW / 2f
                val top = (normCenterY * pageH) - sigH / 2f
                val dest = RectF(left, top, left + sigW, top + sigH)
                canvas.drawBitmap(signatureBitmap, null, dest, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            }
        }
    }

    private fun editPdfPages(
        source: File,
        outputFile: File,
        onProgress: (Int, Int) -> Unit,
        onDrawOverlay: (pageIndex: Int, canvas: Canvas, pageW: Int, pageH: Int) -> Unit
    ): Int {
        outputFile.parentFile?.mkdirs()
        val outPdf = PdfDocument()
        try {
            var total = 0
            ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    total = renderer.pageCount
                    for (i in 0 until total) {
                        onProgress(i + 1, total)
                        renderer.openPage(i).use { page ->
                            val pageW = page.width
                            val pageH = page.height
                            val bmp = Bitmap.createBitmap(
                                (pageW * 2f).roundToInt().coerceAtMost(2200),
                                (pageH * 2f).roundToInt().coerceAtMost(3000),
                                Bitmap.Config.ARGB_8888
                            )
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                            val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, i + 1).create()
                            val outPage = outPdf.startPage(pageInfo)
                            outPage.canvas.drawBitmap(
                                bmp,
                                null,
                                RectF(0f, 0f, pageW.toFloat(), pageH.toFloat()),
                                Paint(Paint.FILTER_BITMAP_FLAG)
                            )
                            onDrawOverlay(i, outPage.canvas, pageW, pageH)
                            outPdf.finishPage(outPage)
                            bmp.recycle()
                        }
                    }
                }
            }
            FileOutputStream(outputFile).use { outPdf.writeTo(it) }
            return total
        } finally {
            outPdf.close()
        }
    }
}
