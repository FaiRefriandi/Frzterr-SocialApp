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

    private val postRepo = PostRepository()
    private val authRepo = AuthRepository()

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
        viewModelScope.launch {
            try {
                if (showLoading) {
                    _isLoading.value = true
                }
                _error.value = null

                // Retry mechanism for getCurrentUser (handle session loading race condition)
                var currentUser = authRepo.getCurrentUser()
                var retries = 0
                while (currentUser == null && retries < 3) {
                    kotlinx.coroutines.delay(300) // Wait for session to load
                    currentUser = authRepo.getCurrentUser()
                    retries++
                }
                
                if (currentUser == null) {
                    // Session genuinely not available - likely actually logged out
                    _error.value = null // Don't show error, let MainActivity handle redirect
                    return@launch
                }

                val fetchedPosts = postRepo.getAllPosts(currentUser.id)
                _posts.value = fetchedPosts

            } catch (e: Exception) {
                _error.value = "Failed to load posts: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleLike(postId: String, currentlyLiked: Boolean) {
        viewModelScope.launch {
            try {
                // Retry mechanism for getCurrentUser
                var currentUser = authRepo.getCurrentUser()
                var retries = 0
                while (currentUser == null && retries < 3) {
                    kotlinx.coroutines.delay(200)
                    currentUser = authRepo.getCurrentUser()
                    retries++
                }
                
                if (currentUser == null) return@launch

                // ⚡ Optimistic update
                val optimisticPosts = _posts.value?.map { postWithUser ->
                    if (postWithUser.post.id == postId) {
                        postWithUser.copy(isLiked = !currentlyLiked)
                    } else {
                        postWithUser
                    }
                }
                _posts.value = optimisticPosts ?: emptyList()

                // Background database operation
                val result = postRepo.toggleLike(postId, currentUser.id, currentlyLiked)
                
                if (result.isFailure) {
                    // Revert on failure
                    _error.value = "Gagal menyukai post"
                    loadPosts(showLoading = false)
                    return@launch
                }

                // Quick refresh with real-time counts
                kotlinx.coroutines.delay(200)
                val refreshedPosts = postRepo.getAllPosts(currentUser.id)
                _posts.value = refreshedPosts

            } catch (e: Exception) {
                _error.value = "Gagal menyukai post"
                loadPosts(showLoading = false)
            }
        }
    }

    fun refresh() {
        loadPosts(showLoading = false)
    }

    fun toggleRepost(postId: String, currentlyReposted: Boolean) {
        viewModelScope.launch {
            try {
                // Retry mechanism for getCurrentUser
                var currentUser = authRepo.getCurrentUser()
                var retries = 0
                while (currentUser == null && retries < 3) {
                    kotlinx.coroutines.delay(200)
                    currentUser = authRepo.getCurrentUser()
                    retries++
                }
                
                if (currentUser == null) return@launch

                // ⚡ Optimistic update
                val optimisticPosts = _posts.value?.map { postWithUser ->
                    if (postWithUser.post.id == postId) {
                        postWithUser.copy(isReposted = !currentlyReposted)
                    } else {
                        postWithUser
                    }
                }
                _posts.value = optimisticPosts ?: emptyList()

                // Background database operation
                val result = postRepo.toggleRepost(postId, currentUser.id, currentlyReposted)
                
                if (result.isFailure) {
                    // Revert on failure
                    _error.value = "Gagal repost"
                    loadPosts(showLoading = false)
                    return@launch
                }

                // Quick refresh with real-time counts
                kotlinx.coroutines.delay(200)
                val refreshedPosts = postRepo.getAllPosts(currentUser.id)
                _posts.value = refreshedPosts

            } catch (e: Exception) {
                _error.value = "Gagal repost"
                loadPosts(showLoading = false)
            }
        }
    }
}
