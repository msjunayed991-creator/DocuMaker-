package com.example.data.local

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.data.processing.PdfEngine
import com.example.domain.model.DocumentSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfRepository(
    private val context: Context,
    private val dao: PdfDocumentDao
) {
    val pdfDir: File = File(context.filesDir, "pdfs").apply { mkdirs() }
    val signatureDir: File = File(context.filesDir, "signatures").apply { mkdirs() }
    val cameraCacheDir: File = File(context.cacheDir, "camera_captures").apply { mkdirs() }

    val allDocuments: Flow<List<PdfDocumentEntity>> = dao.getAllDocuments()
    val recentDocuments: Flow<List<PdfDocumentEntity>> = dao.getRecentDocuments(limit = 8)
    val favoriteDocuments: Flow<List<PdfDocumentEntity>> = dao.getFavoriteDocuments()

    suspend fun getDocumentById(id: Long): PdfDocumentEntity? = dao.getDocumentById(id)

    fun createUniquePdfFile(desiredBaseName: String): File {
        val sanitized = desiredBaseName
            .trim()
            .removeSuffix(".pdf")
            .replace(Regex("[^a-zA-Z0-9._ -]"), "_")
            .ifBlank { "DocuMaker_${System.currentTimeMillis()}" }
        var candidate = File(pdfDir, "$sanitized.pdf")
        var counter = 1
        while (candidate.exists()) {
            candidate = File(pdfDir, "${sanitized}_$counter.pdf")
            counter++
        }
        return candidate
    }

    suspend fun registerGeneratedPdf(
        file: File,
        pageCount: Int,
        sourceType: DocumentSourceType
    ): PdfDocumentEntity = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val entity = PdfDocumentEntity(
            fileName = file.name,
            filePath = file.absolutePath,
            fileSizeBytes = file.length(),
            pageCount = pageCount,
            createdAt = now,
            lastOpenedAt = now,
            isFavorite = false,
            sourceType = sourceType.name
        )
        val id = dao.insertDocument(entity)
        entity.copy(id = id)
    }

    suspend fun importExternalPdf(uri: Uri): PdfDocumentEntity? = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = queryFileName(uri) ?: "Imported_${System.currentTimeMillis()}.pdf"
            val destFile = createUniquePdfFile(displayName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@runCatching null

            val pages = PdfEngine.getPdfPageCount(destFile)
            if (pages <= 0) {
                destFile.delete()
                return@runCatching null
            }
            registerGeneratedPdf(destFile, pages, DocumentSourceType.IMPORTED)
        }.getOrNull()
    }

    suspend fun copyUriToTempFile(uri: Uri): File? = withContext(Dispatchers.IO) {
        runCatching {
            val tempDir = File(context.cacheDir, "temp_pdfs").apply { mkdirs() }
            val tempFile = File(tempDir, "temp_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            tempFile
        }.getOrNull()
    }

    suspend fun exportPdfToUri(sourceFile: File, destUri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(destUri)?.use { output ->
                sourceFile.inputStream().use { input -> input.copyTo(output) }
            } ?: return@runCatching false
            true
        }.getOrDefault(false)
    }

    suspend fun renameDocument(doc: PdfDocumentEntity, newBaseName: String): PdfDocumentEntity? =
        withContext(Dispatchers.IO) {
            val clean = newBaseName.trim().removeSuffix(".pdf")
                .replace(Regex("[^a-zA-Z0-9._ -]"), "_")
            if (clean.isBlank()) return@withContext null

            val oldFile = File(doc.filePath)
            val newFile = File(pdfDir, "$clean.pdf")
            if (oldFile.absolutePath != newFile.absolutePath && newFile.exists()) {
                return@withContext null
            }
            val renamed = if (oldFile.exists()) oldFile.renameTo(newFile) else true
            if (!renamed) return@withContext null

            val updated = doc.copy(
                fileName = newFile.name,
                filePath = newFile.absolutePath
            )
            dao.updateDocument(updated)
            updated
        }

    suspend fun toggleFavorite(doc: PdfDocumentEntity) {
        dao.setFavorite(doc.id, !doc.isFavorite)
    }

    suspend fun markOpened(docId: Long) {
        dao.updateLastOpened(docId)
    }

    suspend fun deleteDocument(doc: PdfDocumentEntity) = withContext(Dispatchers.IO) {
        runCatching { File(doc.filePath).delete() }
        dao.deleteById(doc.id)
    }

    fun getShareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun createCameraCaptureUri(): Uri {
        val file = File(cameraCacheDir, "scan_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun createCameraCaptureFile(): File {
        return File(cameraCacheDir, "scan_${System.currentTimeMillis()}.jpg")
    }

    suspend fun saveSignature(bitmap: Bitmap): File = withContext(Dispatchers.IO) {
        val file = File(signatureDir, "sig_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        file
    }

    suspend fun listSavedSignatures(): List<File> = withContext(Dispatchers.IO) {
        signatureDir.listFiles { f -> f.extension.equals("png", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    suspend fun deleteSignature(file: File) = withContext(Dispatchers.IO) {
        file.delete()
    }

    suspend fun loadBitmapFromFile(file: File): Bitmap? = withContext(Dispatchers.IO) {
        BitmapFactory.decodeFile(file.absolutePath)
    }

    suspend fun clearCacheBytes(): Long = withContext(Dispatchers.IO) {
        var freed = 0L
        context.cacheDir.walkBottomUp().forEach {
            if (it.isFile) {
                freed += it.length()
                it.delete()
            }
        }
        freed
    }

    suspend fun getTotalLibraryBytes(): Long = withContext(Dispatchers.IO) {
        pdfDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    private fun queryFileName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }
}
