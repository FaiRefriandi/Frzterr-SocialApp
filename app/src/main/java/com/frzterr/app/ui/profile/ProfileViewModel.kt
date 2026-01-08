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
import kotlinx.coroutines.runBlocking

class ProfileViewModel : ViewModel() {
    private val _user = MutableLiveData<AppUser?>()
    val user: LiveData<AppUser?> = _user

    var lastRenderedAvatarUrl: String? = null
    var lastImageClickTime: Long = 0L
    
    // 🔥 Store AppBarLayout offset to persist collapse state across navigation
    var appBarOffset: Int = 0

    private val postRepo = PostRepository()
    private val authRepo = AuthRepository()
    private val userRepo = com.frzterr.app.data.repository.user.UserRepository()

    private val _userPosts = MutableLiveData<List<PostWithUser>>(emptyList())
    val userPosts: LiveData<List<PostWithUser>> = _userPosts
    
    fun clearData() {
        _userPosts.value = emptyList()
        _userReposts.value = emptyList()
        _user.value = null
    }

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

                _user.value = dbUser

                // Pre-load posts and reposts in parallel
                launch { loadUserPosts(dbUser.id) }
                launch { loadUserReposts(dbUser.id) }
            } catch (e: Exception) {
                // Silently fail, will retry when profile fragment opens
            }
        }
    }

    fun loadUserPosts(userId: String, force: Boolean = false) {
        // OPTIMIZATION: Skip if already loaded for same user (unless forced)
        if (!force && _userPosts.value?.isNotEmpty() == true && _user.value?.id == userId) return

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
                val userId = _user.value?.id ?: return@launch

                // ⚡ INSTANT Optimistic update for BOTH posts and reposts
                val optimisticPosts = _userPosts.value?.map { postWithUser ->
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
                _userPosts.value = optimisticPosts ?: emptyList()

                // Also update reposts tab
                val optimisticReposts = _userReposts.value?.map { postWithUser ->
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
                _userReposts.value = optimisticReposts ?: emptyList()

                // Background sync - fire and forget
                launch {
                    val result = postRepo.toggleLike(postId, currentUser.id, currentlyLiked)
                    if (result.isFailure) {
                        loadUserPosts(userId)
                        loadUserReposts(userId)
                    }
                }

            } catch (e: Exception) {
                _user.value?.id?.let {
                    loadUserPosts(it)
                    loadUserReposts(it)
                }
            }
        }
    }

    private val _userReposts = MutableLiveData<List<PostWithUser>>(emptyList())
    val userReposts: LiveData<List<PostWithUser>> = _userReposts

    fun loadUserReposts(userId: String, force: Boolean = false) {
        // OPTIMIZATION: Skip if already loaded for same user (unless forced)
        if (!force && _userReposts.value?.isNotEmpty() == true && _user.value?.id == userId) return

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
                val userId = _user.value?.id ?: return@launch

                // ⚡ INSTANT Optimistic update for BOTH posts and reposts
                val optimisticPosts = _userPosts.value?.map { postWithUser ->
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
                _userPosts.value = optimisticPosts ?: emptyList()

                // Also update reposts tab
                val optimisticReposts = _userReposts.value?.map { postWithUser ->
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
                _userReposts.value = optimisticReposts ?: emptyList()

                // Background sync - fire and forget
                launch {
                    val result = postRepo.toggleRepost(postId, currentUser.id, currentlyReposted)
                    if (result.isFailure) {
                        loadUserPosts(userId)
                        loadUserReposts(userId)
                    }
                }

            } catch (e: Exception) {
                _user.value?.id?.let {
                    loadUserPosts(it)
                    loadUserReposts(it)
                }
            }
        }
    }

    fun deletePost(post: com.frzterr.app.data.model.Post) {
        val oldPosts = _userPosts.value
        val oldReposts = _userReposts.value

        // ⚡ INSTANT Optimistic update
        _userPosts.value = oldPosts?.filter { it.post.id != post.id }
        _userReposts.value = oldReposts?.filter { it.post.id != post.id }

        viewModelScope.launch {
            try {
                val currentUser = authRepo.getCurrentUser() ?: return@launch
                val result = postRepo.deletePost(post.id, currentUser.id)

                if (result.isFailure) {
                    _userPosts.value = oldPosts // Revert
                    _userReposts.value = oldReposts // Revert
                }
            } catch (e: Exception) {
                _userPosts.value = oldPosts // Revert
                _userReposts.value = oldReposts // Revert
            }
        }
    }

    fun editPost(post: com.frzterr.app.data.model.Post, newContent: String) {
        val oldPosts = _userPosts.value
        val oldReposts = _userReposts.value

        // ⚡ INSTANT Optimistic update for Posts tab
        val updatedPosts = oldPosts?.map { postWithUser ->
            if (postWithUser.post.id == post.id) {
                postWithUser.copy(
                    post = postWithUser.post.copy(content = newContent)
                )
            } else {
                postWithUser
            }
        }
        _userPosts.value = updatedPosts ?: emptyList()

        // ⚡ INSTANT Optimistic update for Reposts tab
        val updatedReposts = oldReposts?.map { postWithUser ->
            if (postWithUser.post.id == post.id) {
                postWithUser.copy(
                    post = postWithUser.post.copy(content = newContent)
                )
            } else {
                postWithUser
            }
        }
        _userReposts.value = updatedReposts ?: emptyList()

        viewModelScope.launch {
            try {
                val currentUser = authRepo.getCurrentUser() ?: return@launch
                val result = postRepo.updatePost(post.id, currentUser.id, newContent)

                if (result.isFailure) {
                    _userPosts.value = oldPosts // Revert
                    _userReposts.value = oldReposts // Revert
                }
            } catch (e: Exception) {
                _userPosts.value = oldPosts // Revert
                _userReposts.value = oldReposts // Revert
            }
        }
    }
    fun hidePost(postId: String) {
        val oldPosts = _userPosts.value
        val oldReposts = _userReposts.value
        
        _userPosts.value = oldPosts?.filter { it.post.id != postId }
        _userReposts.value = oldReposts?.filter { it.post.id != postId }
    }

    // ========================================================================
    // FOLLOW SYSTEM LOGIC
    // ========================================================================
    private val _isFollowing = MutableLiveData<Boolean>(false)
    val isFollowing: LiveData<Boolean> = _isFollowing

    private val _followerCount = MutableLiveData<Long>(0)
    val followerCount: LiveData<Long> = _followerCount

    private val _followingCount = MutableLiveData<Long>(0)
    val followingCount: LiveData<Long> = _followingCount

    fun loadFollowData(targetUserId: String) {
        viewModelScope.launch {
            try {
                val currentUser = authRepo.getCurrentUser()
                
                // Get counts in parallel
                launch {
                    // 🌟 CUSTOM FOLLOWER COUNTS 🌟
                    // Map of UserId -> Custom Count
                    val customFollowerCounts = mapOf(
                        "c86fe677-236f-4244-ba16-40f78fdc373f" to 666L, // Contoh user ID (Frefriandi?)
                        // Tambahkan ID lain di sini
                        // "user_id_lain" to 1000000L
                    )

                    val followers = if (customFollowerCounts.containsKey(targetUserId)) {
                        customFollowerCounts[targetUserId] ?: 0L
                    } else {
                        userRepo.getFollowerCount(targetUserId)
                    }
                    _followerCount.value = followers
                }
                
                launch {
                    val following = userRepo.getFollowingCount(targetUserId)
                    _followingCount.value = following
                }

                // Check if current user is following target user
                if (currentUser != null && currentUser.id != targetUserId) {
                    val following = userRepo.isFollowing(currentUser.id, targetUserId)
                    _isFollowing.value = following
                } else {
                    _isFollowing.value = false
                }
            } catch (e: Exception) {
                // Ignore errors
            }
        }
    }

    fun toggleFollow(targetUserId: String) {
        viewModelScope.launch {
            try {
                val currentUserId = authRepo.getCurrentUser()?.id ?: return@launch
                val currentlyFollowing = _isFollowing.value ?: false
                
                // Optimistic Update
                _isFollowing.value = !currentlyFollowing
                _followerCount.value = (_followerCount.value ?: 0L) + (if (currentlyFollowing) -1 else 1)
                
                // Use correct repository methods based on current status
                val result = if (currentlyFollowing) {
                    userRepo.unfollowUser(currentUserId, targetUserId)
                } else {
                    userRepo.followUser(currentUserId, targetUserId)
                }

                if (result.isFailure) {
                    // Rollback on failure
                    _isFollowing.value = currentlyFollowing
                    _followerCount.value = (_followerCount.value ?: 0L) + (if (currentlyFollowing) 1 else -1)
                }
            } catch (e: Exception) {
                // Refresh data if something goes wrong
                loadFollowData(targetUserId)
            }
        }
    }

    fun updateUser(updatedUser: AppUser?) {
        _user.value = updatedUser
    }
}
