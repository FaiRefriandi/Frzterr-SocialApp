package com.frzterr.app.ui.aichat

import kotlinx.serialization.Serializable

@Serializable
data class ChatSession(
    val id: String,
    val title: String,
    val messages: List<AiChatMessage>,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class AiChatMessage(
    val role: String,           // "user" or "assistant"
    val content: String,
    val modelName: String = "AI" // nama model yang digunakan saat mengirim
)
