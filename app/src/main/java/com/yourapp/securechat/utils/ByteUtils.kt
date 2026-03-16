package com.yourapp.securechat.utils

import android.util.Base64

/**
 * ByteUtils — Utility functions for byte array conversions.
 *
 * Used throughout the app for:
 * - Converting encrypted payloads to hex strings for logging
 * - Base64 encoding/decoding for display purposes
 * - Byte array comparison and manipulation
 */
object ByteUtils {

    // -------------------------------------------------------------------------
    // Hex conversions
    // -------------------------------------------------------------------------

    /**
     * Converts a byte array to a lowercase hexadecimal string.
     * Example: byteArrayOf(0xDE.toByte(), 0xAD.toByte()) → "dead"
     */
    fun toHexString(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Converts a hexadecimal string to a byte array.
     * Example: "dead" → byteArrayOf(0xDE.toByte(), 0xAD.toByte())
     *
     * @throws IllegalArgumentException if the string length is odd or contains non-hex chars.
     */
    fun fromHexString(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Hex string must have an even length, got ${hex.length}" }
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    // -------------------------------------------------------------------------
    // Base64 conversions
    // -------------------------------------------------------------------------

    /**
     * Encodes a byte array to a Base64 string (no wrapping, no padding).
     */
    fun toBase64(bytes: ByteArray): String {
        return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /**
     * Decodes a Base64 string back to a byte array.
     */
    fun fromBase64(base64: String): ByteArray {
        return Base64.decode(base64, Base64.NO_WRAP or Base64.NO_PADDING)
    }

    // -------------------------------------------------------------------------
    // Byte array utilities
    // -------------------------------------------------------------------------

    /**
     * Constant-time byte array comparison to prevent timing attacks.
     * Returns true only if both arrays have the same length and identical content.
     */
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }

    /**
     * Truncates a byte array for safe logging (shows first N bytes as hex).
     * Example: truncateForLog(bytes, 4) → "deadbeef... (128 bytes)"
     */
    fun truncateForLog(bytes: ByteArray, maxBytes: Int = 8): String {
        return if (bytes.size <= maxBytes) {
            toHexString(bytes)
        } else {
            "${toHexString(bytes.copyOfRange(0, maxBytes))}... (${bytes.size} bytes)"
        }
    }

    /**
     * Concatenates two byte arrays into a single array.
     */
    fun concat(a: ByteArray, b: ByteArray): ByteArray {
        val result = ByteArray(a.size + b.size)
        System.arraycopy(a, 0, result, 0, a.size)
        System.arraycopy(b, 0, result, a.size, b.size)
        return result
    }
}
