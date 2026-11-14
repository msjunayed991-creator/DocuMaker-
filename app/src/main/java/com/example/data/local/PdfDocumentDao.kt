package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfDocumentDao {
    @Query("SELECT * FROM pdf_documents ORDER BY createdAt DESC")
    fun getAllDocuments(): Flow<List<PdfDocumentEntity>>

    @Query("SELECT * FROM pdf_documents ORDER BY lastOpenedAt DESC LIMIT :limit")
    fun getRecentDocuments(limit: Int = 10): Flow<List<PdfDocumentEntity>>

    @Query("SELECT * FROM pdf_documents WHERE isFavorite = 1 ORDER BY createdAt DESC")
    fun getFavoriteDocuments(): Flow<List<PdfDocumentEntity>>

    @Query("SELECT * FROM pdf_documents WHERE id = :id")
    suspend fun getDocumentById(id: Long): PdfDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: PdfDocumentEntity): Long

    @Update
    suspend fun updateDocument(document: PdfDocumentEntity)

    @Query("UPDATE pdf_documents SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    @Query("UPDATE pdf_documents SET lastOpenedAt = :timestamp WHERE id = :id")
    suspend fun updateLastOpened(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM pdf_documents WHERE id = :id")
    suspend fun deleteById(id: Long)
}
