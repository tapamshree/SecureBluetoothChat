package com.yourapp.securechat.data.local

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.yourapp.securechat.data.model.ChatMessage
import com.yourapp.securechat.data.model.ConversationSession

/**
 * ============================================================
 * FILE: data/local/Daos.kt
 * PROJECT: SecureBluetoothChat
 * ============================================================
 *
 * PURPOSE:
 * Consolidates Room DAO interfaces in a single file:
 *   - [MessageDao]
 *   - [SessionDao]
 */

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<ChatMessage>)

    @Update
    suspend fun update(message: ChatMessage)

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateStatus(messageId: String, status: String)

    @Delete
    suspend fun delete(message: ChatMessage)

    @Query("DELETE FROM messages WHERE session_id = :sessionId")
    suspend fun deleteBySession(sessionId: String)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()

    @Query("SELECT * FROM messages WHERE session_id = :sessionId ORDER BY timestamp ASC")
    fun getMessagesBySession(sessionId: String): LiveData<List<ChatMessage>>

    @Query("""
        SELECT * FROM messages
        WHERE id IN (
            SELECT id FROM messages
            GROUP BY session_id
            ORDER BY timestamp DESC
        )
        ORDER BY timestamp DESC
    """)
    fun getLatestMessagePerSession(): LiveData<List<ChatMessage>>

    @Query("SELECT * FROM messages WHERE status = 'SENDING' ORDER BY timestamp ASC")
    suspend fun getUnsentMessages(): List<ChatMessage>
}

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: ConversationSession): Long

    @Update
    suspend fun update(session: ConversationSession)

    @Query("UPDATE sessions SET is_active = 0, end_time = :endTime WHERE id = :sessionId")
    suspend fun endSession(sessionId: Long, endTime: Long = System.currentTimeMillis())

    @Query("UPDATE sessions SET message_count = message_count + 1 WHERE id = :sessionId")
    suspend fun incrementMessageCount(sessionId: Long)

    @Delete
    suspend fun delete(session: ConversationSession)

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()

    @Query("SELECT * FROM sessions ORDER BY start_time DESC")
    fun getAllSessions(): LiveData<List<ConversationSession>>

    @Query("SELECT * FROM sessions WHERE is_active = 1 LIMIT 1")
    suspend fun getActiveSession(): ConversationSession?
}
