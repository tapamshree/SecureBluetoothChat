package com.yourapp.securechat

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.yourapp.securechat.service.BluetoothChatService
import com.yourapp.securechat.ui.device.DeviceListActivity
import com.yourapp.securechat.utils.Extensions.toast
import com.yourapp.securechat.utils.Logger
import com.yourapp.securechat.utils.PermissionHelper

/**
 * Main dashboard activity.
 *
 * Responsibilities:
 *  - Request Bluetooth + notification permissions on launch
 *  - Prompt user to enable Bluetooth if off
 *  - Offer Server / Client mode selection
 *  - Navigate to [DeviceListActivity] (client) or start server and go to chat
 */
class MainActivity : AppCompatActivity() {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    // ── Permission launcher ───────────────────────────────────────────────────

    private val permissionLauncher = PermissionHelper.registerLauncher(this) { allGranted, denied ->
        if (allGranted) {
            onPermissionsGranted()
        } else {
            Logger.w(TAG, "Permissions denied: $denied")
            toast("Bluetooth permissions are required to use this app.")
            showPermissionRationale(denied)
        }
    }

    // ── Enable BT launcher ────────────────────────────────────────────────────

    private val enableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            showModeDialog()
        } else {
            toast("Bluetooth must be enabled to use Secure Chat.")
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Logger.d(TAG, "onCreate")

        if (!isBtSupported()) {
            AlertDialog.Builder(this)
                .setTitle("Not Supported")
                .setMessage("This device does not support Bluetooth.")
                .setPositiveButton("OK") { _, _ -> finish() }
                .show()
            return
        }

        requestPermissionsIfNeeded()
    }

    // ── Permission flow ───────────────────────────────────────────────────────

    private fun requestPermissionsIfNeeded() {
        val missing = PermissionHelper.missingPermissions(this)
        if (missing.isEmpty()) {
            onPermissionsGranted()
        } else {
            permissionLauncher.launch(missing)
        }
    }

    private fun onPermissionsGranted() {
        if (bluetoothAdapter?.isEnabled == true) {
            showModeDialog()
        } else {
            enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    private fun showPermissionRationale(denied: List<String>) {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage(
                "The following permissions are needed:\n\n" +
                denied.joinToString("\n") { "• ${it.substringAfterLast('.')}" } +
                "\n\nPlease grant them in Settings."
            )
            .setPositiveButton("Retry") { _, _ -> requestPermissionsIfNeeded() }
            .setNegativeButton("Exit") { _, _ -> finish() }
            .show()
    }

    // ── Mode selection ────────────────────────────────────────────────────────

    private fun showModeDialog() {
        AlertDialog.Builder(this)
            .setTitle("Start a Secure Chat")
            .setMessage("Choose your role for this session:")
            .setPositiveButton("📡  Wait for connection (Server)") { _, _ -> startServerMode() }
            .setNegativeButton("🔗  Connect to a device (Client)") { _, _ -> startClientMode() }
            .setCancelable(false)
            .show()
    }

    // ── Server mode ───────────────────────────────────────────────────────────

    private fun startServerMode() {
        // Make device discoverable so the client can find us
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
        }
        discoverableLauncher.launch(discoverableIntent)
    }

    private val discoverableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Start the foreground service in server mode
        startService(BluetoothChatService.intentStartServer(this))
        toast("Waiting for a connection…")
        Logger.d(TAG, "Server mode started")
        // Navigate to chat — it will show a 'waiting' state until client connects
        startActivity(Intent(this, com.yourapp.securechat.ui.chat.ChatActivity::class.java))
    }

    // ── Client mode ───────────────────────────────────────────────────────────

    private fun startClientMode() {
        startActivity(Intent(this, DeviceListActivity::class.java))
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun isBtSupported(): Boolean = bluetoothAdapter != null

    companion object {
        private const val TAG = "MainActivity"
    }
}