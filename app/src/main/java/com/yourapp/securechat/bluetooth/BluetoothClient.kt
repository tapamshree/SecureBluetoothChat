package com.yourapp.securechat.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.yourapp.securechat.utils.Logger
import java.io.IOException

/**
 * BluetoothClient — Initiates an outgoing Bluetooth connection to a remote device.
 *
 * Creates an RFCOMM socket and attempts to connect to the remote device's
 * server (which should be running [BluetoothServer] with the same SPP UUID).
 *
 * Includes retry logic (configurable via [BluetoothConstants]) for
 * transient connection failures.
 *
 * Lifecycle:
 *   1. Create instance with the target device and a connection callback
 *   2. Call [connect] — runs on a background thread
 *   3. On success → callback fires with the connected socket
 *   4. On failure → error callback fires
 *   5. Call [cancel] to abort a connection attempt
 *
 * Usage:
 *   val client = BluetoothClient(device,
 *       onConnected = { socket -> connectionManager.connect(socket) },
 *       onFailed = { error -> showError(error) }
 *   )
 *   client.connect()
 */
class BluetoothClient(
    private val device: BluetoothDevice,
    private val onConnected: (BluetoothSocket) -> Unit,
    private val onFailed: (String) -> Unit
) {

    companion object {
        private const val TAG = "BTClient"
    }

    private var socket: BluetoothSocket? = null
    private var connectThread: Thread? = null

    @Volatile
    private var isConnecting = false

    /** Returns true if a connection attempt is in progress. */
    val isAttempting: Boolean get() = isConnecting

    // -------------------------------------------------------------------------
    // Connect / Cancel
    // -------------------------------------------------------------------------

    /**
     * Starts a connection attempt to the remote device.
     * Runs on a background thread with retry logic.
     */
    fun connect() {
        if (isConnecting) {
            Logger.w(TAG, "Connection attempt already in progress")
            return
        }

        isConnecting = true

        connectThread = Thread({
            val deviceName = try { device.name ?: "Unknown" } catch (e: SecurityException) { "Unknown" }
            Logger.i(TAG, "Connecting to $deviceName (${device.address})")

            var lastError: String? = null

            for (attempt in 1..BluetoothConstants.MAX_CONNECT_RETRIES) {
                if (!isConnecting) {
                    Logger.d(TAG, "Connection cancelled by user")
                    return@Thread
                }

                try {
                    // Create a fresh socket for each attempt
                    socket = try {
                        device.createRfcommSocketToServiceRecord(BluetoothConstants.SPP_UUID)
                    } catch (e: SecurityException) {
                        Logger.e(TAG, "Missing BLUETOOTH_CONNECT permission", e)
                        onFailed("Missing Bluetooth permission")
                        isConnecting = false
                        return@Thread
                    }

                    Logger.d(TAG, "Attempt $attempt/${BluetoothConstants.MAX_CONNECT_RETRIES}...")

                    // Connect (blocking call)
                    socket?.connect()

                    // If we get here, connection succeeded
                    Logger.i(TAG, "Connected to $deviceName on attempt $attempt")
                    isConnecting = false

                    socket?.let { onConnected(it) }
                    return@Thread

                } catch (e: IOException) {
                    lastError = e.message ?: "Connection failed"
                    Logger.w(TAG, "Attempt $attempt failed: $lastError")

                    // Close the failed socket
                    try { socket?.close() } catch (_: IOException) {}
                    socket = null

                    // Wait before retrying (unless this was the last attempt)
                    if (attempt < BluetoothConstants.MAX_CONNECT_RETRIES && isConnecting) {
                        try {
                            Thread.sleep(BluetoothConstants.RETRY_DELAY_MS)
                        } catch (ie: InterruptedException) {
                            Logger.d(TAG, "Retry sleep interrupted")
                            break
                        }
                    }
                }
            }

            // All attempts exhausted
            isConnecting = false
            val errorMsg = "Failed to connect after ${BluetoothConstants.MAX_CONNECT_RETRIES} attempts: $lastError"
            Logger.e(TAG, errorMsg)
            onFailed(errorMsg)

        }, "BT-Client-Connect").also { it.start() }
    }

    /**
     * Cancels an ongoing connection attempt.
     */
    fun cancel() {
        isConnecting = false

        try {
            socket?.close()
        } catch (e: IOException) {
            Logger.e(TAG, "Error closing socket during cancel", e)
        }

        connectThread?.interrupt()
        socket = null
        connectThread = null
        Logger.d(TAG, "Connection attempt cancelled")
    }
}
