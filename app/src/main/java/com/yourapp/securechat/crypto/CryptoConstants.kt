package com.yourapp.securechat.crypto

/**
 * Central constants for AES-GCM encryption used throughout the app.
 *
 * Algorithm choice: AES/GCM/NoPadding
 * - AES-256 for strong symmetric encryption
 * - GCM mode provides authenticated encryption (AEAD):
 *     → Encrypts the data (confidentiality)
 *     → Produces an auth tag (integrity + tamper detection)
 * - NoPadding: GCM is a stream mode, no block padding needed
 */
object CryptoConstants {

    // -------------------------------------------------------------------------
    // Algorithm identifiers
    // -------------------------------------------------------------------------

    /** Full algorithm string passed to javax.crypto.Cipher */
    const val AES_GCM_ALGORITHM = "AES/GCM/NoPadding"

    /** Key generation algorithm */
    const val KEY_ALGORITHM = "AES"

    /** Provider used for key generation inside Android Keystore */
    const val KEYSTORE_PROVIDER = "AndroidKeyStore"

    /** PBKDF2 algorithm used when deriving a key from a PIN/passphrase */
    const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"

    /** Diffie-Hellman key agreement algorithm (for future key exchange) */
    const val DH_ALGORITHM = "ECDH"

    // -------------------------------------------------------------------------
    // Key parameters
    // -------------------------------------------------------------------------

    /** AES key size in bits. 256-bit = maximum security. */
    const val KEY_SIZE_BITS = 256

    /** AES key size in bytes. */
    const val KEY_SIZE_BYTES = KEY_SIZE_BITS / 8  // 32

    // -------------------------------------------------------------------------
    // GCM parameters
    // -------------------------------------------------------------------------

    /**
     * IV (Initialization Vector) size in bytes.
     * GCM standard recommends 96-bit (12-byte) IV for best performance
     * and security. Never reuse an IV with the same key.
     */
    const val GCM_IV_SIZE_BYTES = 12

    /**
     * GCM authentication tag length in bits.
     * 128-bit is the maximum and recommended tag size.
     * Passed to GCMParameterSpec.
     */
    const val GCM_TAG_LENGTH_BITS = 128

    /** GCM auth tag size in bytes (for wire-format calculations). */
    const val GCM_TAG_SIZE_BYTES = GCM_TAG_LENGTH_BITS / 8  // 16

    // -------------------------------------------------------------------------
    // PBKDF2 parameters (PIN / passphrase derived keys)
    // -------------------------------------------------------------------------

    /** Number of PBKDF2 iterations. Higher = slower brute-force attacks. */
    const val PBKDF2_ITERATIONS = 100_000

    /** Salt size in bytes for PBKDF2 key derivation. */
    const val PBKDF2_SALT_SIZE_BYTES = 16

    // -------------------------------------------------------------------------
    // Android Keystore aliases
    // Keys stored in Keystore never leave secure hardware (on supported devices)
    // -------------------------------------------------------------------------

    /** Alias for the long-term identity key stored in Android Keystore. */
    const val KEYSTORE_ALIAS_IDENTITY = "securechat_identity_key"

    /** Alias for the current session key (ephemeral, replaced each session). */
    const val KEYSTORE_ALIAS_SESSION  = "securechat_session_key"

    // -------------------------------------------------------------------------
    // Encrypted message wire format
    //
    //  Byte layout of every encrypted payload sent over Bluetooth:
    //
    //  ┌─────────────────┬───────────────────┬─────────────────────────┐
    //  │   IV (12 bytes) │ AuthTag (16 bytes) │  Ciphertext (N bytes)  │
    //  └─────────────────┴───────────────────┴─────────────────────────┘
    //
    //  Total overhead per message = 28 bytes
    // -------------------------------------------------------------------------
    const val WIRE_IV_OFFSET       = 0
    const val WIRE_AUTH_TAG_OFFSET = GCM_IV_SIZE_BYTES                          // 12
    const val WIRE_CIPHER_OFFSET   = GCM_IV_SIZE_BYTES + GCM_TAG_SIZE_BYTES     // 28
    const val WIRE_HEADER_SIZE     = WIRE_CIPHER_OFFSET                         // 28

    // -------------------------------------------------------------------------
    // Encoding
    // -------------------------------------------------------------------------

    /** Charset used when converting strings to/from bytes for encryption. */
    const val CHARSET = "UTF-8"
}