package com.yourapp.securechat.crypto

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

/**
 * Unit tests for [SecureMessageWrapper] — message envelope wrap/unwrap.
 *
 * Tests cover:
 *   - Wrap → Unwrap round-trip preserving all fields
 *   - Different message types (TEXT, ACK, BYE)
 *   - Sequence number preservation
 *   - Custom message ID preservation
 *   - Control frame convenience wrapper
 *   - Wrong key → exception on unwrap
 */
class SecureMessageWrapperTest {

    private val testKey = generateTestKey()

    @Test
    fun `wrap and unwrap preserves all message fields`() {
        val content = "Hello from test!"
        val sender = "AA:BB:CC:DD:EE:FF"
        val sequenceNum = 42L
        val messageId = "test-msg-001"

        val encrypted = SecureMessageWrapper.wrap(
            content = content,
            senderAddress = sender,
            sessionKey = testKey,
            sequenceNum = sequenceNum,
            messageId = messageId
        )

        val decrypted = SecureMessageWrapper.unwrap(encrypted, testKey)

        assertEquals(messageId, decrypted.id)
        assertEquals(sender, decrypted.sender)
        assertEquals(SecureMessageWrapper.MessageType.TEXT, decrypted.type)
        assertEquals(content, decrypted.content)
        assertEquals(sequenceNum, decrypted.sequenceNum)
        assertTrue(decrypted.timestamp > 0)
    }

    @Test
    fun `wrap with ACK type produces ACK message`() {
        val encrypted = SecureMessageWrapper.wrap(
            content = "ack-target-id",
            senderAddress = "11:22:33:44:55:66",
            sessionKey = testKey,
            type = SecureMessageWrapper.MessageType.ACK
        )

        val decrypted = SecureMessageWrapper.unwrap(encrypted, testKey)
        assertEquals(SecureMessageWrapper.MessageType.ACK, decrypted.type)
        assertTrue(decrypted.isControlMessage)
        assertFalse(decrypted.isTextMessage)
    }

    @Test
    fun `wrap with BYE type produces BYE message`() {
        val encrypted = SecureMessageWrapper.wrap(
            content = "",
            senderAddress = "11:22:33:44:55:66",
            sessionKey = testKey,
            type = SecureMessageWrapper.MessageType.BYE
        )

        val decrypted = SecureMessageWrapper.unwrap(encrypted, testKey)
        assertEquals(SecureMessageWrapper.MessageType.BYE, decrypted.type)
        assertTrue(decrypted.isControlMessage)
    }

    @Test
    fun `wrapControl convenience method works`() {
        val encrypted = SecureMessageWrapper.wrapControl(
            type = SecureMessageWrapper.MessageType.PING,
            senderAddress = "AA:BB:CC:DD:EE:FF",
            sessionKey = testKey
        )

        val decrypted = SecureMessageWrapper.unwrap(encrypted, testKey)
        assertEquals(SecureMessageWrapper.MessageType.PING, decrypted.type)
    }

    @Test
    fun `sequence number is preserved`() {
        val seq = 9999L
        val encrypted = SecureMessageWrapper.wrap(
            content = "seq test",
            senderAddress = "AA:BB:CC:DD:EE:FF",
            sessionKey = testKey,
            sequenceNum = seq
        )

        val decrypted = SecureMessageWrapper.unwrap(encrypted, testKey)
        assertEquals(seq, decrypted.sequenceNum)
    }

    @Test(expected = AESCipherException::class)
    fun `unwrap with wrong key throws exception`() {
        val encrypted = SecureMessageWrapper.wrap(
            content = "secret",
            senderAddress = "AA:BB:CC:DD:EE:FF",
            sessionKey = testKey
        )

        val wrongKey = generateTestKey()
        SecureMessageWrapper.unwrap(encrypted, wrongKey)
    }

    @Test
    fun `text message is identified correctly`() {
        val encrypted = SecureMessageWrapper.wrap(
            content = "hello",
            senderAddress = "AA:BB:CC:DD:EE:FF",
            sessionKey = testKey
        )

        val decrypted = SecureMessageWrapper.unwrap(encrypted, testKey)
        assertTrue(decrypted.isTextMessage)
        assertFalse(decrypted.isControlMessage)
    }

    @Test
    fun `unicode content is preserved`() {
        val content = "こんにちは 🔐 مرحبا"
        val encrypted = SecureMessageWrapper.wrap(
            content = content,
            senderAddress = "AA:BB:CC:DD:EE:FF",
            sessionKey = testKey
        )

        val decrypted = SecureMessageWrapper.unwrap(encrypted, testKey)
        assertEquals(content, decrypted.content)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun generateTestKey(): SecretKeySpec {
        val keyBytes = ByteArray(32)
        SecureRandom().nextBytes(keyBytes)
        return SecretKeySpec(keyBytes, "AES")
    }
}
