package com.frzterr.app.ui.comments

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.frzterr.app.R
import com.frzterr.app.data.model.CommentWithUser
import com.google.android.material.imageview.ShapeableImageView
import java.text.SimpleDateFormat
import java.util.Locale

class CommentAdapter : ListAdapter<CommentWithUser, CommentAdapter.CommentViewHolder>(CommentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgAvatar: ShapeableImageView = itemView.findViewById(R.id.imgAvatar)
        private val tvUsername: TextView = itemView.findViewById(R.id.tvUsername)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val tvContent: TextView = itemView.findViewById(R.id.tvContent)

        fun bind(commentWithUser: CommentWithUser) {
            val comment = commentWithUser.comment
            val user = commentWithUser.user

            tvUsername.text = "@${user.username}"
            tvContent.text = comment.content
            tvTimestamp.text = formatTimestamp(comment.createdAt)

            imgAvatar.load(user.avatarUrl) {
                crossfade(false)
                size(96)
                placeholder(R.drawable.ic_user_placeholder)
                error(R.drawable.ic_user_placeholder)
            }
        }

        private fun formatTimestamp(timestamp: String): String {
            return try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")

                val cleanTimestamp = timestamp.substringBefore("+").substringBefore("Z").substringBefore(".")
                val date = sdf.parse(cleanTimestamp)

                if (date != null) {
                    val now = System.currentTimeMillis()
                    DateUtils.getRelativeTimeSpanString(
                        date.time,
                        now,
                        DateUtils.MINUTE_IN_MILLIS
                    ).toString()
                } else {
                    "Just now"
                }
            } catch (e: Exception) {
                "Just now"
            }
        }
    }

    class CommentDiffCallback : DiffUtil.ItemCallback<CommentWithUser>() {
        override fun areItemsTheSame(oldItem: CommentWithUser, newItem: CommentWithUser): Boolean {
            return oldItem.comment.id == newItem.comment.id
        }

        override fun areContentsTheSame(oldItem: CommentWithUser, newItem: CommentWithUser): Boolean {
            return oldItem == newItem
        }
    }
}
