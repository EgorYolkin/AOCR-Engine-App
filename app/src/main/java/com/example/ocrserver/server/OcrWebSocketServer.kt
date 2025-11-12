package com.example.ocrserver.server

import android.graphics.Bitmap
import android.util.Log
import com.example.ocrserver.auth.AuthMiddleware
import com.example.ocrserver.ocr.OcrEngine
import com.example.ocrserver.ocr.OcrLanguageManager
import com.example.ocrserver.utils.ImageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress

class OcrWebSocketServer(
    port: Int = 8080,
    private val context: android.content.Context,
    private val onConnectionChange: (Int) -> Unit = {}
) : WebSocketServer(InetSocketAddress(port)) {

    private val ocrEngine = OcrEngine(context)
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private val activeConnections = mutableSetOf<WebSocket>()

    companion object {
        private const val TAG = "OcrWebSocketServer"
        private const val MESSAGE_TYPE_OCR = "ocr"
        private const val MESSAGE_TYPE_PING = "ping"
        private const val MESSAGE_TYPE_AUTH = "auth"
    }

    override fun onOpen(connection: WebSocket, handshake: ClientHandshake) {
        activeConnections.add(connection)
        val connectionCount = activeConnections.size
        
        Log.d(TAG, "New WebSocket connection from ${connection.remoteSocketAddress}. Total connections: $connectionCount")
        
        sendMessage(connection, createStatusMessage("connected", "Connection established"))
        onConnectionChange(connectionCount)
    }

    override fun onClose(connection: WebSocket, code: Int, reason: String, remote: Boolean) {
        activeConnections.remove(connection)
        val connectionCount = activeConnections.size
        
        Log.d(TAG, "WebSocket connection closed: $reason. Total connections: $connectionCount")
        onConnectionChange(connectionCount)
    }

    override fun onMessage(connection: WebSocket, message: String) {
        Log.d(TAG, "Received message from ${connection.remoteSocketAddress}")
        
        try {
            val json = JSONObject(message)
            val messageType = json.optString("type", MESSAGE_TYPE_OCR)

            when (messageType) {
                MESSAGE_TYPE_AUTH -> handleAuthMessage(connection, json)
                MESSAGE_TYPE_PING -> handlePingMessage(connection)
                MESSAGE_TYPE_OCR -> handleOcrMessage(connection, json)
                else -> sendError(connection, "Unknown message type: $messageType")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing message", e)
            sendError(connection, "Invalid message format: ${e.message}")
        }
    }

    override fun onError(connection: WebSocket?, exception: Exception) {
        Log.e(TAG, "WebSocket error", exception)
        connection?.let {
            sendError(it, "Server error: ${exception.message}")
        }
    }

    override fun onStart() {
        Log.d(TAG, "WebSocket server started on port ${address.port}")
    }

    private fun handleAuthMessage(connection: WebSocket, json: JSONObject) {
        val token = json.optString("token", "")
        
        val headers = mapOf("Authorization" to "Bearer $token")
        val isAuthenticated = AuthMiddleware.isAuthenticated(headers)
        
        if (isAuthenticated) {
            sendMessage(connection, createStatusMessage("authenticated", "Authentication successful"))
        } else {
            sendError(connection, "Authentication failed")
        }
    }

    private fun handlePingMessage(connection: WebSocket) {
        val pongMessage = JSONObject().apply {
            put("type", "pong")
            put("timestamp", System.currentTimeMillis())
        }
        sendMessage(connection, pongMessage)
    }

    private fun handleOcrMessage(connection: WebSocket, json: JSONObject) {
        if (!json.has("image")) {
            sendError(connection, "Missing 'image' field in request")
            return
        }

        val base64Image = json.getString("image")
        val bitmap = ImageUtils.base64ToBitmap(base64Image)

        if (bitmap == null) {
            sendError(connection, "Invalid image data")
            return
        }

        sendMessage(connection, createStatusMessage("processing", "Processing image..."))

        coroutineScope.launch {
            processOcr(connection, bitmap)
        }
    }

    private suspend fun processOcr(connection: WebSocket, bitmap: Bitmap) {
        try {
            val resizedBitmap = ImageUtils.resizeBitmapIfNeeded(bitmap)
            
            val progressMessage = JSONObject().apply {
                put("type", "progress")
                put("status", "recognizing")
                put("progress", 50)
            }
            sendMessage(connection, progressMessage)

            val language = OcrLanguageManager.getCurrentLanguage()
            val ocrResult = ocrEngine.recognizeText(resizedBitmap, language)

            val resultJson = JSONObject().apply {
                put("type", "result")
                put("success", true)
                put("text", ocrResult.text)
                put("confidence", ocrResult.confidence.toDouble())
                put("language", ocrResult.language)
                put("processingTimeMs", ocrResult.processingTimeMs)
                
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
                put("blocks", blocksArray)
            }

            sendMessage(connection, resultJson)

            if (!resizedBitmap.isRecycled) {
                resizedBitmap.recycle()
            }
            if (!bitmap.isRecycled && bitmap != resizedBitmap) {
                bitmap.recycle()
            }

            Log.d(TAG, "OCR completed successfully for WebSocket client")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing OCR via WebSocket", e)
            sendError(connection, "OCR processing failed: ${e.message}")
        }
    }

    private fun createStatusMessage(status: String, message: String): JSONObject {
        return JSONObject().apply {
            put("type", "status")
            put("status", status)
            put("message", message)
            put("timestamp", System.currentTimeMillis())
        }
    }

    private fun sendMessage(connection: WebSocket, json: JSONObject) {
        try {
            if (connection.isOpen) {
                connection.send(json.toString())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
        }
    }

    private fun sendError(connection: WebSocket, errorMessage: String) {
        val errorJson = JSONObject().apply {
            put("type", "error")
            put("error", errorMessage)
            put("timestamp", System.currentTimeMillis())
        }
        sendMessage(connection, errorJson)
    }

    fun getActiveConnectionsCount(): Int {
        return activeConnections.size
    }

    fun broadcastMessage(message: String) {
        val json = JSONObject().apply {
            put("type", "broadcast")
            put("message", message)
            put("timestamp", System.currentTimeMillis())
        }
        
        connections.forEach { connection ->
            if (connection.isOpen) {
                sendMessage(connection, json)
            }
        }
    }

    override fun stop() {
        Log.d(TAG, "Stopping WebSocket server...")
        activeConnections.clear()
        ocrEngine.close()
        super.stop()
    }
}

