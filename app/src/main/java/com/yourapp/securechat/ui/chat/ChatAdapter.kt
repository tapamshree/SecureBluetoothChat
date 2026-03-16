package com.yourapp.securechat.ui.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yourapp.securechat.data.ChatMessage
import com.yourapp.securechat.databinding.ItemMessageReceivedBinding
import com.yourapp.securechat.databinding.ItemMessageSentBinding
import com.yourapp.securechat.utils.Extensions.toFormattedTime

/**
 * RecyclerView adapter for [ChatMessage] items.
 *
 * Two view types:
 *  - [VIEW_TYPE_SENT]     — messages sent by the local user (aligned right)
 *  - [VIEW_TYPE_RECEIVED] — messages received from the remote device (aligned left)
 */
class ChatAdapter : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(DiffCallback) {

    // ── View types ────────────────────────────────────────────────────────────

    override fun getItemViewType(position: Int): Int =
        if (getItem(position).isMine) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED

    // ── ViewHolders ───────────────────────────────────────────────────────────

    inner class SentViewHolder(
        private val binding: ItemMessageSentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: ChatMessage) {
            binding.tvMessage.text  = message.content
            binding.tvTime.text     = message.timestamp.toFormattedTime()
            binding.tvStatus.text   = when (message.status) {
                ChatMessage.Status.SENDING   -> "⏳"
                ChatMessage.Status.SENT      -> "✓"
                ChatMessage.Status.DELIVERED -> "✓✓"
                ChatMessage.Status.FAILED    -> "✗"
                else                         -> ""
            }
        }
    }

    inner class ReceivedViewHolder(
        private val binding: ItemMessageReceivedBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: ChatMessage) {
            binding.tvMessage.text     = message.content
            binding.tvTime.text        = message.timestamp.toFormattedTime()
            binding.tvSenderName.text  = message.senderName
        }
    }

    // ── Inflate ───────────────────────────────────────────────────────────────

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SENT -> SentViewHolder(
                ItemMessageSentBinding.inflate(inflater, parent, false)
            )
            else -> ReceivedViewHolder(
                ItemMessageReceivedBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder) {
            is SentViewHolder     -> holder.bind(message)
            is ReceivedViewHolder -> holder.bind(message)
        }
    }

    // ── DiffUtil ──────────────────────────────────────────────────────────────

    companion object DiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
            oldItem == newItem

        private const val VIEW_TYPE_SENT     = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }
}