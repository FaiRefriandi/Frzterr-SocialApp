package com.frzterr.app.data.model

import com.frzterr.app.data.repository.user.AppUser

data class ConversationWithUser(
    val conversation: Conversation,
    val otherUser: AppUser,
    val lastMessage: Message? = null,
    val unreadCount: Int = 0
)
