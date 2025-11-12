package com.example.ocrserver.auth

import android.util.Log

object AuthMiddleware {
    private const val TAG = "AuthMiddleware"
    private const val AUTH_HEADER = "Authorization"
    
    private var authEnabled = false
    private var authToken: String? = null

    fun setAuthEnabled(enabled: Boolean) {
        authEnabled = enabled
        Log.d(TAG, "Authentication ${if (enabled) "enabled" else "disabled"}")
    }

    fun setAuthToken(token: String?) {
        authToken = token
        Log.d(TAG, "Auth token ${if (token != null) "set" else "cleared"}")
    }

    fun isAuthenticated(headers: Map<String, String>): Boolean {
        if (!authEnabled) {
            return true
        }

        if (authToken == null) {
            Log.w(TAG, "Auth is enabled but no token is set")
            return true
        }

        val providedAuth = headers[AUTH_HEADER] ?: headers[AUTH_HEADER.lowercase()]
        
        if (providedAuth == null) {
            Log.d(TAG, "No authorization header provided")
            return false
        }

        val isValid = validateToken(providedAuth)
        Log.d(TAG, "Token validation result: $isValid")
        return isValid
    }

    private fun validateToken(providedToken: String): Boolean {
        val expectedToken = authToken ?: return true
        
        val cleanProvidedToken = providedToken.removePrefix("Bearer ").trim()
        
        return cleanProvidedToken == expectedToken
    }

    fun getUnauthorizedResponse(): AuthResponse {
        return AuthResponse(
            authorized = false,
            message = "Unauthorized: Invalid or missing authentication token"
        )
    }
}

data class AuthResponse(
    val authorized: Boolean,
    val message: String
)

