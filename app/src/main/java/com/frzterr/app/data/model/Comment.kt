package com.frzterr.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Comment(
    val id: String,

    @SerialName("post_id")
    val postId: String,

    @SerialName("user_id")
    val userId: String,

    val content: String,

    @SerialName("created_at")
    val createdAt: String
)
