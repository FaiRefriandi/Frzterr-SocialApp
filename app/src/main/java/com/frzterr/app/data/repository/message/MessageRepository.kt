package com.frzterr.app.data.repository.message

import android.util.Log
import com.frzterr.app.data.model.Conversation
import com.frzterr.app.data.model.ConversationWithUser
import com.frzterr.app.data.model.Message
import com.frzterr.app.data.remote.supabase.SupabaseManager
import com.frzterr.app.data.repository.user.UserRepository
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MessageRepository {

    private val postgrest get() = SupabaseManager.client.postgrest
    private val userRepo = UserRepository()

    // ========================================================================
    // GET CONVERSATIONS
    // ========================================================================

    /**
     * Fetch all conversations for the current user, including the other user's
     * profile and the last message in each conversation.
     */
    suspend fun getConversations(currentUserId: String): List<ConversationWithUser> =
        withContext(Dispatchers.IO) {
            try {
                // Fetch conversations where the current user is either user1 or user2
                val allConversations = postgrest["conversations"]
                    .select()
                    .decodeList<Conversation>()

                val myConversations = allConversations.filter {
                    it.user1Id == currentUserId || it.user2Id == currentUserId
                }

                if (myConversations.isEmpty()) return@withContext emptyList()

                val result = mutableListOf<ConversationWithUser>()

                for (conv in myConversations) {
                    val otherUserId = if (conv.user1Id == currentUserId) conv.user2Id else conv.user1Id
                    val otherUser = userRepo.getUserById(otherUserId) ?: continue

                    // Get all messages for this conversation
                    val messages = postgrest["messages"]
                        .select {
                            filter { eq("conversation_id", conv.id) }
                        }
                        .decodeList<Message>()

                    val lastMessage = messages.maxByOrNull { it.createdAt }
                    val unreadCount = messages.count { !it.isRead && it.senderId != currentUserId }

                    result.add(
                        ConversationWithUser(
                            conversation = conv,
                            otherUser = otherUser,
                            lastMessage = lastMessage,
                            unreadCount = unreadCount
                        )
                    )
                }

                // Sort by last message time descending
                result.sortedByDescending { it.lastMessage?.createdAt ?: it.conversation.createdAt }
            } catch (e: Exception) {
                Log.e("MessageRepository", "Error fetching conversations", e)
                emptyList()
            }
        }

    // ========================================================================
    // DELETE CONVERSATION
    // ========================================================================

    suspend fun deleteConversation(conversationId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                // Hapus semua messages dulu (foreign key)
                postgrest["messages"].delete {
                    filter { eq("conversation_id", conversationId) }
                }
                // Lalu hapus conversation-nya
                postgrest["conversations"].delete {
                    filter { eq("id", conversationId) }
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("MessageRepository", "Error deleting conversation", e)
                Result.failure(e)
            }
        }

    // ========================================================================
    // GET OR CREATE CONVERSATION
    // ========================================================================

    /**
     * Returns an existing conversation or creates a new one between two users.
     * Always stores user1_id as the smaller UUID (lexicographic) to avoid duplicates.
     */
    suspend fun getOrCreateConversation(currentUserId: String, otherUserId: String): Conversation? =
        withContext(Dispatchers.IO) {
            try {
                val (u1, u2) = if (currentUserId < otherUserId)
                    currentUserId to otherUserId
                else
                    otherUserId to currentUserId

                // Try to find existing
                val existing = postgrest["conversations"]
                    .select {
                        filter {
                            eq("user1_id", u1)
                            eq("user2_id", u2)
                        }
                    }
                    .decodeList<Conversation>()
                    .firstOrNull()

                if (existing != null) return@withContext existing

                // Create new
                val payload = mapOf(
                    "user1_id" to u1,
                    "user2_id" to u2
                )
                postgrest["conversations"].insert(payload)

                // Fetch again after insert
                postgrest["conversations"]
                    .select {
                        filter {
                            eq("user1_id", u1)
                            eq("user2_id", u2)
                        }
                    }
                    .decodeList<Conversation>()
                    .firstOrNull()
            } catch (e: Exception) {
                Log.e("MessageRepository", "Error getting/creating conversation", e)
                null
            }
        }

    // ========================================================================
    // GET MESSAGES
    // ========================================================================

    suspend fun getMessages(conversationId: String): List<Message> =
        withContext(Dispatchers.IO) {
            try {
                postgrest["messages"]
                    .select {
                        filter { eq("conversation_id", conversationId) }
                    }
                    .decodeList<Message>()
                    .sortedBy { it.createdAt }
            } catch (e: Exception) {
                Log.e("MessageRepository", "Error fetching messages", e)
                emptyList()
            }
        }

    // ========================================================================
    // SEND MESSAGE
    // ========================================================================

    suspend fun sendMessage(conversationId: String, senderId: String, content: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val payload = mapOf(
                    "conversation_id" to conversationId,
                    "sender_id" to senderId,
                    "content" to content
                )
                postgrest["messages"].insert(payload)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("MessageRepository", "Error sending message", e)
                Result.failure(e)
            }
        }

    // ========================================================================
    // MARK AS READ
    // ========================================================================

    suspend fun markAsRead(conversationId: String, currentUserId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                postgrest["messages"].update({
                    set("is_read", true)
                }) {
                    filter {
                        eq("conversation_id", conversationId)
                        eq("is_read", false)
                        neq("sender_id", currentUserId)
                    }
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e("MessageRepository", "Error marking messages as read", e)
                Result.failure(e)
            }
        }
}
