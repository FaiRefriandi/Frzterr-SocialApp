package com.frzterr.app.ui.message

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.frzterr.app.R
import com.frzterr.app.data.model.Message
import com.google.android.material.imageview.ShapeableImageView
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ChatMessageAdapter(
    private val currentUserId: String,
    private val otherAvatarUrl: String?
) : ListAdapter<Message, RecyclerView.ViewHolder>(DiffCallback) {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2

        object DiffCallback : DiffUtil.ItemCallback<Message>() {
            override fun areItemsTheSame(a: Message, b: Message) = a.id == b.id
            override fun areContentsTheSame(a: Message, b: Message) = a == b
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).senderId == currentUserId) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

    // =====================
    // SENT VIEW HOLDER
    // =====================
    inner class SentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvContent: TextView = view.findViewById(R.id.tvMessageContent)
        val tvTime: TextView = view.findViewById(R.id.tvMessageTime)
    }

    // =====================
    // RECEIVED VIEW HOLDER
    // =====================
    inner class ReceivedViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvContent: TextView = view.findViewById(R.id.tvMessageContent)
        val tvTime: TextView = view.findViewById(R.id.tvMessageTime)
        val ivAvatar: ShapeableImageView = view.findViewById(R.id.ivSenderAvatar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_SENT) {
            SentViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_sent, parent, false)
            )
        } else {
            ReceivedViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_received, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = getItem(position)
        val timeStr = formatTime(msg.createdAt)

        if (holder is SentViewHolder) {
            holder.tvContent.text = msg.content
            holder.tvTime.text = timeStr
        } else if (holder is ReceivedViewHolder) {
            holder.tvContent.text = msg.content
            holder.tvTime.text = timeStr
            Glide.with(holder.ivAvatar)
                .load(otherAvatarUrl)
                .placeholder(R.drawable.ic_user_placeholder)
                .circleCrop()
                .into(holder.ivAvatar)
        }
    }

    private fun formatTime(isoDate: String): String {
        return try {
            val instant = Instant.parse(isoDate)
            val dt = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
            dt.format(DateTimeFormatter.ofPattern("hh:mm a"))
        } catch (e: Exception) {
            ""
        }
    }
}
