package com.frzterr.app.ui.aichat

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ChatHistoryManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("ai_chat_history", Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val KEY_SESSIONS = "sessions"
        private const val MAX_SESSIONS = 50
    }

    fun saveSessions(sessions: List<ChatSession>) {
        val trimmed = sessions.takeLast(MAX_SESSIONS)
        prefs.edit().putString(KEY_SESSIONS, json.encodeToString(trimmed)).apply()
    }

    fun loadSessions(): MutableList<ChatSession> {
        val raw = prefs.getString(KEY_SESSIONS, null) ?: return mutableListOf()
        return try {
            json.decodeFromString<List<ChatSession>>(raw).toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun deleteSession(sessionId: String) {
        val sessions = loadSessions()
        sessions.removeAll { it.id == sessionId }
        saveSessions(sessions)
    }

    fun clearAll() {
        prefs.edit().remove(KEY_SESSIONS).apply()
    }
}
