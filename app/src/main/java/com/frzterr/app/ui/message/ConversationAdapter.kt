package com.frzterr.app.ui.message

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.frzterr.app.R
import com.frzterr.app.data.model.ConversationWithUser
import com.google.android.material.imageview.ShapeableImageView
import java.time.OffsetDateTime
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ConversationAdapter(
    private val onItemClick: (ConversationWithUser) -> Unit,
    private val onItemLongClick: (ConversationWithUser) -> Unit
) : ListAdapter<ConversationWithUser, ConversationAdapter.ViewHolder>(DiffCallback) {

    companion object DiffCallback : DiffUtil.ItemCallback<ConversationWithUser>() {
        override fun areItemsTheSame(a: ConversationWithUser, b: ConversationWithUser) =
            a.conversation.id == b.conversation.id

        override fun areContentsTheSame(a: ConversationWithUser, b: ConversationWithUser) =
            a == b
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivAvatar: ShapeableImageView = view.findViewById(R.id.ivAvatar)
        val tvUsername: TextView = view.findViewById(R.id.tvUsername)
        val tvLastMessage: TextView = view.findViewById(R.id.tvLastMessage)
        val tvTimestamp: TextView = view.findViewById(R.id.tvTimestamp)
        val tvBadge: TextView = view.findViewById(R.id.ivUnreadBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val user = item.otherUser

        // Username (prefer full_name, fallback to @username)
        holder.tvUsername.text = user.fullName?.takeIf { it.isNotBlank() } ?: "@${user.username}"

        // Last message preview
        holder.tvLastMessage.text = item.lastMessage?.content ?: "No messages yet"

        // Timestamp
        holder.tvTimestamp.text = item.lastMessage?.createdAt?.let { formatTimestamp(it) } ?: ""

        // Unread badge
        if (item.unreadCount > 0) {
            holder.tvBadge.visibility = View.VISIBLE
            holder.tvBadge.text = if (item.unreadCount > 99) "99+" else item.unreadCount.toString()
        } else {
            holder.tvBadge.visibility = View.GONE
        }

        // Avatar
        Glide.with(holder.ivAvatar)
            .load(user.avatarUrl)
            .placeholder(R.drawable.ic_user_placeholder)
            .circleCrop()
            .into(holder.ivAvatar)

        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.itemView.setOnLongClickListener {
            onItemLongClick(item)
            true
        }
    }

    private fun formatTimestamp(isoDate: String): String {
        return try {
            val odt = OffsetDateTime.parse(isoDate)
            val dt = odt.toLocalDateTime()
                .atZone(odt.offset.rules.getOffset(odt.toInstant()))
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime()
            val today = LocalDateTime.now().toLocalDate()
            val msgDate = dt.toLocalDate()

            when {
                msgDate == today                   -> dt.format(DateTimeFormatter.ofPattern("HH:mm"))
                msgDate == today.minusDays(1)      -> "Kemarin"
                else                               -> dt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            }
        } catch (e: Exception) {
            ""
        }
    }
}
