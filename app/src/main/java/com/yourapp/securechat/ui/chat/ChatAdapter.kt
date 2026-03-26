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
 * RecyclerView adapter for [ChatMessage] rows.
 * Uses a single row layout (`item_message.xml`) and toggles outgoing/incoming containers.
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