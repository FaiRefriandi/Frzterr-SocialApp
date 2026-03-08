package com.frzterr.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val id: String,

    @SerialName("conversation_id")
    val conversationId: String,

    @SerialName("sender_id")
    val senderId: String,

    val content: String,

    @SerialName("created_at")
    val createdAt: String,

    @SerialName("is_read")
    val isRead: Boolean = false
)
