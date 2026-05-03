package com.yourapp.securechat.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.yourapp.securechat.utils.Logger
import java.io.IOException

/**
 * ============================================================================
 * FILE: BluetoothClient.kt
 * ============================================================================
 *
 * 1. PURPOSE OF THE FILE:
 * To initiate an outgoing Bluetooth RFCOMM connection to a remote device 
 * that is running `BluetoothServer`.
 *
 * 2. HOW IT WORKS:
 * It calls `device.createRfcommSocketToServiceRecord()` using the shared SPP UUID, 
 * then invokes the blocking `socket.connect()` on a background thread 
 * ("BT-Client-Connect"). If the connection fails, it retries up to 
 * `MAX_CONNECT_RETRIES` times with a configurable delay between attempts.
 *
 * 3. WHY IS IT IMPORTANT:
 * This is the active/initiating half of the Bluetooth handshake. When the user 
 * taps a device in the device list and chooses to connect, this class is what 
 * physically creates the RFCOMM socket channel to the remote phone.
 *
 * 4. ROLE IN THE PROJECT:
 * Used exclusively by `BluetoothChatService` when the user selects "Find a Device" 
 * and picks a target. On success, the socket is handed off to 
 * `BluetoothConnectionManager` for ongoing I/O.
 *
 * 5. WHAT DOES EACH PART DO:
 * - [connect()]: Spawns the background thread, creates the socket, and retries on failure.
 * - [cancel()]: Interrupts the connection attempt and closes the socket.
 * - [isAttempting]: Volatile boolean letting callers check if a connection is in progress.
 * - [onConnected / onFailed]: Callbacks fired on success or exhaustion of retries.
 * ============================================================================
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
