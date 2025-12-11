package com.frzterr.app.data.repository.auth

import android.util.Log
import com.frzterr.app.data.remote.supabase.SupabaseManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AuthRepository {

    private val auth get() = SupabaseManager.client.auth

    // ============================================================
    // EMAIL SIGNUP
    // ============================================================

    suspend fun signUpWithEmail(email: String, password: String): UserInfo? {
        Log.e("SUPABASE_EMAIL", "Trying signUpWithEmail: $email")

        auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }

        return auth.currentUserOrNull()
    }

    // ============================================================
    // EMAIL LOGIN
    // ============================================================

    suspend fun signInWithEmail(email: String, password: String): UserInfo? {
        Log.e("SUPABASE_EMAIL", "Trying signInWithEmail: $email")

        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }

        return auth.currentUserOrNull()
    }

    // ============================================================
    // RESET PASSWORD (Send OTP → Edge Function) using OkHttp
    // ============================================================

    // Ganti PROJECT_REF jika bukan project ref yang benar
    private val PROJECT_REF = "wulshdvhdlcwlhsozkzl"
    private val FUNCTIONS_BASE = "https://$PROJECT_REF.functions.supabase.co"
    private val httpClient = OkHttpClient()

    suspend fun sendResetOtp(email: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.e("SUPABASE_RESET_PW", "Sending OTP to: $email (via functions endpoint)")

            val url = "$FUNCTIONS_BASE/send-otp"
            val json = """{ "email":"${email.replace("\"","\\\"")}" }"""
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val body = json.toRequestBody(mediaType)

            val req = Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Authorization", "Bearer ${SupabaseManager.ANON_KEY}")
                .addHeader("apikey", SupabaseManager.ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .build()

            httpClient.newCall(req).execute().use { resp ->
                Log.e("SUPABASE_RESET_PW", "send-otp HTTP ${resp.code}")
                return@withContext resp.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("SUPABASE_RESET_PW", "sendResetOtp error: ${e.message}", e)
            return@withContext false
        }
    }

    // ============================================================
    // VERIFY OTP & UPDATE PASSWORD → Edge Function using OkHttp
    // ============================================================

    suspend fun verifyResetOtp(email: String, otp: String, newPassword: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.e("SUPABASE_RESET_VERIFY", "Verifying OTP for email: $email (via functions endpoint)")

            val url = "$FUNCTIONS_BASE/verify-reset"
            // escape strings minimally
            val payload = buildString {
                append("{")
                append("\"email\":\"${email.replace("\"","\\\"")}\",")
                append("\"otp\":\"${otp.replace("\"","\\\"")}\",")
                append("\"newPassword\":\"${newPassword.replace("\"","\\\"")}\"")
                append("}")
            }

            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val body = payload.toRequestBody(mediaType)

            val req = Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Authorization", "Bearer ${SupabaseManager.ANON_KEY}")
                .addHeader("apikey", SupabaseManager.ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .build()

            httpClient.newCall(req).execute().use { resp ->
                Log.e("SUPABASE_RESET_VERIFY", "verify-reset HTTP ${resp.code}")
                return@withContext resp.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("SUPABASE_RESET_VERIFY", "verifyResetOtp error: ${e.message}", e)
            return@withContext false
        }
    }

    // ============================================================
    // GOOGLE LOGIN VIA ID TOKEN (GIS Credential Manager)
    // ============================================================

    suspend fun signInWithGoogleIdToken(idToken: String): UserInfo? {
        Log.e("SUPABASE_GOOGLE", "Received ID Token (first 20 chars): ${idToken.take(20)}...")

        val session = auth.signInWith(IDToken) {
            this.idToken = idToken
            this.provider = Google
        }

        Log.e("SUPABASE_GOOGLE", "Supabase session response: $session")

        return auth.currentUserOrNull()
    }

    // ============================================================
    // UPDATE DISPLAY NAME
    // ============================================================

    suspend fun updateDisplayName(name: String) {
        Log.e("SUPABASE_USER", "Updating display name to: $name")

        auth.updateUser {
            data = buildJsonObject {
                put("full_name", name)
            }
        }
    }

    // ============================================================
    // SIGN OUT
    // ============================================================

    suspend fun signOut() {
        Log.e("SUPABASE_AUTH", "Signing out user")
        auth.signOut()
    }

    // ============================================================
    // GET CURRENT USER
    // ============================================================

    fun getCurrentUser(): UserInfo? {
        val usr = auth.currentUserOrNull()
        Log.e("SUPABASE_AUTH", "Current user = $usr")
        return usr
    }

    fun isLoggedIn(): Boolean {
        val session = auth.currentSessionOrNull()
        Log.e("SUPABASE_AUTH", "Checking isLoggedIn = ${session != null}")
        return session != null
    }
}
