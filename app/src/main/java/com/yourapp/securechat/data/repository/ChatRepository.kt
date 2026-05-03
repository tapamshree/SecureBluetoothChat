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
 * ============================================================================
 * FILE: ChatRepository.kt
 * ============================================================================
 * 
 * 1. PURPOSE OF THE FILE:
 * To provide a clean, unified API for the ViewModels to access chat and session data 
 * without needing to know whether the data comes from a local SQLite database, 
 * in-memory cache, or a cloud server.
 * 
 * 2. HOW IT WORKS:
 * It implements the Repository Pattern. It takes the lower-level Room DAOs 
 * (`MessageDao` and `SessionDao`) as constructor dependencies. When a `ChatViewModel` 
 * needs messages, it calls `chatRepository.getMessages()`, which conceptually abstracts
 * away the raw SQL logic inside `Daos.kt`. 
 * 
 * 3. WHY IS IT IMPORTANT:
 * If the UI components (ViewModels/Activities) talked directly to DAOs, the codebase 
 * would be tightly coupled to Room/SQLite. By introducing this repository, we can 
 * easily add logic (like merging a local SQLite cache with a remote REST API) in the 
 * future without breaking a single line of UI code.
 * 
 * 4. ROLE IN THE PROJECT:
 * It forms the critical middle layer of the MVVM architecture (Model-View-ViewModel). 
 * It sits above the Data layer (`AppDatabase`, `Daos`) and below the Presentation 
 * layer (`ChatViewModel`).
 * 
 * 5. WHAT DOES EACH PART DO:
 * - [Internal variables]: Receives DAOs via Dependency Injection (constructor).
 * - [getMessages() / getMessagesLiveData()]: Fetches continuous streams of data from 
 *   Room, bridged into Kotlin Flow or LiveData streams for the UI.
 * - [saveMessage() / updateMessageStatus()]: Forwarding operations to `MessageDao`.
 * - [createSession() / endSession()]: Forwarding operations to `SessionDao`.
 * - [requireSessionDao()]: Internal safeguard allowing legacy instantiation while 
 *   protecting against NullPointerExceptions.
 * ============================================================================
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
