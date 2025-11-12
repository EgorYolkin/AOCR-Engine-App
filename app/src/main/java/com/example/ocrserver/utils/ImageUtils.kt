package com.example.ocrserver.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream

object ImageUtils {
    private const val TAG = "ImageUtils"
    private const val MAX_IMAGE_DIMENSION = 2048
    private const val COMPRESSION_QUALITY = 85

    fun base64ToBitmap(base64String: String): Bitmap? {
        return try {
            val cleanBase64 = base64String.replace("data:image/[^;]+;base64,".toRegex(), "")
            val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding base64 to bitmap", e)
            null
        }
    }

    fun byteArrayToBitmap(bytes: ByteArray): Bitmap? {
        return try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding byte array to bitmap", e)
            null
        }
    }

    fun bitmapToBase64(bitmap: Bitmap, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(format, COMPRESSION_QUALITY, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun resizeBitmapIfNeeded(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= MAX_IMAGE_DIMENSION && height <= MAX_IMAGE_DIMENSION) {
            return bitmap
        }
        
        val scale = if (width > height) {
            MAX_IMAGE_DIMENSION.toFloat() / width
        } else {
            MAX_IMAGE_DIMENSION.toFloat() / height
        }
        
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        
        Log.d(TAG, "Resizing bitmap from ${width}x${height} to ${newWidth}x${newHeight}")
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    fun validateImageData(data: ByteArray): Boolean {
        if (data.isEmpty()) {
            Log.w(TAG, "Image data is empty")
            return false
        }
        
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(data, 0, data.size, options)
        
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            Log.w(TAG, "Invalid image dimensions")
            return false
        }
        
        return true
    }
}

