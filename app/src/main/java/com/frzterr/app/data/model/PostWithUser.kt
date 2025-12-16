package com.frzterr.app.data.model

import com.frzterr.app.data.repository.user.AppUser

/**
 * Combined model for displaying posts with user information in the UI
 */
data class PostWithUser(
    val post: Post,
    val user: AppUser,
    val isLiked: Boolean = false,
    val isReposted: Boolean = false
)
