package com.frzterr.app.ui.message

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frzterr.app.data.model.Message
import com.frzterr.app.data.repository.auth.AuthRepository
import com.frzterr.app.data.repository.message.MessageRepository
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {

    private val repo = MessageRepository()
    private val authRepo = AuthRepository()

    private val _messages = MutableLiveData<List<Message>>(emptyList())
    val messages: LiveData<List<Message>> = _messages

    private val _isSending = MutableLiveData(false)
    val isSending: LiveData<Boolean> = _isSending

    private var conversationId: String? = null
    private val pollHandler = Handler(Looper.getMainLooper())
    private val pollInterval = 3000L // 3 seconds

    private val pollRunnable = object : Runnable {
        override fun run() {
            refreshMessages()
            pollHandler.postDelayed(this, pollInterval)
        }
    }

    // ===================================================
    // INIT / LOAD
    // ===================================================

    fun init(convId: String) {
        conversationId = convId
        loadMessages()
        startPolling()
    }

    private fun loadMessages() {
        val convId = conversationId ?: return
        viewModelScope.launch {
            try {
                val msgs = repo.getMessages(convId)
                _messages.value = msgs
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error loading messages", e)
            }
        }
    }

    fun refreshMessages() {
        loadMessages()
    }

    // ===================================================
    // SEND
    // ===================================================

    fun sendMessage(content: String) {
        val convId = conversationId ?: return
        val senderId = authRepo.getCurrentUser()?.id ?: return
        if (content.isBlank()) return

        _isSending.value = true
        viewModelScope.launch {
            try {
                repo.sendMessage(convId, senderId, content)
                loadMessages()
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error sending message", e)
            } finally {
                _isSending.value = false
            }
        }
    }

    // ===================================================
    // MARK AS READ
    // ===================================================

    fun markAsRead() {
        val convId = conversationId ?: return
        val currentUserId = authRepo.getCurrentUser()?.id ?: return
        viewModelScope.launch {
            repo.markAsRead(convId, currentUserId)
        }
    }

    // ===================================================
    // POLLING
    // ===================================================

    private fun startPolling() {
        pollHandler.postDelayed(pollRunnable, pollInterval)
    }

    override fun onCleared() {
        super.onCleared()
        pollHandler.removeCallbacks(pollRunnable)
    }
}
