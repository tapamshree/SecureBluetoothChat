package com.yourapp.securechat.data.local

import androidx.lifecycle.LiveData
import androidx.room.*
import com.yourapp.securechat.data.model.ChatMessage

/**
 * MessageDao — Room Data Access Object for chat messages.
 *
 * Provides CRUD operations and queries for the `messages` table.
 * All queries return LiveData for automatic UI updates via ViewModel observation.
 */
@Dao
interface MessageDao {

    // -------------------------------------------------------------------------
    // Insert
    // -------------------------------------------------------------------------

    /** Inserts a single message. Replaces on conflict (same ID = update). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessage)

    /** Inserts multiple messages in a single transaction. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<ChatMessage>)

    // -------------------------------------------------------------------------
    // Update
    // -------------------------------------------------------------------------

    /** Updates an existing message (e.g., to change delivery status). */
    @Update
    suspend fun update(message: ChatMessage)

    /**
     * Updates the delivery status of a message by its ID.
     * Used when receiving ACKs from the remote device.
     */
    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateStatus(messageId: String, status: String)

    // -------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------

    /** Deletes a single message. */
    @Delete
    suspend fun delete(message: ChatMessage)

    /** Deletes all messages for a given session (conversation). */
    @Query("DELETE FROM messages WHERE session_id = :sessionId")
    suspend fun deleteBySession(sessionId: String)

    /** Deletes all messages from the database. */
    @Query("DELETE FROM messages")
    suspend fun deleteAll()

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    /**
     * Returns all messages for a session, ordered by timestamp (oldest first).
     * Observed by ChatViewModel to update the chat UI in real-time.
     */
    @Query("SELECT * FROM messages WHERE session_id = :sessionId ORDER BY timestamp ASC")
    fun getMessagesBySession(sessionId: String): LiveData<List<ChatMessage>>

    /**
     * Returns the most recent message for each session (for conversation list).
     */
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

    /**
     * Returns the count of messages in a session.
     */
    @Query("SELECT COUNT(*) FROM messages WHERE session_id = :sessionId")
    suspend fun getMessageCount(sessionId: String): Int

    /**
     * Returns a single message by ID (non-observable, one-shot).
     */
    @Query("SELECT * FROM messages WHERE id = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): ChatMessage?

    /**
     * Returns all unsent (SENDING status) messages — for retry on reconnect.
     */
    @Query("SELECT * FROM messages WHERE status = 'SENDING' ORDER BY timestamp ASC")
    suspend fun getUnsentMessages(): List<ChatMessage>
}
