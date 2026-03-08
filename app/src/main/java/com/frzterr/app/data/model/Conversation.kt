package com.frzterr.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Conversation(
    val id: String,

    @SerialName("user1_id")
    val user1Id: String,

    @SerialName("user2_id")
    val user2Id: String,

    @SerialName("created_at")
    val createdAt: String
)
