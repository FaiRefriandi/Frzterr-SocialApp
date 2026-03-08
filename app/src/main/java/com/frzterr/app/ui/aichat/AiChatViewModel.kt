package com.frzterr.app.ui.aichat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.util.UUID

// Available NVIDIA models
data class NvidiaModel(
    val displayName: String,
    val modelId: String,
    val description: String = "",
    val iconRes: Int = com.frzterr.app.R.drawable.ic_ai_star_bold,
    val speedLabel: String = "",
    val speedColor: String = ""
)

val NVIDIA_MODELS = listOf(
    NvidiaModel("Qwen 3.5 122B",  "qwen/qwen3.5-122b-a10b",       "Cepat • Cocok untuk chat harian",        com.frzterr.app.R.drawable.ic_qwen,     speedLabel = "Cepat",  speedColor = "#1DB954"),
    NvidiaModel("GPT-OSS 120B",   "openai/gpt-oss-120b",           "OpenAI • Performa tinggi & serbaguna",   com.frzterr.app.R.drawable.ic_chat_gpt, speedLabel = "Cepat",  speedColor = "#1DB954"),
    NvidiaModel("Qwen 3.5 397B",  "qwen/qwen3.5-397b-a17b",        "Kuat • Reasoning & analisis mendalam",   com.frzterr.app.R.drawable.ic_qwen,     speedLabel = "Lambat", speedColor = "#E53935"),
    NvidiaModel("DeepSeek V3.2",  "deepseek-ai/deepseek-v3.2",     "DeepSeek • Coding & reasoning canggih",  com.frzterr.app.R.drawable.ic_deepseek, speedLabel = "Lambat", speedColor = "#E53935")
)

class AiChatViewModel(application: Application) : AndroidViewModel(application) {

    private val apiService = NvidiaApiService()
    private val historyManager = ChatHistoryManager(application)

    // ── Chat sessions ──────────────────────────────────────────
    private val _sessions = MutableLiveData<List<ChatSession>>(emptyList())
    val sessions: LiveData<List<ChatSession>> = _sessions

    // ── Active session ─────────────────────────────────────────
    private var currentSessionId: String? = null
    private val currentMessages = mutableListOf<AiChatMessage>()

    private val _messages = MutableLiveData<List<AiChatMessage>>(emptyList())
    val messages: LiveData<List<AiChatMessage>> = _messages

    // ── Model selector ─────────────────────────────────────────
    private val _selectedModel = MutableLiveData(NVIDIA_MODELS[0])
    val selectedModel: LiveData<NvidiaModel> = _selectedModel

    // ── UI state ───────────────────────────────────────────────
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    init {
        loadSessions()
    }

    // ── Session management ─────────────────────────────────────

    private fun loadSessions() {
        _sessions.value = historyManager.loadSessions()
    }

    fun startNewChat() {
        currentSessionId = null
        currentMessages.clear()
        _messages.value = emptyList()
    }

    fun switchToSession(sessionId: String) {
        val session = _sessions.value?.find { it.id == sessionId } ?: return
        currentSessionId = sessionId
        currentMessages.clear()
        currentMessages.addAll(session.messages)
        _messages.value = currentMessages.toList()
    }

    fun deleteSession(sessionId: String) {
        historyManager.deleteSession(sessionId)
        loadSessions()
        if (currentSessionId == sessionId) startNewChat()
    }

    private fun saveCurrentSession() {
        if (currentMessages.isEmpty()) return
        val sessions = historyManager.loadSessions().toMutableList()
        val title = currentMessages.firstOrNull { it.role == "user" }?.content
            ?.take(40) ?: "Chat"
        val session = ChatSession(
            id = currentSessionId ?: UUID.randomUUID().toString().also { currentSessionId = it },
            title = title,
            messages = currentMessages.toList()
        )
        val idx = sessions.indexOfFirst { it.id == currentSessionId }
        if (idx >= 0) sessions[idx] = session else sessions.add(0, session)
        historyManager.saveSessions(sessions)
        _sessions.postValue(sessions)  // postValue = thread-safe, dipanggil dari background thread
    }

    // ── Model selection ────────────────────────────────────────

    fun selectModel(model: NvidiaModel) {
        _selectedModel.value = model
    }

    // ── Sending messages ───────────────────────────────────────

    fun sendMessage(userText: String) {
        if (userText.isBlank() || _isLoading.value == true) return

        val userMsg = AiChatMessage("user", userText.trim())
        currentMessages.add(userMsg)
        _messages.value = currentMessages.toList()
        _isLoading.value = true

        val modelId   = _selectedModel.value?.modelId ?: NVIDIA_MODELS[0].modelId
        val modelName = _selectedModel.value?.displayName ?: "AI"

        // Tambah placeholder kosong untuk AI — akan diisi secara realtime
        val placeholderMsg = AiChatMessage("assistant", "", modelName)
        currentMessages.add(placeholderMsg)
        _messages.postValue(currentMessages.toList())

        val streamBuffer = StringBuilder()

        apiService.sendMessage(
            history = currentMessages.dropLast(1), // jangan kirim placeholder ke API
            modelId = modelId,
            onChunk = { chunk ->
                streamBuffer.append(chunk)
                // Update pesan terakhir (placeholder) dengan teks yang terus bertambah
                val updated = currentMessages.toMutableList()
                updated[updated.lastIndex] = AiChatMessage("assistant", streamBuffer.toString(), modelName)
                _messages.postValue(updated)
            },
            onDone = { fullText ->
                // Finalisasi: ganti placeholder dengan teks lengkap bersih
                currentMessages[currentMessages.lastIndex] = AiChatMessage("assistant", fullText, modelName)
                _messages.postValue(currentMessages.toList())
                _isLoading.postValue(false)
                saveCurrentSession()
            },
            onError = { errMsg ->
                // Hapus placeholder bila error
                if (currentMessages.lastOrNull()?.content?.isEmpty() == true) {
                    currentMessages.removeLastOrNull()
                    _messages.postValue(currentMessages.toList())
                }
                _error.postValue(errMsg)
                _isLoading.postValue(false)
            }
        )
    }

    fun clearAllHistory() {
        historyManager.clearAll()
        _sessions.value = emptyList()
        startNewChat()
    }

    fun clearError() { _error.value = null }
}
