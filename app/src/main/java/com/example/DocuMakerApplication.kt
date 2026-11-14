package com.example

import android.app.Application
import com.example.data.local.DocuMakerDatabase
import com.example.data.local.PdfRepository
import com.example.data.local.SettingsRepository

class AppContainer(application: Application) {
    private val database = DocuMakerDatabase.getInstance(application)
    val pdfRepository = PdfRepository(application, database.pdfDocumentDao())
    val settingsRepository = SettingsRepository(application)
}

class DocuMakerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
