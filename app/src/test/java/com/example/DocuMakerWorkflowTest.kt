package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.DocuMakerDatabase
import com.example.data.local.PdfRepository
import com.example.data.processing.ImageProcessor
import com.example.data.processing.PdfEngine
import com.example.domain.model.CropQuad
import com.example.domain.model.DocumentSourceType
import com.example.domain.model.ImageAdjustments
import com.example.domain.model.ImageFilterType
import com.example.domain.model.NormPoint
import com.example.domain.model.PageMargin
import com.example.domain.model.PageOrientation
import com.example.domain.model.PageSizePreset
import com.example.domain.model.PdfPageSettings
import com.example.domain.model.SelectedImageItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

@Implements(PdfDocument::class)
class TestShadowPdfDocument {
    private val pages = mutableListOf<PdfDocument.PageInfo>()
    private var currentBitmap: Bitmap? = null
    private var closed = false

    @Implementation
    fun __constructor__() {
        // Skip nativeCreateDocument on JVM
    }

    @Implementation
    fun startPage(pageInfo: PdfDocument.PageInfo): PdfDocument.Page {
        check(!closed) { "document is closed!" }
        val bmp = Bitmap.createBitmap(
            pageInfo.pageWidth.coerceAtLeast(1),
            pageInfo.pageHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        currentBitmap = bmp
        val canvas = Canvas(bmp)
        pages.add(pageInfo)
        return ReflectionHelpers.callConstructor(
            PdfDocument.Page::class.java,
            ReflectionHelpers.ClassParameter.from(Canvas::class.java, canvas),
            ReflectionHelpers.ClassParameter.from(PdfDocument.PageInfo::class.java, pageInfo)
        )
    }

    @Implementation
    fun finishPage(page: PdfDocument.Page) {
        currentBitmap?.recycle()
        currentBitmap = null
    }

    @Implementation
    fun writeTo(out: OutputStream) {
        check(!closed) { "document is closed!" }
        val payload = "%PDF-1.4\n% DocuMaker Test PDF (${pages.size} pages)\n%%EOF\n"
        out.write(payload.toByteArray(Charsets.UTF_8))
    }

    @Implementation
    fun close() {
        closed = true
        currentBitmap?.recycle()
        currentBitmap = null
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], shadows = [TestShadowPdfDocument::class])
class DocuMakerWorkflowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `page settings resolve exact standard and custom dimensions`() {
        val a4Portrait = PdfPageSettings(sizePreset = PageSizePreset.A4, orientation = PageOrientation.PORTRAIT)
        assertEquals(595 to 842, a4Portrait.resolvedPageDimensionsPt())

        val a4Landscape = a4Portrait.copy(orientation = PageOrientation.LANDSCAPE)
        assertEquals(842 to 595, a4Landscape.resolvedPageDimensionsPt())

        val letter = PdfPageSettings(sizePreset = PageSizePreset.LETTER, orientation = PageOrientation.PORTRAIT)
        assertEquals(612 to 792, letter.resolvedPageDimensionsPt())

        val custom = PdfPageSettings(sizePreset = PageSizePreset.CUSTOM, customWidthMm = 100, customHeightMm = 200)
        val (w, h) = custom.resolvedPageDimensionsPt()
        assertTrue(h > w)
        assertTrue(w in 275..290)
    }

    @Test
    fun `image processor applies all 8 filters rotation and perspective crop`() {
        val bmp = Bitmap.createBitmap(120, 160, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(210, 140, 100))
        }

        ImageFilterType.entries.forEach { filter ->
            val out = ImageProcessor.applyFullPipeline(
                bmp,
                ImageAdjustments(
                    filter = filter,
                    brightness = 12f,
                    contrast = 1.15f,
                    saturation = 1.1f,
                    rotationDegrees = 90,
                    cropQuad = CropQuad(
                        NormPoint(0.05f, 0.05f),
                        NormPoint(0.95f, 0.08f),
                        NormPoint(0.92f, 0.95f),
                        NormPoint(0.08f, 0.92f)
                    )
                )
            )
            assertTrue(out.width > 0 && out.height > 0)
            out.recycle()
        }
        bmp.recycle()
    }

    @Test
    fun `complete image to pdf generation and repository lifecycle works`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val img1 = File(context.cacheDir, "test_page_1.png")
        val img2 = File(context.cacheDir, "test_page_2.png")
        listOf(img1 to Color.RED, img2 to Color.DKGRAY).forEach { (file, color) ->
            val b = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
            b.eraseColor(color)
            FileOutputStream(file).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            b.recycle()
        }

        val items = listOf(
            SelectedImageItem(
                uriString = Uri.fromFile(img1).toString(),
                displayName = "Page 1",
                adjustments = ImageAdjustments(filter = ImageFilterType.DOCUMENT)
            ),
            SelectedImageItem(
                uriString = Uri.fromFile(img2).toString(),
                displayName = "Page 2",
                adjustments = ImageAdjustments(filter = ImageFilterType.BLACK_AND_WHITE, rotationDegrees = 90)
            )
        )

        val db = DocuMakerDatabase.getInstance(context)
        val repo = PdfRepository(context, db.pdfDocumentDao())
        val outFile = repo.createUniquePdfFile("UnitTest_Doc")

        var lastProgress = 0
        val pages = PdfEngine.generatePdfFromImages(
            context = context,
            items = items,
            settings = PdfPageSettings(
                sizePreset = PageSizePreset.A4,
                margin = PageMargin.NORMAL,
                whiteBackground = true
            ),
            outputFile = outFile,
            onProgress = { cur, _ -> lastProgress = cur }
        )

        assertEquals(2, pages)
        assertEquals(2, lastProgress)
        assertTrue(outFile.exists() && outFile.length() > 10L)

        val header = outFile.inputStream().use { stream ->
            ByteArray(4).also { stream.read(it) }.decodeToString()
        }
        assertEquals("%PDF", header)

        val saved = repo.registerGeneratedPdf(outFile, pages, DocumentSourceType.IMAGE_TO_PDF)
        assertNotNull(saved.id)

        val renamed = repo.renameDocument(saved, "Renamed_Test_Doc")
        assertNotNull(renamed)
        assertEquals("Renamed_Test_Doc.pdf", renamed!!.fileName)

        repo.toggleFavorite(renamed)
        val favs = repo.favoriteDocuments.first()
        assertTrue(favs.any { it.id == renamed.id })

        repo.deleteDocument(renamed)
        img1.delete()
        img2.delete()
    }

    @Test
    fun `bottom navigation and create sheet navigate cleanly`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("DocuMaker").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_files").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("My PDF Files").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_tools").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("PDF Toolkit").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("100% On-Device Processing").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_create_fab").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Create New PDF").assertIsDisplayed()
        composeRule.onNodeWithTag("sheet_image_to_pdf").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Select Images to Convert").assertIsDisplayed()
    }
}
