package com.yourapp.securechat.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.yourapp.securechat.utils.Logger
import com.yourapp.securechat.utils.PermissionHelper
import java.util.UUID

/**
 * ============================================================
 * FILE: bluetooth/BluetoothCore.kt
 * PROJECT: SecureBluetoothChat
 * ============================================================
 *
 * PURPOSE:
 * Consolidated Bluetooth core primitives:
 *   - [BluetoothConstants]
 *   - [BluetoothController]
 */

object BluetoothConstants {
    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    const val SERVICE_NAME = "SecureBluetoothChat"
    const val READ_BUFFER_SIZE = 4096
    const val MAX_CONNECT_RETRIES = 3
    const val RETRY_DELAY_MS = 2000L
}

class BluetoothController(private val context: Context) {
    companion object { private const val TAG = "BluetoothController" }

    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter: BluetoothAdapter? = manager?.adapter
    val isBluetoothSupported: Boolean get() = adapter != null
    val isBluetoothEnabled: Boolean get() = try { adapter?.isEnabled == true } catch (_: SecurityException) { false }

    enum class AdapterState { UNSUPPORTED, OFF, TURNING_ON, ON, TURNING_OFF }
    private val _adapterState = MutableLiveData(getAdapterState())
    val adapterState: LiveData<AdapterState> = _adapterState

    private fun getAdapterState(): AdapterState {
        if (adapter == null) return AdapterState.UNSUPPORTED
        return try {
            when (adapter.state) {
                BluetoothAdapter.STATE_ON -> AdapterState.ON
                BluetoothAdapter.STATE_OFF -> AdapterState.OFF
                BluetoothAdapter.STATE_TURNING_ON -> AdapterState.TURNING_ON
                BluetoothAdapter.STATE_TURNING_OFF -> AdapterState.TURNING_OFF
                else -> AdapterState.OFF
            }
        } catch (_: SecurityException) {
            AdapterState.OFF
        }
    }

    fun getPairedDevices(): Set<BluetoothDevice> {
        return try {
            if (PermissionHelper.hasBluetoothPermissions(context)) adapter?.bondedDevices ?: emptySet()
            else emptySet()
        } catch (_: SecurityException) { emptySet() }
    }

    fun getLocalDeviceName(): String = try { adapter?.name ?: "Unknown Device" } catch (_: SecurityException) { "Unknown Device" }
    fun getLocalAddress(): String = try { adapter?.address ?: "00:00:00:00:00:00" } catch (_: SecurityException) { "00:00:00:00:00:00" }
    fun isEnabled(): Boolean = isBluetoothEnabled

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                _adapterState.postValue(getAdapterState())
            }
        }
    }
    private var isRegistered = false

    fun register() = registerStateReceiver()
    fun unregister() = unregisterStateReceiver()

    fun registerStateReceiver() {
        if (isRegistered) return
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(stateReceiver, filter)
        }
        isRegistered = true
    }

    fun unregisterStateReceiver() {
        if (!isRegistered) return
        runCatching { context.unregisterReceiver(stateReceiver) }
        isRegistered = false
        Logger.d(TAG, "State receiver unregistered")
    }
}
