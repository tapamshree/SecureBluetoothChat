package com.yourapp.securechat.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yourapp.securechat.data.ChatMessage
import com.yourapp.securechat.data.repository.ChatRepository
import com.yourapp.securechat.utils.Logger
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for [ChatActivity].
 *
 * Responsibilities:
 *  - Expose the live message list for the current conversation as a [StateFlow]
 *  - Provide [myDisplayName] for outgoing message attribution
 *  - Offer [deleteConversation] for clearing chat history
 */
class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val deviceAddress: String
) : ViewModel() {

    // ── Display name (could later come from SharedPreferences / SettingsViewModel) ──

    var myDisplayName: String = "Me"

    // ── Messages ──────────────────────────────────────────────────────────────

    /**
     * Live stream of all [ChatMessage]s for this conversation, ordered by
     * timestamp ascending. Backed by Room's Flow — updates automatically
     * whenever the DB changes (incoming message saved by [MessageReceiver],
     * or outgoing message saved by [BluetoothChatService.sendMessage]).
     */
    val messages: StateFlow<List<ChatMessage>> = chatRepository
        .getMessages(deviceAddress)
        .catch { e ->
            Logger.e(TAG, "Error loading messages for $deviceAddress", e)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    // ── Actions ───────────────────────────────────────────────────────────────

    /**
     * Deletes all messages for this conversation from the local database.
     */
    fun deleteConversation() {
        viewModelScope.launch {
            runCatching { chatRepository.deleteConversation(deviceAddress) }
                .onSuccess { Logger.d(TAG, "Conversation deleted for $deviceAddress") }
                .onFailure { e -> Logger.e(TAG, "Failed to delete conversation", e) }
        }
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    override fun onCleared() {
        Logger.d(TAG, "ChatViewModel cleared")
        super.onCleared()
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    class Factory(
        private val chatRepository: ChatRepository,
        private val deviceAddress: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ChatViewModel::class.java))
            return ChatViewModel(chatRepository, deviceAddress) as T
        }
    }

    companion object {
        private const val TAG = "ChatViewModel"
    }
}