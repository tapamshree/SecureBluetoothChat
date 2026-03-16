package com.yourapp.securechat.utils

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Extensions — Kotlin extension functions used throughout the app.
 *
 * Keeps boilerplate out of Activities/ViewModels by extending
 * common Android and Kotlin types with reusable helpers.
 */

// -------------------------------------------------------------------------
// Context extensions
// -------------------------------------------------------------------------

/**
 * Shows a short Toast message.
 */
fun Context.showToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

/**
 * Shows a long Toast message.
 */
fun Context.showLongToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}

// -------------------------------------------------------------------------
// String extensions
// -------------------------------------------------------------------------

/**
 * Returns true if this string is a valid Bluetooth MAC address.
 * Format: "AA:BB:CC:DD:EE:FF"
 */
fun String.isValidBluetoothAddress(): Boolean {
    return BluetoothAdapter.checkBluetoothAddress(this.uppercase())
}

/**
 * Masks a Bluetooth MAC address for safe logging.
 * "AA:BB:CC:DD:EE:FF" → "AA:BB:**:**:**:FF"
 */
fun String.maskBluetoothAddress(): String {
    val parts = this.split(":")
    if (parts.size != 6) return this
    return "${parts[0]}:${parts[1]}:**:**:**:${parts[5]}"
}

// -------------------------------------------------------------------------
// Long (timestamp) extensions
// -------------------------------------------------------------------------

/**
 * Formats a Unix epoch timestamp (millis) into "HH:mm" time string.
 */
fun Long.toTimeString(): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(this))
}

/**
 * Formats a Unix epoch timestamp (millis) into "MMM dd, yyyy" date string.
 */
fun Long.toDateString(): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(this))
}

/**
 * Formats a Unix epoch timestamp into "MMM dd, HH:mm" for display.
 */
fun Long.toDateTimeString(): String {
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return sdf.format(Date(this))
}

// -------------------------------------------------------------------------
// ByteArray extensions
// -------------------------------------------------------------------------

/**
 * Converts this byte array to a hex string via [ByteUtils].
 */
fun ByteArray.toHex(): String = ByteUtils.toHexString(this)

/**
 * Converts this byte array to a Base64 string via [ByteUtils].
 */
fun ByteArray.toBase64(): String = ByteUtils.toBase64(this)
