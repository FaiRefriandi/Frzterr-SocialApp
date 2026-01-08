package com.frzterr.app.data.repository.user

import android.util.Log
import com.frzterr.app.data.remote.supabase.SupabaseManager
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ============================================================================
// DATA MODEL
// ============================================================================

@Serializable
data class AppUser(
    val id: String,

    @SerialName("full_name")
    val fullName: String? = null,

    val email: String? = null,

    @SerialName("avatar_url")
    val avatarUrl: String? = null,

    val provider: String? = null,

    val username: String,

    @SerialName("username_lower")
    val usernameLower: String,
    
    val bio: String? = null
)

// ============================================================================
// REPOSITORY
// ============================================================================

class UserRepository {

    private val postgrest get() = SupabaseManager.client.postgrest

    // ========================================================================
    // GET USER FROM SUPABASE
    // ========================================================================

    suspend fun getUserById(uid: String): AppUser? = withContext(Dispatchers.IO) {

        Log.e("SUPABASE_USER_DB", "Fetching user with ID: $uid")

        val result = postgrest["users"]
            .select {
                filter {
                    eq("id", uid)
                }
            }
            .decodeList<AppUser>()

        val user = result.firstOrNull()

        Log.e("SUPABASE_USER_DB", "User fetched = $user")

        return@withContext user
    }

    suspend fun getUserByIdForce(uid: String): AppUser? =
        withContext(Dispatchers.IO) {
            postgrest["users"]
                .select {
                    filter { eq("id", uid) }
                }
                .decodeSingleOrNull<AppUser>()
        }

    // ========================================================================
    // CREATE / UPDATE USER DATA IN SUPABASE
    // ========================================================================

    suspend fun createOrUpdateUser(
        id: String,
        name: String?,
        email: String?,
        avatarUrl: String?,
        provider: String?,
        username: String,
        usernameLower: String
    ) = withContext(Dispatchers.IO) {

        Log.e(
            "SUPABASE_USER_DB",
            "Upserting user:\n" +
                    "ID = $id\n" +
                    "Name = $name\n" +
                    "Email = $email\n" +
                    "Username = $username\n" +
                    "Provider = $provider"
        )

        val payload = mutableMapOf(
            "id" to id,
            "full_name" to name,
            "email" to email,
            "provider" to provider,
            "username" to username,
            "username_lower" to usernameLower
        )

        if (avatarUrl != null) {
            payload["avatar_url"] = avatarUrl
        }

        postgrest["users"].upsert(payload)
    }

    suspend fun updateAvatarUrl(userId: String, avatarUrl: String) =
        withContext(Dispatchers.IO) {

            val payload = mapOf(
                "id" to userId,
                "avatar_url" to avatarUrl
            )

            postgrest["users"].upsert(payload)
        }

    // ========================================================================
    // USERNAME UTILITIES
    // ========================================================================

    private fun sanitizeUsername(raw: String): String {
        return raw.lowercase()
            .replace(Regex("[^a-z0-9_]"), "")
            .take(20)
    }

    suspend fun isUsernameAvailable(usernameLower: String): Boolean =
        withContext(Dispatchers.IO) {

            val result = postgrest["users"]
                .select {
                    filter { eq("username_lower", usernameLower) }
                }
                .decodeList<AppUser>()

            result.isEmpty()
        }

    suspend fun generateUniqueUsername(base: String): Pair<String, String> {
        val clean = sanitizeUsername(base)
        var candidate = clean
        var suffix = 1

        while (!isUsernameAvailable(candidate)) {
            candidate = "${clean}_$suffix"
            suffix++
        }

        return candidate to candidate.lowercase()
    }

    suspend fun updateUserProfile(
        userId: String,
        fullName: String,
        username: String,
        bio: String?,  // Can be null to clear
        avatarUrl: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.e("SUPABASE_USER_DB", "Updating profile for user: $userId")
            Log.e("SUPABASE_USER_DB", "fullName=$fullName, username=$username, bio=$bio, avatarUrl=$avatarUrl")
            
            // Don't use <String, Any?> - kotlinx.serialization can't serialize Any?
            // Let Kotlin infer the type automatically
            val payload = mutableMapOf("id" to userId)
            
            // Always send these fields (even null to clear them)
            payload["full_name"] = fullName
            payload["username"] = username
            payload["username_lower"] = username.lowercase()
            
            // Handle nullable bio - kotlin can't infer nullable in map
            if (bio != null) {
                payload["bio"] = bio
            } else {
                payload["bio"] = ""  // Empty string instead of null
            }
            
            if (avatarUrl != null) payload["avatar_url"] = avatarUrl
            
            Log.e("SUPABASE_USER_DB", "Payload: $payload")
            
            postgrest["users"].upsert(payload)
            
            Log.e("SUPABASE_USER_DB", "Profile updated successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("SUPABASE_USER_DB", "Error updating profile: ${e.message}", e)
            Log.e("SUPABASE_USER_DB", "Stack trace: ${e.stackTraceToString()}")
            Result.failure(e)
        }
    }

    suspend fun checkUsernameAvailable(username: String, currentUserId: String): Boolean =
        withContext(Dispatchers.IO) {
            val result = postgrest["users"]
                .select {
                    filter {
                        eq("username_lower", username.lowercase())
                    }
                }
                .decodeList<AppUser>()
            
            // Username is available if no results, or the only result is current user
            result.isEmpty() || (result.size == 1 && result[0].id == currentUserId)
        }

    // ========================================================================
    // FOLLOW SYSTEM
    // ========================================================================

    suspend fun followUser(followerId: String, followingId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val payload = mapOf(
                    "follower_id" to followerId,
                    "following_id" to followingId
                )
                postgrest["follows"].insert(payload)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun unfollowUser(followerId: String, followingId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                postgrest["follows"]
                    .delete {
                        filter {
                            eq("follower_id", followerId)
                            eq("following_id", followingId)
                        }
                    }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun isFollowing(followerId: String, followingId: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val result = postgrest["follows"]
                    .select {
                        filter {
                            eq("follower_id", followerId)
                            eq("following_id", followingId)
                        }
                        count(io.github.jan.supabase.postgrest.query.Count.EXACT)
                    }
                // If count > 0, then is following. 
                // Note: The simple select might return list of objects.
                // Checking if list is not empty is safer.
                 val count = result.countOrNull() ?: 0L 
                 count > 0
            } catch (e: Exception) {
                false
            }
        }

    suspend fun getFollowerCount(userId: String): Long =
        withContext(Dispatchers.IO) {
            try {
                val result = postgrest["follows"]
                    .select {
                        filter {
                            eq("following_id", userId)
                        }
                        count(io.github.jan.supabase.postgrest.query.Count.EXACT)
                    }
                result.countOrNull() ?: 0L
            } catch (e: Exception) {
                0L
            }
        }

    suspend fun getFollowingCount(userId: String): Long {
        return withContext(Dispatchers.IO) {
            try {
                val result = postgrest["follows"]
                    .select {
                        filter {
                            eq("follower_id", userId)
                        }
                        count(io.github.jan.supabase.postgrest.query.Count.EXACT)
                    }
                result.countOrNull() ?: 0L
            } catch (e: Exception) {
                Log.e("UserRepository", "Error getting following count", e)
                0L
            }
        }
    }

    // ============================================================================
    // SEARCH
    // ============================================================================
    
    suspend fun searchUsers(query: String, limit: Int = 20, excludeUserId: String? = null): List<AppUser> {
        return withContext(Dispatchers.IO) {
            try {
                if (query.isBlank()) return@withContext emptyList()
                
                val searchPattern = "%${query.trim()}%"
                
                val response = postgrest["users"]
                    .select {
                        filter {
                            or {
                                ilike("full_name", searchPattern)
                                ilike("username", searchPattern)
                            }
                            if (excludeUserId != null) {
                                neq("id", excludeUserId)
                            }
                        }
                        limit(limit.toLong())
                    }

                response.decodeList<AppUser>()
            } catch (e: Exception) {
                Log.e("UserRepository", "Error searching users", e)
                emptyList()
            }
        }
    }
}
