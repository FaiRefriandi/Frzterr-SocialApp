package com.frzterr.app.data.model

import com.frzterr.app.data.repository.user.AppUser

/**
 * Combined model for displaying comments with user information
 */
data class CommentWithUser(
    val comment: Comment,
    val user: AppUser
)
