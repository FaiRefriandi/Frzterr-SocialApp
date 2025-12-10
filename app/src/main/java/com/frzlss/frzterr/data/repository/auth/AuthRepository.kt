package com.frzlss.frzterr.data.repository.auth

import com.frzlss.frzterr.data.remote.supabase.SupabaseManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put



class AuthRepository {

    private val auth get() = SupabaseManager.client.auth

    suspend fun signUpWithEmail(email: String, password: String): UserInfo? {
        auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        return auth.currentUserOrNull()
    }

    suspend fun signInWithEmail(email: String, password: String): UserInfo? {
        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        return auth.currentUserOrNull()
    }

    suspend fun signInWithGoogleIdToken(idToken: String): UserInfo? {
        auth.signInWith(IDToken) {
            this.idToken = idToken
            this.provider = Google
        }
        return auth.currentUserOrNull()
    }

    suspend fun updateDisplayName(name: String) {
        auth.updateUser {
            this.data = buildJsonObject {
                put("full_name", name)
            }
        }
    }

    suspend fun signOut() {
        auth.signOut()
    }

    fun getCurrentUser(): UserInfo? = auth.currentUserOrNull()

    fun isLoggedIn(): Boolean = auth.currentSessionOrNull() != null
}
