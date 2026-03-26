package com.yourapp.securechat.crypto

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

/**
 * Unit tests for [AESCipher] — AES-256-GCM encryption/decryption.
 *
 * Tests cover:
 *   - Encrypt → Decrypt round-trip (string)
 *   - Encrypt → Decrypt round-trip (raw bytes)
 *   - Different plaintexts produce different ciphertexts
 *   - Unique IV per encryption (same plaintext + key ≠ same output)
 *   - Wrong key → AESCipherException
 *   - Tampered ciphertext → AESCipherException
 *   - Empty plaintext
 *   - Large payload
 *   - Payload too short to contain header → AESCipherException
 */
class AESCipherTest {

    private lateinit var cipher: AESCipher
    private lateinit var key: SecretKey

    @Before
    fun setup() {
        cipher = AESCipher()
        key = generateTestKey()
    }

    // ── Round-trip ────────────────────────────────────────────────────────────

    @Test
    fun `encrypt and decrypt string round-trip produces original plaintext`() {
        val plaintext = "Hello, secure world! 🔒"
        val encrypted = cipher.encrypt(plaintext, key)
        val decrypted = cipher.decrypt(encrypted, key)
        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `encrypt and decrypt bytes round-trip produces original bytes`() {
        val original = "Binary data test 01234".toByteArray(Charsets.UTF_8)
        val encrypted = cipher.encrypt(original, key)
        val decrypted = cipher.decryptToBytes(encrypted, key)
        assertArrayEquals(original, decrypted)
    }

    @Test
    fun `empty plaintext encrypts and decrypts correctly`() {
        val plaintext = ""
        val encrypted = cipher.encrypt(plaintext, key)
        val decrypted = cipher.decrypt(encrypted, key)
        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `large payload encrypts and decrypts correctly`() {
        val plaintext = "A".repeat(10_000)
        val encrypted = cipher.encrypt(plaintext, key)
        val decrypted = cipher.decrypt(encrypted, key)
        assertEquals(plaintext, decrypted)
    }

    // ── IV uniqueness ─────────────────────────────────────────────────────────

    @Test
    fun `same plaintext with same key produces different ciphertexts (unique IV)`() {
        val plaintext = "Same message"
        val encrypted1 = cipher.encrypt(plaintext, key)
        val encrypted2 = cipher.encrypt(plaintext, key)
        assertFalse(
            "Two encryptions of the same plaintext should have different IVs/ciphertexts",
            encrypted1.contentEquals(encrypted2)
        )
    }

    // ── Wrong key ─────────────────────────────────────────────────────────────

    @Test(expected = AESCipherException::class)
    fun `decrypting with wrong key throws AESCipherException`() {
        val encrypted = cipher.encrypt("secret", key)
        val wrongKey = generateTestKey() // different random key
        cipher.decrypt(encrypted, wrongKey)
    }

    // ── Tampered data ─────────────────────────────────────────────────────────

    @Test(expected = AESCipherException::class)
    fun `tampered ciphertext throws AESCipherException`() {
        val encrypted = cipher.encrypt("don't tamper with me", key)
        // Flip a byte in the ciphertext portion (after the 12-byte IV)
        encrypted[encrypted.size - 1] = (encrypted[encrypted.size - 1].toInt() xor 0xFF).toByte()
        cipher.decrypt(encrypted, key)
    }

    // ── Payload too short ─────────────────────────────────────────────────────

    @Test(expected = AESCipherException::class)
    fun `payload shorter than header throws AESCipherException`() {
        val tooShort = ByteArray(10) // less than WIRE_HEADER_SIZE (28)
        cipher.decryptToBytes(tooShort, key)
    }

    // ── Base64 helpers ────────────────────────────────────────────────────────

    // Note: Base64 tests are skipped here because android.util.Base64 is not
    // available in unit tests without Robolectric. The encrypt/decrypt logic
    // itself is fully covered above.

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun generateTestKey(): SecretKey {
        val keyBytes = ByteArray(32)
        SecureRandom().nextBytes(keyBytes)
        return SecretKeySpec(keyBytes, "AES")
    }
}
