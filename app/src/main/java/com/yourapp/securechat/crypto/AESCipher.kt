package com.yourapp.securechat.crypto

import android.util.Base64
import com.yourapp.securechat.crypto.CryptoConstants.AES_GCM_ALGORITHM
import com.yourapp.securechat.crypto.CryptoConstants.CHARSET
import com.yourapp.securechat.crypto.CryptoConstants.GCM_IV_SIZE_BYTES
import com.yourapp.securechat.crypto.CryptoConstants.GCM_TAG_LENGTH_BITS
import com.yourapp.securechat.crypto.CryptoConstants.WIRE_CIPHER_OFFSET
import com.yourapp.securechat.crypto.CryptoConstants.WIRE_HEADER_SIZE
import com.yourapp.securechat.crypto.CryptoConstants.WIRE_IV_OFFSET
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * ============================================================================
 * FILE: AESCipher.kt
 * ============================================================================
 * 
 * 1. PURPOSE OF THE FILE:
 * To provide the core cryptographic engines for securely encrypting and decrypting
 * the exact byte payloads sent over the Bluetooth socket.
 * 
 * 2. HOW IT WORKS:
 * It utilizes the Java Cryptography Extension (JCE) `javax.crypto.Cipher` class 
 * configured for AES-256 in Galois/Counter Mode (GCM). 
 * - Encryption: It dynamically generates a random 12-byte Initialization Vector (IV), 
 *   encrypts the payload, and produces a 16-byte authentication tag. It then packs 
 *   these together into a single byte array (the "wire format") to send over Bluetooth.
 * - Decryption: It receives the wire format byte array, extracts the IV, and feeds the
 *   ciphertext and authentication tag back into the cipher, yielding the plaintext.
 * 
 * 3. WHY IS IT IMPORTANT:
 * This file is the absolute bedrock of the "Secure" in CipherLink. Without 
 * this process, chat data would be broadcasted in plain text over the airwaves, easily
 * interceptable by anyone running a packet sniffer nearby.
 * 
 * 4. ROLE IN THE PROJECT:
 * This component acts as the "Middleman" between the application data formatting layer 
 * (`SecureMessageWrapper`) and the raw Bluetooth Socket output stream. It is strictly
 * focused on byte-level encryption, agnostic to what those bytes actually mean.
 * 
 * 5. WHAT DOES EACH PART DO:
 * - [encrypt()]: Converts plain string/bytes into safe ciphertext along with its IV and Tag.
 * - [decrypt() / decryptToBytes()]: Validates the Tag, uses the IV, and reverses the encryption.
 * - [encryptToBase64() / decryptFromBase64()]: Helper methods to convert binary cipher 
 *   data into printable ASCII strings (useful for debugging).
 * - [generateIV()]: A private helper that queries `SecureRandom` to ensure IVs are never reused.
 * - [AESCipherException]: A custom error type to cleanly handle internal decryption failures 
 *   (like tampered packets resulting in `AEADBadTagException`).
 * ============================================================================
 */
class AESCipher {

    private val secureRandom = SecureRandom()

    // -------------------------------------------------------------------------
    // Encrypt
    // -------------------------------------------------------------------------

    /**
     * Encrypts a plaintext string using AES-256-GCM.
     *
     * @param plaintext  The message string to encrypt.
     * @param key        The AES-256 [SecretKey] shared between both devices.
     * @return           Byte array in wire format: [IV | AuthTag | Ciphertext]
     * @throws AESCipherException on any cryptographic failure.
     */
    fun encrypt(plaintext: String, key: SecretKey): ByteArray {
        return encrypt(plaintext.toByteArray(Charsets.UTF_8), key)
    }

    /**
     * Encrypts raw bytes using AES-256-GCM.
     *
     * @param plainBytes Raw bytes to encrypt.
     * @param key        The AES-256 [SecretKey].
     * @return           Byte array in wire format: [IV | AuthTag | Ciphertext]
     */
    fun encrypt(plainBytes: ByteArray, key: SecretKey): ByteArray {
        return try {
            // 1. Generate a fresh random IV for every message
            //    CRITICAL: Never reuse an IV with the same key in GCM mode
            val iv = generateIV()

            // 2. Initialize cipher in ENCRYPT mode with GCM parameters
            val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)

            // 3. Perform encryption
            //    Android's GCM implementation appends the 16-byte AuthTag
            //    to the end of the ciphertext automatically.
            val cipherTextWithTag = cipher.doFinal(plainBytes)

            // 4. Assemble wire format: IV (12) + CipherText+Tag (N+16)
            //    Total size = 12 + N + 16
            val output = ByteArray(GCM_IV_SIZE_BYTES + cipherTextWithTag.size)
            System.arraycopy(iv,             0, output, WIRE_IV_OFFSET,     GCM_IV_SIZE_BYTES)
            System.arraycopy(cipherTextWithTag, 0, output, GCM_IV_SIZE_BYTES, cipherTextWithTag.size)

            output
        } catch (e: Exception) {
            throw AESCipherException("Encryption failed: ${e.message}", e)
        }
    }

    // -------------------------------------------------------------------------
    // Decrypt
    // -------------------------------------------------------------------------

    /**
     * Decrypts an encrypted byte array (wire format) back to a plaintext string.
     *
     * @param encryptedBytes  Byte array in wire format: [IV | AuthTag | Ciphertext]
     * @param key             The AES-256 [SecretKey] shared between both devices.
     * @return                Decrypted plaintext string.
     * @throws AESCipherException if decryption or authentication fails.
     */
    fun decrypt(encryptedBytes: ByteArray, key: SecretKey): String {
        return decryptInternal(encryptedBytes, key).toString(Charsets.UTF_8)
    }

    /**
     * Decrypts an encrypted byte array (wire format) to raw bytes.
     *
     * @param encryptedBytes  Byte array in wire format: [IV | AuthTag | Ciphertext]
     * @param key             The AES-256 [SecretKey].
     * @return                Decrypted raw bytes.
     * @throws AESCipherException if decryption or GCM authentication fails.
     */
    fun decryptToBytes(encryptedBytes: ByteArray, key: SecretKey): ByteArray {
        if (encryptedBytes.size <= WIRE_HEADER_SIZE) {
            throw AESCipherException(
                "Encrypted payload too short: ${encryptedBytes.size} bytes " +
                "(minimum is ${WIRE_HEADER_SIZE + 1})"
            )
        }

        return try {
            // 1. Extract IV from the first 12 bytes
            val iv = encryptedBytes.copyOfRange(WIRE_IV_OFFSET, GCM_IV_SIZE_BYTES)

            // 2. Extract ciphertext + auth tag (everything after the IV)
            //    Android's Cipher.doFinal in DECRYPT mode expects ciphertext
            //    with the tag appended — which is exactly how we stored it.
            val cipherTextWithTag = encryptedBytes.copyOfRange(GCM_IV_SIZE_BYTES, encryptedBytes.size)

            // 3. Initialize cipher in DECRYPT mode
            val cipher = Cipher.getInstance(AES_GCM_ALGORITHM)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)

            // 4. Decrypt — throws AEADBadTagException if auth tag doesn't match
            //    (i.e., message was tampered with or wrong key used)
            cipher.doFinal(cipherTextWithTag)

        } catch (e: javax.crypto.AEADBadTagException) {
            throw AESCipherException(
                "Authentication failed: message may have been tampered with or " +
                "the wrong key was used.", e
            )
        } catch (e: Exception) {
            throw AESCipherException("Decryption failed: ${e.message}", e)
        }
    }

    // -------------------------------------------------------------------------
    // Base64 helpers (for logging / display — never store keys in Base64)
    // -------------------------------------------------------------------------

    /**
     * Encrypts plaintext and returns the result as a Base64 string.
     * Useful for logging or displaying encrypted payloads (not for transport).
     */
    fun encryptToBase64(plaintext: String, key: SecretKey): String {
        return Base64.encodeToString(encrypt(plaintext, key), Base64.NO_WRAP)
    }

    /**
     * Decrypts a Base64-encoded encrypted string.
     */
    fun decryptFromBase64(base64Encrypted: String, key: SecretKey): String {
        val bytes = Base64.decode(base64Encrypted, Base64.NO_WRAP)
        return decrypt(bytes, key)
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Generates a cryptographically secure random 12-byte IV.
     * A new IV must be generated for every single encryption operation.
     */
    private fun generateIV(): ByteArray {
        val iv = ByteArray(GCM_IV_SIZE_BYTES)
        secureRandom.nextBytes(iv)
        return iv
    }

    /**
     * Internal decrypt that returns ByteArray (avoids double charset conversion).
     * Routes through [decryptToBytes] for all validation and cipher logic.
     */
    private fun decryptInternal(encryptedBytes: ByteArray, key: SecretKey): ByteArray {
        return decryptToBytes(encryptedBytes, key)
    }
}

// -------------------------------------------------------------------------
// Exception
// -------------------------------------------------------------------------

/**
 * Thrown when any encryption or decryption operation fails.
 * Wraps underlying JCE exceptions with a readable message.
 */
class AESCipherException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
