package com.example.ocrserver.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {
    private const val TAG = "NetworkUtils"
    private const val DEFAULT_PORT = 8080

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in interfaces) {
                if (!networkInterface.isUp || networkInterface.isLoopback) {
                    continue
                }
                
                val addresses = Collections.list(networkInterface.inetAddresses)
                for (address in addresses) {
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        val hostAddress = address.hostAddress
                        if (hostAddress != null && !hostAddress.startsWith("127.")) {
                            Log.d(TAG, "Found IP address: $hostAddress on interface ${networkInterface.name}")
                            return hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local IP address", e)
        }
        return null
    }

    fun getServerAddress(port: Int = DEFAULT_PORT): String {
        val ipAddress = getLocalIpAddress()
        return if (ipAddress != null) {
            "http://$ipAddress:$port"
        } else {
            "No network connection"
        }
    }

    fun getWebSocketAddress(port: Int = DEFAULT_PORT): String {
        val ipAddress = getLocalIpAddress()
        return if (ipAddress != null) {
            "ws://$ipAddress:$port/ws"
        } else {
            "No network connection"
        }
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun isWifiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}

