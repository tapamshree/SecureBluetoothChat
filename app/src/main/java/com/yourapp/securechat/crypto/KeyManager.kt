package com.yourapp.securechat.crypto

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.yourapp.securechat.crypto.CryptoConstants.KEY_ALGORITHM
import com.yourapp.securechat.crypto.CryptoConstants.KEY_SIZE_BITS
import com.yourapp.securechat.crypto.CryptoConstants.KEYSTORE_ALIAS_IDENTITY
import com.yourapp.securechat.crypto.CryptoConstants.KEYSTORE_ALIAS_SESSION
import com.yourapp.securechat.crypto.CryptoConstants.KEYSTORE_PROVIDER
import com.yourapp.securechat.crypto.CryptoConstants.PBKDF2_ALGORITHM
import com.yourapp.securechat.crypto.CryptoConstants.PBKDF2_ITERATIONS
import com.yourapp.securechat.crypto.CryptoConstants.PBKDF2_SALT_SIZE_BYTES
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * ============================================================================
 * FILE: KeyManager.kt
 * ============================================================================
 * 
 * 1. PURPOSE OF THE FILE:
 * To handle the complete lifecycle (generation, storage, retrieval, and deletion) 
 * of AES-256 SecretKeys used by the application for both identity verification and 
 * active chat sessions.
 * 
 * 2. HOW IT WORKS:
 * It bridges the gap between raw cryptographic key bytes and the physical Android OS 
 * `KeyStore`. Specifically, it uses `KeyGenerator` initialized with `KeyGenParameterSpec`
 * to request the Android OS to generate keys inside the secure hardware (StrongBox/TEE).
 * It also handles raw ephemeral key generation for short-lived chat sessions and 
 * delegates PBKDF2 derivations for password-based keys.
 * 
 * 3. WHY IS IT IMPORTANT:
 * If an AES key is leaked, the cipher is broken. By using the Android Keystore, this 
 * file ensures that the master identity key never touches RAM and cannot be exported.
 * Proper key management prevents attackers from stealing the keys via malware or 
 * physical device extraction.
 * 
 * 4. ROLE IN THE PROJECT:
 * The KeyManager is the "Vault". It provides `AESCipher` with the actual keys it 
 * needs to perform encryption, and is utilized heavily by the `BluetoothChatService` 
 * during the initial handshake sequence to setup the session secret.
 * 
 * 5. WHAT DOES EACH PART DO:
 * - [generateKeystoreKey()]: Inks a new hardware-backed AES-256 key into the Android OS.
 * - [getKeystoreKey()]: Safely looks up an existing key without exporting its material.
 * - [getOrCreateKeystoreKey()]: Convenience method ensuring a key is always available.
 * - [clearSessionKey() / deleteKeystoreKey()]: Securely wipes keys from the device to 
 *   guarantee forward secrecy.
 * - [generateSessionKey()]: Generates volatile, RAM-only AES keys intended to die when 
 *   the Bluetooth socket closes.
 * - [deriveKeyFromPassphrase()]: Uses PBKDF2 to turn a weak human password into a 
 *   strong 256-bit AES key.
 * ============================================================================
 */
class KeyManager(private val context: Context) {

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(KEYSTORE_PROVIDER).also { it.load(null) }
    }

    // -------------------------------------------------------------------------
    // Android Keystore — hardware-backed key generation
    // -------------------------------------------------------------------------

    /**
     * Generates a new AES-256 key inside the Android Keystore.
     *
     * The key is stored under [alias] and never leaves the secure hardware
     * on devices that support StrongBox or TEE (Trusted Execution Environment).
     *
     * @param alias  Keystore alias (use [KEYSTORE_ALIAS_IDENTITY] or [KEYSTORE_ALIAS_SESSION]).
     * @param requireUserAuth  If true, key use requires biometric/PIN authentication.
     * @return  The generated [SecretKey].
     */
    fun generateKeystoreKey(
        alias: String = KEYSTORE_ALIAS_IDENTITY,
        requireUserAuth: Boolean = false
    ): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KEY_ALGORITHM, KEYSTORE_PROVIDER)

        val specBuilder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(true) // Enforces unique IV per operation

        // Require user authentication (biometric/PIN) before key use
        if (requireUserAuth) {
            specBuilder.setUserAuthenticationRequired(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                specBuilder.setUserAuthenticationParameters(
                    30, // Auth valid for 30 seconds
                    KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                )
            }
        }

        // Use StrongBox (dedicated security chip) if available on device
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                specBuilder.setIsStrongBoxBacked(true)
                keyGenerator.init(specBuilder.build())
                return keyGenerator.generateKey()
            } catch (e: Exception) {
                // StrongBox not available — fall back to TEE
                specBuilder.setIsStrongBoxBacked(false)
            }
        }

        keyGenerator.init(specBuilder.build())
        return keyGenerator.generateKey()
    }

    /**
     * Retrieves an existing key from the Android Keystore.
     *
     * @param alias  The Keystore alias the key was stored under.
     * @return  The [SecretKey], or null if no key exists for [alias].
     */
    fun getKeystoreKey(alias: String = KEYSTORE_ALIAS_IDENTITY): SecretKey? {
        return try {
            val entry = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
            entry?.secretKey
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Returns an existing Keystore key, or generates a new one if none exists.
     *
     * @param alias  The Keystore alias to look up or create.
     * @return  The [SecretKey].
     */
    fun getOrCreateKeystoreKey(alias: String = KEYSTORE_ALIAS_IDENTITY): SecretKey {
        return getKeystoreKey(alias) ?: generateKeystoreKey(alias)
    }

    /**
     * Checks whether a key exists in the Keystore under [alias].
     */
    fun hasKeystoreKey(alias: String): Boolean {
        return try {
            keyStore.containsAlias(alias)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Deletes a key from the Android Keystore.
     * Call this when a session ends or to rotate keys.
     *
     * @param alias  The alias of the key to delete.
     */
    fun deleteKeystoreKey(alias: String) {
        try {
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
            }
        } catch (e: Exception) {
            // Key didn't exist or already deleted — safe to ignore
        }
    }

    /**
     * Deletes the current session key from the Keystore.
     * Call this when a Bluetooth connection is closed.
     */
    fun clearSessionKey() {
        deleteKeystoreKey(KEYSTORE_ALIAS_SESSION)
    }

    // -------------------------------------------------------------------------
    // In-memory raw key generation (for session keys)
    // -------------------------------------------------------------------------

    /**
     * Generates a fresh AES-256 key in memory (not stored in Keystore).
     *
     * Used for ephemeral session keys that are negotiated per-connection
     * and should not persist beyond the session.
     *
     * @return  A new random [SecretKey].
     */
    fun generateSessionKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KEY_ALGORITHM)
        keyGenerator.init(KEY_SIZE_BITS, SecureRandom())
        return keyGenerator.generateKey()
    }

    /**
     * Reconstructs a [SecretKey] from raw key bytes.
     *
     * Used when the session key has been received from the remote device
     * (e.g., after a Diffie-Hellman key exchange).
     *
     * @param keyBytes  Raw 32-byte (256-bit) AES key material.
     * @return  A [SecretKey] wrapping the provided bytes.
     * @throws IllegalArgumentException if keyBytes length is not [KEY_SIZE_BITS]/8.
     */
    fun keyFromBytes(keyBytes: ByteArray): SecretKey {
        require(keyBytes.size == KEY_SIZE_BITS / 8) {
            "Invalid key length: expected ${KEY_SIZE_BITS / 8} bytes, got ${keyBytes.size}"
        }
        return SecretKeySpec(keyBytes, KEY_ALGORITHM)
    }

    /**
     * Returns the raw bytes of a [SecretKey].
     * Use only for transmitting session keys during handshake.
     * Never log or persist these bytes.
     */
    fun keyToBytes(key: SecretKey): ByteArray = key.encoded

    // -------------------------------------------------------------------------
    // PBKDF2 — PIN / passphrase derived keys
    // -------------------------------------------------------------------------

    /**
     * Derives an AES-256 key from a user-provided passphrase using PBKDF2.
     *
     * Use this when both users agree on a shared PIN/passphrase instead of
     * performing a full Diffie-Hellman exchange.
     *
     * @param passphrase  The shared secret string (e.g., a PIN).
     * @param salt        Random salt bytes. Generate once and share with peer,
     *                    or derive deterministically from device addresses.
     * @return  A [SecretKey] derived from the passphrase.
     */
    fun deriveKeyFromPassphrase(passphrase: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(
            passphrase.toCharArray(),
            salt,
            PBKDF2_ITERATIONS,
            KEY_SIZE_BITS
        )
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val keyBytes = factory.generateSecret(spec).encoded
        spec.clearPassword() // Clear sensitive data from memory
        return SecretKeySpec(keyBytes, KEY_ALGORITHM)
    }

    /**
     * Generates a cryptographically secure random salt for PBKDF2.
     * The salt should be shared with the remote peer so both devices
     * derive the same key from the same passphrase.
     */
    fun generateSalt(): ByteArray {
        val salt = ByteArray(PBKDF2_SALT_SIZE_BYTES)
        SecureRandom().nextBytes(salt)
        return salt
    }
}