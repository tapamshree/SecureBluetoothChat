package com.yourapp.securechat.data.local

import androidx.lifecycle.LiveData
import androidx.room.*
import com.yourapp.securechat.data.model.ConversationSession

/**
 * SessionDao — Room Data Access Object for conversation sessions.
 *
 * Tracks when connections with remote devices start and end,
 * and provides queries for the conversation list UI.
 */
@Dao
interface SessionDao {

    // -------------------------------------------------------------------------
    // Insert
    // -------------------------------------------------------------------------

    /** Creates a new session. Returns the auto-generated ID. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: ConversationSession): Long

    // -------------------------------------------------------------------------
    // Update
    // -------------------------------------------------------------------------

    /** Updates an existing session (e.g., to mark it as ended). */
    @Update
    suspend fun update(session: ConversationSession)

    /** Marks a session as ended (inactive). */
    @Query("UPDATE sessions SET is_active = 0, end_time = :endTime WHERE id = :sessionId")
    suspend fun endSession(sessionId: Long, endTime: Long = System.currentTimeMillis())

    /** Increments the message count for a session. */
    @Query("UPDATE sessions SET message_count = message_count + 1 WHERE id = :sessionId")
    suspend fun incrementMessageCount(sessionId: Long)

    // -------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------

    /** Deletes a session and all its associated data. */
    @Delete
    suspend fun delete(session: ConversationSession)

    /** Deletes all sessions. */
    @Query("DELETE FROM sessions")
    suspend fun deleteAll()

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    /** Returns all sessions, most recent first. */
    @Query("SELECT * FROM sessions ORDER BY start_time DESC")
    fun getAllSessions(): LiveData<List<ConversationSession>>

    /** Returns the currently active session (if any). */
    @Query("SELECT * FROM sessions WHERE is_active = 1 LIMIT 1")
    suspend fun getActiveSession(): ConversationSession?

    /** Returns all sessions for a specific device. */
    @Query("SELECT * FROM sessions WHERE device_address = :address ORDER BY start_time DESC")
    fun getSessionsByDevice(address: String): LiveData<List<ConversationSession>>

    /** Returns a session by its ID. */
    @Query("SELECT * FROM sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: Long): ConversationSession?
}
