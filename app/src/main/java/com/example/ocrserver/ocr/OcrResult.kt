package com.example.ocrserver.ocr

data class OcrResult(
    val text: String,
    val confidence: Float,
    val language: String,
    val blocks: List<TextBlock>,
    val processingTimeMs: Long
)

data class TextBlock(
    val text: String,
    val boundingBox: BoundingBox?,
    val lines: List<TextLine>
)

data class TextLine(
    val text: String,
    val boundingBox: BoundingBox?
)

data class BoundingBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

