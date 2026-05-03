package com.yourapp.securechat.crypto

/**
 * ============================================================================
 * FILE: CryptoConstants.kt
 * ============================================================================
 * 
 * 1. PURPOSE OF THE FILE:
 * This file serves as the centralized configuration dictionary for all cryptographic
 * operations within the application. It defines the specific algorithms, key sizes,
 * byte offsets, and keystore aliases used by the security layer.
 * 
 * 2. HOW IT WORKS:
 * It uses a Kotlin `object` to statically expose constant variables (primitive 
 * constants evaluated at compile-time). Other components (like AESCipher or KeyManager)
 * reference these constants instead of hardcoding strings or numbers, ensuring 
 * cryptographic parameters remain uniform across the entire app.
 * 
 * 3. WHY IS IT IMPORTANT:
 * Cryptography requires absolute precision. A single mismatched byte size or algorithm
 * string between the sender and receiver—or between key generation and encryption—
 * will cause the entire security protocol to fail. Centralizing these constants prevents
 * fatal mismatches and makes it trivial to upgrade the protocol security later (e.g., 
 * changing key size or iterations).
 * 
 * 4. ROLE IN THE PROJECT:
 * It acts as the "Settings Configuration" for the whole `crypto` package. It sits at the
 * base dependency level; everything in the crypto layer relies on it, but it relies on nothing.
 * 
 * 5. WHAT DOES EACH PART DO:
 * - [Algorithm Identifiers]: Defines exact cipher strings like "AES/GCM/NoPadding".
 * - [Key Parameters]: Sets the AES key size to 256-bit (maximum security).
 * - [GCM Parameters]: Defines Initialization Vector (IV) size (12 bytes) and Tag size (16 bytes).
 * - [PBKDF2 Parameters]: Configures the iteration count (100,000) for deriving keys from passwords.
 * - [Android Keystore Aliases]: Hardcodes the internal string names for saving keys to the OS.
 * - [Wire Format Offsets]: Defines the exact byte math to parse incoming encrypted data packets.
 * ============================================================================
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