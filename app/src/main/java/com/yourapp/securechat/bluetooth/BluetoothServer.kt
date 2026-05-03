package com.yourapp.securechat.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import com.yourapp.securechat.utils.Logger
import java.io.IOException

/**
 * ============================================================================
 * FILE: BluetoothServer.kt
 * ============================================================================
 *
 * 1. PURPOSE OF THE FILE:
 * To listen for and accept incoming Bluetooth RFCOMM connections from a remote 
 * device that is running `BluetoothClient`.
 *
 * 2. HOW IT WORKS:
 * It opens a `BluetoothServerSocket` using `listenUsingRfcommWithServiceRecord()` 
 * and spawns a dedicated background thread ("BT-Server-Accept") that blocks on 
 * `serverSocket.accept()`. When a client connects, the resulting `BluetoothSocket` 
 * is passed to the `onClientConnected` callback. The server socket is then closed 
 * because this app only supports one-to-one connections.
 *
 * 3. WHY IS IT IMPORTANT:
 * Without a server-side listener, the "Wait for Connection" mode in `MainActivity` 
 * would have nothing to bind to. The server is the passive half of the Bluetooth 
 * handshake—it is what allows the remote user's phone to "find" this device.
 *
 * 4. ROLE IN THE PROJECT:
 * Used exclusively by `BluetoothChatService` when the user selects "Wait for 
 * Connection". Once a connection is accepted, the socket is handed off to 
 * `BluetoothConnectionManager` for ongoing I/O.
 *
 * 5. WHAT DOES EACH PART DO:
 * - [start()]: Opens the server socket and spawn the blocking accept thread.
 * - [stop()]: Closes the server socket, which interrupts the blocking accept call.
 * - [isListening]: A volatile boolean exposing whether the accept loop is active.
 * - [onClientConnected callback]: Lambda fired once with the connected socket.
 * ============================================================================
 */
class BluetoothServer(
    private val adapter: BluetoothAdapter?,
    private val onClientConnected: (BluetoothSocket) -> Unit
) {

    companion object {
        private const val TAG = "BTServer"
    }

    private var serverSocket: BluetoothServerSocket? = null
    private var acceptThread: Thread? = null

    @Volatile
    private var isRunning = false

    /** Returns true if the server is currently listening for connections. */
    val isListening: Boolean get() = isRunning

    // -------------------------------------------------------------------------
    // Start / Stop
    // -------------------------------------------------------------------------

    /**
     * Opens a server socket and begins listening for incoming connections.
     * This creates a background thread that blocks on accept().
     *
     * @throws SecurityException if BLUETOOTH_CONNECT permission is missing.
     */
    fun start() {
        if (isRunning) {
            Logger.w(TAG, "Server already running")
            return
        }

        try {
            serverSocket = adapter?.listenUsingRfcommWithServiceRecord(
                BluetoothConstants.SERVICE_NAME,
                BluetoothConstants.SPP_UUID
            )
        } catch (e: SecurityException) {
            Logger.e(TAG, "Missing BLUETOOTH_CONNECT permission", e)
            return
        } catch (e: IOException) {
            Logger.e(TAG, "Failed to open server socket", e)
            return
        }

        isRunning = true

        acceptThread = Thread({
            Logger.i(TAG, "Server listening on SPP UUID: ${BluetoothConstants.SPP_UUID}")

            while (isRunning) {
                try {
                    // This call blocks until a client connects or the socket is closed
                    val clientSocket = serverSocket?.accept()

                    if (clientSocket != null) {
                        val deviceName = try {
                            clientSocket.remoteDevice?.name ?: "Unknown"
                        } catch (e: SecurityException) {
                            "Unknown"
                        }
                        Logger.i(TAG, "Client connected: $deviceName")

                        // Pass the connected socket to the callback
                        onClientConnected(clientSocket)

                        // Close the server socket — we only handle one connection at a time
                        // If you want to support multiple connections, remove this line
                        serverSocket?.close()
                        isRunning = false
                    }
                } catch (e: IOException) {
                    if (isRunning) {
                        Logger.e(TAG, "Accept failed", e)
                    } else {
                        Logger.d(TAG, "Server socket closed (expected)")
                    }
                    break
                }
            }

            Logger.d(TAG, "Accept thread exiting")
        }, "BT-Server-Accept").also { it.start() }
    }

    /**
     * Stops the server and closes the server socket.
     * If a client is currently connecting, it will be interrupted.
     */
    fun stop() {
        isRunning = false

        try {
            serverSocket?.close()
        } catch (e: IOException) {
            Logger.e(TAG, "Error closing server socket", e)
        }

        serverSocket = null
        acceptThread = null
        Logger.d(TAG, "Server stopped")
    }
}
