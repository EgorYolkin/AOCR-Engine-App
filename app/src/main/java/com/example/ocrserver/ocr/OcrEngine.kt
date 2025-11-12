package com.example.ocrserver.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.system.measureTimeMillis

class OcrEngine(private val context: Context) {
    private val latinRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val chineseRecognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    private val devanagariRecognizer = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
    private val japaneseRecognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    private val koreanRecognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    private val languageIdentifier = LanguageIdentification.getClient()
    
    private var tesseract: TessBaseAPI? = null
    private var isTesseractInitialized = false
    
    companion object {
        private const val TAG = "OcrEngine"
        private const val DEFAULT_CONFIDENCE = 0.0f
        private const val TESSDATA_URL = "https://github.com/tesseract-ocr/tessdata/raw/main/"
    }

    suspend fun recognizeText(bitmap: Bitmap, language: OcrLanguage = OcrLanguage.ENGLISH): OcrResult {
        val startTime = System.currentTimeMillis()
        
        try {
            if (language.tesseractCode != null) {
                return recognizeWithTesseract(bitmap, language, startTime)
            }
            
            val recognizer = getRecognizerForLanguage(language)
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val visionText = recognizer.process(inputImage).await()
            
            val fullText = visionText.text
            if (fullText.isEmpty()) {
                return createEmptyResult(System.currentTimeMillis() - startTime)
            }
            
            val language = detectLanguage(fullText)
            val confidence = calculateAverageConfidence(visionText)
            val blocks = convertToBlocks(visionText)
            
            val processingTime = System.currentTimeMillis() - startTime
            
            Log.d(TAG, "OCR completed in ${processingTime}ms. Text length: ${fullText.length}, Language: $language")
            
            return OcrResult(
                text = fullText,
                confidence = confidence,
                language = language,
                blocks = blocks,
                processingTimeMs = processingTime
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error during OCR processing", e)
            throw OcrException("Failed to recognize text: ${e.message}", e)
        }
    }


    private suspend fun detectLanguage(text: String): String {
        return try {
            val languageCode = languageIdentifier.identifyLanguage(text).await()
            when {
                languageCode == "und" -> "unknown"
                else -> languageCode
            }
        } catch (e: Exception) {
            Log.w(TAG, "Language detection failed", e)
            "unknown"
        }
    }

    private fun calculateAverageConfidence(visionText: Text): Float {
        val blocks = visionText.textBlocks
        if (blocks.isEmpty()) return DEFAULT_CONFIDENCE
        
        var totalConfidence = 0f
        var count = 0
        
        for (block in blocks) {
            for (line in block.lines) {
                for (element in line.elements) {
                    totalConfidence += 1.0f
                    count++
                }
            }
        }
        
        return if (count > 0) totalConfidence / count else DEFAULT_CONFIDENCE
    }

    private fun convertToBlocks(visionText: Text): List<TextBlock> {
        return visionText.textBlocks.map { block ->
            val boundingBox = block.boundingBox?.let {
                BoundingBox(it.left, it.top, it.right, it.bottom)
            }
            
            val lines = block.lines.map { line ->
                val lineBoundingBox = line.boundingBox?.let {
                    BoundingBox(it.left, it.top, it.right, it.bottom)
                }
                TextLine(line.text, lineBoundingBox)
            }
            
            TextBlock(block.text, boundingBox, lines)
        }
    }

    private fun createEmptyResult(processingTime: Long): OcrResult {
        return OcrResult(
            text = "",
            confidence = 0.0f,
            language = "unknown",
            blocks = emptyList(),
            processingTimeMs = processingTime
        )
    }

    private suspend fun recognizeWithTesseract(bitmap: Bitmap, language: OcrLanguage, startTime: Long): OcrResult = withContext(Dispatchers.IO) {
        try {
            if (!isTesseractInitialized) {
                initTesseract(language.tesseractCode!!)
            }
            
            tesseract?.setImage(bitmap)
            val recognizedText = tesseract?.utF8Text ?: ""
            val confidence = tesseract?.meanConfidence()?.toFloat() ?: 0f
            
            val processingTime = System.currentTimeMillis() - startTime
            
            OcrResult(
                text = recognizedText.trim(),
                confidence = confidence / 100f,
                language = language.code,
                blocks = emptyList(),
                processingTimeMs = processingTime
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error during Tesseract OCR", e)
            throw OcrException("Tesseract OCR failed: ${e.message}", e)
        }
    }
    
    private suspend fun initTesseract(langCode: String) = withContext(Dispatchers.IO) {
        try {
            val tessDataDir = File(context.filesDir, "tessdata")
            if (!tessDataDir.exists()) {
                tessDataDir.mkdirs()
            }
            
            val trainedDataFile = File(tessDataDir, "$langCode.traineddata")
            if (!trainedDataFile.exists()) {
                Log.d(TAG, "Extracting Tesseract data for $langCode")
                context.assets.open("tessdata/$langCode.traineddata").use { input ->
                    FileOutputStream(trainedDataFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            
            tesseract = TessBaseAPI().apply {
                init(context.filesDir.absolutePath, langCode)
            }
            isTesseractInitialized = true
            Log.d(TAG, "Tesseract initialized for $langCode")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Tesseract", e)
            throw OcrException("Tesseract initialization failed: ${e.message}", e)
        }
    }
    
    private fun getRecognizerForLanguage(language: OcrLanguage): TextRecognizer {
        return when (language) {
            OcrLanguage.AUTO, OcrLanguage.ENGLISH, OcrLanguage.RUSSIAN -> latinRecognizer
            OcrLanguage.CHINESE -> chineseRecognizer
            OcrLanguage.DEVANAGARI -> devanagariRecognizer
            OcrLanguage.JAPANESE -> japaneseRecognizer
            OcrLanguage.KOREAN -> koreanRecognizer
        }
    }

    fun close() {
        try {
            latinRecognizer.close()
            chineseRecognizer.close()
            devanagariRecognizer.close()
            japaneseRecognizer.close()
            koreanRecognizer.close()
            languageIdentifier.close()
            tesseract?.end()
            tesseract = null
            isTesseractInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "Error closing OCR engine", e)
        }
    }
}

class OcrException(message: String, cause: Throwable? = null) : Exception(message, cause)

