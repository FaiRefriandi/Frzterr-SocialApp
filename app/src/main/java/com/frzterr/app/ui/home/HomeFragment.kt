package com.frzterr.app.ui.home

import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.facebook.shimmer.ShimmerFrameLayout
import com.frzterr.app.R
import com.frzterr.app.data.repository.auth.AuthRepository
import com.frzterr.app.ui.create.CreatePostActivity

import com.google.android.material.floatingactionbutton.FloatingActionButton

class HomeFragment : Fragment(R.layout.fragment_home) {

    private val viewModel: HomeViewModel by viewModels()
    private val authRepo = AuthRepository()
    private lateinit var adapter: PostAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        postponeEnterTransition()

        // ===============================
        // 🔹 HEADER STATUS BAR SPACING
        // ===============================
        val header = view.findViewById<android.widget.TextView>(R.id.tvHomeHeader)

        ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val statusBarHeight = insets
                .getInsets(WindowInsetsCompat.Type.statusBars())
                .top

            v.setPadding(
                v.paddingLeft,
                statusBarHeight + 16.dp, // Adaptive spacing (not too close)
                v.paddingRight,
                v.paddingBottom
            )
            insets
        }

        // ===============================
        // 🔹 VIEW INIT
        // ===============================
        val rvPosts: RecyclerView = view.findViewById(R.id.rvPosts)
        val swipeRefresh: SwipeRefreshLayout = view.findViewById(R.id.swipeRefresh)
        val fabCreatePost: FloatingActionButton = view.findViewById(R.id.fabCreatePost)
        val emptyState: View = view.findViewById(R.id.emptyState)
        val shimmerViewContainer: ShimmerFrameLayout =
            view.findViewById(R.id.shimmerViewContainer)

        // ===============================
        // 🔹 ADAPTER
        // ===============================
        val currentUser = authRepo.getCurrentUser()

        // Initialize Adapter
        initAdapter()

        // ===============================
        // 🔹 OBSERVERS
        // ===============================
        viewModel.posts.observe(viewLifecycleOwner) {
            adapter.submitList(it) { startPostponedEnterTransition() }
            emptyState.visibility =
                if (it.isEmpty() && viewModel.isLoading.value == false) View.VISIBLE else View.GONE
        }

        viewModel.isLoading.observe(viewLifecycleOwner) {
            if (it) {
                shimmerViewContainer.visibility = View.VISIBLE
                shimmerViewContainer.startShimmer()
                rvPosts.visibility = View.GONE
            } else {
                shimmerViewContainer.stopShimmer()
                shimmerViewContainer.visibility = View.GONE
                rvPosts.visibility = View.VISIBLE
                swipeRefresh.isRefreshing = false

                // Re-enable saving after refresh completes
                // This allows normal scroll position saving to resume
                HomeViewModel.enableSavingPositions()
            }
        }

        viewModel.error.observe(viewLifecycleOwner) {
            it?.let { msg ->
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
        }

        swipeRefresh.setOnRefreshListener {
            performNuclearRefresh(rvPosts)
        }

        fabCreatePost.setOnClickListener {
            startActivity(Intent(requireContext(), CreatePostActivity::class.java))
        }
        
        // ===============================
        // 🔹 SCROLL LISTENER FOR HEADER & BOTTOM NAV
        // ===============================
        rvPosts.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            private val maxTextSize = 24f // sp
            private val minTextSize = 20f // sp
            private var isHeaderExpanded = true // Track header state
            
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                
                // ===============================
                // 🔹 HEADER TEXT SIZE ANIMATION (Threads-like)
                // ===============================
                // Check if we're at the top of the RecyclerView
                val isAtTop = !recyclerView.canScrollVertically(-1)
                
                // Only animate when state changes (not every scroll event)
                if (isAtTop != isHeaderExpanded) {
                    isHeaderExpanded = isAtTop
                    
                    // Cancel any running animation
                    header.tag?.let { (it as? android.animation.ValueAnimator)?.cancel() }
                    
                    val currentTextSize = header.textSize / resources.displayMetrics.scaledDensity
                    val targetTextSize = if (isAtTop) maxTextSize else minTextSize
                    
                    // Create smooth transition
                    val animator = android.animation.ValueAnimator.ofFloat(currentTextSize, targetTextSize)
                    animator.duration = if (isAtTop) 200 else 100 // Expand slower, shrink faster
                    animator.addUpdateListener { animation ->
                        header.textSize = animation.animatedValue as Float
                    }
                    animator.start()
                    header.tag = animator
                }
                
                // ===============================
                // 🔹 BOTTOM NAV ANIMATION
                // ===============================
                val bottomNav = requireActivity().findViewById<View>(R.id.bottom_nav) ?: return

                // Threads-like behavior: Move synchronously with scroll
                val currentTranslation = bottomNav.translationY
                val newTranslation =
                    (currentTranslation + dy).coerceIn(0f, bottomNav.height.toFloat())

                bottomNav.translationY = newTranslation

                // Sync FAB Scale (Inverse of Bottom Nav)
                // Translation 0 (Nav Visible) -> Fraction 0 -> FAB Hidden (Scale 0)
                // Translation Max (Nav Hidden) -> Fraction 1 -> FAB Visible (Scale 1)
                val fraction = newTranslation / bottomNav.height.toFloat()
                fabCreatePost.scaleX = fraction
                fabCreatePost.scaleY = fraction
            }
        })
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadPosts(showLoading = viewModel.posts.value.isNullOrEmpty())
        // Reset bottom nav position when returning to fragment
        requireActivity().findViewById<View>(R.id.bottom_nav)?.animate()?.translationY(0f)
            ?.setDuration(0)?.start()
    }

    fun scrollToTopAndRefresh() {
        val rvPosts: RecyclerView? = view?.findViewById(R.id.rvPosts)
        val swipeRefresh: SwipeRefreshLayout? = view?.findViewById(R.id.swipeRefresh)

        if (rvPosts != null && swipeRefresh != null && !swipeRefresh.isRefreshing) {
            rvPosts.smoothScrollToPosition(0)

            // Programmatically trigger refresh logic if needed, or just standard refresh
            // But we want the FULL reset logic (Nuclear Option)
            // So we can manually invoke the listener logic:

            // Show loading indicator
            swipeRefresh.isRefreshing = true

            // Delay slightly to allow scroll to top animation
            rvPosts.postDelayed({
                performNuclearRefresh(rvPosts)
            }, 300)
        }
    }

    private fun performNuclearRefresh(rvPosts: RecyclerView) {
        // Same logic as SwipeRefresh listener
        val currentUser = authRepo.getCurrentUser()

        HomeViewModel.clearCarouselPositions("home")
        rvPosts.scrollToPosition(0)

        // Re-init adapter (we need to copy the FULL creation logic, so extracting is best)
        initAdapter()

        viewModel.refresh()
    }

    private fun initAdapter() {
        val currentUser = authRepo.getCurrentUser()
        adapter = PostAdapter(
            currentUserId = currentUser?.id,
            contextType = "home",
            onLikeClick = { viewModel.toggleLike(it.post.id, it.isLiked) },
            onCommentClick = { postWithUser ->
                val sheet = com.frzterr.app.ui.comments.CommentsBottomSheet(
                    postId = postWithUser.post.id,
                    postOwnerId = postWithUser.post.userId,
                    onCommentAdded = { viewModel.refresh() },
                    onUserClick = { userId, avatarUrl ->
                        val bundle = Bundle().apply {
                            putString("userId", userId)
                            putString("avatarUrl", avatarUrl)
                        }
                        if (userId == currentUser?.id) {
                            findNavController().navigate(R.id.profileFragment, bundle)
                        } else {
                            findNavController().navigate(R.id.publicProfileFragment, bundle)
                        }
                    }
                )
                sheet.show(parentFragmentManager, sheet.tag)
            },
            onRepostClick = { viewModel.toggleRepost(it.post.id, it.isReposted) },
            onUserClick = {
                val bundle = Bundle().apply {
                    putString("userId", it.user.id)
                    putString("avatarUrl", it.user.avatarUrl)
                }
                if (it.user.id == currentUser?.id) {
                    findNavController().navigate(R.id.profileFragment, bundle)
                } else {
                    findNavController().navigate(R.id.publicProfileFragment, bundle)
                }
            },
            onOptionClick = { postWithUser ->
                val isOwner = currentUser?.id == postWithUser.post.userId
                PostOptionsBottomSheet(
                    isOwner = isOwner,
                    onCopyClick = {
                        val clipboard =
                            requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText(
                                "Post Content",
                                postWithUser.post.content
                            )
                        )
                        Toast.makeText(requireContext(), "Teks disalin", Toast.LENGTH_SHORT).show()
                    },
                    onEditClick = {
                        EditPostBottomSheet(
                            post = postWithUser.post,
                            onSaveClick = { viewModel.editPost(postWithUser.post, it) }).show(parentFragmentManager,
                            EditPostBottomSheet.TAG
                        )
                    },
                    onDeleteClick = { viewModel.deletePost(postWithUser.post) },
                    onNotInterestedClick = {
                        viewModel.hidePost(postWithUser.post.id)
                        Toast.makeText(
                            requireContext(),
                            "Postingan disembunyikan",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                ).show(requireActivity().supportFragmentManager, PostOptionsBottomSheet.TAG)
            },
            onImageClick = { images, position, _ ->
                if (System.currentTimeMillis() - viewModel.lastImageClickTime >= 500) {
                    viewModel.lastImageClickTime = System.currentTimeMillis()
                    val bundle = Bundle().apply {
                        putStringArrayList(
                            "arg_images",
                            ArrayList(images)
                        ); putInt("arg_position", position)
                    }
                    findNavController().navigate(R.id.imageViewerFragment, bundle)
                }
            }
        )

        val rvPosts: RecyclerView? = view?.findViewById(R.id.rvPosts)
        rvPosts?.adapter = adapter
        adapter.stateRestorationPolicy =
            RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    // ===============================
// 🔹 EXTENSION
// ===============================
    private val Int.dp: Int
        get() = (this * Resources.getSystem().displayMetrics.density).toInt()
}
