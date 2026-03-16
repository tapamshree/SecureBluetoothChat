package com.yourapp.securechat.data.model

/**
 * BluetoothDeviceInfo — Lightweight model representing a discovered or paired Bluetooth device.
 *
 * Unlike [android.bluetooth.BluetoothDevice], this is a simple data class that
 * can be safely passed between layers without needing Bluetooth permissions
 * or Parcelable handling.
 *
 * Used by DeviceListAdapter and DeviceViewModel.
 */
data class BluetoothDeviceInfo(
    /** Human-readable device name (from BT adapter). */
    val name: String,

    /** Bluetooth MAC address. Example: "AA:BB:CC:DD:EE:FF" */
    val address: String,

    /** Bond state with this device. */
    val bondState: BondState = BondState.NONE,

    /** Signal strength in dBm (from discovery, if available). */
    val rssi: Int? = null
) {
    /**
     * Bond states mirroring BluetoothDevice constants.
     */
    enum class BondState {
        NONE,       // Not paired
        BONDING,    // Pairing in progress
        BONDED      // Paired
    }

    /** Returns true if this device is paired with the local device. */
    val isPaired: Boolean get() = bondState == BondState.BONDED

    /** Display label — name if available, otherwise the MAC address. */
    val displayName: String get() = name.ifBlank { address }

    companion object {
        /**
         * Creates a [BluetoothDeviceInfo] from an Android [android.bluetooth.BluetoothDevice].
         * Handles SecurityException for name access on Android 12+.
         */
        fun fromBluetoothDevice(device: android.bluetooth.BluetoothDevice): BluetoothDeviceInfo {
            val name = try { device.name ?: "" } catch (e: SecurityException) { "" }
            val bondState = when (device.bondState) {
                android.bluetooth.BluetoothDevice.BOND_BONDED -> BondState.BONDED
                android.bluetooth.BluetoothDevice.BOND_BONDING -> BondState.BONDING
                else -> BondState.NONE
            }
            return BluetoothDeviceInfo(
                name = name,
                address = device.address,
                bondState = bondState
            )
        }
    }
}
