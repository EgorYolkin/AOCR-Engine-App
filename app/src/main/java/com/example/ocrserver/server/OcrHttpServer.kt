package com.example.ocrserver.server

import android.graphics.Bitmap
import android.util.Log
import com.example.ocrserver.auth.AuthMiddleware
import com.example.ocrserver.ocr.OcrEngine
import com.example.ocrserver.ocr.OcrLanguage
import com.example.ocrserver.ocr.OcrLanguageManager
import com.example.ocrserver.utils.ImageUtils
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class OcrHttpServer(
    private val port: Int = 8080,
    private val context: android.content.Context,
    private val onRequestLogged: (RequestLog) -> Unit = {}
) : NanoHTTPD(port) {

    private val ocrEngine = OcrEngine(context)
    private val requestLogs = mutableListOf<RequestLog>()
    private var requestCount = 0
    private val serverStartTime = System.currentTimeMillis()

    companion object {
        private const val TAG = "OcrHttpServer"
        private const val MAX_LOGS = 100
    }

    override fun serve(session: IHTTPSession): Response {
        val startTime = System.currentTimeMillis()
        val method = session.method.name
        val uri = session.uri
        val clientIp = session.remoteIpAddress ?: "unknown"

        Log.d(TAG, "Received request: $method $uri from $clientIp")

        val response = try {
            when {
                uri == "/status" && method == "GET" -> handleStatus()
                uri == "/ocr" && method == "POST" -> handleOcrRequest(session)
                uri == "/health" && method == "GET" -> handleHealth()
                else -> createErrorResponse(Response.Status.NOT_FOUND, "Endpoint not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling request", e)
            createErrorResponse(Response.Status.INTERNAL_ERROR, "Internal server error: ${e.message}")
        }

        val processingTime = System.currentTimeMillis() - startTime
        logRequest(method, uri, response.status.requestStatus, processingTime, clientIp)

        return response
    }

    private fun handleStatus(): Response {
        val headers = mutableMapOf<String, String>()
        
        if (!AuthMiddleware.isAuthenticated(headers)) {
            return createUnauthorizedResponse()
        }

        val statusJson = JSONObject().apply {
            put("status", "running")
            put("uptime", System.currentTimeMillis() - serverStartTime)
            put("requestCount", requestCount)
            put("port", port)
            put("ocrEngine", "Google ML Kit")
        }

        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            statusJson.toString()
        )
    }

    private fun handleHealth(): Response {
        val healthJson = JSONObject().apply {
            put("status", "healthy")
            put("timestamp", System.currentTimeMillis())
        }

        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            healthJson.toString()
        )
    }

    private fun handleOcrRequest(session: IHTTPSession): Response {
        if (!AuthMiddleware.isAuthenticated(session.headers)) {
            return createUnauthorizedResponse()
        }

        val files = mutableMapOf<String, String>()
        
        try {
            session.parseBody(files)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing request body", e)
            return createErrorResponse(Response.Status.BAD_REQUEST, "Failed to parse request body")
        }

        val bitmap = extractImageFromRequest(session, files)
            ?: return createErrorResponse(Response.Status.BAD_REQUEST, "No valid image provided")

        return processOcrAsync(bitmap)
    }

    private fun extractImageFromRequest(session: IHTTPSession, files: Map<String, String>): Bitmap? {
        if (files.containsKey("image")) {
            val tempFilePath = files["image"]
            if (tempFilePath != null) {
                val imageBytes = java.io.File(tempFilePath).readBytes()
                return ImageUtils.byteArrayToBitmap(imageBytes)
            }
        }

        val contentType = session.headers["content-type"] ?: ""
        if (contentType.contains("application/json")) {
            val bodyLength = session.headers["content-length"]?.toIntOrNull() ?: 0
            val buffer = ByteArray(bodyLength)
            
            try {
                session.inputStream.read(buffer, 0, bodyLength)
                val jsonBody = String(buffer)
                val json = JSONObject(jsonBody)
                
                if (json.has("image")) {
                    val base64Image = json.getString("image")
                    return ImageUtils.base64ToBitmap(base64Image)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting image from JSON", e)
            }
        }

        return null
    }

    private fun processOcrAsync(bitmap: Bitmap): Response {
        val resultJson = JSONObject()
        
        try {
            val resizedBitmap = ImageUtils.resizeBitmapIfNeeded(bitmap)
            
            val language = OcrLanguageManager.getCurrentLanguage()
            Log.d(TAG, "Processing OCR with language: ${language.code}")
            
            val ocrResult = kotlinx.coroutines.runBlocking {
                ocrEngine.recognizeText(resizedBitmap, language)
            }

            resultJson.put("success", true)
            resultJson.put("text", ocrResult.text)
            resultJson.put("confidence", ocrResult.confidence.toDouble())
            resultJson.put("language", ocrResult.language)
            resultJson.put("processingTimeMs", ocrResult.processingTimeMs)
            
            val blocksArray = JSONArray()
            for (block in ocrResult.blocks) {
                val blockJson = JSONObject().apply {
                    put("text", block.text)
                    block.boundingBox?.let {
                        put("boundingBox", JSONObject().apply {
                            put("left", it.left)
                            put("top", it.top)
                            put("right", it.right)
                            put("bottom", it.bottom)
                        })
                    }
                }
                blocksArray.put(blockJson)
            }
            resultJson.put("blocks", blocksArray)

            if (!resizedBitmap.isRecycled) {
                resizedBitmap.recycle()
            }
            if (!bitmap.isRecycled && bitmap != resizedBitmap) {
                bitmap.recycle()
            }

            return newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                resultJson.toString()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error during OCR processing", e)
            return createErrorResponse(
                Response.Status.INTERNAL_ERROR,
                "OCR processing failed: ${e.message}"
            )
        }
    }

    private fun createErrorResponse(status: Response.Status, message: String): Response {
        val errorJson = JSONObject().apply {
            put("success", false)
            put("error", message)
        }

        return newFixedLengthResponse(status, "application/json", errorJson.toString())
    }

    private fun createUnauthorizedResponse(): Response {
        val authResponse = AuthMiddleware.getUnauthorizedResponse()
        val json = JSONObject().apply {
            put("success", false)
            put("error", authResponse.message)
        }

        return newFixedLengthResponse(
            Response.Status.UNAUTHORIZED,
            "application/json",
            json.toString()
        )
    }

    private fun logRequest(method: String, path: String, statusCode: Int, processingTime: Long, clientIp: String) {
        requestCount++
        
        val log = RequestLog(
            method = method,
            path = path,
            statusCode = statusCode,
            processingTimeMs = processingTime,
            clientIp = clientIp
        )

        requestLogs.add(log)
        if (requestLogs.size > MAX_LOGS) {
            requestLogs.removeAt(0)
        }

        onRequestLogged(log)
        Log.i(TAG, log.getFormattedLog())
    }

    fun getRequestLogs(): List<RequestLog> {
        return requestLogs.toList()
    }

    fun clearLogs() {
        requestLogs.clear()
    }

    override fun stop() {
        super.stop()
        ocrEngine.close()
        Log.d(TAG, "HTTP Server stopped")
    }
}

