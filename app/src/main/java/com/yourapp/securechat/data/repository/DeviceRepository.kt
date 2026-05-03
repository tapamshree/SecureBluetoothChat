package com.yourapp.securechat.data.repository

import android.content.Context
import com.yourapp.securechat.bluetooth.BluetoothController
import com.yourapp.securechat.data.model.BluetoothDeviceInfo
import com.yourapp.securechat.utils.Logger

/**
 * ============================================================================
 * FILE: DeviceRepository.kt
 * ============================================================================
 * 
 * 1. PURPOSE OF THE FILE:
 * To act as the central dispatcher for retrieving information about nearby or 
 * previously paired Bluetooth devices for display in the UI.
 * 
 * 2. HOW IT WORKS:
 * It utilizes the Repository Pattern to wrap the raw `BluetoothController` class. 
 * When asked for paired devices, it queries the hardware controller, intercepts the 
 * raw Java `BluetoothDevice` objects, and maps them into safe, stateless Kotlin 
 * `BluetoothDeviceInfo` models.
 * 
 * 3. WHY IS IT IMPORTANT:
 * Directly exposing `BluetoothDevice` objects to the View/ViewModel layer can cause
 * massive memory leaks or fatal `SecurityException` crashes if location/Bluetooth 
 * permissions are suddenly revoked. Wrapping these calls inside a Repository guarantees 
 * that UI components only ever receive safe, parsed POJOs.
 * 
 * 4. ROLE IN THE PROJECT:
 * Sits in the Data/Repository layer specifically servicing the `DeviceViewModel` and 
 * the initial scanning screens. It acts as a safety firewall around the hardware APIs.
 * 
 * 5. WHAT DOES EACH PART DO:
 * - [getPairedDevices()]: Queries hardware for bonded devices and maps them to models.
 * - [getLocalDeviceName() / getLocalAddress()]: Fetches self-identity info.
 * - [isBluetoothEnabled() / isBluetoothSupported()]: Simple boolean checks for hardware state.
 * ============================================================================
 */
class DeviceRepository(
    private val bluetoothController: BluetoothController
) {

    companion object {
        private const val TAG = "DeviceRepo"
    }

    /**
     * Returns the list of currently paired (bonded) devices.
     * Converts from Android's BluetoothDevice to our [BluetoothDeviceInfo] model.
     */
    fun getPairedDevices(): List<BluetoothDeviceInfo> {
        return try {
            bluetoothController.getPairedDevices().map { device ->
                BluetoothDeviceInfo.fromBluetoothDevice(device)
            }.also {
                Logger.d(TAG, "Found ${it.size} paired device(s)")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error getting paired devices", e)
            emptyList()
        }
    }

    /**
     * Returns the local device name for display purposes.
     */
    fun getLocalDeviceName(): String {
        return bluetoothController.getLocalDeviceName()
    }

    /**
     * Returns the local Bluetooth MAC address.
     */
    fun getLocalAddress(): String {
        return bluetoothController.getLocalAddress()
    }

    /**
     * Returns true if Bluetooth is enabled.
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothController.isBluetoothEnabled
    }

    /**
     * Returns true if the device supports Bluetooth.
     */
    fun isBluetoothSupported(): Boolean {
        return bluetoothController.isBluetoothSupported
    }
}
