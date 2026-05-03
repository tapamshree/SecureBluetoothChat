package com.yourapp.securechat.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.yourapp.securechat.bluetooth.BluetoothController
import com.yourapp.securechat.bluetooth.BluetoothDeviceScanner
import com.yourapp.securechat.data.model.BluetoothDeviceInfo
import com.yourapp.securechat.utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ============================================================================
 * FILE: DeviceViewModel.kt
 * ============================================================================
 *
 * 1. PURPOSE OF THE FILE:
 * To manage Bluetooth device scanning state and paired device lists in a 
 * lifecycle-aware manner for the `DeviceListActivity`.
 *
 * 2. HOW IT WORKS:
 * It wraps `BluetoothController` and `BluetoothDeviceScanner`, converting 
 * their `LiveData` outputs into Kotlin `StateFlow` streams. It also provides 
 * commands to start/stop scanning and exposes error messages via `SharedFlow`.
 *
 * 3. WHY IS IT IMPORTANT:
 * Keeps scanning state alive across configuration changes and ensures the 
 * scanner's `BroadcastReceiver` is properly unregistered when the ViewModel 
 * is cleared.
 *
 * 4. ROLE IN THE PROJECT:
 * MVVM ViewModel for the device discovery flow. Bridges hardware-level 
 * Bluetooth APIs to the UI layer.
 *
 * 5. WHAT DOES EACH PART DO:
 * - [pairedDevices]: StateFlow of already-bonded devices.
 * - [discoveredDevices]: StateFlow of newly found devices during a scan.
 * - [isScanning]: StateFlow boolean reflecting active scan state.
 * - [startScan() / stopScan()]: Controls for the discovery process.
 * - [onCleared()]: Lifecycle cleanup for the scanner and controller.
 * - [Factory]: Custom ViewModelProvider.Factory for constructor injection.
 * ============================================================================
 */
class DeviceViewModel(
    private val controller: BluetoothController,
    private val scanner: BluetoothDeviceScanner
) : ViewModel() {

    // ── Paired devices ────────────────────────────────────────────────────────

    private val _pairedDevices = MutableStateFlow<List<BluetoothDeviceInfo>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDeviceInfo>> = _pairedDevices

    // ── Discovered devices (live from scanner) ────────────────────────────────

    val discoveredDevices: StateFlow<List<BluetoothDeviceInfo>> = scanner.discoveredDevices.asFlow()
        .map { list -> list.map { BluetoothDeviceInfo.fromBluetoothDevice(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Scan state ────────────────────────────────────────────────────────────

    val isScanning: StateFlow<Boolean> = scanner.isScanning.asFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // ── Error messages ────────────────────────────────────────────────────────

    private val _errorMessage = MutableSharedFlow<String?>(extraBufferCapacity = 1)
    val errorMessage: SharedFlow<String?> = _errorMessage

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        controller.register()
        loadPairedDevices()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun loadPairedDevices() {
        viewModelScope.launch {
            runCatching {
                val paired = scanner.getPairedDevices()
                    .map { BluetoothDeviceInfo.fromBluetoothDevice(it) }
                _pairedDevices.value = paired
                Logger.d(TAG, "Loaded ${paired.size} paired devices")
            }.onFailure { e ->
                Logger.e(TAG, "Failed to load paired devices", e)
                _errorMessage.emit("Could not load paired devices.")
            }
        }
    }

    fun startScan() {
        if (!controller.isEnabled()) {
            viewModelScope.launch { _errorMessage.emit("Bluetooth is not enabled.") }
            return
        }
        Logger.d(TAG, "Starting scan")
        scanner.startDiscovery()
    }

    fun stopScan() {
        Logger.d(TAG, "Stopping scan")
        scanner.stopDiscovery()
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    override fun onCleared() {
        scanner.unregister()
        controller.unregister()
        super.onCleared()
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    class Factory(
        private val controller: BluetoothController,
        private val scanner: BluetoothDeviceScanner
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(DeviceViewModel::class.java))
            return DeviceViewModel(controller, scanner) as T
        }
    }

    companion object {
        private const val TAG = "DeviceViewModel"
    }
}