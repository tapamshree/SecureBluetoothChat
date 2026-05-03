package com.yourapp.securechat.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yourapp.securechat.data.model.ChatMessage
import com.yourapp.securechat.databinding.ItemMessageBinding
import com.yourapp.securechat.utils.Extensions.toFormattedTime

/**
 * ============================================================================
 * FILE: ChatAdapter.kt
 * ============================================================================
 *
 * 1. PURPOSE OF THE FILE:
 * To render individual chat message bubbles inside the `RecyclerView` on 
 * the `ChatActivity` screen.
 *
 * 2. HOW IT WORKS:
 * It extends `ListAdapter` which uses `DiffUtil` to efficiently diff old and 
 * new message lists, only re-rendering rows that actually changed. Each row 
 * inflates `item_message.xml` and toggles between outgoing (right-aligned) 
 * and incoming (left-aligned) containers based on `ChatMessage.isOutgoing`.
 *
 * 3. WHY IS IT IMPORTANT:
 * Without a properly diffed adapter, every new message would force the entire 
 * list to re-render, causing visible stuttering and battery drain.
 *
 * 4. ROLE IN THE PROJECT:
 * Pure UI component connecting `ChatViewModel`'s message Flow to the 
 * `RecyclerView` on screen.
 *
 * 5. WHAT DOES EACH PART DO:
 * - [MessageViewHolder.bind()]: Toggles visibility of incoming/outgoing containers.
 * - [DiffCallback]: Compares messages by ID for item identity, full equality for content.
 * ============================================================================
 */
class ChatAdapter : ListAdapter<ChatMessage, ChatAdapter.MessageViewHolder>(DiffCallback) {

    inner class MessageViewHolder(
        private val binding: ItemMessageBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMessage) {
            if (message.isOutgoing) {
                binding.outgoingContainer.visibility = View.VISIBLE
                binding.incomingContainer.visibility = View.GONE
                binding.outgoingText.text = message.content
                binding.outgoingTime.text = message.timestamp.toFormattedTime()
            } else {
                binding.outgoingContainer.visibility = View.GONE
                binding.incomingContainer.visibility = View.VISIBLE
                binding.incomingText.text = message.content
                binding.incomingTime.text = message.timestamp.toFormattedTime()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // ── DiffUtil ──────────────────────────────────────────────────────────────

    companion object DiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
            oldItem == newItem
    }
}