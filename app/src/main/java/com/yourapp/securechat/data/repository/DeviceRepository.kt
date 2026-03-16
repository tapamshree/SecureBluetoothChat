package com.yourapp.securechat.data.repository

import android.content.Context
import com.yourapp.securechat.bluetooth.BluetoothController
import com.yourapp.securechat.data.model.BluetoothDeviceInfo
import com.yourapp.securechat.utils.Logger

/**
 * DeviceRepository — Manages the list of known and discovered Bluetooth devices.
 *
 * Provides a clean API for the device list UI to get paired and
 * discovered devices without directly interacting with the Bluetooth adapter.
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
