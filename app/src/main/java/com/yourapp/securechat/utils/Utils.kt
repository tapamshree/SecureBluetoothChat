package com.yourapp.securechat.utils

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ============================================================
 * FILE: utils/Utils.kt
 * PROJECT: SecureBluetoothChat
 * ============================================================
 *
 * PURPOSE:
 * Consolidated utility source. Hosts stateless helper objects:
 *   - [ByteUtils]
 *   - [Extensions]
 *   - [PermissionHelper]
 *   - [Logger]
 */

object ByteUtils {
    fun toHexString(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    fun fromHexString(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Hex string must be even length." }
        return ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }
    fun toBase64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.NO_PADDING)
    fun fromBase64(base64: String): ByteArray = Base64.decode(base64, Base64.NO_WRAP or Base64.NO_PADDING)
}

object Extensions {
    fun Context.toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    fun View.show() { visibility = View.VISIBLE }
    fun View.hide() { visibility = View.GONE }
    fun Long.toFormattedTime(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(this))
}

object PermissionHelper {
    fun getRequiredBluetoothPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

    fun hasBluetoothPermissions(context: Context): Boolean =
        getRequiredBluetoothPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    fun missingPermissions(context: Context): Array<String> =
        getRequiredBluetoothPermissions()
            .filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
            .toTypedArray()

    fun registerLauncher(
        activity: androidx.activity.ComponentActivity,
        onResult: (allGranted: Boolean, denied: List<String>) -> Unit
    ): androidx.activity.result.ActivityResultLauncher<Array<String>> {
        return activity.registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            val denied = results.filterValues { !it }.keys.toList()
            onResult(denied.isEmpty(), denied)
        }
    }

    fun getBluetoothAdapter(context: Context): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    fun requestBluetoothPermissions(activity: Activity, requestCode: Int = 1001) {
        ActivityCompat.requestPermissions(activity, getRequiredBluetoothPermissions(), requestCode)
    }

    fun getEnableBluetoothIntent(): Intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
}

object Logger {
    private const val APP_TAG = "SecureChat"
    var isEnabled: Boolean = true
    fun d(tag: String, message: String) { if (isEnabled) Log.d("$APP_TAG:$tag", message) }
    fun i(tag: String, message: String) { if (isEnabled) Log.i("$APP_TAG:$tag", message) }
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (!isEnabled) return
        if (throwable != null) Log.w("$APP_TAG:$tag", message, throwable) else Log.w("$APP_TAG:$tag", message)
    }
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (!isEnabled) return
        if (throwable != null) Log.e("$APP_TAG:$tag", message, throwable) else Log.e("$APP_TAG:$tag", message)
    }
    fun v(tag: String, message: String) { if (isEnabled) Log.v("$APP_TAG:$tag", message) }
}
