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
import coil.imageLoader
import coil.load
import com.frzterr.app.R
import com.frzterr.app.data.model.PostWithUser
import com.google.android.material.imageview.ShapeableImageView
import java.text.SimpleDateFormat
import java.util.Locale

class PostAdapter(
    private val currentUserId: String?,
    private val contextType: String = "default", // Context for state isolation (e.g. "home", "profile")
    private val onLikeClick: (PostWithUser) -> Unit,
    private val onCommentClick: (PostWithUser) -> Unit,
    private val onRepostClick: (PostWithUser) -> Unit,
    private val onUserClick: (PostWithUser) -> Unit,
    private val onOptionClick: (PostWithUser) -> Unit,

    private val onImageClick: (List<String>, Int, View) -> Unit
) : ListAdapter<PostWithUser, PostAdapter.PostViewHolder>(PostDiffCallback()) {

    // Carousel positions now stored in HomeViewModel companion object (app-wide persistence)

    override fun onViewRecycled(holder: PostViewHolder) {
        super.onViewRecycled(holder)
        holder.cleanup()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_post, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else {
            val combinedPayloads = payloads.filterIsInstance<Set<String>>().flatten().toSet()
            if (combinedPayloads.isEmpty()) {
                super.onBindViewHolder(holder, position, payloads) // Fallback to full bind
            } else {
                val item = getItem(position)
                holder.updateItem(item) // Update the item reference for click listeners

                if (combinedPayloads.contains("PAYLOAD_LIKE")) {
                    holder.updateLikeState(item.isLiked, item.post.likeCount)
                }
                if (combinedPayloads.contains("PAYLOAD_REPOST")) {
                    holder.updateRepostState(item.isReposted, item.post.repostCount)
                }
                if (combinedPayloads.contains("PAYLOAD_COMMENT")) {
                    holder.updateCommentCount(item.post.commentCount)
                }
            }
        }
    }

    inner class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgAvatar: ShapeableImageView = itemView.findViewById(R.id.imgAvatar)
        private val tvUsername: TextView = itemView.findViewById(R.id.tvUsername)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val tvContent: TextView = itemView.findViewById(R.id.tvContent)
        
        private val btnLike: ImageView = itemView.findViewById(R.id.btnLike)
        private val tvLikeCount: TextView = itemView.findViewById(R.id.tvLikeCount)
        private val tvCommentCount: TextView = itemView.findViewById(R.id.tvCommentCount)
        private val btnRepost: ImageView = itemView.findViewById(R.id.btnRepost)
        private val tvRepostCount: TextView = itemView.findViewById(R.id.tvRepostCount)
        private val btnOption: ImageView = itemView.findViewById(R.id.btnOption)
        private val rvPostImages: RecyclerView = itemView.findViewById(R.id.rvPostImages)
        
        private var currentPostId: String? = null
        private lateinit var currentItem: PostWithUser

        init {
            // Setup Horizontal RecyclerView once
            rvPostImages.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
                itemView.context,
                androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL,
                false
            )

            // Fix ViewPager2 Conflict with Vertical Scroll Support AND Handle Padding Clicks
            val gestureDetector = android.view.GestureDetector(itemView.context, object : android.view.GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: android.view.MotionEvent): Boolean {
                    // Forward click to parent when tapping empty space (padding)
                    itemView.performClick()
                    return true
                }

                override fun onLongPress(e: android.view.MotionEvent) {
                    itemView.performLongClick()
                }

                override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                    // Direct Double Tap Logic for padding area
                    animateButton(btnLike)
                    // Haptic feedback
                    itemView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    
                    if (::currentItem.isInitialized && !currentItem.isLiked) {
                        onLikeClick(currentItem)
                    }
                    return true
                }
                
                override fun onDown(e: android.view.MotionEvent): Boolean {
                    return true // Necessary to receive further events
                }
            })

            var startX = 0f
            var startY = 0f

            rvPostImages.setOnTouchListener { v, e ->
                val rv = v as RecyclerView
                
                // 1. Handle Padding Clicks logic
                val childView = rv.findChildViewUnder(e.x, e.y)
                if (childView == null) {
                    // Feed to gesture detector for Click handling
                    gestureDetector.onTouchEvent(e)
                    
                    // Manually trigger Ripple Effect on parent
                    when (e.action) {
                        android.view.MotionEvent.ACTION_DOWN -> itemView.isPressed = true
                        android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> itemView.isPressed = false
                    }
                }

                // 2. Handle Scroll Conflicts logic
                when (e.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        startX = e.x
                        startY = e.y
                        rv.parent?.requestDisallowInterceptTouchEvent(true) // Lock initially
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dx = Math.abs(e.x - startX)
                        val dy = Math.abs(e.y - startY)

                        // If vertical scroll is dominant, release the lock so parent can scroll
                        if (dy > dx) {
                            rv.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    android.view.MotionEvent.ACTION_UP,
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        rv.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
                
                // Return false to let RecyclerView handle scrolling naturally
                false
            }
            
            // Manual double-tap detection (more reliable than GestureDetector in RecyclerView)
            var lastClickTime = 0L
            val doubleClickListener = View.OnClickListener {
                if (!::currentItem.isInitialized) return@OnClickListener
                
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastClickTime < 300) {
                    // Double tap detected!
                    
                    // Always animate and provide feedback
                    animateButton(btnLike)
                    it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)

                    // Only toggle like state if NOT already liked
                    if (!currentItem.isLiked) {
                        onLikeClick(currentItem)
                    }
                    
                    lastClickTime = 0 // Reset
                } else {
                    lastClickTime = currentTime
                }
            }
            
            // Apply to main container ONLY
            itemView.setOnClickListener(doubleClickListener)

            // Long click to show options (For all posts)
            itemView.setOnLongClickListener {
                if (::currentItem.isInitialized) {
                    it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    onOptionClick(currentItem)
                }
                true
            }
            
            // Click listeners - lightweight animation + haptic feedback
            btnLike.setOnClickListener {
                if (::currentItem.isInitialized) {
                    animateButton(btnLike)
                    onLikeClick(currentItem)
                }
            }

            btnRepost.setOnClickListener {
                if (::currentItem.isInitialized) {
                    animateButton(btnRepost)
                    onRepostClick(currentItem)
                }
            }

            itemView.findViewById<View>(R.id.btnComment).setOnClickListener {
                if (::currentItem.isInitialized) onCommentClick(currentItem)
            }
            imgAvatar.setOnClickListener { if (::currentItem.isInitialized) onUserClick(currentItem) }
            tvUsername.setOnClickListener { if (::currentItem.isInitialized) onUserClick(currentItem) }
            
            // OPTION CLICK Logic
            // Always show option button (Copy / Not Interested / Edit / Delete)
            btnOption.visibility = View.VISIBLE
            btnOption.setOnClickListener {
                if (::currentItem.isInitialized) onOptionClick(currentItem)
            }
        }
        
        fun updateItem(item: PostWithUser) {
            this.currentItem = item
        }

        fun bind(postWithUser: PostWithUser) {
            this.currentItem = postWithUser
            val post = postWithUser.post
            val user = postWithUser.user

            // Save carousel state before binding new post
            if (currentPostId != null && currentPostId != post.id && rvPostImages.visibility == View.VISIBLE) {
                HomeViewModel.saveCarouselPosition(
                    "${currentPostId}_$contextType", 
                    rvPostImages.layoutManager?.onSaveInstanceState()
                )
            }

            // User info
            tvUsername.text = user.username
            
            // Verified Badge
            if (user.isVerified) {
                val drawable = androidx.core.content.ContextCompat.getDrawable(itemView.context, R.drawable.ic_verified_request)?.mutate()
                drawable?.setBounds(0, 0, tvUsername.textSize.toInt(), tvUsername.textSize.toInt())
                tvUsername.setCompoundDrawables(null, null, drawable, null)
                tvUsername.compoundDrawablePadding = 8
            } else {
                tvUsername.setCompoundDrawables(null, null, null, null)
            }
            
            // Avatar
            // Avatar
            val shimmerAvatar: com.facebook.shimmer.ShimmerFrameLayout = itemView.findViewById(R.id.shimmerAvatar)

            // Reset State
            shimmerAvatar.visibility = View.VISIBLE
            shimmerAvatar.startShimmer()

            imgAvatar.load(user.avatarUrl) {
                crossfade(false)
                size(120)
                // Removed placeholder as requested, using Shimmer instead
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

            // Image Logic Views
            val imgPostSingle: ShapeableImageView = itemView.findViewById(R.id.imgPostSingle)
            val shimmerSingle: com.facebook.shimmer.ShimmerFrameLayout = itemView.findViewById(R.id.shimmerSingle)
            val layoutSingleContainer: View = imgPostSingle.parent as View // The FrameLayout wrapper
            
            val layoutCarousel: View = itemView.findViewById(R.id.layoutCarousel)

            val images = postWithUser.post.imageUrls
            
            if (images.isEmpty()) {
                // NO IMAGES
                layoutSingleContainer.visibility = View.GONE
                layoutCarousel.visibility = View.GONE
            } else if (images.size == 1) {
                // SINGLE IMAGE - Use Adaptive ImageView
                layoutSingleContainer.visibility = View.VISIBLE
                layoutCarousel.visibility = View.GONE
                
                // Reset State
                shimmerSingle.visibility = View.VISIBLE
                shimmerSingle.startShimmer()
                imgPostSingle.strokeWidth = 0f
                
                val url = images[0]
                androidx.core.view.ViewCompat.setTransitionName(imgPostSingle, url)

                imgPostSingle.load(url) {
                    crossfade(true)
                    listener(
                        onSuccess = { _, _ ->
                            shimmerSingle.stopShimmer()
                            shimmerSingle.visibility = View.GONE
                            imgPostSingle.strokeWidth = 3f
                        },
                        onError = { _, _ ->
                            shimmerSingle.stopShimmer()
                            shimmerSingle.visibility = View.GONE
                            imgPostSingle.strokeWidth = 0f
                        }
                    )
                }
                
                imgPostSingle.setOnClickListener {
                    onImageClick(images, 0, imgPostSingle)
                }
            } else {
                // MULTI IMAGES - Use Recycler Carousel (Threads Style)
                layoutSingleContainer.visibility = View.GONE
                layoutCarousel.visibility = View.VISIBLE
                
                // Setup adapter (create once, reuse forever for this ViewHolder)
                if (rvPostImages.adapter == null) {
                    val imageAdapter = PostImageAdapter { position, view ->
                        val currentImages = (rvPostImages.adapter as? PostImageAdapter)?.currentList ?: emptyList()
                        if (position < currentImages.size) {
                            onImageClick(currentImages, position, view)
                        }
                    }
                    rvPostImages.adapter = imageAdapter
                }
                
                // Save carousel state before binding new post
                if (currentPostId != null && currentPostId != post.id) {
                    HomeViewModel.saveCarouselPosition(
                        "${currentPostId}_$contextType", 
                        rvPostImages.layoutManager?.onSaveInstanceState()
                    )
                }
                
                // ALWAYS update and reset position for GUARANTEED fresh state
                // No conditions, no compromises - ensures refresh ALWAYS works
                val isDifferentPost = currentPostId != post.id
                
                // For new/recycled views, hide content temporarily to prevent visual "jump"
                if (isDifferentPost) {
                     rvPostImages.alpha = 0f
                }
                
                // Update current post ID
                currentPostId = post.id
                
                // Check saved state BEFORE submitList
                val savedState = HomeViewModel.getCarouselPosition("${post.id}_$contextType")
                
                // FORCE scroll to position (savedState or 0)
                // This ensures EVERY bind resets carousel properly
                rvPostImages.clearOnScrollListeners() // Clear first to prevent conflicts
                
                if (savedState == null) {
                    // NO saved state = fresh start, scroll to 0 IMMEDIATELY
                    rvPostImages.scrollToPosition(0)
                } else {
                    // Has saved state = restore position
                    rvPostImages.post {
                        rvPostImages.layoutManager?.onRestoreInstanceState(savedState)
                    }
                }
                
                // Update images for this post
                (rvPostImages.adapter as? PostImageAdapter)?.submitList(images) {
                    // Smoothly reveal the carousel after position is set
                    if (rvPostImages.alpha < 1f) {
                        rvPostImages.post {
                            rvPostImages.animate()
                                .alpha(1f)
                                .setDuration(200)
                                .start()
                        }
                    }
                }
                
                // Setup real-time position tracking (once per post)
                rvPostImages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        super.onScrollStateChanged(recyclerView, newState)
                        // Save position immediately when user stops swiping
                        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                            val layoutManager = recyclerView.layoutManager
                            if (layoutManager != null && currentPostId != null) {
                                HomeViewModel.saveCarouselPosition(
                                    "${currentPostId}_$contextType",
                                    layoutManager.onSaveInstanceState()
                                )
                            }
                        }
                    }
                })
            }

            // Timestamp
            tvTimestamp.text = formatTimestamp(post.createdAt)

            // Content
            tvContent.text = post.content

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

            // Repost button state - white when active, gray when inactive
            if (postWithUser.isReposted) {
                btnRepost.setColorFilter(0xFFFFFF.toInt() or 0xFF000000.toInt()) // White
            } else {
                btnRepost.setColorFilter(0x808080.toInt() or 0xFF000000.toInt()) // Gray
            }

            // Counts - hide when 0, show with slide animation when > 0
            updateCountWithAnimation(tvLikeCount, post.likeCount)
            updateCountWithAnimation(tvCommentCount, post.commentCount)
            updateCountWithAnimation(tvRepostCount, post.repostCount)

            // Click listeners are moved to init block
            // bind() only updates data and visual state that is bound to data
            
            // Image click listeners still need access to 'images' which is local to bind()
            // But we can delegate that too if we want, or keep it here for now.
            // Since images list might change, we should be careful.
            
            // Wait, I moved all listeners to init, so I need to remove them from here to avoid duplicate or overwriting with stale closures?
            // Actually, if I keep them here, they will overwrite the ones in init, effectively capturing the new 'postWithUser'.
            // BUT, wait. If partial bind happens, bind() is NOT called. So listeners are NOT updated.
            // So moving them to init and using 'currentItem' is the CORRECT way.
            // Therefore, I must DELETE the listener setting code from bind().
            
            // ... (Deleting listeners from bind) ...

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

        /**
         * Update count with slide-up animation
         * Hide when 0, show with animation when > 0
         */
        private fun updateCountWithAnimation(textView: TextView, count: Int) {
            val formattedCount = formatCount(count)
            val wasVisible = textView.visibility == View.VISIBLE
            
            if (count == 0) {
                // Hide when 0
                textView.visibility = View.GONE
            } else {
                // Show with slide animation if becoming visible
                if (!wasVisible) {
                    textView.visibility = View.VISIBLE
                    textView.alpha = 0f
                    textView.translationY = 20f
                    textView.text = formattedCount
                    textView.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(200)
                        .start()
                } else {
                    // Already visible, check if text needs update
                    if (textView.text.toString() != formattedCount) {
                         // Animate update from bottom (slide up)
                        textView.text = formattedCount
                        textView.alpha = 0f
                        textView.translationY = 20f
                        
                        textView.animate()
                            .alpha(1f)
                            .translationY(0f)
                            .setDuration(200)
                            .start()
                    }
                }
            }
        }

        private fun formatCount(count: Int): String {
            return when {
                count >= 1000000 -> "${count / 1000000}M"
                count >= 1000 -> "${count / 1000}K"
                else -> count.toString()
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
        fun cleanup() {
            // Save carousel state when view is recycled
            if (currentPostId != null && rvPostImages.visibility == View.VISIBLE) {
                HomeViewModel.saveCarouselPosition(
                    "${currentPostId}_$contextType", 
                    rvPostImages.layoutManager?.onSaveInstanceState()
                )
            }
            // Reset currentPostId so next bind treats it as a different post
            // This ensures carousel resets work immediately on refresh
            currentPostId = null
        }

        fun updateLikeState(isLiked: Boolean, count: Int) {
            // Like button state
            if (isLiked) {
                btnLike.setImageResource(R.drawable.ic_like_filled)
                btnLike.setColorFilter(
                    itemView.context.getColor(android.R.color.holo_red_dark)
                )
            } else {
                btnLike.setImageResource(R.drawable.ic_like)
                btnLike.setColorFilter(0x808080.toInt() or 0xFF000000.toInt()) // #808080
            }
            updateCountWithAnimation(tvLikeCount, count)
        }

        fun updateRepostState(isReposted: Boolean, count: Int) {
            // Repost button state
            if (isReposted) {
                btnRepost.setColorFilter(0xFFFFFF.toInt() or 0xFF000000.toInt()) // White
            } else {
                btnRepost.setColorFilter(0x808080.toInt() or 0xFF000000.toInt()) // Gray
            }
            updateCountWithAnimation(tvRepostCount, count)
        }

        fun updateCommentCount(count: Int) {
            updateCountWithAnimation(tvCommentCount, count)
        }
    }

    class PostDiffCallback : DiffUtil.ItemCallback<PostWithUser>() {
        override fun areItemsTheSame(oldItem: PostWithUser, newItem: PostWithUser): Boolean {
            return oldItem.post.id == newItem.post.id
        }

        override fun areContentsTheSame(oldItem: PostWithUser, newItem: PostWithUser): Boolean {
            return oldItem == newItem
        }

        override fun getChangePayload(oldItem: PostWithUser, newItem: PostWithUser): Any? {
            val payloads = mutableSetOf<String>()
            
            if (oldItem.isLiked != newItem.isLiked || oldItem.post.likeCount != newItem.post.likeCount) {
                payloads.add("PAYLOAD_LIKE")
            }
            
            if (oldItem.isReposted != newItem.isReposted || oldItem.post.repostCount != newItem.post.repostCount) {
                payloads.add("PAYLOAD_REPOST")
            }
            
            if (oldItem.post.commentCount != newItem.post.commentCount) {
                payloads.add("PAYLOAD_COMMENT")
            }
            
            return if (payloads.isNotEmpty()) payloads else null
        }
    }
}
