package com.yourapp.securechat.bluetooth

import android.bluetooth.BluetoothSocket
import com.yourapp.securechat.utils.Logger
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * BluetoothConnectionManager — Manages an active Bluetooth socket connection.
 *
 * After [BluetoothServer] or [BluetoothClient] establishes a socket,
 * this class takes ownership and handles:
 * - Reading incoming bytes on a background thread
 * - Writing outgoing bytes (thread-safe)
 * - Detecting connection loss
 * - Cleaning up streams and socket on disconnect
 *
 * All raw bytes pass through this class — encryption/decryption is handled
 * by the caller (typically [BluetoothChatService] using [SecureMessageWrapper]).
 *
 * Usage:
 *   val manager = BluetoothConnectionManager(
 *       onDataReceived = { bytes, length -> processBytes(bytes, length) },
 *       onConnectionLost = { reason -> handleDisconnect(reason) }
 *   )
 *   manager.connect(socket)
 *   manager.write(encryptedBytes)
 *   manager.disconnect()
 */
class BluetoothConnectionManager(
    private val onDataReceived: (ByteArray, Int) -> Unit,
    private val onConnectionLost: (String) -> Unit
) {

    companion object {
        private const val TAG = "BTConnManager"
    }

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var readThread: Thread? = null

    @Volatile
    private var isConnected = false

    /** Returns true if there is an active socket connection. */
    val connected: Boolean get() = isConnected

    /**
     * Returns the remote device name, if connected.
     */
    val remoteDeviceName: String?
        get() = try {
            socket?.remoteDevice?.name
        } catch (e: SecurityException) {
            null
        }

    /**
     * Returns the remote device MAC address, if connected.
     */
    val remoteDeviceAddress: String?
        get() = socket?.remoteDevice?.address

    // -------------------------------------------------------------------------
    // Connect / Disconnect
    // -------------------------------------------------------------------------

    /**
     * Takes ownership of an already-connected [BluetoothSocket].
     * Opens I/O streams and starts the read thread.
     *
     * @param connectedSocket A socket that has already completed its handshake.
     */
    fun connect(connectedSocket: BluetoothSocket) {
        // Clean up any existing connection first
        disconnect()

        socket = connectedSocket

        try {
            inputStream = connectedSocket.inputStream
            outputStream = connectedSocket.outputStream
        } catch (e: IOException) {
            Logger.e(TAG, "Failed to get socket streams", e)
            onConnectionLost("Failed to open I/O streams")
            return
        }

        isConnected = true
        startReadThread()

        val deviceName = remoteDeviceName ?: "Unknown"
        Logger.i(TAG, "Connected to $deviceName — read thread started")
    }

    /**
     * Closes the connection, streams, and socket.
     * Safe to call multiple times — subsequent calls are no-ops.
     */
    fun disconnect() {
        if (!isConnected && socket == null) return

        isConnected = false
        readThread?.interrupt()

        try { inputStream?.close() }  catch (_: IOException) {}
        try { outputStream?.close() } catch (_: IOException) {}
        try { socket?.close() }       catch (_: IOException) {}

        inputStream = null
        outputStream = null
        socket = null
        readThread = null

        Logger.d(TAG, "Disconnected and cleaned up")
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    /**
     * Writes bytes to the remote device.
     * This method is thread-safe — callers can invoke from any thread.
     *
     * @param data  The byte array to send (typically an encrypted payload).
     * @return true if the write succeeded, false if the connection is closed.
     */
    @Synchronized
    fun write(data: ByteArray): Boolean {
        if (!isConnected) {
            Logger.w(TAG, "Cannot write — not connected")
            return false
        }

        return try {
            outputStream?.let { stream ->
                // Write a 4-byte length prefix so the receiver knows how many
                // bytes to read for each message.
                val lengthPrefix = ByteArray(4)
                lengthPrefix[0] = (data.size shr 24 and 0xFF).toByte()
                lengthPrefix[1] = (data.size shr 16 and 0xFF).toByte()
                lengthPrefix[2] = (data.size shr 8  and 0xFF).toByte()
                lengthPrefix[3] = (data.size        and 0xFF).toByte()

                stream.write(lengthPrefix)
                stream.write(data)
                stream.flush()

                Logger.v(TAG, "Wrote ${data.size} bytes (+ 4-byte header)")
                true
            } ?: false
        } catch (e: IOException) {
            Logger.e(TAG, "Write failed", e)
            handleConnectionLost("Write failed: ${e.message}")
            false
        }
    }

    // -------------------------------------------------------------------------
    // Read thread
    // -------------------------------------------------------------------------

    /**
     * Starts a background thread that continuously reads from the input stream.
     * Uses a length-prefixed protocol: 4 bytes length + N bytes data.
     */
    private fun startReadThread() {
        readThread = Thread({
            Logger.d(TAG, "Read thread started")
            val headerBuffer = ByteArray(4)

            while (isConnected && !Thread.currentThread().isInterrupted) {
                try {
                    // 1. Read the 4-byte length header
                    readFully(headerBuffer, 4)

                    val messageLength =
                        (headerBuffer[0].toInt() and 0xFF shl 24) or
                        (headerBuffer[1].toInt() and 0xFF shl 16) or
                        (headerBuffer[2].toInt() and 0xFF shl 8)  or
                        (headerBuffer[3].toInt() and 0xFF)

                    if (messageLength <= 0 || messageLength > BluetoothConstants.READ_BUFFER_SIZE) {
                        Logger.e(TAG, "Invalid message length: $messageLength")
                        continue
                    }

                    // 2. Read the message body
                    val messageBuffer = ByteArray(messageLength)
                    readFully(messageBuffer, messageLength)

                    Logger.v(TAG, "Read $messageLength bytes")

                    // 3. Deliver to callback
                    onDataReceived(messageBuffer, messageLength)

                } catch (e: IOException) {
                    if (isConnected) {
                        handleConnectionLost("Read failed: ${e.message}")
                    }
                    break
                }
            }

            Logger.d(TAG, "Read thread exiting")
        }, "BT-Read").also { it.start() }
    }

    /**
     * Reads exactly [length] bytes from the input stream.
     * Blocks until all bytes are read or an exception occurs.
     */
    @Throws(IOException::class)
    private fun readFully(buffer: ByteArray, length: Int) {
        var totalRead = 0
        while (totalRead < length) {
            val bytesRead = inputStream?.read(buffer, totalRead, length - totalRead) ?: -1
            if (bytesRead == -1) {
                throw IOException("Stream ended unexpectedly after $totalRead/$length bytes")
            }
            totalRead += bytesRead
        }
    }

    // -------------------------------------------------------------------------
    // Connection loss handling
    // -------------------------------------------------------------------------

    private fun handleConnectionLost(reason: String) {
        if (!isConnected) return
        isConnected = false
        Logger.e(TAG, "Connection lost: $reason")
        disconnect()
        onConnectionLost(reason)
    }
}
