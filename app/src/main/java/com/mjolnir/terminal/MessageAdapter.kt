package com.mjolnir.terminal

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

private val DIFF = object : DiffUtil.ItemCallback<ChatMessage>() {
    override fun areItemsTheSame(a: ChatMessage, b: ChatMessage) = a.id == b.id
    override fun areContentsTheSame(a: ChatMessage, b: ChatMessage) = a == b
}

class MessageAdapter : ListAdapter<ChatMessage, MessageAdapter.MessageViewHolder>(DIFF) {

    inner class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.message_text)
        val role: TextView = view.findViewById(R.id.message_role)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder =
        MessageViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
        )

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val msg = getItem(position)
        holder.text.text = if (msg.isStreaming && msg.content.isEmpty()) "▊" else msg.content
        holder.role.text = if (msg.role == "user") "YOU" else "MJOLNIR"
        holder.itemView.alpha = if (msg.isStreaming) 0.75f else 1.0f

        val isUser = msg.role == "user"
        holder.role.setTextColor(
            holder.itemView.context.getColor(if (isUser) R.color.text_dim else R.color.accent)
        )
    }
}
