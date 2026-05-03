package com.yourapp.securechat.crypto

import org.json.JSONObject
import javax.crypto.SecretKey

/**
 * ============================================================================
 * FILE: SecureMessageWrapper.kt
 * ============================================================================
 * 
 * 1. PURPOSE OF THE FILE:
 * To package raw plaintext strings, timestamps, message types, and sequence numbers 
 * into a structured JSON envelope, which is then encrypted by `AESCipher` before
 * transmission. Conversely, it receives decrypted bytes and parses them back into
 * strongly-typed data objects.
 * 
 * 2. HOW IT WORKS:
 * It utilizes an Envelope pattern. Before sending a message (like "Hello"), it creates
 * a JSON object `{id: "123", sender: "MAC", type: "TEXT", content: "Hello", ...}`. 
 * This entire JSON string is serialized to UTF-8 bytes and pushes through AES-GCM 
 * encryption. The receiving device decrypts the bytes back to a JSON string and maps
 * it to a Kotlin `DecryptedMessage` data class.
 * 
 * 3. WHY IS IT IMPORTANT:
 * If we only encrypted the user's message body ("Hello"), the sender wouldn't be 
 * authenticated at the protocol level, and we wouldn't be able to send control commands
 * (like "PING" or "CHESS_INVITE") without confusing them for normal chat text. The 
 * envelope ensures metadata is securely bound to the message content via the AES-GCM 
 * authentication tag, defeating replay and tampering attacks.
 * 
 * 4. ROLE IN THE PROJECT:
 * This is the Application layer of the networking stack. `BluetoothCore` handles socket
 * streams, `AESCipher` handles encryption of bytes, but `SecureMessageWrapper` gives 
 * semantic meaning to those bytes so the `ChatActivity` knows what to display.
 * 
 * 5. WHAT DOES EACH PART DO:
 * - [MessageType]: Standard constants dictating how the receiver handles the payload.
 * - [wrap()]: Bundles raw arguments into a JSON envelope and calls `aesCipher.encrypt()`.
 * - [unwrap()]: Calls `aesCipher.decryptToBytes()` and inflates the result into a `DecryptedMessage`.
 * - [generateMessageId()]: Provides basic idempotency tracking via unique IDs.
 * - [DecryptedMessage]: An immutable data class summarizing the inflated payload.
 * - [MessageParseException]: Custom error for payloads that decrypt successfully but
 *   fail to parse as valid JSON.
 * ============================================================================
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
        sequenceNum: Long = 0L,
        messageId: String = generateMessageId()
    ): ByteArray {
        val envelope = JSONObject().apply {
            put(FIELD_ID,        messageId)
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
        sessionKey: SecretKey,
        content: String = type
    ): ByteArray = wrap(
        content = content,
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
