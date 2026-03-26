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