package com.frzterr.app.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.frzterr.app.R
import com.frzterr.app.ui.create.CreatePostActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton

class HomeFragment : Fragment(R.layout.fragment_home) {

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var adapter: PostAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvPosts = view.findViewById<RecyclerView>(R.id.rvPosts)
        val swipeRefresh = view.findViewById<SwipeRefreshLayout>(R.id.swipeRefresh)
        val fabCreatePost = view.findViewById<FloatingActionButton>(R.id.fabCreatePost)
        val emptyState = view.findViewById<View>(R.id.emptyState)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)

        // Setup adapter
        adapter = PostAdapter(
            onLikeClick = { postWithUser ->
                viewModel.toggleLike(postWithUser.post.id, postWithUser.isLiked)
            },
            onCommentClick = { postWithUser ->
                val commentsBottomSheet = com.frzterr.app.ui.comments.CommentsBottomSheet(
                    postId = postWithUser.post.id,
                    onCommentAdded = {
                        // Refresh to update comment count
                        viewModel.refresh()
                    }
                )
                commentsBottomSheet.show(
                    childFragmentManager,
                    com.frzterr.app.ui.comments.CommentsBottomSheet.TAG
                )
            },
            onRepostClick = { postWithUser ->
                viewModel.toggleRepost(postWithUser.post.id, postWithUser.isReposted)
            },
            onUserClick = { postWithUser ->
                // TODO: Navigate to user profile
                Toast.makeText(
                    requireContext(),
                    "Profile: @${postWithUser.user.username}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )

        rvPosts.adapter = adapter

        // Observe posts
        viewModel.posts.observe(viewLifecycleOwner) { posts ->
            adapter.submitList(posts)
            emptyState.visibility = if (posts.isEmpty()) View.VISIBLE else View.GONE
        }

        // Observe loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                progressBar.visibility = View.VISIBLE
            } else {
                progressBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false
            }
        }

        // Observe errors
        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            }
        }

        // Swipe to refresh
        swipeRefresh.setOnRefreshListener {
            viewModel.refresh()
        }

        // FAB click
        fabCreatePost.setOnClickListener {
            startActivity(Intent(requireContext(), CreatePostActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh posts when returning to this fragment
        viewModel.refresh()
    }
}
