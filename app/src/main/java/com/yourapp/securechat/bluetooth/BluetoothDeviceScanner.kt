package com.yourapp.securechat.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.yourapp.securechat.utils.Logger
import com.yourapp.securechat.utils.PermissionHelper

/**
 * BluetoothDeviceScanner — Discovers nearby Bluetooth devices.
 *
 * Uses [BluetoothAdapter.startDiscovery] to find unpaired devices
 * and reports results via [LiveData]. Also provides access to
 * already-paired (bonded) devices from the adapter.
 *
 * Lifecycle:
 *   1. Call [startDiscovery] to begin scanning
 *   2. Observe [discoveredDevices] for new devices as they appear
 *   3. Discovery auto-stops after ~12 seconds, or call [stopDiscovery]
 *   4. Call [unregister] when done (Activity.onDestroy)
 *
 * Usage:
 *   val scanner = BluetoothDeviceScanner(context, adapter)
 *   scanner.discoveredDevices.observe(this) { devices -> updateUI(devices) }
 *   scanner.startDiscovery()
 */
class BluetoothDeviceScanner(
    private val context: Context,
    private val adapter: BluetoothAdapter?
) {

    companion object {
        private const val TAG = "BTScanner"
    }

    // -------------------------------------------------------------------------
    // Discovered devices
    // -------------------------------------------------------------------------

    private val _discoveredDevices = MutableLiveData<List<BluetoothDevice>>(emptyList())
    /** Observable list of discovered (unpaired) devices. Updated as devices are found. */
    val discoveredDevices: LiveData<List<BluetoothDevice>> = _discoveredDevices

    private val _isScanning = MutableLiveData(false)
    /** Observable scanning state. */
    val isScanning: LiveData<Boolean> = _isScanning

    private val deviceSet = mutableSetOf<String>() // Track by MAC address to avoid duplicates

    // -------------------------------------------------------------------------
    // Discovery BroadcastReceiver
    // -------------------------------------------------------------------------

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }

                    device?.let { addDevice(it) }
                }

                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    _isScanning.postValue(true)
                    Logger.d(TAG, "Discovery started")
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isScanning.postValue(false)
                    Logger.d(TAG, "Discovery finished. Found ${deviceSet.size} device(s)")
                }
            }
        }
    }

    private var isReceiverRegistered = false

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Registers the discovery receiver and starts scanning for nearby devices.
     * Clears any previously discovered devices.
     */
    fun startDiscovery() {
        if (!PermissionHelper.hasBluetoothPermissions(context)) {
            Logger.e(TAG, "Cannot start discovery — missing permissions")
            return
        }

        registerReceiver()
        clearDevices()

        try {
            // Cancel any ongoing discovery first
            adapter?.cancelDiscovery()
            // Start fresh scan
            val started = adapter?.startDiscovery() ?: false
            if (started) {
                Logger.i(TAG, "Discovery started successfully")
            } else {
                Logger.e(TAG, "Failed to start discovery")
                _isScanning.postValue(false)
            }
        } catch (e: SecurityException) {
            Logger.e(TAG, "SecurityException starting discovery", e)
            _isScanning.postValue(false)
        }
    }

    /**
     * Stops an ongoing device discovery.
     */
    fun stopDiscovery() {
        try {
            if (adapter?.isDiscovering == true) {
                adapter.cancelDiscovery()
                Logger.d(TAG, "Discovery cancelled")
            }
        } catch (e: SecurityException) {
            Logger.e(TAG, "SecurityException stopping discovery", e)
        }
        _isScanning.postValue(false)
    }

    /**
     * Returns the list of already-paired (bonded) devices.
     */
    fun getPairedDevices(): List<BluetoothDevice> {
        return try {
            adapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            Logger.e(TAG, "Cannot get bonded devices", e)
            emptyList()
        }
    }

    /**
     * Clears the discovered device list.
     */
    fun clearDevices() {
        deviceSet.clear()
        _discoveredDevices.postValue(emptyList())
    }

    /**
     * Registers the BroadcastReceiver for discovery events.
     */
    private fun registerReceiver() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(discoveryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(discoveryReceiver, filter)
            }
            isReceiverRegistered = true
            Logger.d(TAG, "Discovery receiver registered")
        }
    }

    /**
     * Unregisters the BroadcastReceiver and stops any active discovery.
     * Call this from Activity.onDestroy().
     */
    fun unregister() {
        stopDiscovery()
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(discoveryReceiver)
            } catch (e: IllegalArgumentException) {
                Logger.w(TAG, "Receiver already unregistered")
            }
            isReceiverRegistered = false
            Logger.d(TAG, "Discovery receiver unregistered")
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun addDevice(device: BluetoothDevice) {
        try {
            val address = device.address ?: return
            if (address !in deviceSet) {
                deviceSet.add(address)
                val name = device.name ?: "Unknown"
                Logger.d(TAG, "Found device: $name ($address)")

                val currentList = _discoveredDevices.value.orEmpty().toMutableList()
                currentList.add(device)
                _discoveredDevices.postValue(currentList)
            }
        } catch (e: SecurityException) {
            Logger.e(TAG, "SecurityException reading device info", e)
        }
    }
}
