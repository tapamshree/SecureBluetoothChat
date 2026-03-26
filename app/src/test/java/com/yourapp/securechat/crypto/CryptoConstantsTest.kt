package com.yourapp.securechat.crypto

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [CryptoConstants] — validates constant values are correct
 * and consistent with each other.
 */
class CryptoConstantsTest {

    @Test
    fun `key size bytes equals key size bits divided by 8`() {
        assertEquals(CryptoConstants.KEY_SIZE_BITS / 8, CryptoConstants.KEY_SIZE_BYTES)
    }

    @Test
    fun `AES-256 key size is 256 bits`() {
        assertEquals(256, CryptoConstants.KEY_SIZE_BITS)
    }

    @Test
    fun `GCM IV size is 12 bytes (96 bits)`() {
        assertEquals(12, CryptoConstants.GCM_IV_SIZE_BYTES)
    }

    @Test
    fun `GCM tag length is 128 bits`() {
        assertEquals(128, CryptoConstants.GCM_TAG_LENGTH_BITS)
    }

    @Test
    fun `GCM tag size bytes is 16`() {
        assertEquals(16, CryptoConstants.GCM_TAG_SIZE_BYTES)
    }

    @Test
    fun `wire header size equals IV plus tag size`() {
        assertEquals(
            CryptoConstants.GCM_IV_SIZE_BYTES + CryptoConstants.GCM_TAG_SIZE_BYTES,
            CryptoConstants.WIRE_HEADER_SIZE
        )
    }

    @Test
    fun `wire offsets are consistent`() {
        assertEquals(0, CryptoConstants.WIRE_IV_OFFSET)
        assertEquals(CryptoConstants.GCM_IV_SIZE_BYTES, CryptoConstants.WIRE_AUTH_TAG_OFFSET)
        assertEquals(
            CryptoConstants.GCM_IV_SIZE_BYTES + CryptoConstants.GCM_TAG_SIZE_BYTES,
            CryptoConstants.WIRE_CIPHER_OFFSET
        )
    }

    @Test
    fun `algorithm strings are not empty`() {
        assertTrue(CryptoConstants.AES_GCM_ALGORITHM.isNotBlank())
        assertTrue(CryptoConstants.KEY_ALGORITHM.isNotBlank())
        assertTrue(CryptoConstants.PBKDF2_ALGORITHM.isNotBlank())
    }

    @Test
    fun `PBKDF2 iterations are sufficiently high`() {
        assertTrue(
            "PBKDF2 iterations should be >= 10000 for security",
            CryptoConstants.PBKDF2_ITERATIONS >= 10_000
        )
    }
}
