package com.yourapp.securechat.service

import com.yourapp.securechat.crypto.AESCipher
import com.yourapp.securechat.crypto.SecureMessageWrapper
import com.yourapp.securechat.data.ChatMessage
import com.yourapp.securechat.data.repository.ChatRepository
import com.yourapp.securechat.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import javax.crypto.SecretKey

/**
 * Sits between [com.yourapp.securechat.bluetooth.BluetoothConnectionManager] and the
 * data/UI layers.
 *
 * Pipeline per incoming packet:
 *   raw encrypted bytes
 *     → AESCipher.decrypt()
 *     → SecureMessageWrapper.unwrap()
 *     → handle by MessageType (TEXT / ACK / PING / PONG / BYE)
 *     → ChatRepository.saveMessage()   (TEXT only)
 *     → emit on [incomingMessages]     (TEXT only)
 */
class MessageReceiver(
    private val chatRepository: ChatRepository,
    private val aesCipher: AESCipher,
    private val deviceAddress: String,
    private val deviceName: String
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Public flows observed by BluetoothChatService / ChatViewModel ─────────

    private val _incomingMessages = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 64)
    /** Emits every fully decrypted and persisted TEXT message. */
    val incomingMessages: SharedFlow<ChatMessage> = _incomingMessages

    private val _pingReceived = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emits whenever a PING control frame arrives — service should reply with PONG. */
    val pingReceived: SharedFlow<Unit> = _pingReceived

    private val _byeReceived = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emits whenever a BYE frame arrives — service should tear down the connection. */
    val byeReceived: SharedFlow<Unit> = _byeReceived

    // ── Session key — must be set before bytes start arriving ─────────────────

    var sessionKey: SecretKey? = null

    // ── Entry point ───────────────────────────────────────────────────────────

    /**
     * Called by [BluetoothChatService] on every chunk emitted by
     * [com.yourapp.securechat.bluetooth.BluetoothConnectionManager.incomingBytes].
     * Dispatches work to the IO coroutine scope immediately.
     */
    fun onBytesReceived(encryptedBytes: ByteArray) {
        scope.launch { processBytes(encryptedBytes) }
    }

    // ── Processing pipeline ───────────────────────────────────────────────────

    private suspend fun processBytes(encryptedBytes: ByteArray) {
        val key = sessionKey ?: run {
            Logger.w(TAG, "Dropping packet — session key not initialised")
            return
        }

        // Step 1: decrypt
        val plainBytes = runCatching { aesCipher.decrypt(encryptedBytes, key) }
            .getOrElse { e ->
                Logger.e(TAG, "Decryption failed", e)
                return
            }

        // Step 2: unwrap JSON envelope
        val envelope = runCatching {
            SecureMessageWrapper.unwrap(String(plainBytes, Charsets.UTF_8))
        }.getOrElse { e ->
            Logger.e(TAG, "Envelope unwrap failed", e)
            return
        }

        // Step 3: route by message type
        when (envelope.type) {
            SecureMessageWrapper.MessageType.TEXT -> handleText(envelope)
            SecureMessageWrapper.MessageType.ACK  -> handleAck(envelope)
            SecureMessageWrapper.MessageType.PING -> {
                Logger.d(TAG, "PING received from $deviceAddress")
                _pingReceived.emit(Unit)
            }
            SecureMessageWrapper.MessageType.PONG -> {
                Logger.d(TAG, "PONG received from $deviceAddress")
            }
            SecureMessageWrapper.MessageType.BYE  -> {
                Logger.d(TAG, "BYE received from $deviceAddress")
                _byeReceived.emit(Unit)
            }
        }
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private suspend fun handleText(envelope: SecureMessageWrapper.Envelope) {
        val message = ChatMessage(
            id          = envelope.messageId,
            deviceAddress = deviceAddress,
            senderName  = deviceName,
            content     = envelope.payload,
            timestamp   = envelope.timestamp,
            isMine      = false,
            status      = ChatMessage.Status.RECEIVED
        )

        runCatching { chatRepository.saveMessage(message) }
            .onFailure { e -> Logger.e(TAG, "Failed to persist message ${message.id}", e) }
            .onSuccess {
                _incomingMessages.emit(message)
                Logger.d(TAG, "Text message stored & emitted [id=${message.id}]")
            }
    }

    private fun handleAck(envelope: SecureMessageWrapper.Envelope) {
        // envelope.payload carries the id of the message being acknowledged
        scope.launch {
            runCatching {
                chatRepository.updateStatus(envelope.payload, ChatMessage.Status.DELIVERED)
            }.onFailure { e -> Logger.e(TAG, "Failed to update ACK status", e) }
            Logger.d(TAG, "ACK applied for message ${envelope.payload}")
        }
    }

    companion object {
        private const val TAG = "MessageReceiver"
    }
}