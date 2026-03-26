package com.yourapp.securechat.data.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for data models — [ChatMessage], [BluetoothDeviceInfo], [ConversationSession].
 */
class ModelsTest {

    // ── ChatMessage ──────────────────────────────────────────────────────────

    @Test
    fun `ChatMessage default type is TEXT`() {
        val msg = createTestMessage()
        assertEquals(ChatMessage.MessageType.TEXT, msg.type)
    }

    @Test
    fun `ChatMessage default status is SENDING`() {
        val msg = createTestMessage()
        assertEquals(ChatMessage.DeliveryStatus.SENDING, msg.status)
    }

    @Test
    fun `ChatMessage default timestamp is positive`() {
        val msg = createTestMessage()
        assertTrue(msg.timestamp > 0)
    }

    @Test
    fun `ChatMessage equality based on all fields`() {
        val msg1 = createTestMessage(id = "same-id")
        val msg2 = createTestMessage(id = "same-id")
        assertEquals(msg1, msg2)
    }

    @Test
    fun `ChatMessage inequality on different ids`() {
        val msg1 = createTestMessage(id = "id-1")
        val msg2 = createTestMessage(id = "id-2")
        assertNotEquals(msg1, msg2)
    }

    // ── BluetoothDeviceInfo ──────────────────────────────────────────────────

    @Test
    fun `BluetoothDeviceInfo displayName returns name when available`() {
        val device = BluetoothDeviceInfo(name = "My Phone", address = "AA:BB:CC:DD:EE:FF")
        assertEquals("My Phone", device.displayName)
    }

    @Test
    fun `BluetoothDeviceInfo displayName returns address when name is blank`() {
        val device = BluetoothDeviceInfo(name = "", address = "AA:BB:CC:DD:EE:FF")
        assertEquals("AA:BB:CC:DD:EE:FF", device.displayName)
    }

    @Test
    fun `BluetoothDeviceInfo isPaired returns true for BONDED state`() {
        val device = BluetoothDeviceInfo(
            name = "Paired Device",
            address = "AA:BB:CC:DD:EE:FF",
            bondState = BluetoothDeviceInfo.BondState.BONDED
        )
        assertTrue(device.isPaired)
    }

    @Test
    fun `BluetoothDeviceInfo isPaired returns false for NONE state`() {
        val device = BluetoothDeviceInfo(
            name = "New Device",
            address = "AA:BB:CC:DD:EE:FF",
            bondState = BluetoothDeviceInfo.BondState.NONE
        )
        assertFalse(device.isPaired)
    }

    @Test
    fun `BluetoothDeviceInfo default bondState is NONE`() {
        val device = BluetoothDeviceInfo(name = "Test", address = "00:00:00:00:00:00")
        assertEquals(BluetoothDeviceInfo.BondState.NONE, device.bondState)
    }

    // ── ConversationSession ──────────────────────────────────────────────────

    @Test
    fun `ConversationSession default isActive is true`() {
        val session = ConversationSession(
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            deviceName = "Test Device"
        )
        assertTrue(session.isActive)
    }

    @Test
    fun `ConversationSession default messageCount is 0`() {
        val session = ConversationSession(
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            deviceName = "Test"
        )
        assertEquals(0, session.messageCount)
    }

    @Test
    fun `ConversationSession default endTime is null`() {
        val session = ConversationSession(
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            deviceName = "Test"
        )
        assertNull(session.endTime)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun createTestMessage(
        id: String = "test-id",
        sessionId: String = "session-1",
        content: String = "Hello"
    ) = ChatMessage(
        id = id,
        sessionId = sessionId,
        senderAddress = "AA:BB:CC:DD:EE:FF",
        senderName = "Tester",
        isOutgoing = true,
        content = content
    )
}
