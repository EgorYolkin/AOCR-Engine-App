package com.example.ocrserver

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ocrserver.auth.AuthMiddleware
import com.example.ocrserver.ocr.OcrLanguage
import com.example.ocrserver.ocr.OcrLanguageManager
import com.example.ocrserver.server.RequestLog
import com.example.ocrserver.service.OcrServerService
import com.example.ocrserver.ui.RequestLogAdapter
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class MainActivity : AppCompatActivity() {

    private lateinit var httpPortEditText: TextInputEditText
    private lateinit var wsPortEditText: TextInputEditText
    private lateinit var languageSpinner: Spinner
    private lateinit var authSwitch: SwitchMaterial
    private lateinit var authTokenLayout: TextInputLayout
    private lateinit var authTokenEditText: TextInputEditText
    private lateinit var toggleServerButton: MaterialButton
    private lateinit var serverStatusText: TextView
    private lateinit var serverAddressText: TextView
    private lateinit var websocketAddressText: TextView
    private lateinit var wsConnectionsText: TextView
    private lateinit var statusIndicator: View
    private lateinit var requestLogsRecyclerView: RecyclerView
    private lateinit var clearLogsButton: MaterialButton

    private val requestLogAdapter = RequestLogAdapter()
    private lateinit var prefs: SharedPreferences
    
    private var serverService: OcrServerService? = null
    private var isServiceBound = false

    private val requestLogListener: (RequestLog) -> Unit = { log ->
        runOnUiThread {
            updateRequestLogs()
        }
    }

    private val connectionChangeListener: (Int) -> Unit = { count ->
        runOnUiThread {
            updateWebSocketConnections(count)
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as OcrServerService.LocalBinder
            serverService = binder.getService()
            isServiceBound = true
            
            serverService?.addRequestLogListener(requestLogListener)
            serverService?.addConnectionChangeListener(connectionChangeListener)
            
            updateUI()
            Log.d(TAG, "Service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serverService?.removeRequestLogListener(requestLogListener)
            serverService?.removeConnectionChangeListener(connectionChangeListener)
            serverService = null
            isServiceBound = false
            Log.d(TAG, "Service disconnected")
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
        private const val PREFS_NAME = "OcrServerPrefs"
        private const val KEY_HTTP_PORT = "http_port"
        private const val KEY_WS_PORT = "ws_port"
        private const val KEY_OCR_LANGUAGE = "ocr_language"
        private const val KEY_AUTH_ENABLED = "auth_enabled"
        private const val KEY_AUTH_TOKEN = "auth_token"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        initializeViews()
        loadSettings()
        setupRecyclerView()
        setupListeners()
        requestNotificationPermissionIfNeeded()
        bindToService()
    }

    private fun initializeViews() {
        httpPortEditText = findViewById(R.id.httpPortEditText)
        wsPortEditText = findViewById(R.id.wsPortEditText)
        languageSpinner = findViewById(R.id.languageSpinner)
        authSwitch = findViewById(R.id.authSwitch)
        authTokenLayout = findViewById(R.id.authTokenLayout)
        authTokenEditText = findViewById(R.id.authTokenEditText)
        toggleServerButton = findViewById(R.id.toggleServerButton)
        serverStatusText = findViewById(R.id.serverStatusText)
        serverAddressText = findViewById(R.id.serverAddressText)
        websocketAddressText = findViewById(R.id.websocketAddressText)
        wsConnectionsText = findViewById(R.id.wsConnectionsText)
        statusIndicator = findViewById(R.id.statusIndicator)
        requestLogsRecyclerView = findViewById(R.id.requestLogsRecyclerView)
        clearLogsButton = findViewById(R.id.clearLogsButton)
        
        setupLanguageSpinner()
    }

    private fun setupLanguageSpinner() {
        val languages = OcrLanguage.values().map { it.displayName }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        languageSpinner.adapter = adapter
    }

    private fun loadSettings() {
        httpPortEditText.setText(prefs.getInt(KEY_HTTP_PORT, 8080).toString())
        wsPortEditText.setText(prefs.getInt(KEY_WS_PORT, 8081).toString())
        
        val savedLanguage = prefs.getString(KEY_OCR_LANGUAGE, OcrLanguage.ENGLISH.code) ?: OcrLanguage.ENGLISH.code
        val languageIndex = OcrLanguage.values().indexOfFirst { it.code == savedLanguage }
        if (languageIndex >= 0) {
            languageSpinner.setSelection(languageIndex)
        }
        
        authSwitch.isChecked = prefs.getBoolean(KEY_AUTH_ENABLED, false)
        authTokenEditText.setText(prefs.getString(KEY_AUTH_TOKEN, ""))
        
        authTokenLayout.visibility = if (authSwitch.isChecked) View.VISIBLE else View.GONE
    }

    private fun saveSettings() {
        val httpPort = httpPortEditText.text.toString().toIntOrNull() ?: 8080
        val wsPort = wsPortEditText.text.toString().toIntOrNull() ?: 8081
        val selectedLanguage = OcrLanguage.values()[languageSpinner.selectedItemPosition]
        
        prefs.edit().apply {
            putInt(KEY_HTTP_PORT, httpPort)
            putInt(KEY_WS_PORT, wsPort)
            putString(KEY_OCR_LANGUAGE, selectedLanguage.code)
            putBoolean(KEY_AUTH_ENABLED, authSwitch.isChecked)
            putString(KEY_AUTH_TOKEN, authTokenEditText.text.toString())
            apply()
        }
        
        OcrLanguageManager.setCurrentLanguage(selectedLanguage)
        
        AuthMiddleware.setAuthEnabled(authSwitch.isChecked)
        if (authSwitch.isChecked) {
            AuthMiddleware.setAuthToken(authTokenEditText.text.toString())
        }
    }

    private fun setupRecyclerView() {
        requestLogsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity).apply {
                reverseLayout = true
                stackFromEnd = true
            }
            adapter = requestLogAdapter
        }
    }

    private fun setupListeners() {
        toggleServerButton.setOnClickListener {
            if (serverService?.isRunning() == true) {
                stopServerService()
            } else {
                saveSettings()
                startServerService()
            }
        }

        clearLogsButton.setOnClickListener {
            serverService?.clearRequestLogs()
            updateRequestLogs()
        }
        
        authSwitch.setOnCheckedChangeListener { _, isChecked ->
            authTokenLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (serverService?.isRunning() != true) {
                saveSettings()
            }
        }
        
        httpPortEditText.addTextChangedListener {
            if (serverService?.isRunning() != true) {
                saveSettings()
            }
        }
        
        wsPortEditText.addTextChangedListener {
            if (serverService?.isRunning() != true) {
                saveSettings()
            }
        }
    }

    private fun bindToService() {
        val intent = Intent(this, OcrServerService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun startServerService() {
        val httpPort = httpPortEditText.text.toString().toIntOrNull() ?: 8080
        val wsPort = wsPortEditText.text.toString().toIntOrNull() ?: 8081
        
        val intent = Intent(this, OcrServerService::class.java).apply {
            action = OcrServerService.ACTION_START
            putExtra(OcrServerService.EXTRA_HTTP_PORT, httpPort)
            putExtra(OcrServerService.EXTRA_WS_PORT, wsPort)
        }
        
        httpPortEditText.isEnabled = false
        wsPortEditText.isEnabled = false
        authSwitch.isEnabled = false
        authTokenEditText.isEnabled = false
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        updateUI()
    }

    private fun stopServerService() {
        val intent = Intent(this, OcrServerService::class.java).apply {
            action = OcrServerService.ACTION_STOP
        }
        startService(intent)
        
        httpPortEditText.isEnabled = true
        wsPortEditText.isEnabled = true
        authSwitch.isEnabled = true
        authTokenEditText.isEnabled = true
        
        updateUI()
    }

    private fun updateUI() {
        val isRunning = serverService?.isRunning() ?: false

        if (isRunning) {
            toggleServerButton.text = getString(R.string.stop_server)
            serverStatusText.text = getString(R.string.server_running)
            serverStatusText.setTextColor(getColor(R.color.green_500))
            statusIndicator.setBackgroundResource(R.drawable.status_indicator_running)
            
            val httpAddress = serverService?.getServerAddress() ?: getString(R.string.no_network)
            val wsAddress = serverService?.getWebSocketAddress() ?: getString(R.string.no_network)
            
            serverAddressText.text = "HTTP: $httpAddress"
            websocketAddressText.text = "WS: $wsAddress"
        } else {
            toggleServerButton.text = getString(R.string.start_server)
            serverStatusText.text = getString(R.string.server_stopped)
            serverStatusText.setTextColor(getColor(R.color.red_500))
            statusIndicator.setBackgroundResource(R.drawable.status_indicator_stopped)
            
            serverAddressText.text = getString(R.string.no_network)
            websocketAddressText.text = getString(R.string.no_network)
        }

        updateRequestLogs()
        updateWebSocketConnections(serverService?.getActiveWebSocketConnections() ?: 0)
    }

    private fun updateRequestLogs() {
        val logs = serverService?.getRequestLogs() ?: emptyList()
        requestLogAdapter.submitList(logs.toList())
    }

    private fun updateWebSocketConnections(count: Int) {
        wsConnectionsText.text = count.toString()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isServiceBound) {
            serverService?.removeRequestLogListener(requestLogListener)
            serverService?.removeConnectionChangeListener(connectionChangeListener)
            unbindService(serviceConnection)
            isServiceBound = false
        }
    }
}

