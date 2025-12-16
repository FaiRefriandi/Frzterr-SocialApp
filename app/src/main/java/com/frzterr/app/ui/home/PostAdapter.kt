package com.frzterr.app.ui.home

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.frzterr.app.R
import com.frzterr.app.data.model.PostWithUser
import com.google.android.material.imageview.ShapeableImageView
import java.text.SimpleDateFormat
import java.util.Locale

class PostAdapter(
    private val onLikeClick: (PostWithUser) -> Unit,
    private val onCommentClick: (PostWithUser) -> Unit,
    private val onRepostClick: (PostWithUser) -> Unit,
    private val onUserClick: (PostWithUser) -> Unit
) : ListAdapter<PostWithUser, PostAdapter.PostViewHolder>(PostDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_post, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgAvatar: ShapeableImageView = itemView.findViewById(R.id.imgAvatar)
        private val tvUsername: TextView = itemView.findViewById(R.id.tvUsername)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val tvContent: TextView = itemView.findViewById(R.id.tvContent)
        private val imgPost: ShapeableImageView = itemView.findViewById(R.id.imgPost)
        private val btnLike: ImageView = itemView.findViewById(R.id.btnLike)
        private val tvLikeCount: TextView = itemView.findViewById(R.id.tvLikeCount)
        private val tvCommentCount: TextView = itemView.findViewById(R.id.tvCommentCount)
        private val btnRepost: ImageView = itemView.findViewById(R.id.btnRepost)
        private val tvRepostCount: TextView = itemView.findViewById(R.id.tvRepostCount)

        fun bind(postWithUser: PostWithUser) {
            val post = postWithUser.post
            val user = postWithUser.user

            // User info
            tvUsername.text = "@${user.username}"
            
            // Avatar
            imgAvatar.load(user.avatarUrl) {
                crossfade(false)
                size(120)
                placeholder(R.drawable.ic_user_placeholder)
                error(R.drawable.ic_user_placeholder)
            }

            // Timestamp
            tvTimestamp.text = formatTimestamp(post.createdAt)

            // Content
            tvContent.text = post.content

            // Post image
            if (post.imageUrl != null) {
                imgPost.visibility = View.VISIBLE
                imgPost.load(post.imageUrl) {
                    crossfade(true)
                    placeholder(android.R.color.darker_gray)
                }
            } else {
                imgPost.visibility = View.GONE
            }

            // Like button state
            if (postWithUser.isLiked) {
                btnLike.setImageResource(R.drawable.ic_like_filled)
                btnLike.setColorFilter(
                    itemView.context.getColor(android.R.color.holo_red_dark)
                )
            } else {
                btnLike.setImageResource(R.drawable.ic_like)
                btnLike.setColorFilter(0x808080.toInt() or 0xFF000000.toInt()) // #808080
            }

            // Repost button state
            if (postWithUser.isReposted) {
                btnRepost.setColorFilter(
                    itemView.context.getColor(android.R.color.holo_green_dark)
                )
            } else {
                btnRepost.setColorFilter(0x808080.toInt() or 0xFF000000.toInt()) // #808080
            }

            // Counts
            tvLikeCount.text = formatCount(post.likeCount)
            tvCommentCount.text = formatCount(post.commentCount)
            tvRepostCount.text = formatCount(post.repostCount)

            // Click listeners
            btnLike.setOnClickListener {
                // Animate heart
                btnLike.animate()
                    .scaleX(0.7f)
                    .scaleY(0.7f)
                    .setDuration(100)
                    .withEndAction {
                        btnLike.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(100)
                            .start()
                    }
                    .start()
                
                onLikeClick(postWithUser)
            }

            btnRepost.setOnClickListener {
                // Animate repost button
                btnRepost.animate()
                    .scaleX(0.7f)
                    .scaleY(0.7f)
                    .setDuration(100)
                    .withEndAction {
                        btnRepost.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(100)
                            .start()
                    }
                    .start()
                
                onRepostClick(postWithUser)
            }

            itemView.findViewById<View>(R.id.btnComment).setOnClickListener {
                onCommentClick(postWithUser)
            }
            imgAvatar.setOnClickListener { onUserClick(postWithUser) }
            tvUsername.setOnClickListener { onUserClick(postWithUser) }
        }

        private fun formatTimestamp(timestamp: String): String {
            return try {
                // Supabase returns timestamp with timezone, e.g. "2025-12-16T06:13:45+00:00"
                // We need to parse it as UTC and convert to local time
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                
                // Extract timestamp without timezone part
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

        private fun formatCount(count: Int): String {
            return when {
                count >= 1000000 -> "${count / 1000000}M"
                count >= 1000 -> "${count / 1000}K"
                else -> count.toString()
            }
        }
    }

    class PostDiffCallback : DiffUtil.ItemCallback<PostWithUser>() {
        override fun areItemsTheSame(oldItem: PostWithUser, newItem: PostWithUser): Boolean {
            return oldItem.post.id == newItem.post.id
        }

        override fun areContentsTheSame(oldItem: PostWithUser, newItem: PostWithUser): Boolean {
            return oldItem == newItem
        }
    }
}
