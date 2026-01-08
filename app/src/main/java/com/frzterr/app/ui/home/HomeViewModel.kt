package com.frzterr.app.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frzterr.app.data.model.PostWithUser
import com.frzterr.app.data.repository.auth.AuthRepository
import com.frzterr.app.data.repository.post.PostRepository
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    companion object {
        // App-wide carousel position storage (survives navigation & config changes)
        private val carouselPositions = mutableMapOf<String, android.os.Parcelable?>()
        
        // Flag to disable saving during refresh (prevents race conditions)
        private var allowSavingPositions = true
        
        /**
         * Save carousel position for a specific post
         * Called by PostAdapter when carousel position changes
         */
        fun saveCarouselPosition(postId: String, state: android.os.Parcelable?) {
            // Only save if allowed (not during refresh)
            if (allowSavingPositions) {
                carouselPositions[postId] = state
            }
        }
        
        /**
         * Get saved carousel position for a specific post
         * Returns null if no position saved yet
         */
        fun getCarouselPosition(postId: String): android.os.Parcelable? {
            return carouselPositions[postId]
        }
        
        /**
         * Clear saved carousel positions
         * @param contextType If provided, only clears positions for this context (e.g., "home"). 
         *                    If null, clears ALL positions.
         */
        fun clearCarouselPositions(contextType: String? = null) {
            // Disable saving first to prevent race conditions
            allowSavingPositions = false
            
            if (contextType != null) {
                // Clear only keys belonging to this context
                val iterator = carouselPositions.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (entry.key.endsWith("_$contextType")) {
                        iterator.remove()
                    }
                }
            } else {
                // Clear everything
                carouselPositions.clear()
            }
        }
        
        /**
         * Re-enable position saving after refresh is complete
         */
        fun enableSavingPositions() {
            allowSavingPositions = true
        }
    }

    private val postRepo = PostRepository()
    private val authRepo = AuthRepository()

    var lastImageClickTime: Long = 0L
    private var hasInitialLoadCompleted = false

    private val _posts = MutableLiveData<List<PostWithUser>>()
    val posts: LiveData<List<PostWithUser>> = _posts

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    init {
        loadPosts()
    }

    fun loadPosts(showLoading: Boolean = true) {
        // ALWAYS show loading on first load (cold start) or forced
        val shouldShowLoading = showLoading || !hasInitialLoadCompleted
        if (shouldShowLoading) {
            _isLoading.value = true // ⚡ SET INSTANTLY
        }

        viewModelScope.launch {
            try {
                // If we forced loading above, ensure we don't double-set or unset prematurely
                // The isLoading flag is already true if needed.
                _error.value = null

                var currentUser = authRepo.getCurrentUser()
                
                // If user not found, try to explicitly load/wait for session from storage
                // This fixes the race condition where MainActivity is still loading session
                if (currentUser == null) {
                   authRepo.loadSession() // Suspending call - waits for disk/check
                   currentUser = authRepo.getCurrentUser()
                }

                if (currentUser == null) {
                     // Still null? Retry loop for slow devices
                     var retries = 0
                     while (currentUser == null && retries < 10) { // Retry up to ~5 seconds
                        kotlinx.coroutines.delay(500)
                        authRepo.loadSession()
                        currentUser = authRepo.getCurrentUser()
                        retries++
                    }
                }
                
                if (currentUser == null) {
                    // Session genuinely not available - likely actually logged out
                    _error.value = null // Don't show error, let MainActivity redirect
                    return@launch
                }

                val fetchedPosts = postRepo.getAllPosts(currentUser.id)
                _posts.value = fetchedPosts
                hasInitialLoadCompleted = true

            } catch (e: Exception) {
                _error.value = "Failed to load posts: ${e.message}"
                hasInitialLoadCompleted = true
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleLike(postId: String, currentlyLiked: Boolean) {
        viewModelScope.launch {
            try {
                val currentUser = authRepo.getCurrentUser() ?: return@launch

                // ⚡ INSTANT Optimistic update - NO DELAY
                val optimisticPosts = _posts.value?.map { postWithUser ->
                    if (postWithUser.post.id == postId) {
                        val updatedPost = postWithUser.post.copy(
                            likeCount = if (currentlyLiked) 
                                (postWithUser.post.likeCount - 1).coerceAtLeast(0) 
                            else 
                                postWithUser.post.likeCount + 1
                        )
                        postWithUser.copy(
                            post = updatedPost,
                            isLiked = !currentlyLiked
                        )
                    } else {
                        postWithUser
                    }
                }
                _posts.value = optimisticPosts ?: emptyList()

                // Background sync - fire and forget
                launch {
                    val result = postRepo.toggleLike(postId, currentUser.id, currentlyLiked)
                    if (result.isFailure) {
                        // Revert on failure
                        loadPosts(showLoading = false)
                    }
                }

            } catch (e: Exception) {
                loadPosts(showLoading = false)
            }
        }
    }

    fun refresh() {
        loadPosts(showLoading = true)
    }

    fun toggleRepost(postId: String, currentlyReposted: Boolean) {
        viewModelScope.launch {
            try {
                val currentUser = authRepo.getCurrentUser() ?: return@launch

                // ⚡ INSTANT Optimistic update - NO DELAY
                val optimisticPosts = _posts.value?.map { postWithUser ->
                    if (postWithUser.post.id == postId) {
                        val updatedPost = postWithUser.post.copy(
                            repostCount = if (currentlyReposted) 
                                (postWithUser.post.repostCount - 1).coerceAtLeast(0) 
                            else 
                                postWithUser.post.repostCount + 1
                        )
                        postWithUser.copy(
                            post = updatedPost,
                            isReposted = !currentlyReposted
                        )
                    } else {
                        postWithUser
                    }
                }
                _posts.value = optimisticPosts ?: emptyList()

                // Background sync - fire and forget
                launch {
                    val result = postRepo.toggleRepost(postId, currentUser.id, currentlyReposted)
                    if (result.isFailure) {
                        // Revert on failure
                        loadPosts(showLoading = false)
                    }
                }

            } catch (e: Exception) {
                loadPosts(showLoading = false)
            }
        }
    }
    fun deletePost(post: com.frzterr.app.data.model.Post) {
        val oldList = _posts.value

        // ⚡ INSTANT Optimistic update
        _posts.value = oldList?.filter { it.post.id != post.id }

        viewModelScope.launch {
            try {
                val currentUser = authRepo.getCurrentUser() ?: return@launch
                val result = postRepo.deletePost(post.id, currentUser.id)

                if (result.isFailure) {
                    _error.value = "Gagal menghapus postingan"
                    _posts.value = oldList // Revert
                }
            } catch (e: Exception) {
                _error.value = "Gagal menghapus postingan"
                _posts.value = oldList // Revert
            }
        }
    }

    fun editPost(post: com.frzterr.app.data.model.Post, newContent: String) {
        val oldList = _posts.value

        // ⚡ INSTANT Optimistic update
        val updatedList = oldList?.map { postWithUser ->
            if (postWithUser.post.id == post.id) {
                postWithUser.copy(
                    post = postWithUser.post.copy(content = newContent)
                )
            } else {
                postWithUser
            }
        }
        _posts.value = updatedList ?: emptyList()

        viewModelScope.launch {
            try {
                val currentUser = authRepo.getCurrentUser() ?: return@launch
                val result = postRepo.updatePost(post.id, currentUser.id, newContent)

                if (result.isFailure) {
                    _error.value = "Gagal mengupdate postingan"
                    _posts.value = oldList // Revert
                }
            } catch (e: Exception) {
                _error.value = "Gagal mengupdate postingan"
                _posts.value = oldList // Revert
            }
        }
    }
    fun hidePost(postId: String) {
        val oldList = _posts.value
        _posts.value = oldList?.filter { it.post.id != postId }
    }
}
