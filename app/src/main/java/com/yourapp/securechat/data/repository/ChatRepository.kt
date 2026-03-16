package com.yourapp.securechat.data.repository

import androidx.lifecycle.LiveData
import com.yourapp.securechat.data.local.MessageDao
import com.yourapp.securechat.data.local.SessionDao
import com.yourapp.securechat.data.model.ChatMessage
import com.yourapp.securechat.data.model.ConversationSession
import com.yourapp.securechat.utils.Logger

/**
 * ChatRepository — Abstracts data access for the chat UI layer.
 *
 * ViewModels interact with this repository instead of directly using DAOs.
 * This allows us to add caching, combine data sources, or swap
 * implementations without touching the ViewModel.
 *
 * Follows the Repository Pattern from Android Architecture Components.
 */
class ChatRepository(
    private val messageDao: MessageDao,
    private val sessionDao: SessionDao
) {

    companion object {
        private const val TAG = "ChatRepo"
    }

    // -------------------------------------------------------------------------
    // Messages
    // -------------------------------------------------------------------------

    /** Returns all messages for a session as observable LiveData. */
    fun getMessages(sessionId: String): LiveData<List<ChatMessage>> {
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

    /** Returns the latest message per session (for conversation list). */
    fun getLatestMessages(): LiveData<List<ChatMessage>> {
        return messageDao.getLatestMessagePerSession()
    }

    // -------------------------------------------------------------------------
    // Sessions
    // -------------------------------------------------------------------------

    /** Creates a new session and returns its auto-generated ID. */
    suspend fun createSession(deviceAddress: String, deviceName: String): Long {
        val session = ConversationSession(
            deviceAddress = deviceAddress,
            deviceName = deviceName
        )
        val id = sessionDao.insert(session)
        Logger.i(TAG, "Created session $id for $deviceName ($deviceAddress)")
        return id
    }

    /** Marks a session as ended. */
    suspend fun endSession(sessionId: Long) {
        sessionDao.endSession(sessionId)
        Logger.i(TAG, "Ended session $sessionId")
    }

    /** Returns the currently active session (if any). */
    suspend fun getActiveSession(): ConversationSession? {
        return sessionDao.getActiveSession()
    }

    /** Returns all sessions as observable LiveData. */
    fun getAllSessions(): LiveData<List<ConversationSession>> {
        return sessionDao.getAllSessions()
    }

    /** Increments the message count for a session. */
    suspend fun incrementSessionMessageCount(sessionId: Long) {
        sessionDao.incrementMessageCount(sessionId)
    }
}
