package com.yourapp.securechat.crypto

import org.json.JSONObject
import javax.crypto.SecretKey

/**
 * SecureMessageWrapper — Packages a plaintext chat message into a structured
 * encrypted payload, and unpacks received encrypted payloads back to readable messages.
 *
 * Why this layer exists:
 * [AESCipher] only handles raw bytes ↔ encrypted bytes. This wrapper adds a
 * structured envelope around the message BEFORE encryption, so that metadata
 * (sender ID, timestamp, message type, sequence number) travels with the
 * ciphertext and is also protected by AES-GCM authentication.
 *
 * Plaintext JSON envelope (encrypted as a whole):
 * {
 *   "id":        "uuid-string",        // Unique message ID
 *   "sender":    "device-address",     // Sender's BT MAC address
 *   "type":      "TEXT",               // Message type (TEXT, ACK, PING, etc.)
 *   "content":   "Hello!",             // The actual message body
 *   "timestamp": 1712345678901,        // Unix epoch milliseconds
 *   "seq":       42                    // Sequence number (replay attack prevention)
 * }
 *
 * The entire JSON object is encrypted — metadata is NOT visible in transit.
 *
 * Usage:
 *   // Sender side
 *   val payload = SecureMessageWrapper.wrap("Hello!", senderAddress, sessionKey)
 *   bluetoothSocket.outputStream.write(payload)
 *
 *   // Receiver side
 *   val message = SecureMessageWrapper.unwrap(receivedBytes, sessionKey)
 *   displayMessage(message.content)
 */
object SecureMessageWrapper {

    private val aesCipher = AESCipher()

    // -------------------------------------------------------------------------
    // Message types
    // -------------------------------------------------------------------------

    object MessageType {
        const val TEXT  = "TEXT"   // Regular chat message
        const val ACK   = "ACK"    // Delivery acknowledgement
        const val PING  = "PING"   // Keep-alive / connection check
        const val PONG  = "PONG"   // Response to PING
        const val BYE   = "BYE"    // Graceful disconnect signal
    }

    // JSON field keys
    private const val FIELD_ID        = "id"
    private const val FIELD_SENDER    = "sender"
    private const val FIELD_TYPE      = "type"
    private const val FIELD_CONTENT   = "content"
    private const val FIELD_TIMESTAMP = "timestamp"
    private const val FIELD_SEQ       = "seq"

    // -------------------------------------------------------------------------
    // Wrap (encrypt outgoing message)
    // -------------------------------------------------------------------------

    /**
     * Wraps a plaintext string into an encrypted [WrappedMessage] byte array.
     *
     * Steps:
     *  1. Build a JSON envelope containing the message + metadata
     *  2. Serialize the JSON to UTF-8 bytes
     *  3. Encrypt the bytes with AES-GCM using the session key
     *  4. Return the encrypted byte array (ready to write to BT socket)
     *
     * @param content       The plaintext message body to send.
     * @param senderAddress The sender's Bluetooth MAC address.
     * @param sessionKey    The shared AES-256 session key.
     * @param type          Message type — defaults to [MessageType.TEXT].
     * @param sequenceNum   Monotonically increasing sequence number.
     * @return              Encrypted byte array in AES-GCM wire format.
     */
    fun wrap(
        content: String,
        senderAddress: String,
        sessionKey: SecretKey,
        type: String = MessageType.TEXT,
        sequenceNum: Long = 0L
    ): ByteArray {
        val envelope = JSONObject().apply {
            put(FIELD_ID,        generateMessageId())
            put(FIELD_SENDER,    senderAddress)
            put(FIELD_TYPE,      type)
            put(FIELD_CONTENT,   content)
            put(FIELD_TIMESTAMP, System.currentTimeMillis())
            put(FIELD_SEQ,       sequenceNum)
        }

        val plainBytes = envelope.toString().toByteArray(Charsets.UTF_8)
        return aesCipher.encrypt(plainBytes, sessionKey)
    }

    /**
     * Convenience wrapper for sending control messages (ACK, PING, PONG, BYE).
     *
     * @param type          One of [MessageType] constants.
     * @param senderAddress The sender's Bluetooth MAC address.
     * @param sessionKey    The shared AES-256 session key.
     * @return              Encrypted byte array ready for transmission.
     */
    fun wrapControl(
        type: String,
        senderAddress: String,
        sessionKey: SecretKey
    ): ByteArray = wrap(
        content = type,
        senderAddress = senderAddress,
        sessionKey = sessionKey,
        type = type
    )

    // -------------------------------------------------------------------------
    // Unwrap (decrypt incoming message)
    // -------------------------------------------------------------------------

    /**
     * Decrypts and unwraps an incoming encrypted byte array into a [DecryptedMessage].
     *
     * Steps:
     *  1. Decrypt the byte array with AES-GCM (throws if tampered)
     *  2. Parse the decrypted bytes as a JSON envelope
     *  3. Return a typed [DecryptedMessage] data class
     *
     * @param encryptedBytes  Raw bytes received from the Bluetooth socket.
     * @param sessionKey      The shared AES-256 session key.
     * @return                A [DecryptedMessage] with all fields populated.
     * @throws AESCipherException   If decryption or authentication fails.
     * @throws MessageParseException If the decrypted payload is not valid JSON.
     */
    fun unwrap(encryptedBytes: ByteArray, sessionKey: SecretKey): DecryptedMessage {
        // 1. Decrypt
        val plainBytes = try {
            aesCipher.decryptToBytes(encryptedBytes, sessionKey)
        } catch (e: AESCipherException) {
            throw e // Re-throw crypto failures as-is
        }

        // 2. Parse JSON
        val json = try {
            JSONObject(String(plainBytes, Charsets.UTF_8))
        } catch (e: Exception) {
            throw MessageParseException("Failed to parse decrypted message as JSON: ${e.message}", e)
        }

        // 3. Build result
        return try {
            DecryptedMessage(
                id          = json.getString(FIELD_ID),
                sender      = json.getString(FIELD_SENDER),
                type        = json.getString(FIELD_TYPE),
                content     = json.getString(FIELD_CONTENT),
                timestamp   = json.getLong(FIELD_TIMESTAMP),
                sequenceNum = json.getLong(FIELD_SEQ)
            )
        } catch (e: Exception) {
            throw MessageParseException("Missing required field in message envelope: ${e.message}", e)
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Generates a simple unique message ID using timestamp + random suffix.
     * For production, consider replacing with UUID.randomUUID().toString()
     */
    private fun generateMessageId(): String {
        val timestamp = System.currentTimeMillis()
        val random = (Math.random() * 0xFFFF).toInt()
        return "${timestamp}_${Integer.toHexString(random)}"
    }
}

// -------------------------------------------------------------------------
// Data classes
// -------------------------------------------------------------------------

/**
 * Represents a fully decrypted and parsed incoming message.
 *
 * @property id           Unique message identifier.
 * @property sender       Bluetooth MAC address of the sender.
 * @property type         Message type (see [SecureMessageWrapper.MessageType]).
 * @property content      The plaintext message body.
 * @property timestamp    Unix epoch time in milliseconds when the message was sent.
 * @property sequenceNum  Sender's sequence number (for replay attack detection).
 */
data class DecryptedMessage(
    val id: String,
    val sender: String,
    val type: String,
    val content: String,
    val timestamp: Long,
    val sequenceNum: Long
) {
    /** Returns true if this is a regular chat message. */
    val isTextMessage: Boolean get() = type == SecureMessageWrapper.MessageType.TEXT

    /** Returns true if this is a control message (ACK, PING, PONG, BYE). */
    val isControlMessage: Boolean get() = !isTextMessage

    /** Formatted timestamp as a readable string (HH:mm). */
    val formattedTime: String get() {
        val cal = java.util.Calendar.getInstance().also {
            it.timeInMillis = timestamp
        }
        return String.format(
            "%02d:%02d",
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE)
        )
    }
}

/**
 * Thrown when a decrypted payload cannot be parsed as a valid message envelope.
 */
class MessageParseException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)