package com.yourapp.securechat.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * ChatMessage — Room database entity representing a single chat message.
 *
 * Each message is stored in decrypted form locally on the device.
 * If you require encrypted-at-rest storage, consider using SQLCipher
 * as the Room database driver (drop-in replacement for SQLite).
 *
 * Table: messages
 * Indexed on: [sessionId] for fast per-conversation queries,
 *             [timestamp] for chronological ordering.
 *
 * Message lifecycle:
 *   SENDING  → message written to BT socket, awaiting ACK
 *   SENT     → ACK received from remote device
 *   RECEIVED → message received and decrypted from remote device
 *   FAILED   → socket write failed or connection dropped before ACK
 */
@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["session_id"]),
        Index(value = ["timestamp"])
    ]
)
data class ChatMessage(

    // -------------------------------------------------------------------------
    // Primary key
    // -------------------------------------------------------------------------

    /**
     * Unique message ID — matches the [DecryptedMessage.id] from the wire envelope.
     * Using the wire ID ensures deduplication if the same message arrives twice.
     */
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    // -------------------------------------------------------------------------
    // Session / conversation context
    // -------------------------------------------------------------------------

    /**
     * The Bluetooth MAC address of the remote device this message belongs to.
     * Used to group messages into conversations.
     * Example: "AA:BB:CC:DD:EE:FF"
     */
    @ColumnInfo(name = "session_id")
    val sessionId: String,

    // -------------------------------------------------------------------------
    // Sender / direction
    // -------------------------------------------------------------------------

    /**
     * Bluetooth MAC address of the message sender.
     * Compare with the local device address to determine direction.
     */
    @ColumnInfo(name = "sender_address")
    val senderAddress: String,

    /**
     * Display name of the sender (resolved from paired device name).
     * Stored for display without needing to re-query the BT adapter.
     */
    @ColumnInfo(name = "sender_name")
    val senderName: String,

    /**
     * True if this message was sent by the local device.
     * False if it was received from the remote device.
     * Used by the UI to align message bubbles (right = sent, left = received).
     */
    @ColumnInfo(name = "is_outgoing")
    val isOutgoing: Boolean,

    // -------------------------------------------------------------------------
    // Content
    // -------------------------------------------------------------------------

    /**
     * The decrypted plaintext message body.
     * This is the content shown in the chat UI.
     */
    @ColumnInfo(name = "content")
    val content: String,

    /**
     * Message type — matches [SecureMessageWrapper.MessageType].
     * Only TEXT messages are stored; control messages (ACK, PING, etc.) are not persisted.
     */
    @ColumnInfo(name = "type")
    val type: String = MessageType.TEXT,

    // -------------------------------------------------------------------------
    // Timing
    // -------------------------------------------------------------------------

    /**
     * Unix epoch timestamp in milliseconds when the message was sent/received.
     * Taken from the wire envelope — reflects the sender's clock.
     */
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    // -------------------------------------------------------------------------
    // Delivery status
    // -------------------------------------------------------------------------

    /**
     * Current delivery status of the message.
     * See [DeliveryStatus] for possible values.
     */
    @ColumnInfo(name = "status")
    val status: String = DeliveryStatus.SENDING,

    // -------------------------------------------------------------------------
    // Sequence / integrity
    // -------------------------------------------------------------------------

    /**
     * Sequence number from the wire envelope.
     * Stored for potential replay detection and ordering disambiguation.
     */
    @ColumnInfo(name = "sequence_num")
    val sequenceNum: Long = 0L

) {

    // -------------------------------------------------------------------------
    // Delivery status constants
    // -------------------------------------------------------------------------

    object DeliveryStatus {
        const val SENDING  = "SENDING"   // Outgoing: written to socket, no ACK yet
        const val SENT     = "SENT"      // Outgoing: ACK received from remote
        const val RECEIVED = "RECEIVED"  // Incoming: successfully decrypted
        const val FAILED   = "FAILED"    // Outgoing: socket write failed
    }

    // -------------------------------------------------------------------------
    // Message type constants (mirrors SecureMessageWrapper.MessageType)
    // -------------------------------------------------------------------------

    object MessageType {
        const val TEXT = "TEXT"
        const val ACK  = "ACK"
    }

    // -------------------------------------------------------------------------
    // Convenience properties (not stored in DB — computed at runtime)
    // -------------------------------------------------------------------------

    /** Formatted time string for display in the chat UI (HH:mm). */
    val formattedTime: String
        get() {
            val cal = java.util.Calendar.getInstance().also {
                it.timeInMillis = timestamp
            }
            return String.format(
                "%02d:%02d",
                cal.get(java.util.Calendar.HOUR_OF_DAY),
                cal.get(java.util.Calendar.MINUTE)
            )
        }

    /** Formatted date string for date separators in the chat UI (MMM dd, yyyy). */
    val formattedDate: String
        get() {
            val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
            return sdf.format(java.util.Date(timestamp))
        }

    /** Returns true if the message has been successfully delivered or received. */
    val isDelivered: Boolean
        get() = status == DeliveryStatus.SENT || status == DeliveryStatus.RECEIVED

    /** Returns true if the message failed to send. */
    val isFailed: Boolean
        get() = status == DeliveryStatus.FAILED

    // -------------------------------------------------------------------------
    // Copy helpers for status updates (Room entities are immutable data classes)
    // -------------------------------------------------------------------------

    /** Returns a copy of this message with status set to [DeliveryStatus.SENT]. */
    fun asSent() = copy(status = DeliveryStatus.SENT)

    /** Returns a copy of this message with status set to [DeliveryStatus.FAILED]. */
    fun asFailed() = copy(status = DeliveryStatus.FAILED)

    companion object {

        /**
         * Factory: creates a [ChatMessage] from a [DecryptedMessage] received over Bluetooth.
         *
         * @param decrypted     The decrypted message from [SecureMessageWrapper.unwrap].
         * @param sessionId     The remote device's Bluetooth MAC address.
         * @param senderName    The display name of the remote device.
         */
        fun fromDecrypted(
            decrypted: com.yourapp.securechat.crypto.DecryptedMessage,
            sessionId: String,
            senderName: String
        ) = ChatMessage(
            id            = decrypted.id,
            sessionId     = sessionId,
            senderAddress = decrypted.sender,
            senderName    = senderName,
            isOutgoing    = false,
            content       = decrypted.content,
            type          = decrypted.type,
            timestamp     = decrypted.timestamp,
            status        = DeliveryStatus.RECEIVED,
            sequenceNum   = decrypted.sequenceNum
        )

        /**
         * Factory: creates an outgoing [ChatMessage] before it is sent.
         *
         * @param id            Unique message ID (generate before sending).
         * @param content       Plaintext message body.
         * @param sessionId     The remote device's Bluetooth MAC address.
         * @param senderAddress The local device's Bluetooth MAC address.
         * @param senderName    The local device's display name.
         * @param sequenceNum   Current outgoing sequence number.
         */
        fun outgoing(
            id: String,
            content: String,
            sessionId: String,
            senderAddress: String,
            senderName: String,
            sequenceNum: Long = 0L
        ) = ChatMessage(
            id            = id,
            sessionId     = sessionId,
            senderAddress = senderAddress,
            senderName    = senderName,
            isOutgoing    = true,
            content       = content,
            type          = MessageType.TEXT,
            timestamp     = System.currentTimeMillis(),
            status        = DeliveryStatus.SENDING,
            sequenceNum   = sequenceNum
        )
    }
}