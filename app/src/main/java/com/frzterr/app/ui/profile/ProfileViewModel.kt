package com.frzterr.app.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frzterr.app.data.model.PostWithUser
import com.frzterr.app.data.repository.auth.AuthRepository
import com.frzterr.app.data.repository.post.PostRepository
import com.frzterr.app.data.repository.user.AppUser
import kotlinx.coroutines.launch

class ProfileViewModel : ViewModel() {
    var cachedUser: AppUser? = null
    var lastRenderedAvatarUrl: String? = null

    private val postRepo = PostRepository()
    private val authRepo = AuthRepository()
    private val userRepo = com.frzterr.app.data.repository.user.UserRepository()

    private val _userPosts = MutableLiveData<List<PostWithUser>>(emptyList())
    val userPosts: LiveData<List<PostWithUser>> = _userPosts

    // 🚀 PRE-LOAD DATA SAAT VIEWMODEL DIBUAT
    init {
        preloadProfileData()
    }

    /**
     * Pre-load profile data in background
     * This runs when ViewModel is created (app start or activity recreation)
     */
    private fun preloadProfileData() {
        viewModelScope.launch {
            try {
                val currentUser = authRepo.getCurrentUser() ?: return@launch
                val dbUser = userRepo.getUserByIdForce(currentUser.id) ?: return@launch
                
                cachedUser = dbUser
                
                // Pre-load posts and reposts in parallel
                launch { loadUserPosts(dbUser.id) }
                launch { loadUserReposts(dbUser.id) }
            } catch (e: Exception) {
                // Silently fail, will retry when profile fragment opens
            }
        }
    }

    fun loadUserPosts(userId: String) {
        viewModelScope.launch {
            try {
                val currentUser = authRepo.getCurrentUser() ?: return@launch
                val posts = postRepo.getUserPosts(userId, currentUser.id)
                _userPosts.value = posts
            } catch (e: Exception) {
                _userPosts.value = emptyList()
            }
        }
    }

    fun toggleLike(postId: String, currentlyLiked: Boolean) {
        viewModelScope.launch {
            try {
                val currentUser = authRepo.getCurrentUser() ?: return@launch
                val userId = cachedUser?.id ?: return@launch

                // ⚡ Optimistic update
                val optimisticPosts = _userPosts.value?.map { postWithUser ->
                    if (postWithUser.post.id == postId) {
                        postWithUser.copy(isLiked = !currentlyLiked)
                    } else {
                        postWithUser
                    }
                }
                _userPosts.value = optimisticPosts ?: emptyList()

                // Background operation
                val result = postRepo.toggleLike(postId, currentUser.id, currentlyLiked)
                
                if (result.isFailure) {
                    loadUserPosts(userId)
                    return@launch
                }

                // Quick refresh
                kotlinx.coroutines.delay(200)
                val posts = postRepo.getUserPosts(userId, currentUser.id)
                _userPosts.value = posts

            } catch (e: Exception) {
                cachedUser?.id?.let { loadUserPosts(it) }
            }
        }
    }

    private val _userReposts = MutableLiveData<List<PostWithUser>>(emptyList())
    val userReposts: LiveData<List<PostWithUser>> = _userReposts

    fun loadUserReposts(userId: String) {
        viewModelScope.launch {
            try {
                val currentUser = authRepo.getCurrentUser() ?: return@launch
                val reposts = postRepo.getUserReposts(userId, currentUser.id)
                _userReposts.value = reposts
            } catch (e: Exception) {
                _userReposts.value = emptyList()
            }
        }
    }

    fun toggleRepost(postId: String, currentlyReposted: Boolean) {
        viewModelScope.launch {
            try {
                val currentUser = authRepo.getCurrentUser() ?: return@launch
                val userId = cachedUser?.id ?: return@launch

                // ⚡ Optimistic update for posts
                val optimisticPosts = _userPosts.value?.map { postWithUser ->
                    if (postWithUser.post.id == postId) {
                        postWithUser.copy(isReposted = !currentlyReposted)
                    } else {
                        postWithUser
                    }
                }
                _userPosts.value = optimisticPosts ?: emptyList()

                // Background operation
                val result = postRepo.toggleRepost(postId, currentUser.id, currentlyReposted)
                
                if (result.isFailure) {
                    loadUserPosts(userId)
                    return@launch
                }

                // Quick refresh
                kotlinx.coroutines.delay(200)
                val posts = postRepo.getUserPosts(userId, currentUser.id)
                _userPosts.value = posts

                // Also refresh reposts if visible
                val reposts = postRepo.getUserReposts(userId, currentUser.id)
                _userReposts.value = reposts

            } catch (e: Exception) {
                cachedUser?.id?.let { loadUserPosts(it) }
            }
        }
    }
}
