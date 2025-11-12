package com.example.ocrserver.ocr

object OcrLanguageManager {
    private var currentLanguage: OcrLanguage = OcrLanguage.ENGLISH

    fun setCurrentLanguage(language: OcrLanguage) {
        currentLanguage = language
    }

    fun getCurrentLanguage(): OcrLanguage {
        return currentLanguage
    }
}

