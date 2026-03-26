package com.yourapp.securechat.data.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.asFlow
import com.yourapp.securechat.data.local.MessageDao
import com.yourapp.securechat.data.local.SessionDao
import com.yourapp.securechat.data.model.ChatMessage
import com.yourapp.securechat.data.model.ConversationSession
import com.yourapp.securechat.utils.Logger
import kotlinx.coroutines.flow.Flow

/**
 * ChatRepository — Data access gateway for chat and session features.
 *
 * ============================================================
 * ARCHITECTURAL ROLE
 * ============================================================
 * ViewModels should interact with this repository rather than talking
 * to DAOs directly. This keeps data-source details in one place and
 * allows us to evolve storage strategy later (cache, remote sync, etc.)
 * without rewriting UI layer code.
 *
 * ============================================================
 * COMPATIBILITY NOTES
 * ============================================================
 * The current project has mixed call sites:
 *   - Some code expects only MessageDao in constructor.
 *   - Some code expects both MessageDao + SessionDao.
 *   - Some code expects Flow from getMessages.
 *   - Some code expects deleteConversation(), others clearConversation().
 *
 * To reduce integration breakage while cleanup is ongoing, this class
 * intentionally supports both styles via overloads/aliases.
 */
class ChatRepository(
    private val messageDao: MessageDao,
    private val sessionDao: SessionDao? = null
) {

    companion object {
        private const val TAG = "ChatRepo"
    }

    /**
     * Secondary constructor for legacy call sites that only provide MessageDao.
     * Session features become unavailable and guarded at runtime.
     */
    constructor(messageDao: MessageDao) : this(messageDao, null)

    // -------------------------------------------------------------------------
    // Messages
    // -------------------------------------------------------------------------

    /**
     * Flow variant used by coroutine-first ViewModels.
     * Internally bridges Room LiveData to Flow via lifecycle-livedata-ktx.
     */
    fun getMessages(sessionId: String): Flow<List<ChatMessage>> {
        return messageDao.getMessagesBySession(sessionId).asFlow()
    }

    /**
     * LiveData variant kept for XML/legacy observers.
     */
    fun getMessagesLiveData(sessionId: String): LiveData<List<ChatMessage>> {
        return messageDao.getMessagesBySession(sessionId)
    }

    /** Saves a message to the database. */
    suspend fun saveMessage(message: ChatMessage) {
        messageDao.insert(message)
        Logger.d(TAG, "Saved message ${message.id} (${message.status})")
    }

    /** Updates the delivery status of a message (e.g., SENDING → SENT). */
    suspend fun updateMessageStatus(messageId: String, status: String) {
        messageDao.updateStatus(messageId, status)
        Logger.d(TAG, "Updated message $messageId status to $status")
    }

    /** Returns all messages stuck in SENDING state (for retry on reconnect). */
    suspend fun getUnsentMessages(): List<ChatMessage> {
        return messageDao.getUnsentMessages()
    }

    /** Deletes all messages for a session. */
    suspend fun clearConversation(sessionId: String) {
        messageDao.deleteBySession(sessionId)
        Logger.i(TAG, "Cleared all messages for session $sessionId")
    }

    /**
     * Clears all locally stored chat history across sessions.
     * Used by settings-level destructive action.
     */
    suspend fun clearAllMessages() {
        messageDao.deleteAll()
        sessionDao?.deleteAll()
        Logger.i(TAG, "Cleared all messages and sessions")
    }

    /**
     * Alias kept for compatibility with ViewModels still using old method name.
     */
    suspend fun deleteConversation(sessionId: String) {
        clearConversation(sessionId)
    }

    /** Returns the latest message per session (for conversation list). */
    fun getLatestMessages(): LiveData<List<ChatMessage>> {
        return messageDao.getLatestMessagePerSession()
    }

    // -------------------------------------------------------------------------
    // Sessions
    // -------------------------------------------------------------------------

    /** Creates a new session and returns its auto-generated ID. */
    suspend fun createSession(deviceAddress: String, deviceName: String): Long {
        val dao = requireSessionDao()
        val session = ConversationSession(
            deviceAddress = deviceAddress,
            deviceName = deviceName
        )
        val id = dao.insert(session)
        Logger.i(TAG, "Created session $id for $deviceName ($deviceAddress)")
        return id
    }

    /** Marks a session as ended. */
    suspend fun endSession(sessionId: Long) {
        requireSessionDao().endSession(sessionId)
        Logger.i(TAG, "Ended session $sessionId")
    }

    /** Returns the currently active session (if any). */
    suspend fun getActiveSession(): ConversationSession? {
        return requireSessionDao().getActiveSession()
    }

    /** Returns all sessions as observable LiveData. */
    fun getAllSessions(): LiveData<List<ConversationSession>> {
        return requireSessionDao().getAllSessions()
    }

    /** Increments the message count for a session. */
    suspend fun incrementSessionMessageCount(sessionId: Long) {
        requireSessionDao().incrementMessageCount(sessionId)
    }

    // -------------------------------------------------------------------------
    // Internal guards
    // -------------------------------------------------------------------------

    /**
     * Ensures session operations fail fast with a clear message when repository
     * was created in message-only mode.
     */
    private fun requireSessionDao(): SessionDao {
        return requireNotNull(sessionDao) {
            "SessionDao is required for session operations. " +
            "Use ChatRepository(messageDao, sessionDao)."
        }
    }
}
