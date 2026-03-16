package com.yourapp.securechat.bluetooth

import java.util.UUID

/**
 * Central constants for Bluetooth communication.
 *
 * SPP (Serial Port Profile) is used for Classic Bluetooth RFCOMM socket
 * communication — the standard approach for Android-to-Android chat.
 */
object BluetoothConstants {

    // -------------------------------------------------------------------------
    // SPP UUID — must be identical on both server and client devices
    // This is the well-known SPP UUID registered with Bluetooth SIG
    // -------------------------------------------------------------------------
    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // -------------------------------------------------------------------------
    // App-specific service name shown during BT service discovery
    // -------------------------------------------------------------------------
    const val SERVICE_NAME = "SecureBluetoothChat"

    // -------------------------------------------------------------------------
    // Socket I/O
    // -------------------------------------------------------------------------

    /** Size of the read buffer in bytes. 4KB handles most message payloads. */
    const val READ_BUFFER_SIZE = 4096

    /** Maximum allowed plaintext message length (before encryption) in bytes. */
    const val MAX_MESSAGE_BYTES = 2048

    // -------------------------------------------------------------------------
    // Connection states — used across ViewModels and the Service
    // -------------------------------------------------------------------------
    const val STATE_NONE        = 0   // Idle, no connection
    const val STATE_LISTENING   = 1   // Server socket open, waiting for client
    const val STATE_CONNECTING  = 2   // Client socket connecting
    const val STATE_CONNECTED   = 3   // Active bidirectional connection
    const val STATE_FAILED      = 4   // Connection attempt failed
    const val STATE_LOST        = 5   // Connection dropped unexpectedly

    // -------------------------------------------------------------------------
    // Message/Handler codes — used with Android Handler to post messages
    // from background threads to the UI thread
    // -------------------------------------------------------------------------
    const val MSG_STATE_CHANGE  = 1
    const val MSG_READ          = 2
    const val MSG_WRITE         = 3
    const val MSG_DEVICE_NAME   = 4
    const val MSG_TOAST         = 5

    // -------------------------------------------------------------------------
    // Intent / Bundle keys
    // -------------------------------------------------------------------------
    const val KEY_DEVICE_NAME    = "device_name"
    const val KEY_DEVICE_ADDRESS = "device_address"
    const val KEY_TOAST          = "toast"

    // -------------------------------------------------------------------------
    // Encryption wire-format offsets (matches AESCipher output layout)
    //
    //  [ IV (12 bytes) | AuthTag (16 bytes) | Ciphertext (N bytes) ]
    //
    // -------------------------------------------------------------------------
    const val IV_SIZE_BYTES       = 12
    const val AUTH_TAG_SIZE_BYTES = 16
    const val HEADER_SIZE_BYTES   = IV_SIZE_BYTES + AUTH_TAG_SIZE_BYTES // 28 bytes

    // -------------------------------------------------------------------------
    // Handshake protocol markers
    // Sent as the first exchange when a connection is established,
    // before any encrypted messages are transmitted.
    // -------------------------------------------------------------------------
    const val HANDSHAKE_HELLO   = "SECURE_CHAT_HELLO_V1"
    const val HANDSHAKE_ACK     = "SECURE_CHAT_ACK_V1"

    // -------------------------------------------------------------------------
    // Reconnection
    // -------------------------------------------------------------------------
    /** How many times to retry a failed client connection before giving up. */
    const val MAX_CONNECT_RETRIES = 3

    /** Delay in milliseconds between retry attempts. */
    const val RETRY_DELAY_MS = 2000L
}