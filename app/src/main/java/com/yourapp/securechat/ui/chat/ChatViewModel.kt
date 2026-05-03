package com.yourapp.securechat.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yourapp.securechat.data.model.ChatMessage
import com.yourapp.securechat.data.repository.ChatRepository
import com.yourapp.securechat.utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ============================================================================
 * FILE: ChatViewModel.kt
 * ============================================================================
 *
 * 1. PURPOSE OF THE FILE:
 * To hold and manage UI-related data for the chat screen in a lifecycle-aware 
 * manner, surviving configuration changes like screen rotations.
 *
 * 2. HOW IT WORKS:
 * It exposes a `StateFlow<List<ChatMessage>>` that reactively emits the latest 
 * messages for the active device. Internally it uses `flatMapLatest` to switch 
 * the upstream Room Flow whenever the connected device address changes.
 *
 * 3. WHY IS IT IMPORTANT:
 * Activities are destroyed and recreated on every screen rotation. Without a 
 * ViewModel, the message list and display name would be lost each time.
 *
 * 4. ROLE IN THE PROJECT:
 * MVVM ViewModel sitting between `ChatActivity` (View) and `ChatRepository` 
 * (Model). It never references Android UI classes directly.
 *
 * 5. WHAT DOES EACH PART DO:
 * - [messages]: StateFlow of chat messages, auto-switches when device changes.
 * - [setConversationDevice()]: Updates the active device address to load messages for.
 * - [clearConversation()]: Deletes all messages for the current device.
 * - [Factory]: Custom ViewModelProvider.Factory for constructor injection.
 * ============================================================================
 */
class ChatViewModel(
    private val chatRepository: ChatRepository,
    initialDeviceAddress: String
) : ViewModel() {

    var myDisplayName: String = "Me"

    private val activeDeviceAddress = MutableStateFlow(initialDeviceAddress)

    val messages: StateFlow<List<ChatMessage>> = activeDeviceAddress
        .flatMapLatest { deviceAddress ->
            if (deviceAddress.isBlank()) flowOf(emptyList())
            else chatRepository.getMessages(deviceAddress)
        }
        .catch { e ->
            Logger.e(TAG, "Error loading messages for ${activeDeviceAddress.value}", e)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun setConversationDevice(deviceAddress: String) {
        if (deviceAddress.isNotBlank() && deviceAddress != activeDeviceAddress.value) {
            activeDeviceAddress.value = deviceAddress
        }
    }

    fun clearConversation() {
        viewModelScope.launch {
            val deviceAddress = activeDeviceAddress.value
            if (deviceAddress.isBlank()) return@launch
            runCatching { chatRepository.clearConversation(deviceAddress) }
                .onSuccess { Logger.d(TAG, "Conversation cleared for $deviceAddress") }
                .onFailure { e -> Logger.e(TAG, "Failed to clear conversation", e) }
        }
    }

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
