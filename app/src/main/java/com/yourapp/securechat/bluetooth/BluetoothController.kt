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

/**
 * BluetoothController — Central manager for Bluetooth adapter state.
 *
 * Responsibilities:
 * - Check if BT is supported and enabled
 * - Enable/disable Bluetooth (via user intent)
 * - Monitor adapter state changes via BroadcastReceiver
 * - Expose adapter state as observable LiveData
 *
 * This class does NOT handle connections or scanning — those are
 * delegated to [BluetoothDeviceScanner], [BluetoothServer], and
 * [BluetoothClient] respectively.
 */
class BluetoothController(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothController"
    }

    // -------------------------------------------------------------------------
    // Bluetooth adapter
    // -------------------------------------------------------------------------

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    val adapter: BluetoothAdapter? = bluetoothManager?.adapter

    /** Returns true if the device has a Bluetooth adapter. */
    val isBluetoothSupported: Boolean get() = adapter != null

    /** Returns true if Bluetooth is currently turned on. */
    val isBluetoothEnabled: Boolean
        get() = try {
            adapter?.isEnabled == true
        } catch (e: SecurityException) {
            Logger.e(TAG, "Missing BLUETOOTH_CONNECT permission", e)
            false
        }

    // -------------------------------------------------------------------------
    // Adapter state (observable)
    // -------------------------------------------------------------------------

    private val _adapterState = MutableLiveData(getAdapterState())
    /** Observable Bluetooth adapter state. */
    val adapterState: LiveData<AdapterState> = _adapterState

    /**
     * Possible Bluetooth adapter states.
     */
    enum class AdapterState {
        UNSUPPORTED,    // Device has no BT hardware
        OFF,            // BT is turned off
        TURNING_ON,     // BT is in the process of turning on
        ON,             // BT is enabled and ready
        TURNING_OFF     // BT is in the process of turning off
    }

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
        } catch (e: SecurityException) {
            Logger.e(TAG, "Cannot read adapter state — missing permission", e)
            AdapterState.OFF
        }
    }

    // -------------------------------------------------------------------------
    // Paired devices
    // -------------------------------------------------------------------------

    /**
     * Returns the list of currently bonded (paired) devices.
     * Requires BLUETOOTH_CONNECT permission on Android 12+.
     */
    fun getPairedDevices(): Set<BluetoothDevice> {
        return try {
            if (PermissionHelper.hasBluetoothPermissions(context)) {
                adapter?.bondedDevices ?: emptySet()
            } else {
                Logger.w(TAG, "Missing BT permissions — cannot get paired devices")
                emptySet()
            }
        } catch (e: SecurityException) {
            Logger.e(TAG, "SecurityException getting paired devices", e)
            emptySet()
        }
    }

    /**
     * Returns the local Bluetooth adapter name.
     */
    fun getLocalDeviceName(): String {
        return try {
            adapter?.name ?: "Unknown Device"
        } catch (e: SecurityException) {
            "Unknown Device"
        }
    }

    /**
     * Returns the local Bluetooth MAC address.
     * Note: On Android 6+ this returns a constant "02:00:00:00:00:00" for privacy.
     * Use Settings.Secure for the real address if needed.
     */
    fun getLocalAddress(): String {
        return try {
            adapter?.address ?: "00:00:00:00:00:00"
        } catch (e: SecurityException) {
            "00:00:00:00:00:00"
        }
    }

    // -------------------------------------------------------------------------
    // State change receiver
    // -------------------------------------------------------------------------

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(
                    BluetoothAdapter.EXTRA_STATE,
                    BluetoothAdapter.ERROR
                )
                val newState = when (state) {
                    BluetoothAdapter.STATE_ON -> AdapterState.ON
                    BluetoothAdapter.STATE_OFF -> AdapterState.OFF
                    BluetoothAdapter.STATE_TURNING_ON -> AdapterState.TURNING_ON
                    BluetoothAdapter.STATE_TURNING_OFF -> AdapterState.TURNING_OFF
                    else -> AdapterState.OFF
                }
                _adapterState.postValue(newState)
                Logger.d(TAG, "Adapter state changed: $newState")
            }
        }
    }

    private var isReceiverRegistered = false

    /**
     * Starts listening for Bluetooth adapter state changes.
     * Call this in Activity.onCreate() or Service.onCreate().
     */
    fun registerStateReceiver() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(stateReceiver, filter)
            }
            isReceiverRegistered = true
            Logger.d(TAG, "State receiver registered")
        }
    }

    /**
     * Stops listening for adapter state changes.
     * Call this in Activity.onDestroy() or Service.onDestroy().
     */
    fun unregisterStateReceiver() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(stateReceiver)
            } catch (e: IllegalArgumentException) {
                Logger.w(TAG, "Receiver was already unregistered")
            }
            isReceiverRegistered = false
            Logger.d(TAG, "State receiver unregistered")
        }
    }

    /**
     * Returns an Intent to prompt the user to enable Bluetooth.
     * Use with registerForActivityResult(StartActivityForResult()).
     */
    fun getEnableBluetoothIntent(): Intent {
        return Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
    }

    /**
     * Returns an Intent to make the device discoverable.
     *
     * @param durationSeconds How long the device should be discoverable (max 300).
     */
    fun getDiscoverableIntent(durationSeconds: Int = 120): Intent {
        return Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, durationSeconds)
        }
    }

    /**
     * Clean up — call when the controller is no longer needed.
     */
    fun destroy() {
        unregisterStateReceiver()
        Logger.d(TAG, "BluetoothController destroyed")
    }
}
