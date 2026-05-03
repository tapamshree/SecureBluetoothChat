package com.yourapp.securechat.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * ============================================================================
 * FILE: Models.kt
 * ============================================================================
 * 
 * 1. PURPOSE OF THE FILE:
 * To provide the consolidated Domain Models (Kotlin data classes) representing 
 * the core business objects within the application.
 * 
 * 2. HOW IT WORKS:
 * These data classes define the "shape" of the data. 
 * - `ChatMessage` and `ConversationSession` are annotated with `@Entity(tableName = ...)`. 
 *   This signals the Android Room Database compiler to auto-generate SQLite tables matching 
 *   these fields.
 * - `BluetoothDeviceInfo` is entirely stateless and in-memory, mapping raw Android 
 *   `BluetoothDevice` OS objects into pure Kotlin objects that the UI can safely consume.
 * 
 * 3. WHY IS IT IMPORTANT:
 * Having a single source of truth for object definitions is critical to prevent 
 * "Package Drift". If the Database expects a message to look one way, but the UI 
 * expects it differently, the app crashes. Centralizing them ensures the entire 
 * architecture shares the exact same understanding of a "Message" or a "Device".
 * 
 * 4. ROLE IN THE PROJECT:
 * This is the purest layer of the application (Domain Layer). Everything else—UI, 
 * Repositories, Database DAOs, and the Bluetooth service—depends on these models to 
 * shuttle data back and forth. 
 * 
 * 5. WHAT DOES EACH PART DO:
 * - [ChatMessage]: Represents a single bubble in a chat. Holds sender info, timestamps, 
 *   content string, and delivery status (SENDING, SENT, FAILED). Annotated for Room SQLite.
 * - [BluetoothDeviceInfo]: Represents a nearby phone. Holds its MAC address, Bluetooth Name,
 *   and current Bond/Paired state. Not saved to SQLite, just used by RecyclerView adapters.
 * - [ConversationSession]: Represents a running chat instance. Used to group multiple 
 *   [ChatMessage] objects together logically in the database.
 * ============================================================================
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
