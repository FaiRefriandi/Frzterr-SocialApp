package com.frzterr.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Post(
    val id: String,

    @SerialName("user_id")
    val userId: String,

    val content: String,

    @SerialName("image_url")
    val imageUrl: String? = null,

    @SerialName("created_at")
    val createdAt: String,

    @SerialName("like_count")
    val likeCount: Int = 0,

    @SerialName("comment_count")
    val commentCount: Int = 0,

    @SerialName("repost_count")
    val repostCount: Int = 0
)
