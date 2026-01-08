package com.frzterr.app.data.local

import android.content.Context
import android.content.SharedPreferences
import com.frzterr.app.data.repository.user.AppUser
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

@Serializable
data class RecentSearch(
    val userId: String,
    val username: String,
    val fullName: String?,
    val avatarUrl: String?,
    val timestamp: Long
)

object SearchHistoryStore {
    private const val PREFS_NAME = "search_history"
    private const val KEY_HISTORY = "recent_searches"
    private const val MAX_HISTORY = 10
    
    private val json = Json { ignoreUnknownKeys = true }
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    fun save(context: Context, user: AppUser) {
        val prefs = getPrefs(context)
        val current = load(context).toMutableList()
        
        // Remove if already exists
        current.removeAll { it.userId == user.id }
        
        // Add to top
        current.add(0, RecentSearch(
            userId = user.id,
            username = user.username ?: "",
            fullName = user.fullName,
            avatarUrl = user.avatarUrl,
            timestamp = System.currentTimeMillis()
        ))
        
        // Limit to max
        val limited = current.take(MAX_HISTORY)
        
        // Save
        val jsonString = json.encodeToString(limited)
        prefs.edit().putString(KEY_HISTORY, jsonString).apply()
    }
    
    fun load(context: Context): List<RecentSearch> {
        val prefs = getPrefs(context)
        val jsonString = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        
        return try {
            json.decodeFromString<List<RecentSearch>>(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun remove(context: Context, userId: String) {
        val prefs = getPrefs(context)
        val current = load(context).toMutableList()
        current.removeAll { it.userId == userId }
        
        val jsonString = json.encodeToString(current)
        prefs.edit().putString(KEY_HISTORY, jsonString).apply()
    }
    
    fun clear(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
