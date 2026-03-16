package com.yourapp.securechat.service

import com.yourapp.securechat.crypto.AESCipherException
import com.yourapp.securechat.crypto.DecryptedMessage
import com.yourapp.securechat.crypto.MessageParseException
import com.yourapp.securechat.crypto.SecureMessageWrapper
import com.yourapp.securechat.data.model.ChatMessage
import com.yourapp.securechat.data.repository.ChatRepository
import com.yourapp.securechat.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.crypto.SecretKey

/**
 * MessageReceiver — Processes incoming raw bytes from the Bluetooth socket.
 *
 * Pipeline for every incoming byte payload:
 *   1. bytes → [SecureMessageWrapper.unwrap] → [DecryptedMessage]
 *   2. Check message type (TEXT, ACK, PING, PONG, BYE)
 *   3. For TEXT: save to Room DB → notify UI → send ACK back
 *   4. For ACK:  update outgoing message status → SENT
 *   5. For PING: respond with PONG
 *   6. For BYE:  trigger graceful disconnect
 *
 * This class is used by [BluetoothChatService] which feeds it raw bytes
 * from [BluetoothConnectionManager].
 */
class MessageReceiver(
    private val chatRepository: ChatRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    companion object {
        private const val TAG = "MsgReceiver"
    }

    /** Callback for different message events. */
    interface MessageCallback {
        /** Called when a text message is received and saved. */
        fun onTextMessageReceived(message: ChatMessage)

        /** Called when an ACK is received for an outgoing message. */
        fun onAckReceived(messageId: String)

        /** Called when a PING is received — caller should send PONG back. */
        fun onPingReceived(senderAddress: String)

        /** Called when remote sends BYE — graceful disconnect requested. */
        fun onDisconnectRequested(senderAddress: String)
    }

    private var callback: MessageCallback? = null
    private var sessionKey: SecretKey? = null
    private var sessionId: String = ""
    private var remoteDeviceName: String = "Unknown"

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    /**
     * Configures the receiver for the current session.
     *
     * @param key           The AES-256 session key for decryption.
     * @param sessionId     The current conversation session ID.
     * @param deviceName    The remote device's display name.
     * @param callback      Callback for message events.
     */
    fun configure(
        key: SecretKey,
        sessionId: String,
        deviceName: String,
        callback: MessageCallback
    ) {
        this.sessionKey = key
        this.sessionId = sessionId
        this.remoteDeviceName = deviceName
        this.callback = callback
        Logger.d(TAG, "Configured for session $sessionId with $deviceName")
    }

    // -------------------------------------------------------------------------
    // Process incoming bytes
    // -------------------------------------------------------------------------

    /**
     * Processes a raw byte payload received from the Bluetooth socket.
     *
     * @param data   The raw encrypted byte array.
     * @param length The number of valid bytes in the array.
     */
    fun onBytesReceived(data: ByteArray, length: Int) {
        val key = sessionKey
        if (key == null) {
            Logger.e(TAG, "Cannot process message — no session key configured")
            return
        }

        scope.launch {
            try {
                // 1. Decrypt and unwrap
                val encryptedBytes = if (data.size == length) data else data.copyOfRange(0, length)
                val decrypted = SecureMessageWrapper.unwrap(encryptedBytes, key)

                Logger.d(TAG, "Received ${decrypted.type} from ${decrypted.sender}")

                // 2. Route by message type
                when (decrypted.type) {
                    SecureMessageWrapper.MessageType.TEXT -> handleTextMessage(decrypted)
                    SecureMessageWrapper.MessageType.ACK  -> handleAck(decrypted)
                    SecureMessageWrapper.MessageType.PING -> handlePing(decrypted)
                    SecureMessageWrapper.MessageType.PONG -> handlePong(decrypted)
                    SecureMessageWrapper.MessageType.BYE  -> handleBye(decrypted)
                    else -> Logger.w(TAG, "Unknown message type: ${decrypted.type}")
                }

            } catch (e: AESCipherException) {
                Logger.e(TAG, "Decryption failed — possible tampering or wrong key", e)
            } catch (e: MessageParseException) {
                Logger.e(TAG, "Failed to parse decrypted payload", e)
            } catch (e: Exception) {
                Logger.e(TAG, "Unexpected error processing message", e)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Message type handlers
    // -------------------------------------------------------------------------

    private suspend fun handleTextMessage(decrypted: DecryptedMessage) {
        // Convert to ChatMessage and save to Room
        val chatMessage = ChatMessage.fromDecrypted(
            decrypted = decrypted,
            sessionId = sessionId,
            senderName = remoteDeviceName
        )

        chatRepository.saveMessage(chatMessage)
        chatRepository.incrementSessionMessageCount(sessionId.toLongOrNull() ?: 0L)

        Logger.i(TAG, "Text message saved: ${decrypted.content.take(30)}...")
        callback?.onTextMessageReceived(chatMessage)
    }

    private suspend fun handleAck(decrypted: DecryptedMessage) {
        // The content of an ACK is the ID of the message being acknowledged
        val ackedMessageId = decrypted.content
        chatRepository.updateMessageStatus(ackedMessageId, ChatMessage.DeliveryStatus.SENT)
        Logger.d(TAG, "ACK received for message: $ackedMessageId")
        callback?.onAckReceived(ackedMessageId)
    }

    private fun handlePing(decrypted: DecryptedMessage) {
        Logger.d(TAG, "PING received from ${decrypted.sender}")
        callback?.onPingReceived(decrypted.sender)
    }

    private fun handlePong(decrypted: DecryptedMessage) {
        Logger.d(TAG, "PONG received from ${decrypted.sender} — connection alive")
        // Could update a "last seen" timestamp here
    }

    private fun handleBye(decrypted: DecryptedMessage) {
        Logger.i(TAG, "BYE received from ${decrypted.sender} — disconnect requested")
        callback?.onDisconnectRequested(decrypted.sender)
    }
}
