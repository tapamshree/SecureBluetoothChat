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
 * ============================================================================
 * FILE: Daos.kt
 * ============================================================================
 * 
 * 1. PURPOSE OF THE FILE:
 * To provide the Data Access Objects (DAOs) for the Android Room persistence library.
 * These interfaces define the standard CRUD (Create, Read, Update, Delete) operations 
 * required to interact with the underlying SQLite database.
 * 
 * 2. HOW IT WORKS:
 * Android Room parses the `@Dao`, `@Insert`, and `@Query` annotations at compile-time 
 * and automatically generates the raw SQL boilerplate needed to safely execute these 
 * operations. Functions returning `LiveData` automatically observe the database for 
 * changes on a background thread and push updates to the UI, while `suspend func` 
 * methods execute one-off statements asynchronously via Kotlin Coroutines.
 * 
 * 3. WHY IS IT IMPORTANT:
 * This acts as the secure, type-safe perimeter shielding the rest of the application 
 * from risky string-based raw SQL queries. Without it, reading/writing chat history 
 * would require manual cursor tracking, explicit thread management to avoid freezing 
 * the UI, and lack compile-time query verification.
 * 
 * 4. ROLE IN THE PROJECT:
 * Daos are strictly the Persistence mechanism. They sit between the Repository pattern 
 * (`ChatRepository`) and the local Database, completely ignorant of anything related 
 * to UI or Bluetooth hardware.
 * 
 * 5. WHAT DOES EACH PART DO:
 * - [MessageDao]: Focuses strictly on `ChatMessage` objects. It provides queries to 
 *   fetch streams of messages for a specific session (`getMessagesBySession`), insert 
 *   history, and locate unsent messages.
 * - [SessionDao]: Handles the metadata of a conversation (`ConversationSession`), 
 *   including opening new sessions, fetching active sessions by MAC address, and 
 *   ending instances securely.
 * ============================================================================
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

    @Query("SELECT * FROM sessions WHERE device_address = :address AND is_active = 1 LIMIT 1")
    suspend fun getActiveSessionByAddress(address: String): ConversationSession?
}
