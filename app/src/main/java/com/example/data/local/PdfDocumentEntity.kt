package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.DocumentSourceType

@Entity(tableName = "pdf_documents")
data class PdfDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val filePath: String,
    val fileSizeBytes: Long,
    val pageCount: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val sourceType: String = DocumentSourceType.IMAGE_TO_PDF.name
)
