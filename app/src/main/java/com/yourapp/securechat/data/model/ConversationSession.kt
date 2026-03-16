package com.yourapp.securechat.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * ConversationSession — Room entity representing a chat session with a remote device.
 *
 * Each time two devices connect, a new session is created. The session tracks:
 * - Which device we're communicating with
 * - When the session started/ended
 * - Whether the session is currently active
 *
 * Table: sessions
 */
@Entity(tableName = "sessions")
data class ConversationSession(

    /** Unique session ID (auto-generated). */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    /** Bluetooth MAC address of the remote device. */
    @ColumnInfo(name = "device_address")
    val deviceAddress: String,

    /** Display name of the remote device (from BT adapter). */
    @ColumnInfo(name = "device_name")
    val deviceName: String,

    /** Unix epoch millis when the session started. */
    @ColumnInfo(name = "start_time")
    val startTime: Long = System.currentTimeMillis(),

    /** Unix epoch millis when the session ended. Null if still active. */
    @ColumnInfo(name = "end_time")
    val endTime: Long? = null,

    /** Whether this session is currently active (connected). */
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    /** Number of messages exchanged in this session. */
    @ColumnInfo(name = "message_count")
    val messageCount: Int = 0
) {
    /** Returns a copy marking the session as ended. */
    fun asEnded() = copy(
        isActive = false,
        endTime = System.currentTimeMillis()
    )

    /** Returns a copy with incremented message count. */
    fun withIncrementedCount() = copy(messageCount = messageCount + 1)
}
