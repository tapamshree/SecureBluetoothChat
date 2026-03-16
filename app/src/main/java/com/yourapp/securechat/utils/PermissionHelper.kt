package com.yourapp.securechat.utils

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * PermissionHelper — Handles runtime Bluetooth and location permission requests.
 *
 * Android's permission model for Bluetooth changed significantly in API 31 (Android 12):
 * - Pre-12: Requires BLUETOOTH, BLUETOOTH_ADMIN, and ACCESS_FINE_LOCATION
 * - 12+:    Requires BLUETOOTH_SCAN, BLUETOOTH_CONNECT, BLUETOOTH_ADVERTISE
 *
 * This helper abstracts the version check and provides a simple API
 * for Activities to request and verify permissions.
 *
 * Usage:
 *   if (!PermissionHelper.hasBluetoothPermissions(this)) {
 *       PermissionHelper.requestBluetoothPermissions(this, REQUEST_CODE)
 *   }
 */
object PermissionHelper {

    private const val TAG = "PermissionHelper"

    // Request codes used with Activity.onRequestPermissionsResult
    const val REQUEST_BLUETOOTH_PERMISSIONS = 1001
    const val REQUEST_NOTIFICATION_PERMISSION = 1002

    // -------------------------------------------------------------------------
    // Required permissions (version-dependent)
    // -------------------------------------------------------------------------

    /**
     * Returns the set of Bluetooth-related permissions required on the current API level.
     */
    fun getRequiredBluetoothPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ (API 31+)
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            // Android 8–11 (API 26–30)
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    /**
     * Returns notification permission (Android 13+ only).
     */
    fun getNotificationPermission(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray() // Not needed pre-13
        }
    }

    // -------------------------------------------------------------------------
    // Permission checks
    // -------------------------------------------------------------------------

    /**
     * Checks whether all required Bluetooth permissions have been granted.
     */
    fun hasBluetoothPermissions(context: Context): Boolean {
        return getRequiredBluetoothPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Checks whether notification permission has been granted (Android 13+).
     * Returns true on pre-13 devices where it's not required.
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Checks a single permission.
     */
    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    // -------------------------------------------------------------------------
    // Permission requests
    // -------------------------------------------------------------------------

    /**
     * Requests all required Bluetooth permissions from the user.
     *
     * @param activity    The calling Activity.
     * @param requestCode The request code for onRequestPermissionsResult.
     */
    fun requestBluetoothPermissions(
        activity: Activity,
        requestCode: Int = REQUEST_BLUETOOTH_PERMISSIONS
    ) {
        ActivityCompat.requestPermissions(
            activity,
            getRequiredBluetoothPermissions(),
            requestCode
        )
        Logger.d(TAG, "Requested Bluetooth permissions for API ${Build.VERSION.SDK_INT}")
    }

    /**
     * Requests notification permission (Android 13+).
     */
    fun requestNotificationPermission(
        activity: Activity,
        requestCode: Int = REQUEST_NOTIFICATION_PERMISSION
    ) {
        val permissions = getNotificationPermission()
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, permissions, requestCode)
        }
    }

    /**
     * Requests all permissions needed by the app (BT + notifications).
     */
    fun requestAllPermissions(
        activity: Activity,
        requestCode: Int = REQUEST_BLUETOOTH_PERMISSIONS
    ) {
        val allPermissions = getRequiredBluetoothPermissions() + getNotificationPermission()
        ActivityCompat.requestPermissions(activity, allPermissions, requestCode)
    }

    // -------------------------------------------------------------------------
    // Permission result processing
    // -------------------------------------------------------------------------

    /**
     * Processes the result of a permission request.
     *
     * @param permissions   The requested permissions.
     * @param grantResults  The grant results for each permission.
     * @return true if ALL requested permissions were granted.
     */
    fun allPermissionsGranted(
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        return grantResults.isNotEmpty() &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
    }

    /**
     * Returns a list of permissions that were denied in the result.
     */
    fun getDeniedPermissions(
        permissions: Array<out String>,
        grantResults: IntArray
    ): List<String> {
        return permissions.zip(grantResults.toList())
            .filter { (_, result) -> result != PackageManager.PERMISSION_GRANTED }
            .map { (permission, _) -> permission }
    }

    /**
     * Checks whether the user selected "Don't ask again" for any permission.
     * If true, we need to direct them to app settings instead of re-requesting.
     */
    fun shouldShowRationale(activity: Activity): Boolean {
        return getRequiredBluetoothPermissions().any { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }
    }

    // -------------------------------------------------------------------------
    // Bluetooth adapter checks
    // -------------------------------------------------------------------------

    /**
     * Returns the system BluetoothAdapter, or null if the device doesn't support BT.
     */
    fun getBluetoothAdapter(context: Context): BluetoothAdapter? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter
    }

    /**
     * Checks whether Bluetooth is currently enabled on the device.
     */
    fun isBluetoothEnabled(context: Context): Boolean {
        return try {
            getBluetoothAdapter(context)?.isEnabled == true
        } catch (e: SecurityException) {
            Logger.e(TAG, "Missing BLUETOOTH_CONNECT permission", e)
            false
        }
    }

    /**
     * Returns an Intent to prompt the user to enable Bluetooth.
     * Use with an ActivityResultLauncher.
     */
    fun getEnableBluetoothIntent(): Intent {
        return Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
    }
}
