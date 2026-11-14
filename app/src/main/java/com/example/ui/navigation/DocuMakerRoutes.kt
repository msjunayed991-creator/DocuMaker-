package com.example.ui.navigation

import com.example.domain.model.PdfToolType

object DocuMakerRoutes {
    const val HOME = "home"
    const val FILES = "files"
    const val TOOLS = "tools"
    const val SETTINGS = "settings"

    const val IMAGE_TO_PDF = "image_to_pdf"
    const val CAMERA_SCANNER = "camera_scanner"
    const val PDF_READER = "pdf_reader/{docId}"
    const val TOOL_WORKFLOW = "tool_workflow/{toolType}"

    fun pdfReaderRoute(docId: Long): String = "pdf_reader/$docId"
    fun toolWorkflowRoute(toolType: PdfToolType): String = "tool_workflow/${toolType.name}"
}
