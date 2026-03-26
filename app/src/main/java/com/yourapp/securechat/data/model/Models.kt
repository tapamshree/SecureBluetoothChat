package com.yourapp.securechat.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * ============================================================
 * FILE: data/model/Models.kt
 * PROJECT: SecureBluetoothChat
 * ============================================================
 *
 * PURPOSE:
 * Consolidated data model source for the app's core domain objects:
 *   1) [ChatMessage]         - Room entity for persisted messages
 *   2) [BluetoothDeviceInfo] - runtime model for scanned/paired devices
 *   3) [ConversationSession] - Room entity for session metadata
 *
 * WHY CONSOLIDATED:
 * Historically these were split across multiple files and import paths,
 * which caused package drift (`data.ChatMessage` vs `data.model.ChatMessage`).
 * Keeping them together in one file enforces a single canonical package path.
 */

@Entity(
    tableName = "messages",
    indices = [Index(value = ["session_id"]), Index(value = ["timestamp"])]
)
data class ChatMessage(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    @ColumnInfo(name = "sender_address")
    val senderAddress: String,
    @ColumnInfo(name = "sender_name")
    val senderName: String,
    @ColumnInfo(name = "is_outgoing")
    val isOutgoing: Boolean,
    @ColumnInfo(name = "content")
    val content: String,
    @ColumnInfo(name = "type")
    val type: String = MessageType.TEXT,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "status")
    val status: String = DeliveryStatus.SENDING,
    @ColumnInfo(name = "sequence_num")
    val sequenceNum: Long = 0L
) {
    object DeliveryStatus {
        const val SENDING = "SENDING"
        const val SENT = "SENT"
        const val RECEIVED = "RECEIVED"
        const val FAILED = "FAILED"
    }

    object MessageType {
        const val TEXT = "TEXT"
        const val ACK = "ACK"
    }
}

/**
 * Bluetooth device runtime model used by scanner + device list UI.
 * Not a Room entity.
 */
data class BluetoothDeviceInfo(
    val name: String,
    val address: String,
    val bondState: BondState = BondState.NONE,
    val rssi: Int? = null
) {
    enum class BondState { NONE, BONDING, BONDED }
    val isPaired: Boolean get() = bondState == BondState.BONDED
    val displayName: String get() = name.ifBlank { address }

    companion object {
        fun fromBluetoothDevice(device: android.bluetooth.BluetoothDevice): BluetoothDeviceInfo {
            val safeName = try { device.name ?: "" } catch (_: SecurityException) { "" }
            val mappedState = when (device.bondState) {
                android.bluetooth.BluetoothDevice.BOND_BONDED -> BondState.BONDED
                android.bluetooth.BluetoothDevice.BOND_BONDING -> BondState.BONDING
                else -> BondState.NONE
            }
            return BluetoothDeviceInfo(
                name = safeName,
                address = device.address,
                bondState = mappedState
            )
        }
    }
}

@Entity(tableName = "sessions")
data class ConversationSession(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "device_address")
    val deviceAddress: String,
    @ColumnInfo(name = "device_name")
    val deviceName: String,
    @ColumnInfo(name = "start_time")
    val startTime: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "end_time")
    val endTime: Long? = null,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    @ColumnInfo(name = "message_count")
    val messageCount: Int = 0
)
