package com.frzterr.app.ui.message

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frzterr.app.data.model.Conversation
import com.frzterr.app.data.model.ConversationWithUser
import com.frzterr.app.data.remote.supabase.SupabaseManager
import com.frzterr.app.data.repository.auth.AuthRepository
import com.frzterr.app.data.repository.message.MessageRepository
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class MessageViewModel : ViewModel() {

    private val repo = MessageRepository()
    private val authRepo = AuthRepository()

    private val _conversations = MutableLiveData<List<ConversationWithUser>>(emptyList())
    val conversations: LiveData<List<ConversationWithUser>> = _conversations

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private val _openConversation = MutableLiveData<Conversation?>(null)
    val openConversation: LiveData<Conversation?> = _openConversation

    // Guard agar hanya sekali subscribe meski fragment di-recreate
    private var isStarted = false
    private val realtimeChannel = SupabaseManager.client.channel("conv_messages_rt")

    // ===================================================
    // START — dipanggil dari onViewCreated
    // ===================================================

    fun startRealtimeAndLoad() {
        val currentUserId = authRepo.getCurrentUser()?.id ?: return

        // Selalu load sekali saat masuk — supaya langsung fresh
        viewModelScope.launch { fetchConversations(currentUserId) }

        if (isStarted) return  // Jangan double-subscribe
        isStarted = true

        // 1️⃣  Realtime — flow + subscribe dalam satu coroutine, urutan dijamin
        viewModelScope.launch {
            val flow = realtimeChannel
                .postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "messages"
                }

            realtimeChannel.subscribe()
            Log.d("MessageViewModel", "Realtime channel subscribed ✅")

            flow.collect {
                Log.d("MessageViewModel", "Realtime event received: $it")
                fetchConversations(currentUserId)
            }
        }

        // 2️⃣  Polling 1 detik sebagai fallback
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                fetchConversations(currentUserId)
            }
        }
    }

    private suspend fun fetchConversations(currentUserId: String) {
        try {
            val result = repo.getConversations(currentUserId)
            // Hanya emit kalau data benar-benar berubah → cegah flicker
            if (result != _conversations.value) {
                _conversations.postValue(result)
            }
        } catch (e: Exception) {
            Log.e("MessageViewModel", "Error loading conversations", e)
        }
    }

    // ===================================================
    // GET OR CREATE CONVERSATION
    // ===================================================

    fun getOrCreateConversation(otherUserId: String) {
        val currentUserId = authRepo.getCurrentUser()?.id ?: return
        viewModelScope.launch {
            try {
                val conv = repo.getOrCreateConversation(currentUserId, otherUserId)
                _openConversation.value = conv
            } catch (e: Exception) {
                Log.e("MessageViewModel", "Error creating conversation", e)
                _error.value = "Tidak bisa membuka percakapan"
            }
        }
    }

    fun clearOpenConversation() {
        _openConversation.value = null
    }

    // Dipanggil dari onResume → refresh instan saat balik dari chat
    fun refreshNow() {
        val currentUserId = authRepo.getCurrentUser()?.id ?: return
        viewModelScope.launch { fetchConversations(currentUserId) }
    }

    fun deleteConversation(conversationId: String) {
        val currentUserId = authRepo.getCurrentUser()?.id ?: return
        viewModelScope.launch {
            repo.deleteConversation(conversationId)
            fetchConversations(currentUserId)
        }
    }

    // ===================================================
    // CLEANUP
    // ===================================================

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            try { realtimeChannel.unsubscribe() } catch (_: Exception) {}
        }
    }
}
