package com.frzlss.frzterr.data.repository.user

import com.frzlss.frzterr.data.remote.supabase.SupabaseManager
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppUser(
    val id: String,
    val fullName: String?,
    val email: String?,
    val avatarUrl: String?,
    val provider: String?
)

class UserRepository {

    private val postgrest get() = SupabaseManager.client.postgrest

    suspend fun getUserById(uid: String): AppUser? = withContext(Dispatchers.IO) {

        val result = postgrest["users"]
            .select {
                filter {
                    eq("id", uid)
                }
            }
            .decodeList<AppUser>()

        return@withContext result.firstOrNull()
    }

    suspend fun createOrUpdateUser(
        id: String,
        name: String?,
        email: String?,
        avatarUrl: String?,
        provider: String?
    ) = withContext(Dispatchers.IO) {

        postgrest["users"].upsert(
            mapOf(
                "id" to id,
                "full_name" to name,
                "email" to email,
                "avatar_url" to avatarUrl,
                "provider" to provider
            )
        )
    }
}
