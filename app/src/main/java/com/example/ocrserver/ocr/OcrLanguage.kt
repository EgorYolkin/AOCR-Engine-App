package com.example.ocrserver.ocr

enum class OcrLanguage(val code: String, val displayName: String, val tesseractCode: String? = null) {
    AUTO("auto", "Auto Detect", null),
    ENGLISH("eng", "English", "eng"),
    RUSSIAN("rus", "Russian (Русский)", "rus"),
    CHINESE("chinese", "Chinese", null),
    DEVANAGARI("devanagari", "Devanagari", null),
    JAPANESE("japanese", "Japanese", null),
    KOREAN("korean", "Korean", null);
    
    companion object {
        fun fromCode(code: String): OcrLanguage {
            return values().find { it.code == code } ?: AUTO
        }
    }
}

