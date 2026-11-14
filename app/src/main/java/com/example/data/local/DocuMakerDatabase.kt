package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PdfDocumentEntity::class], version = 1, exportSchema = false)
abstract class DocuMakerDatabase : RoomDatabase() {
    abstract fun pdfDocumentDao(): PdfDocumentDao

    companion object {
        @Volatile
        private var INSTANCE: DocuMakerDatabase? = null

        fun getInstance(context: Context): DocuMakerDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DocuMakerDatabase::class.java,
                    "documaker_local.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
