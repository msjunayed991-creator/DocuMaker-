package com.example.domain.model

import java.util.UUID

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class PageSizePreset(
    val label: String,
    val widthPt: Int,
    val heightPt: Int,
    val subtitle: String
) {
    A4("A4", 595, 842, "210 × 297 mm"),
    A3("A3", 842, 1191, "297 × 420 mm"),
    A5("A5", 420, 595, "148 × 210 mm"),
    LETTER("Letter", 612, 792, "8.5 × 11 in"),
    LEGAL("Legal", 612, 1008, "8.5 × 14 in"),
    CUSTOM("Custom", 595, 842, "Custom mm")
}

enum class PageOrientation(val label: String) {
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

enum class PageMargin(val label: String, val points: Int) {
    NONE("None", 0),
    NARROW("Narrow", 18),
    NORMAL("Normal", 36),
    WIDE("Wide", 54)
}

enum class ImageFitMode(val label: String) {
    FIT("Fit Page"),
    FILL("Fill Page"),
    ORIGINAL("Original")
}

data class PdfPageSettings(
    val sizePreset: PageSizePreset = PageSizePreset.A4,
    val customWidthMm: Int = 210,
    val customHeightMm: Int = 297,
    val orientation: PageOrientation = PageOrientation.PORTRAIT,
    val margin: PageMargin = PageMargin.NORMAL,
    val imageFit: ImageFitMode = ImageFitMode.FIT,
    val maintainAspectRatio: Boolean = true,
    val whiteBackground: Boolean = true,
    val jpegQuality: Int = 88
) {
    fun resolvedPageDimensionsPt(): Pair<Int, Int> {
        val baseW = if (sizePreset == PageSizePreset.CUSTOM) {
            ((customWidthMm.coerceIn(50, 1000)) * 72f / 25.4f).toInt().coerceAtLeast(100)
        } else {
            sizePreset.widthPt
        }
        val baseH = if (sizePreset == PageSizePreset.CUSTOM) {
            ((customHeightMm.coerceIn(50, 1400)) * 72f / 25.4f).toInt().coerceAtLeast(100)
        } else {
            sizePreset.heightPt
        }
        return if (orientation == PageOrientation.PORTRAIT) {
            minOf(baseW, baseH) to maxOf(baseW, baseH)
        } else {
            maxOf(baseW, baseH) to minOf(baseW, baseH)
        }
    }
}

enum class ImageFilterType(val label: String) {
    ORIGINAL("Original"),
    AUTO_ENHANCE("Auto Enhance"),
    DOCUMENT("Document"),
    BLACK_AND_WHITE("B&W"),
    GRAYSCALE("Grayscale"),
    BRIGHT("Bright"),
    CONTRAST("Contrast"),
    COLOR("Color")
}

data class NormPoint(val x: Float, val y: Float)

data class CropQuad(
    val topLeft: NormPoint = NormPoint(0f, 0f),
    val topRight: NormPoint = NormPoint(1f, 0f),
    val bottomRight: NormPoint = NormPoint(1f, 1f),
    val bottomLeft: NormPoint = NormPoint(0f, 1f)
) {
    val isDefault: Boolean
        get() = topLeft.x < 0.01f && topLeft.y < 0.01f &&
            topRight.x > 0.99f && topRight.y < 0.01f &&
            bottomRight.x > 0.99f && bottomRight.y > 0.99f &&
            bottomLeft.x < 0.01f && bottomLeft.y > 0.99f
}

data class ImageAdjustments(
    val filter: ImageFilterType = ImageFilterType.ORIGINAL,
    val brightness: Float = 0f,     // -100f .. +100f
    val contrast: Float = 1f,       // 0.5f .. 2.0f
    val saturation: Float = 1f,     // 0.0f .. 2.0f
    val rotationDegrees: Int = 0,   // 0, 90, 180, 270
    val cropQuad: CropQuad = CropQuad()
)

data class SelectedImageItem(
    val id: String = UUID.randomUUID().toString(),
    val uriString: String,
    val displayName: String = "Page",
    val adjustments: ImageAdjustments = ImageAdjustments()
)

enum class DocumentSourceType(val label: String) {
    IMAGE_TO_PDF("Image to PDF"),
    CAMERA_SCAN("Camera Scan"),
    MERGED("Merged"),
    COMPRESSED("Compressed"),
    ROTATED("Rotated"),
    REORDERED("Reordered"),
    ANNOTATED("Annotated"),
    SIGNED("Signed"),
    IMPORTED("Imported")
}

enum class FileSortOption(val label: String) {
    DATE_DESC("Newest first"),
    DATE_ASC("Oldest first"),
    NAME_ASC("Name (A–Z)"),
    NAME_DESC("Name (Z–A)"),
    SIZE_DESC("Largest first"),
    SIZE_ASC("Smallest first")
}

enum class CompressionLevel(val label: String, val scale: Float, val jpegQuality: Int, val description: String) {
    LOW("Low Compression", 1.0f, 82, "Highest visual fidelity"),
    MEDIUM("Balanced", 0.78f, 65, "Ideal for sharing & email"),
    HIGH("Max Compression", 0.55f, 45, "Smallest possible file size")
}

enum class PdfToolType(
    val title: String,
    val subtitle: String,
    val category: String
) {
    IMAGE_TO_PDF("Image to PDF", "Convert photos with filters & page layout", "Create"),
    CAMERA_SCANNER("Camera Scanner", "Scan docs with edge crop & dewarp", "Create"),
    MERGE_PDF("Merge PDF", "Combine multiple PDFs into one file", "Organize"),
    COMPRESS_PDF("Compress PDF", "Reduce PDF file size offline", "Optimize"),
    PDF_TO_JPG("PDF to JPG", "Export PDF pages as crisp JPG images", "Convert"),
    ROTATE_PDF("Rotate PDF", "Rotate individual or all pages", "Organize"),
    REORDER_PAGES("Reorder Pages", "Rearrange or remove PDF pages", "Organize"),
    ADD_TEXT("Add Text", "Stamp custom text onto PDF pages", "Edit"),
    DRAW_ON_PDF("Draw on PDF", "Freehand markups & highlights", "Edit"),
    ADD_SIGNATURE("Add Signature", "Draw & place reusable signatures", "Edit")
}
