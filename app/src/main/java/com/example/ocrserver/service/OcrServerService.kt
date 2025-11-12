package com.example.ocrserver.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.ocrserver.MainActivity
import com.example.ocrserver.R
import com.example.ocrserver.server.OcrHttpServer
import com.example.ocrserver.server.OcrWebSocketServer
import com.example.ocrserver.server.RequestLog
import com.example.ocrserver.utils.NetworkUtils
import fi.iki.elonen.NanoHTTPD

class OcrServerService : Service() {

    private var httpServer: OcrHttpServer? = null
    private var webSocketServer: OcrWebSocketServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    private val binder = LocalBinder()
    private var isServerRunning = false
    private var httpPort = DEFAULT_PORT
    private var wsPort = DEFAULT_WS_PORT
    
    private val requestLogListeners = mutableListOf<(RequestLog) -> Unit>()
    private val connectionChangeListeners = mutableListOf<(Int) -> Unit>()

    companion object {
        private const val TAG = "OcrServerService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "ocr_server_channel"
        private const val DEFAULT_PORT = 8080
        private const val DEFAULT_WS_PORT = 8081
        private const val WAKE_LOCK_TAG = "OcrServer::WakeLock"
        
        const val ACTION_START = "com.example.ocrserver.START"
        const val ACTION_STOP = "com.example.ocrserver.STOP"
        const val EXTRA_HTTP_PORT = "http_port"
        const val EXTRA_WS_PORT = "ws_port"
    }

    inner class LocalBinder : Binder() {
        fun getService(): OcrServerService = this@OcrServerService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val hPort = intent.getIntExtra(EXTRA_HTTP_PORT, DEFAULT_PORT)
                val wPort = intent.getIntExtra(EXTRA_WS_PORT, DEFAULT_WS_PORT)
                startServer(hPort, wPort)
            }
            ACTION_STOP -> {
                stopServer()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startServer(hPort: Int = DEFAULT_PORT, wPort: Int = DEFAULT_WS_PORT) {
        if (isServerRunning) {
            Log.w(TAG, "Server is already running")
            return
        }

        httpPort = hPort
        wsPort = wPort

        try {
            httpServer = OcrHttpServer(httpPort, applicationContext) { log ->
                notifyRequestLogged(log)
            }.apply {
                start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            }

            webSocketServer = OcrWebSocketServer(wsPort, applicationContext) { count ->
                notifyConnectionChange(count)
            }.apply {
                start()
            }

            isServerRunning = true
            
            val notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)
            
            Log.i(TAG, "OCR Server started - HTTP:$httpPort, WS:$wsPort")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server", e)
            isServerRunning = false
            stopSelf()
        }
    }

    fun stopServer() {
        if (!isServerRunning) {
            return
        }

        try {
            httpServer?.stop()
            httpServer = null

            webSocketServer?.stop()
            webSocketServer = null

            isServerRunning = false
            
            Log.i(TAG, "OCR Server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server", e)
        }
    }

    fun isRunning(): Boolean = isServerRunning

    fun getServerAddress(): String = NetworkUtils.getServerAddress(httpPort)

    fun getWebSocketAddress(): String = NetworkUtils.getWebSocketAddress(wsPort)

    fun getActiveWebSocketConnections(): Int {
        return webSocketServer?.getActiveConnectionsCount() ?: 0
    }

    fun getRequestLogs(): List<RequestLog> {
        return httpServer?.getRequestLogs() ?: emptyList()
    }

    fun clearRequestLogs() {
        httpServer?.clearLogs()
    }

    fun addRequestLogListener(listener: (RequestLog) -> Unit) {
        requestLogListeners.add(listener)
    }

    fun removeRequestLogListener(listener: (RequestLog) -> Unit) {
        requestLogListeners.remove(listener)
    }

    fun addConnectionChangeListener(listener: (Int) -> Unit) {
        connectionChangeListeners.add(listener)
    }

    fun removeConnectionChangeListener(listener: (Int) -> Unit) {
        connectionChangeListeners.remove(listener)
    }

    private fun notifyRequestLogged(log: RequestLog) {
        requestLogListeners.forEach { it(log) }
        updateNotification()
    }

    private fun notifyConnectionChange(count: Int) {
        connectionChangeListeners.forEach { it(count) }
        updateNotification()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val serverAddress = getServerAddress()
        val wsAddress = getWebSocketAddress()
        val requestCount = getRequestLogs().size
        val wsConnections = getActiveWebSocketConnections()

        val notificationText = buildString {
            append("HTTP: $serverAddress\n")
            append("WS: $wsAddress\n")
            append("Requests: $requestCount | Connections: $wsConnections")
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(serverAddress)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationText))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification() {
        if (!isServerRunning) return

        val notification = createNotification()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKE_LOCK_TAG
            ).apply {
                acquire(10 * 60 * 60 * 1000L)
            }
            Log.d(TAG, "Wake lock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "Wake lock released")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release wake lock", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
        releaseWakeLock()
        requestLogListeners.clear()
        connectionChangeListeners.clear()
        Log.d(TAG, "Service destroyed")
    }
}

