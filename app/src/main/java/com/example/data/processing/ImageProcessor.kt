package com.example.data.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import com.example.domain.model.CropQuad
import com.example.domain.model.ImageAdjustments
import com.example.domain.model.ImageFilterType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ImageProcessor {

    suspend fun loadBitmapFromUri(
        context: Context,
        uri: Uri,
        maxDimension: Int = 2048
    ): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openStream(context, uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return@runCatching null

            var sampleSize = 1
            val longest = max(boundsOpts.outWidth, boundsOpts.outHeight)
            while (longest / sampleSize > maxDimension) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val rawBitmap = openStream(context, uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return@runCatching null

            val exifRotation = readExifRotation(context, uri)
            if (exifRotation != 0) {
                val rotated = rotateBitmap(rawBitmap, exifRotation)
                if (rotated !== rawBitmap) rawBitmap.recycle()
                rotated
            } else {
                rawBitmap
            }
        }.getOrNull()
    }

    private fun openStream(context: Context, uri: Uri): InputStream? {
        return context.contentResolver.openInputStream(uri)
    }

    private fun readExifRotation(context: Context, uri: Uri): Int {
        return runCatching {
            openStream(context, uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        }.getOrDefault(0)
    }

    fun rotateBitmap(source: Bitmap, degrees: Int): Bitmap {
        val normalized = ((degrees % 360) + 360) % 360
        if (normalized == 0) return source
        val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    fun applyPerspectiveAndCrop(source: Bitmap, quad: CropQuad): Bitmap {
        if (quad.isDefault) return source
        val w = source.width.toFloat()
        val h = source.height.toFloat()

        val src = floatArrayOf(
            quad.topLeft.x.coerceIn(0f, 1f) * w, quad.topLeft.y.coerceIn(0f, 1f) * h,
            quad.topRight.x.coerceIn(0f, 1f) * w, quad.topRight.y.coerceIn(0f, 1f) * h,
            quad.bottomRight.x.coerceIn(0f, 1f) * w, quad.bottomRight.y.coerceIn(0f, 1f) * h,
            quad.bottomLeft.x.coerceIn(0f, 1f) * w, quad.bottomLeft.y.coerceIn(0f, 1f) * h
        )

        val topWidth = hypot((src[2] - src[0]).toDouble(), (src[3] - src[1]).toDouble())
        val bottomWidth = hypot((src[4] - src[6]).toDouble(), (src[5] - src[7]).toDouble())
        val leftHeight = hypot((src[6] - src[0]).toDouble(), (src[7] - src[1]).toDouble())
        val rightHeight = hypot((src[4] - src[2]).toDouble(), (src[5] - src[3]).toDouble())

        val outW = max(topWidth, bottomWidth).roundToInt().coerceIn(32, source.width * 2)
        val outH = max(leftHeight, rightHeight).roundToInt().coerceIn(32, source.height * 2)

        val dst = floatArrayOf(
            0f, 0f,
            outW.toFloat(), 0f,
            outW.toFloat(), outH.toFloat(),
            0f, outH.toFloat()
        )

        val matrix = Matrix()
        val ok = matrix.setPolyToPoly(src, 0, dst, 0, 4)
        if (!ok) return source

        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, matrix, paint)
        return output
    }

    fun applyFullPipeline(source: Bitmap, adjustments: ImageAdjustments): Bitmap {
        val cropped = if (!adjustments.cropQuad.isDefault) {
            applyPerspectiveAndCrop(source, adjustments.cropQuad)
        } else {
            source
        }
        val rotated = if (adjustments.rotationDegrees % 360 != 0) {
            val r = rotateBitmap(cropped, adjustments.rotationDegrees)
            if (cropped !== source && r !== cropped) cropped.recycle()
            r
        } else {
            cropped
        }
        val filtered = applyFilterAndAdjustments(rotated, adjustments)
        if (rotated !== source && filtered !== rotated) rotated.recycle()
        return filtered
    }

    fun applyFilterAndAdjustments(source: Bitmap, adjustments: ImageAdjustments): Bitmap {
        val isIdentity = adjustments.filter == ImageFilterType.ORIGINAL &&
            adjustments.brightness == 0f &&
            adjustments.contrast == 1f &&
            adjustments.saturation == 1f
        if (isIdentity) return source.copy(Bitmap.Config.ARGB_8888, false)

        val matrix = buildCombinedColorMatrix(adjustments)
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)

        if (adjustments.filter == ImageFilterType.BLACK_AND_WHITE) {
            applyAdaptiveMonochromeThreshold(result)
        }
        return result
    }

    private fun buildCombinedColorMatrix(adjustments: ImageAdjustments): ColorMatrix {
        val base = ColorMatrix()
        when (adjustments.filter) {
            ImageFilterType.ORIGINAL -> Unit
            ImageFilterType.AUTO_ENHANCE -> {
                base.postConcat(createSaturationMatrix(1.14f))
                base.postConcat(createContrastBrightnessMatrix(1.18f, 8f))
            }
            ImageFilterType.DOCUMENT -> {
                base.postConcat(createSaturationMatrix(0.22f))
                base.postConcat(createContrastBrightnessMatrix(1.48f, 22f))
            }
            ImageFilterType.BLACK_AND_WHITE -> {
                base.postConcat(createSaturationMatrix(0f))
                base.postConcat(createContrastBrightnessMatrix(1.35f, 12f))
            }
            ImageFilterType.GRAYSCALE -> {
                base.postConcat(createSaturationMatrix(0f))
            }
            ImageFilterType.BRIGHT -> {
                base.postConcat(createContrastBrightnessMatrix(1.06f, 34f))
            }
            ImageFilterType.CONTRAST -> {
                base.postConcat(createContrastBrightnessMatrix(1.42f, -12f))
            }
            ImageFilterType.COLOR -> {
                base.postConcat(createSaturationMatrix(1.48f))
                base.postConcat(createContrastBrightnessMatrix(1.12f, 4f))
            }
        }

        // Apply user sliders on top
        if (adjustments.saturation != 1f &&
            adjustments.filter != ImageFilterType.GRAYSCALE &&
            adjustments.filter != ImageFilterType.BLACK_AND_WHITE
        ) {
            base.postConcat(createSaturationMatrix(adjustments.saturation))
        }
        if (adjustments.contrast != 1f || adjustments.brightness != 0f) {
            base.postConcat(createContrastBrightnessMatrix(adjustments.contrast, adjustments.brightness))
        }
        return base
    }

    private fun createSaturationMatrix(sat: Float): ColorMatrix {
        return ColorMatrix().apply { setSaturation(sat.coerceIn(0f, 2.5f)) }
    }

    private fun createContrastBrightnessMatrix(contrast: Float, brightness: Float): ColorMatrix {
        val c = contrast.coerceIn(0.3f, 3.0f)
        val translate = (-0.5f * c + 0.5f) * 255f + brightness
        return ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, translate,
                0f, c, 0f, 0f, translate,
                0f, 0f, c, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    private fun applyAdaptiveMonochromeThreshold(bitmap: Bitmap) {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // Compute mean luminance for robust document thresholding
        var sum = 0L
        val step = max(1, pixels.size / 10_000)
        var count = 0
        var idx = 0
        while (idx < pixels.size) {
            val px = pixels[idx]
            val lum = ((px shr 16 and 0xFF) * 299 + (px shr 8 and 0xFF) * 587 + (px and 0xFF) * 114) / 1000
            sum += lum
            count++
            idx += step
        }
        val mean = if (count > 0) (sum / count).toInt() else 128
        val threshold = min(185, max(85, mean - 12))

        for (i in pixels.indices) {
            val px = pixels[i]
            val lum = ((px shr 16 and 0xFF) * 299 + (px shr 8 and 0xFF) * 587 + (px and 0xFF) * 114) / 1000
            pixels[i] = if (lum >= threshold) Color.WHITE else Color.rgb(18, 18, 20)
        }
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }
}
