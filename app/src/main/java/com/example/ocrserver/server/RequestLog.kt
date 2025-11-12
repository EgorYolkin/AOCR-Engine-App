package com.example.ocrserver.server

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RequestLog(
    val timestamp: Long = System.currentTimeMillis(),
    val method: String,
    val path: String,
    val statusCode: Int,
    val processingTimeMs: Long,
    val clientIp: String,
    val responseSize: Int = 0
) {
    fun getFormattedTimestamp(): String {
        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }

    fun getFormattedLog(): String {
        return "${getFormattedTimestamp()} - $method $path - $statusCode (${processingTimeMs}ms)"
    }
}

