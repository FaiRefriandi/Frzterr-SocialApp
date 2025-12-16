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
    val usernameLower: String
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
}
