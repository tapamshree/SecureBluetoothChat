package com.yourapp.securechat.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import com.yourapp.securechat.utils.Logger
import java.io.IOException

/**
 * BluetoothServer — Listens for and accepts incoming Bluetooth connections.
 *
 * Opens a server socket using RFCOMM/SPP and waits for a remote device
 * (running [BluetoothClient]) to connect. Once a client connects, the
 * resulting [BluetoothSocket] is passed to a callback for use by
 * [BluetoothConnectionManager].
 *
 * The server runs on its own thread and can be stopped at any time.
 *
 * Lifecycle:
 *   1. Create instance with a connection callback
 *   2. Call [start] — opens server socket, blocks on accept()
 *   3. When a client connects → callback fires with the socket
 *   4. Call [stop] to close the server socket
 *
 * Usage:
 *   val server = BluetoothServer(adapter) { socket ->
 *       connectionManager.connect(socket)
 *   }
 *   server.start()
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
