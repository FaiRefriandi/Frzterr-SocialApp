package com.frzterr.app.ui.comments

import android.text.format.DateUtils
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
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

class CommentAdapter(
    private val currentUserId: String?,
    private val postOwnerId: String,
    private val onLikeClick: (CommentWithUser) -> Unit,
    private val onReplyClick: (CommentWithUser) -> Unit,
    private val onDeleteClick: (CommentWithUser) -> Unit,
    private val onReplyToggle: (String) -> Unit,
    private val onUserClick: (CommentWithUser) -> Unit
) : ListAdapter<CommentWithUser, CommentAdapter.CommentViewHolder>(CommentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgAvatar: ShapeableImageView = itemView.findViewById(R.id.imgAvatar)
        private val tvUsername: TextView = itemView.findViewById(R.id.tvUsername)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val tvContent: TextView = itemView.findViewById(R.id.tvContent)
        
        // Actions
        // Actions
        // In new layout, btnLike is a LinearLayout containing imgLike and tvLikeCount
        private val btnLike: LinearLayout = itemView.findViewById(R.id.btnLike)
        private val imgLike: ImageView = itemView.findViewById(R.id.imgLike)
        private val tvLikeCount: TextView = itemView.findViewById(R.id.tvLikeCount)
        private val btnReply: TextView = itemView.findViewById(R.id.btnReply)
        private val tvReplyToggle: TextView = itemView.findViewById(R.id.tvReplyToggle)

        fun bind(commentWithUser: CommentWithUser) {
            val comment = commentWithUser.comment
            val user = commentWithUser.user

            tvUsername.text = user.username // Remove @ prefix
            tvContent.text = comment.content
            tvTimestamp.text = formatTimestamp(comment.createdAt)

            val shimmerAvatar: com.facebook.shimmer.ShimmerFrameLayout = itemView.findViewById(R.id.shimmerAvatar)

            // Reset Shimmer
            shimmerAvatar.visibility = View.VISIBLE
            shimmerAvatar.startShimmer()

            imgAvatar.load(user.avatarUrl) {
                crossfade(false)
                size(96)
                // Remove placeholder, use shimmer
                error(R.drawable.ic_user_placeholder)
                listener(
                    onSuccess = { _, _ ->
                        shimmerAvatar.stopShimmer()
                        shimmerAvatar.visibility = View.GONE
                    },
                    onError = { _, _ ->
                        shimmerAvatar.stopShimmer()
                        shimmerAvatar.visibility = View.GONE
                    }
                )
            }
            
            imgAvatar.setOnClickListener { onUserClick(commentWithUser) }
            tvUsername.setOnClickListener { onUserClick(commentWithUser) }
            
            // Get commentContainer reference (used for indentation and gestures)
            val commentContainer = itemView.findViewById<View>(R.id.commentContainer)
            
            // Apply indentation for replies
            val layoutParams = commentContainer.layoutParams as ViewGroup.MarginLayoutParams
            if (comment.parentCommentId != null) {
                // This is a reply, indent it
                layoutParams.marginStart = itemView.context.resources.getDimensionPixelSize(R.dimen.reply_indent)
                tvReplyToggle.visibility = View.GONE // Replies don't have toggles
            } else {
                // Top-level comment, no indent
                layoutParams.marginStart = 0
                
                // Toggle Logic for Root Comments
                if (commentWithUser.replyCount > 0) {
                    tvReplyToggle.visibility = View.VISIBLE
                    if (commentWithUser.isExpanded) {
                        tvReplyToggle.text = "Sembunyikan balasan"
                    } else {
                        tvReplyToggle.text = "Lihat ${commentWithUser.replyCount} balasan"
                    }
                    
                    tvReplyToggle.setOnClickListener {
                        onReplyToggle(comment.id)
                    }
                } else {
                    tvReplyToggle.visibility = View.GONE
                }
            }
            commentContainer.layoutParams = layoutParams

            // Like State with icon switching
            if (commentWithUser.isLiked) {
                imgLike.setImageResource(R.drawable.ic_like_filled)
                imgLike.setColorFilter(
                    itemView.context.getColor(android.R.color.holo_red_dark)
                )
            } else {
                imgLike.setImageResource(R.drawable.ic_like)
                imgLike.setColorFilter(0x808080.toInt() or 0xFF000000.toInt()) // #808080
            }

            // Like Count - always gray
            if (comment.likeCount > 0) {
                tvLikeCount.text = comment.likeCount.toString()
                tvLikeCount.setTextColor(itemView.context.getColor(R.color.icon_inactive))
                tvLikeCount.visibility = View.VISIBLE
            } else {
                tvLikeCount.visibility = View.GONE
            }

            // Interactions
            btnLike.setOnClickListener {
                animateButton(imgLike)
                onLikeClick(commentWithUser)
            }

            btnReply.setOnClickListener {
                onReplyClick(commentWithUser)
            }

            // Double Tap to Like & Long Press to Delete
            val gestureDetector = GestureDetector(itemView.context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    // ALWAYS animate, even if already liked
                    animateButton(imgLike)
                    
                    // Only trigger like if not already liked
                    if (!commentWithUser.isLiked) {
                        onLikeClick(commentWithUser)
                    }
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    if (currentUserId == comment.userId || currentUserId == postOwnerId) {
                        onDeleteClick(commentWithUser)
                    }
                }
            })

            commentContainer.setOnTouchListener { v, event ->
                gestureDetector.onTouchEvent(event)
                
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> v.isPressed = true
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.isPressed = false
                }
                
                if (event.action == MotionEvent.ACTION_UP) {
                    v.performClick()
                }
                true
            }
        }

        private fun animateButton(view: View) {
            view.animate()
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(100)
                .withEndAction {
                    view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(100)
                        .start()
                }
                .start()
        }

        private fun updateCountWithAnimation(textView: TextView, count: Int) {
            val wasVisible = textView.visibility == View.VISIBLE
            
            if (count == 0) {
                textView.visibility = View.GONE
            } else {
                if (!wasVisible) {
                    textView.visibility = View.VISIBLE
                    textView.alpha = 0f
                    textView.translationY = 10f
                    textView.text = count.toString()
                    textView.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(200)
                        .start()
                } else {
                    textView.text = count.toString()
                }
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
            return oldItem == newItem && oldItem.isExpanded == newItem.isExpanded
        }
    }
}
