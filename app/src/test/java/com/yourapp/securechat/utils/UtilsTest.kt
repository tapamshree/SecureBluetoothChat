package com.yourapp.securechat.utils

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for utility classes in Utils.kt.
 */
class UtilsTest {

    // ── ByteUtils ────────────────────────────────────────────────────────────

    @Test
    fun `toHexString converts bytes to lowercase hex`() {
        val bytes = byteArrayOf(0x0A, 0xFF.toByte(), 0x00, 0x7B)
        assertEquals("0aff007b", ByteUtils.toHexString(bytes))
    }

    @Test
    fun `fromHexString converts hex string to bytes`() {
        val hex = "0aff007b"
        val expected = byteArrayOf(0x0A, 0xFF.toByte(), 0x00, 0x7B)
        assertArrayEquals(expected, ByteUtils.fromHexString(hex))
    }

    @Test
    fun `toHexString and fromHexString are inverse operations`() {
        val original = byteArrayOf(0x01, 0x23, 0x45, 0x67, 0x89.toByte(), 0xAB.toByte())
        val hex = ByteUtils.toHexString(original)
        val result = ByteUtils.fromHexString(hex)
        assertArrayEquals(original, result)
    }

    @Test
    fun `empty byte array converts to empty hex string`() {
        assertEquals("", ByteUtils.toHexString(byteArrayOf()))
    }

    @Test
    fun `empty hex string converts to empty byte array`() {
        assertArrayEquals(byteArrayOf(), ByteUtils.fromHexString(""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `odd-length hex string throws exception`() {
        ByteUtils.fromHexString("0af")
    }

    // ── Extensions ───────────────────────────────────────────────────────────

    @Test
    fun `toFormattedTime returns HH-mm format`() {
        // Just verify it returns a non-empty string in expected format
        val timestamp = System.currentTimeMillis()
        val formatted = with(Extensions) { timestamp.toFormattedTime() }
        assertTrue(formatted.isNotBlank())
        assertTrue("Should match HH:mm pattern", formatted.matches(Regex("\\d{2}:\\d{2}")))
    }
}
