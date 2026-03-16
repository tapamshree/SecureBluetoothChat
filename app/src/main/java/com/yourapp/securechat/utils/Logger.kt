package com.yourapp.securechat.utils

import android.util.Log

/**
 * Logger — Centralized debug logging utility.
 *
 * All app logs go through this class so logging can be:
 * - Completely disabled in release builds
 * - Tagged consistently with the app name
 * - Filtered by severity level
 *
 * Usage:
 *   Logger.d("BluetoothServer", "Client connected from ${device.name}")
 *   Logger.e("AESCipher", "Decryption failed", exception)
 */
object Logger {

    private const val APP_TAG = "SecureChat"

    /**
     * Master toggle — set to false in release builds.
     * Controlled by BuildConfig.DEBUG at initialization.
     */
    var isEnabled: Boolean = true

    /**
     * Debug-level log.
     * Use for routine operational messages (connection state changes, etc.).
     */
    fun d(tag: String, message: String) {
        if (isEnabled) Log.d("$APP_TAG:$tag", message)
    }

    /**
     * Info-level log.
     * Use for significant lifecycle events (service started, key generated, etc.).
     */
    fun i(tag: String, message: String) {
        if (isEnabled) Log.i("$APP_TAG:$tag", message)
    }

    /**
     * Warning-level log.
     * Use for recoverable issues (retry attempt, fallback to TEE, etc.).
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (isEnabled) {
            if (throwable != null) {
                Log.w("$APP_TAG:$tag", message, throwable)
            } else {
                Log.w("$APP_TAG:$tag", message)
            }
        }
    }

    /**
     * Error-level log.
     * Use for failures that need attention (decryption failed, socket error, etc.).
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (isEnabled) {
            if (throwable != null) {
                Log.e("$APP_TAG:$tag", message, throwable)
            } else {
                Log.e("$APP_TAG:$tag", message)
            }
        }
    }

    /**
     * Verbose-level log.
     * Use for detailed tracing (raw byte dumps, step-by-step flow, etc.).
     * Only visible when explicitly filtered in Logcat.
     */
    fun v(tag: String, message: String) {
        if (isEnabled) Log.v("$APP_TAG:$tag", message)
    }
}
